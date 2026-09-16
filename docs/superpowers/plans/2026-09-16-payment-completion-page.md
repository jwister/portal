# 支付完成页 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 PayPal 或 TRC20-USDT 订单实际到账后显示独立支付完成页，提供五秒自动跳转概览和立即创建 API Key 的入口。

**Architecture:** 新增受会话保护但不列入控制台导航的 `/console/payment-complete` 路由。完成页只通过订单号重读服务端订单，只有 `PAID` 启动五秒倒计时；充值页将两个渠道已有的订单回调统一为仅 `PAID` 时导航完成页。

**Tech Stack:** React 19、TypeScript、Semi UI、i18next、Vitest、React Testing Library。

---

## 文件结构

- `frontend/src/features/payments/PaymentCompletePage.tsx`：服务端订单读取、成功/失败状态、倒计时、跳转。
- `frontend/src/features/payments/payment-complete-page.css`：完成页响应式样式。
- `frontend/src/features/payments/RechargePage.tsx`：付款实际到账时导航。
- `frontend/src/ConsoleRoutes.tsx`、`frontend/src/App.tsx`：受保护的非导航完成页路由。
- `frontend/src/i18n/locales/zh-CN.json`、`frontend/src/i18n/locales/en.json`：完成页文案。
- `frontend/src/features/payments/__tests__/payment-complete-page.test.tsx`：页面与计时测试。
- `frontend/src/features/payments/__tests__/recharge-page.test.tsx`、`frontend/src/__tests__/app-shell.test.tsx`：付款衔接和直接路由测试。

### Task 1: 完成页组件、文案和倒计时

**Files:**

- Create: `frontend/src/features/payments/PaymentCompletePage.tsx`
- Create: `frontend/src/features/payments/payment-complete-page.css`
- Create: `frontend/src/features/payments/__tests__/payment-complete-page.test.tsx`
- Modify: `frontend/src/i18n/locales/zh-CN.json`
- Modify: `frontend/src/i18n/locales/en.json`

- [ ] **Step 1: 写 PAID 成功页的失败测试**

在新测试中设地址为 `/console/payment-complete?orderNo=PO-PAID-1`，mock `GET /api/payments/orders/PO-PAID-1` 返回 `PAID` 订单，断言标题、订单号、`$25.50`、`12,750,000`、初始 5 秒和立即创建 API Key：

```ts
expect(await screen.findByRole('heading', { name: 'Payment complete' })).toBeVisible()
expect(screen.getByText('PO-PAID-1')).toBeVisible()
expect(screen.getByText('$25.50')).toBeVisible()
expect(screen.getByText('12,750,000')).toBeVisible()
expect(screen.getByText(/5 seconds/)).toBeVisible()
expect(screen.getByRole('button', { name: 'Create API key now' })).toBeVisible()
```

- [ ] **Step 2: 写自动与手动跳转的失败测试**

使用 `vi.useFakeTimers()` 与 `vi.spyOn(window.location, 'assign')`。订单读取后推进 `5_000` 毫秒并断言 `assign('/console/dashboard')`。独立测试点击按钮断言 `assign('/console/tokens')`，再推进 5 秒仍只导航一次；每项测试恢复真实计时器与 spy。

- [ ] **Step 3: 写非 PAID、读取失败和缺少订单号的失败测试**

分别返回 `CONFIRMED`、404 及没有 `orderNo` 的地址。断言不出现成功标题或创建按钮、不会调用 `location.assign`，而显示“订单尚未到账”或“无法读取订单”与返回订单管理入口。

- [ ] **Step 4: 确认测试失败**

Run: `npm test -- --run src/features/payments/__tests__/payment-complete-page.test.tsx`

Expected: FAIL，`PaymentCompletePage` 模块尚不存在。

- [ ] **Step 5: 补齐双语文案**

在两份 locale 新增 `payment.complete.*`：`eyebrow`、`title`、`description`、`returning`、`createApiKey`、`loading`、`notPaidTitle`、`notPaidDescription`、`loadErrorTitle`、`loadErrorDescription`、`backToOrders`。中文值分别为“支付完成”“充值成功”“额度已发放到你的账户。”“{{seconds}} 秒后自动跳转到概览页”“立即创建 API Key”“正在确认订单状态…”“订单尚未到账”“订单仍在处理中，到账后将显示支付完成信息。”“无法读取订单”“请返回订单管理后刷新状态。”“返回订单管理”；英文使用对应翻译。

- [ ] **Step 6: 实现服务端订单唯一来源**

实现 `PaymentCompletePage`，从 `new URLSearchParams(window.location.search)` 读取 `orderNo`。用以下状态读取 `getPaymentOrder(orderNo)`；无参数不请求 API：

```ts
type CompletionState =
  | { kind: 'loading' }
  | { kind: 'paid'; order: PaymentOrder }
  | { kind: 'not-paid' }
  | { kind: 'error' }
```

只有 `order.status === 'PAID'` 才设置 `paid`。成功区域使用 `formatUsd(order.amountUsdMinor)` 与 `formatQuota(order.quotaToCredit)`，绝不从 URL 接受金额、额度、用户或状态；非 `PAID`、404、网络失败不渲染成功信息。

- [ ] **Step 7: 实现安全倒计时与跳转**

仅 `paid` 状态从 5 创建 `window.setInterval`。每秒减一，降到 0 时先清理计时器后 `window.location.assign('/console/dashboard')`。创建 Key 按钮先清计时器，再 `window.location.assign('/console/tokens')`。`useEffect` cleanup 在组件卸载、订单号变化、失败与非 PAID 时清理；不允许背景跳转。

- [ ] **Step 8: 实现样式并验证通过**

完成页以 `<main className="payment-complete-page" aria-live="polite">` 包裹；CSS 复用 Portal 绿色控制台变量，提供居中成功卡片、成功图标、金额/额度信息和移动端单列布局。

Run: `npm test -- --run src/features/payments/__tests__/payment-complete-page.test.tsx`

Expected: PAID 展示、五秒跳概览、手动跳 Key 停止倒计时、非 PAID/失败安全状态均通过。

- [ ] **Step 9: 提交完成页**

Run: `git add src/features/payments/PaymentCompletePage.tsx src/features/payments/payment-complete-page.css src/features/payments/__tests__/payment-complete-page.test.tsx src/i18n/locales/en.json src/i18n/locales/zh-CN.json; git commit -m "新增支付完成倒计时页面"`

### Task 2: 路由和两种支付渠道衔接

**Files:**

- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/ConsoleRoutes.tsx`
- Modify: `frontend/src/features/payments/RechargePage.tsx`
- Modify: `frontend/src/features/payments/__tests__/recharge-page.test.tsx`
- Modify: `frontend/src/__tests__/app-shell.test.tsx`

- [ ] **Step 1: 写直接完成页路由失败测试**

在 `app-shell.test.tsx` 设 `/console/payment-complete?orderNo=PO-PAID-1`，mock认证成功和 PAID 订单，动态加载后断言“Payment complete”。该测试确认完成页仍经过 `useAuthStatus` 认证保护。

- [ ] **Step 2: 写充值页仅 PAID 导航的失败测试**

mock checkout 并取得 `RechargePage` 传入的 `onCompleted`。传 `CONFIRMED` 断言未调用 `window.location.assign`；传 `PAID` 断言：

```ts
expect(assign).toHaveBeenCalledWith('/console/payment-complete?orderNo=PO-1')
```

同时断言 URL 不含金额、额度、支付方式和用户字段。

- [ ] **Step 3: 确认路由与衔接测试失败**

Run: `npm test -- --run src/__tests__/app-shell.test.tsx src/features/payments/__tests__/recharge-page.test.tsx`

Expected: FAIL，当前完成页不是控制台路由，充值页仍留在 checkout。

- [ ] **Step 4: 增加受保护但不进侧栏的路由**

在 `App.tsx` 的控制台路径正则加入 `payment-complete`。在 `ConsoleRoutes.tsx` lazy load 组件，并在 `ConsoleLayout` 前处理：

```tsx
if (path === '/console/payment-complete') {
  return <Suspense fallback={<RemoteState kind="loading" />}><PaymentCompletePage /></Suspense>
}
```

不得将它转换为 `ConsoleKey`、加入 `ConsoleLayout` 或侧栏；匿名用户保留既有含查询参数的登录回跳。

- [ ] **Step 5: 统一充值页 PAID 导航**

保留 `setOrder(next)`，仅 `next.status === 'PAID'` 时刷新 `getDashboard()` 并跳转：

```ts
if (next.status === 'PAID') {
  void getDashboard().catch(() => {})
  window.location.assign(`/console/payment-complete?${new URLSearchParams({ orderNo: next.orderNo })}`)
}
```

`CONFIRMED`、`CREDITING`、`CREDIT_FAILED`、`CREDIT_UNKNOWN`、`EXPIRED` 和 `CANCELLED` 不跳转。不得在 `PayPalCheckout` 或 `Trc20Checkout` 内重复构建 URL。

- [ ] **Step 6: 验证路由和充值页测试**

Run: `npm test -- --run src/__tests__/app-shell.test.tsx src/features/payments/__tests__/recharge-page.test.tsx src/features/payments/__tests__/payment-complete-page.test.tsx`

Expected: 直接路由通过认证、只有 PAID 跳完成页、倒计时测试全通过。

- [ ] **Step 7: 提交支付衔接**

Run: `git add src/App.tsx src/ConsoleRoutes.tsx src/features/payments/RechargePage.tsx src/features/payments/__tests__/recharge-page.test.tsx src/__tests__/app-shell.test.tsx; git commit -m "支付到账后跳转完成页"`

### Task 3: 回归、构建与子模块交付

**Files:**

- Modify: `docs/superpowers/plans/2026-09-16-payment-completion-page.md`（仅勾选实际完成步骤）

- [ ] **Step 1: 支付与路由回归**

Run: `npm test -- --run src/features/payments src/__tests__/app-shell.test.tsx`

Expected: TRC20、PayPal、充值页、完成页和应用路由测试全部通过，处理中订单不会被误报成功。

- [ ] **Step 2: 前端生产构建**

Run: `npm run build`

Expected: TypeScript、Vite、预渲染和构建校验全部通过。

- [ ] **Step 3: 检查边界并更新门户引用**

在前端子模块运行 `git status --short; git diff --check`，确认只包含本计划文件；提交前端后，在 Portal 根目录运行 `git add frontend; git commit -m "更新支付完成页前端模块"`。`backend/src/main/resources/application.yml` 如仍修改，属于用户原有配置，禁止暂存或提交。
