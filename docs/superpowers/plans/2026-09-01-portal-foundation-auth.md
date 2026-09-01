# Ztoken Portal 基础骨架与邮箱认证实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不修改 NewAPI 源码的前提下，完成 Portal 的单端口 React/Spring Boot 基础骨架、统一页面壳、中文/英文切换，以及可通过 NewAPI HTTP API 完成的邮箱注册登录流程。

**Architecture:** 延续现有 `portal` 项目结构。React 只调用 Portal 的 `/api/**` 接口；Spring Boot 负责会话、NewAPI HTTP 调用和 SPA 回退。Portal 不访问 NewAPI 数据库，NewAPI access token 只以加密形式保存在 Portal 会话记录中。首页、模型页和控制台页面先形成可运行的页面边界，完整支付、订单、OAuth 和控制台业务留给后续独立计划。

**Tech Stack:** Java 17, Spring Boot 3.3, Spring MVC/WebClient, JPA, Flyway, MySQL, React 19, TypeScript, Vite, Semi Design, React Query, i18next, Vitest, Testing Library.

---

## 文件结构与职责

### 后端

- Modify: `backend/src/main/java/io/ztoken/portal/newapi/NewApiClient.java` — 定义认证、当前用户和公共目录所需的 NewAPI 边界。
- Modify: `backend/src/main/java/io/ztoken/portal/newapi/NewApiHttpClient.java` — 实现 NewAPI 登录、注册和当前用户请求；集中处理认证头与错误。
- Modify: `backend/src/main/java/io/ztoken/portal/auth/AuthController.java` — 暴露 Portal 登录、注册、当前会话和退出接口；沿用现有 `/login`、`/register`、`/me` 路径并补充退出。
- Modify: `backend/src/main/java/io/ztoken/portal/session/PortalSessionService.java` — 创建、读取、撤销会话，并确保 access token 不以明文写入日志或响应。
- Modify: `backend/src/main/java/io/ztoken/portal/session/SessionCrypto.java` — 使用配置的 32 字节密钥加密 NewAPI access token。
- Modify: `backend/src/main/java/io/ztoken/portal/config/PortalProperties.java` — 增加 OAuth/认证基础配置的预留结构，但本计划不实现 OAuth。
- Modify: `backend/src/main/resources/application.yml` — 将 NewAPI 地址、会话密钥和 Cookie 安全属性改为明确的环境变量配置。
- Create: `backend/src/test/java/io/ztoken/portal/auth/AuthControllerTest.java` — 验证登录、注册、当前会话和退出的 HTTP 合约。
- Create: `backend/src/test/java/io/ztoken/portal/session/SessionCryptoTest.java` — 验证加密结果可解密、错误密钥不可解密。

### 前端

- Modify: `frontend/src/App.tsx` — 统一公开页面、认证页面和控制台页面路由分发。
- Modify: `frontend/src/components/PublicHeader.tsx` — 公共导航：首页、模型、文档、购买、控制台和语言切换。
- Modify: `frontend/src/components/ConsoleLayout.tsx` — Semi Design 控制台布局和侧边导航。
- Modify: `frontend/src/features/home/HomePage.tsx` — 大气风格首页基础结构和模型价格摘要入口。
- Modify: `frontend/src/features/catalog/ModelsPage.tsx` — 模型页基础分组展示边界。
- Create: `frontend/src/features/purchase/PurchasePage.tsx` — 充值金额卡片占位，不创建真实支付订单。
- Create: `frontend/src/features/console/ConsoleShellPage.tsx` — 控制台首页壳和后续功能入口。
- Modify: `frontend/src/features/auth/SignInPage.tsx` — 邮箱登录表单和错误状态。
- Modify: `frontend/src/features/auth/SignUpPage.tsx` — 邮箱注册表单和错误状态。
- Modify: `frontend/src/api/auth.ts` — 登录、注册、会话状态和退出 API 客户端。
- Modify: `frontend/src/i18n/index.ts` — 中文/英文语言检测、切换与持久化。
- Modify: `frontend/src/i18n/locales/en.json` — 英文翻译。
- Modify: `frontend/src/i18n/locales/zh-CN.json` — 中文翻译（沿用现有文件名）。
- Create: `frontend/src/features/purchase/__tests__/purchase-page.test.tsx` — 验证金额卡片和支付方式占位展示。
- Modify: `frontend/src/__tests__/app-shell.test.tsx` — 验证所有一级路径进入正确页面。

---

## Task 1: 固化认证与配置边界

**Files:**
- Modify: `backend/src/main/java/io/ztoken/portal/newapi/NewApiClient.java`
- Modify: `backend/src/main/java/io/ztoken/portal/newapi/NewApiHttpClient.java`
- Modify: `backend/src/main/java/io/ztoken/portal/config/PortalProperties.java`
- Modify: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/io/ztoken/portal/newapi/NewApiHttpClientTest.java`

- [ ] **Step 1: 写 NewAPI HTTP 合约测试**

为登录和注册准备 MockWebServer 测试，固定请求方法、路径、JSON 字段和响应解析。登录请求必须发送 `username`、`password`，并从响应解析 access token、用户 ID 和用户名；注册请求必须发送 `username`、`email`、`password`。测试还要验证非 2xx 响应转换为 `NewApiException`，响应体不得原样包含在用户可见错误中。

```java
@Test
void loginSendsCredentialsAndParsesIdentity() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody("{\"success\":true,\"data\":{\"access_token\":\"token-1\",\"id\":7,\"username\":\"alice\"}}"));

    NewApiLogin result = client.login("alice", "secret");

    RecordedRequest request = server.takeRequest();
    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getPath()).isEqualTo("/api/user/login");
    assertThat(request.getBody().readUtf8()).contains("\"username\":\"alice\"");
    assertThat(result.accessToken()).isEqualTo("token-1");
    assertThat(result.identity().id()).isEqualTo(7);
}

@Test
void registerSendsEmailAndPassword() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody("{\"success\":true,\"data\":null}"));

    client.register("alice", "alice@example.com", "secret");

    RecordedRequest request = server.takeRequest();
    assertThat(request.getPath()).isEqualTo("/api/user/register");
    assertThat(request.getBody().readUtf8())
        .contains("\"email\":\"alice@example.com\"")
        .contains("\"password\":\"secret\"");
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
cd F:\WorkSpace\study\AIProject\Ztoken\portal
mvn -f backend/pom.xml -Dtest=NewApiHttpClientTest test
```

Expected: FAIL because the contract test targets the final request/response behavior that is not fully represented by the current adapter.

- [ ] **Step 3: 实现最小 NewAPI 适配边界**

在 `NewApiClient` 中保持领域接口，不让 Controller 依赖 WebClient 类型。`NewApiLogin` 增加不可变的 `accessToken` 和 `NewApiIdentity`；`NewApiHttpClient` 统一通过一个私有请求方法发送 JSON，并仅在服务端日志记录状态码、请求路径和业务订单号之外的安全信息。登录路径使用 `/api/user/login`，注册路径使用 `/api/user/register`。所有 access token 只能返回给 `PortalSessionService`，不能出现在 REST 响应中。

```java
public interface NewApiClient {
    NewApiLogin login(String username, String password);
    void register(String username, String email, String password);
    NewApiIdentity getSelf(PortalPrincipal principal);
    DashboardSummary getDashboard(PortalPrincipal principal);
    TokenList getTokens(PortalPrincipal principal);
    ModelCatalog getModelCatalog();
}
```

- [ ] **Step 4: 增加安全配置绑定**

`PortalProperties` 必须从环境变量读取：

```yaml
portal:
  new-api:
    base-url: ${NEWAPI_BASE_URL:http://localhost:3005}
  session-ttl: ${PORTAL_SESSION_TTL:7d}
  session-key: ${PORTAL_SESSION_KEY:}
  session-secure-cookie: ${PORTAL_SESSION_SECURE_COOKIE:false}
```

生产启动时缺少 `PORTAL_SESSION_KEY` 必须失败，而不是使用固定默认密钥。测试 profile 可以显式提供测试密钥。保留当前测试环境地址只能通过环境变量配置，不能写入 Java 常量。

- [ ] **Step 5: 运行测试确认通过**

Run:

```powershell
mvn -f backend/pom.xml -Dtest=NewApiHttpClientTest test
```

Expected: PASS。

- [ ] **Step 6: 提交**

```powershell
git add backend/src/main/java backend/src/main/resources/application.yml backend/src/test/java/io/ztoken/portal/newapi/NewApiHttpClientTest.java
git commit -m "feat: 固化 Portal NewAPI 认证适配边界"
```

---

## Task 2: 完成安全会话闭环

**Files:**
- Modify: `backend/src/main/java/io/ztoken/portal/session/SessionCrypto.java`
- Modify: `backend/src/main/java/io/ztoken/portal/session/PortalSessionService.java`
- Modify: `backend/src/main/java/io/ztoken/portal/auth/AuthController.java`
- Test: `backend/src/test/java/io/ztoken/portal/session/SessionCryptoTest.java`
- Test: `backend/src/test/java/io/ztoken/portal/auth/AuthControllerTest.java`

- [ ] **Step 1: 写加密和会话失败路径测试**

测试以下可观察契约：同一明文每次加密结果不同；正确密钥可解密；错误密钥和篡改密文抛出受控异常；登录成功后只返回认证状态，不返回 NewAPI access token；退出后会话不能继续访问受保护接口。

```java
@Test
void encryptionUsesFreshNonceAndRoundTrips() {
    String first = crypto.encrypt("new-api-token");
    String second = crypto.encrypt("new-api-token");

    assertThat(first).isNotEqualTo(second);
    assertThat(crypto.decrypt(first)).isEqualTo("new-api-token");
}

@Test
void tamperedCiphertextIsRejected() {
    String encrypted = crypto.encrypt("new-api-token");

    assertThatThrownBy(() -> crypto.decrypt(encrypted + "x"))
        .isInstanceOf(IllegalArgumentException.class);
}
```

- [ ] **Step 2: 运行测试确认失败**

```powershell
mvn -f backend/pom.xml -Dtest=SessionCryptoTest,AuthControllerTest test
```

Expected: FAIL on the new security assertions.

- [ ] **Step 3: 实现会话创建、读取和撤销**

`PortalSessionService` 登录成功后执行以下顺序：调用 NewAPI 登录、校验返回的用户 ID、加密 access token、保存会话记录、返回不含 token 的认证状态。读取会话时拒绝已撤销或已过期记录；退出时写入 `revoked_at` 而不是物理删除，保留审计能力。Cookie 使用 `HttpOnly`、`SameSite=Lax`，`Secure` 由配置控制。

`SessionCrypto` 使用 AES-GCM：随机 12 字节 nonce，密文携带 nonce 和认证标签；密钥必须是解码后 32 字节的配置值。禁止日志输出明文 token、密码或完整密文。

- [ ] **Step 4: 实现认证 REST 合约**

接口固定为：

```text
POST /api/auth/sign-in
POST /api/auth/sign-up
GET  /api/auth/status
POST /api/auth/sign-out
```

登录和注册请求使用 Bean Validation；错误响应统一为不泄露 NewAPI 内部细节的结构。`/api/auth/status` 未登录返回 `authenticated:false`，而不是 500。

- [ ] **Step 5: 运行后端认证测试**

```powershell
mvn -f backend/pom.xml -Dtest=SessionCryptoTest,AuthControllerTest test
```

Expected: PASS。

- [ ] **Step 6: 提交**

```powershell
git add backend/src/main/java/io/ztoken/portal/session backend/src/main/java/io/ztoken/portal/auth backend/src/test/java/io/ztoken/portal/session backend/src/test/java/io/ztoken/portal/auth
git commit -m "feat: 完成 Portal 邮箱会话闭环"
```

---

## Task 3: 建立 React 页面壳和中英文国际化

**Files:**
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/components/PublicHeader.tsx`
- Modify: `frontend/src/components/ConsoleLayout.tsx`
- Modify: `frontend/src/i18n/index.ts`
- Modify: `frontend/src/i18n/locales/en.json`
- Modify: `frontend/src/i18n/locales/zh.json`
- Modify: `frontend/src/__tests__/app-shell.test.tsx`
- Modify: `frontend/src/components/__tests__/public-header.test.tsx`
- Modify: `frontend/src/components/__tests__/console-layout.test.tsx`

- [ ] **Step 1: 写路径和导航测试**

测试 `/`、`/models`、`/purchase`、`/sign-in`、`/sign-up`、`/console/dashboard`、`/console/tokens`、`/console/logs`、`/console/profile`、`/console/orders` 都能进入明确页面；公开头部包含五个一级菜单；控制台使用 Semi Design 的 Layout/Menu 组件并包含六个控制台入口。

```tsx
it('renders the five public entries', () => {
  render(<App />)
  expect(screen.getByRole('link', { name: '首页' })).toBeInTheDocument()
  expect(screen.getByRole('link', { name: '模型' })).toBeInTheDocument()
  expect(screen.getByRole('link', { name: '文档' })).toBeInTheDocument()
  expect(screen.getByRole('link', { name: '购买' })).toBeInTheDocument()
  expect(screen.getByRole('link', { name: '控制台' })).toBeInTheDocument()
})
```

- [ ] **Step 2: 运行前端测试确认失败**

```powershell
cd F:\WorkSpace\study\AIProject\Ztoken\portal\frontend
npm test -- --run
```

Expected: FAIL because the purchase and console routes/navigation are not yet present.

- [ ] **Step 3: 实现公开页面壳**

`App.tsx` 保持当前轻量路径分发风格，不在本计划引入新的路由库。公开页面统一包裹 `PublicHeader`；控制台路径统一包裹 `ConsoleLayout`。`/docs` 使用配置的外部 URL；`/purchase` 渲染购买页占位；未识别路径显示 Not Found 页面，而不是静默渲染首页。

`PublicHeader` 使用 Semi Design 的 `Nav`、`Button` 或现有项目等价组件；用户可见文本全部通过 `useTranslation()` 获取。首页可以使用自定义 CSS，但控制台不得引入另一套 UI 组件库。

- [ ] **Step 4: 实现中英文检测和手动切换**

语言优先级固定为：

```text
localStorage 中用户选择 > 浏览器语言 > 英文
```

`zh`、`zh-CN`、`zh-TW`、`zh-HK` 映射中文，其余语言映射英文。切换后写入 localStorage 并立即刷新 i18next；翻译文件使用扁平 JSON，英文源字符串作为 key。

```ts
export function detectPortalLanguage(browserLanguage: string | undefined): 'zh' | 'en' {
  if (browserLanguage?.toLowerCase().startsWith('zh')) return 'zh'
  return 'en'
}
```

- [ ] **Step 5: 运行前端测试和构建**

```powershell
npm test -- --run
npm run build
```

Expected: 全部 PASS，Vite 构建成功。

- [ ] **Step 6: 提交**

```powershell
git add frontend/src
 git commit -m "feat: 建立 Portal 页面壳与中英文切换"
```

---

## Task 4: 实现邮箱登录注册前端

**Files:**
- Modify: `frontend/src/api/auth.ts`
- Modify: `frontend/src/features/auth/SignInPage.tsx`
- Modify: `frontend/src/features/auth/SignUpPage.tsx`
- Modify: `frontend/src/auth/use-auth-status.ts`
- Test: `frontend/src/features/auth/__tests__/sign-in-page.test.tsx`
- Test: `frontend/src/features/auth/__tests__/sign-up-page.test.tsx`

- [ ] **Step 1: 写表单行为测试**

测试：空字段阻止提交；邮箱格式错误显示本地化错误；提交时按钮进入 loading；成功登录跳转控制台；后端 401/409 错误显示安全的服务端消息；密码字段使用 password 类型；注册成功跳转登录页。

```tsx
it('does not submit an invalid email', async () => {
  const user = userEvent.setup()
  render(<SignInPage onAuthenticated={vi.fn()} />)

  await user.type(screen.getByLabelText('邮箱'), 'not-an-email')
  await user.type(screen.getByLabelText('密码'), 'secret123')
  await user.click(screen.getByRole('button', { name: '登录' }))

  expect(fetch).not.toHaveBeenCalled()
  expect(screen.getByText('请输入有效的邮箱地址')).toBeInTheDocument()
})
```

- [ ] **Step 2: 运行认证前端测试确认失败**

```powershell
npm test -- --run
```

Expected: FAIL on the new validation and loading assertions.

- [ ] **Step 3: 实现 API 客户端**

在 `frontend/src/api/auth.ts` 中集中实现 `signIn`、`signUp`、`getAuthStatus`、`signOut`。所有请求使用 `credentials: 'include'`；非 2xx 统一解析 `{message}`，没有安全消息时使用本地化通用错误。API 客户端不保存 token，不读取管理员凭据。

- [ ] **Step 4: 实现 Semi Design 表单页面**

使用 Semi Design 的 `Form`、`Form.Input`、`Button`、`Typography`、`Toast` 等组件。登录字段为邮箱/用户名兼容字段（后端沿用 NewAPI 登录接口需要的 username）和密码；注册字段为用户名、邮箱、密码、确认密码。密码只发送 HTTPS 请求，确认密码仅前端校验不发送。

- [ ] **Step 5: 更新认证状态保护**

`use-auth-status.ts` 调用 `/api/auth/status`。控制台页面未认证时跳转 `/sign-in?returnTo=<encoded-path>`；登录成功只允许跳转到同源且以 `/` 开头的 returnTo，拒绝外部 URL，防止开放重定向。

- [ ] **Step 6: 运行测试和构建**

```powershell
npm test -- --run
npm run build
```

Expected: 全部 PASS，构建成功。

- [ ] **Step 7: 提交**

```powershell
git add frontend/src/api/auth.ts frontend/src/features/auth frontend/src/auth
 git commit -m "feat: 完成 Portal 邮箱登录注册页面"
```

---

## Task 5: 验证单端口打包与基础回归

**Files:**
- Modify: `backend/pom.xml`（仅在现有前端打包配置缺少资源复制时修改）
- Modify: `backend/src/test/java/io/ztoken/portal/web/SpaRouteControllerTest.java`（如现有测试需要补充）
- Modify: `README.md`（补充启动和环境变量说明）

- [ ] **Step 1: 运行前端完整检查**

```powershell
cd F:\WorkSpace\study\AIProject\Ztoken\portal\frontend
npm ci
npm test -- --run
npm run build
```

Expected: 测试全部通过，生成 `dist/`。

- [ ] **Step 2: 运行后端完整测试**

```powershell
cd F:\WorkSpace\study\AIProject\Ztoken\portal
mvn -f backend/pom.xml test
```

Expected: 后端测试全部通过；如果本机未提供 MySQL，必须明确记录失败原因，不得把 H2 测试结果称为 MySQL 验证。

- [ ] **Step 3: 验证 Maven 单端口构建**

```powershell
mvn -f backend/pom.xml clean package
```

Expected: 生成 `backend/target/ztoken-portal-0.1.0-SNAPSHOT.jar`，JAR 内包含 React `dist` 静态资源。

- [ ] **Step 4: 更新运行文档**

README 至少说明以下环境变量及用途：

```text
NEWAPI_BASE_URL
PORTAL_SESSION_KEY
PORTAL_SESSION_TTL
PORTAL_SESSION_SECURE_COOKIE
PORTAL_DB_URL
PORTAL_DB_USERNAME
PORTAL_DB_PASSWORD
```

明确写出：生产必须设置随机 32 字节 Base64 `PORTAL_SESSION_KEY`；Portal 和 NewAPI 使用不同端口；大模型客户端直接请求 NewAPI Relay API。

- [ ] **Step 5: 提交回归验证结果**

```powershell
git add backend/pom.xml backend/src/test README.md
git commit -m "docs: 补充 Portal 基础运行说明"
```

最终报告必须列出前端测试、后端测试、Maven 打包的实际输出；未运行的数据库矩阵验证必须明确标为未完成，不得声称已验证所有数据库兼容性。

---

## 计划自审结果

- 已覆盖：React + Java、单端口打包、公共五个菜单、控制台导航壳、Semi Design、首页自由样式、中英文检测与切换、邮箱登录注册、NewAPI HTTP 复用、不修改 NewAPI 源码。
- 未纳入：GitHub/Google OAuth、PayPal、TRC20-USDT、支付订单、异常审核、完整仪表盘/日志/资料页面；这些属于后续独立计划，避免 Plan A 变成不可独立验收的跨系统任务。
- 认证安全边界已明确：Portal 不向浏览器返回 NewAPI access token，管理员凭据不进入前端，returnTo 仅允许同源路径。
- 当前项目使用 npm 的既有 Portal 构建流程；未改变 `new-api` 要求 Bun 的独立约定。
