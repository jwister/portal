# Shared Icon Payment Selector Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Render only PayPal and TRC20-USDT as icon-led payment cards in the one component shared by public purchase and console recharge pages.

**Architecture:** PaymentSelectionPanel remains the sole amount-selection and order-creation owner. It imports the two local image assets and maps each supported PaymentMethod to a card; both page containers keep consuming that component unchanged.

**Tech Stack:** React 19, TypeScript, Semi UI, Vitest, Testing Library, CSS.

---

### Task 1: Define shared-card behavior through both page tests

**Files:**

- Modify: frontend/src/features/payments/__tests__/purchase-page.test.tsx
- Modify: frontend/src/features/payments/__tests__/recharge-page.test.tsx

- [ ] **Step 1: Write failing assertions for the public purchase page**

Append this test to purchase-page.test.tsx:

~~~tsx
  it('renders exactly the two supported payment icons', () => {
    render(<PurchasePage />)

    expect(screen.getByAltText('PayPal')).toHaveAttribute('src', '/src/public/Paypal.png')
    expect(screen.getByAltText('TRC20 USDT')).toHaveAttribute('src', '/src/public/Tron.png')
    expect(screen.queryByText('Other payment method')).not.toBeInTheDocument()
    expect(screen.queryByText('Coming soon')).not.toBeInTheDocument()
  })
~~~

- [ ] **Step 2: Update the existing recharge expectation before running it**

In recharge-page.test.tsx, replace these assertions:

~~~tsx
    expect(screen.getByText('Other payment method')).toBeVisible()
    expect(screen.getAllByText('Coming soon')).toHaveLength(1)
~~~

with:

~~~tsx
    expect(screen.getByAltText('PayPal')).toHaveAttribute('src', '/src/public/Paypal.png')
    expect(screen.getByAltText('TRC20 USDT')).toHaveAttribute('src', '/src/public/Tron.png')
    expect(screen.queryByText('Other payment method')).not.toBeInTheDocument()
    expect(screen.queryByText('Coming soon')).not.toBeInTheDocument()
~~~

- [ ] **Step 3: Verify both tests fail**

Run: npm test -- src/features/payments/__tests__/purchase-page.test.tsx src/features/payments/__tests__/recharge-page.test.tsx

Expected: FAIL because neither accessible image exists and the other-method card is still rendered.

- [ ] **Step 4: Commit the red tests**

~~~powershell
git add -- frontend/src/features/payments/__tests__/purchase-page.test.tsx frontend/src/features/payments/__tests__/recharge-page.test.tsx
git commit -m "test: define supported payment icon cards"
~~~

### Task 2: Implement the two-card shared selector

**Files:**

- Modify: frontend/src/features/payments/PaymentSelectionPanel.tsx
- Modify: frontend/src/styles/public-ledger.css

- [ ] **Step 1: Import the local assets**

Add these imports after the AmountSelector import:

~~~tsx
import paypalLogo from '../../public/Paypal.png'
import tronLogo from '../../public/Tron.png'
~~~

- [ ] **Step 2: Replace the payment method cards**

Keep the PaymentMethod grid heading. Replace the three existing Card elements with exactly these two cards:

~~~tsx
        <Card className="payment-method-card payment-method-card--paypal" title={<img className="payment-method-logo" src={paypalLogo} alt="PayPal" />}>
          <Space spacing={8} align="center"><Tag color="green">{t('payment.paypalAvailable')}</Tag></Space>
          <Typography.Paragraph type="tertiary" className="payment-method-description">{t('payment.paypalDescription')}</Typography.Paragraph>
          <Button theme="solid" type="primary" block disabled={!amountIsUsable || submitting} loading={submitting} onClick={() => { void handleMethod('PAYPAL') }}>
            {t('payment.continuePaypal')}
          </Button>
        </Card>
        <Card className="payment-method-card payment-method-card--trc20" title={<img className="payment-method-logo" src={tronLogo} alt="TRC20 USDT" />}>
          <Typography.Paragraph type="tertiary" className="payment-method-description">{t('payment.trc20Description')}</Typography.Paragraph>
          <Button theme="solid" type="primary" block disabled={!amountIsUsable || submitting} onClick={() => { void handleMethod('USDT_TRC20') }}>
            {t('payment.continueTrc20')}
          </Button>
        </Card>
~~~

Do not change handleMethod, amount validation, or createPaymentOrder.

- [ ] **Step 3: Add the icon-card CSS**

Add after the current payment-method-card rules in public-ledger.css:

~~~css
.payment-method-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.payment-method-card .semi-card-header { min-height: 64px; }
.payment-method-card .payment-method-logo { height: 28px; max-width: 112px; object-fit: contain; object-position: left center; width: auto; }
.payment-method-card--paypal .payment-method-logo { height: 25px; }
.payment-method-card--trc20 .payment-method-logo { height: 30px; }
@media (max-width: 760px) { .payment-method-grid { grid-template-columns: 1fr; } }
~~~

- [ ] **Step 4: Verify the shared behavior passes**

Run: npm test -- src/features/payments/__tests__/purchase-page.test.tsx src/features/payments/__tests__/recharge-page.test.tsx

Expected: PASS; the existing PayPal and TRC20 request assertions still use the exact selected amount and payment method.

- [ ] **Step 5: Commit the component and styles**

~~~powershell
git add -- frontend/src/features/payments/PaymentSelectionPanel.tsx frontend/src/styles/public-ledger.css
git commit -m "feat: show supported payment methods as icons"
~~~

### Task 3: Validate the full frontend

**Files:**

- Verify only: frontend/src/features/payments/PurchasePage.tsx
- Verify only: frontend/src/features/payments/RechargePage.tsx

- [ ] **Step 1: Run all frontend tests**

Run: npm test

Expected: PASS.

- [ ] **Step 2: Build production assets**

Run: npm run build

Expected: vite build succeeds.

- [ ] **Step 3: Inspect both consumers**

Run: npm run dev -- --host 127.0.0.1

Inspect /purchase and /console/recharge at desktop and 375px widths. Confirm each page shows exactly PayPal and TRC20 logos, the continue buttons remain visible, no other-method card appears, and card layout is two columns on desktop and one column on mobile.

