# Customer Portal Payments and Orders Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add PayPal and TRC20-USDT checkout, safe idempotent NewAPI quota crediting, and customer-only order management to the completed portal core.

**Architecture:** The portal database stores immutable payment snapshots, provider events, and NewAPI credit attempts. Provider confirmation moves a payment order through an explicit state machine; quota crediting uses NewAPI's documented administrator management endpoint and never accesses the NewAPI database.

**Tech Stack:** Java 17, Spring Boot 3.3, Spring Data JPA, Flyway, WebClient, MockWebServer, PayPal REST API, TronGrid API, React 19, TypeScript, React Query, Vitest, React Testing Library.

---

## File structure

```text
portal/backend/
  src/main/java/io/ztoken/portal/payment/
    PaymentMethod.java
    PaymentOrderStatus.java
    PaymentOrder.java
    PaymentProviderEvent.java
    CreditAttempt.java
    PaymentOrderRepository.java
    PaymentProviderEventRepository.java
    CreditAttemptRepository.java
    PaymentOrderService.java
    PaymentCreditService.java
    PaymentOrderController.java
    PayPalClient.java
    PayPalPaymentService.java
    PayPalWebhookController.java
    TronGridClient.java
    UsdtVerificationService.java
  src/main/resources/db/migration/V2__create_payment_orders.sql
  src/test/java/io/ztoken/portal/payment/...
portal/frontend/
  src/features/payments/RechargePage.tsx
  src/features/payments/OrderListPage.tsx
  src/features/payments/OrderDetailPage.tsx
  src/features/payments/PayPalCheckout.tsx
  src/features/payments/UsdtPaymentPanel.tsx
  src/features/payments/__tests__/...
```

### Task 1: Create the payment schema and explicit order state machine

**Files:**
- Create: `portal/backend/src/main/resources/db/migration/V2__create_payment_orders.sql`
- Create: `portal/backend/src/main/java/io/ztoken/portal/payment/PaymentMethod.java`
- Create: `portal/backend/src/main/java/io/ztoken/portal/payment/PaymentOrderStatus.java`
- Create: `portal/backend/src/main/java/io/ztoken/portal/payment/PaymentOrder.java`
- Create: `portal/backend/src/main/java/io/ztoken/portal/payment/PaymentProviderEvent.java`
- Create: `portal/backend/src/main/java/io/ztoken/portal/payment/CreditAttempt.java`
- Create: `portal/backend/src/main/java/io/ztoken/portal/payment/PaymentOrderRepository.java`
- Create: `portal/backend/src/test/java/io/ztoken/portal/payment/PaymentOrderStateTest.java`

- [ ] **Step 1: Write failing domain tests for allowed and rejected state transitions.**

```java
@Test void confirmedPaymentCreditsExactlyOnce() {
  PaymentOrder order = PaymentOrder.waitingForPayment(7L, new BigDecimal("10.00"), PaymentMethod.PAYPAL);
  order.markPaymentConfirmed("capture-1");
  order.startCrediting();
  order.completeCredit();
  assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.COMPLETED);
  assertThatThrownBy(() -> order.startCrediting()).isInstanceOf(IllegalStateException.class);
}

@Test void unknownCreditResultRequiresManualReview() {
  PaymentOrder order = confirmedOrder();
  order.startCrediting();
  order.markCreditUnknown();
  assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.MANUAL_REVIEW);
}
```

- [ ] **Step 2: Run the domain test and confirm it fails.**

Run: `mvn -f backend/pom.xml test -Dtest=PaymentOrderStateTest`

Expected: compilation fails because payment domain classes do not exist.

- [ ] **Step 3: Implement the schema and aggregate.**

Create `payment_orders` with immutable `order_no`, `newapi_user_id`, USD amount, payment method, state, payment snapshot JSON/text fields, expiry, provider transaction ID, and timestamps. Create `payment_provider_events` with unique `(provider, provider_event_id)` and `credit_attempts` with unique order/attempt sequence. Implement only these legal transitions: `WAITING_PAYMENT -> PAYMENT_CONFIRMED | EXPIRED | FAILED`, `PAYMENT_CONFIRMED -> CREDITING`, `CREDITING -> COMPLETED | MANUAL_REVIEW`, and `MANUAL_REVIEW -> CREDITING` after an explicit operator action.

- [ ] **Step 4: Run domain tests and migration-backed tests.**

Run: `mvn -f backend/pom.xml test -Dtest=PaymentOrderStateTest`

Expected: PASS; illegal duplicate transitions throw before persistence.

- [ ] **Step 5: Commit the payment data model.**

```bash
git add backend
git commit -m "feat: add payment order state machine"
```

### Task 2: Add NewAPI credit adapter and idempotent credit service

**Files:**
- Create: `portal/backend/src/main/java/io/ztoken/portal/payment/NewApiCreditClient.java`
- Create: `portal/backend/src/main/java/io/ztoken/portal/payment/PaymentCreditService.java`
- Create: `portal/backend/src/test/java/io/ztoken/portal/payment/PaymentCreditServiceTest.java`
- Modify: `portal/backend/src/main/java/io/ztoken/portal/config/PortalProperties.java`

- [ ] **Step 1: Write failing tests for success, explicit rejection, and unknown NewAPI credit outcomes.**

```java
@Test void marksCompletedOnlyAfterNewApiConfirmsQuotaIncrease() {
  mockNewApi(200, "{\"success\":true}");
  credits.credit(confirmedOrder());
  assertThat(order.getStatus()).isEqualTo(COMPLETED);
}

@Test void timeoutCreatesAttemptAndMovesOrderToManualReviewWithoutRetry() {
  mockTimeout();
  credits.credit(confirmedOrder());
  assertThat(order.getStatus()).isEqualTo(MANUAL_REVIEW);
  assertThat(attempts.findByOrderId(order.getId())).hasSize(1);
}
```

- [ ] **Step 2: Run the service test and confirm failure.**

Run: `mvn -f backend/pom.xml test -Dtest=PaymentCreditServiceTest`

Expected: compilation fails because the payment credit service does not exist.

- [ ] **Step 3: Implement the administrator-only credit adapter.**

Use `NEWAPI_ADMIN_ACCESS_TOKEN` only in `NewApiCreditClient`. Send `POST /api/user/manage` with `{ "id": userId, "action": "add_quota", "mode": "add", "value": quota }`. Convert USD to quota through one configured `quotaPerUsd` decimal value using exact decimal arithmetic and reject non-positive/out-of-range values. Persist the attempt before the request, mark it successful only on `success:true`, and mark timeout/network/invalid-body outcomes unknown. Do not automatically repeat an unknown attempt.

- [ ] **Step 4: Run the focused service tests.**

Run: `mvn -f backend/pom.xml test -Dtest=PaymentCreditServiceTest`

Expected: PASS; no test performs a direct NewAPI database operation.

- [ ] **Step 5: Commit the quota credit boundary.**

```bash
git add backend
git commit -m "feat: add idempotent NewAPI quota crediting"
```

### Task 3: Implement checkout/order APIs and customer-only order access

**Files:**
- Create: `portal/backend/src/main/java/io/ztoken/portal/payment/CreateOrderRequest.java`
- Create: `portal/backend/src/main/java/io/ztoken/portal/payment/PaymentOrderDto.java`
- Create: `portal/backend/src/main/java/io/ztoken/portal/payment/PaymentOrderService.java`
- Create: `portal/backend/src/main/java/io/ztoken/portal/payment/PaymentOrderController.java`
- Create: `portal/backend/src/test/java/io/ztoken/portal/payment/PaymentOrderControllerTest.java`

- [ ] **Step 1: Write failing endpoint tests for amount validation and order ownership.**

```java
@Test void createsOrderForCurrentUserNotBodySuppliedUser() {
  ResponseEntity<PaymentOrderDto> response = authenticatedPost("/api/orders", new CreateOrderRequest("10", "PAYPAL"));
  assertThat(response.getBody().newApiUserId()).isNull();
  assertThat(repository.findByOrderNo(response.getBody().orderNo()).orElseThrow().getNewapiUserId()).isEqualTo(7L);
}

@Test void deniesOrderDetailOwnedByAnotherUser() {
  assertThat(authenticatedGetAs(8L, "/api/orders/" + orderForUser(7L).getOrderNo()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
}
```

- [ ] **Step 2: Run the controller test and confirm failure.**

Run: `mvn -f backend/pom.xml test -Dtest=PaymentOrderControllerTest`

Expected: compilation fails because order endpoints do not exist.

- [ ] **Step 3: Implement order creation/list/detail routes.**

Expose `POST /api/orders`, `GET /api/orders`, `GET /api/orders/{orderNo}`, and `POST /api/orders/{orderNo}/refresh`. Allow only decimal values matching preset amounts 5, 10, 50, 100, 200, 500 or a configured min/max custom range. Resolve the user from `PortalAuthInterceptor`; omit `newApiUserId`, provider secrets, and credit-attempt internals from customer DTOs. List orders newest-first and limit page size to 50.

- [ ] **Step 4: Run the endpoint tests and entire backend test suite.**

Run: `mvn -f backend/pom.xml test`

Expected: PASS; a client cannot choose a different recipient or read another customer's payment metadata.

- [ ] **Step 5: Commit checkout and order management APIs.**

```bash
git add backend
git commit -m "feat: add customer payment order APIs"
```

### Task 4: Implement PayPal order creation, capture, and webhook verification

**Files:**
- Create: `portal/backend/src/main/java/io/ztoken/portal/payment/PayPalClient.java`
- Create: `portal/backend/src/main/java/io/ztoken/portal/payment/PayPalPaymentService.java`
- Create: `portal/backend/src/main/java/io/ztoken/portal/payment/PayPalWebhookController.java`
- Create: `portal/backend/src/test/java/io/ztoken/portal/payment/PayPalPaymentServiceTest.java`
- Create: `portal/backend/src/test/java/io/ztoken/portal/payment/PayPalWebhookControllerTest.java`

- [ ] **Step 1: Write failing tests for verified capture and duplicate webhooks.**

```java
@Test void verifiedCompletedCaptureConfirmsExactlyOneMatchingOrder() {
  service.capture(order.getOrderNo(), "paypal-order-id");
  assertThat(order.getStatus()).isEqualTo(PAYMENT_CONFIRMED);
}

@Test void duplicateWebhookEventDoesNotCreditTwice() {
  webhook.receive(headers("event-1"), completedCapturePayload(order));
  webhook.receive(headers("event-1"), completedCapturePayload(order));
  assertThat(providerEvents.count()).isEqualTo(1);
  assertThat(creditAttempts.count()).isEqualTo(1);
}
```

- [ ] **Step 2: Run PayPal tests and confirm failure.**

Run: `mvn -f backend/pom.xml test -Dtest=PayPalPaymentServiceTest,PayPalWebhookControllerTest`

Expected: compilation fails because the PayPal service and webhook controller do not exist.

- [ ] **Step 3: Implement PayPal server verification.**

Create PayPal orders server-side with the portal order number in `custom_id`, return only PayPal client ID and order ID required by the browser SDK, and verify capture status/amount/currency/custom ID through PayPal's server API. Verify webhook signatures using the configured webhook ID before processing event content. Store each verified provider event under its unique PayPal event ID, then invoke `PaymentCreditService` once for the matching order.

- [ ] **Step 4: Run PayPal tests.**

Run: `mvn -f backend/pom.xml test -Dtest=PayPalPaymentServiceTest,PayPalWebhookControllerTest`

Expected: PASS; tampered signature, currency mismatch, amount mismatch, and duplicate event do not credit quota.

- [ ] **Step 5: Commit PayPal payment handling.**

```bash
git add backend
git commit -m "feat: add verified PayPal checkout"
```

### Task 5: Implement TRC20-USDT order snapshots and chain verification

**Files:**
- Create: `portal/backend/src/main/java/io/ztoken/portal/payment/TronGridClient.java`
- Create: `portal/backend/src/main/java/io/ztoken/portal/payment/UsdtVerificationService.java`
- Create: `portal/backend/src/main/java/io/ztoken/portal/payment/SubmitTxidRequest.java`
- Modify: `portal/backend/src/main/java/io/ztoken/portal/payment/PaymentOrderController.java`
- Create: `portal/backend/src/test/java/io/ztoken/portal/payment/UsdtVerificationServiceTest.java`

- [ ] **Step 1: Write failing tests for matched transfers and invalid transaction hashes.**

```java
@Test void confirmsOnlyTransferWithMatchingContractRecipientAmountAndConfirmations() {
  tronGrid.returnsTransfer(contract, order.getReceiveAddress(), order.getPayableAmount(), 20);
  verifier.verify(order.getOrderNo(), "a".repeat(64));
  assertThat(order.getStatus()).isEqualTo(PAYMENT_CONFIRMED);
}

@Test void rejectsTransactionHashForAnotherCustomersOrder() {
  assertThat(authenticatedPostAs(8L, "/api/orders/" + orderForUser(7L).getOrderNo() + "/txid", new SubmitTxidRequest("a".repeat(64))).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
}
```

- [ ] **Step 2: Run USDT tests and confirm failure.**

Run: `mvn -f backend/pom.xml test -Dtest=UsdtVerificationServiceTest`

Expected: compilation fails because the chain verification classes do not exist.

- [ ] **Step 3: Implement immutable USDT snapshots and verification.**

On USDT order creation, snapshot the configured TRC20 contract, recipient address, expiry, confirmation count, and a unique payable amount generated from the USD amount using a configured exchange rate plus a collision-free fractional suffix. Add `POST /api/orders/{orderNo}/txid`, require a 64-character hex transaction hash, and authorize against the current user. Query TronGrid, require a matching TRC20 contract, recipient, amount, successful transfer, and sufficient confirmations. Record observed transaction hashes uniquely; only a verified match can confirm and credit the order.

- [ ] **Step 4: Run USDT tests.**

Run: `mvn -f backend/pom.xml test -Dtest=UsdtVerificationServiceTest`

Expected: PASS; wrong contract, receiver, amount, confirmation count, expired order, and reused transaction hash remain uncredited.

- [ ] **Step 5: Commit USDT payment verification.**

```bash
git add backend
git commit -m "feat: add verified TRC20 USDT checkout"
```

### Task 6: Build recharge and order-management pages

**Files:**
- Create: `portal/frontend/src/features/payments/RechargePage.tsx`
- Create: `portal/frontend/src/features/payments/PayPalCheckout.tsx`
- Create: `portal/frontend/src/features/payments/UsdtPaymentPanel.tsx`
- Create: `portal/frontend/src/features/payments/OrderListPage.tsx`
- Create: `portal/frontend/src/features/payments/OrderDetailPage.tsx`
- Create: `portal/frontend/src/features/payments/__tests__/recharge-page.test.tsx`
- Create: `portal/frontend/src/features/payments/__tests__/order-management.test.tsx`
- Modify: `portal/frontend/src/routes.tsx`

- [ ] **Step 1: Write failing UI tests for valid payment selection and an eligible USDT TxID submission.**

```tsx
it('creates a PayPal order from the selected fixed amount', async () => {
  render(<RechargePage />);
  await userEvent.click(screen.getByRole('button', { name: '$10' }));
  await userEvent.click(screen.getByRole('radio', { name: /paypal/i }));
  await userEvent.click(screen.getByRole('button', { name: /continue to payment/i }));
  expect(await screen.findByTestId('paypal-checkout')).toBeVisible();
});

it('shows TxID input only for a waiting USDT order and submits it', async () => {
  render(<OrderDetailPage order={waitingUsdtOrder} />);
  await userEvent.type(screen.getByLabelText(/transaction hash/i), 'a'.repeat(64));
  await userEvent.click(screen.getByRole('button', { name: /submit transaction/i }));
  expect(await screen.findByText(/verification submitted/i)).toBeVisible();
});
```

- [ ] **Step 2: Run payment UI tests and confirm failure.**

Run: `npm --prefix frontend test -- --run src/features/payments/__tests__/recharge-page.test.tsx src/features/payments/__tests__/order-management.test.tsx`

Expected: Vitest reports missing payment components.

- [ ] **Step 3: Implement the customer payment UI.**

Render fixed amount cards plus a numeric custom amount input with client-side bounds matching server configuration. Create orders through `/api/orders`, render PayPal only after the backend returns a PayPal session/order ID, and show the USDT snapshot with copyable address/amount, expiry countdown, refresh action, and eligible TxID submission. Add an Orders console route with localized statuses, list pagination, and detail routes. Do not treat client-side completion as payment confirmation; display the server-reported state after polling/refresh.

- [ ] **Step 4: Run all frontend tests and build.**

Run: `npm --prefix frontend test`

Run: `npm --prefix frontend run build`

Expected: PASS; translations exist for every displayed payment state.

- [ ] **Step 5: Commit checkout and order pages.**

```bash
git add frontend
git commit -m "feat: add checkout and customer orders"
```

### Task 7: Execute payment integration and package verification

**Files:**
- Modify: `portal/README.md`
- Create: `portal/backend/src/test/java/io/ztoken/portal/payment/PaymentFlowIntegrationTest.java`

- [ ] **Step 1: Write a failing end-to-end payment test with mocked providers.**

```java
@Test void verifiedPaypalEventCreditsOnceAndCustomerCanReadCompletedOrder() {
  PaymentOrderDto order = createOrderAs(7L, "10", "PAYPAL");
  sendVerifiedPayPalWebhook(order, "event-1");
  sendVerifiedPayPalWebhook(order, "event-1");
  assertThat(getOrderAs(7L, order.orderNo()).status()).isEqualTo("COMPLETED");
  assertThat(mockNewApi.creditCalls()).isEqualTo(1);
}
```

- [ ] **Step 2: Run the integration test and confirm failure.**

Run: `mvn -f backend/pom.xml test -Dtest=PaymentFlowIntegrationTest`

Expected: FAIL until the payment controller, provider verification, and credit service are wired together.

- [ ] **Step 3: Wire the production configuration and document deployment.**

Add blank environment-variable examples for `PORTAL_SESSION_KEY`, `NEWAPI_BASE_URL`, `NEWAPI_ADMIN_ACCESS_TOKEN`, `PAYPAL_CLIENT_ID`, `PAYPAL_CLIENT_SECRET`, `PAYPAL_WEBHOOK_ID`, `TRONGRID_API_KEY`, `USDT_TRC20_CONTRACT`, `USDT_RECEIVE_ADDRESS`, `USDT_CONFIRMATIONS`, `USDT_USD_RATE`, `PORTAL_DB_URL`, `PORTAL_DB_USERNAME`, and `PORTAL_DB_PASSWORD`. Document PayPal sandbox/live setup, NewAPI OAuth callback configuration, and TronGrid rate limits without committing secret values.

- [ ] **Step 4: Run full tests and create the single JAR.**

Run: `mvn -f backend/pom.xml clean package`

Expected: backend/frontend tests pass and the packaged JAR serves the React recharge/order pages plus payment APIs from one port.

- [ ] **Step 5: Commit the verified payment flow.**

```bash
git add README.md backend frontend
git commit -m "test: verify payment order flow"
```
