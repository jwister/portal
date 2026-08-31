# Semi Design Portal Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the prototype portal interface with a Semi Design public site, authenticated account navigation, and NewAPI-derived model catalog.

**Architecture:** The React application uses Semi Design for all standard interactions and a small custom theme layer for the public hero, atmospheric background, code panel, and model cards. The Java BFF adds a customer-safe catalog endpoint sourced from NewAPI's existing `/api/pricing` response; no browser request accesses NewAPI directly.

**Tech Stack:** React 19, TypeScript, Vite, Semi Design, Vitest, React Testing Library, Java 17, Spring Boot 3.3, WebClient, MockWebServer.

---

## File structure

```text
portal/
  backend/src/main/java/io/ztoken/portal/
    catalog/ModelCatalogController.java
    catalog/ModelCatalog.java
    catalog/ModelCatalogItem.java
    newapi/NewApiClient.java
    newapi/NewApiHttpClient.java
  backend/src/test/java/io/ztoken/portal/catalog/ModelCatalogControllerTest.java
  frontend/src/
    App.tsx
    api/portal.ts
    theme/semi-theme.ts
    components/PublicHeader.tsx
    components/PublicFooter.tsx
    features/home/HomePage.tsx
    features/auth/SignInPage.tsx
    features/auth/SignUpPage.tsx
    features/catalog/ModelsPage.tsx
    features/**/__tests__/*.test.tsx
    styles.css
```

### Task 1: Install Semi Design and apply the shared theme

**Files:**
- Modify: `portal/frontend/package.json`
- Modify: `portal/frontend/package-lock.json`
- Create: `portal/frontend/src/theme/semi-theme.ts`
- Modify: `portal/frontend/src/main.tsx`
- Modify: `portal/frontend/src/styles.css`
- Test: `portal/frontend/src/__tests__/app-shell.test.tsx`

- [ ] **Step 1: Write a failing shell test for Semi public actions.**

```tsx
it('renders the public sign-in action as a button link', () => {
  render(<App />)
  expect(screen.getByRole('link', { name: '登录' })).toHaveAttribute('href', '/sign-in')
})
```

- [ ] **Step 2: Run the focused test and confirm it fails against the existing custom shell.**

Run: `npm --prefix frontend test -- --run src/__tests__/app-shell.test.tsx`

Expected: FAIL because the Semi header/action has not been rendered.

- [ ] **Step 3: Install and configure Semi Design.**

```ts
// src/theme/semi-theme.ts
export const semiTheme = {
  token: {
    colorPrimary: '#3389e8',
    colorBg0: '#ffffff',
    colorText0: '#131720',
    borderRadius: 12,
  },
}
```

Install `@douyinfe/semi-ui` and `@douyinfe/semi-icons`, import `@douyinfe/semi-ui/dist/css/semi.min.css`, and wrap the application in `ConfigProvider theme={semiTheme}`. Replace global dark-only colors with the approved warm-white, blue-mint atmospheric palette while preserving responsive behavior.

- [ ] **Step 4: Run the shell test and production build.**

Run: `npm --prefix frontend test -- --run src/__tests__/app-shell.test.tsx`

Run: `npm --prefix frontend run build`

Expected: PASS and a production Vite bundle.

- [ ] **Step 5: Commit the Semi foundation.**

```bash
git add frontend/package.json frontend/package-lock.json frontend/src/theme frontend/src/main.tsx frontend/src/styles.css frontend/src/__tests__/app-shell.test.tsx
git commit -m "feat: adopt Semi Design portal theme"
```

### Task 2: Add server-backed public authentication state

**Files:**
- Create: `portal/frontend/src/api/auth.ts`
- Create: `portal/frontend/src/auth/use-auth-status.ts`
- Create: `portal/frontend/src/components/PublicHeader.tsx`
- Modify: `portal/frontend/src/App.tsx`
- Test: `portal/frontend/src/components/__tests__/public-header.test.tsx`

- [ ] **Step 1: Write failing tests for anonymous and authenticated header actions.**

```tsx
it('shows 登录 after /api/auth/me returns 401', async () => {
  mockAuth(401)
  render(<PublicHeader />)
  expect(await screen.findByRole('link', { name: '登录' })).toHaveAttribute('href', '/sign-in')
})

it('shows 控制台 after /api/auth/me returns a profile', async () => {
  mockAuth(200, { id: 7, username: 'alice' })
  render(<PublicHeader />)
  expect(await screen.findByRole('link', { name: '控制台' })).toHaveAttribute('href', '/console/dashboard')
})
```

- [ ] **Step 2: Run the header tests and confirm they fail.**

Run: `npm --prefix frontend test -- --run src/components/__tests__/public-header.test.tsx`

Expected: FAIL because no auth-status hook or Semi header exists.

- [ ] **Step 3: Implement `fetch('/api/auth/me', { credentials: 'include' })` and Semi navigation.**

Return `authenticated`, `anonymous`, or `loading` from `useAuthStatus`. `PublicHeader` renders Home, Models, Documentation, Purchase, language control, and exactly one right-side action: 登录 for anonymous and 控制台 for authenticated. Do not render registration in public navigation.

- [ ] **Step 4: Run header tests and build.**

Run: `npm --prefix frontend test -- --run src/components/__tests__/public-header.test.tsx`

Run: `npm --prefix frontend run build`

Expected: PASS.

- [ ] **Step 5: Commit authentication-aware navigation.**

```bash
git add frontend/src/api/auth.ts frontend/src/auth frontend/src/components/PublicHeader.tsx frontend/src/App.tsx frontend/src/components/__tests__/public-header.test.tsx
git commit -m "feat: add authentication-aware public navigation"
```

### Task 3: Rebuild sign-in and sign-up with Semi Form

**Files:**
- Modify: `portal/frontend/src/features/auth/SignInPage.tsx`
- Modify: `portal/frontend/src/features/auth/SignUpPage.tsx`
- Modify: `portal/frontend/src/features/auth/__tests__/sign-in-page.test.tsx`
- Modify: `portal/frontend/src/features/auth/__tests__/sign-up-page.test.tsx`

- [ ] **Step 1: Write failing tests for reciprocal auth links.**

```tsx
it('offers registration only from the sign-in page', () => {
  render(<SignInPage />)
  expect(screen.getByRole('link', { name: '创建账户' })).toHaveAttribute('href', '/sign-up')
})

it('offers sign-in from the sign-up page', () => {
  render(<SignUpPage />)
  expect(screen.getByRole('link', { name: '登录' })).toHaveAttribute('href', '/sign-in')
})
```

- [ ] **Step 2: Run the auth-page tests and confirm failure.**

Run: `npm --prefix frontend test -- --run src/features/auth/__tests__/sign-in-page.test.tsx src/features/auth/__tests__/sign-up-page.test.tsx`

Expected: FAIL because reciprocal Semi links do not exist.

- [ ] **Step 3: Replace custom fields with Semi `Form`, `Input`, `Button`, `Typography`, and `Toast`.**

Keep the existing portal endpoints: `POST /api/auth/login` and `POST /api/auth/register`. Show a Semi validation error for blank inputs, a safe server failure toast, and the reciprocal account link below the submit button. On login success navigate to `/console/dashboard`; on registration success navigate to `/sign-in`.

- [ ] **Step 4: Run tests and build.**

Run: `npm --prefix frontend test -- --run src/features/auth/__tests__/sign-in-page.test.tsx src/features/auth/__tests__/sign-up-page.test.tsx`

Run: `npm --prefix frontend run build`

Expected: PASS without browser-navigation warnings.

- [ ] **Step 5: Commit the Semi authentication pages.**

```bash
git add frontend/src/features/auth
git commit -m "feat: redesign account access with Semi forms"
```

### Task 4: Rebuild the Ztoken home page

**Files:**
- Create: `portal/frontend/src/features/home/HomePage.tsx`
- Create: `portal/frontend/src/components/PublicFooter.tsx`
- Modify: `portal/frontend/src/App.tsx`
- Modify: `portal/frontend/src/i18n/locales/en.json`
- Modify: `portal/frontend/src/i18n/locales/zh-CN.json`
- Modify: `portal/frontend/src/styles.css`
- Test: `portal/frontend/src/features/home/__tests__/home-page.test.tsx`

- [ ] **Step 1: Write a failing content-hierarchy test.**

```tsx
it('renders the API hero, three onboarding steps, and final API-key call to action', () => {
  render(<HomePage />)
  expect(screen.getByRole('heading', { name: '一个 API，连接海量 AI 模型' })).toBeVisible()
  expect(screen.getByText('创建账户')).toBeVisible()
  expect(screen.getByText('充值余额')).toBeVisible()
  expect(screen.getByText('调用 API')).toBeVisible()
  expect(screen.getByRole('link', { name: '获取 API Key' })).toHaveAttribute('href', '/sign-in')
})
```

- [ ] **Step 2: Run the home-page test and confirm failure.**

Run: `npm --prefix frontend test -- --run src/features/home/__tests__/home-page.test.tsx`

Expected: FAIL because the redesigned home page does not exist.

- [ ] **Step 3: Implement the Semi and custom-layout home page.**

Use Semi `Button`, `Card`, `Tag`, `Divider`, and `Typography`. Render an original Ztoken proposition and a code panel that shows a generic OpenAI-compatible request/response. Add model/provider capability statistics, feature cards, onboarding steps, final CTA, and footer. Keep the right hero panel and gradient atmosphere as custom CSS; do not copy the reference-site text or assets.

- [ ] **Step 4: Run home tests and build.**

Run: `npm --prefix frontend test -- --run src/features/home/__tests__/home-page.test.tsx`

Run: `npm --prefix frontend run build`

Expected: PASS.

- [ ] **Step 5: Commit the public home redesign.**

```bash
git add frontend/src/features/home frontend/src/components/PublicFooter.tsx frontend/src/App.tsx frontend/src/i18n frontend/src/styles.css
git commit -m "feat: redesign public portal home"
```

### Task 5: Add a safe NewAPI model catalog endpoint

**Files:**
- Create: `portal/backend/src/main/java/io/ztoken/portal/catalog/ModelCatalog.java`
- Create: `portal/backend/src/main/java/io/ztoken/portal/catalog/ModelCatalogItem.java`
- Create: `portal/backend/src/main/java/io/ztoken/portal/catalog/ModelCatalogController.java`
- Modify: `portal/backend/src/main/java/io/ztoken/portal/newapi/NewApiClient.java`
- Modify: `portal/backend/src/main/java/io/ztoken/portal/newapi/NewApiHttpClient.java`
- Test: `portal/backend/src/test/java/io/ztoken/portal/catalog/ModelCatalogControllerTest.java`

- [ ] **Step 1: Write a failing MockWebServer contract test.**

```java
@Test void catalogMapsOnlyPublicPricingFields() throws Exception {
  newApi.enqueue(json(200, """
    {"success":true,"data":[{"id":3,"model_name":"gpt-5-mini","vendor_name":"OpenAI","model_price":1.2,"completion_ratio":2,"enable_groups":["default"]}],"vendors":[]}
  """));
  ResponseEntity<ModelCatalog> response = http.getForEntity("/api/catalog/models", ModelCatalog.class);
  assertThat(response.getBody().items()).containsExactly(new ModelCatalogItem("gpt-5-mini", "OpenAI", "default", 1.2, 2.4, null, true));
  assertThat(newApi.takeRequest().getPath()).isEqualTo("/api/pricing");
}
```

- [ ] **Step 2: Run the catalog test and confirm failure.**

Run: `mvn -f backend/pom.xml test -Dtest=ModelCatalogControllerTest`

Expected: FAIL because the catalog endpoint and DTOs do not exist.

- [ ] **Step 3: Implement catalog mapping from NewAPI `/api/pricing`.**

Call only `GET /api/pricing`. Map `model_name`, `vendor_name`, first enabled group, `model_price`, `completion_ratio`, `cache_ratio`, `quota_type`, `tags`, and supported endpoint types into portal DTOs. For token-priced models derive output price as `model_price * completion_ratio`; for absent or dynamic pricing return null amounts and `priceAvailable=false`. Never return upstream IDs, group-ratio maps, billing expressions, channel data, or administrator fields.

- [ ] **Step 4: Run catalog tests and the backend suite.**

Run: `mvn -f backend/pom.xml test -Dtest=ModelCatalogControllerTest`

Run: `mvn -f backend/pom.xml test`

Expected: PASS.

- [ ] **Step 5: Commit the catalog BFF.**

```bash
git add backend/src/main/java/io/ztoken/portal/catalog backend/src/main/java/io/ztoken/portal/newapi backend/src/test/java/io/ztoken/portal/catalog
git commit -m "feat: add public model catalog BFF"
```

### Task 6: Build the Semi model plaza

**Files:**
- Create: `portal/frontend/src/features/catalog/ModelsPage.tsx`
- Create: `portal/frontend/src/features/catalog/model-catalog.ts`
- Modify: `portal/frontend/src/api/portal.ts`
- Modify: `portal/frontend/src/App.tsx`
- Modify: `portal/frontend/src/i18n/locales/en.json`
- Modify: `portal/frontend/src/i18n/locales/zh-CN.json`
- Modify: `portal/frontend/src/styles.css`
- Test: `portal/frontend/src/features/catalog/__tests__/models-page.test.tsx`

- [ ] **Step 1: Write a failing catalog search test.**

```tsx
it('filters Semi model cards by model name', async () => {
  mockCatalog([{ name: 'gpt-5-mini', vendor: 'OpenAI' }, { name: 'glm-5', vendor: 'Zhipu' }])
  render(<ModelsPage />)
  await userEvent.type(await screen.findByPlaceholderText('搜索模型'), 'glm')
  expect(screen.getByText('glm-5')).toBeVisible()
  expect(screen.queryByText('gpt-5-mini')).not.toBeInTheDocument()
})
```

- [ ] **Step 2: Run the models-page test and confirm failure.**

Run: `npm --prefix frontend test -- --run src/features/catalog/__tests__/models-page.test.tsx`

Expected: FAIL because the catalog page does not exist.

- [ ] **Step 3: Implement Semi `Input`, `Select`, `Button`, `Card`, `Tag`, `Skeleton`, and `Empty` states.**

Fetch `/api/catalog/models` with credentials. Render the reference-inspired heading, count, search, vendor/group filters, sorting control, three-column responsive card grid, model-name copy action, and details action. Display input/output/cache prices only if `priceAvailable`; otherwise render the translated unavailable label.

- [ ] **Step 4: Run catalog UI tests and build.**

Run: `npm --prefix frontend test -- --run src/features/catalog/__tests__/models-page.test.tsx`

Run: `npm --prefix frontend run build`

Expected: PASS.

- [ ] **Step 5: Commit the model plaza.**

```bash
git add frontend/src/features/catalog frontend/src/api/portal.ts frontend/src/App.tsx frontend/src/i18n frontend/src/styles.css
git commit -m "feat: add Semi model plaza"
```
