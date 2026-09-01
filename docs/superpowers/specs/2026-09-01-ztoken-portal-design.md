# Ztoken 客户门户 设计文档（第一期）

> 日期：2026-09-01
> 状态：已确认（待用户最终审阅）

## 1. 目标与边界

在**不修改 NewAPI 源码**、**不直接访问 NewAPI 数据库**的前提下，为开源的 NewAPI AI API 网关构建一个独立的客户门户（Portal）。Portal 通过 HTTP API 复用 NewAPI 的现有业务能力，并自行扩展三方登录、PayPal 支付、TRC20-USDT 支付和订单管理。

### 复用边界

| 能力 | 来源 |
|---|---|
| 模型列表与价格 | NewAPI `GET /api/pricing` |
| 用户信息 / 余额 | NewAPI 用户 API |
| 令牌管理 | NewAPI Token API |
| 使用日志 | NewAPI 日志 API |
| 个人资料 | NewAPI 用户 API |
| 大模型调用 | 直接调用 NewAPI Relay 接口，不经 Portal 代理 |
| 管理员身份验证 | 复用 NewAPI 管理员账号 |

### Portal 自行扩展

- 全部客户页面（首页、模型、购买、控制台）
- 管理端订单审核页面
- PayPal 支付编排
- TRC20-USDT 收款地址轮询与链上扫描
- 异常订单人工审核与入账
- GitHub / Google OAuth 登录
- 邮箱注册登录
- 中英文国际化
- Portal 会话与三方账号绑定

## 2. 技术栈

- **后端**：Java 17、Spring Boot 3.3、JPA、Flyway、WebClient
- **前端**：React 19、TypeScript、Vite、Axios、TanStack Query、TanStack Router
- **UI 组件**：控制台统一使用 Semi Design；首页单独设计（灵活、大气风格）
- **数据库**：MySQL（Flyway 管理迁移，`ddl-auto: validate`）
- **部署**：前端构建产物由 Maven 打包进 Spring Boot JAR，单端口运行

## 3. 架构

```text
浏览器
   ↓ 单端口 (8080)
Java Spring Boot Portal
   ├─ 认证 / 会话 / 三方登录
   ├─ 支付编排（PayPal + TRC20-USDT 扫描）
   ├─ 订单管理与人工审核
   └─ NewApiClient（复用 NewAPI HTTP API）
        ↓ HTTP API
NewAPI（独立运行，自带管理员后台，不改源码）
        ↓
DB / Redis / 各大模型渠道
```

- Portal 不复制 NewAPI 的模型价格、Token、日志、配额数据，实时读取。
- NewAPI 仍独立运行（默认 `:3005`），大模型客户端直接访问 NewAPI Relay 接口。

## 4. 前端页面结构

### 公开菜单（无需登录）

| 菜单 | 说明 |
|---|---|
| 首页 | 介绍页 + 部分模型价格，大气灵活风格 |
| 模型 | 复用 `/api/pricing` 数据，Semi Design 展示分组与价格 |
| 文档 | 外链跳转 |
| 购买 | 金额卡片 + 支付方式 |
| 控制台 | 需登录 |

### 控制台（登录后，Semi Design）

| 页面 | 数据来源 |
|---|---|
| 仪表盘 | NewAPI（余额、请求数、额度消耗、Token 消耗等） |
| 余额充值 | 与购买共用组件 |
| 令牌管理 | 复用 NewAPI Token API |
| 使用日志 | 复用 NewAPI 日志 API |
| 个人资料 | 复用 NewAPI 用户 API |
| 订单管理 | Portal 自建订单表 |

### 管理端（复用 NewAPI 管理员身份登录）

| 页面 | 说明 |
|---|---|
| 订单列表 | 全部支付订单 |
| 异常订单审核 | 少付 / 多付 / 过期 / 无法匹配 |
| 人工入账 | 通过 / 驳回 / 标记处理 + 备注 + 操作记录 |

## 5. Portal 数据库表

```text
portal_sessions        会话
portal_oauth_accounts  三方登录绑定（provider + 三方uid → NewAPI 用户）
payment_orders         支付订单（PayPal / TRC20-USDT）
payment_callbacks      支付回调 / 扫描原始记录
payment_exceptions     异常订单与人工审核记录
```

## 6. 支付流程

### 6.1 统一订单模型

购买页与控制台“余额充值”共用同一订单服务。前端不直接调用支付平台。

第一期固定 `1 USDT = 1 USD`，不引入汇率与手续费。

### 6.2 PayPal

```text
Portal 创建本地订单 → 调用 PayPal API 创建订单
→ 用户跳转 PayPal 付款 → PayPal 返回 Portal
→ Portal 服务端接收 PayPal Webhook
→ 校验订单号 / 金额 / 币种 / 状态
→ 幂等确认 → 调用 NewAPI 完成入账
```

以服务端回调为准，不信任浏览器跳转。Webhook event ID 建立唯一约束防重复。

### 6.3 TRC20-USDT

使用**多个固定收款地址轮询**（不按订单生成新地址）。

```text
Portal 创建订单 → 从地址池轮询分配地址
→ 页面显示地址 / 金额 / 二维码 / 有效期
→ 后台定时扫描 TRON 链
→ 匹配收款地址 / USDT 金额 / 订单时间
→ 确认区块数达标 → 自动入账
```

扫描、金额判断、确认数判断、订单更新全部在 Java 后端完成；前端不直接查链上数据。

### 6.4 异常规则

| 情况 | 处理 |
|---|---|
| 金额匹配且未过期 | 自动入账 |
| 少付 / 多付 | 标记异常，人工审核 |
| 过期后付款 | 标记异常，人工审核 |
| 无法匹配 | 标记异常，人工审核 |
| 同一交易哈希重复 | 拒绝重复入账并记录安全事件 |
| 已支付订单再次回调 | 幂等返回，不重复加款 |
| 支付成功但 NewAPI 入账失败 | 保留“待入账”，支持重试 |

### 6.5 状态模型

订单状态与入账状态分离：

```text
订单状态：PENDING / PAYMENT_PROCESSING / PAID / EXCEPTION / EXPIRED / CANCELLED
入账状态：NOT_CREDITED / CREDITING / CREDITED / CREDIT_FAILED / MANUAL_CREDITED
```

### 6.6 人工审核

管理员可查看异常订单详情（含链上 / PayPal 交易信息）、填写备注、选择驳回 / 入账 / 标记无需处理，并记录管理员与时间。人工入账同样经过幂等检查。NewAPI 管理员凭据只在 Java 后端环境变量中保存，React 不接触。

## 7. 登录与注册

### 7.1 邮箱

Portal 负责表单与错误展示，Java 后端负责校验、调用 NewAPI 注册/登录、创建 Portal 会话、加密保存 access token。第一期不强制邮箱验证码，生产环境建议增加邮箱验证与注册限流。

### 7.2 GitHub / Google OAuth

```text
用户点击三方登录 → Portal 跳转授权 → 第三方返回授权码
→ Portal 服务端换取身份
→ 按 (provider, provider_user_id) 查找绑定
→ 已绑定：登录
→ 未绑定：自动创建 NewAPI 用户并绑定
→ 创建 Portal 会话
```

- 自动创建 NewAPI 用户时生成不冲突用户名，处理邮箱为空、重名、邮箱冲突等情况。
- 若三方邮箱已对应已有 NewAPI 账号，不自动合并，进入安全关联流程，避免账号被接管。

## 8. 部署

- Portal 打包为单个 Spring Boot JAR，`/`、`/models`、`/console/*`、`/admin/*`、`/api/*` 共用 `:8080`。
- NewAPI 独立运行 `:3005`。
- 推荐 `portal.example.com → Portal`，`api.example.com → NewAPI`。
- TRON 扫描任务需支持单实例执行或分布式锁，避免多实例重复处理。

## 9. 安全要点

- 第三方账号、支付回调、人工入账均需幂等。
- NewAPI 管理员凭据仅存在于 Java 后端环境变量。
- 支付确认以服务端回调 / 链上确认为准。
- 注册与登录需要限流。
- 生产启用 HTTPS 与 Secure Cookie。

## 10. 待确认 / 后续

- 邮箱验证码是否需要（第一期暂不做）。
- PayPal 商户账号 / API 凭据与回调地址配置。
- TRC20 收款地址、私钥保管与资金归集策略。
- 需要核实的 NewAPI 管理接口：能否通过 `PUT /api/user/` 安全地增加指定用户额度，及入账所用接口的确切字段。
- NewAPI 日志与仪表盘数据是否完整满足页面字段需求。
