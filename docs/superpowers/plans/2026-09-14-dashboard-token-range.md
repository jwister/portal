# Dashboard Token Range Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the dashboard Token card and Token trend display the selected seven- or thirty-day usage window.

**Architecture:** The backend already validates the requested range and queries that many Shanghai business days. Pass that integer to the analytics value object so its Token series uses the same number of days. The React page retains the selected range as its source of truth for the Token chart title and sums the returned series for the Token card.

**Tech Stack:** Java 21, Spring Boot, JUnit 5, AssertJ, React 19, TypeScript, Vitest, Testing Library.

---

### Task 1: Cover range-aware Token aggregation in the backend

**Files:**
- Modify: `backend/src/test/java/io/ztoken/portal/newapi/NewApiHttpClientTest.java`
- Modify: `backend/src/main/java/io/ztoken/portal/newapi/NewApiHttpClient.java:247-288`
- Modify: `backend/src/main/java/io/ztoken/portal/console/DashboardAnalytics.java:36-73`

- [ ] **Step 1: Write the failing 30-day aggregation test**

Add this test before the existing seven-day analytics test in `NewApiHttpClientTest`:

```java
@Test
void analyticsBuildsThirtyDayTokenSeriesForThirtyDayRange() throws Exception {
    Instant now = Instant.parse("2024-03-30T04:00:00Z");
    Instant olderUsage = now.minus(20, ChronoUnit.DAYS);
    NEW_API.enqueue(new MockResponse()
            .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            .setBody("""
                    {"success":true,"data":[
                      {"created_at":%d,"model_name":"gpt-4o","quota":40,"count":2,"token_used":200},
                      {"created_at":%d,"model_name":"gpt-4o","quota":10,"count":1,"token_used":100}
                    ]}
                    """.formatted(olderUsage.getEpochSecond(), now.getEpochSecond())));

    DashboardAnalytics result = clientAt(now)
            .getDashboardAnalytics(new PortalPrincipal(7L, "alice", "access-token"), 30);

    assertThat(result.tokenUsage()).hasSize(30);
    assertThat(result.tokenUsage()).contains(
            new DashboardAnalytics.TokenUsage("2024-03-10", 200L),
            new DashboardAnalytics.TokenUsage("2024-03-30", 100L));
    assertThat(result.tokenUsage().stream()
            .mapToLong(DashboardAnalytics.TokenUsage::tokenUsage)
            .sum()).isEqualTo(300L);
    NEW_API.takeRequest();
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -f backend/pom.xml -Dskip.frontend=true -Dtest=NewApiHttpClientTest#analyticsBuildsThirtyDayTokenSeriesForThirtyDayRange test`

Expected: FAIL because `result.tokenUsage()` has size `7`, not `30`.

- [ ] **Step 3: Make Token-series construction range-aware**

Change the final call in `NewApiHttpClient.getDashboardAnalytics` to:

```java
return DashboardAnalytics.from(dailyTotals, modelQuotas, endDate, rangeDays);
```

Change `DashboardAnalytics.from` to accept `rangeDays`, preserve its existing empty-data early return, and derive the zero-filled series as follows:

```java
List<TokenUsage> tokenUsage = new ArrayList<>();
for (int offset = rangeDays - 1; offset >= 0; offset--) {
    LocalDate date = endDate.minusDays(offset);
    DailyAggregate total = dailyTotals.get(date);
    tokenUsage.add(new TokenUsage(date.toString(), total == null ? 0L : total.tokenUsage()));
}
return new DashboardAnalytics(dailyUsage, topModels, tokenUsage);
```

Update the method Javadoc to describe a range-length Token series rather than a fixed seven-day series.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -f backend/pom.xml -Dskip.frontend=true -Dtest=NewApiHttpClientTest#analyticsBuildsThirtyDayTokenSeriesForThirtyDayRange test`

Expected: PASS.

- [ ] **Step 5: Run analytics regression tests and commit**

Run: `mvn -f backend/pom.xml -Dskip.frontend=true -Dtest=NewApiHttpClientTest,DashboardControllerTest test`

Expected: PASS.

```powershell
git add backend/src/main/java/io/ztoken/portal/newapi/NewApiHttpClient.java backend/src/main/java/io/ztoken/portal/console/DashboardAnalytics.java backend/src/test/java/io/ztoken/portal/newapi/NewApiHttpClientTest.java
git commit -m "fix: align dashboard token aggregation with range"
```

### Task 2: Render a range-aware Token trend title in the frontend

**Files:**
- Modify: `frontend/src/features/console/__tests__/dashboard-page.test.tsx`
- Modify: `frontend/src/features/console/DashboardPage.tsx:195-196`
- Modify: `frontend/src/i18n/locales/en.json:478-479`
- Modify: `frontend/src/i18n/locales/zh-CN.json:478-479`

- [ ] **Step 1: Write the failing 30-day Token-title assertion**

In the existing `renders four account metrics with the selected range token total` test, replace the fixed-title assertion with:

```tsx
expect(screen.getByRole('region', { name: 'Last 30 days token usage' })).toBeVisible()
```

Keep the existing analytics mock’s `tokenUsage` total of `1_200_000`, which already verifies that the metric card is derived from the selected analytics response.

- [ ] **Step 2: Run test to verify it fails**

Run: `npm --prefix frontend test -- --run src/features/console/__tests__/dashboard-page.test.tsx`

Expected: FAIL because no region is named `Last 30 days token usage`.

- [ ] **Step 3: Use the active range in the Token title translation**

Replace the static locale values with interpolation keys:

```json
"dashboard.tokensTitle": "Last {{days}} days token usage"
```

```json
"dashboard.tokensTitle": "近 {{days}} 天 Token 消耗"
```

Render the title from the selected range:

```tsx
<DashboardChart
  title={t('dashboard.tokensTitle', { days: range === '7d' ? 7 : 30 })}
  summary={t('dashboard.tokensSummary')}
  accessibleDescription={tokensAccessibleDescription}
  option={tokenOption}
/>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm --prefix frontend test -- --run src/features/console/__tests__/dashboard-page.test.tsx`

Expected: PASS.

- [ ] **Step 5: Build, run focused regressions, and commit**

Run: `npm --prefix frontend run build`

Run: `npm --prefix frontend test -- --run src/features/console/__tests__/dashboard-page.test.tsx src/__tests__/app-shell.test.tsx`

Expected: both commands PASS.

```powershell
git add frontend/src/features/console/DashboardPage.tsx frontend/src/features/console/__tests__/dashboard-page.test.tsx frontend/src/i18n/locales/en.json frontend/src/i18n/locales/zh-CN.json
git commit -m "fix: match dashboard token chart title to range"
```
