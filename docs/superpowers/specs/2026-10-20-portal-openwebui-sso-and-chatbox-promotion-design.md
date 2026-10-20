# 设计规范：Portal 与 Open WebUI 登录态共享（根域 Cookie SSO）与 Chatbox 首页推广卡片

## 1. 概述与目标

### 1.1 背景
当前平台包含两个核心 Web 服务：
1. **Portal**（`ztoken.cc`）：基于 Spring Boot + React 的用户控制台与 API 额度管理中心，依托 New-API 作为主账号体系。
2. **Open WebUI**（`chatbox.ztoken.cc`）：基于 FastAPI + SvelteKit 的大模型与智能体聊天界面，支持独立访问。

用户在 Portal 登录后，期望能够无缝前往 Open WebUI 开启模型对话，无需再次输入账号密码；同时必须保证 Open WebUI 的独立访问不受任何干扰（未登录访客仍能正常浏览界面并在发消息时弹出登录框）。此外，需要在 Portal 首页与全站导航增加具有设计感的“Chatbox 在线聊智能体”推广入口。

### 1.2 目标与非功能性要求
- **单点登录（SSO）体验**：
  - 用户在 `ztoken.cc` 登录后，访问 `chatbox.ztoken.cc` 时自动静默同步登录状态，秒级进入对话，URL 无杂质参数。
  - 用户未在 `ztoken.cc` 登录或直接独立输入 `chatbox.ztoken.cc` 访问时，保持 Open WebUI 原生访客模式，不阻断、不报错、不出现多余弹窗。
- **首页与导航推广（Chatbox Promotion）**：
  - 在 Portal 首页设计高质感、高颜值的专属特色推广区块（科技微流光边框、玻璃拟态、模型标签阵列、明确行动号召）。
  - 在全站公共顶部导航栏（`PublicHeader`）增加带有高亮标识的 Chatbox 入口。
- **安全性与解耦性**：
  - 维持现有账号安全体系，`PORTAL_SESSION` 维持 HttpOnly 与 SameSite 保护。
  - Open WebUI 与 Portal 保持松耦合，单点登录同步接口失败时平滑降级为访客模式。

---

## 2. 总体架构与交互时序

```mermaid
sequenceDiagram
    autonumber
    actor User as 用户浏览器
    participant Portal_FE as Portal 前端 (ztoken.cc)
    participant Portal_BE as Portal 后端 (8084)
    participant OWUI_FE as Open WebUI 前端 (chatbox.ztoken.cc)
    participant OWUI_BE as Open WebUI 后端 (8080)

    rect rgb(240, 248, 255)
    Note over User, Portal_BE: 阶段一：用户在 Portal 登录
    User->>Portal_BE: 登录 (POST /api/auth/login)
    Portal_BE-->>User: Set-Cookie: PORTAL_SESSION=xxx; Domain=.ztoken.cc; Path=/; HttpOnly
    end

    rect rgb(255, 250, 240)
    Note over User, OWUI_BE: 阶段二：用户访问 Chatbox (首页点击或直接输入域名)
    User->>OWUI_FE: 打开 https://chatbox.ztoken.cc (localStorage.token 为空)
    OWUI_FE->>OWUI_BE: GET /api/v1/auths/portal-sync (携带 .ztoken.cc 下的 PORTAL_SESSION Cookie)
    
    alt 场景 A：Cookie 存在且有效 (Portal 已登录)
        OWUI_BE->>Portal_BE: GET /api/auth/session/info (携带 PORTAL_SESSION)
        Portal_BE-->>OWUI_BE: 返回 { userId, username, email }
        OWUI_BE->>OWUI_BE: 本地查找/自动建号，生成 Open WebUI JWT Token
        OWUI_BE-->>OWUI_FE: 200 OK { token, user }
        OWUI_FE->>OWUI_FE: 写入 localStorage.token，自动完成登录，进入聊天
    else 场景 B：Cookie 不存在或无效 (Portal 未登录 / 独立访客)
        OWUI_BE-->>OWUI_FE: 204 No Content
        OWUI_FE->>OWUI_FE: 忽略并保持普通访客模式 (可看模型，发消息时弹窗)
    end
    end
```

---

## 3. 详细设计与改动范围

### 3.1 Portal 后端（Spring Boot）

#### 3.1.1 Cookie 根域配置支持
- **配置属性**：
  - 在 `PortalProperties.java` 中新增 `sessionCookieDomain` 字段（配置项：`portal.session-cookie-domain`，环境变量：`PORTAL_SESSION_COOKIE_DOMAIN`，默认为空）。
  - 在生产环境配置为 `.ztoken.cc`，在本地开发或单元测试时保持为空（避免本地 localhost 下域名匹配报错）。
- **Cookie 签发与清理调整**：
  - 在 `AuthController.java` 中的 `withPortalSession`：
    ```java
    ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from("PORTAL_SESSION", session.getId())
            .httpOnly(true)
            .secure(properties.isSessionSecureCookie())
            .sameSite("Lax")
            .path("/")
            .maxAge(properties.getSessionTtl());
    if (properties.getSessionCookieDomain() != null && !properties.getSessionCookieDomain().isBlank()) {
        builder.domain(properties.getSessionCookieDomain());
    }
    ```
  - 在 `AuthController.java` 中的 `signOut`：
    清除 Cookie 时同样挂载 `properties.getSessionCookieDomain()`，确保子域与主域的 Cookie 同步失效。

#### 3.1.2 新增会话信息端点 (`GET /api/auth/session/info`)
- **路径**：`GET /api/auth/session/info`
- **入参**：从 Cookie 中读取 `PORTAL_SESSION`。
- **业务逻辑**：
  - 检查会话是否存在且有效，若无效抛出 `UnauthenticatedException`（返回 401）。
  - 通过 `sessions.withAuthenticatedPrincipal(sessionId, principal -> ...)` 调用 `newApiClient.getProfile(principal)`。
  - 获取用户的 `userId`、`username`、`email`（若用户未绑定邮箱，则以 `${username}@ztoken.cc` 兜底）。
  - 返回 DTO：`SessionUserInfo(long userId, String username, String email)`。

---

### 3.2 Open WebUI（FastAPI + SvelteKit）

#### 3.2.1 后端静默同步端点 (`GET /api/v1/auths/portal-sync`)
- **文件位置**：`open-webui/backend/open_webui/routers/auths.py`
- **逻辑流程**：
  1. 读取请求 Cookie `PORTAL_SESSION = request.cookies.get('PORTAL_SESSION')`。
  2. 若 Cookie 为空，直接返回 `Response(status_code=status.HTTP_204_NO_CONTENT)`。
  3. 若 Cookie 存在，发起 HTTP 请求调用 Portal 的会话查询端点 `${PORTAL_BASE_URL}/api/auth/session/info`（通过环境变量 `PORTAL_BASE_URL` 配置，默认 `http://ztoken-portal:8084` 或 `https://ztoken.cc`）。
  4. 若 Portal 返回 401 或请求失败，返回 204 No Content。
  5. 若 Portal 成功返回用户信息：
     - 在 Open WebUI 本地数据库中查询该邮箱/OAuth 用户（`Users.get_user_by_email`）。
     - 若用户不存在，调用 `Auths.insert_new_auth` 自动创建本地用户（角色默认为 user，记录 NewAPI 映射信息）。
     - 若用户已存在，更新或补齐必要凭据信息。
     - 调用 `create_session_response` 生成 Open WebUI JWT Token。
     - 返回 200 携带 `{ token, ...user }`。

#### 3.2.2 前端初始化时静默探测
- **文件位置**：`open-webui/src/routes/+layout.svelte`
- **逻辑流程**：
  - 在页面加载且 `!localStorage.token` 时：
    ```javascript
    try {
        const res = await fetch(`${WEBUI_API_BASE_URL}/auths/portal-sync`, {
            method: 'GET',
            credentials: 'include'
        });
        if (res.status === 200) {
            const data = await res.json();
            if (data?.token) {
                localStorage.token = data.token;
                user.set(data);
                // 刷新模型与用户设置
            }
        }
    } catch (e) {
        // 静默失败，保持访客模式
    }
    ```
  - 若为 204 或请求失败，完全不触发报错 toast，用户平滑保持未登录访客体验。

---

### 3.3 Portal 前端首页与导航（React）

#### 3.3.1 首页 “Chatbox · 在线聊智能体” 特色推广卡片
- **位置**：置于 `portal/frontend/src/features/home/HomePage.tsx` 的 Hero 区域下方、步骤引导区上方。
- **视觉风格**：
  - **外框与背景**：深色与浅色自适应玻璃拟态背景，搭配微妙的蓝紫色微流光外边框（Gradient Glow Border）。
  - **亮点标签**：`✨ 官方功能 · 免配置即开即聊`。
  - **主标题**：`Chatbox · 在线聊智能体`。
  - **副标题描述**：`无需配置客户端与 API Key，即开即聊。聚合 GPT-4o、Claude 3.5、Gemini 1.5 及全系列智能体，开启沉浸式智能对话体验。`
  - **模型胶囊阵列**：嵌入高对比度的模型标签（`GPT-4o`、`Claude 3.5 Sonnet`、`DeepSeek-R1`、`智能体中心`）。
  - **行动按钮（CTA）**：`立即在线体验 →`，在新标签页打开 `https://chatbox.ztoken.cc`。
  - **响应式适配**：移动端下按钮全宽，模型标签自动折行，在所有屏幕尺寸下保持优雅观感。

#### 3.3.2 公共顶栏入口（`PublicHeader.tsx`）
- 在顶部公共导航中追加 `Chatbox` 链接项。
- 导航链接附带精美的 `AI` 或 `HOT` 渐变小角标，提升视觉点击吸引力。
- 多语言词条在 `zh-CN.json` 和 `en-US.json` 中完整配置。

---

## 4. 边界场景与容错策略

| 场景 | 系统行为 | 用户体验 |
| :--- | :--- | :--- |
| **Portal 已登录，点击推广卡片** | 携带 `PORTAL_SESSION` 打开 Chatbox，静默完成登录 | 秒级自动进入对话主页，无任何登录框打扰 |
| **Portal 未登录，点击推广卡片** | 无 `PORTAL_SESSION` 打开 Chatbox，portal-sync 返回 204 | 正常进入 Open WebUI 访客界面，发消息时弹出登录引导 |
| **独立输入域名直接访问 Chatbox** | 检查是否有根域 Cookie，无则返回 204 | 表现与原生完全一致，支持直接在弹窗中登录 |
| **Portal 后端不可用或网络超时** | Open WebUI 的 portal-sync 请求超时捕获异常，降级返回 204 | 访客模式兜底，不导致 Open WebUI 页面崩溃或白屏 |
| **用户在 Portal 退出登录** | 清理 Cookie 时指定 `Domain=.ztoken.cc`，全域 Cookie 失效 | Portal 登出，下次再进 Chatbox 变为访客状态 |

---

## 5. 验证与测试方案

1. **Portal 后端单元测试**：
   - 验证 `PortalProperties` 中读取 `sessionCookieDomain` 的正确性。
   - 验证 `withPortalSession` 和 `signOut` 中 Cookie 是否正确携带 `domain`。
   - 验证 `GET /api/auth/session/info` 在有会话和无会话时的正确响应。
2. **Open WebUI 接口与前端验证**：
   - 测试无 Cookie 请求 `/api/v1/auths/portal-sync` 返回 204。
   - 测试有有效 Cookie 请求时，用户在 Open WebUI 本地自动建号并返回正确 JWT。
3. **前端视觉与响应式验证**：
   - 验证 Portal 首页推广卡片在 PC 端、平板端与手机端的展示效果及暗黑模式适配。
   - 验证顶栏 Chatbox 链接与角标正常显示并正确跳转。
