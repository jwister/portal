# Model Catalog Visual Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Refresh the `/models` page with a clean technology visual system, stronger header hierarchy, and more legible model cards without changing behavior.

**Architecture:** Keep the existing `ModelsPage` data flow and Semi UI controls. Add semantic wrappers for the hero summary and card identity/pricing sections, then scope all visual changes to the model-page selectors in `styles.css`.

**Tech Stack:** React 19, TypeScript, Semi UI, CSS, Vitest + Testing Library.

---

### Task 1: Lock the refreshed page structure with a test

**Files:**
- Modify: `portal/frontend/src/features/catalog/__tests__/models-page.test.tsx`

- [ ] Add assertions that the loaded page exposes a catalog eyebrow, summary metrics, and semantic card sections while preserving model names and action labels.
- [ ] Run `npm test -- src/features/catalog/__tests__/models-page.test.tsx` and confirm the new assertions fail against the old markup.

### Task 2: Add semantic wrappers for the hero and cards

**Files:**
- Modify: `portal/frontend/src/features/catalog/ModelsPage.tsx`

- [ ] Wrap the hero content with a left copy block and right summary block showing model/group counts.
- [ ] Add an eyebrow label above the toolbar and split each card into identity, pricing, and metadata wrappers while retaining existing Semi `Button`, `Tag`, `Input`, and modal behavior.
- [ ] Run the focused catalog test and confirm it passes.

### Task 3: Implement the clean technology visual refresh

**Files:**
- Modify: `portal/frontend/src/styles.css`

- [ ] Replace model-page rules with the approved canvas, typography, grid atmosphere, panel surfaces, card accent rail, hover/focus states, and responsive breakpoints.
- [ ] Add reduced-motion handling for card hover/entrance transitions.
- [ ] Run focused tests and `npm run build`.

### Task 4: Verify regression safety and polish

**Files:**
- No source changes expected unless verification finds an issue.

- [ ] Run the complete frontend test suite with `npm test`.
- [ ] Inspect the page at desktop, tablet, and 375px widths; verify no horizontal clipping, visible keyboard focus, and intact modal actions.
- [ ] Request a code review of the completed diff and address any important findings.
