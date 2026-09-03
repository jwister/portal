# Public Header Auth Menu Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make the portal header navigation and account actions accurately reflect anonymous and authenticated states.

**Architecture:** Keep `PublicHeader` as the state-aware composition point, call the existing `signOut` API from a small logout handler, and scope visual changes to public-header selectors. Tests will assert menu contents, account identity, and logout behavior.

**Tech Stack:** React 19, TypeScript, Semi UI, i18next, Vitest + Testing Library.

---

### Task 1: Define auth-aware header behavior in tests

**Files:**
- Modify: `portal/frontend/src/components/__tests__/public-header.test.tsx`

- [ ] Add authenticated assertions that Console is absent from navigation links, username/avatar/logout are visible, and registration is absent.
- [ ] Add anonymous assertions that only Login is shown as the auth action and no account cluster exists.
- [ ] Add a logout test that clicks the logout button, verifies the sign-out request, and verifies the page reload callback.
- [ ] Run the focused test file and observe failures for the new expectations.

### Task 2: Implement state-aware public header actions

**Files:**
- Modify: `portal/frontend/src/components/PublicHeader.tsx`

- [ ] Remove `/console/dashboard` from `navItems`.
- [ ] Import `signOut` and `Toast`, derive the avatar initial from `status.profile.username`, and render authenticated/anonymous footer variants per the approved behavior.
- [ ] Handle logout success with `window.location.reload()` and failure with an error toast.
- [ ] Run the focused header tests until green.

### Task 3: Add copy and visual account-cluster styling

**Files:**
- Modify: `portal/frontend/src/i18n/locales/zh-CN.json`
- Modify: `portal/frontend/src/i18n/locales/en.json`
- Modify: `portal/frontend/src/styles.css`

- [ ] Add locale keys for logout, user menu, and logout failure.
- [ ] Style `.public-account`, `.public-avatar`, `.public-username`, and `.public-logout` with existing portal colors, 44px-friendly control sizing, and narrow-screen wrapping.
- [ ] Run focused tests and `npm run build`.

### Task 4: Regression verification

- [ ] Run `npm test` for the complete frontend suite.
- [ ] Run `git diff --check` and review only the intended files; leave unrelated worktree changes untouched.
