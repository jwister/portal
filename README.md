# Ztoken Portal

Customer-facing portal for NewAPI. The portal is an independent React and Spring Boot application; it does not modify NewAPI source code or query the NewAPI database.

The backend packages the React production build into a single executable JAR. Run the complete verification suite with:

```powershell
mvn -f backend/pom.xml clean package
```

Start the packaged application with Java 17:

```powershell
java -jar backend\target\ztoken-portal-0.1.0-SNAPSHOT.jar
```

## 前后端分离开发

开发时，前端运行在 Vite 的 `http://127.0.0.1:5173`，后端运行在 Spring Boot 的 `http://127.0.0.1:8084`。前端所有 `/api/*` 请求由 Vite 代理到后端，因此前端代码继续使用相对 API 路径，不需要配置浏览器 CORS。

```powershell
# 终端 1：后端。跳过 Maven 内嵌的前端打包步骤。
mvn -f backend/pom.xml spring-boot:run "-Dskip.frontend=true"

# 终端 2：前端。
Set-Location frontend
npm ci
npm run dev
```

默认代理目标为 `http://127.0.0.1:8084`。如需连接其他本地后端实例，启动前设置 `VITE_API_PROXY_TARGET`，例如：

```powershell
$env:VITE_API_PROXY_TARGET = 'http://127.0.0.1:18084'
npm run dev
```

发布流程不变：`mvn -f backend/pom.xml clean package` 仍会运行前端生产构建，并将 `frontend/dist` 打进 Spring Boot JAR 的静态资源；线上仍只暴露 Portal 的一个端口，同时提供页面和 `/api/*`。

The values after `:` in `application.yml` are local defaults. Docker can override Spring Boot properties with their canonical environment-variable names, such as `PORTAL_NEW_API_BASE_URL`, `PORTAL_NEW_API_PRICING_TOKEN`, or `PORTAL_SESSION_KEY`.

## GitHub 与 Google 登录

Portal 复用 NewAPI 的 OAuth 账号体系：Portal 只保存加密的 NewAPI 登录令牌并签发自己的 `PORTAL_SESSION`，不保存 GitHub/Google access token 或 OAuth client secret。

在 NewAPI 管理设置中启用 GitHub OAuth，并将 OIDC provider 配置为 Google。NewAPI 的 `ServerAddress` 必须是 Portal 的公网地址，例如 `https://portal.example.com`。随后在第三方平台登记以下回调地址：

```text
GitHub OAuth App:     https://portal.example.com/oauth/github
Google OAuth Client:  https://portal.example.com/oauth/oidc
```

GitHub 和 Google 按钮只会在对应的 NewAPI provider 已启用且公开启动配置完整时显示。两个 OAuth 回调都由 Portal BFF 转发给 NewAPI 的固定 OAuth endpoint，浏览器不会获得 NewAPI access token。

## PayPal Sandbox 充值

`Portal` exposes a PayPal Sandbox 充值闭环：本地订单、服务端 Capture、签名验证 Webhook 和 NewAPI quota 入账。生产只通过环境变量切到 PayPal Live；任何 PayPal Secret、Webhook ID 或 NewAPI 管理员 Access Token 都不得写入源码或 `application.yml`。

### 部署环境变量

```text
PAYMENT_PAYPAL_MODE=sandbox          # 或 live；切换只改环境变量
PAYMENT_PAYPAL_CLIENT_ID=...
PAYMENT_PAYPAL_CLIENT_SECRET=...
PAYMENT_PAYPAL_WEBHOOK_ID=...
PAYMENT_NEWAPI_CREDIT_ACCESS_TOKEN=...
PAYMENT_ORDER_EXPIRY_MINUTES=30      # 默认 30 分钟
```

### Sandbox Webhook

PayPal Sandbox 需要公网 HTTPS 入口，把 webhook 指向：

```text
https://<portal-domain>/api/webhooks/paypal
```

Portal 后端会调用 PayPal `verify-webhook-signature`，并在事件 ID、provider order ID、金额或币种不匹配时拒绝确认。

### 入账语义

- `WAITING_PAYMENT` → `CONFIRMED` → `CREDITING` → `PAID` 为成功路径。
- `CREDIT_FAILED` 表示 NewAPI 明确拒绝加款，需要人工对账。
- `CREDIT_UNKNOWN` 表示 NewAPI 超时或连接中断；Portal 不会自动重试，必须由人工触发，避免重复加款。
- `EXPIRED` / `CANCELLED` 不再触发任何 NewAPI 请求。

### 不在 Plan C 范围内

TRC20-USDT、地址池、链上扫描、TxID、管理员审核 UI、PayPal Live 联调、NewAPI 源码或数据库修改均不在本计划内。
