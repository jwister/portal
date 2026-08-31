# Customer Portal Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a one-port React and Java customer portal with NewAPI-backed email/OAuth authentication, model pages, a bilingual protected console, and safe user-scoped management APIs.

**Architecture:** React is built by Maven and served from the Spring Boot JAR. Spring Boot owns a secure portal session that encrypts the NewAPI access token returned by NewAPI login, and it exposes only allowlisted BFF endpoints for the current user. Model inference remains a direct client-to-NewAPI `/v1` call.

**Tech Stack:** Java 17, Spring Boot 3.3, Spring Data JPA, Flyway, WebClient, MySQL, React 19, TypeScript, Vite, React Router, TanStack Query, i18next, Vitest, React Testing Library.

---

## File structure

```text
portal/
  .gitignore
  README.md
  backend/
    pom.xml
    src/main/java/io/ztoken/portal/
      PortalApplication.java
      config/PortalProperties.java
      config/WebConfig.java
      web/SpaRouteController.java
      session/PortalSession.java
      session/PortalSessionRepository.java
      session/PortalSessionService.java
      session/SessionCrypto.java
      newapi/NewApiClient.java
      newapi/NewApiHttpClient.java
      newapi/NewApiResponse.java
      auth/AuthController.java
      auth/PortalAuthInterceptor.java
      console/ConsoleController.java
      catalog/ModelCatalogController.java
      error/ApiExceptionHandler.java
    src/main/resources/application.yml
    src/main/resources/db/migration/V1__create_portal_sessions.sql
    src/test/java/io/ztoken/portal/...
  frontend/
    package.json
    package-lock.json
    vite.config.ts
    tsconfig.json
    src/
      main.tsx
      App.tsx
      api/client.ts
      api/portal.ts
      i18n/index.ts
      i18n/locales/en.json
      i18n/locales/zh-CN.json
      auth/auth-store.ts
      auth/RequireAuth.tsx
      routes.tsx
      components/AppHeader.tsx
      components/ConsoleLayout.tsx
      features/auth/AuthPage.tsx
      features/catalog/ModelCatalogPage.tsx
      features/home/HomePage.tsx
      features/console/DashboardPage.tsx
      features/console/TokensPage.tsx
      features/console/LogsPage.tsx
      features/console/ProfilePage.tsx
      features/**/__tests__/*.test.tsx
```

### Task 1: Bootstrap the single-port application

**Files:**
- Create: `portal/.gitignore`
- Create: `portal/README.md`
- Create: `portal/backend/pom.xml`
- Create: `portal/backend/src/main/java/io/ztoken/portal/PortalApplication.java`
- Create: `portal/backend/src/main/java/io/ztoken/portal/web/SpaRouteController.java`
- Create: `portal/backend/src/main/resources/application.yml`
- Create: `portal/backend/src/test/resources/application.yml`
- Create: `portal/backend/src/test/java/io/ztoken/portal/PortalApplicationTest.java`
- Create: `portal/frontend/package.json`
- Create: `portal/frontend/vite.config.ts`
- Create: `portal/frontend/tsconfig.json`
- Create: `portal/frontend/index.html`
- Create: `portal/frontend/src/main.tsx`
- Create: `portal/frontend/src/App.tsx`

- [ ] **Step 1: Write the failing Spring context and SPA fallback tests.**

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PortalApplicationTest {
  @Autowired TestRestTemplate http;

  @Test void unknownClientRouteReturnsSpaEntry() {
    ResponseEntity<String> response = http.getForEntity("/models", String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("<div id=\"root\">");
  }
}
```

- [ ] **Step 2: Run the new backend test and confirm it fails because the project does not exist.**

Run: `mvn -f backend/pom.xml test -Dtest=PortalApplicationTest`

Expected: Maven reports that `backend/pom.xml` is missing.

- [ ] **Step 3: Create the Maven and Vite skeleton.**

Use Spring Boot 3.3 with `web`, `validation`, `data-jpa`, `webflux`, `flyway-core`, `flyway-mysql`, MySQL runtime driver, H2 test driver, MockWebServer test driver, and `spring-boot-starter-test`. Configure `frontend-maven-plugin` to copy `../frontend` into `target/frontend-build`, install Node 20, run `npm ci`, then `npm run build` during `generate-resources`; copy `dist/**` to `static/**`. Set the test profile datasource to `jdbc:h2:mem:portal;MODE=MySQL;DB_CLOSE_DELAY=-1` and enable Flyway. Add this controller:

```java
@Controller
class SpaRouteController {
  @GetMapping({"/", "/models", "/purchase", "/console/**", "/sign-in", "/sign-up"})
  String index() { return "forward:/index.html"; }
}
```

Make the React entry render `<h1>Ztoken</h1>` into `#root`, and make Vite build to `dist`.

- [ ] **Step 4: Run the test and package the JAR.**

Run: `mvn -f backend/pom.xml clean package`

Expected: tests pass and `backend/target/portal-*.jar` contains `BOOT-INF/classes/static/index.html`.

- [ ] **Step 5: Commit the application skeleton.**

```bash
git add .gitignore README.md backend frontend
git commit -m "feat: bootstrap one-port customer portal"
```

### Task 2: Add configuration, encrypted portal sessions, and session tests

**Files:**
- Create: `portal/backend/src/main/java/io/ztoken/portal/config/PortalProperties.java`
- Create: `portal/backend/src/main/java/io/ztoken/portal/config/WebConfig.java`
- Create: `portal/backend/src/main/java/io/ztoken/portal/session/PortalSession.java`
- Create: `portal/backend/src/main/java/io/ztoken/portal/session/PortalSessionRepository.java`
- Create: `portal/backend/src/main/java/io/ztoken/portal/session/SessionCrypto.java`
- Create: `portal/backend/src/main/java/io/ztoken/portal/session/PortalSessionService.java`
- Create: `portal/backend/src/main/java/io/ztoken/portal/session/UnauthenticatedException.java`
- Create: `portal/backend/src/main/resources/db/migration/V1__create_portal_sessions.sql`
- Modify: `portal/backend/src/main/resources/application.yml`
- Create: `portal/backend/src/test/java/io/ztoken/portal/session/PortalSessionServiceTest.java`

- [ ] **Step 1: Write failing tests for session encryption, expiry, and invalid cookie rejection.**

```java
@Test void savesOnlyEncryptedNewApiTokenAndRestoresCurrentUser() {
  PortalSession session = sessions.create(new AuthenticatedNewApiUser(42L, "alice"), "access-token");
  assertThat(repository.findById(session.getId()).orElseThrow().getEncryptedAccessToken())
      .doesNotContain("access-token");
  assertThat(sessions.require(session.getId())).extracting(PortalPrincipal::userId).isEqualTo(42L);
}

@Test void expiredOrTamperedSessionIsRejected() {
  assertThatThrownBy(() -> sessions.require("bad-cookie")).isInstanceOf(UnauthenticatedException.class);
}
```

- [ ] **Step 2: Run the focused test and confirm it fails before the classes exist.**

Run: `mvn -f backend/pom.xml test -Dtest=PortalSessionServiceTest`

Expected: compilation fails because `PortalSessionService` is undefined.

- [ ] **Step 3: Implement encrypted, server-side sessions.**

Create table `portal_sessions(id varchar(64) primary key, newapi_user_id bigint not null, username varchar(255) not null, encrypted_access_token text not null, expires_at timestamp not null, created_at timestamp not null, revoked_at timestamp null)`. Implement AES-GCM encryption with a 32-byte base64 key from `PORTAL_SESSION_KEY`; use a random 12-byte IV prepended to ciphertext. Generate a 48-character opaque cookie ID, persist only the encrypted NewAPI access token, set a `PORTAL_SESSION` HttpOnly/Secure/SameSite=Lax cookie, and reject revoked, expired, or malformed sessions.

- [ ] **Step 4: Run session tests and the migration-backed application test.**

Run: `mvn -f backend/pom.xml test -Dtest=PortalSessionServiceTest,PortalApplicationTest`

Expected: PASS; no plaintext access token occurs in the database entity or log output.

- [ ] **Step 5: Commit session infrastructure.**

```bash
git add backend
git commit -m "feat: add encrypted portal sessions"
```

### Task 3: Implement the typed NewAPI client and user authentication BFF

**Files:**
- Create: `portal/backend/src/main/java/io/ztoken/portal/newapi/NewApiClient.java`
- Create: `portal/backend/src/main/java/io/ztoken/portal/newapi/NewApiHttpClient.java`
- Create: `portal/backend/src/main/java/io/ztoken/portal/newapi/NewApiUser.java`
- Create: `portal/backend/src/main/java/io/ztoken/portal/newapi/NewApiException.java`
- Create: `portal/backend/src/main/java/io/ztoken/portal/auth/AuthController.java`
- Create: `portal/backend/src/main/java/io/ztoken/portal/auth/LoginRequest.java`
- Create: `portal/backend/src/main/java/io/ztoken/portal/auth/RegisterRequest.java`
- Create: `portal/backend/src/main/java/io/ztoken/portal/auth/OAuthCompleteRequest.java`
- Create: `portal/backend/src/main/java/io/ztoken/portal/auth/PortalAuthInterceptor.java`
- Create: `portal/backend/src/main/java/io/ztoken/portal/error/ApiExceptionHandler.java`
- Modify: `portal/backend/src/main/java/io/ztoken/portal/config/WebConfig.java`
- Create: `portal/backend/src/test/java/io/ztoken/portal/auth/AuthControllerTest.java`
- Create: `portal/backend/src/test/java/io/ztoken/portal/newapi/NewApiHttpClientTest.java`

- [ ] **Step 1: Write failing WireMock/MockWebServer contract tests for NewAPI login and current-user lookup.**

```java
@Test void loginStoresNewApiAccessTokenInServerSession() {
  newApi.enqueue(json(200, "{\"success\":true,\"data\":{\"access_token\":\"at\",\"user\":{\"id\":7,\"username\":\"alice\"}}}"));
  ResponseEntity<Void> response = http.postForEntity("/api/auth/login", new LoginRequest("alice", "secret"), Void.class);
  assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("PORTAL_SESSION=");
  assertThat(newApi.takeRequest().getPath()).isEqualTo("/api/user/login");
}

@Test void currentUserRequestUsesBearerTokenAndMatchingUserHeader() {
  client.getSelf(principal);
  RecordedRequest request = newApi.takeRequest();
  assertThat(request.getHeader("Authorization")).isEqualTo("Bearer at");
  assertThat(request.getHeader("New-Api-User")).isEqualTo("7");
}
```

- [ ] **Step 2: Run the contract tests and confirm failure.**

Run: `mvn -f backend/pom.xml test -Dtest=AuthControllerTest,NewApiHttpClientTest`

Expected: compilation fails because the client and controller do not exist.

- [ ] **Step 3: Implement the explicit BFF contract.**

Implement `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/logout`, and `GET /api/auth/me`. Login forwards only username/password to `POST /api/user/login`, parses `data.access_token` and `data.user`, creates the encrypted portal session, and returns a safe profile. Register forwards its validated NewAPI registration payload without persisting it. The client must have named methods for `/api/user/self`, `/api/user/models`, `/api/user/self/groups`, `/api/token/**`, `/api/log/self/**`, and `/api/data/self`; it must not accept an arbitrary upstream URL or request path.

For OAuth, implement `GET /api/auth/oauth/providers`, `POST /api/auth/oauth/{provider}/state`, and `POST /api/auth/oauth/{provider}/complete` only for `github` and `oidc`. The providers endpoint obtains safe public provider data from NewAPI `/api/status`; the state endpoint forwards `{ "provider": provider, "intent": "login" }` to NewAPI `/api/oauth/state`; the complete endpoint forwards the provider callback `code`, `state`, `error`, and `error_description` to NewAPI `GET /api/oauth/{provider}` and turns the returned auth bundle into a portal session. The React callback route is `/oauth/{provider}`. Configure NewAPI `ServerAddress` to `PORTAL_PUBLIC_URL` so its GitHub/OIDC token exchange uses `PORTAL_PUBLIC_URL/oauth/github` and `PORTAL_PUBLIC_URL/oauth/oidc` as the registered provider callback URLs. Reject an unsupported provider, malformed code/state, or external return URL with HTTP 400.

- [ ] **Step 4: Run tests and verify bearer headers are not browser-controlled.**

Run: `mvn -f backend/pom.xml test -Dtest=AuthControllerTest,NewApiHttpClientTest`

Expected: PASS; requests with a fabricated `New-Api-User` header cannot change the resolved user.

- [ ] **Step 5: Commit authentication and NewAPI API boundary.**

```bash
git add backend
git commit -m "feat: add NewAPI-backed portal authentication"
```

### Task 4: Implement model catalog, profile, dashboard, token, and log BFF endpoints

**Files:**
- Create: `portal/backend/src/main/java/io/ztoken/portal/catalog/ModelCatalogController.java`
- Create: `portal/backend/src/main/java/io/ztoken/portal/catalog/ModelCatalogService.java`
- Create: `portal/backend/src/main/java/io/ztoken/portal/catalog/PublicConfigController.java`
- Create: `portal/backend/src/main/java/io/ztoken/portal/console/ConsoleController.java`
- Create: `portal/backend/src/main/java/io/ztoken/portal/console/ConsoleService.java`
- Create: `portal/backend/src/main/java/io/ztoken/portal/console/PortalDtos.java`
- Create: `portal/backend/src/test/java/io/ztoken/portal/catalog/ModelCatalogControllerTest.java`
- Create: `portal/backend/src/test/java/io/ztoken/portal/console/ConsoleControllerTest.java`

- [ ] **Step 1: Write failing endpoint tests for user scoping and dashboard aggregation.**

```java
@Test void dashboardCombinesOnlyTheAuthenticatedUsersSelfStatistics() {
  mockSelf(7L); mockLogStats(7L); mockQuotaData(7L);
  ResponseEntity<DashboardDto> response = authenticatedGet("/api/console/dashboard");
  assertThat(response.getBody().availableQuota()).isEqualTo(900L);
  assertThat(response.getBody().requestCount()).isEqualTo(12L);
}

@Test void publicCatalogNeverForwardsAnAdministratorModelPayload() {
  ResponseEntity<String> response = http.getForEntity("/api/catalog/models", String.class);
  assertThat(response.getBody()).doesNotContain("admin_permissions");
}
```

- [ ] **Step 2: Run the controller tests and confirm they fail.**

Run: `mvn -f backend/pom.xml test -Dtest=ModelCatalogControllerTest,ConsoleControllerTest`

Expected: compilation fails because the catalog and console controllers do not exist.

- [ ] **Step 3: Implement allowlisted customer endpoints.**

Provide `GET /api/public/config`, `GET /api/catalog/models`, `GET /api/console/dashboard`, `GET/PUT /api/console/profile`, `GET/POST/PUT/DELETE /api/console/tokens`, `GET /api/console/tokens/{id}/usage`, `GET /api/console/logs`, and `GET /api/console/logs/stats`. `GET /api/public/config` returns only the configured documentation URL and public portal name. Each user endpoint loads the current portal principal, calls only the matching NewAPI user endpoint with the stored token, and maps the response into portal DTOs. Never return NewAPI access tokens, administrator permissions, internal remarks, or raw unbounded provider responses. Return a consistent `{code, message, data}` error body for upstream 401, 403, 404, and 5xx results.

- [ ] **Step 4: Run controller tests and the backend suite.**

Run: `mvn -f backend/pom.xml test`

Expected: PASS; a session for user 7 cannot fetch data with user 8 by changing route/query/header values.

- [ ] **Step 5: Commit the console API.**

```bash
git add backend
git commit -m "feat: expose customer console BFF endpoints"
```

### Task 5: Add React application shell, bilingual language detection, and authentication UI

**Files:**
- Create: `portal/frontend/src/i18n/index.ts`
- Create: `portal/frontend/src/i18n/locales/en.json`
- Create: `portal/frontend/src/i18n/locales/zh-CN.json`
- Create: `portal/frontend/src/api/client.ts`
- Create: `portal/frontend/src/api/portal.ts`
- Create: `portal/frontend/src/auth/auth-store.ts`
- Create: `portal/frontend/src/auth/RequireAuth.tsx`
- Create: `portal/frontend/src/components/AppHeader.tsx`
- Create: `portal/frontend/src/routes.tsx`
- Create: `portal/frontend/src/features/auth/AuthPage.tsx`
- Create: `portal/frontend/src/test/setup.ts`
- Create: `portal/frontend/src/i18n/__tests__/language-detection.test.ts`
- Create: `portal/frontend/src/features/auth/__tests__/auth-page.test.tsx`

- [ ] **Step 1: Write failing frontend tests for language selection and protected navigation.**

```tsx
it('selects Chinese for a zh browser language and persists an explicit switch', () => {
  Object.defineProperty(navigator, 'languages', { value: ['zh-CN'], configurable: true });
  expect(resolveInitialLanguage()).toBe('zh-CN');
  setLanguage('en');
  expect(localStorage.getItem('ztoken.locale')).toBe('en');
});

it('redirects an anonymous visitor from console to sign-in', async () => {
  render(<App initialPath="/console/dashboard" />);
  expect(await screen.findByRole('heading', { name: /sign in/i })).toBeVisible();
});
```

- [ ] **Step 2: Run the focused frontend tests and confirm failure.**

Run: `npm --prefix frontend test -- --run src/i18n/__tests__/language-detection.test.ts src/features/auth/__tests__/auth-page.test.tsx`

Expected: Vitest reports missing modules.

- [ ] **Step 3: Implement the shell and auth routes.**

Configure i18next with exactly `en` and `zh-CN`; select `zh-CN` if the first browser language starts with `zh`, otherwise `en`, and save manual selection under `ztoken.locale`. Configure Axios with `baseURL: '/api'` and `withCredentials: true`. Add `@testing-library/jest-dom` in `src/test/setup.ts` and configure Vitest `setupFiles` to load it. Add public header links for Home, Models, Documentation, Purchase, Console, a language switch, and Sign in. Load the external documentation URL from `/api/public/config`; render it as a normal external link and hide it when the configured URL is empty. Implement sign-in/register forms that call the BFF, display field/server errors, and redirect successful sign-in to `/console/dashboard`. The GitHub and Google buttons first request an OAuth flow token from the BFF, then navigate to the authorization URL built from the safe provider settings and `PORTAL_PUBLIC_URL/oauth/{provider}`. The callback page posts the code/state to the BFF complete endpoint and then routes to `/console/dashboard`.

- [ ] **Step 4: Run frontend tests and build.**

Run: `npm --prefix frontend test`

Run: `npm --prefix frontend run build`

Expected: all tests pass and Vite produces `frontend/dist`.

- [ ] **Step 5: Commit the bilingual public shell and authentication UI.**

```bash
git add frontend
git commit -m "feat: add bilingual portal shell and authentication"
```

### Task 6: Build public home/model pages and protected console pages

**Files:**
- Create: `portal/frontend/src/features/home/HomePage.tsx`
- Create: `portal/frontend/src/features/catalog/ModelCatalogPage.tsx`
- Create: `portal/frontend/src/features/catalog/model-catalog.ts`
- Create: `portal/frontend/src/components/ConsoleLayout.tsx`
- Create: `portal/frontend/src/features/console/DashboardPage.tsx`
- Create: `portal/frontend/src/features/console/TokensPage.tsx`
- Create: `portal/frontend/src/features/console/LogsPage.tsx`
- Create: `portal/frontend/src/features/console/ProfilePage.tsx`
- Create: `portal/frontend/src/features/catalog/__tests__/model-catalog.test.tsx`
- Create: `portal/frontend/src/features/console/__tests__/dashboard.test.tsx`
- Create: `portal/frontend/src/features/console/__tests__/tokens.test.tsx`

- [ ] **Step 1: Write failing behavior tests for catalog and console data states.**

```tsx
it('renders grouped models with price and shows a retry after catalog failure', async () => {
  server.use(http.get('/api/catalog/models', () => HttpResponse.json({ code: 'UPSTREAM_UNAVAILABLE' }, { status: 503 })));
  render(<ModelCatalogPage />);
  expect(await screen.findByRole('button', { name: /retry/i })).toBeVisible();
});

it('renders dashboard balance and request count from the BFF response', async () => {
  mockDashboard({ availableQuota: 900, requestCount: 12, usedQuota: 100, tokenUsage: 300 });
  render(<DashboardPage />);
  expect(await screen.findByText('900')).toBeVisible();
  expect(screen.getByText('12')).toBeVisible();
});
```

- [ ] **Step 2: Run the new page tests and confirm failure.**

Run: `npm --prefix frontend test -- --run src/features/catalog/__tests__/model-catalog.test.tsx src/features/console/__tests__/dashboard.test.tsx src/features/console/__tests__/tokens.test.tsx`

Expected: Vitest reports missing pages and mocks.

- [ ] **Step 3: Implement pages using React Query.**

Create model grouping helpers that accept the BFF catalog DTO and never calculate prices from hard-coded ratio values. Render loading, empty, error/retry, and populated states. Add the console navigation for Dashboard, Recharge, Orders, Tokens, Logs, and Profile; the payment pages initially link to the routes implemented by the payment plan. Implement token create/edit/delete confirmation, logs filters/pagination, and profile update using only portal BFF calls. Use accessible labels, semantic headings, and mobile-first CSS.

- [ ] **Step 4: Run the frontend test suite and type check.**

Run: `npm --prefix frontend test`

Run: `npm --prefix frontend run build`

Expected: PASS with a production bundle.

- [ ] **Step 5: Commit the customer-facing pages and console.**

```bash
git add frontend
git commit -m "feat: add customer catalog and console"
```

### Task 7: Verify the complete portal core package

**Files:**
- Modify: `portal/README.md`
- Create: `portal/backend/src/test/java/io/ztoken/portal/web/OnePortPackageTest.java`

- [ ] **Step 1: Write a failing JAR package verification test.**

```java
@Test void packagedJarContainsSpaAndApiRoutes() throws Exception {
  Process process = startJar();
  assertThat(get("/").body()).contains("id=\"root\"");
  assertThat(get("/api/auth/me").statusCode()).isEqualTo(401);
}
```

- [ ] **Step 2: Run the verification test and confirm that it fails until the packaged JAR exists.**

Run: `mvn -f backend/pom.xml test -Dtest=OnePortPackageTest`

Expected: FAIL before `package` has created the executable JAR.

- [ ] **Step 3: Document and implement the verification command.**

In `README.md`, document required environment variables without values, the `mvn -f backend/pom.xml clean package` command, and how to launch the resulting JAR. Make the test locate the latest `target/*.jar`, start it with a random server port, wait for `/`, then terminate the process.

- [ ] **Step 4: Run full core verification.**

Run: `mvn -f backend/pom.xml clean package`

Run: `mvn -f backend/pom.xml test -Dtest=OnePortPackageTest`

Expected: JAR starts, `/` returns the SPA entry, and `/api/auth/me` returns a safe unauthenticated response.

- [ ] **Step 5: Commit package verification and documentation.**

```bash
git add README.md backend
git commit -m "test: verify one-port portal package"
```
