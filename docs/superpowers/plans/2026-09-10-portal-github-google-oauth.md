# Portal GitHub 与 Google OAuth Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** 让 Portal 通过 NewAPI 的 GitHub 与 Google OIDC 登录流程建立现有的 Portal 安全会话。

**Architecture:** Portal 后端只提供三个固定 OAuth BFF 操作：读取公开 provider 启动信息、创建 NewAPI state、完成固定 provider 回调。React 负责启动浏览器授权和在回调页将 code/state 交给 BFF；所有账号创建、OAuth token 换取和 NewAPI 登录包仍由 NewAPI 完成。

**Tech Stack:** Spring Boot 3 / Java 17、WebClient、JUnit 5 + MockWebServer、React 19、TypeScript、Vitest、Semi UI、i18next。

---

## 文件结构

- backend/src/main/java/io/ztoken/portal/newapi/NewApiClient.java 与 NewApiHttpClient.java：受限的 NewAPI OAuth 读取、state 与完成操作。
- backend/src/main/java/io/ztoken/portal/newapi/OAuthProviderStatus.java、OAuthCallback.java：BFF 的安全 DTO。
- backend/src/main/java/io/ztoken/portal/auth/AuthController.java：OAuth HTTP 端点与 Portal cookie 写入。
- backend/src/test/java/io/ztoken/portal/auth/AuthControllerTest.java：端到端 BFF 合同测试。
- frontend/src/api/auth.ts、features/auth/SignInPage.tsx、features/auth/OAuthCallbackPage.tsx、App.tsx：按钮、授权跳转和回调。
- frontend/src/features/auth/__tests__/oauth-callback-page.test.tsx：OAuth 前端回归测试。
- README.md：回调地址和 NewAPI ServerAddress 配置。

### Task 1: NewAPI OAuth 客户端

**Files:**

- Create: backend/src/main/java/io/ztoken/portal/newapi/OAuthProviderStatus.java
- Create: backend/src/main/java/io/ztoken/portal/newapi/OAuthCallback.java
- Modify: backend/src/main/java/io/ztoken/portal/newapi/NewApiClient.java
- Modify: backend/src/main/java/io/ztoken/portal/newapi/NewApiHttpClient.java
- Test: backend/src/test/java/io/ztoken/portal/newapi/NewApiHttpClientTest.java

- [ ] **Step 1: 写失败测试：OAuth completion 仅向固定的 NewAPI 路径发送允许的查询参数，并返回标准登录包。**

~~~java
@Test
void oauthCompletionParsesNewApiLoginBundle() throws Exception {
    server.enqueue(json("{\"success\":true,\"data\":{\"access_token\":\"at\",\"user\":{\"id\":7,\"username\":\"alice\"}}}"));
    NewApiLogin login = client.completeOAuth("github", new OAuthCallback("code", "state", null, null));
    assertThat(login).isEqualTo(new NewApiLogin(new NewApiIdentity(7L, "alice"), "at"));
    assertThat(server.takeRequest().getPath()).isEqualTo("/api/oauth/github?code=code&state=state");
}
~~~

- [ ] **Step 2: 运行并确认失败。**

Run: mvn -f backend/pom.xml test -Dskip.frontend=true -Dtest=NewApiHttpClientTest#oauthCompletionParsesNewApiLoginBundle

Expected: FAIL，因为 OAuth DTO 和客户端方法尚未存在。

- [ ] **Step 3: 写最小实现。**

~~~java
public record OAuthProviderStatus(boolean githubEnabled, String githubClientId,
                                  boolean oidcEnabled, String oidcClientId,
                                  String oidcAuthorizationEndpoint, String oidcDisplayName) {}
public record OAuthCallback(String code, String state, String error, String errorDescription) {}

OAuthProviderStatus getOAuthProviderStatus();
String createOAuthState(String provider);
NewApiLogin completeOAuth(String provider, OAuthCallback callback);
~~~

NewApiHttpClient 只读取 /api/status 所需公开字段；固定 POST /api/oauth/state 的 {provider, intent:"login"}；用 UriBuilder 构造 GET /api/oauth/{provider}。三个方法均拒绝 github、oidc 之外的 provider，不记录 code、state 或 token。

- [ ] **Step 4: 验证测试通过。**

Run: mvn -f backend/pom.xml test -Dskip.frontend=true -Dtest=NewApiHttpClientTest

Expected: PASS。

- [ ] **Step 5: 提交。**

~~~powershell
git add backend/src/main/java/io/ztoken/portal/newapi backend/src/test/java/io/ztoken/portal/newapi/NewApiHttpClientTest.java
git commit -m "feat: 增加 Portal OAuth 上游客户端"
~~~

### Task 2: Portal OAuth BFF 与会话

**Files:**

- Modify: backend/src/main/java/io/ztoken/portal/auth/AuthController.java
- Modify: backend/src/test/java/io/ztoken/portal/auth/AuthControllerTest.java

- [ ] **Step 1: 写失败的集成测试。**

~~~java
@Test
void oauthCompleteCreatesPortalSessionWithoutReturningNewApiToken() throws Exception {
    NEW_API.enqueue(json("{\"success\":true,\"data\":{\"access_token\":\"upstream-secret\",\"user\":{\"id\":7,\"username\":\"alice\"}}}"));
    ResponseEntity<String> response = http.postForEntity("/api/auth/oauth/github/complete",
            Map.of("code", "provider-code", "state", "flow-token"), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("PORTAL_SESSION=");
    assertThat(response.getBody()).doesNotContain("upstream-secret");
}
~~~

增加测试覆盖 GET /api/auth/oauth/providers 的公开投影、state 创建、未知 provider 为 400、上游错误或 OAuth error 不创建 cookie。

- [ ] **Step 2: 运行并确认失败。**

Run: mvn -f backend/pom.xml test -Dskip.frontend=true -Dtest=AuthControllerTest

Expected: FAIL，OAuth endpoint 不存在。

- [ ] **Step 3: 写最小实现。**

AuthController 增加 GET /oauth/providers、POST /oauth/{provider}/state、POST /oauth/{provider}/complete。将已有密码登录创建 cookie 的尾部提取为 withPortalSession(NewApiLogin)，让 OAuth 使用完全相同的 HttpOnly、Secure、SameSite、TTL 设置。回调 DTO 约束 state 非空，且 code 或 error 至少一个有效；只接受两个常量 provider。上游错误继续交给既有异常处理器，绝不把 response body、refresh cookie 或 token 返回给浏览器。

- [ ] **Step 4: 验证测试通过。**

Run: mvn -f backend/pom.xml test -Dskip.frontend=true -Dtest=AuthControllerTest

Expected: PASS。

- [ ] **Step 5: 提交。**

~~~powershell
git add backend/src/main/java/io/ztoken/portal/auth backend/src/test/java/io/ztoken/portal/auth/AuthControllerTest.java
git commit -m "feat: 提供 Portal OAuth 登录接口"
~~~

### Task 3: 登录按钮和回调页

**Files:**

- Modify: frontend/src/api/auth.ts
- Modify: frontend/src/features/auth/SignInPage.tsx
- Create: frontend/src/features/auth/OAuthCallbackPage.tsx
- Modify: frontend/src/App.tsx、frontend/src/styles.css
- Modify: frontend/src/i18n/locales/en.json、frontend/src/i18n/locales/zh-CN.json
- Test: frontend/src/features/auth/__tests__/sign-in-page.test.tsx
- Test: frontend/src/features/auth/__tests__/oauth-callback-page.test.tsx

- [ ] **Step 1: 写失败的前端测试。**

~~~tsx
it('starts GitHub OAuth with a BFF-issued state', async () => {
  vi.stubGlobal('fetch', vi.fn()
    .mockResolvedValueOnce(jsonResponse({ githubEnabled: true, githubClientId: 'gh-client', oidcEnabled: false }))
    .mockResolvedValueOnce(jsonResponse({ state: 'flow-token' })))
  render(<SignInPage />)
  await userEvent.click(await screen.findByRole('button', { name: 'Continue with GitHub' }))
  expect(window.location.assign).toHaveBeenCalledWith(expect.stringContaining('state=flow-token'))
})
~~~

回调页测试传入 /oauth/github?code=abc&state=flow，断言 POST complete 后 location.replace('/console/dashboard')；非法 provider 或失败请求替换到 /sign-in，且页面不渲染 code/state。

- [ ] **Step 2: 运行并确认失败。**

Run: npm --prefix frontend test -- src/features/auth/__tests__/sign-in-page.test.tsx src/features/auth/__tests__/oauth-callback-page.test.tsx

Expected: FAIL，OAuth UI 和 callback page 不存在。

- [ ] **Step 3: 写最小实现。**

auth.ts 提供 getOAuthProviders、createOAuthState、completeOAuth。GitHub URL 使用 client ID、state、scope=user:email；OIDC 使用授权端点与 client_id、当前 origin 的 /oauth/oidc、response_type=code、scope=openid profile email、state。登录页仅在启用且参数完整时展示按钮。回调页仅执行一次提交，成功 location.replace('/console/dashboard')，失败显示本地化错误后 location.replace('/sign-in')。App 显式匹配两个 callback route。

- [ ] **Step 4: 添加翻译和样式。**

添加 auth.oauthDivider、auth.continueGithub、auth.continueGoogle、auth.oauthLoading、auth.oauthFailed 的中英文文本；使用全宽次级按钮、可见焦点和移动端同宽布局。

- [ ] **Step 5: 验证前端测试与构建。**

Run: npm --prefix frontend test -- src/features/auth/__tests__/sign-in-page.test.tsx src/features/auth/__tests__/oauth-callback-page.test.tsx

Expected: PASS。

Run: npm --prefix frontend run build

Expected: PASS。

- [ ] **Step 6: 提交。**

~~~powershell
git add frontend/src
git commit -m "feat: 增加 GitHub 与 Google 登录入口"
~~~

### Task 4: 部署文档与全量验证

**Files:**

- Modify: README.md

- [ ] **Step 1: 更新部署说明。**

记录 GitHub callback https://<portal-domain>/oauth/github、Google callback https://<portal-domain>/oauth/oidc、NewAPI ServerAddress=https://<portal-domain>，明确 Google 走 NewAPI OIDC，Portal 不保存 OAuth secret；替换 README 中“OAuth 不在本计划内”的过时表述。

- [ ] **Step 2: 运行完整验证。**

Run: mvn -f backend/pom.xml clean package

Expected: PASS，包含后端测试与前端生产构建。

- [ ] **Step 3: 审核敏感信息与空白。**

Run: git diff --check; rg -n "client_secret|access_token|flow-token|provider-code" backend/src/main frontend/src

Expected: 无空白错误；生产代码无硬编码 OAuth secret、授权码或测试 token。

- [ ] **Step 4: 提交。**

~~~powershell
git add README.md
git commit -m "docs: 说明 Portal OAuth 部署配置"
~~~
