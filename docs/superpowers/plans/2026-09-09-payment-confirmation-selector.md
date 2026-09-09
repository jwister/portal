# Payment Confirmation Selector Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Replace per-method payment buttons with selectable icon/text cards and one bottom confirmation button, and show payment order estimates in dollars.

**Architecture:** PaymentSelectionPanel owns selected payment method state and invokes the existing order creation path only through one confirmation button. PayPalCheckout and Trc20Checkout format immutable order amounts for estimates and never expose quotaToCredit there.

**Tech Stack:** React 19, TypeScript, Semi UI, react-i18next, Vitest, Testing Library, CSS.

---

### Task 1: Define the shared confirmation behavior

**Files:**

- Modify: frontend/src/features/payments/__tests__/purchase-page.test.tsx
- Modify: frontend/src/features/payments/__tests__/recharge-page.test.tsx

- [ ] **Step 1: Write failing purchase assertions**

Replace the per-method button click in the PayPal purchase test with:

~~~tsx
    await user.click(screen.getByRole('button', { name: 'Confirm payment' }))
~~~

Append:

~~~tsx
  it('submits the selected TRC20 method only when payment is confirmed', async () => {
    const user = userEvent.setup()
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    render(<PurchasePage />)

    const trc20 = screen.getByRole('radio', { name: 'TRC20 USDT' })
    expect(screen.getByRole('radio', { name: 'PayPal' })).toBeChecked()
    await user.click(trc20)
    expect(trc20).toBeChecked()
    expect(fetchMock).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: 'Confirm payment' }))
    expect(fetchMock).toHaveBeenCalledWith('/api/payments/orders', expect.objectContaining({
      body: JSON.stringify({ amount: '5', method: 'USDT_TRC20' }),
    }))
  })
~~~

- [ ] **Step 2: Update failing recharge expectations**

Replace per-method button assertions with:

~~~tsx
    expect(screen.getByRole('radio', { name: 'PayPal' })).toBeChecked()
    expect(screen.getByRole('radio', { name: 'TRC20 USDT' })).not.toBeChecked()
    expect(screen.getByRole('button', { name: 'Confirm payment' })).toBeVisible()
    expect(screen.queryByText(/Authorize the charge/i)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Continue with PayPal' })).not.toBeInTheDocument()
~~~

Change its final click to Confirm payment.

- [ ] **Step 3: Run tests to verify failure**

Run: npm test -- src/features/payments/__tests__/purchase-page.test.tsx src/features/payments/__tests__/recharge-page.test.tsx

Expected: FAIL because radios and Confirm payment are absent.

- [ ] **Step 4: Implement selected cards and confirmation button**

Modify: frontend/src/features/payments/PaymentSelectionPanel.tsx

Add selected state and replace handleMethod with:

~~~tsx
  const [method, setMethod] = useState<PaymentMethod>('PAYPAL')
  const handleConfirm = async () => {
    if (!amountIsUsable) return
    setError(null)
    setSubmitting(true)
    try {
      onConfirm(await createPaymentOrder({ amount, method }))
    } catch {
      setError(t('payment.createFailed'))
    } finally {
      setSubmitting(false)
    }
  }
~~~

Replace each card body with a label that has one radio, its existing image and a text span. Use className={method === 'PAYPAL' ? 'payment-method-card is-selected' : 'payment-method-card'} for PayPal and the equivalent USDT condition for TRC20. Render only this button after the payment grid:

~~~tsx
      <Button className="payment-confirm-button" theme="solid" type="primary" block disabled={!amountIsUsable || submitting} loading={submitting} onClick={() => { void handleConfirm() }}>
        {t('payment.confirm')}
      </Button>
~~~

Remove Card, Space, Tag imports, channel descriptions, tags and per-method buttons.

- [ ] **Step 5: Add CSS and translations**

Modify: frontend/src/styles/public-ledger.css

~~~css
.payment-method-card { align-items:center; cursor:pointer; display:flex; gap:12px; min-height:92px; padding:18px; }
.payment-method-card input { appearance:none; border:1px solid var(--ledger-line); border-radius:50%; height:18px; margin:0; width:18px; }
.payment-method-card input:checked { border:5px solid var(--ledger-green); }
.payment-method-card.is-selected { background:var(--ledger-soft); border-color:var(--ledger-green); }
.payment-method-card .payment-method-logo { height:30px; max-width:112px; }
.payment-method-card > span { color:var(--ledger-ink); font-weight:700; }
.payment-confirm-button { margin-top:4px; min-height:46px; }
~~~

Modify frontend/src/i18n/locales/zh-CN.json:

~~~json
"purchase.copy": "选择金额，确认支付后，等待额度到账。",
"payment.confirm": "确认支付",
"payment.quotaEquivalent": "预计入账 {{amount}}"
~~~

Modify frontend/src/i18n/locales/en.json:

~~~json
"purchase.copy": "Choose an amount, confirm payment, then wait for your balance to be credited.",
"payment.confirm": "Confirm payment",
"payment.quotaEquivalent": "Expected credit: {{amount}}"
~~~

- [ ] **Step 6: Run tests to verify success**

Run: npm test -- src/features/payments/__tests__/purchase-page.test.tsx src/features/payments/__tests__/recharge-page.test.tsx

Expected: PASS and payloads contain only amount and selected method.

- [ ] **Step 7: Commit**

~~~powershell
git add -- frontend/src/features/payments/PaymentSelectionPanel.tsx frontend/src/i18n/locales/en.json frontend/src/i18n/locales/zh-CN.json frontend/src/styles/public-ledger.css frontend/src/features/payments/__tests__/purchase-page.test.tsx frontend/src/features/payments/__tests__/recharge-page.test.tsx
git commit -m "feat: add shared payment confirmation selector"
~~~

### Task 2: Show checkout estimates in dollars

**Files:**

- Modify: frontend/src/features/payments/__tests__/paypal-checkout.test.tsx
- Modify: frontend/src/features/payments/__tests__/trc20-checkout.test.tsx
- Modify: frontend/src/features/payments/PayPalCheckout.tsx
- Modify: frontend/src/features/payments/Trc20Checkout.tsx

- [ ] **Step 1: Write failing USD assertions**

In both checkout tests, assert ledger summaries contain Expected credit: $25.50 and do not contain 12,750,000.

- [ ] **Step 2: Run tests to verify failure**

Run: npm test -- src/features/payments/__tests__/paypal-checkout.test.tsx src/features/payments/__tests__/trc20-checkout.test.tsx

Expected: FAIL because summaries format quotaToCredit.

- [ ] **Step 3: Pass compact dollars to the translation**

In PayPalCheckout:

~~~tsx
  const creditText = amountText.endsWith('.00') ? amountText.slice(0, -3) : amountText
~~~

Pass amount: creditText to quotaEquivalent.

In Trc20Checkout import formatUsd, define:

~~~tsx
  const creditText = formatUsd(current.amountUsdMinor).replace(/\.00$/, '')
~~~

Pass amount: creditText to quotaEquivalent.

- [ ] **Step 4: Run tests to verify success**

Run: npm test -- src/features/payments/__tests__/paypal-checkout.test.tsx src/features/payments/__tests__/trc20-checkout.test.tsx

Expected: PASS; SDK, polling, TxID and status behavior remains unchanged.

- [ ] **Step 5: Commit**

~~~powershell
git add -- frontend/src/features/payments/PayPalCheckout.tsx frontend/src/features/payments/Trc20Checkout.tsx frontend/src/features/payments/__tests__/paypal-checkout.test.tsx frontend/src/features/payments/__tests__/trc20-checkout.test.tsx
git commit -m "feat: show payment estimates in dollars"
~~~

### Task 3: Verify production payment paths

- [ ] **Step 1: Run all frontend tests**

Run: npm test

Expected: PASS.

- [ ] **Step 2: Build production output**

Run: npm run build

Expected: vite build succeeds.

- [ ] **Step 3: Inspect desktop and mobile**

Run: npm run dev -- --host 127.0.0.1

At /purchase and /console/recharge, confirm PayPal starts selected, cards use visible radio/focus state, no descriptions or per-method buttons exist, Confirm payment sits beneath cards, and checkout summaries display dollars.
