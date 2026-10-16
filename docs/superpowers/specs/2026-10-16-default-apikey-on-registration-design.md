# 设计规范：用户注册与首次登录自动创建默认 API Key（default）

## 1. 概述与目标

### 1.1 背景
在当前 Portal 系统中，新用户通过账号密码或邮箱注册后，或者通过第三方 OAuth（GitHub、Google）首次登录后，账户内初始没有 API Key（令牌），用户必须手动进入控制台创建后方可使用。为了提升新用户接入与使用体验，需要在用户注册及首次接入系统后，系统全自动为其创建初始默认 API Key。

### 1.2 目标
- **默认令牌配置**：
  - 名称（`name`）：`"default"`
  - 额度（`unlimited`）：`true`（无限额度，直接共享扣减用户的主账户余额）
  - 剩余额度（`remainingQuota`）：`0`
  - 过期时间（`expiredTime`）：`-1`（永不过期）
  - 分组（`group`）：`"default"`
- **适用场景**：
  - 普通注册流程（`POST /api/auth/register`）成功后自动创建；
  - 第三方 OAuth 登录流程（`POST /api/auth/oauth/{provider}/complete`）首次登录无令牌时自动创建；
  - 登录流程（`POST /api/auth/login`）零令牌自愈兜底（若此前因瞬时网络异常导致创建失败，首次登录时自动补齐）。
- **非功能目标**：
  - 保持高可用，创建令牌失败不阻塞用户的注册与登录主流程；
  - 遵循代码规范：中文注释、中文日志、严格不修改 `newapi` 仓库代码。

---

## 2. 系统架构与核心组件

### 2.1 组件分工
```
+-------------------------------------------------------------+
|                      AuthController                         |
|   /api/auth/register, /api/auth/oauth/.../complete, /login  |
+------------------------------+------------------------------+
                               |
                               v
+-------------------------------------------------------------+
|                   DefaultTokenService                       |
|   - initializeDefaultTokenOnRegister(username, password)    |
|   - ensureDefaultTokenExists(PortalPrincipal)               |
+------------------------------+------------------------------+
                               |
                               v
+-------------------------------------------------------------+
|                      NewApiClient                           |
|   - login(username, password)                               |
|   - getTokens(principal, page, pageSize)                    |
|   - createToken(principal, TokenWriteRequest)               |
|   - logout(principal)                                       |
+-------------------------------------------------------------+
```

### 2.2 详细组件设计

#### 2.2.1 `DefaultTokenService`
在包 `io.ztoken.portal.auth` 中新增专职服务类 `DefaultTokenService`：
- **依赖注入**：`NewApiClient newApiClient`
- **常量定义**：
  - `DEFAULT_TOKEN_NAME = "default"`
  - `DEFAULT_TOKEN_GROUP = "default"`
- **核心方法**：
  1. `void initializeDefaultTokenOnRegister(String username, String password)`：
     - 在普通注册成功后由 `AuthController` 调用；
     - 内部使用注册的账密调用 `newApiClient.login(username, password)` 获取临时用户凭据；
     - 根据返回的 `NewApiLogin` 构造临时 `PortalPrincipal`；
     - 调用 `ensureDefaultTokenExists(principal)`；
     - 在 `finally` 块中调用 `newApiClient.logout(principal)` 清理临时会话；
     - 捕获任何异常，记录中文警告日志，保证不影响注册主结果。
  2. `void ensureDefaultTokenExists(PortalPrincipal principal)`：
     - 供注册流程、OAuth 流程以及登录兜底流程统一调用；
     - 查询用户令牌列表：`TokenList tokens = newApiClient.getTokens(principal, 1, 1)`；
     - 检查 `tokens.total() == 0`：
       - 若为 0，则构造 `TokenWriteRequest("default", true, 0L, -1L, "default")`，调用 `newApiClient.createToken(principal, request)`；
       - 打印中文日志：`log.info("已成功为用户 {} 自动创建默认 API Key: default", principal.username())`；
       - 若已有令牌，则直接跳过，保证幂等。
     - 捕获任何异常，记录中文警告日志，不向上抛出。

#### 2.2.2 `AuthController` 增强
- 注入 `DefaultTokenService`；
- 在 `register` 方法中：在 `newApiClient.register(...)` 成功后，调用 `defaultTokenService.initializeDefaultTokenOnRegister(...)`；
- 在 `completeOAuth` 方法中：在 `newApiClient.completeOAuth(...)` 拿到 `NewApiLogin` 之后，将凭据转换为 `PortalPrincipal` 并调用 `defaultTokenService.ensureDefaultTokenExists(principal)`；
- 在 `login` 方法中：在 `newApiClient.login(...)` 拿到 `NewApiLogin` 之后，转换为 `PortalPrincipal` 并调用 `defaultTokenService.ensureDefaultTokenExists(principal)`。

---

## 3. 详细流程与交互

### 3.1 账号密码/邮箱注册流程
1. 前端提交 `POST /api/auth/register`，包含用户名、邮箱、密码及验证码；
2. `AuthController.register` 调用 `newApiClient.register` 在 NewAPI 中创建用户；
3. 调用 `defaultTokenService.initializeDefaultTokenOnRegister(username, password)`；
4. `DefaultTokenService` 登录该用户获取会话凭证，通过 `getTokens` 判断是否需要创建，并调用 `createToken` 创建 `default` 令牌；
5. 在 `finally` 块中安全注销临时会话；
6. `AuthController` 返回 `204 No Content`，前端跳转登录界面。

### 3.2 第三方 OAuth 登录流程
1. 前端提交 `POST /api/auth/oauth/{provider}/complete`；
2. `AuthController.completeOAuth` 调用 `newApiClient.completeOAuth` 获得 `NewApiLogin`；
3. 提取凭据为 `PortalPrincipal`，调用 `defaultTokenService.ensureDefaultTokenExists(principal)`；
4. 若此 OAuth 用户为初次接入（`total == 0`），则自动生成 `default` 令牌；若为老用户则不重复创建；
5. 继续执行 `withPortalSession(login)`，设置 Cookie 并返回 `204 No Content`。

### 3.3 登录自愈兜底机制
1. 用户在登录页面提交 `POST /api/auth/login`；
2. 登录成功获得 `NewApiLogin`，在建立本地 Session 之前调用 `defaultTokenService.ensureDefaultTokenExists(principal)`；
3. 若检测到该账号下令牌总数为 0，立即为其补建 `default` 令牌；若已有令牌则秒级跳过；
4. 保证了在极端网络丢包场景下的 100% 自动自愈。

---

## 4. 异常处理与容错保障

1. **非阻塞设计**：
   - 创建 API Key 属于增强型配套服务，绝对不能影响主认证逻辑。
   - `DefaultTokenService` 所有外部调用（登录、查询、创建、登出）均做安全捕获：
   ```java
   try {
       // 创建逻辑
   } catch (Exception e) {
       log.warn("为用户 {} 自动创建默认令牌失败: {}", username, e.getMessage());
   }
   ```
2. **凭据安全**：
   - 注册时使用的临时登录会话仅存在于该次 HTTP 请求的堆内存中，并在 `finally` 中显式向 NewAPI 发起 `logout`；
   - 绝不向前端暴露该临时凭据。

---

## 5. 测试与验证策略

### 5.1 单元测试（`DefaultTokenServiceTest`）
- 验证普通注册触发：成功登录并调用创建令牌，且执行登出；
- 验证幂等性：当用户已有 Token 时，不触发 `createToken`；
- 验证容错性：当 NewAPI 调用发生异常时，不抛出异常打断上层流程。

### 5.2 控制器集成测试（`AuthControllerTest`）
- 验证 `register` 请求在新用户注册成功后会向 MockWebServer 请求登录并创建令牌；
- 验证 `completeOAuth` 请求在首次登录时触发令牌检查并成功创建令牌。

### 5.3 构建验证
- 执行全量后端测试：
  ```powershell
  mvn -f portal/backend/pom.xml test
  ```
  确保既有测试与新增测试全部通过。
