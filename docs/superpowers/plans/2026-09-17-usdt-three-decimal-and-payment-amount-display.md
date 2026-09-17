# USDT 三位小数与充值金额展示 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新建 TRC20-USDT 订单以 0.001 USDT 步长生成唯一金额，并让订单及支付流程只展示美元充值金额而不展示内部额度。

**Architecture:** 后端仍以 6 位 USDT 最小单位保存和精确匹配金额，只缩小地址池识别尾数的步长；历史订单无需迁移。前端继续保留接口中的 `quotaToCredit`，但支付相关组件统一从 `amountUsdMinor` 格式化美元金额，并移除额度列、详情项和状态文案参数。

**Tech Stack:** Java 17、Spring Boot、JUnit 5、Mockito、React、TypeScript、Vitest、Testing Library、i18next

---

## 文件结构

- `backend/src/main/java/io/ztoken/portal/payment/trc20/Trc20AddressPoolService.java`：定义新订单的三位小数识别尾数范围。
- `backend/src/test/java/io/ztoken/portal/payment/trc20/Trc20AddressPoolServiceTest.java`：覆盖首个尾数、冲突递增和关闭尾数三种行为。
- `frontend/src/features/orders/OrdersPage.tsx`：删除订单列表和详情中的额度展示。
- `frontend/src/features/payments/PayPalCheckout.tsx`：只展示美元充值金额并按金额生成完成状态文案。
- `frontend/src/features/payments/Trc20Checkout.tsx`：同时展示美元充值金额与链上精确 USDT 应付金额，不展示内部额度。
- `frontend/src/features/payments/PaymentCompletePage.tsx`：完成页只保留订单号和美元充值金额。
- `frontend/src/i18n/locales/zh-CN.json`、`frontend/src/i18n/locales/en.json`：把支付流程文案从额度语义调整为充值金额语义。
- 对应 `frontend/src/features/**/__tests__/*.test.tsx`：锁定新的表格、详情、支付和完成页展示行为。

### Task 1: TRC20 三位小数识别尾数

**Files:**
- Modify: `backend/src/test/java/io/ztoken/portal/payment/trc20/Trc20AddressPoolServiceTest.java`
- Modify: `backend/src/main/java/io/ztoken/portal/payment/trc20/Trc20AddressPoolService.java`

- [ ] **Step 1: 写首个尾数、冲突递增和关闭尾数的失败测试**

将首个启用尾数订单的期望值改为 `25_001_000L`、scale 改为 `3`；增加首个金额已占用时期望 `25_002_000L` 的测试；增加关闭尾数时期望 `25_000_000L` 的测试。

- [ ] **Step 2: 运行测试确认按预期失败**

Run: `mvn -pl backend -Dtest=Trc20AddressPoolServiceTest test`

Expected: 首个尾数仍为 `25_010_000L`，三位尾数测试失败。

- [ ] **Step 3: 实现最小改动**

把 `MIN_SUFFIX`、`MAX_SUFFIX`、`SUFFIX_STEP` 分别改为 `1_000L`、`999_000L`、`1_000L`，并将注释同步为 `0.001` 至 `0.999`。

- [ ] **Step 4: 运行后端支付测试**

Run: `mvn -pl backend -Dtest=Trc20AddressPoolServiceTest,TransferVerificationServiceTest,TxidVerificationServiceTest test`

Expected: PASS；链上核验仍直接比较订单保存的 `payableMinor`。

- [ ] **Step 5: 提交后端改动**

```powershell
git add -- backend/src/main/java/io/ztoken/portal/payment/trc20/Trc20AddressPoolService.java backend/src/test/java/io/ztoken/portal/payment/trc20/Trc20AddressPoolServiceTest.java
git commit -m "支付：USDT订单改用三位小数识别金额"
```

### Task 2: 订单页移除额度展示

**Files:**
- Modify: `frontend/src/features/orders/__tests__/orders-page.test.tsx`
- Modify: `frontend/src/features/orders/OrdersPage.tsx`

- [ ] **Step 1: 写失败测试**

订单列表加载后断言只有 `$25.50`，不存在 `12,750,000`，也不存在名称为 `Quota` 的表头；打开详情后再次断言 `$25.50` 可见且 `12,750,000` 不存在。

- [ ] **Step 2: 运行测试确认失败**

Run: `npm --prefix frontend test -- --run src/features/orders/__tests__/orders-page.test.tsx`

Expected: FAIL，当前页面仍渲染额度列或详情项。

- [ ] **Step 3: 删除额度列和详情项**

从列定义中删除 `quotaToCredit` 列，从详情 `<dl>` 中删除 `orders.quota` 项，并移除不再使用的 `formatQuota` 导入。

- [ ] **Step 4: 运行订单页测试**

Run: `npm --prefix frontend test -- --run src/features/orders/__tests__/orders-page.test.tsx`

Expected: PASS。

- [ ] **Step 5: 提交订单页改动**

```powershell
git add -- frontend/src/features/orders/OrdersPage.tsx frontend/src/features/orders/__tests__/orders-page.test.tsx
git commit -m "前端：订单页仅展示充值金额"
```

### Task 3: 支付过程与完成页统一展示充值金额

**Files:**
- Modify: `frontend/src/features/payments/__tests__/paypal-checkout.test.tsx`
- Modify: `frontend/src/features/payments/__tests__/trc20-checkout.test.tsx`
- Modify: `frontend/src/features/payments/__tests__/payment-complete-page.test.tsx`
- Modify: `frontend/src/features/payments/PayPalCheckout.tsx`
- Modify: `frontend/src/features/payments/Trc20Checkout.tsx`
- Modify: `frontend/src/features/payments/PaymentCompletePage.tsx`
- Modify: `frontend/src/i18n/locales/zh-CN.json`
- Modify: `frontend/src/i18n/locales/en.json`

- [ ] **Step 1: 写失败测试**

PayPal 和 TRC20 摘要断言出现 `Recharge amount: $25.50`，不出现 `Expected credit` 或 `12,750,000`；TRC20 仍断言精确应付金额 `25.501 USDT`。完成页断言 `$25.50` 出现且内部额度不存在。

- [ ] **Step 2: 运行支付组件测试确认失败**

Run: `npm --prefix frontend test -- --run src/features/payments/__tests__/paypal-checkout.test.tsx src/features/payments/__tests__/trc20-checkout.test.tsx src/features/payments/__tests__/payment-complete-page.test.tsx`

Expected: FAIL，当前文案仍为额度语义且完成页仍渲染额度。

- [ ] **Step 3: 实现统一展示**

移除三个组件的 `formatQuota` 使用；直接用 `formatUsd(currentOrder.amountUsdMinor)` 渲染充值金额；支付成功状态不再插入内部额度。把现有 `payment.quotaEquivalent` 文案键改为充值金额语义或替换为明确的 `payment.rechargeAmount` 键，并同步中英文资源。完成页详情只保留订单号与 `orders.amount`。

- [ ] **Step 4: 运行支付组件测试**

Run: `npm --prefix frontend test -- --run src/features/payments/__tests__/paypal-checkout.test.tsx src/features/payments/__tests__/trc20-checkout.test.tsx src/features/payments/__tests__/payment-complete-page.test.tsx`

Expected: PASS。

- [ ] **Step 5: 提交支付展示改动**

```powershell
git add -- frontend/src/features/payments frontend/src/i18n/locales/zh-CN.json frontend/src/i18n/locales/en.json
git commit -m "前端：支付流程统一展示充值金额"
```

### Task 4: 回归验证

**Files:**
- Verify only

- [ ] **Step 1: 运行后端全量测试**

Run: `mvn -pl backend test`

Expected: PASS。

- [ ] **Step 2: 运行前端全量测试**

Run: `npm --prefix frontend test -- --run`

Expected: PASS。

- [ ] **Step 3: 运行前端生产构建**

Run: `npm --prefix frontend run build`

Expected: 构建成功，无 TypeScript 或 Vite 错误。

- [ ] **Step 4: 检查差异**

Run: `git diff HEAD~3 --check`

Expected: 无空白错误；改动只覆盖设计范围。
