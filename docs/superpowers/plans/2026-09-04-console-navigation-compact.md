# Console Navigation Compact Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Compact the authenticated console sidebar and remove redundant public-home links while making account-menu actions easier to scan.

**Architecture:** Keep `ConsoleLayout` as the sole source of console navigation and account-menu markup. Use the existing Semi icon package and the console CSS block in `styles.css`; no routes, translations, or API behavior change.

**Tech Stack:** React 19, TypeScript, Vitest, Testing Library, Semi UI and Semi Icons.

---

## Files

- Modify: `frontend/src/components/ConsoleLayout.tsx` — remove duplicate home links and render menu icons.
- Modify: `frontend/src/components/__tests__/console-layout.test.tsx` — define and verify the new account-menu contract.
- Modify: `frontend/src/styles.css` — reduce the desktop sidebar to 216px and style account menu rows with consistent icon spacing.

### Task 1: Define the compact console navigation contract

**Files:**
- Modify: `frontend/src/components/__tests__/console-layout.test.tsx`

- [ ] **Step 1: Replace the obsolete sidebar-home assertion with a failing absence assertion**

```tsx
expect(screen.queryByRole('link', { name: '门户首页' })).not.toBeInTheDocument()
```

- [ ] **Step 2: Extend the account-menu test with the removed route assertion**

```tsx
expect(screen.getByText('alice')).toBeVisible()
expect(screen.getByRole('button', { name: '退出登录' })).toBeVisible()
expect(screen.queryByRole('link', { name: '门户首页' })).not.toBeInTheDocument()
```

- [ ] **Step 3: Run the focused test to verify it fails because the existing layout still renders the two public-home links**

Run: `npm test -- src/components/__tests__/console-layout.test.tsx`

Expected: FAIL; the test finds at least one `门户首页` link.

### Task 2: Remove duplicate portal-home routes and add account-menu icons

**Files:**
- Modify: `frontend/src/components/ConsoleLayout.tsx`

- [ ] **Step 1: Import the sign-out icon together with the existing Semi icons**

```tsx
import { IconBell, IconCreditCard, IconExit, IconHistory, IconHome, IconKey, IconList, IconUser } from '@douyinfe/semi-icons'
```

- [ ] **Step 2: Remove the sidebar public-home anchor while retaining the brand anchor**

Delete this exact element and leave the preceding `console-brand` anchor intact:

```tsx
<a className="console-sidebar-home" href="/"><IconHome />{t('console.home')}</a>
```

- [ ] **Step 3: Replace the account-menu body with icon-prefixed label and sign-out action**

```tsx
<div className="public-account-menu">
  <span className="public-username"><IconUser aria-hidden="true" />{accountName}</span>
  <Button theme="borderless" className="public-logout" icon={<IconExit aria-hidden="true" />} onClick={() => void handleSignOut()}>{t('auth.signOut')}</Button>
</div>
```

- [ ] **Step 4: Run the focused test to verify the new contract passes**

Run: `npm test -- src/components/__tests__/console-layout.test.tsx`

Expected: PASS; all three console layout tests pass.

### Task 3: Apply compact layout styling and verify the frontend

**Files:**
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: Reduce the desktop sidebar width and remove obsolete sidebar-home rules**

Replace the sidebar minimum width declaration and delete the unused `.console-sidebar-home` and `.console-sidebar-home:hover` rules:

```css
.console-sider { background: #fff; border-right: 1px solid #e7ecf3; min-width: 216px; padding: 24px 12px; }
```

- [ ] **Step 2: Make the account label align its user icon and text**

```css
.public-username { align-items: center; display: inline-flex; gap: 7px; }
```

The existing Semi `Button` icon prop supplies the sign-out icon and retains its current alignment, hover color, and button semantics.

- [ ] **Step 3: Run the complete frontend test suite**

Run: `npm test`

Expected: PASS with no failed tests.

- [ ] **Step 4: Build the production frontend bundle**

Run: `npm run build`

Expected: TypeScript completes successfully and Vite reports a successful build.

- [ ] **Step 5: Commit only files owned by this change**

```bash
git add frontend/src/components/ConsoleLayout.tsx frontend/src/components/__tests__/console-layout.test.tsx frontend/src/styles.css
git commit -m "feat: compact console navigation"
```
