# Ztoken Portal

Customer-facing portal for NewAPI. The portal is an independent React and Spring Boot application; it does not modify NewAPI source code or query the NewAPI database.

The backend packages the React production build into a single executable JAR. Run the complete verification suite with:

```powershell
mvn -f backend/pom.xml clean package
```

Start the packaged application with Java 17 and the user-scoped portal session key:

```powershell
.\scripts\start-portal.ps1
```

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

TRC20-USDT、地址池、链上扫描、TxID、管理员审核 UI、GitHub/Google OAuth、PayPal Live 联调、NewAPI 源码或数据库修改均不在本计划内。
