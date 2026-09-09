# Portal Unified Ledger Visual System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Make public, authentication, and console routes feel like one Unified Ledger product without changing portal behavior.

**Architecture:** Keep existing page components and routes. Add route-specific shell classes where semantic visual boundaries are missing, then consolidate all shared palette, surface, typography, navigation, card, table and responsive rules in the existing public-ledger stylesheet imported after legacy styles.

**Tech Stack:** React 19, TypeScript, Semi UI, ECharts, Vitest, Testing Library, CSS.

---

### Task 1: Add testable page-system boundaries

**Files:**

- Modify: frontend/src/__tests__/app-shell.test.tsx
- Modify: frontend/src/features/console/__tests__/dashboard-page.test.tsx
- Modify: frontend/src/features/auth/SignInPage.tsx
- Modify: frontend/src/features/auth/SignUpPage.tsx
- Modify: frontend/src/features/console/DashboardPage.tsx

- [ ] **Step 1: Write failing public/auth shell assertions**

Append this test to app-shell.test.tsx:

~~~tsx
  it('mounts the ledger system on public and authentication routes', () => {
    window.history.pushState({}, '', '/sign-in')
    const { rerender } = render(<App />)
    expect(screen.getByTestId('ledger-auth-page')).toBeVisible()

    window.history.pushState({}, '', '/')
    rerender(<App />)
    expect(screen.getByTestId('ledger-public-page')).toBeVisible()
  })
~~~

- [ ] **Step 2: Write the failing dashboard shell assertion**

In the existing successful dashboard test, add:

~~~tsx
    expect(screen.getByTestId('ledger-console-page')).toBeVisible()
~~~

- [ ] **Step 3: Run tests to verify they fail**

Run: npm test -- src/__tests__/app-shell.test.tsx src/features/console/__tests__/dashboard-page.test.tsx

Expected: FAIL because the ledger system test ids are absent.

- [ ] **Step 4: Add only the required class and test id boundaries**

Replace each auth page main opening tag with:

~~~tsx
    <main className="auth-page ledger-auth-page" data-testid="ledger-auth-page">
~~~

Replace DashboardPage successful main with:

~~~tsx
    <main className="ledger-console-page" data-testid="ledger-console-page">
~~~

In App.tsx, wrap the home route in:

~~~tsx
  if (path === '/') return <div className="ledger-public-page" data-testid="ledger-public-page"><PublicHeader /><HomePage /></div>
~~~

Do not alter event handlers, requests, translation values, form fields, or dashboard chart options.

- [ ] **Step 5: Run tests to verify they pass**

Run: npm test -- src/__tests__/app-shell.test.tsx src/features/console/__tests__/dashboard-page.test.tsx

Expected: PASS.

- [ ] **Step 6: Commit**

~~~powershell
git add -- frontend/src/App.tsx frontend/src/features/auth/SignInPage.tsx frontend/src/features/auth/SignUpPage.tsx frontend/src/features/console/DashboardPage.tsx frontend/src/__tests__/app-shell.test.tsx frontend/src/features/console/__tests__/dashboard-page.test.tsx
git commit -m "feat: add unified ledger page boundaries"
~~~

### Task 2: Apply the public and authentication ledger layer

**Files:**

- Modify: frontend/src/styles/public-ledger.css
- Verify: frontend/src/features/home/__tests__/home-page.test.tsx
- Verify: frontend/src/features/auth/__tests__/sign-in-page.test.tsx
- Verify: frontend/src/features/auth/__tests__/sign-up-page.test.tsx

- [ ] **Step 1: Add these exact shared public/auth rules**

Append to public-ledger.css:

~~~css
.ledger-public-page,.ledger-auth-page {
  --ledger-paper:#f4f6f0; --ledger-surface:#fffefb; --ledger-ink:#183128;
  --ledger-muted:#61746a; --ledger-green:#147b57; --ledger-green-dark:#0d5d40;
  --ledger-soft:#e4efe6; --ledger-line:#d5dfd6;
  background:radial-gradient(circle at 88% 3%,rgba(191,222,201,.7),transparent 23%),var(--ledger-paper);
  color:var(--ledger-ink);
}
.ledger-public-page .semi-home { background:transparent; color:var(--ledger-ink); }
.ledger-public-page .semi-hero h1,.ledger-public-page .section-intro h2,.ledger-public-page .home-cta h2,.ledger-auth-page h1 { color:var(--ledger-ink); font-family:Georgia,'Times New Roman',serif; }
.ledger-public-page .feature-card,.ledger-public-page .step-card,.ledger-public-page .api-panel,.ledger-public-page .home-cta,.ledger-auth-page .auth-panel {
  background:var(--ledger-surface); border:1px solid var(--ledger-line); box-shadow:0 16px 38px rgba(33,67,48,.08);
}
.ledger-public-page .semi-button-primary,.ledger-auth-page .semi-button-primary { background:var(--ledger-green); border-color:var(--ledger-green); }
.ledger-public-page .public-footer { background:var(--ledger-surface); border-color:var(--ledger-line); color:var(--ledger-muted); }
.ledger-auth-page .auth-page { background:transparent; }.ledger-auth-page .brand-mark { background:var(--ledger-green); }
~~~

- [ ] **Step 2: Run public and authentication behavior tests**

Run: npm test -- src/features/home/__tests__/home-page.test.tsx src/features/auth/__tests__/sign-in-page.test.tsx src/features/auth/__tests__/sign-up-page.test.tsx

Expected: PASS; assertions confirm existing navigation, sign-in, registration and validation behavior remains unchanged.

- [ ] **Step 3: Commit**

~~~powershell
git add -- frontend/src/styles/public-ledger.css
git commit -m "feat: apply ledger styling to public and auth pages"
~~~

### Task 3: Apply console ledger surfaces without changing data behavior

**Files:**

- Modify: frontend/src/styles/public-ledger.css
- Verify: frontend/src/components/__tests__/console-layout.test.tsx
- Verify: frontend/src/features/console/__tests__/dashboard-page.test.tsx
- Verify: frontend/src/features/console/__tests__/tokens-page.test.tsx
- Verify: frontend/src/features/console/__tests__/logs-page.test.tsx
- Verify: frontend/src/features/orders/__tests__/orders-page.test.tsx

- [ ] **Step 1: Add console system rules**

Append these rules to public-ledger.css:

~~~css
.console-shell,.ledger-console-page,.recharge-page {
  --ledger-paper:#f4f6f0; --ledger-surface:#fffefb; --ledger-ink:#183128;
  --ledger-muted:#61746a; --ledger-green:#147b57; --ledger-green-dark:#0d5d40;
  --ledger-soft:#e4efe6; --ledger-line:#d5dfd6;
}
.console-shell { background:var(--ledger-paper); color:var(--ledger-ink); }
.console-sider,.console-topbar { background:var(--ledger-surface); border-color:var(--ledger-line); }
.console-brand,.console-topbar h1,.console-sider .semi-navigation-item { color:var(--ledger-ink); }
.console-sider .semi-navigation-item-selected { background:var(--ledger-soft); color:var(--ledger-green-dark); }
.console-content .semi-card,.console-table-wrap,.console-filter-bar,.profile-form,.dashboard-chart,.remote-state {
  background:var(--ledger-surface); border-color:var(--ledger-line);
}
.console-content .semi-button-primary,.console-content .semi-button-primary.semi-button-solid { background:var(--ledger-green); border-color:var(--ledger-green); }
.console-content .semi-typography-tertiary,.console-content .semi-table-tbody .semi-table-row-cell { color:var(--ledger-muted); }
.console-content .metric-card::before { background:var(--ledger-green); }
~~~

- [ ] **Step 2: Run console behavior suite**

Run: npm test -- src/components/__tests__/console-layout.test.tsx src/features/console/__tests__/dashboard-page.test.tsx src/features/console/__tests__/tokens-page.test.tsx src/features/console/__tests__/logs-page.test.tsx src/features/orders/__tests__/orders-page.test.tsx

Expected: PASS; no account data, navigation, token, log or order behavior changes.

- [ ] **Step 3: Commit**

~~~powershell
git add -- frontend/src/styles/public-ledger.css
git commit -m "feat: unify console surfaces with ledger system"
~~~

### Task 4: Verify responsive and payment-state integrity

**Files:**

- Verify only: frontend/src/features/payments/PaymentSelectionPanel.tsx
- Verify only: frontend/src/features/payments/PayPalCheckout.tsx
- Verify only: frontend/src/features/payments/Trc20Checkout.tsx
- Verify only: frontend/src/styles/public-ledger.css

- [ ] **Step 1: Run payment and full frontend tests**

Run: npm test

Expected: PASS; PayPal SDK URL, payment payload, TxID submission, status messages and all existing frontend behavior remain correct.

- [ ] **Step 2: Build production assets**

Run: npm run build

Expected: vite build succeeds.

- [ ] **Step 3: Inspect responsive routes**

Run: npm run dev -- --host 127.0.0.1

At desktop and 375px, inspect /, /models, /purchase, /sign-in, /sign-up, /console/dashboard and /console/recharge. Confirm the public header does not overlap, console navigation remains usable, selected payment amount remains visible, and PayPal/TRC20 failure or expiry states retain amber/red rather than green.

- [ ] **Step 4: Inspect scoped diff**

Run: git diff --check; git status --short

Expected: no whitespace error and no unrelated files staged.

