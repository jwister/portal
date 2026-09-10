# Portal 模型广场接口透传 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 NewAPI 模型广场的价格和性能接口经 Portal 固定路由原样返回，并让 Portal 模型页读取原始价格响应。

**Architecture:** `NewApiHttpClient` 以固定的上游路径读取字节响应，附加服务端定价令牌并逐项转发查询参数。`ModelCatalogController` 写回上游状态、内容类型和字节体；前端只在视图内从原始定价响应导出既有卡片字段。

**Tech Stack:** Java 17、Spring Boot 3、WebClient、MockWebServer、React 19、TypeScript、Vitest。

---

## 文件结构

- 删除：`backend/src/main/java/io/ztoken/portal/catalog/ModelCatalog.java`、`backend/src/main/java/io/ztoken/portal/catalog/ModelCatalogItem.java`，消除旧的转换后目录协议。
- 创建：`backend/src/main/java/io/ztoken/portal/newapi/NewApiRawResponse.java`，保存上游 HTTP 状态、内容类型与字节体。
- 修改：`backend/src/main/java/io/ztoken/portal/newapi/NewApiClient.java`、`NewApiHttpClient.java` 与 `catalog/ModelCatalogController.java`，实现三个固定的原样代理路由。
- 修改：`backend/src/test/java/io/ztoken/portal/catalog/ModelCatalogControllerTest.java`，验证所有代理契约。
- 修改：`frontend/src/api/portal.ts`、`frontend/src/features/catalog/ModelsPage.tsx` 和对应测试，消费完整 `pricing` 响应。

### Task 1: 后端模型广场原样代理

**Files:**

- Create: `backend/src/main/java/io/ztoken/portal/newapi/NewApiRawResponse.java`
- Modify: `backend/src/main/java/io/ztoken/portal/newapi/NewApiClient.java`
- Modify: `backend/src/main/java/io/ztoken/portal/newapi/NewApiHttpClient.java`
- Modify: `backend/src/main/java/io/ztoken/portal/catalog/ModelCatalogController.java`
- Delete: `backend/src/main/java/io/ztoken/portal/catalog/ModelCatalog.java`
- Delete: `backend/src/main/java/io/ztoken/portal/catalog/ModelCatalogItem.java`
- Test: `backend/src/test/java/io/ztoken/portal/catalog/ModelCatalogControllerTest.java`

- [ ] **Step 1: 写入失败的端到端代理测试**

将当前 DTO 映射测试替换为三个测试，并在动态属性加入 `portal.new-api.pricing-token=test-pricing-token`。

```java
@Test
void pricingForwardsCompletePayloadAndPricingToken() throws Exception {
    String body = "{\"success\":true,\"data\":[{\"model_name\":\"gpt-5-mini\",\"billing_usage_schema\":{\"duration\":\"second\"}}],\"vendors\":[{\"id\":7,\"name\":\"OpenAI\"}],\"group_ratio\":{\"default\":1},\"pricing_version\":\"v42\"}";
    NEW_API.enqueue(new MockResponse().setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).setBody(body));
    ResponseEntity<String> response = http.getForEntity("/api/catalog/pricing", String.class);
    RecordedRequest upstream = NEW_API.takeRequest();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    assertThat(response.getBody()).isEqualTo(body);
    assertThat(upstream.getPath()).isEqualTo("/api/pricing");
    assertThat(upstream.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer test-pricing-token");
}

@Test
void performanceSummaryForwardsHoursAndPayload() throws Exception {
    String body = "{\"success\":true,\"data\":{\"models\":[{\"model_name\":\"gpt-5-mini\",\"success_rate\":99.9}]}}";
    NEW_API.enqueue(new MockResponse().setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).setBody(body));
    ResponseEntity<String> response = http.getForEntity("/api/catalog/perf-metrics/summary?hours=48", String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo(body);
    assertThat(NEW_API.takeRequest().getPath()).isEqualTo("/api/perf-metrics/summary?hours=48");
}

@Test
void performanceMetricsForwardsQueryAndUpstreamError() throws Exception {
    String body = "{\"success\":false,\"message\":\"metrics unavailable\"}";
    NEW_API.enqueue(new MockResponse().setResponseCode(503).setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).setBody(body));
    ResponseEntity<String> response = http.getForEntity("/api/catalog/perf-metrics?model=gpt-5-mini&group=premium&hours=12", String.class);
    RecordedRequest upstream = NEW_API.takeRequest();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody()).isEqualTo(body);
    assertThat(upstream.getRequestUrl().queryParameter("model")).isEqualTo("gpt-5-mini");
    assertThat(upstream.getRequestUrl().queryParameter("group")).isEqualTo("premium");
    assertThat(upstream.getRequestUrl().queryParameter("hours")).isEqualTo("12");
}
```

- [ ] **Step 2: 运行测试并确认正确失败**

Run: `mvn -f backend/pom.xml -Dskip.frontend=true -Dtest=ModelCatalogControllerTest test`

Expected: FAIL；三个 `/api/catalog` 代理路由尚不存在，故返回 404 而非预期响应。

- [ ] **Step 3: 编写最小代理实现**

创建 `NewApiRawResponse.java`：

```java
package io.ztoken.portal.newapi;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;

/** NewAPI 模型广场响应的原始 HTTP 语义，禁止在 Portal 中转换响应体。 */
public record NewApiRawResponse(HttpStatusCode status, MediaType contentType, byte[] body) {
}
```

从 `NewApiClient` 删除 `getModelCatalog()`，导入 `MultiValueMap`，并加入：

```java
NewApiRawResponse getPricing();
NewApiRawResponse getPerformanceSummary(MultiValueMap<String, String> query);
NewApiRawResponse getPerformanceMetrics(MultiValueMap<String, String> query);
```

从 `NewApiHttpClient` 删除旧 DTO 导入和 `getModelCatalog()`，导入 `LinkedMultiValueMap`、`MultiValueMap`，并加入：

```java
@Override public NewApiRawResponse getPricing() { return proxyModelSquare("/api/pricing", new LinkedMultiValueMap<>()); }
@Override public NewApiRawResponse getPerformanceSummary(MultiValueMap<String, String> query) { return proxyModelSquare("/api/perf-metrics/summary", query); }
@Override public NewApiRawResponse getPerformanceMetrics(MultiValueMap<String, String> query) { return proxyModelSquare("/api/perf-metrics", query); }
private NewApiRawResponse proxyModelSquare(String path, MultiValueMap<String, String> query) {
    try {
        return client.get().uri(builder -> builder.path(path).queryParams(query).build()).headers(this::applyPricingHeaders)
                .exchangeToMono(response -> response.bodyToMono(byte[].class).defaultIfEmpty(new byte[0])
                        .map(body -> new NewApiRawResponse(response.statusCode(), response.headers().contentType().orElse(MediaType.APPLICATION_JSON), body)))
                .block(Duration.ofSeconds(10));
    } catch (RuntimeException exception) {
        log.warn("NewAPI 模型广场请求失败: upstream={}, path={}, exceptionType={}", upstreamTarget, path, exception.getClass().getSimpleName());
        throw new NewApiException("NewAPI model square request failed");
    }
}
private void applyPricingHeaders(HttpHeaders headers) { if (hasText(pricingToken)) headers.setBearerAuth(pricingToken); }
```

将控制器替换为 `/pricing`、`/perf-metrics/summary`、`/perf-metrics` 三条 `ResponseEntity<byte[]>` GET 路由。后两条接收 `@RequestParam MultiValueMap<String, String> query`，并统一用以下方法写回：

```java
private ResponseEntity<byte[]> passthrough(NewApiRawResponse upstream) {
    return ResponseEntity.status(upstream.status()).contentType(upstream.contentType()).body(upstream.body());
}
```

删除两个旧 DTO 文件。

- [ ] **Step 4: 重新运行后端测试**

Run: `mvn -f backend/pom.xml -Dskip.frontend=true -Dtest=ModelCatalogControllerTest test`

Expected: PASS；状态码、`Content-Type`、JSON 字节体、查询参数和 Bearer 令牌均符合断言。

- [ ] **Step 5: 提交后端改动**

Run: `git add backend/src/main/java/io/ztoken/portal/catalog/ModelCatalogController.java backend/src/main/java/io/ztoken/portal/newapi/NewApiClient.java backend/src/main/java/io/ztoken/portal/newapi/NewApiHttpClient.java backend/src/main/java/io/ztoken/portal/newapi/NewApiRawResponse.java backend/src/test/java/io/ztoken/portal/catalog/ModelCatalogControllerTest.java; git rm backend/src/main/java/io/ztoken/portal/catalog/ModelCatalog.java backend/src/main/java/io/ztoken/portal/catalog/ModelCatalogItem.java; git commit -m "feat: 透传模型广场接口"`

### Task 2: 前端改用原始价格契约

**Files:**

- Modify: `frontend/src/api/portal.ts`
- Modify: `frontend/src/features/catalog/ModelsPage.tsx`
- Test: `frontend/src/features/catalog/__tests__/models-page.test.tsx`

- [ ] **Step 1: 写入失败的前端契约测试**

把 fetch mock 改为完整 `pricing` 响应，而不是 `{ items: [...] }`：

```tsx
vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
  success: true,
  data: [{ id: 1, model_name: 'gpt-5-mini', vendor_id: 7, enable_groups: ['default', 'premium'], model_ratio: 1, completion_ratio: 2, quota_type: 0 }, { id: 2, model_name: 'glm-5', vendor_id: 8, enable_groups: ['standard'], model_price: .5, completion_ratio: 2, quota_type: 1 }],
  vendors: [{ id: 7, name: 'OpenAI' }, { id: 8, name: 'Zhipu' }], group_ratio: { default: 1, premium: 1.5, standard: 1 },
  usable_group: { default: 'default', premium: 'premium', standard: 'standard' }, supported_endpoint: {}, auto_groups: [], pricing_version: 'v42',
}), { status: 200 })))
```

在首个模型出现后断言 `fetch` 以 `{ credentials: 'include' }` 请求 `/api/catalog/pricing`，并断言 `OpenAI` 可见。

- [ ] **Step 2: 运行测试并确认正确失败**

Run: `npm --prefix frontend test -- src/features/catalog/__tests__/models-page.test.tsx`

Expected: FAIL；现有代码请求 `/api/catalog/models` 且无法从 `data` 读取模型。

- [ ] **Step 3: 编写最小前端实现**

在 `portal.ts` 删除 `ModelCatalogItem` 与 `getModelCatalog()`，并添加以下原始契约和函数：

```ts
export interface NewApiPricingModel { id?: number; model_name: string; vendor_id?: number; vendor_name?: string; enable_groups?: string[]; model_ratio?: number; model_price?: number; completion_ratio?: number; cache_ratio?: number; quota_type?: number }
export interface NewApiVendor { id: number; name: string }
export interface NewApiPricingResponse { success: boolean; data: NewApiPricingModel[]; vendors: NewApiVendor[]; group_ratio: Record<string, number>; usable_group: Record<string, string>; supported_endpoint: Record<string, string[]>; auto_groups: string[]; pricing_version: string }
export function getPricing(): Promise<NewApiPricingResponse> { return requestJson('/api/catalog/pricing') }
export function getPerformanceSummary(query: { hours?: number } = {}): Promise<unknown> { return requestJson(`/api/catalog/perf-metrics/summary${queryString(query)}`) }
export function getPerformanceMetrics(query: { model: string; group?: string; hours?: number }): Promise<unknown> { return requestJson(`/api/catalog/perf-metrics${queryString(query)}`) }
```

在 `ModelsPage.tsx` 用 `getPricing` 保存整个 `NewApiPricingResponse`。新增局部 `CatalogModel` 和 `useMemo`：按 `vendor_id` 在 `pricing.vendors` 找厂商名；直接使用 `enable_groups ?? []`；按 `quota_type === 1 ? model_price : model_ratio`、`completion_ratio ?? 1` 和 `cache_ratio` 生成现有卡片的显示价格。搜索、分组、弹窗和卡片只消费这个局部数组，绝不将转换后的结果写回后端。

- [ ] **Step 4: 重新运行前端测试**

Run: `npm --prefix frontend test -- src/features/catalog/__tests__/models-page.test.tsx`

Expected: PASS；页面请求新路由，显示由上游 `vendors` 映射的厂商，并继续按 `enable_groups` 筛选。

- [ ] **Step 5: 提交前端改动**

Run: `git add frontend/src/api/portal.ts frontend/src/features/catalog/ModelsPage.tsx frontend/src/features/catalog/__tests__/models-page.test.tsx; git commit -m "feat: 模型页使用原始价格数据"`

### Task 3: 全量验证与边界检查

**Files:**

- Modify: `docs/superpowers/specs/2026-09-10-portal-model-square-proxy-design.md`（仅当实现与已批准设计不一致时）

- [ ] **Step 1: 运行后端全量测试**

Run: `mvn -f backend/pom.xml -Dskip.frontend=true test`

Expected: PASS；模型代理、会话、支付和控制台测试全部通过。

- [ ] **Step 2: 运行前端测试和生产构建**

Run: `npm --prefix frontend test && npm --prefix frontend run build`

Expected: PASS；Vitest、TypeScript 与 Vite 均无错误。

- [ ] **Step 3: 复查上游路径白名单和令牌边界**

Run: `rg -n 'proxyModelSquare|/api/catalog/(pricing|perf-metrics)|pricing-token' backend/src/main frontend/src`

Expected: 上游模型广场路径只有 `/api/pricing`、`/api/perf-metrics`、`/api/perf-metrics/summary` 三个固定值；`pricing-token` 只存在后端配置和请求头设置中。

- [ ] **Step 4: 如设计文档确需同步则提交**

Run: `git add docs/superpowers/specs/2026-09-10-portal-model-square-proxy-design.md; git commit -m "docs: 同步模型广场代理实现"`

Expected: 仅在实际更新设计文档时创建提交；否则不创建空提交。
