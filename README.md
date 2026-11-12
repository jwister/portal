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

## Docker 部署

GitHub Actions 在每次推送 `master` 时发布一个精确版本的镜像，例如 `wenyou7/ztoken-portal:0.1.0`，不会发布 `latest`。首次发布前，必须在 Docker Hub 的 `wenyou7/ztoken-portal` 仓库中启用**不可变标签**；这是阻止并发或外部操作覆盖已发布版本的最终保护。部署前必须设置 `PORTAL_IMAGE` 为要使用的完整镜像名和版本，以便升级和回滚都可追溯：

```powershell
$env:PORTAL_IMAGE = 'wenyou7/ztoken-portal:0.1.0'
docker compose up -d
```

The values after `:` in `application.yml` are local defaults. Docker can override Spring Boot properties with their canonical environment-variable names, such as `PORTAL_NEW_API_BASE_URL`, `PORTAL_NEW_API_PRICING_TOKEN`, or `PORTAL_SESSION_KEY`.

## GitHub 与 Google 登录

### Portal 转发客户端 IP 与处理 429

Portal 使用 Spring Boot/Tomcat 现有的 `native` 代理链解析，并在每次向 NewAPI 发请求时转发容器确认的客户端 IP（`X-Forwarded-For` 和 `X-Real-IP`）。不会照搬浏览器传入的原始头，也不会把某个用户的 IP 缓存在共用客户端中。没有 HTTP 请求的后台任务不附加客户端地址；用户令牌、入账服务令牌和实际操作人保持原样。

部署时只需要核对两段链路：

1. **CDN / Nginx → Portal**：如果现有入口已经正确传递真实 IP，就继续复用。Tomcat 默认识别本机和内网代理；CDN 公网节点直接回源 Portal 时，使用可选环境变量 `PORTAL_TRUSTED_PROXY_PATTERN` 配置实际可信的 CDN/入口地址正则，不使用匹配所有地址的规则。该变量不配置时不会信任任意公网代理。
2. **Portal → NewAPI**：当前已知 Portal 出口为 `95.41.67.11`。在 NewAPI 现有 `TRUSTED_PROXIES` 中追加该地址；如果中间还有负载均衡/Nginx，需保留它们原有的可信地址。NewAPI 的显式配置会替换默认列表，不能只添加 Portal 地址却丢掉原有代理链。

若 NewAPI 目前未配置 `TRUSTED_PROXIES`，下面是在其原有默认网段基础上追加已知 Portal 出口的值（配置到 **NewAPI 容器**）：

```text
TRUSTED_PROXIES=95.41.67.11,127.0.0.0/8,::1,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,fc00::/7
```

已有自定义列表时只追加出口地址；后续可按实际拓扑收窄内网网段。Portal 端口应只允许 CDN/受控入口访问，入口需要清理不可信转发头，避免外部伪造来源。

NewAPI 返回 429 时，Portal 保留 429 状态、`no-store` 和合法 `Retry-After`，登录/注册页面显示等待秒数并临时禁止重复提交。不会自动重试一次性 OAuth 授权或支付入账。当前 CDN 的实际回源地址尚未提供，上述代码和本地测试不代表线上代理配置已经完成；部署后用两个不同公网来源登录，检查 NewAPI 审计应出现各自的客户端 IP。

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
