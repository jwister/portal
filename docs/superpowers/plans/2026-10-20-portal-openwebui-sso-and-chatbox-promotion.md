# Portal 与 Open WebUI 登录态共享（根域 Cookie SSO）及 Chatbox 推广实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现用户在 Portal (`ztoken.cc`) 登录后，访问 Open WebUI (`chatbox.ztoken.cc`) 自动同步登录状态（免密秒进），同时保证 Open WebUI 独立访问与未登录访客体验完全不受干扰；在 Portal 首页与全站导航增加高颜值、有设计感的 “Chatbox · 在线聊智能体” 推广入口。

**Architecture:** 依托根域名 `.ztoken.cc` 的 Cookie 共享机制，Portal 签发与清理 `PORTAL_SESSION` 时统一设置根域；Portal 后端提供轻量会话验证端点；Open WebUI 后端新增静默同步接口并在前端启动时探测同步，无 Cookie 时优雅降级为原生访客模式；Portal 前端打造流光质感的专属推广卡片与顶栏联动入口。

**Tech Stack:** Java 17, Spring Boot 3, React 18, Vite, TypeScript, FastAPI (Python), SvelteKit, Tailwind CSS.

---

### Task 1: Portal 后端 - Cookie 主域作用域配置与注销支持

**Files:**
- Modify: `portal/backend/src/main/java/io/ztoken/portal/config/PortalProperties.java`
- Modify: `portal/backend/src/main/resources/application.yml`
- Modify: `portal/backend/src/main/java/io/ztoken/portal/auth/AuthController.java`
- Test: `portal/backend/src/test/java/io/ztoken/portal/auth/AuthControllerTest.java`

- [ ] **Step 1: 编写单元测试验证 Cookie 包含 Domain 属性**

在 `portal/backend/src/test/java/io/ztoken/portal/auth/AuthControllerTest.java` 中增加针对 `sessionCookieDomain` 的测试用例：

```java
@Test
void loginSetsCookieWithConfiguredDomain() {
    when(properties.getSessionCookieDomain()).thenReturn(".ztoken.cc");
    when(properties.getSessionTtl()).thenReturn(Duration.ofDays(7));
    when(properties.isSessionSecureCookie()).thenReturn(false);
    when(newApiClient.login("alice", "secret123")).thenReturn(testLogin);
    when(sessions.create(testLogin)).thenReturn(new PortalSession("test-id-1234567890123456789012345678901234567890", 1L, "alice", "enc-token", "enc-refresh", "sid", Instant.now().plusSeconds(900), Instant.now().plusSeconds(604800), Instant.now()));

    ResponseEntity<Void> response = controller.login(new LoginRequest("alice", "secret123"));

    List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
    assertThat(cookies).isNotNull();
    assertThat(cookies.get(0)).contains("Domain=.ztoken.cc");
}
```

- [ ] **Step 2: 运行测试验证失败**

运行：
```powershell
mvn -f portal/backend/pom.xml test -Dtest=AuthControllerTest#loginSetsCookieWithConfiguredDomain
```
预期：编译失败或测试失败，提示 `getSessionCookieDomain` 方法不存在或未设置 Domain。

- [ ] **Step 3: 修改 `PortalProperties.java` 与 `application.yml`**

在 `PortalProperties.java` 中增加字段与 getter：
```java
private String sessionCookieDomain;

public String getSessionCookieDomain() {
    return sessionCookieDomain;
}

public void setSessionCookieDomain(String sessionCookieDomain) {
    this.sessionCookieDomain = sessionCookieDomain;
}
```

在 `application.yml` 的 `portal:` 节点下增加配置：
```yaml
portal:
  session-cookie-domain: ${PORTAL_SESSION_COOKIE_DOMAIN:}
```

- [ ] **Step 4: 修改 `AuthController.java` 挂载 Domain 属性**

在 `AuthController.java` 中的 `withPortalSession` 方法：
```java
private ResponseEntity<Void> withPortalSession(NewApiLogin login) {
    PortalSession session = sessions.create(login);
    ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from("PORTAL_SESSION", session.getId())
            .httpOnly(true)
            .secure(properties.isSessionSecureCookie())
            .sameSite("Lax")
            .path("/")
            .maxAge(properties.getSessionTtl());
    if (properties.getSessionCookieDomain() != null && !properties.getSessionCookieDomain().isBlank()) {
        builder.domain(properties.getSessionCookieDomain());
    }
    return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, builder.build().toString()).build();
}
```

在 `signOut` 方法中：
```java
ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from("PORTAL_SESSION", "")
        .httpOnly(true)
        .secure(properties.isSessionSecureCookie())
        .sameSite("Lax")
        .path("/")
        .maxAge(0);
if (properties.getSessionCookieDomain() != null && !properties.getSessionCookieDomain().isBlank()) {
    builder.domain(properties.getSessionCookieDomain());
}
return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, builder.build().toString()).build();
```

- [ ] **Step 5: 运行测试验证通过**

运行：
```powershell
mvn -f portal/backend/pom.xml test -Dtest=AuthControllerTest
```
预期：所有测试 PASS。

- [ ] **Step 6: 提交代码**

```bash
git -C portal add backend/src/main/java/io/ztoken/portal/config/PortalProperties.java backend/src/main/resources/application.yml backend/src/main/java/io/ztoken/portal/auth/AuthController.java backend/src/test/java/io/ztoken/portal/auth/AuthControllerTest.java
git -C portal commit -m "feat(auth): 支持配置 PORTAL_SESSION 跨主域 Cookie 作用域"
```

---

### Task 2: Portal 后端 - 新增轻量会话验证端点 `GET /api/auth/session/info`

**Files:**
- Create: `portal/backend/src/main/java/io/ztoken/portal/auth/SessionUserInfo.java`
- Modify: `portal/backend/src/main/java/io/ztoken/portal/auth/AuthController.java`
- Test: `portal/backend/src/test/java/io/ztoken/portal/auth/AuthControllerTest.java`

- [ ] **Step 1: 编写测试用例**

在 `AuthControllerTest.java` 中增加针对 `sessionInfo` 的测试用例：
```java
@Test
void sessionInfoReturnsUserWhenSessionValid() {
    String sessionId = "valid-session-id-12345678901234567890123456789012";
    PortalPrincipal principal = new PortalPrincipal(sessionId, 100L, "alice", "token", "refresh", "sid", Instant.now().plusSeconds(900));
    when(sessions.withAuthenticatedPrincipal(eq(sessionId), any())).thenAnswer(invocation -> {
        java.util.function.Function<PortalPrincipal, SessionUserInfo> op = invocation.getArgument(1);
        return op.apply(principal);
    });
    when(newApiClient.getProfile(principal)).thenReturn(new Profile(100L, "alice", "Alice", "alice@example.com", "zh-CN"));

    SessionUserInfo info = controller.sessionInfo(sessionId);

    assertThat(info.userId()).isEqualTo(100L);
    assertThat(info.username()).isEqualTo("alice");
    assertThat(info.email()).isEqualTo("alice@example.com");
}

@Test
void sessionInfoThrowsUnauthenticatedWhenSessionNull() {
    assertThatThrownBy(() -> controller.sessionInfo(null))
            .isInstanceOf(UnauthenticatedException.class);
}
```

- [ ] **Step 2: 运行测试验证失败**

运行：
```powershell
mvn -f portal/backend/pom.xml test -Dtest=AuthControllerTest#sessionInfoReturnsUserWhenSessionValid
```
预期：FAIL，`SessionUserInfo` 或 `sessionInfo` 方法未定义。

- [ ] **Step 3: 创建 `SessionUserInfo.java`**

创建 `portal/backend/src/main/java/io/ztoken/portal/auth/SessionUserInfo.java`：
```java
package io.ztoken.portal.auth;

/**
 * 专供 SSO 与内部应用获取当前会话用户基础身份信息的数据对象。
 */
public record SessionUserInfo(long userId, String username, String email) {
}
```

- [ ] **Step 4: 在 `AuthController.java` 中实现 `sessionInfo` 端点**

在 `AuthController.java` 中新增端点：
```java
@GetMapping("/session/info")
public SessionUserInfo sessionInfo(@CookieValue(value = "PORTAL_SESSION", required = false) String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
        throw new io.ztoken.portal.session.UnauthenticatedException();
    }
    return sessions.withAuthenticatedPrincipal(sessionId, principal -> {
        io.ztoken.portal.console.Profile profile = newApiClient.getProfile(principal);
        String email = profile.email();
        if (email == null || email.isBlank()) {
            email = principal.username() + "@ztoken.cc";
        }
        return new SessionUserInfo(profile.id(), profile.username(), email);
    });
}
```

- [ ] **Step 5: 运行测试验证通过**

运行：
```powershell
mvn -f portal/backend/pom.xml test -Dtest=AuthControllerTest
```
预期：PASS。

- [ ] **Step 6: 提交代码**

```bash
git -C portal add backend/src/main/java/io/ztoken/portal/auth/SessionUserInfo.java backend/src/main/java/io/ztoken/portal/auth/AuthController.java backend/src/test/java/io/ztoken/portal/auth/AuthControllerTest.java
git -C portal commit -m "feat(auth): 新增会话信息端点 GET /api/auth/session/info 供 SSO 验证"
```

---

### Task 3: Open WebUI 后端 - 新增静默同步接口 `GET /api/v1/auths/portal-sync`

**Files:**
- Modify: `open-webui/backend/open_webui/routers/auths.py`
- Modify: `open-webui/backend/open_webui/config.py`
- Modify: `open-webui/docker-compose.yaml`

- [ ] **Step 1: 在 `config.py` 增加 `PORTAL_BASE_URL` 配置**

在 `open-webui/backend/open_webui/config.py` 中增加：
```python
PORTAL_BASE_URL = os.getenv('PORTAL_BASE_URL', 'http://ztoken-portal:8084').rstrip('/')
```

- [ ] **Step 2: 在 `routers/auths.py` 中实现 `/portal-sync` 端点**

在 `open-webui/backend/open_webui/routers/auths.py` 中增加：
```python
@router.get('/portal-sync', response_model=Optional[SessionUserResponse])
async def portal_sync(
    request: Request,
    response: Response,
    db: AsyncSession = Depends(get_async_session),
):
    portal_session = request.cookies.get('PORTAL_SESSION')
    if not portal_session:
        response.status_code = status.HTTP_204_NO_CONTENT
        return None

    try:
        from open_webui.env import AIOHTTP_CLIENT_SESSION_SSL
        import aiohttp
        async with aiohttp.ClientSession(timeout=aiohttp.ClientTimeout(total=5)) as session:
            cookies = {'PORTAL_SESSION': portal_session}
            async with session.get(f'{PORTAL_BASE_URL}/api/auth/session/info', cookies=cookies, ssl=AIOHTTP_CLIENT_SESSION_SSL) as resp:
                if resp.status != 200:
                    response.status_code = status.HTTP_204_NO_CONTENT
                    return None
                user_info = await resp.json()
    except Exception as e:
        log.warning(f'Portal session verification failed: {e}')
        response.status_code = status.HTTP_204_NO_CONTENT
        return None

    email = str(user_info.get('email') or '').lower().strip()
    username = str(user_info.get('username') or '')
    if not email:
        response.status_code = status.HTTP_204_NO_CONTENT
        return None

    user = await Users.get_user_by_email(email, db=db)
    if not user:
        try:
            user = await signup_handler(
                request,
                email,
                str(uuid.uuid4()),
                username or email,
                db=db,
                source='portal_sso',
            )
        except Exception as e:
            user = await Users.get_user_by_email(email, db=db)
            if not user:
                log.error(f'Failed to create user during portal-sync: {e}')
                response.status_code = status.HTTP_204_NO_CONTENT
                return None

    return await create_session_response(request, user, db, response, set_cookie=True, source='portal_sso')
```

- [ ] **Step 3: 更新 `docker-compose.yaml` 注入环境变量**

在 `open-webui/docker-compose.yaml` 的 `environment` 中补充：
```yaml
      - 'PORTAL_BASE_URL=${PORTAL_BASE_URL:-http://host.docker.internal:8084}'
```

- [ ] **Step 4: 提交代码**

```bash
git -C open-webui add backend/open_webui/config.py backend/open_webui/routers/auths.py docker-compose.yaml
git -C open-webui commit -m "feat(auth): 新增 GET /api/v1/auths/portal-sync 端点实现 Portal 会话静默同步"
```

---

### Task 4: Open WebUI 前端 - 应用启动时自动触发静默同步

**Files:**
- Modify: `open-webui/src/routes/+layout.svelte`

- [ ] **Step 1: 在 `+layout.svelte` 中增加静默同步探测**

在 `open-webui/src/routes/+layout.svelte` 中，位于 `if (localStorage.token)` 的 else 分支或未登录检查段落加入静默同步：
```javascript
if (!localStorage.token) {
    try {
        const syncRes = await fetch(`${WEBUI_API_BASE_URL}/auths/portal-sync`, {
            method: 'GET',
            credentials: 'include'
        });
        if (syncRes.status === 200) {
            const sessionUser = await syncRes.json();
            if (sessionUser?.token) {
                localStorage.token = sessionUser.token;
                await user.set(sessionUser);
                await refreshChatList(sessionUser.token);
            }
        }
    } catch (err) {
        // 静默失败，保持原生访客模式
    }
}
```

- [ ] **Step 2: 验证独立访问体验未被破坏**

当没有 `PORTAL_SESSION` Cookie 时，`portal-sync` 返回 204，前端捕获后不产生任何 toast，不跳转，继续渲染现有的未登录对话界面。

- [ ] **Step 3: 提交代码**

```bash
git -C open-webui add src/routes/+layout.svelte
git -C open-webui commit -m "feat(auth): 前端初始化支持静默同步 Portal 登录凭据"
```

---

### Task 5: Portal 前端 - 首页打造 “Chatbox · 在线聊智能体” 专属推广卡片

**Files:**
- Modify: `portal/frontend/src/features/home/HomePage.tsx`
- Modify: `portal/frontend/src/features/home/home.css` (或全局对应样式文件)
- Modify: `portal/frontend/src/i18n/locales/zh-CN.json`
- Modify: `portal/frontend/src/i18n/locales/en-US.json`
- Test: `portal/frontend/src/features/home/__tests__/home-page.test.tsx`

- [ ] **Step 1: 补充多语言翻译配置**

在 `portal/frontend/src/i18n/locales/zh-CN.json` 中增加：
```json
"chatbox": {
  "tag": "✨ 官方功能 · 免配置即开即聊",
  "title": "Chatbox · 在线聊智能体",
  "desc": "无需配置客户端与 API Key，即开即聊。聚合 GPT-4o、Claude 3.5、Gemini 1.5 及全系列智能体，开启沉浸式智能对话体验。",
  "cta": "立即在线体验",
  "models": {
    "gpt4o": "GPT-4o 顶尖智能",
    "claude": "Claude 3.5 Sonnet",
    "deepseek": "DeepSeek-R1 深度推理",
    "agents": "智能体聚合中心"
  }
}
```
并在 `en-US.json` 中配置对应英文。

- [ ] **Step 2: 编写测试用例 `home-page.test.tsx`**

在 `home-page.test.tsx` 中增加断言验证推广卡片正常渲染及目标跳转链接：
```tsx
it('renders the chatbox showcase card linking to openwebui', () => {
  renderWithProviders(<HomePage />)
  const link = screen.getByRole('link', { name: /立即在线体验/i })
  expect(link).toBeInTheDocument()
  expect(link).toHaveAttribute('href', 'https://chatbox.ztoken.cc')
  expect(link).toHaveAttribute('target', '_blank')
})
```

- [ ] **Step 3: 在 `HomePage.tsx` 中创建 Chatbox 推广卡片组件**

在 `HomePage.tsx` 中增加 `ChatboxShowcase` 组件：
```tsx
function ChatboxShowcase() {
  const { t } = useTranslation()
  return (
    <section className="reference-section reference-chatbox-section" aria-label={t('chatbox.title')}>
      <div className="reference-chatbox-card">
        <div className="reference-chatbox-glow" aria-hidden="true" />
        <div className="reference-chatbox-content">
          <div className="reference-chatbox-badge">
            <span>{t('chatbox.tag')}</span>
          </div>
          <h2>{t('chatbox.title')}</h2>
          <p>{t('chatbox.desc')}</p>
          <div className="reference-chatbox-pills">
            <span className="reference-pill pill-gpt">{t('chatbox.models.gpt4o')}</span>
            <span className="reference-pill pill-claude">{t('chatbox.models.claude')}</span>
            <span className="reference-pill pill-deepseek">{t('chatbox.models.deepseek')}</span>
            <span className="reference-pill pill-agent">{t('chatbox.models.agents')}</span>
          </div>
          <div className="reference-chatbox-actions">
            <a
              href="https://chatbox.ztoken.cc"
              target="_blank"
              rel="noopener noreferrer"
              className="reference-chatbox-cta"
            >
              <span>{t('chatbox.cta')}</span>
              <svg viewBox="0 0 20 20" fill="currentColor" width="16" height="16" aria-hidden="true">
                <path fillRule="evenodd" d="M3 10a.75.75 0 0 1 .75-.75h10.638L10.23 5.29a.75.75 0 1 1 1.04-1.08l5.5 5.25a.75.75 0 0 1 0 1.08l-5.5 5.25a.75.75 0 1 1-1.04-1.08l4.158-3.96H3.75A.75.75 0 0 1 3 10Z" clipRule="evenodd" />
              </svg>
            </a>
          </div>
        </div>
      </div>
    </section>
  )
}
```
并在 `HomePage` 渲染树中置于 `reference-hero` 紧下方。

- [ ] **Step 4: 编写 CSS 样式实现微流光边框与玻璃拟态质感**

```css
.reference-chatbox-section {
  padding: 0 1.5rem 3rem;
  max-width: 1200px;
  margin: 0 auto;
}
.reference-chatbox-card {
  position: relative;
  border-radius: 1.25rem;
  padding: 2.5rem 2rem;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.85), rgba(240, 246, 255, 0.7));
  border: 1px solid rgba(59, 130, 246, 0.25);
  box-shadow: 0 20px 40px -15px rgba(37, 99, 235, 0.12);
  backdrop-filter: blur(12px);
  overflow: hidden;
}
:root[data-theme='dark'] .reference-chatbox-card {
  background: linear-gradient(135deg, rgba(17, 24, 39, 0.8), rgba(30, 41, 59, 0.6));
  border: 1px solid rgba(99, 102, 241, 0.3);
  box-shadow: 0 20px 40px -15px rgba(0, 0, 0, 0.6);
}
.reference-chatbox-badge {
  display: inline-flex;
  font-size: 0.8125rem;
  font-weight: 600;
  color: #2563eb;
  background: rgba(37, 99, 235, 0.1);
  padding: 0.25rem 0.75rem;
  border-radius: 9999px;
  margin-bottom: 1rem;
}
.reference-chatbox-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin: 1.25rem 0 1.75rem;
}
.reference-pill {
  font-size: 0.75rem;
  font-weight: 500;
  padding: 0.25rem 0.625rem;
  border-radius: 0.5rem;
  background: rgba(0, 0, 0, 0.04);
  border: 1px solid rgba(0, 0, 0, 0.06);
}
:root[data-theme='dark'] .reference-pill {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
}
.reference-chatbox-cta {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  background: linear-gradient(135deg, #2563eb, #4f46e5);
  color: #ffffff;
  font-weight: 600;
  padding: 0.75rem 1.75rem;
  border-radius: 0.75rem;
  text-decoration: none;
  box-shadow: 0 4px 14px rgba(37, 99, 235, 0.35);
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}
.reference-chatbox-cta:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(37, 99, 235, 0.45);
}
```

- [ ] **Step 5: 运行前端单元测试验证通过**

运行：
```powershell
npm --prefix portal/frontend test -- -t "chatbox"
```
预期：PASS。

- [ ] **Step 6: 提交代码**

```bash
git -C portal add frontend/src/features/home/HomePage.tsx frontend/src/features/home/__tests__/home-page.test.tsx frontend/src/i18n/locales/
git -C portal commit -m "feat(home): 首页新增 Chatbox 在线聊智能体专属推广卡片"
```

---

### Task 6: Portal 前端 - 全站公共顶栏入口与高亮角标增加

**Files:**
- Modify: `portal/frontend/src/components/PublicHeader.tsx`
- Modify: `portal/frontend/src/components/__tests__/public-header.test.tsx`
- Modify: `portal/frontend/src/i18n/locales/zh-CN.json`
- Modify: `portal/frontend/src/i18n/locales/en-US.json`

- [ ] **Step 1: 增加多语言词条**

在 `zh-CN.json` 的 `nav` 节点中增加：`"chatbox": "Chatbox"`。

- [ ] **Step 2: 编写测试断言顶栏包含 Chatbox 导航项**

在 `public-header.test.tsx` 中验证顶栏导航中包含目标链接及在新窗口打开的属性。

- [ ] **Step 3: 修改 `PublicHeader.tsx`**

在 `PublicHeader.tsx` 中的导航项列表内添加外部链接项：
```tsx
const navItems: Array<[string, string, boolean?]> = [
  ['/', t('nav.home')],
  ['/models', t('nav.models')],
  ['https://chatbox.ztoken.cc', t('nav.chatbox'), true],
  ['/docs/guides/quick-start', t('nav.docs')]
]
```
并在链接渲染逻辑中为带有外部标识的链接渲染特殊的 `AI` 高亮角标并支持新窗口跳转。

- [ ] **Step 4: 运行前端测试验证通过**

运行：
```powershell
npm --prefix portal/frontend test -- -t "PublicHeader"
```
预期：PASS。

- [ ] **Step 5: 提交代码**

```bash
git -C portal add frontend/src/components/PublicHeader.tsx frontend/src/components/__tests__/public-header.test.tsx frontend/src/i18n/locales/
git -C portal commit -m "feat(nav): 公共顶栏增加 Chatbox 直达导航项与高亮角标"
```

---

### Task 7: 完整集成构建与全链路验证

**Files:**
- Modify: 无代码修改，执行全量自动化验证

- [ ] **Step 1: 验证 Portal 后端完整测试套件**

运行：
```powershell
mvn -f portal/backend/pom.xml clean test
```
预期：BUILD SUCCESS，所有测试无报错。

- [ ] **Step 2: 验证 Portal 前端完整构建与类型检查**

运行：
```powershell
npm --prefix portal/frontend run build
```
预期：TypeScript 编译无报错，Vite 打包成功生成静态资源。

- [ ] **Step 3: 整体提交与状态同步**

```bash
git -C portal status
git -C open-webui status
```
确认工作区干净，所有新功能与测试均已纳入版本控制。
