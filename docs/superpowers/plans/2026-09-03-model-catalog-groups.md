# Model Catalog Groups Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Present NewAPI model groups as portal navigation and show each model in every group to which it belongs.

**Architecture:** The backend keeps its server-side NewAPI pricing proxy, but exposes all `enable_groups` values as `groups`. The React page derives unique groups and counts locally, filters a single catalogue response by the selected group plus search text, and presents responsive sidebar navigation.

**Tech Stack:** Java 17, Spring Boot, Jackson, JUnit 5, React, TypeScript, Semi UI, Vitest, Testing Library.

---

### Task 1: Preserve all NewAPI groups in the catalogue contract

**Files:**
- Modify: `backend/src/main/java/io/ztoken/portal/catalog/ModelCatalogItem.java`
- Modify: `backend/src/main/java/io/ztoken/portal/newapi/NewApiHttpClient.java:356-375`
- Modify: `backend/src/test/java/io/ztoken/portal/catalog/ModelCatalogControllerTest.java:38-50`

- [ ] **Step 1: Write the failing backend contract test**

Replace the mocked pricing item and assertion with:

```java
{"success":true,"data":[{"id":3,"model_name":"gpt-5-mini","vendor_name":"OpenAI","model_price":1.2,"completion_ratio":2,"cache_ratio":0.25,"enable_groups":["default","premium"],"quota_type":0}],"vendors":[]}
```

```java
assertThat(response.getBody().items()).containsExactly(
        new ModelCatalogItem("gpt-5-mini", "OpenAI", List.of("default", "premium"), 1.2, 2.4, 0.3, true));
```

Add a second item with no groups and assert `List.of("default")`.

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `mvn -q '-Dskip.frontend=true' '-Dtest=ModelCatalogControllerTest' test`

Expected: compilation failure because `ModelCatalogItem` still accepts a `String group`.

- [ ] **Step 3: Implement the minimal contract mapping**

Change `ModelCatalogItem` to:

```java
public record ModelCatalogItem(String name, String vendor, List<String> groups, Double inputPrice,
                               Double outputPrice, Double cachePrice, boolean priceAvailable) {
}
```

In `NewApiHttpClient.getModelCatalog()`, map `enable_groups` to all nonblank values; if none remain, use `List.of("default")`.

- [ ] **Step 4: Run the focused test and verify it passes**

Run: `mvn -q '-Dskip.frontend=true' '-Dtest=ModelCatalogControllerTest' test`

Expected: PASS.

- [ ] **Step 5: Commit the backend contract**

```powershell
git add backend/src/main/java/io/ztoken/portal/catalog/ModelCatalogItem.java backend/src/main/java/io/ztoken/portal/newapi/NewApiHttpClient.java backend/src/test/java/io/ztoken/portal/catalog/ModelCatalogControllerTest.java
git commit -m "feat: expose all model catalog groups"
```

### Task 2: Build grouped catalogue navigation

**Files:**
- Modify: `frontend/src/api/portal.ts:18-26`
- Modify: `frontend/src/features/catalog/ModelsPage.tsx`
- Modify: `frontend/src/features/catalog/__tests__/models-page.test.tsx`

- [ ] **Step 1: Write the failing page interaction test**

Use a fixture where `gpt-5-mini` has `groups: ["default", "premium"]` and `glm-5` has `groups: ["standard"]`. Assert that selecting `premium` shows `gpt-5-mini` but not `glm-5`; selecting `standard` shows `glm-5`; and search still filters within the selected group.

- [ ] **Step 2: Run the focused frontend test and verify it fails**

Run: `npm test -- --run src/features/catalog/__tests__/models-page.test.tsx`

Expected: failure because the page fixture/type and UI do not provide group navigation.

- [ ] **Step 3: Implement the minimal grouped page**

Update the client `ModelCatalogItem` with `groups: string[]`. In `ModelsPage`, derive sorted groups and their counts, track `selectedGroup` (`all` by default), and filter models with `model.groups.includes(selectedGroup)`. Replace the vendor select with accessible group buttons and retain the search, copy, pricing and remote states.

- [ ] **Step 4: Run the focused frontend test and verify it passes**

Run: `npm test -- --run src/features/catalog/__tests__/models-page.test.tsx`

Expected: PASS.

- [ ] **Step 5: Commit the grouped page**

```powershell
git add frontend/src/api/portal.ts frontend/src/features/catalog/ModelsPage.tsx frontend/src/features/catalog/__tests__/models-page.test.tsx
git commit -m "feat: group models in catalog"
```

### Task 3: Add responsive grouped catalogue styling and run regression checks

**Files:**
- Modify: `frontend/src/styles.css` (model catalogue styles)

- [ ] **Step 1: Add the failing layout expectation**

Extend the page test to assert that the group navigation has `aria-label="模型分组"` and each group control exposes pressed state via `aria-pressed`.

- [ ] **Step 2: Run the focused frontend test and verify it fails**

Run: `npm test -- --run src/features/catalog/__tests__/models-page.test.tsx`

Expected: FAIL until the navigation semantics are present.

- [ ] **Step 3: Implement responsive layout styles**

Use a desktop CSS grid with a fixed-width group navigation and flexible result column. At the existing mobile breakpoint, switch the group navigation to a horizontal scrolling row above the results. Give selected controls a visible border/background state and preserve keyboard focus outlines.

- [ ] **Step 4: Run all relevant checks**

Run:

```powershell
cd frontend
npm test -- --run
npm run build
cd ..\backend
mvn -q '-Dskip.frontend=true' test
```

Expected: frontend tests, frontend build, and backend tests all PASS.

- [ ] **Step 5: Commit the styles and tests**

```powershell
git add frontend/src/styles.css frontend/src/features/catalog/ModelsPage.tsx frontend/src/features/catalog/__tests__/models-page.test.tsx
git commit -m "style: add responsive model group navigation"
```
