# Portal PayPal 与 TRC20-USDT 支付设计

> 日期：2026-09-08  
> 状态：已确认，待书面设计审阅

## 1. 目标与范围

在现有 Ztoken Portal 内完成可扩展的支付能力：保留现有 PayPal 充值闭环，并从 `F:\WorkSpace\study\AIProject\New-api\usdt` 迁移 TRC20-USDT 的地址池、多地址分配、唯一金额尾数、自动扫描和用户提交 TxID 即时核验。两种渠道共用订单、状态机、额度入账和订单历史。

首期支付渠道为 `PAYPAL` 和 `USDT_TRC20`。后续新渠道通过支付渠道注册表接入，不能让新增渠道修改已有 PayPal 或 TRC20 业务分支。

不修改 NewAPI 源码、不直接访问 NewAPI 数据库。Portal 继续通过 NewAPI 管理接口为当前 Portal Session 对应的用户增加 quota。

## 2. 已确认决策

| 决策 | 结果 |
| --- | --- |
| TRC20 收款 | 迁移旧项目的地址池和多地址能力 |
| 链上确认 | 自动扫描与用户提交 TxID 即时核验双通道 |
| 最小确认数 | 20 个区块确认 |
| 地址识别 | 同一地址同时待支付订单使用唯一 USDT 最小单位尾数 |
| 支付扩展 | 以渠道注册表和独立渠道适配器实现 |
| 金额与额度 | 服务端计算；美元金额使用整数美分，USDT 使用 6 位最小单位 |
| 配置与密钥 | 按用户要求写入 Portal `backend/src/main/resources/application.yml`，不复制旧项目中已有的实际密钥值 |
| 额度异常 | 明确失败为 `CREDIT_FAILED`；未知结果为 `CREDIT_UNKNOWN`，禁止自动重试 |

## 3. 架构

```text
Portal 浏览器
  │
  ├─ 创建支付订单（金额、支付方式）
  ▼
Portal 支付订单核心
  ├─ PaymentProviderRegistry
  │    ├─ PayPalPaymentProvider
  │    └─ Trc20PaymentProvider
  ├─ 统一 PaymentOrder 状态机
  ├─ 统一 PaymentCreditService（幂等增加 NewAPI quota）
  └─ 用户归属校验（Portal Session）
       │
       ├─ PayPal：SDK Create / Capture / Webhook
       └─ TRC20：地址池 + 金额尾数 + TronGrid 扫描 / TxID 查询
```

`PaymentProviderRegistry` 只负责按照 `PaymentMethod` 取得渠道适配器。订单核心仅依赖渠道适配器的创建支付上下文、处理用户动作、轮询待确认交易三个能力。渠道不得直接增加 NewAPI quota；所有确认结果必须进入统一入账服务。

## 4. 数据模型

现有 `payment_orders` 保持为唯一客户订单表，扩展如下：

```text
payment_method                  PAYPAL / USDT_TRC20
payment_address_id              TRC20 地址池记录，可空
receive_address                 创建订单时的收款地址快照，可空
payable_minor                   TRC20 应付 USDT 最小单位（6 位），可空
payable_currency                TRC20 为 USDT，PayPal 为 USD
payable_scale                   TRC20 为 6，PayPal 为 2
submitted_txid                  客户提交的交易 ID，可空
txid_check_count                即时核验后的重查次数
last_txid_checked_at
next_txid_check_at
last_txid_check_result
```

新增/迁移的专用表：

| 表 | 用途 |
| --- | --- |
| `payment_addresses` | 可启用/停用的 TRON 收款地址池和当前活跃订单负载 |
| `payment_amount_registries` | `(地址、应付金额)` 的唯一占用，避免尾数冲突 |
| `chain_transfers` | 已观察到的 `(txid、log_index)` 链上 TRC20 转账，防止一笔转账复用 |
| `payment_scan_cursors` | 每个收款地址的 TronGrid 扫描游标 |
| `payment_transactions` | PayPal provider order/capture；现有数据模型继续使用 |
| `payment_provider_events` | PayPal Webhook 去重；现有数据模型继续使用 |
| `credit_attempts` | 每次统一入账的审计记录；现有数据模型继续使用 |

`payment_orders` 与 `payment_addresses` 和 `payment_amount_registries` 均使用外键；`chain_transfers` 的唯一约束为 `(txid, log_index)`。订单过期或关闭时降低地址 `active_order_count`，但金额占用与链上交易记录保留，防止历史转账被新订单误认。

## 5. TRC20 支付流程

1. 客户端以当前 Portal Session 调用 `POST /api/payments/orders`，提交美元金额和 `USDT_TRC20`。
2. 服务端验证金额，按既有兑换规则计算 quota 与基础 USDT（`1 USD = 1 USDT = 1_000_000` 最小单位）。
3. 地址池按 `active_order_count` 选取启用地址；在数据库事务中分配一个未被该地址使用的尾数，生成精确应付金额并创建 30 分钟订单。
4. 客户端展示 TRC20 网络、精确 USDT 金额、收款地址、倒计时、复制操作和 TxID 输入框。浏览器不能指定地址、金额、用户、额度或确认结果。
5. 自动扫描任务遍历活跃地址的 TronGrid TRC20 转账，过滤为配置的 USDT 合约，记录未见过的 `(txid, log_index)`，再按收款地址和精确金额匹配待支付订单。
6. 客户提交 TxID 时，服务端立即读取该交易的所有 TRC20 transfer；后续按 5、10、15、30、60 秒退避重查，直到交易已被索引、达到 20 确认、订单过期，或发现终态不匹配。
7. 两条路径使用同一个核验器：目标地址、精确金额、TRC20 合约、未被其他订单使用、最少 20 确认全部成立时，订单在行锁下从 `WAITING_PAYMENT` 转为 `CONFIRMED`。
8. `PaymentCreditService` 独占执行 `CONFIRMED → CREDITING`，调用 NewAPI 管理接口增加 quota，并将结果置为 `PAID`、`CREDIT_FAILED` 或 `CREDIT_UNKNOWN`。

## 6. PayPal 兼容与统一状态机

PayPal 继续使用现有的服务端 Create/Capture、Webhook 签名验证和 provider 交易表。其订单创建 API、前端 SDK 加载和订单轮询接口维持兼容；`PaymentMethod` 扩展不会改变现有 PayPal 请求或订单号语义。

两种渠道共享以下状态：

```text
WAITING_PAYMENT
  ├─ TRC20 通过扫描或 TxID 核验 / PayPal Capture 或已验签 Webhook
  ▼
CONFIRMED → CREDITING → PAID
                    ├→ CREDIT_FAILED
                    └→ CREDIT_UNKNOWN

WAITING_PAYMENT ─过期→ EXPIRED
WAITING_PAYMENT ─取消→ CANCELLED
```

重复 PayPal Capture/Webhook、重复扫描、重复 TxID、同一交易的多日志命中和并发确认均以唯一约束和订单行锁保证不会重复入账。

## 7. API 与前端

统一订单接口：

```text
POST /api/payments/orders                         { amount, method }
GET  /api/payments/orders
GET  /api/payments/orders/{orderNo}

POST /api/payments/orders/{orderNo}/trc20/txid    { txid }
GET  /api/payments/orders/{orderNo}/trc20/status

GET  /api/payments/orders/{orderNo}/paypal/config
POST /api/payments/orders/{orderNo}/paypal/order
POST /api/payments/orders/{orderNo}/paypal/capture
POST /api/webhooks/paypal
```

所有订单读取和操作均由 `PortalPrincipal.userId` 做归属校验。TRC20 创建/读取响应只在该订单属于当前用户时返回收款地址、精确金额、确认进度和 TxID 核验状态。

购买页和控制台充值页的支付方式卡片同时启用 PayPal 与 TRC20-USDT。选 PayPal 后进入已有 SDK Checkout；选 TRC20 后显示转账工作区及状态轮询。订单页显示支付方式、金额、地址/交易 ID 摘要、状态、创建时间与异常提示。

## 8. 配置

按用户要求，以下配置项存在于 `backend/src/main/resources/application.yml` 的 `payment` 节点；具体密钥由部署者填写，不能从旧项目复制：

```yaml
payment:
  order-expiry-minutes: 30
  usdt-contract: TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t
  confirmation-count: 20
  amount-suffix-enabled: true
  chain-scan-fixed-delay-ms: 5000
  txid-retry-fixed-delay-ms: 5000
  order-expiry-fixed-delay-ms: 5000
  trongrid:
    base-url: https://api.trongrid.io
    api-key: ""
  paypal:
    mode: sandbox
    client-id: ""
    client-secret: ""
    webhook-id: ""
  newapi-credit:
    base-url: ""
    access-token: ""
```

`payment-addresses` 的初始地址池通过受控配置或启动导入声明；重复地址跳过，已存在地址不被覆盖。运行期的启用/停用和负载状态由 Portal 数据库保存。

## 9. 失败处理与安全

- TronGrid 网络失败、限流或尚未索引交易时不确认、不入账，并按重试计划继续查询。
- TxID 已被其他订单使用，或转账的合约、收款地址、精确金额任一不匹配时，记录拒绝原因，不触发入账。
- 低于 20 确认时显示待确认，不将支付视为失败。
- NewAPI 调用得到明确业务失败时为 `CREDIT_FAILED`；超时、连接中断或无法判定远端结果时为 `CREDIT_UNKNOWN`，不自动重试。
- 日志不记录 PayPal Secret、NewAPI Token、TronGrid API Key 或用户完整私密凭据；TxID 和地址只作为公开链上标识记录。

## 10. 测试与验收

- 迁移：H2/MySQL 启动、唯一约束、地址池初始导入幂等。
- 领域：地址负载均衡、地址禁用、尾数冲突/耗尽、订单过期释放负载、状态机。
- 链上：TronGrid 模拟响应、合约/金额/地址/确认数/重复交易校验、扫描游标。
- 双通道：自动扫描确认与 TxID 立即查询、索引延迟重试、确认数变化、并发确认只有一次入账。
- 授权：用户不能读取或提交他人订单的 TxID。
- PayPal：现有自动化测试全量回归，确认枚举扩展未破坏 Capture 与 Webhook。
- 前端：两种方式选择、TRC20 地址/精确金额展示、复制、TxID 提交、状态轮询、订单列表及中英文文案。

## 11. 非目标

首期不含运营管理支付地址的 Web UI、人工补单/强制入账 UI、其他链或代币、法币汇率换算、支付退款及对 NewAPI 源码的任何修改。
