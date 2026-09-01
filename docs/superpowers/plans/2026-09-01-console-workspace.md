# Console Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a unified Semi Design console workspace with readable left navigation, top utility bar, responsive mobile drawer, and screenshot-inspired dashboard layout.

**Architecture:** `ConsoleLayout` owns all shared console chrome and renders page content as children. Dashboard and token pages keep their data fetching responsibility but render inside the layout; routing selects the active nav key. The existing customer-safe BFF endpoints remain unchanged.

**Tech Stack:** React 19, TypeScript, Semi Design Layout/Nav/Drawer/Card/Button/Empty/Skeleton, React Testing Library, Vitest.

---

## File structure

```text
portal/frontend/src/
  components/ConsoleLayout.tsx
  components/__tests__/console-layout.test.tsx
  features/console/DashboardPage.tsx
  features/console/TokensPage.tsx
  features/console/__tests__/dashboard-page.test.tsx
  features/console/__tests__/tokens-page.test.tsx
  App.tsx
  i18n/locales/en.json
  i18n/locales/zh-CN.json
  styles.css
```

### Task 1: Build the shared Semi console layout

**Files:**
- Create: `portal/frontend/src/components/ConsoleLayout.tsx`
- Create: `portal/frontend/src/components/__tests__/console-layout.test.tsx`
- Modify: `portal/frontend/src/i18n/locales/en.json`
- Modify: `portal/frontend/src/i18n/locales/zh-CN.json`
- Modify: `portal/frontend/src/styles.css`

- [ ] **Step 1: Write failing desktop navigation tests.**

```tsx
it('renders readable dashboard and token navigation with the active item selected', () => {
  render(<ConsoleLayout activeKey="dashboard"><div>content</div></ConsoleLayout>)
  expect(screen.getByRole('navigation', { name: 'Console navigation' })).toBeVisible()
  expect(screen.getByText('仪表盘').closest('[role="menuitem"]')).toHaveAttribute('aria-selected', 'true')
  expect(screen.getByText('令牌管理')).toBeVisible()
})
```

- [ ] **Step 2: Run the test and confirm it fails.**

Run: `npm --prefix frontend test -- --run src/components/__tests__/console-layout.test.tsx`

Expected: FAIL because `ConsoleLayout` is absent.

- [ ] **Step 3: Implement `ConsoleLayout` with Semi Layout and Nav.**

```tsx
<Layout className="console-shell">
  <Layout.Sider className="console-sider"><Nav mode="vertical" selectedKeys={[props.activeKey]} /></Layout.Sider>
  <Layout>
    <Layout.Header className="console-topbar">…</Layout.Header>
    <Layout.Content className="console-content">{props.children}</Layout.Content>
  </Layout>
</Layout>
```

Use menu keys `dashboard`, `recharge`, `tokens`, `logs`, and `profile`. Nav rows use 44px minimum height, dark text `#344054`, selected mint surface, visible keyboard focus ring, and Semi icon components with `aria-label` on icon-only header actions.

- [ ] **Step 4: Run the layout test and production build.**

Run: `npm --prefix frontend test -- --run src/components/__tests__/console-layout.test.tsx`

Run: `npm --prefix frontend run build`

Expected: PASS and a production bundle.

- [ ] **Step 5: Commit the console shell.**

```bash
git add frontend/src/components/ConsoleLayout.tsx frontend/src/components/__tests__/console-layout.test.tsx frontend/src/i18n frontend/src/styles.css
git commit -m "feat: add console sidebar workspace"
```

### Task 2: Place dashboard and token pages inside the workspace

**Files:**
- Modify: `portal/frontend/src/App.tsx`
- Modify: `portal/frontend/src/features/console/DashboardPage.tsx`
- Modify: `portal/frontend/src/features/console/TokensPage.tsx`
- Modify: `portal/frontend/src/features/console/__tests__/dashboard-page.test.tsx`
- Modify: `portal/frontend/src/features/console/__tests__/tokens-page.test.tsx`
- Modify: `portal/frontend/src/styles.css`

- [ ] **Step 1: Write failing route-layout tests.**

```tsx
it('renders dashboard metrics inside the console workspace', async () => {
  mockDashboard({ availableQuota: 900, usedQuota: 100, requestCount: 12 })
  window.history.pushState({}, '', '/console/dashboard')
  render(<App />)
  expect(await screen.findByText('900')).toBeVisible()
  expect(screen.getByRole('navigation', { name: 'Console navigation' })).toBeVisible()
})
```

- [ ] **Step 2: Run console page tests and confirm failure.**

Run: `npm --prefix frontend test -- --run src/features/console/__tests__/dashboard-page.test.tsx src/features/console/__tests__/tokens-page.test.tsx`

Expected: FAIL because the pages do not use `ConsoleLayout`.

- [ ] **Step 3: Render each page through the layout and rebuild dashboard content.**

Dashboard uses four Semi cards: available balance from `availableQuota`, total requests from `requestCount`, quota consumption from `usedQuota`, and token consumption with `0` plus an explicit “暂无 Token 消耗数据” label. Add a consumption workspace with date/model buttons, four summary cells, an explicit empty trend panel, and a right-side three-step onboarding card. Keep all page data labels and actions accessible.

Token page renders in the same workspace with `activeKey="tokens"`; preserve existing list data and use Semi table/card styling rather than raw HTML table styling.

- [ ] **Step 4: Run affected tests and build.**

Run: `npm --prefix frontend test -- --run src/features/console/__tests__/dashboard-page.test.tsx src/features/console/__tests__/tokens-page.test.tsx`

Run: `npm --prefix frontend run build`

Expected: PASS without contrast, canvas, range, or resize-observer test errors.

- [ ] **Step 5: Commit the integrated console pages.**

```bash
git add frontend/src/App.tsx frontend/src/features/console frontend/src/styles.css
git commit -m "feat: redesign dashboard workspace"
```

### Task 3: Add mobile drawer behavior and anonymous route guard

**Files:**
- Modify: `portal/frontend/src/components/ConsoleLayout.tsx`
- Modify: `portal/frontend/src/components/__tests__/console-layout.test.tsx`
- Modify: `portal/frontend/src/App.tsx`
- Modify: `portal/frontend/src/styles.css`

- [ ] **Step 1: Write failing mobile and anonymous behavior tests.**

```tsx
it('opens navigation drawer from the mobile menu button', async () => {
  setViewport(375, 812)
  render(<ConsoleLayout activeKey="dashboard"><div>content</div></ConsoleLayout>)
  await userEvent.click(screen.getByRole('button', { name: '打开控制台菜单' }))
  expect(await screen.findByText('令牌管理')).toBeVisible()
})

it('redirects an anonymous console request to sign-in', async () => {
  mockAuth(401)
  window.history.pushState({}, '', '/console/dashboard')
  render(<App />)
  expect(await screen.findByRole('heading', { name: '登录' })).toBeVisible()
})
```

- [ ] **Step 2: Run the tests and confirm failure.**

Run: `npm --prefix frontend test -- --run src/components/__tests__/console-layout.test.tsx`

Expected: FAIL because the mobile drawer and auth route guard do not exist.

- [ ] **Step 3: Implement responsive drawer and route guard.**

Use Semi `Drawer` on viewports below 768px. The header menu button uses `aria-label="打开控制台菜单"`; selecting a nav item closes the drawer and routes to its target. App-level console route handling reads `getCurrentProfile`; it renders the sign-in page when the current session is anonymous and otherwise renders the requested console page.

- [ ] **Step 4: Run console-layout tests and full frontend build.**

Run: `npm --prefix frontend test -- --run src/components/__tests__/console-layout.test.tsx`

Run: `npm --prefix frontend run build`

Expected: PASS; the 375px layout has no horizontal page overflow.

- [ ] **Step 5: Commit responsive console behavior.**

```bash
git add frontend/src/components/ConsoleLayout.tsx frontend/src/components/__tests__/console-layout.test.tsx frontend/src/App.tsx frontend/src/styles.css
git commit -m "feat: add responsive console navigation"
```
