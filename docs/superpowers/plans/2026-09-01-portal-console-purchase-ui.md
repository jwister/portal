# Ztoken Portal 控制台与购买界面实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐客户控制台的仪表盘、令牌、使用日志、个人资料和订单空状态页面，并实现购买/余额充值 UI，同时通过明确的 Java BFF 复用 NewAPI API；本阶段不接入真实支付、OAuth 或订单持久化。

**Architecture:** React 通过 Portal 自己的 `/api/console/**` 接口获取数据，Spring Boot 通过 `NewApiClient` 调用 NewAPI 的用户作用域 API，并从服务器会话获取身份。页面使用 Semi Design，NewAPI 原始响应不直接透传；购买和订单页面只建立可替换的 UI/类型边界，为后续支付计划预留接口。

**Tech Stack:** Java 17, Spring Boot 3.3, WebClient, JPA/Flyway（本阶段不新增迁移）, React 19, TypeScript, Vite, Semi Design, Vitest, Testing Library, i18next.

**重要约束：** 不修改 `new-api`；不读取、修改或提交用户当前未提交的 `backend/src/main/resources/application.yml`；不执行真实充值、支付、OAuth 或链上操作。

---

## 文件职责映射

### 后端

- Modify: `backend/src/main/java/io/ztoken/portal/newapi/NewApiClient.java` — 增加 dashboard、token CRUD、日志、资料的明确接口。
- Modify: `backend/src/main/java/io/ztoken/portal/newapi/NewApiHttpClient.java` — 实现已确认的 NewAPI user-scoped 请求，统一注入会话身份头并映射安全 DTO。
- Modify: `backend/src/main/java/io/ztoken/portal/console/DashboardSummary.java` — 增加真实可用的 tokenUsage nullable 字段。
- Modify: `backend/src/main/java/io/ztoken/portal/console/DashboardController.java` — 扩展 dashboard 和 token BFF 路由。
- Create: `backend/src/main/java/io/ztoken/portal/console/LogController.java` — 日志列表与统计接口。
- Create: `backend/src/main/java/io/ztoken/portal/console/ProfileController.java` — 个人资料读写接口。
- Create: `backend/src/main/java/io/ztoken/portal/console/*Dto.java` — 用户可见的日志、统计、资料、token 请求/响应 DTO。
- Create/Modify: `backend/src/test/java/io/ztoken/portal/newapi/NewApiHttpClientTest.java` — NewAPI 请求、字段映射和敏感字段契约测试。
- Create: `backend/src/test/java/io/ztoken/portal/console/TokenControllerTest.java` — token 行为及越权测试。
- Create: `backend/src/test/java/io/ztoken/portal/console/LogControllerTest.java` — 日志分页、筛选、统计测试。
- Create: `backend/src/test/java/io/ztoken/portal/console/ProfileControllerTest.java` — profile 白名单和安全字段测试。

### 前端

- Modify: `frontend/src/App.tsx` — 接入 logs、profile、orders、recharge 页面。
- Modify: `frontend/src/api/portal.ts` — 增加 dashboard、token、logs、profile、order 类型及请求函数。
- Modify: `frontend/src/features/console/DashboardPage.tsx` — 四项指标、不可用 token 状态、刷新和错误状态。
- Modify: `frontend/src/features/console/TokensPage.tsx` — token CRUD、启停、删除确认和一次性明文展示。
- Create: `frontend/src/features/console/LogsPage.tsx` — 日志筛选、统计、分页。
- Create: `frontend/src/features/console/ProfilePage.tsx` — 资料表单。
- Create: `frontend/src/features/payments/AmountSelector.tsx` — 固定金额和自定义金额选择。
- Create: `frontend/src/features/payments/PaymentMethodPlaceholder.tsx` — 支付方式禁用占位。
- Create: `frontend/src/features/payments/PurchasePage.tsx` — 公共购买页面。
- Create: `frontend/src/features/payments/RechargePage.tsx` — 控制台充值页面。
- Create: `frontend/src/features/orders/OrdersPage.tsx` — 订单功能未开放的空状态页面。
- Create: `frontend/src/components/RemoteState.tsx`、`ConsolePageHeader.tsx`、`MetricCard.tsx` — 仅抽取稳定的共享 UI 语义。
- Modify: `frontend/src/i18n/locales/en.json`、`zh-CN.json`、`styles.css` — 新增翻译和响应式样式。

---

## Task B1：先确认 NewAPI 接口契约

**目标：** 在实现 BFF 前锁定当前目标 NewAPI 版本的真实请求路径、参数和响应字段。

- [ ] **Step 1：只读核对 NewAPI 代码/公开 API**

确认以下接口的实际结构：

```text
GET /api/user/self
GET /api/data/self
GET /api/data/flow/self
GET /api/token/?p=0&size=50
GET /api/token/{id}
POST /api/token/
PUT /api/token/
PUT /api/token/?status_only=true
DELETE /api/token/{id}/
GET /api/log/self
GET /api/log/self/stat
PUT /api/user/self
```

重点记录：分页字段、token 的字段名、日志响应结构、`/api/data/self` 中是否存在 token 统计字段，以及 profile 可修改字段。不要通过猜测兼容字段来掩盖版本差异。

- [ ] **Step 2：为每个接口建立 Mock 响应样例**

在测试 fixture 中只保留 Portal 所需字段。例如 dashboard 缺少 token 字段时，期望 `tokenUsage == null`；禁止把 `quota`、`tpm` 或其他指标冒充 token 数量。

- [ ] **Step 3：记录契约结论**

将最终采用的路径、参数和字段写入对应测试名称/fixture；若某个接口在当前版本不存在，Portal 应返回明确的“暂不支持”错误，而不是创建一个虚假的适配路径。

---

## Task B2：扩展 Dashboard BFF

**Files:**
- Modify: `backend/src/main/java/io/ztoken/portal/console/DashboardSummary.java`
- Modify: `backend/src/main/java/io/ztoken/portal/newapi/NewApiClient.java`
- Modify: `backend/src/main/java/io/ztoken/portal/newapi/NewApiHttpClient.java`
- Modify: `backend/src/main/java/io/ztoken/portal/console/DashboardController.java`
- Test: `backend/src/test/java/io/ztoken/portal/newapi/NewApiHttpClientTest.java`
- Test: `backend/src/test/java/io/ztoken/portal/console/DashboardControllerTest.java`

- [ ] **Step 1：写失败的 dashboard 契约测试**

覆盖：`/api/user/self` 的 `quota`、`used_quota`、`request_count` 映射；`/api/data/self` 有 token 字段时映射；没有 token 字段时为 null；请求含会话服务提供的 `Authorization` 和 `New-Api-User`；上游错误不泄露响应体。

```java
@Test
void missingTokenFieldIsNotReportedAsZero() {
    enqueueSelf("{\"quota\":1000,\"used_quota\":100,\"request_count\":8}");
    enqueueData("{\"items\":[{\"quota\":20}]}" );

    DashboardSummary result = client.getDashboard(principal);

    assertThat(result.tokenUsage()).isNull();
}
```

- [ ] **Step 2：实现明确 DTO 映射**

使用：

```java
public record DashboardSummary(
        long availableQuota,
        long usedQuota,
        long requestCount,
        Long tokenUsage
) {}
```

不将内部 quota 命名为美元余额；若 token 统计无法从真实字段获得，返回 null。Controller 只返回该 DTO，不返回 NewAPI 原始 JSON。

- [ ] **Step 3：运行测试确认通过**

```powershell
mvn -f backend/pom.xml -Dskip.frontend=true -Dtest=NewApiHttpClientTest,DashboardControllerTest test
```

Expected: PASS。

- [ ] **Step 4：提交**

```powershell
git add backend/src/main/java/io/ztoken/portal/console backend/src/main/java/io/ztoken/portal/newapi backend/src/test/java/io/ztoken/portal/newapi backend/src/test/java/io/ztoken/portal/console
git commit -m "feat: 扩展 Portal 仪表盘数据接口"
```

---

## Task B3：补齐 Token BFF

- [ ] **Step 1：写 token CRUD 和越权失败测试**

覆盖列表、创建、更新、启停、删除；所有调用必须使用 `PortalPrincipal` 的用户上下文；请求体不能接收 user ID；Token 列表不能把其他用户 token 返回；上游 4xx/5xx 转换为安全错误。

- [ ] **Step 2：实现 Portal 路由**

```text
GET    /api/console/tokens
POST   /api/console/tokens
PUT    /api/console/tokens/{id}
PUT    /api/console/tokens/{id}/status
DELETE /api/console/tokens/{id}
GET    /api/console/tokens/{id}/usage
```

将 Portal 的 `{id}` 显式转换为 NewAPI 真实请求格式。若目标 NewAPI 版本没有 token usage endpoint，返回统一的未支持响应，不猜测路径。

- [ ] **Step 3：实现敏感字段策略**

普通列表返回掩码 token；明文只在创建成功的明确响应中短暂返回，不缓存、不写日志、不保存到 Portal 数据库。响应 DTO 排除 access token、admin 字段、权限字段和其他用户信息。

- [ ] **Step 4：测试并提交**

```powershell
mvn -f backend/pom.xml -Dskip.frontend=true -Dtest=TokenControllerTest,NewApiHttpClientTest test
git add backend/src/main/java backend/src/test/java
git commit -m "feat: 补齐 Portal 令牌管理接口"
```

Expected: 测试全部通过。

---

## Task B4：补齐日志和个人资料 BFF

### 日志

- [ ] **Step 1：写失败测试**

验证 `/api/log/self` 的分页/筛选参数转换，`pageSize` 最大为 50，空列表正常返回；`/api/log/self/stat` 映射 `quota`、`rpm`、`tpm`；响应排除 `admin_info`、内部渠道和其他用户信息。

- [ ] **Step 2：实现路由和 DTO**

```text
GET /api/console/logs
GET /api/console/logs/stats
```

参数仅允许 `page`、`pageSize`、`startTimestamp`、`endTimestamp`、`modelName`、`tokenName`、`type`。筛选参数变更后由前端重置页码。

### 个人资料

- [ ] **Step 3：写 profile 白名单测试**

验证读取只返回安全字段；更新请求中的 `quota`、`used_quota`、`role`、`permission`、管理员/affiliate 字段被拒绝或忽略；服务端错误不伪造成功。

- [ ] **Step 4：实现 profile 路由**

```text
GET /api/console/profile
PUT /api/console/profile
```

只允许当前 NewAPI 版本明确支持的安全字段；用户 ID 始终来自会话，不来自浏览器请求。

- [ ] **Step 5：运行测试并提交**

```powershell
mvn -f backend/pom.xml -Dskip.frontend=true -Dtest=LogControllerTest,ProfileControllerTest,NewApiHttpClientTest test
git add backend/src/main/java backend/src/test/java
git commit -m "feat: 增加 Portal 日志与个人资料接口"
```

Expected: PASS。

---

## Task B5：统一控制台远程状态与 i18n

**Files:**
- Create: `frontend/src/components/RemoteState.tsx`
- Create: `frontend/src/components/ConsolePageHeader.tsx`
- Create: `frontend/src/components/MetricCard.tsx`
- Modify: `frontend/src/components/ConsoleLayout.tsx`
- Modify: `frontend/src/i18n/locales/en.json`
- Modify: `frontend/src/i18n/locales/zh-CN.json`
- Modify: `frontend/src/styles.css`

- [ ] **Step 1：写共享状态组件测试**

覆盖 loading、error/retry、empty 三种状态；每个状态提供可访问名称，文本进入两份 locale 文件。

- [ ] **Step 2：实现共享组件**

使用 Semi Design 的 `Spin`、`Skeleton`、`Empty`、`Button`、`Card`、`Typography`，不要引入第二套组件库。共享组件只处理稳定的视觉语义，不把不同页面的业务逻辑塞进一个通用组件。

- [ ] **Step 3：扩展控制台翻译和响应式样式**

控制台页面所有可见文本通过 `useTranslation()`；移动端侧栏折叠/抽屉、表格横向滚动、购买卡片单列或双列。状态 key 统一映射，不直接显示 NewAPI 英文状态。

- [ ] **Step 4：运行前端测试**

```powershell
cd frontend
npm test -- --run
```

Expected: 现有测试与新增测试全部通过。

- [ ] **Step 5：提交**

```powershell
git add frontend/src
git commit -m "feat: 统一 Portal 控制台状态与视觉组件"
```

---

## Task B6：实现 Dashboard 和 Token 页面

**Files:**
- Modify: `frontend/src/api/portal.ts`
- Modify: `frontend/src/features/console/DashboardPage.tsx`
- Modify: `frontend/src/features/console/TokensPage.tsx`
- Modify: `frontend/src/features/console/__tests__/dashboard-page.test.tsx`
- Modify: `frontend/src/features/console/__tests__/tokens-page.test.tsx`

- [ ] **Step 1：写页面行为测试**

Dashboard 测试四项指标；`tokenUsage: null` 显示“暂无数据”而不是 0；测试刷新、loading、error/retry。Token 测试列表、创建 Modal、编辑、启停确认、删除确认、空态、错误态和成功 mutation 后刷新列表。

- [ ] **Step 2：扩展 Portal API 类型和函数**

```ts
export interface DashboardSummary {
  availableQuota: number
  usedQuota: number
  requestCount: number
  tokenUsage: number | null
}
```

统一处理 401（回登录页）、其他错误（安全消息）；不要在前端自行把 quota 换算成美元。

- [ ] **Step 3：实现 Dashboard**

使用 Semi `Card`、`Button`、`Skeleton`、`Empty`、`Toast`。指标名称明确使用 quota 单位；快捷入口指向充值、令牌和日志。Token 缺失显示本地化不可用状态。

- [ ] **Step 4：实现 Token 页面**

使用 Semi `Table`、`Modal`、`Form`、`Tag`、`Popconfirm`、`Drawer`。创建成功的明文 token 仅在一次性 Modal 中展示并提供复制；mutation 失败不修改本地列表。

- [ ] **Step 5：运行测试和构建**

```powershell
npm test -- --run
npm run build
```

Expected: PASS，构建成功。

- [ ] **Step 6：提交**

```powershell
git add frontend/src/api/portal.ts frontend/src/features/console
git commit -m "feat: 完善 Portal 仪表盘与令牌页面"
```

---

## Task B7：实现日志、个人资料和订单空状态页面

**Files:**
- Create: `frontend/src/features/console/LogsPage.tsx`
- Create: `frontend/src/features/console/ProfilePage.tsx`
- Create: `frontend/src/features/orders/OrdersPage.tsx`
- Create/Modify: 对应三组前端测试
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/api/portal.ts`

- [ ] **Step 1：写失败页面测试**

日志覆盖筛选、分页、统计、空态和错误重试；profile 覆盖初始值、只读字段、保存 loading、成功/失败；orders 覆盖功能未开放空状态和前往充值链接。

- [ ] **Step 2：实现 LogsPage**

使用 Semi `Table`、`DatePicker`、`Select`、`Pagination`、`Card`。筛选参数使用 URL query，变更筛选后页码回到 1；表格仅展示 BFF DTO 中的用户可见字段。

- [ ] **Step 3：实现 ProfilePage**

使用 Semi `Form`；只读字段明确标识；保存失败显示字段级或通用错误；不在本阶段实现密码修改，避免不完整的会话轮换流程。

- [ ] **Step 4：实现 OrdersPage 空状态**

页面只显示“订单功能即将开放”和前往充值入口，不伪造订单、不显示支付成功状态、不调用订单 API。

- [ ] **Step 5：接入路由并验证**

将页面接入：

```text
/console/logs
/console/profile
/console/orders
```

运行：

```powershell
npm test -- --run
npm run build
```

Expected: PASS。

- [ ] **Step 6：提交**

```powershell
git add frontend/src
 git commit -m "feat: 增加 Portal 日志资料与订单页面"
```

---

## Task B8：实现购买与余额充值 UI

**Files:**
- Create: `frontend/src/features/payments/AmountSelector.tsx`
- Create: `frontend/src/features/payments/PaymentMethodPlaceholder.tsx`
- Create: `frontend/src/features/payments/PurchasePage.tsx`
- Create: `frontend/src/features/payments/RechargePage.tsx`
- Create: `frontend/src/features/payments/__tests__/purchase-page.test.tsx`
- Create: `frontend/src/features/payments/__tests__/recharge-page.test.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/components/ConsoleLayout.tsx`
- Modify: locale files and styles

- [ ] **Step 1：写失败 UI 测试**

验证固定金额全部可选：

```ts
const presetAmounts = [5, 10, 50, 100, 200, 500]
```

验证 Custom 正数/小数位校验，支付方式均为 disabled/Coming soon，继续操作不调用 fetch 或真实订单 API。

- [ ] **Step 2：实现 AmountSelector**

使用 Semi `RadioGroup`/`Card`/`InputNumber`。固定金额为美元显示；自定义金额限制为正数并限制小数位，前端校验只用于体验，不能作为支付安全边界。

- [ ] **Step 3：实现支付方式占位**

显示 PayPal、Crypto/USDT 等未来方式，但全部 disabled；继续按钮只显示“支付功能暂未开放”，不创建订单、不更新余额、不调用 NewAPI 充值接口。

- [ ] **Step 4：复用两种页面**

`PurchasePage` 包裹公共头部，`RechargePage` 包裹控制台布局；两者共享 AmountSelector 和 PaymentMethodPlaceholder，不复制金额列表或翻译文案。

- [ ] **Step 5：运行测试和构建**

```powershell
npm test -- --run
npm run build
```

Expected: PASS，构建成功。

- [ ] **Step 6：提交**

```powershell
git add frontend/src
 git commit -m "feat: 增加 Portal 购买与充值界面"
```

---

## Task B9：整体验证

- [ ] **Step 1：运行前端完整检查**

```powershell
npm --prefix F:/WorkSpace/study/AIProject/Ztoken/portal/frontend test -- --run
npm --prefix F:/WorkSpace/study/AIProject/Ztoken/portal/frontend run build
```

Expected: 测试全部通过、构建成功；chunk 体积 warning 需记录但不当作失败。

- [ ] **Step 2：运行后端完整测试**

```powershell
mvn -f F:/WorkSpace/study/AIProject/Ztoken/portal/backend/pom.xml test
```

Expected: 后端测试通过。若没有 MySQL/PostgreSQL 实例，只能报告 H2 结果，不能声称完成多数据库验证。

- [ ] **Step 3：执行 Maven 打包验证**

```powershell
mvn -f F:/WorkSpace/study/AIProject/Ztoken/portal/backend/pom.xml clean package
```

Expected: 生成 JAR 且包含 React 静态资源。本步骤不得修改或覆盖用户未提交配置。

- [ ] **Step 4：检查边界**

确认：

- `new-api` 没有改动。
- 未新增真实支付、OAuth、链上扫描或订单迁移。
- Portal 未直接访问 NewAPI 数据库。
- NewAPI access token、admin 字段和其他用户数据未返回前端。
- `application.yml` 当前未提交修改保持原样且未被加入 commit。

- [ ] **Step 5：提交验证结果**

最终交付报告列出实际命令、测试数量、构建结果和未执行的数据库矩阵；不把“测试通过”扩大解释为真实 NewAPI/支付环境已验证。

---

## 计划自审

- **Spec 覆盖：** 控制台 Semi Design、仪表盘、令牌、日志、资料、订单入口、购买/充值金额卡片、支付占位、双语、单端口兼容均有对应任务。
- **范围：** Plan B 不实现 OAuth、PayPal、TRC20 和真实订单，后续分别进入支付/OAuth 计划。
- **一致性：** Dashboard 的 `tokenUsage` 全程使用 `Long`/`number | null`；额度始终使用 quota 命名；订单页面始终是空状态；Portal 路由和前端路径一致。
- **安全：** 身份来自会话；NewAPI 管理员凭据不参与本计划；明文 token 不进入列表/日志；不触碰当前未提交配置。
- **无占位符：** 计划中的“未支持”均是明确的运行时边界，不是待实现步骤；每个实现任务均给出文件、行为、命令和验收结果。

## Task B1 契约核对补充（2026-09-01）

实现必须以当前 NewAPI 源码核对结果为准：

- `/api/data/self` 与 `/api/data/flow/self` 均为 GET，时间范围通过 `start_timestamp`、`end_timestamp` query 参数传递；self 查询由认证上下文限定用户，不接受浏览器提供的 user ID 或 username 作为身份依据。
- 两个 data self 接口存在约 30 天时间跨度限制；业务失败可能以 HTTP 200 且 `success: false` 返回，BFF 必须检查业务 success，而非只检查 HTTP 状态。
- `/api/token/` 列表分页响应为 `data: { page, page_size, total, items }`；`size`、`page_size`、`ps` 为兼容页大小参数，服务端上限为 100。
- Token 创建成功通常只有 `success`/`message`，不返回新 Token 或明文 key；完整 key 必须通过 `POST /api/token/{id}/key` 单独获取，列表和详情中的 key 是脱敏值。
- Token 删除的路由注册形式为 `DELETE /api/token/{id}`；尾斜杠只能作为兼容性测试，不得作为唯一契约。
- `/api/log/self` 返回分页对象，不是裸数组；self 日志会清理 `channel_name` 与 `other` 中的管理员/root/audit 信息。`/api/log/self/stat` 返回 `quota`、`rpm`、`tpm`，并固定按当前认证用户统计。
- `/api/user/self` 返回字段必须按 `buildSelfUserData` 映射，不直接复制 Web 类型；`PUT /api/user/self` 是分支型接口，分别处理 `display_name`、密码轮换、`language` 和字符串形式的 `sidebar_modules`。
- NewAPI 通用业务错误可能仍返回 HTTP 200；Java 客户端必须统一解析 `success` 并将失败转换为安全异常。
