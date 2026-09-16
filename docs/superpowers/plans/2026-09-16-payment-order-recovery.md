# 支付订单恢复处理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让用户在订单管理页双击订单后恢复处理待支付订单：所有待支付订单可取消，待支付 TRC20-USDT 订单可补交或重新提交 TxID。

**Architecture:** 后端在 `PaymentOrderService` 中增加事务化取消操作，复用 `PaymentOrderRepository.findByOrderNoForUpdate` 的悲观锁和 `PaymentOrder.cancel` 的状态机。前端在订单页增加详情弹窗，后端订单详情是唯一状态来源；成功取消或提交 TxID 后刷新弹窗与订单列表。

**Tech Stack:** Spring Boot、Spring Data JPA、JUnit 5、React 19、TypeScript、Semi UI、Vitest、React Testing Library、i18next。

---

## 文件结构

- `backend/src/main/java/io/ztoken/portal/payment/order/PaymentOrderService.java`：当前用户的加锁取消服务。
- `backend/src/main/java/io/ztoken/portal/payment/api/PaymentOrderController.java`：取消订单 HTTP API。
- `backend/src/test/java/io/ztoken/portal/payment/api/PaymentOrderControllerTest.java`：取消 API 集成测试。
- `frontend/src/api/portal.ts`：取消订单请求函数。
- `frontend/src/features/orders/OrdersPage.tsx`：双击详情、取消确认、TRC20 恢复支付。
- `frontend/src/features/orders/__tests__/orders-page.test.tsx`：订单恢复 UI 测试。
- `frontend/src/i18n/locales/zh-CN.json` 与 `frontend/src/i18n/locales/en.json`：订单恢复文案。

### Task 1: 后端取消订单能力

**Files:**

- Modify: `backend/src/main/java/io/ztoken/portal/payment/order/PaymentOrderService.java`
- Modify: `backend/src/main/java/io/ztoken/portal/payment/api/PaymentOrderController.java`
- Test: `backend/src/test/java/io/ztoken/portal/payment/api/PaymentOrderControllerTest.java`

- [ ] **Step 1: 编写取消成功的失败测试**

在 `PaymentOrderControllerTest` 中以当前会话用户创建 PayPal 待支付订单，并断言 `POST /api/payments/orders/{orderNo}/cancel` 成功、带 `Cache-Control: no-store`、返回订单号和 `CANCELLED`。

```java
ResponseEntity<Map> response = http.exchange("/api/payments/orders/" + order.orderNo() + "/cancel",
        HttpMethod.POST, authed(null, sessionId), Map.class);
assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
assertThat(response.getBody()).containsEntry("status", "CANCELLED");
```

- [ ] **Step 2: 编写越权和状态冲突的失败测试**

另一会话取消订单断言 `404 PAYMENT_ORDER_NOT_FOUND`。将当前用户订单以 `persisted.confirm(Instant.now())` 转为已确认并 `saveAndFlush` 后取消，断言 `409 PAYMENT_ACTION_CONFLICT`；不得从请求体伪造状态。

- [ ] **Step 3: 运行测试确认失败**

Run: `./mvnw.cmd -Dtest=PaymentOrderControllerTest test`

Expected: 新增测试返回 `404`，因为取消路由尚不存在。

- [ ] **Step 4: 实现加锁、按用户授权的取消服务**

在 `PaymentOrderService` 添加 `@Transactional` 的 `cancelForUser(PortalPrincipal principal, String orderNo)`。方法使用 `orders.findByOrderNoForUpdate(orderNo)`，过滤 `getNewApiUserId() == principal.userId()`，不匹配抛 `NoSuchElementException`。调用 `order.cancel(Instant.now())`；返回 `false` 时记录中文告警并抛 `IllegalStateException`；成功时 `orders.save(order)`、记录中文信息日志并返回 `PaymentOrderView.from(saved)`。添加中文方法注释。不得接受客户端金额、地址、状态或用户 ID。

- [ ] **Step 5: 实现控制器端点和安全错误映射**

在 `PaymentOrderController` 新增：

```java
@PostMapping("/{orderNo}/cancel")
public ResponseEntity<PaymentOrderResponse> cancel(
        @CookieValue(value = "PORTAL_SESSION", required = false) String sessionId,
        @PathVariable String orderNo) {
    PortalPrincipal principal = sessions.require(sessionId);
    try {
        return noStore(ResponseEntity.ok(PaymentOrderResponse.from(orders.cancelForUser(principal, orderNo))));
    } catch (java.util.NoSuchElementException exception) {
        throw PaymentApiException.orderNotFound();
    } catch (IllegalStateException exception) {
        throw PaymentApiException.conflict(exception);
    }
}
```

在调用前、越权/不存在和状态冲突分支增加中文日志，日志中仅包含订单号、用户 ID、状态或异常类型。

- [ ] **Step 6: 运行后端测试确认通过**

Run: `./mvnw.cmd -Dtest=PaymentOrderControllerTest test`

Expected: 创建、列表、详情和新增三类取消测试全部通过。

- [ ] **Step 7: 提交后端改动**

Run: `git add backend/src/main/java/io/ztoken/portal/payment/order/PaymentOrderService.java backend/src/main/java/io/ztoken/portal/payment/api/PaymentOrderController.java backend/src/test/java/io/ztoken/portal/payment/api/PaymentOrderControllerTest.java; git commit -m "支持用户取消待支付订单"`

### Task 2: 前端 API 与文案

**Files:**

- Modify: `frontend/src/api/portal.ts`
- Modify: `frontend/src/i18n/locales/zh-CN.json`
- Modify: `frontend/src/i18n/locales/en.json`
- Test: `frontend/src/features/orders/__tests__/orders-page.test.tsx`

- [ ] **Step 1: 先增加取消请求的失败断言**

在订单页测试中模拟待支付 PayPal 订单并触发取消；断言请求为：

```ts
expect(fetchMock).toHaveBeenCalledWith('/api/payments/orders/PO-1/cancel', expect.objectContaining({
  method: 'POST', credentials: 'include',
}))
```

- [ ] **Step 2: 添加取消订单 API**

在 `portal.ts` 的订单 API 区添加：

```ts
/** 仅请求服务端取消当前会话所属的待支付订单。 */
export function cancelPaymentOrder(orderNo: string): Promise<PaymentOrder> {
  return requestJson<PaymentOrder>(`/api/payments/orders/${encodeURIComponent(orderNo)}/cancel`, { method: 'POST' })
}
```

- [ ] **Step 3: 增加双语订单恢复文案**

在两份 locale 追加同名 `orders` 键：`detailTitle`、`detailHint`、`cancel`、`cancelConfirmTitle`、`cancelConfirmContent`、`cancelSuccess`、`cancelError`、`trc20Recovery`、`trc20RecoveryHint`、`txidSubmitError`、`detailLoadError`、`close`。中文文案分别表达“订单详情”“双击订单可查看详情并恢复待支付操作”“取消订单”“确认取消订单？”“取消后订单将无法继续支付，且不能恢复”“订单已取消”“取消订单失败，请刷新后重试”“TRC20 支付恢复”“请确认链上转账后补交交易哈希（TxID）”“提交 TxID 失败，请稍后重试”“读取订单详情失败，请刷新后重试”“关闭”。复用所有已有 `payment.trc20*` 与 `payment.trc20Result.*` 键。

- [ ] **Step 4: 运行定向测试确认 UI 尚未实现而失败**

Run: `npm test -- --run src/features/orders/__tests__/orders-page.test.tsx`

Expected: 新交互断言失败，原订单列表测试通过。

- [ ] **Step 5: 提交 API 与文案**

Run: `git add frontend/src/api/portal.ts frontend/src/i18n/locales/zh-CN.json frontend/src/i18n/locales/en.json frontend/src/features/orders/__tests__/orders-page.test.tsx; git commit -m "补充订单恢复操作接口与文案"`

### Task 3: 双击订单详情和恢复操作 UI

**Files:**

- Modify: `frontend/src/features/orders/OrdersPage.tsx`
- Test: `frontend/src/features/orders/__tests__/orders-page.test.tsx`

- [ ] **Step 1: 编写双击详情和只读订单失败测试**

引入 `userEvent`，双击 `PO-1` 所在 `tr`，断言 `Modal` 显示订单详情、订单号和状态。以 `PAID` 订单断言不出现“取消订单”按钮或 TxID 输入框：

```ts
await user.dblClick(screen.getByText('PO-1').closest('tr')!)
expect(await screen.findByRole('dialog', { name: /Order details/ })).toBeVisible()
expect(screen.queryByRole('button', { name: /Cancel order/ })).not.toBeInTheDocument()
expect(screen.queryByLabelText(/TxID/)).not.toBeInTheDocument()
```

- [ ] **Step 2: 编写待支付取消和刷新失败测试**

依次模拟初始列表、`POST /cancel` 返回 `CANCELLED`、刷新后列表。双击待支付 PayPal 订单、打开取消确认框、确认操作，断言取消端点调用和刷新列表显示已取消状态。使用 `waitFor` 等待刷新。

- [ ] **Step 3: 编写 TRC20 补交 TxID 失败测试**

为 `USDT_TRC20` 待支付订单模拟 `GET /trc20/status`，返回地址 `TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE`、金额 `1.000001 USDT` 和 `SUBMITTED`。双击后断言地址、金额、输入框可见；输入 `a`.repeat(64) 并提交，断言 POST body 为 `JSON.stringify({ txid: 'a'.repeat(64) })`，`CONFIRMED` 响应显示已有转账确认提示。对待支付 PayPal 订单断言没有 TxID 控件。

- [ ] **Step 4: 运行测试确认失败**

Run: `npm test -- --run src/features/orders/__tests__/orders-page.test.tsx`

Expected: 新增弹窗、取消、TxID 测试失败。

- [ ] **Step 5: 增加详情状态、加载和双击行回调**

在 `OrdersPage` 引入 `Modal`、`Input`、`Toast` 及 `getPaymentOrder`、`getTrc20PaymentStatus`、`submitTrc20Txid`、`cancelPaymentOrder`。维护 `selectedOrderNo`、`selectedOrder`、`trc20Instruction`、`txid`、`busy`、`cancelConfirmVisible` 状态。将列表加载抽成 `loadOrders(showLoading)`；打开详情先读 `getPaymentOrder`，仅 TRC20 时再读指引。失败 Toast 提示并关闭详情。为 `Table` 设置：

```tsx
onRow={(record) => ({ onDoubleClick: () => { void openDetail(record.orderNo) } })}
```

- [ ] **Step 6: 渲染受控的只读详情与操作条件**

在表格后渲染详情 `Modal`，展示订单号、金额、额度、方式、创建/到期时间、原有 `statusTag`。条件只能派生自服务端详情：

```ts
const canCancel = selectedOrder?.status === 'WAITING_PAYMENT'
const canRecoverTrc20 = canCancel && selectedOrder?.method === 'USDT_TRC20'
```

关闭时清空详情、TRC20 指引、TxID、busy 和确认状态，避免订单之间泄露输入值。

- [ ] **Step 7: 实现取消确认和刷新**

仅 `canCancel` 显示取消按钮及确认 `Modal`。确认函数在 `busy === 'cancel'` 期间禁用重复操作；成功后以 `cancelPaymentOrder` 的服务端订单覆盖详情、清空 TRC20 指引、Toast 成功并 `await loadOrders(false)`；失败后 Toast 错误，再读详情和刷新列表。确认框在忙碌时不得关闭。

- [ ] **Step 8: 实现 TRC20 收款指引和 TxID 补交**

仅 `canRecoverTrc20 && trc20Instruction` 显示服务端的地址、精确金额、复制按钮、TxID 输入和提交按钮。复制复用 `navigator.clipboard.writeText` 与已有 `payment.trc20Copied`/`payment.trc20CopyError` 文案。提交时禁用输入与按钮，调用 `submitTrc20Txid`，按已有 `payment.trc20Result.${result.result}` 提示，并无论成功失败都刷新详情及列表；保留 TxID 输入供用户核对或重试。

- [ ] **Step 9: 验证前端测试和生产构建**

Run: `npm test -- --run src/features/orders/__tests__/orders-page.test.tsx`

Expected: 原有列表、只读详情、取消、TRC20 及非 TRC20 断言全部通过。

Run: `npm run build`

Expected: TypeScript、Vite、预渲染和构建校验全部成功。

- [ ] **Step 10: 提交前端恢复界面**

Run: `git add frontend/src/features/orders/OrdersPage.tsx frontend/src/features/orders/__tests__/orders-page.test.tsx; git commit -m "支持订单页恢复TRC20支付"`

### Task 4: 支付回归和工作区检查

**Files:**

- Modify: `docs/superpowers/plans/2026-09-16-payment-order-recovery.md`（仅勾选实际完成步骤）

- [ ] **Step 1: 运行支付 API 回归测试**

Run: `./mvnw.cmd -Dtest=PaymentOrderControllerTest,Trc20PaymentControllerTest test`

Expected: 订单创建、列表、详情、取消、TRC20 指引和既有 TxID 行为均通过。

- [ ] **Step 2: 运行订单与支付前端测试**

Run: `npm test -- --run src/features/orders src/features/payments`

Expected: 相关测试通过，且没有未处理 Promise 警告。

- [ ] **Step 3: 检查改动边界和敏感配置**

Run: `git status --short; git diff --check HEAD; git log --oneline -3`

Expected: 无空白错误；`backend/src/main/resources/application.yml` 如仍显示修改，属于用户既有修改，绝不暂存或提交。
