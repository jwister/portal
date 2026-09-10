# Portal GitHub 与 Google OAuth 设计

## 目标

让客户从 Portal 登录页使用 GitHub 或 Google 登录，并在成功后获得与密码登录相同的 Portal 服务端会话；NewAPI 继续作为唯一的用户与 OAuth 账号绑定权威。

## 范围与前提

- 只支持 `github` 与 `oidc` 两个 NewAPI OAuth provider；Google 通过 NewAPI 的 OIDC 配置接入。
- NewAPI 原生网页不保留这两个 OAuth 登录入口。NewAPI 的全局 `ServerAddress` 设置为 Portal 公网地址。
- Portal 不保存密码、GitHub/Google access token 或 OAuth client secret；它只加密保存 NewAPI 在登录完成后签发的 access token，沿用现有 `PortalSessionService`。
- 已有的账号、绑定关系、注册开关和禁用状态均由 NewAPI 的 `/api/oauth/{provider}` 执行。

## 架构与数据流

1. Portal 登录页调用 `GET /api/auth/oauth/providers`。BFF 读取 NewAPI `/api/status`，仅投影已启用 provider 的公开启动参数：GitHub client ID，或 OIDC 的名称、client ID 与授权端点。
2. 用户选择 provider 后，前端调用 `POST /api/auth/oauth/{provider}/state`。BFF 只允许 `github`、`oidc`，并将 `{provider, intent:"login"}` 转交给 NewAPI `/api/oauth/state`；返回的短期一次性 `flow_token` 作为 OAuth `state`，同时写入只限当前浏览器的 HttpOnly、SameSite=Lax 短期 cookie。完成回调前 BFF 必须以常量时间比较 cookie 与 state，随后清除 cookie，阻止跨浏览器重放和登录会话置换。
3. 前端跳转到 GitHub 或 Google 的授权地址，回调地址固定为 Portal 的 `/oauth/github` 或 `/oauth/oidc`。
4. 回调页校验 provider 和回调查询参数，调用 `POST /api/auth/oauth/{provider}/complete`。BFF 服务器端向 NewAPI `GET /api/oauth/{provider}` 透传 `code`、`state`、`error` 与 `error_description`，不让浏览器直接持有 NewAPI 的认证响应。
5. NewAPI 成功返回其标准登录包后，Portal 建立现有的 HttpOnly `PORTAL_SESSION`，并重定向到受保护的控制台。失败时回调页显示安全的错误文字并回到登录页。

## 后端边界

- `NewApiClient` 获得面向 OAuth 的三个明确方法：读取公开 provider 状态、创建 OAuth state、完成 OAuth 回调。它不提供任意 URL、任意 path 或任意 header 的代理能力。
- `AuthController` 负责 provider 白名单、请求格式、Portal cookie 写入和统一错误响应；OAuth 成功路径必须复用密码登录创建 cookie 的逻辑，保证 SameSite、Secure、TTL 一致。
- 上游的 `refresh_token`、`Set-Cookie` 和错误响应体不得复制到 Portal 响应；日志也不得记录授权码、state 或令牌。

## 前端边界

- 登录页仅在 provider 已启用且启动参数完整时显示对应按钮。
- GitHub URL 使用 `scope=user:email`；OIDC URL 使用 `openid profile email`、`response_type=code`，且 callback URL 由当前 Portal origin 生成。
- `/oauth/:provider` 只接受 `github` 与 `oidc`；回调完成后使用 `location.replace` 去除地址栏中的 code/state，不保留可重放信息。
- 所有新增可见文本进入中英文 locale 文件，并为加载、不可用和失败场景提供可访问状态。

## 配置与部署

- 在 NewAPI 管理设置中启用 GitHub OAuth，并配置 Google OIDC 的授权端点、token endpoint、userinfo endpoint、client ID 与 secret。
- GitHub OAuth App 回调为 `https://<portal-domain>/oauth/github`；Google OAuth Client 回调为 `https://<portal-domain>/oauth/oidc`。
- 将 NewAPI `ServerAddress` 配置为 `https://<portal-domain>`，以确保 OIDC token 交换使用匹配的 `redirect_uri`。
- Portal 不新增 OAuth secret 配置。它仅需要现有 `PORTAL_NEW_API_BASE_URL`、会话密钥及 HTTPS Secure cookie 配置。

## 验收与测试

- 后端集成测试验证：公开 provider 投影不泄露 secret、state 请求只转发允许的 provider、成功回调写入 Portal cookie、上游错误不泄露且不写会话、错误回调也不写会话。
- 前端测试验证：按状态显示 provider 按钮、发起跳转包含正确的回调与 state、回调成功跳转控制台、无效 provider 与失败回调安全返回登录页。
- 完整 Maven 构建验证后端测试和打包时的前端构建。
