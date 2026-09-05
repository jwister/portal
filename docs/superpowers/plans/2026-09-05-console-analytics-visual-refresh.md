# 控制台数据分析与视觉升级实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (\`- [ ]\`) syntax for tracking.

**Goal:** 使用安全的 portal BFF 聚合 New API 个人统计数据，并将控制台各页面升级为统一、图标丰富、响应式的数据工作台。

**Architecture:** \`NewApiHttpClient\` 从 \`/api/data/self\` 取得按小时的个人统计数据，portal 服务端生成前端专用 \`DashboardAnalytics\`，浏览器不接触 New API 令牌。前端用按需注册的 ECharts 和共享信息卡、标题、工具栏组件实现一致的视觉系统。

**Tech Stack:** Spring Boot、Jackson、React 19、TypeScript、Semi UI、Semi Icons、ECharts、Vitest、JUnit 5、MockWebServer。

---

## 文件结构

- Create: \`backend/src/main/java/io/ztoken/portal/console/DashboardAnalytics.java\` — 图表数据 DTO。
- Modify: \`NewApiClient.java\`、\`NewApiHttpClient.java\`、\`DashboardController.java\` — BFF、上游查询、聚合。
- Create: \`frontend/src/components/DashboardChart.tsx\` — 可释放、可缩放的 ECharts 容器。
- Modify: \`frontend/src/api/portal.ts\`、\`package.json\`、\`package-lock.json\` — analytics 请求和依赖。
- Modify: \`MetricCard.tsx\`、\`ConsolePageHeader.tsx\`、\`RemoteState.tsx\`、五个控制台页面、双语 locale、\`styles.css\`。
- Test: 对应 Dashboard controller/client、Dashboard、共享组件、Tokens、Logs、Profile、Orders 测试文件。

### Task 1: 受控 analytics BFF 契约

**Files:**
- Create: \`backend/src/main/java/io/ztoken/portal/console/DashboardAnalytics.java\`
- Modify: \`backend/src/main/java/io/ztoken/portal/newapi/NewApiClient.java\`
- Modify: \`backend/src/main/java/io/ztoken/portal/console/DashboardController.java\`
- Test: \`backend/src/test/java/io/ztoken/portal/console/DashboardControllerTest.java\`

- [ ] **Step 1: 写失败测试，覆盖默认范围与非法范围**

~~~
@Test
void analyticsUsesDefaultThirtyDayRangeAndRejectsUnsupportedRange() throws Exception {
    ResponseEntity<DashboardAnalytics> valid = http.exchange("/api/console/dashboard/analytics", HttpMethod.GET,
            new HttpEntity<>(headers), DashboardAnalytics.class);
    ResponseEntity<String> invalid = http.exchange("/api/console/dashboard/analytics?range=90d", HttpMethod.GET,
            new HttpEntity<>(headers), String.class);
    assertThat(valid.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
}
~~~

- [ ] **Step 2: 验证 RED**

Run: \`mvn test -Dtest=DashboardControllerTest#analyticsUsesDefaultThirtyDayRangeAndRejectsUnsupportedRange\`

Expected: FAIL，缺少 DTO 或 endpoint。

- [ ] **Step 3: 定义无凭据数据结构和客户端方法**

~~~
public record DashboardAnalytics(List<DailyUsage> dailyUsage, List<ModelUsage> topModels,
                                 List<TokenUsage> tokenUsage) {
    public record DailyUsage(String date, long quota, long requestCount) {}
    public record ModelUsage(String modelName, long quota) {}
    public record TokenUsage(String date, long tokenUsage) {}
}
// NewApiClient
DashboardAnalytics getDashboardAnalytics(PortalPrincipal principal, int rangeDays);
~~~

- [ ] **Step 4: 添加严格范围映射**

~~~
@GetMapping("/dashboard/analytics")
public DashboardAnalytics analytics(@CookieValue(value = "PORTAL_SESSION", required = false) String sessionId,
                                    @RequestParam(defaultValue = "30d") String range) {
    int rangeDays = switch (range) {
        case "7d" -> 7;
        case "30d" -> 30;
        default -> throw new IllegalArgumentException("不支持的统计时间范围");
    };
    return newApiClient.getDashboardAnalytics(sessions.require(sessionId), rangeDays);
}
~~~

- [ ] **Step 5: 验证 GREEN**

Run: \`mvn test -Dtest=DashboardControllerTest\`

Expected: PASS；上游请求只携带当前会话的 Bearer 与 \`New-Api-User\`。

### Task 2: 聚合 New API 原始统计

**Files:**
- Modify: \`backend/src/main/java/io/ztoken/portal/newapi/NewApiHttpClient.java\`
- Test: \`backend/src/test/java/io/ztoken/portal/newapi/NewApiHttpClientTest.java\`

- [ ] **Step 1: 写聚合失败测试**

~~~
@Test
void analyticsAggregatesDailyUsageTopModelsAndSevenDayTokenSeries() throws Exception {
    NEW_API.enqueue(json("""
      {"success":true,"data":[
        {"created_at":1710000000,"model_name":"gpt-4o","quota":40,"count":2,"token_used":10},
        {"created_at":1710003600,"model_name":"gpt-4o","quota":20,"count":1,"token_used":5},
        {"created_at":1710003600,"model_name":"claude","quota":30,"count":3,"token_used":8}
      ]}
      """));
    DashboardAnalytics result = client.getDashboardAnalytics(principal(), 7);
    assertThat(result.dailyUsage()).containsExactly(new DailyUsage("2024-03-09", 90, 6));
    assertThat(result.topModels()).containsExactly(new ModelUsage("gpt-4o", 60), new ModelUsage("claude", 30));
    assertThat(result.tokenUsage()).containsExactly(new TokenUsage("2024-03-09", 23));
}
~~~

- [ ] **Step 2: 验证 RED**

Run: \`mvn test -Dtest=NewApiHttpClientTest#analyticsAggregatesDailyUsageTopModelsAndSevenDayTokenSeries\`

Expected: FAIL，\`getDashboardAnalytics\` 尚不存在。

- [ ] **Step 3: 实现请求和聚合**

~~~
public DashboardAnalytics getDashboardAnalytics(PortalPrincipal principal, int rangeDays) {
    long end = currentTimestamp();
    long start = end - Duration.ofDays(rangeDays).toSeconds();
    JsonNode rows = getData("/api/data/self?start_timestamp=" + start + "&end_timestamp=" + end, principal);
    Map<LocalDate, DailyAccumulator> days = new TreeMap<>();
    Map<String, Long> modelQuota = new HashMap<>();
    for (JsonNode row : rows) {
        LocalDate day = Instant.ofEpochSecond(row.path("created_at").asLong())
                .atZone(ZoneId.systemDefault()).toLocalDate();
        days.computeIfAbsent(day, ignored -> new DailyAccumulator())
                .add(row.path("quota").asLong(), row.path("count").asLong(), row.path("token_used").asLong());
        modelQuota.merge(row.path("model_name").asText("未知模型"), row.path("quota").asLong(), Long::sum);
    }
    return DashboardAnalytics.from(days, modelQuota, rangeDays);
}
~~~

\`DashboardAnalytics.from\` 负责填齐最近 7 天零值 Token 点、把第 6 名及以后模型合并为“其他”、保证日期升序和模型额度降序。原始字段、口径和聚合边界加中文业务注释。

- [ ] **Step 4: 补充空数组、缺失字段、上游业务失败测试并验证**

Run: \`mvn test -Dtest=NewApiHttpClientTest\`

Expected: PASS；不得伪造统计数据。

- [ ] **Step 5: 提交后端小步改动**

~~~
git add backend/src/main/java/io/ztoken/portal/console/DashboardAnalytics.java backend/src/main/java/io/ztoken/portal/console/DashboardController.java backend/src/main/java/io/ztoken/portal/newapi/NewApiClient.java backend/src/main/java/io/ztoken/portal/newapi/NewApiHttpClient.java backend/src/test/java/io/ztoken/portal/console/DashboardControllerTest.java backend/src/test/java/io/ztoken/portal/newapi/NewApiHttpClientTest.java
git commit -m "feat: 新增控制台用量统计接口"
~~~

### Task 3: ECharts 与仪表盘

**Files:**
- Create: \`frontend/src/components/DashboardChart.tsx\`
- Modify: \`frontend/package.json\`, \`frontend/package-lock.json\`, \`frontend/src/api/portal.ts\`, \`frontend/src/features/console/DashboardPage.tsx\`
- Test: \`frontend/src/features/console/__tests__/dashboard-page.test.tsx\`

- [ ] **Step 1: 写仪表盘失败测试**

~~~
expect(fetch).toHaveBeenCalledWith('/api/console/dashboard/analytics?range=30d', expect.anything())
expect(await screen.findByRole('button', { name: '7 天' })).toBeVisible()
expect(screen.getByRole('region', { name: '30 天消耗趋势' })).toBeVisible()
expect(screen.getByRole('region', { name: '模型消耗 Top 5' })).toBeVisible()
expect(screen.getByRole('region', { name: '近 7 天 Token 消耗' })).toBeVisible()
~~~

- [ ] **Step 2: 验证 RED**

Run: \`npm test -- src/features/console/__tests__/dashboard-page.test.tsx\`

Expected: FAIL，缺少范围按钮或图表区域。

- [ ] **Step 3: 安装依赖并定义请求**

~~~
npm install echarts
~~~

~~~
export interface DashboardAnalytics {
  dailyUsage: Array<{ date: string; quota: number; requestCount: number }>
  topModels: Array<{ modelName: string; quota: number }>
  tokenUsage: Array<{ date: string; tokenUsage: number }>
}
export function getDashboardAnalytics(range: '7d' | '30d'): Promise<DashboardAnalytics> {
  return requestJson('/api/console/dashboard/analytics' + queryString({ range }))
}
~~~

- [ ] **Step 4: 创建图表容器**

~~~
export function DashboardChart({ title, option, summary }: DashboardChartProps) {
  const elementRef = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (!elementRef.current || !option) return
    const chart = echarts.init(elementRef.current)
    chart.setOption({ ...option, animation: !window.matchMedia('(prefers-reduced-motion: reduce)').matches })
    const observer = new ResizeObserver(() => chart.resize())
    observer.observe(elementRef.current)
    return () => { observer.disconnect(); chart.dispose() }
  }, [option])
  return <section className="dashboard-chart" aria-label={title} role="region"><header><h3>{title}</h3><span>{summary}</span></header><div ref={elementRef} className="dashboard-chart-canvas" /></section>
}
~~~

按需注册 \`BarChart\`、\`LineChart\`、\`PieChart\`、Grid、Tooltip、Legend、Dataset 和 Canvas renderer。

- [ ] **Step 5: 并行加载并映射图表**

~~~
const [range, setRange] = useState<'7d' | '30d'>('30d')
useEffect(() => {
  void Promise.all([getDashboard(), getDashboardAnalytics(range)])
    .then(([summary, analytics]) => setData({ summary, analytics }))
}, [range, revision])
~~~

\`dailyUsage\` 映射为双 y 轴 \`bar\` + \`line\`；\`topModels\` 映射 donut \`pie\`；\`tokenUsage\` 映射含 \`areaStyle\` 的 \`line\`。数组为空时用 \`RemoteState kind="empty"\`，不初始化图表。为创建令牌、充值、日志提供带图标快捷入口。

- [ ] **Step 6: 验证仪表盘和构建**

Run: \`npm test -- src/features/console/__tests__/dashboard-page.test.tsx && npm run build\`

Expected: PASS.

### Task 4: 共享图标化视觉组件

**Files:**
- Modify: \`frontend/src/components/MetricCard.tsx\`, \`ConsolePageHeader.tsx\`, \`RemoteState.tsx\`, \`frontend/src/styles.css\`
- Test: \`frontend/src/components/__tests__/console-shared.test.tsx\`

- [ ] **Step 1: 写 MetricCard 图标契约失败测试**

~~~
render(<MetricCard icon={<IconWallet />} label="可用额度" value="900" />)
expect(screen.getByText('可用额度')).toBeVisible()
expect(screen.getByTestId('metric-card-icon')).toBeVisible()
~~~

- [ ] **Step 2: 验证 RED**

Run: \`npm test -- src/components/__tests__/console-shared.test.tsx\`

Expected: FAIL，现有 props 没有 \`icon\`。

- [ ] **Step 3: 实现可复用组件与集中样式**

~~~
interface MetricCardProps { label: string; value: string | number; hint?: string; icon?: ReactNode; tone?: 'blue' | 'mint' | 'amber' | 'rose' }
export function MetricCard({ label, value, hint, icon, tone = 'blue' }: MetricCardProps) {
  return <Card className={'metric-card metric-card--' + tone}><div className="metric-card-top"><Typography.Text type="tertiary">{label}</Typography.Text>{icon && <span data-testid="metric-card-icon" aria-hidden="true">{icon}</span>}</div><Typography.Title heading={3}>{value}</Typography.Title>{hint && <Typography.Text type="tertiary">{hint}</Typography.Text>}</Card>
}
~~~

为 \`.console-page-header\`、\`.metric-card\`、\`.dashboard-chart\`、\`.console-summary-grid\`、\`.console-identity-card\`、\`.console-table-toolbar\` 建立统一响应式规则，禁止逐页复制 CSS。

- [ ] **Step 4: 验证 GREEN**

Run: \`npm test -- src/components/__tests__/console-shared.test.tsx\`

Expected: PASS.

### Task 5: 令牌、日志、资料和订单页面升级

**Files:**
- Modify: \`TokensPage.tsx\`, \`LogsPage.tsx\`, \`ProfilePage.tsx\`, \`OrdersPage.tsx\`，双语 locale 和 \`styles.css\`
- Test: 对应现有 tokens、logs、profile、orders Vitest 文件

- [ ] **Step 1: 为新增核心可见结构写失败测试**

~~~
expect(await screen.findByText('活跃令牌')).toBeVisible()
expect(screen.getByRole('region', { name: '日志筛选' })).toBeVisible()
expect(screen.getByText('账户身份')).toBeVisible()
expect(screen.getByAltText('PayPal')).toBeVisible()
~~~

- [ ] **Step 2: 验证 RED**

Run: \`npm test -- src/features/console/__tests__/tokens-page.test.tsx src/features/console/__tests__/logs-page.test.tsx src/features/console/__tests__/profile-page.test.tsx src/features/orders/__tests__/orders-page.test.tsx\`

Expected: FAIL，新增结构尚不存在。

- [ ] **Step 3: 复用共享组件实现页面细节**

- Tokens：从 \`tokens.items\` 计算总数、活跃数、非无限令牌额度；查看、编辑、开关、删除改为有可访问名称的图标按钮和 Tooltip。
- Logs：筛选容器增加 \`aria-label={t('logs.filters')}\`，输入项使用 Semi 图标；额度、RPM、TPM 用带图标指标卡。
- Profile：用账户首字符、用户名、邮箱生成身份卡；偏好设置仍只编辑 display name 和语言。
- Orders：从 \`items\` 计算总数、已到账、待处理；\`PAYPAL\` 使用 \`<img src="/Paypal.png" alt="PayPal" />\`，状态映射增加语义图标。
- 所有新增文案进入中英文 locale，新增业务计算、上游字段映射、状态归类写中文注释。

- [ ] **Step 4: 验证页面测试**

Run: \`npm test -- src/features/console/__tests__/tokens-page.test.tsx src/features/console/__tests__/logs-page.test.tsx src/features/console/__tests__/profile-page.test.tsx src/features/orders/__tests__/orders-page.test.tsx\`

Expected: PASS.

### Task 6: 全量验证、响应式检查与提交

- [ ] **Step 1: 运行后端全量测试**

Run: \`mvn test\`

Expected: PASS.

- [ ] **Step 2: 运行前端全量测试和生产构建**

Run: \`npm test && npm run build\`

Expected: PASS；Vite chunk-size 建议仅作非阻断记录。

- [ ] **Step 3: 检查桌面与移动布局**

Run: \`npm run dev -- --host 127.0.0.1\`

Use browser checks for \`/console/dashboard\`, \`/console/tokens\`, \`/console/logs\`, \`/console/profile\`, \`/console/orders\` at 1440×900 and 390×844. Verify no page horizontal overflow, chart containers resize, tables alone scroll horizontally, and icon-only buttons retain accessible names.

- [ ] **Step 4: 仅提交本功能文件**

~~~
git add backend/src/main/java/io/ztoken/portal/console backend/src/main/java/io/ztoken/portal/newapi/NewApiClient.java backend/src/main/java/io/ztoken/portal/newapi/NewApiHttpClient.java backend/src/test/java/io/ztoken/portal/console backend/src/test/java/io/ztoken/portal/newapi/NewApiHttpClientTest.java frontend/package.json frontend/package-lock.json frontend/src/api/portal.ts frontend/src/components frontend/src/features/console frontend/src/features/orders frontend/src/i18n/locales frontend/src/styles.css
git commit -m "feat: 升级控制台数据分析与视觉体验"
~~~
