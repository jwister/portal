# Ztoken Portal PayPal Sandbox 支付设计（Plan C）

> 日期：2026-09-02
> 状态：已确认，待书面设计审阅

## 1. 目标

在不修改 NewAPI 源码、不直接访问 NewAPI 数据库的前提下，为 Ztoken Portal 增加 PayPal Sandbox 充值闭环：本地订单、浏览器 PayPal JS SDK 结账、服务端 Create/Capture、签名验证 Webhook、幂等状态机，以及通过独立 NewAPI 管理员 Access Token 自动增加用户 quota。

生产环境通过环境变量切换到 PayPal Live，不在源码或 `application.yml` 写入任何 PayPal/管理员密钥。

## 2. 已确认决策

| 决策 | 结果 |
|---|---|
| PayPal 环境 | Sandbox 优先，环境变量可切换 Live |
| 金额范围 | $1.00–$10,000.00，最多两位小数 |
| 金额表示 | 整数美分，禁止浮点金额计算 |
| 额度兑换 | $1 = 500,000 NewAPI quota |
| 支付确认 | 服务端 Capture 后立即尝试入账；Webhook 为签名验证和幂等兜底 |
| NewAPI 入账凭据 | 独立管理员 Access Token，仅在 Portal 后端环境变量保存 |
| 订单有效期 | 创建后 30 分钟 |
| TRC20 | 不属于 Plan C；保留未来兼容的订单边界 |
| 管理员审核 UI | 不属于 Plan C；后续 Plan E 实现 |

例如：`$25.50` 持久化为 `2550` 美分，入账额度为 `12,750,000` quota。

## 3. 架构与支付流

```text
客户浏览器
  │
  ├─ POST /api/payments/orders
  │      仅提交金额和 PAYPAL
  ▼
Portal Spring Boot
  ├─ 创建本地 payment_order
  ├─ 创建/复用 PayPal provider order
  ├─ Capture 与金额、币种、订单核验
  ├─ 调用 NewAPI 管理接口增加 quota
  └─ 保存订单、交易、Webhook 和入账审计
  │
  ├─ PayPal JS SDK（仅公开 Client ID）
  ▼
PayPal Sandbox
  │
  ├─ 浏览器授权后 Capture
  └─ POST /api/webhooks/paypal（官方签名验证）
```

浏览器永远不接触 PayPal Client Secret、PayPal Webhook ID、NewAPI 管理员 Access Token 或 NewAPI 管理员会话。

大模型请求继续直接访问 NewAPI Relay API，不经过 Portal。

## 4. Portal 数据模型

### `payment_orders`

```text
id
order_no                    唯一公开订单号
newapi_user_id              当前 Portal Session 对应的用户
payment_method              PAYPAL
amount_usd_minor            整数美分
quota_to_credit             待增加的 NewAPI quota
status
expires_at                  订单有效期
created_at / updated_at
confirmed_at / credited_at
```

### `payment_transactions`

```text
id
payment_order_id
provider                    PAYPAL
provider_order_id           唯一
provider_capture_id         唯一，可空
provider_status
idempotency_key             <portal-order-no>-paypal
created_at / updated_at
```

### `payment_provider_events`

```text
id
provider                    PAYPAL
provider_event_id           唯一，Webhook 去重键
event_type
payment_order_id            可空，未知订单事件仍留审计记录
verified_at
audit_summary               脱敏摘要；不保存密钥
```

### `credit_attempts`

```text
id
payment_order_id
status                      PROCESSING / SUCCESS / FAILED / UNKNOWN
message                     脱敏审计信息
created_at / finished_at
```

所有表属于 Portal MySQL，使用 Flyway 新迁移创建。不复制或读取 NewAPI 数据库数据。

## 5. 状态机与入账规则

```text
WAITING_PAYMENT
    │  经过服务端 Capture 或已验证 Webhook
    ▼
CONFIRMED
    ▼
CREDITING
 ┌──┼─────────────┐
 ▼  ▼             ▼
PAID CREDIT_FAILED CREDIT_UNKNOWN

WAITING_PAYMENT ──过期──> EXPIRED
WAITING_PAYMENT ──取消──> CANCELLED
```

| 场景 | 行为 |
|---|---|
| 重复创建 PayPal order | 复用本地订单关联的 provider order |
| 重复 Capture | 返回既有确认结果，不重复入账 |
| 重复 Webhook | 以 `provider_event_id` 唯一约束幂等忽略 |
| Webhook 先于浏览器 Capture | 验签和核验成功后确认并触发入账 |
| 浏览器 Capture 先完成 | Capture 立即入账；后续 Webhook 仅审计/幂等 |
| 订单/捕获 ID、金额、币种不匹配 | 拒绝确认，不入账 |
| 订单已过期 | 标记 `EXPIRED`，不入账 |
| NewAPI 明确拒绝额度增加 | `CREDIT_FAILED`，等待后续人工审核 |
| NewAPI 超时或连接中断 | `CREDIT_UNKNOWN`，禁止自动重试，避免重复加款 |
| NewAPI 明确成功 | `PAID`，记录到账时间 |

Capture、Webhook 和入账都在订单行锁保护下竞争状态迁移；只有获得 `CONFIRMED → CREDITING` 的执行者能调用 NewAPI 管理接口。

## 6. PayPal 服务端集成

参考 `F:\WorkSpace\study\AIProject\New-api\usdt` 的既有实现模式：

1. 用 Client ID / Client Secret 获取和缓存 PayPal OAuth token。
2. `POST /v2/checkout/orders` 创建 CAPTURE intent 的 provider order。
3. `PayPal-Request-Id` 固定为本地订单号派生值，确保创建/捕获请求可重试。
4. 服务端 Capture：`POST /v2/checkout/orders/{orderId}/capture`。
5. 每次 Capture 核验：
   - provider order ID 与本地交易一致
   - capture ID 非空
   - 状态为 `COMPLETED`
   - 币种为 `USD`
   - 金额美分与本地订单完全一致
6. Webhook 收到后调用 PayPal `verify-webhook-signature`；验签失败返回 400，不写入成功事件。
7. Webhook 完成事件只处理 `PAYMENT.CAPTURE.COMPLETED`，其他事件可审计但不触发入账。

## 7. Portal API

所有订单 API 由当前 Portal Session 授权，订单归属始终根据服务端 `PortalPrincipal.userId` 判断。浏览器传入的用户 ID、quota、PayPal provider ID 或 capture ID 都不作为信任来源。

```text
POST /api/payments/orders
  Body: { amount: "25.50", method: "PAYPAL" }

GET /api/payments/orders
GET /api/payments/orders/{orderNo}

GET /api/payments/orders/{orderNo}/paypal/config
POST /api/payments/orders/{orderNo}/paypal/order
POST /api/payments/orders/{orderNo}/paypal/capture

POST /api/webhooks/paypal
```

`/paypal/config` 仅对当前订单用户返回公开 PayPal Client ID 与 mode；其他 PayPal 凭据不返回。

## 8. 客户端体验

1. 用户在购买或控制台充值页面选金额和 PayPal。
2. Portal 创建本地订单并显示创建状态。
3. 获取公开 Client ID，动态加载 PayPal JS SDK。
4. SDK 的创建订单回调调用 Portal `/paypal/order`；金额不从浏览器提交。
5. SDK 授权完成后调用 Portal `/paypal/capture`。
6. 客户端轮询订单详情状态：
   - `PAID`：显示到账额度和成功状态
   - `CONFIRMED` / `CREDITING`：显示处理中
   - `CREDIT_FAILED` / `CREDIT_UNKNOWN`：显示已支付、待人工处理
   - `EXPIRED`：显示已过期
7. 订单管理页面替换现有占位状态，展示当前用户的订单号、金额、方式、状态和时间。

SDK 加载失败、客户取消授权或浏览器刷新不会改变本地支付订单；用户可在订单页恢复查看状态。

## 9. 安全配置

以下变量只由部署环境注入，不写入源码或 `application.yml`：

```text
PAYMENT_PAYPAL_MODE=sandbox
PAYMENT_PAYPAL_CLIENT_ID=...
PAYMENT_PAYPAL_CLIENT_SECRET=...
PAYMENT_PAYPAL_WEBHOOK_ID=...
PAYMENT_NEWAPI_CREDIT_ACCESS_TOKEN=...
PAYMENT_ORDER_EXPIRY_MINUTES=30
```

Sandbox webhook 联调需要公开 HTTPS 地址：

```text
https://<portal-domain>/api/webhooks/paypal
```

Live 切换只改变环境变量；Sandbox 和 Live 密钥不可混用。

## 10. 测试与验收

- Flyway：新表、唯一约束、数据迁移和重复启动验证。
- MockWebServer：PayPal OAuth、Create、Capture、Webhook 签名验证、NewAPI 管理员加额度请求。
- 订单行为：美分校验、固定兑换、过期、重复 Create/Capture/Webhook、Capture 与 Webhook 竞争。
- 入账：成功、明确失败、未知结果；未知结果不自动重试。
- 授权：用户 A 无法读取、Capture 或操作用户 B 的订单。
- 前端：PayPal SDK 加载失败、取消授权、订单轮询、订单列表、双语文案。
- 无真实 PayPal 凭据时，所有自动化测试使用 MockWebServer；不得发起真实支付。

## 11. 非目标

Plan C 不包括：TRC20-USDT、地址池、链上扫描、TxID、管理员审核 UI、人工补单页面、GitHub/Google OAuth、PayPal Live 联调和 NewAPI 源码修改。
