# Portal Models and Purchase Visual Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Apply the approved Quiet Ledger visual system to the public model catalog, purchase selection, and PayPal/TRC20 checkout views without changing payment or catalog behavior.

**Architecture:** Add a page-scoped public-ledger.css stylesheet imported after the shared stylesheet so the public refresh cannot alter the home page or console. Keep domain state in the existing page components; add only presentational landmarks and data-testid hooks that make the new hierarchy verifiable. Checkout summaries remain inside their existing components so current polling, SDK, copy, and TxID interactions stay untouched.

**Tech Stack:** React 19, TypeScript, Semi UI, react-i18next, Vitest, Testing Library, CSS.

---

### Task 1: Establish catalog hierarchy regression coverage

**Files:**

- Modify: frontend/src/features/catalog/__tests__/models-page.test.tsx
- Modify: frontend/src/features/catalog/ModelsPage.tsx

- [ ] **Step 1: Write the failing catalog-landmark test**

Append this test:

~~~tsx
  it('renders the Quiet Ledger catalog status and a labelled results region', async () => {
    render(<ModelsPage />)

    expect(await screen.findByText('gpt-5-mini')).toBeVisible()
    expect(screen.getByTestId('models-ledger-status')).toHaveTextContent('模型目录')
    expect(screen.getByRole('region', { name: '模型目录' })).toBeVisible()
  })
~~~

- [ ] **Step 2: Run test to verify it fails**

Run: npm test -- src/features/catalog/__tests__/models-page.test.tsx

Expected: FAIL because models-ledger-status does not exist.

- [ ] **Step 3: Add the minimal semantic structure**

In ModelsPage.tsx, immediately inside the summary aside, add:

~~~tsx
          <div className="models-ledger-status" data-testid="models-ledger-status">
            <span aria-hidden="true" />
            {t('models.liveCatalog')}
          </div>
~~~

Replace the opening results section tag with:

~~~tsx
        <section className="models-results" aria-label={t('models.catalogLabel')}>
~~~

- [ ] **Step 4: Run test to verify it passes**

Run: npm test -- src/features/catalog/__tests__/models-page.test.tsx

Expected: PASS, including search and group-filter tests.

- [ ] **Step 5: Commit**

~~~powershell
git add -- frontend/src/features/catalog/ModelsPage.tsx frontend/src/features/catalog/__tests__/models-page.test.tsx
git commit -m "feat: add catalog ledger landmarks"
~~~

### Task 2: Establish purchase selection hierarchy regression coverage

**Files:**

- Modify: frontend/src/features/payments/__tests__/purchase-page.test.tsx
- Modify: frontend/src/features/payments/PaymentSelectionPanel.tsx

- [ ] **Step 1: Write the failing purchase-summary test**

Append this test:

~~~tsx
  it('keeps the selected amount in the ledger summary while choosing a payment method', () => {
    render(<PurchasePage />)

    expect(screen.getByTestId('purchase-ledger-steps')).toHaveTextContent('1')
    expect(screen.getByTestId('purchase-ledger-steps')).toHaveTextContent('2')
    expect(screen.getByTestId('purchase-ledger-summary')).toHaveTextContent('$5')
  })
~~~

- [ ] **Step 2: Run test to verify it fails**

Run: npm test -- src/features/payments/__tests__/purchase-page.test.tsx

Expected: FAIL because purchase-ledger-steps does not exist.

- [ ] **Step 3: Add the minimal presentational hierarchy without changing order creation**

In PaymentSelectionPanel.tsx, add this directly before AmountSelector:

~~~tsx
      <ol className="purchase-ledger-steps" data-testid="purchase-ledger-steps" aria-label={t('purchase.title')}>
        <li className="is-current"><span>1</span>{t('purchase.amount')}</li>
        <li><span>2</span>{t('payment.title')}</li>
      </ol>
~~~

Replace the selected-amount Typography.Text with:

~~~tsx
      <aside className="purchase-ledger-summary" data-testid="purchase-ledger-summary" aria-live="polite">
        <Typography.Text type="tertiary">{t('purchase.selected')}</Typography.Text>
        <strong>{'$'}{amount || '—'}</strong>
      </aside>
~~~

Do not alter amount, amountIsUsable, handleMethod, button labels, or the createPaymentOrder call.

- [ ] **Step 4: Run test to verify it passes**

Run: npm test -- src/features/payments/__tests__/purchase-page.test.tsx

Expected: PASS; existing assertions still prove that only amount and method go to the order API.

- [ ] **Step 5: Commit**

~~~powershell
git add -- frontend/src/features/payments/PaymentSelectionPanel.tsx frontend/src/features/payments/__tests__/purchase-page.test.tsx
git commit -m "feat: add purchase ledger summary"
~~~

### Task 3: Add shared checkout-summary landmarks

**Files:**

- Modify: frontend/src/features/payments/__tests__/paypal-checkout.test.tsx
- Modify: frontend/src/features/payments/__tests__/trc20-checkout.test.tsx
- Modify: frontend/src/features/payments/PayPalCheckout.tsx
- Modify: frontend/src/features/payments/Trc20Checkout.tsx

- [ ] **Step 1: Write failing checkout landmark assertions**

In PayPal's load test, after the buttons-container assertion, add:

~~~tsx
    expect(screen.getByTestId('paypal-ledger-summary')).toHaveTextContent('$25.50')
    expect(screen.getByTestId('paypal-ledger-summary')).toHaveTextContent('Waiting for payment')
~~~

In the TRC20 test, after the address assertion, add:

~~~tsx
    expect(screen.getByTestId('trc20-ledger-summary')).toHaveTextContent('TRC20 USDT payment')
    expect(screen.getByTestId('trc20-ledger-summary')).toHaveTextContent('Waiting for payment')
~~~

- [ ] **Step 2: Run tests to verify they fail**

Run: npm test -- src/features/payments/__tests__/paypal-checkout.test.tsx src/features/payments/__tests__/trc20-checkout.test.tsx

Expected: FAIL because ledger-summary test ids are absent.

- [ ] **Step 3: Make the existing checkout headers explicit summaries**

In PayPalCheckout.tsx, add data-testid="paypal-ledger-summary" to the existing header with class paypal-checkout-summary. Keep amountText, quotaText, statusText, the PayPal SDK container, and all effects unchanged.

In Trc20Checkout.tsx, replace the current compact header with:

~~~tsx
    <header className="trc20-checkout-summary" data-testid="trc20-ledger-summary">
      <p className="trc20-kicker">TRON · TRC20</p>
      <h3>{t('payment.trc20Title')}</h3>
      <p>{t('payment.quotaEquivalent', { quota: formatQuota(current.quotaToCredit) })}</p>
      <p className="trc20-checkout-status" data-status={current.status}>{status}</p>
    </header>
~~~

Remove the separate trc20-status paragraph so status appears once. Do not change fetch URLs, intervals, copy behavior, or TxID submission.

- [ ] **Step 4: Run tests to verify they pass**

Run: npm test -- src/features/payments/__tests__/paypal-checkout.test.tsx src/features/payments/__tests__/trc20-checkout.test.tsx

Expected: PASS, including endpoint and client-trust assertions.

- [ ] **Step 5: Commit**

~~~powershell
git add -- frontend/src/features/payments/PayPalCheckout.tsx frontend/src/features/payments/Trc20Checkout.tsx frontend/src/features/payments/__tests__/paypal-checkout.test.tsx frontend/src/features/payments/__tests__/trc20-checkout.test.tsx
git commit -m "feat: unify payment checkout summaries"
~~~

### Task 4: Implement page-scoped Quiet Ledger styling

**Files:**

- Create: frontend/src/styles/public-ledger.css
- Modify: frontend/src/main.tsx

- [ ] **Step 1: Write a failing build check before adding the stylesheet**

Add this import immediately after import './styles.css' in main.tsx:

~~~ts
import './styles/public-ledger.css'
~~~

Run: npm run build

Expected: FAIL with a Vite module-resolution error because the stylesheet has not been created.

- [ ] **Step 2: Create the stylesheet**

Create frontend/src/styles/public-ledger.css:

~~~css
.models-page,.purchase-page {
  --ledger-paper:#f5f7f3; --ledger-ink:#13231d; --ledger-muted:#62716a;
  --ledger-green:#13855d; --ledger-line:#d7e0d9; --ledger-soft:#e9f1eb;
  background:radial-gradient(circle at 87% 4%,#dceee2 0,transparent 24%),var(--ledger-paper);
  color:var(--ledger-ink);
}
.models-page { min-height:calc(100vh - 68px); padding:140px clamp(20px,6vw,96px) 96px; }
.purchase-page { min-height:calc(100vh - 68px); padding:140px clamp(20px,5vw,78px) 80px; }
.models-hero h1,.purchase-header h1 { color:var(--ledger-ink); font-family:Georgia,serif; }
.models-summary,.purchase-panel,.paypal-checkout,.trc20-checkout { background:rgba(255,255,255,.84); border:1px solid var(--ledger-line); border-radius:18px; box-shadow:0 16px 38px rgba(20,54,37,.08); }
.models-ledger-status { align-items:center; color:var(--ledger-green); display:flex; font:700 10px ui-monospace,monospace; gap:7px; letter-spacing:.12em; text-transform:uppercase; }
.models-ledger-status span { background:var(--ledger-green); border-radius:50%; box-shadow:0 0 0 4px #dcefe3; height:6px; width:6px; }
.models-group.is-selected,.model-card-footer .semi-tag { background:#e2f1e7; color:#0d6b4b; }
.model-card::before { background:var(--ledger-green); }.model-prices span { background:#f8faf8; border-color:#e1e8e3; }
.purchase-ledger-steps { display:flex; gap:20px; list-style:none; margin:0; padding:0; }
.purchase-ledger-steps li { align-items:center; color:var(--ledger-muted); display:flex; font-size:12px; gap:7px; }
.purchase-ledger-steps span { border:1px solid var(--ledger-line); border-radius:50%; display:grid; height:22px; place-items:center; width:22px; }
.purchase-ledger-steps .is-current { color:var(--ledger-ink); font-weight:700; }.purchase-ledger-steps .is-current span { background:var(--ledger-green); border-color:var(--ledger-green); color:#fff; }
.purchase-ledger-summary { align-items:end; background:var(--ledger-soft); border:1px solid #d5e7da; border-radius:12px; display:flex; justify-content:space-between; padding:14px 16px; }
.purchase-ledger-summary strong { color:var(--ledger-ink); font:700 24px ui-monospace,monospace; }
.payment-method-card { border-color:var(--ledger-line); box-shadow:none; }.payment-method-card:hover { border-color:var(--ledger-green); }
.paypal-checkout-summary,.trc20-checkout-summary { border-bottom:1px solid var(--ledger-line); display:grid; gap:7px; padding-bottom:17px; }
.paypal-checkout-status,.trc20-checkout-status { color:var(--ledger-green); font-weight:700; margin:0; }
.trc20-checkout { color:var(--ledger-ink); }.trc20-checkout h3,.trc20-instruction strong,.trc20-instruction code { color:var(--ledger-ink); }
.trc20-instruction { border-color:var(--ledger-line); }.trc20-instruction .semi-button { color:var(--ledger-green); }
.trc20-txid .semi-button { background:var(--ledger-green); border-color:var(--ledger-green); color:#fff; }
@media (max-width:760px) { .models-page,.purchase-page { padding:102px 20px 56px; }.models-hero { grid-template-columns:1fr; }.models-summary { max-width:none; }.purchase-ledger-steps { gap:12px; }.payment-method-grid { grid-template-columns:1fr; } }
@media (max-width:520px) { .amount-grid,.model-prices { grid-template-columns:repeat(2,minmax(0,1fr)); }.purchase-ledger-summary { align-items:start; flex-direction:column; gap:4px; }.trc20-instruction > div { grid-template-columns:1fr auto; }.trc20-instruction span { grid-column:1 / -1; } }
@media (prefers-reduced-motion:reduce) { .model-card,.payment-method-card { transition:none; }.model-card:hover { transform:none; } }
~~~

- [ ] **Step 3: Verify stylesheet compilation and focused behavior**

Run: npm run build; npm test -- src/features/catalog/__tests__/models-page.test.tsx src/features/payments/__tests__/purchase-page.test.tsx src/features/payments/__tests__/paypal-checkout.test.tsx src/features/payments/__tests__/trc20-checkout.test.tsx

Expected: build succeeds and all four test files pass.

- [ ] **Step 4: Commit**

~~~powershell
git add -- frontend/src/main.tsx frontend/src/styles/public-ledger.css
git commit -m "feat: style portal pages as quiet ledger"
~~~

### Task 5: Full verification and visual QA

**Files:**

- Verify only: frontend/src/features/catalog/ModelsPage.tsx
- Verify only: frontend/src/features/payments/PurchasePage.tsx
- Verify only: frontend/src/features/payments/PayPalCheckout.tsx
- Verify only: frontend/src/features/payments/Trc20Checkout.tsx

- [ ] **Step 1: Run the complete frontend suite**

Run: npm test

Expected: PASS with no failures.

- [ ] **Step 2: Run production compilation**

Run: npm run build

Expected: vite build completes successfully.

- [ ] **Step 3: Manually inspect desktop and mobile states**

Run: npm run dev -- --host 127.0.0.1

Inspect /models and /purchase at 1440px and 375px. Confirm the fixed public header does not overlap content; model loading, query, group filtering, copy and details work; selected amount updates in the summary; PayPal/TRC20 order buttons retain their endpoints; both checkout summaries, address copy, TxID validation, errors and status text are legible; no page overflows horizontally.

- [ ] **Step 4: Inspect this plan's diff before any final correction commit**

Run: git diff --check; git status --short

Expected: no whitespace errors, and no unrelated dirty files staged.

