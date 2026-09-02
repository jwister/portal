# PayPal Sandbox 支付 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 Ztoken Portal 实现 PayPal Sandbox 充值闭环：美分金额订单、服务端 Create/Capture、签名验证 Webhook、幂等 NewAPI quota 入账、客户订单查询，以及购买/充值页面的 PayPal JS SDK Checkout。

**Architecture:** Portal 将支付订单、PayPal 交易、已验证 Webhook 事件和入账尝试保存到自己的 MySQL 数据库；订单用户 ID 始终来自 Portal Session。PayPal Client Secret、Webhook ID 和 NewAPI 管理员 Access Token 仅存在于后端环境变量。浏览器只得到公开 PayPal Client ID，并且不提供可被信任的金额、NewAPI 用户 ID、PayPal provider ID 或 capture ID。

**Tech Stack:** Java 17, Spring Boot 3.3, Spring Data JPA, Flyway, WebClient, MockWebServer, MySQL/H2 test mode, React 19, TypeScript, Semi Design, Vitest, PayPal JavaScript SDK.

**Non-negotiable constraints:**
- Do not modify NewAPI source code or access its database.
- Do not read, modify, stage, reset, or commit `backend/src/main/resources/application.yml`.
- All dollar values are stored as integer USD cents; all quota values are integer `long` values.
- Use fixed conversion `quota = amountUsdMinor × 5_000`, which represents `$1 = 500,000 quota`.
- On an unknown NewAPI credit result, mark `CREDIT_UNKNOWN` and never retry automatically.
- Plan C includes PayPal only. TRC20, admin review UI, OAuth, and PayPal Live credentials are excluded.

---

## File Structure and Boundaries

### Configuration and persistence

- Create: `backend/src/main/java/io/ztoken/portal/payment/config/PaymentProperties.java` — environment-bound PayPal, NewAPI credit, amount and expiry configuration.
- Modify: `backend/src/main/java/io/ztoken/portal/PortalApplication.java` — register `PaymentProperties` with configuration properties binding.
- Create: `backend/src/main/resources/db/migration/V2__create_payment_schema.sql` — Portal-only payment tables and constraints.
- Create: `backend/src/main/java/io/ztoken/portal/payment/domain/PaymentMethod.java` — `PAYPAL` enum.
- Create: `backend/src/main/java/io/ztoken/portal/payment/domain/PaymentOrderStatus.java` — order/credit state enum.
- Create: `backend/src/main/java/io/ztoken/portal/payment/domain/PaymentOrder.java` — order state transition methods.
- Create: `backend/src/main/java/io/ztoken/portal/payment/domain/PaymentTransaction.java` — PayPal provider order/capture state.
- Create: `backend/src/main/java/io/ztoken/portal/payment/domain/PaymentProviderEvent.java` — verified Webhook deduplication record.
- Create: `backend/src/main/java/io/ztoken/portal/payment/domain/CreditAttempt.java` — credit audit record.
- Create: repositories in `backend/src/main/java/io/ztoken/portal/payment/repository/` — locking and lookup contracts only.

### Payment services

- Create: `backend/src/main/java/io/ztoken/portal/payment/order/PaymentOrderService.java` — validates money, creates customer orders, exposes ownership-checked reads.
- Create: `backend/src/main/java/io/ztoken/portal/payment/credit/NewApiCreditClient.java` — narrow admin quota-credit boundary.
- Create: `backend/src/main/java/io/ztoken/portal/payment/credit/HttpNewApiCreditClient.java` — uses `POST /api/user/manage` and classifies success/failed/unknown.
- Create: `backend/src/main/java/io/ztoken/portal/payment/credit/CreditResult.java` — `SUCCESS`, `FAILED`, `UNKNOWN`.
- Create: `backend/src/main/java/io/ztoken/portal/payment/credit/PaymentCreditService.java` — locked `CONFIRMED → CREDITING` settlement.
- Create: `backend/src/main/java/io/ztoken/portal/payment/paypal/PayPalClient.java` — provider interface.
- Create: `backend/src/main/java/io/ztoken/portal/payment/paypal/HttpPayPalClient.java` — OAuth, Create, Capture, official webhook verification API.
- Create: `backend/src/main/java/io/ztoken/portal/payment/paypal/PayPalOrderDetails.java` and `PayPalCaptureDetails.java` — provider DTOs.
- Create: `backend/src/main/java/io/ztoken/portal/payment/paypal/PayPalPaymentService.java` — provider order idempotency and verified capture.
- Create: `backend/src/main/java/io/ztoken/portal/payment/paypal/PayPalWebhookService.java` — signed event processing and event-ID deduplication.

### HTTP API and frontend

- Create: `backend/src/main/java/io/ztoken/portal/payment/api/PaymentOrderController.java` — authenticated customer order API.
- Create: `backend/src/main/java/io/ztoken/portal/payment/api/PayPalPaymentController.java` — authenticated order-specific SDK/Create/Capture API.
- Create: `backend/src/main/java/io/ztoken/portal/payment/api/PayPalWebhookController.java` — unauthenticated raw-body Webhook endpoint.
- Create: request/response DTOs in `backend/src/main/java/io/ztoken/portal/payment/api/`.
- Modify: `frontend/src/api/portal.ts` — typed order and PayPal API functions.
- Create: `frontend/src/features/payments/paypal-sdk.ts` — safe, idempotent PayPal SDK loader.
- Create: `frontend/src/features/payments/PayPalCheckout.tsx` — orders-only SDK UI and status polling.
- Modify: `frontend/src/features/payments/PaymentSelectionPanel.tsx` — enable PayPal after a local amount is selected; leave other methods disabled.
- Modify: `frontend/src/features/payments/PurchasePage.tsx` and `RechargePage.tsx` — create order then render Checkout/status.
- Modify: `frontend/src/features/orders/OrdersPage.tsx` — replace empty placeholder with customer-only order list.
- Modify: `frontend/src/i18n/locales/en.json`, `frontend/src/i18n/locales/zh-CN.json`, `frontend/src/styles.css` — all user-visible payment/order text and responsive styles.

---

### Task 1: Bind payment configuration without local secrets

**Files:**
- Create: `backend/src/main/java/io/ztoken/portal/payment/config/PaymentProperties.java`
- Modify: `backend/src/main/java/io/ztoken/portal/PortalApplication.java`
- Test: `backend/src/test/java/io/ztoken/portal/payment/config/PaymentPropertiesTest.java`

- [ ] **Step 1: Write failing configuration tests**

Test defaults and configuration safety without loading real environment values:

```java
@Test
void defaultsToSandboxAndThirtyMinuteExpiry() {
    PaymentProperties properties = new PaymentProperties();

    assertThat(properties.getPaypal().getMode()).isEqualTo("sandbox");
    assertThat(properties.getOrderExpiryMinutes()).isEqualTo(30);
    assertThat(properties.getQuotaPerUsd()).isEqualTo(500_000L);
}

@Test
void paypalRequiresAllServerSideCredentials() {
    PaymentProperties properties = new PaymentProperties();
    properties.getPaypal().setClientId("client");

    assertThat(properties.getPaypal().isConfigured()).isFalse();
}
```

- [ ] **Step 2: Run the test to verify failure**

```powershell
cd F:\WorkSpace\study\AIProject\Ztoken\portal
mvn -f backend/pom.xml -Dskip.frontend=true -Dtest=PaymentPropertiesTest test
```

Expected: compilation failure because `PaymentProperties` does not exist.

- [ ] **Step 3: Implement the minimal configuration class**

Bind `payment.*` variables in code only; do not add values to `application.yml`.

```java
@ConfigurationProperties(prefix = "payment")
public class PaymentProperties {
    private int orderExpiryMinutes = 30;
    private long quotaPerUsd = 500_000L;
    private final Paypal paypal = new Paypal();
    private final NewApiCredit newapiCredit = new NewApiCredit();

    public static class Paypal {
        private String mode = "sandbox";
        private String clientId;
        private String clientSecret;
        private String webhookId;
        // getters/setters
        public boolean isConfigured() {
            return hasText(clientId) && hasText(clientSecret) && hasText(webhookId);
        }
    }

    public static class NewApiCredit {
        private String accessToken;
        // getters/setters
        public boolean isConfigured() { return hasText(accessToken); }
    }
}
```

Enable it with:

```java
@SpringBootApplication
@EnableConfigurationProperties({PortalProperties.class, PaymentProperties.class})
public class PortalApplication { }
```

Expose expected deployment names in README later, not in the local YAML file:

```text
PAYMENT_PAYPAL_MODE
PAYMENT_PAYPAL_CLIENT_ID
PAYMENT_PAYPAL_CLIENT_SECRET
PAYMENT_PAYPAL_WEBHOOK_ID
PAYMENT_NEWAPI_CREDIT_ACCESS_TOKEN
PAYMENT_ORDER_EXPIRY_MINUTES
```

- [ ] **Step 4: Run the test to verify it passes**

```powershell
mvn -f backend/pom.xml -Dskip.frontend=true -Dtest=PaymentPropertiesTest test
```

Expected: PASS.

- [ ] **Step 5: Commit only payment configuration files**

```powershell
git add backend/src/main/java/io/ztoken/portal/payment/config/PaymentProperties.java backend/src/main/java/io/ztoken/portal/PortalApplication.java backend/src/test/java/io/ztoken/portal/payment/config/PaymentPropertiesTest.java
git commit -m "feat: 添加 Portal 支付配置边界"
```

---

### Task 2: Create Portal payment schema and state domain

**Files:**
- Create: `backend/src/main/resources/db/migration/V2__create_payment_schema.sql`
- Create: `backend/src/main/java/io/ztoken/portal/payment/domain/PaymentMethod.java`
- Create: `backend/src/main/java/io/ztoken/portal/payment/domain/PaymentOrderStatus.java`
- Create: `backend/src/main/java/io/ztoken/portal/payment/domain/PaymentOrder.java`
- Create: `backend/src/main/java/io/ztoken/portal/payment/domain/PaymentTransaction.java`
- Create: `backend/src/main/java/io/ztoken/portal/payment/domain/PaymentProviderEvent.java`
- Create: `backend/src/main/java/io/ztoken/portal/payment/domain/CreditAttempt.java`
- Create: repositories under `backend/src/main/java/io/ztoken/portal/payment/repository/`
- Test: `backend/src/test/java/io/ztoken/portal/payment/domain/PaymentOrderTest.java`
- Test: `backend/src/test/java/io/ztoken/portal/payment/repository/PaymentOrderRepositoryTest.java`

- [ ] **Step 1: Write state transition tests**

```java
@Test
void confirmedOrderCanBeClaimedForExactlyOneCreditAttempt() {
    PaymentOrder order = PaymentOrder.paypal("PO-1", 7L, 2_550L, 12_750_000L, now, expiresAt);

    order.confirm(now);
    assertThat(order.startCrediting(now.plusSeconds(1))).isTrue();
    assertThat(order.startCrediting(now.plusSeconds(2))).isFalse();
    assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.CREDITING);
}

@Test
void expiredWaitingOrderCannotBeConfirmed() {
    PaymentOrder order = PaymentOrder.paypal("PO-2", 7L, 500L, 2_500_000L, now.minusMinutes(31), now.minusMinutes(1));

    assertThat(order.expireIfPast(now)).isTrue();
    assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.EXPIRED);
}
```

- [ ] **Step 2: Run tests to verify failure**

```powershell
mvn -f backend/pom.xml -Dskip.frontend=true -Dtest=PaymentOrderTest,PaymentOrderRepositoryTest test
```

Expected: compilation failure because payment entities and repositories do not exist.

- [ ] **Step 3: Add Flyway V2 migration**

Create tables with portable SQL types used by H2 MySQL mode and MySQL:

```sql
CREATE TABLE payment_orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_no VARCHAR(48) NOT NULL,
    newapi_user_id BIGINT NOT NULL,
    payment_method VARCHAR(32) NOT NULL,
    amount_usd_minor BIGINT NOT NULL,
    quota_to_credit BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    confirmed_at TIMESTAMP NULL,
    credited_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_orders_order_no UNIQUE (order_no)
);

CREATE INDEX idx_payment_orders_user_created ON payment_orders (newapi_user_id, created_at);
CREATE INDEX idx_payment_orders_status_expiry ON payment_orders (status, expires_at);

CREATE TABLE payment_transactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    payment_order_id BIGINT NOT NULL,
    provider VARCHAR(32) NOT NULL,
    provider_order_id VARCHAR(128) NOT NULL,
    provider_capture_id VARCHAR(128) NULL,
    provider_status VARCHAR(64) NULL,
    idempotency_key VARCHAR(96) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_transactions_provider_order UNIQUE (provider, provider_order_id),
    CONSTRAINT uk_payment_transactions_provider_capture UNIQUE (provider, provider_capture_id),
    CONSTRAINT uk_payment_transactions_order_provider UNIQUE (payment_order_id, provider)
);

CREATE TABLE payment_provider_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    provider VARCHAR(32) NOT NULL,
    provider_event_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payment_order_id BIGINT NULL,
    verified_at TIMESTAMP NOT NULL,
    audit_summary VARCHAR(512) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_provider_events_provider_event UNIQUE (provider, provider_event_id)
);

CREATE TABLE credit_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    payment_order_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    message VARCHAR(512) NULL,
    created_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_credit_attempts_order_created ON credit_attempts (payment_order_id, created_at);
```

Use JPA state methods, not public setters, for status transitions. Repository lock query must use JPA pessimistic write locking:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select order from PaymentOrder order where order.orderNo = :orderNo")
Optional<PaymentOrder> findByOrderNoForUpdate(String orderNo);
```

- [ ] **Step 4: Run tests and Flyway startup twice**

```powershell
mvn -f backend/pom.xml -Dskip.frontend=true -Dtest=PaymentOrderTest,PaymentOrderRepositoryTest test
mvn -f backend/pom.xml -Dskip.frontend=true -Dtest=PortalApplicationTest test
```

Expected: PASS. The second startup must see V2 as already applied.

- [ ] **Step 5: Commit persistence layer**

```powershell
git add backend/src/main/resources/db/migration/V2__create_payment_schema.sql backend/src/main/java/io/ztoken/portal/payment/domain backend/src/main/java/io/ztoken/portal/payment/repository backend/src/test/java/io/ztoken/portal/payment/domain backend/src/test/java/io/ztoken/portal/payment/repository
git commit -m "feat: 添加 Portal 支付订单状态模型"
```

---

### Task 3: Create trusted customer orders and NewAPI credit boundary

**Files:**
- Create: `backend/src/main/java/io/ztoken/portal/payment/order/PaymentOrderService.java`
- Create: `backend/src/main/java/io/ztoken/portal/payment/order/PaymentOrderView.java`
- Create: `backend/src/main/java/io/ztoken/portal/payment/credit/CreditResult.java`
- Create: `backend/src/main/java/io/ztoken/portal/payment/credit/NewApiCreditClient.java`
- Create: `backend/src/main/java/io/ztoken/portal/payment/credit/HttpNewApiCreditClient.java`
- Create: `backend/src/main/java/io/ztoken/portal/payment/credit/PaymentCreditService.java`
- Test: `backend/src/test/java/io/ztoken/portal/payment/order/PaymentOrderServiceTest.java`
- Test: `backend/src/test/java/io/ztoken/portal/payment/credit/HttpNewApiCreditClientTest.java`
- Test: `backend/src/test/java/io/ztoken/portal/payment/credit/PaymentCreditServiceTest.java`

- [ ] **Step 1: Write failing money and credit tests**

```java
@Test
void convertsCentsToQuotaWithoutFloatingPoint() {
    PaymentOrderView order = service.createForUser(principal(7L), new BigDecimal("25.50"));

    assertThat(order.amountUsdMinor()).isEqualTo(2_550L);
    assertThat(order.quotaToCredit()).isEqualTo(12_750_000L);
}

@Test
void rejectsAmountWithMoreThanTwoDecimalPlaces() {
    assertThatThrownBy(() -> service.createForUser(principal(7L), new BigDecimal("1.001")))
        .isInstanceOf(IllegalArgumentException.class);
}

@Test
void unknownCreditResultIsNeverRetriedAutomatically() {
    order.confirm(now);
    when(newApiCredit.addQuota(7L, 2_500_000L)).thenReturn(CreditResult.UNKNOWN);

    creditService.creditConfirmedOrder(order.getOrderNo());
    creditService.creditConfirmedOrder(order.getOrderNo());

    verify(newApiCredit, times(1)).addQuota(7L, 2_500_000L);
    assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.CREDIT_UNKNOWN);
}
```

- [ ] **Step 2: Run tests to verify failure**

```powershell
mvn -f backend/pom.xml -Dskip.frontend=true -Dtest=PaymentOrderServiceTest,HttpNewApiCreditClientTest,PaymentCreditServiceTest test
```

Expected: compilation failure because the order and credit services do not exist.

- [ ] **Step 3: Implement strict amount conversion and ownership**

`PaymentOrderService.createForUser` accepts only the authenticated `PortalPrincipal`; it must reject values below `100` cents, above `1_000_000` cents, negative values, non-two-decimal values, and arithmetic overflow.

```java
private static final long MIN_USD_MINOR = 100L;
private static final long MAX_USD_MINOR = 1_000_000L;
private static final long QUOTA_PER_USD_MINOR = 5_000L;

long amountUsdMinor = amount.setScale(2, RoundingMode.UNNECESSARY)
        .movePointRight(2).longValueExact();
if (amountUsdMinor < MIN_USD_MINOR || amountUsdMinor > MAX_USD_MINOR) {
    throw new IllegalArgumentException("Payment amount must be between $1.00 and $10,000.00");
}
long quotaToCredit = Math.multiplyExact(amountUsdMinor, QUOTA_PER_USD_MINOR);
```

Generate an opaque `orderNo` with a non-sequential prefix and 32 random URL-safe characters. Customer read/list methods always include `where newapiUserId = principal.userId()`.

`HttpNewApiCreditClient` must send only from server-side properties:

```http
POST /api/user/manage
Authorization: Bearer ${PAYMENT_NEWAPI_CREDIT_ACCESS_TOKEN}
Content-Type: application/json

{"id":7,"action":"add_quota","mode":"add","value":2500000}
```

Classify results exactly:

```text
HTTP 2xx + success:true → SUCCESS
HTTP 2xx + success:false or HTTP 4xx → FAILED
Timeout, connection failure, malformed response, HTTP 5xx → UNKNOWN
```

`PaymentCreditService` locks the order, claims only `CONFIRMED`, writes `CREDITING` before calling NewAPI, then writes a `CreditAttempt` and transitions to `PAID`, `CREDIT_FAILED`, or `CREDIT_UNKNOWN`. It must not invoke NewAPI for any other state.

- [ ] **Step 4: Run service tests**

```powershell
mvn -f backend/pom.xml -Dskip.frontend=true -Dtest=PaymentOrderServiceTest,HttpNewApiCreditClientTest,PaymentCreditServiceTest test
```

Expected: PASS.

- [ ] **Step 5: Commit order and credit services**

```powershell
git add backend/src/main/java/io/ztoken/portal/payment/order backend/src/main/java/io/ztoken/portal/payment/credit backend/src/test/java/io/ztoken/portal/payment/order backend/src/test/java/io/ztoken/portal/payment/credit
git commit -m "feat: 添加 Portal 订单与 NewAPI 入账服务"
```

---

### Task 4: Implement PayPal OAuth, Create, Capture, and Webhook verification

**Files:**
- Create: `backend/src/main/java/io/ztoken/portal/payment/paypal/PayPalClient.java`
- Create: `backend/src/main/java/io/ztoken/portal/payment/paypal/HttpPayPalClient.java`
- Create: `backend/src/main/java/io/ztoken/portal/payment/paypal/PayPalOrderDetails.java`
- Create: `backend/src/main/java/io/ztoken/portal/payment/paypal/PayPalCaptureDetails.java`
- Create: `backend/src/main/java/io/ztoken/portal/payment/paypal/PayPalPaymentService.java`
- Create: `backend/src/main/java/io/ztoken/portal/payment/paypal/PayPalWebhookService.java`
- Test: `backend/src/test/java/io/ztoken/portal/payment/paypal/HttpPayPalClientTest.java`
- Test: `backend/src/test/java/io/ztoken/portal/payment/paypal/PayPalPaymentServiceTest.java`
- Test: `backend/src/test/java/io/ztoken/portal/payment/paypal/PayPalWebhookServiceTest.java`

- [ ] **Step 1: Write failing PayPal contract tests**

```java
@Test
void createOrderSendsServerAmountAndStableIdempotencyKey() throws Exception {
    mockPayPal.enqueue(oauthResponse());
    mockPayPal.enqueue(createOrderResponse("PP-1", "25.50"));

    PayPalOrderDetails result = client.createOrder("PO-1", 2_550L, "PO-1-paypal");

    RecordedRequest create = mockPayPal.takeRequest();
    RecordedRequest order = mockPayPal.takeRequest();
    assertThat(order.getPath()).isEqualTo("/v2/checkout/orders");
    assertThat(order.getHeader("PayPal-Request-Id")).isEqualTo("PO-1-paypal");
    assertThat(order.getBody().readUtf8()).contains("\"value\":\"25.50\"");
    assertThat(result.orderId()).isEqualTo("PP-1");
}

@Test
void webhookWithSameEventIdCreditsAtMostOnce() {
    when(payPal.verifyWebhook(anyMap(), anyString())).thenReturn(true);
    when(events.insertIfAbsent("PAYPAL", "WH-1", "PAYMENT.CAPTURE.COMPLETED", order.getId(), any(), any()))
        .thenReturn(1, 0);

    webhooks.handle(headers(), completedCaptureEvent("WH-1"));
    webhooks.handle(headers(), completedCaptureEvent("WH-1"));

    verify(eventPublisher, times(1)).publishEvent(any(PaymentConfirmedEvent.class));
}
```

- [ ] **Step 2: Run tests to verify failure**

```powershell
mvn -f backend/pom.xml -Dskip.frontend=true -Dtest=HttpPayPalClientTest,PayPalPaymentServiceTest,PayPalWebhookServiceTest test
```

Expected: compilation failure because the PayPal adapter and services do not exist.

- [ ] **Step 3: Implement `HttpPayPalClient`**

Use Sandbox by default and Live only when `payment.paypal.mode=live`:

```java
private String baseUrl() {
    return "live".equalsIgnoreCase(properties.getMode())
            ? "https://api-m.paypal.com"
            : "https://api-m.sandbox.paypal.com";
}
```

Implement:

```text
POST /v1/oauth2/token                         OAuth client_credentials
POST /v2/checkout/orders                      CAPTURE order creation
POST /v2/checkout/orders/{orderId}/capture    provider capture
POST /v1/notifications/verify-webhook-signature
```

Cache the OAuth access token in-memory until 30 seconds before `expires_in`. Never log access tokens, secrets, raw authorization headers, or full raw webhook payloads.

- [ ] **Step 4: Implement idempotent provider service**

`PayPalPaymentService.createProviderOrder(orderNo)`:

1. Lock and require a non-expired `WAITING_PAYMENT` PayPal order.
2. Reuse existing `payment_transactions.provider_order_id` when present.
3. Create provider order with `<orderNo>-paypal` idempotency key otherwise.
4. Require matching USD amount and nonblank provider order ID.
5. Persist transaction.

`capture(orderNo)`:

1. Lock local order.
2. Return existing order for `CONFIRMED`, `CREDITING`, `PAID`, `CREDIT_FAILED`, or `CREDIT_UNKNOWN`.
3. Require a non-expired waiting order and provider transaction.
4. Call PayPal Capture using `<orderNo>-capture` idempotency key.
5. Require matching provider order ID, nonblank capture ID, `COMPLETED`, `USD`, and exact amount cents.
6. Persist capture ID and transition to `CONFIRMED` once.
7. Publish `PaymentConfirmedEvent(orderNo)` only after transaction commit.

`PayPalWebhookService.handle(headers, rawBody)`:

1. Verify official PayPal signature before trusting fields.
2. Parse event ID/type after signature verification.
3. Insert event with unique `(provider, provider_event_id)`; duplicate means return normally.
4. Only confirm `PAYMENT.CAPTURE.COMPLETED` events.
5. Locate transaction by provider order ID under order lock.
6. Validate capture ID, completed status, USD and exact local cents.
7. Expired orders become `EXPIRED`; never credit.
8. Confirm once and publish the same post-commit credit event.

- [ ] **Step 5: Run PayPal tests**

```powershell
mvn -f backend/pom.xml -Dskip.frontend=true -Dtest=HttpPayPalClientTest,PayPalPaymentServiceTest,PayPalWebhookServiceTest test
```

Expected: PASS.

- [ ] **Step 6: Commit PayPal provider services**

```powershell
git add backend/src/main/java/io/ztoken/portal/payment/paypal backend/src/test/java/io/ztoken/portal/payment/paypal
git commit -m "feat: 添加 PayPal Sandbox 支付服务"
```

---

### Task 5: Expose customer payment API and raw PayPal webhook endpoint

**Files:**
- Create: `backend/src/main/java/io/ztoken/portal/payment/api/CreatePaymentOrderRequest.java`
- Create: `backend/src/main/java/io/ztoken/portal/payment/api/PaymentOrderResponse.java`
- Create: `backend/src/main/java/io/ztoken/portal/payment/api/PaymentOrderListResponse.java`
- Create: `backend/src/main/java/io/ztoken/portal/payment/api/PayPalConfigResponse.java`
- Create: `backend/src/main/java/io/ztoken/portal/payment/api/PayPalProviderOrderResponse.java`
- Create: `backend/src/main/java/io/ztoken/portal/payment/api/PaymentOrderController.java`
- Create: `backend/src/main/java/io/ztoken/portal/payment/api/PayPalPaymentController.java`
- Create: `backend/src/main/java/io/ztoken/portal/payment/api/PayPalWebhookController.java`
- Modify: `backend/src/main/java/io/ztoken/portal/error/ApiExceptionHandler.java` — add safe payment validation/configuration errors.
- Test: `backend/src/test/java/io/ztoken/portal/payment/api/PaymentOrderControllerTest.java`
- Test: `backend/src/test/java/io/ztoken/portal/payment/api/PayPalPaymentControllerTest.java`
- Test: `backend/src/test/java/io/ztoken/portal/payment/api/PayPalWebhookControllerTest.java`

- [ ] **Step 1: Write authorization and raw Webhook tests**

```java
@Test
void customerCannotReadAnotherUsersOrder() {
    String sessionForUserA = session(7L);
    PaymentOrder orderForUserB = persistedOrder(8L);

    ResponseEntity<Map> response = http.exchange(
        "/api/payments/orders/" + orderForUserB.getOrderNo(), HttpMethod.GET, authed(sessionForUserA), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
}

@Test
void webhookPassesRawBodyAndNormalizedHeadersToVerifier() {
    mvc.perform(post("/api/webhooks/paypal")
            .header("PAYPAL-TRANSMISSION-ID", "transmission-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(completedCaptureEvent()))
        .andExpect(status().isOk());

    verify(webhooks).handle(argThat(headers -> headers.get("paypal-transmission-id").equals("transmission-1")),
            eq(completedCaptureEvent()));
}
```

- [ ] **Step 2: Run tests to verify failure**

```powershell
mvn -f backend/pom.xml -Dskip.frontend=true -Dtest=PaymentOrderControllerTest,PayPalPaymentControllerTest,PayPalWebhookControllerTest test
```

Expected: compilation failure because API controllers and DTOs do not exist.

- [ ] **Step 3: Implement authenticated customer endpoints**

Create order request:

```java
public record CreatePaymentOrderRequest(
        @NotBlank String amount,
        @NotNull PaymentMethod method
) {}
```

Every customer route calls `sessions.require(sessionId)` and passes only the resulting principal to the service. Return 404 for a valid order number not owned by the current user, preventing order enumeration.

PayPal endpoints must not accept amount/provider/capture IDs in request bodies:

```text
GET  /api/payments/orders/{orderNo}/paypal/config
POST /api/payments/orders/{orderNo}/paypal/order
POST /api/payments/orders/{orderNo}/paypal/capture
```

`config` returns only:

```json
{"clientId":"public-client-id","mode":"sandbox"}
```

Set `Cache-Control: no-store` on all order detail, PayPal config, provider order, and capture responses.

Webhook endpoint must use raw string body, normalize headers to lower case, and return only 200/400/503 with no provider internals in body.

- [ ] **Step 4: Run API tests**

```powershell
mvn -f backend/pom.xml -Dskip.frontend=true -Dtest=PaymentOrderControllerTest,PayPalPaymentControllerTest,PayPalWebhookControllerTest test
```

Expected: PASS.

- [ ] **Step 5: Commit payment HTTP API**

```powershell
git add backend/src/main/java/io/ztoken/portal/payment/api backend/src/main/java/io/ztoken/portal/error/ApiExceptionHandler.java backend/src/test/java/io/ztoken/portal/payment/api
git commit -m "feat: 添加 Portal PayPal 订单接口"
```

---

### Task 6: Enable PayPal checkout and customer order list in React

**Files:**
- Modify: `frontend/src/api/portal.ts`
- Create: `frontend/src/features/payments/paypal-sdk.ts`
- Create: `frontend/src/features/payments/PayPalCheckout.tsx`
- Modify: `frontend/src/features/payments/PaymentSelectionPanel.tsx`
- Modify: `frontend/src/features/payments/PurchasePage.tsx`
- Modify: `frontend/src/features/payments/RechargePage.tsx`
- Modify: `frontend/src/features/orders/OrdersPage.tsx`
- Modify: `frontend/src/features/payments/__tests__/purchase-page.test.tsx`
- Modify: `frontend/src/features/payments/__tests__/recharge-page.test.tsx`
- Modify: `frontend/src/features/orders/__tests__/orders-page.test.tsx`
- Create: `frontend/src/features/payments/__tests__/paypal-sdk.test.ts`
- Create: `frontend/src/features/payments/__tests__/paypal-checkout.test.tsx`
- Modify: `frontend/src/i18n/locales/en.json`
- Modify: `frontend/src/i18n/locales/zh-CN.json`
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: Write failing frontend flow tests**

```tsx
it('creates a PayPal order from the selected amount without sending quota or user ID', async () => {
  render(<PurchasePage />)
  await user.click(screen.getByRole('button', { name: '$25.50' }))
  await user.click(screen.getByRole('button', { name: 'PayPal' }))

  expect(fetch).toHaveBeenCalledWith('/api/payments/orders', expect.objectContaining({
    method: 'POST',
    body: JSON.stringify({ amount: '25.50', method: 'PAYPAL' }),
  }))
  expect(String(vi.mocked(fetch).mock.calls[0][1]?.body)).not.toContain('quota')
  expect(String(vi.mocked(fetch).mock.calls[0][1]?.body)).not.toContain('userId')
})

it('shows manual-review status for CREDIT_UNKNOWN without claiming payment succeeded', async () => {
  mockOrderPolling({ status: 'CREDIT_UNKNOWN' })
  render(<PayPalCheckout order={order} />)

  expect(await screen.findByText('Payment received and awaiting manual review.')).toBeVisible()
  expect(screen.queryByText('Recharge completed.')).not.toBeInTheDocument()
})
```

- [ ] **Step 2: Run frontend tests to verify failure**

```powershell
cd F:\WorkSpace\study\AIProject\Ztoken\portal\frontend
npm test -- --run src/features/payments/__tests__/paypal-sdk.test.ts src/features/payments/__tests__/paypal-checkout.test.tsx src/features/payments/__tests__/purchase-page.test.tsx src/features/orders/__tests__/orders-page.test.tsx
```

Expected: import failure because order and PayPal Checkout code does not exist.

- [ ] **Step 3: Implement typed payment API client**

Add types:

```ts
export type PaymentOrderStatus = 'WAITING_PAYMENT' | 'CONFIRMED' | 'CREDITING' | 'PAID' | 'CREDIT_FAILED' | 'CREDIT_UNKNOWN' | 'EXPIRED' | 'CANCELLED'

export interface PaymentOrder {
  orderNo: string
  amountUsdMinor: number
  quotaToCredit: number
  method: 'PAYPAL'
  status: PaymentOrderStatus
  expiresAt: string
  confirmedAt: string | null
  creditedAt: string | null
}
```

Implement `createPaymentOrder`, `getPaymentOrder`, `getPaymentOrders`, `getPayPalConfig`, `createPayPalProviderOrder`, and `capturePayPalOrder`. All calls use `credentials: 'include'`; no function accepts client-side quota, NewAPI user ID, PayPal provider order ID, or capture ID.

- [ ] **Step 4: Implement idempotent SDK loader**

`paypal-sdk.ts` creates exactly one script per `clientId`/mode and resolves only after `window.paypal` exists. It must reject on script error and never append Client Secret to a URL.

```ts
export function loadPayPalSdk(clientId: string, mode: 'sandbox' | 'live'): Promise<PayPalNamespace> {
  const src = `https://www.paypal.com/sdk/js?client-id=${encodeURIComponent(clientId)}&currency=USD&intent=capture`
  // Cache a promise keyed by src; append one script; reject on error.
}
```

- [ ] **Step 5: Implement payment UI state transitions**

`PaymentSelectionPanel` enables PayPal only after a valid amount is selected. On selection it creates a Portal order; `PayPalCheckout` then:

1. Loads public config and SDK.
2. Uses `createOrder` callback to call Portal `/paypal/order`.
3. Uses `onApprove` callback to call Portal `/paypal/capture`.
4. Polls `GET /api/payments/orders/{orderNo}` every two seconds while `WAITING_PAYMENT`, `CONFIRMED`, or `CREDITING`.
5. Stops polling on `PAID`, `CREDIT_FAILED`, `CREDIT_UNKNOWN`, `EXPIRED`, or `CANCELLED`.
6. Shows explicit status-specific text; no success claim for failure/unknown states.

Leave Crypto/USDT and other methods disabled with their existing coming-soon display.

`OrdersPage` fetches only current-user orders, uses Semi `Table`/`Pagination`, and maps every status through i18n keys.

- [ ] **Step 6: Run frontend tests and build**

```powershell
npm test -- --run
npm run build
```

Expected: all tests pass and Vite build succeeds.

- [ ] **Step 7: Commit payment frontend**

```powershell
git add frontend/src/api/portal.ts frontend/src/features/payments frontend/src/features/orders frontend/src/i18n/locales/en.json frontend/src/i18n/locales/zh-CN.json frontend/src/styles.css
git commit -m "feat: 添加 Portal PayPal Checkout 与订单页面"
```

---

### Task 7: Final verification and Sandbox readiness documentation

**Files:**
- Modify: `README.md`
- Test: all payment backend and frontend tests

- [ ] **Step 1: Document only variable names and operational steps**

Add a README PayPal Sandbox section without secret values:

```text
PAYMENT_PAYPAL_MODE=sandbox
PAYMENT_PAYPAL_CLIENT_ID
PAYMENT_PAYPAL_CLIENT_SECRET
PAYMENT_PAYPAL_WEBHOOK_ID
PAYMENT_NEWAPI_CREDIT_ACCESS_TOKEN
PAYMENT_ORDER_EXPIRY_MINUTES=30
```

Document that the Sandbox Webhook must target:

```text
https://<portal-domain>/api/webhooks/paypal
```

Document that `CREDIT_UNKNOWN` requires manual reconciliation and must not be auto-retried.

- [ ] **Step 2: Run complete frontend verification**

```powershell
npm --prefix F:/WorkSpace/study/AIProject/Ztoken/portal/frontend test -- --run
npm --prefix F:/WorkSpace/study/AIProject/Ztoken/portal/frontend run build
```

Expected: all frontend tests pass; bundle-size warning may be recorded but is not a failure.

- [ ] **Step 3: Run complete backend verification**

```powershell
mvn -f F:/WorkSpace/study/AIProject/Ztoken/portal/backend/pom.xml test
mvn -f F:/WorkSpace/study/AIProject/Ztoken/portal/backend/pom.xml package
```

Expected: all backend tests pass and the JAR is created with frontend static assets.

- [ ] **Step 4: Verify migration behavior explicitly**

```powershell
mvn -f F:/WorkSpace/study/AIProject/Ztoken/portal/backend/pom.xml -Dskip.frontend=true -Dtest=PortalApplicationTest test
mvn -f F:/WorkSpace/study/AIProject/Ztoken/portal/backend/pom.xml -Dskip.frontend=true -Dtest=PortalApplicationTest test
```

Expected: both runs pass; the second run applies no new Flyway migration.

- [ ] **Step 5: Verify no forbidden files were touched**

```powershell
cd F:\WorkSpace\study\AIProject\Ztoken\portal
git status --short
git diff --name-only HEAD~7..HEAD
```

Expected: no changes in `new-api`; `backend/src/main/resources/application.yml` remains untracked by commits and unstaged.

- [ ] **Step 6: Commit operational documentation**

```powershell
git add README.md
git commit -m "docs: 记录 PayPal Sandbox 部署要求"
```

---

## Plan Self-Review

### Spec coverage

- Sandbox/Live configuration: Task 1 and Task 7.
- Integer cents and 500,000 quota/USD: Task 3.
- Local order, provider transaction, event and credit audit tables: Task 2.
- Capture immediately credits; webhook is signed/idempotent fallback: Task 4.
- `CREDIT_FAILED` and `CREDIT_UNKNOWN` behavior: Task 3, Task 4, and Task 6.
- Customer-only order access and raw Webhook endpoint: Task 5.
- PayPal JS SDK, purchase/recharge UX, status polling, and orders page: Task 6.
- No TRC20, no admin review UI, no OAuth, no NewAPI source/database access: stated in constraints and every task boundary.

### Placeholder scan

The plan contains no deferred implementation markers. Sandbox credentials are intentionally external deployment inputs; the exact variable names, validation behavior, and lack of real payment calls in automated tests are explicit.

### Type consistency

- `amountUsdMinor` is `long` on backend and `number` on frontend.
- `quotaToCredit` is `long` on backend and `number` on frontend.
- `PaymentOrderStatus` values are used consistently by persistence, API and UI.
- Customer authorization consistently comes from `PortalPrincipal`.
- PayPal provider/capture identifiers never originate from trusted browser input.
