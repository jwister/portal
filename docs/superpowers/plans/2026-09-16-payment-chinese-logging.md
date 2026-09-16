# Portal 支付中文日志 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 Portal 支付的订单、PayPal、TRC20 和 NewAPI 入账闭环添加不泄露凭据的详细中文业务日志。

**Architecture:** 在状态机服务和外部 HTTP 客户端附近记录可关联订单的关键事件；Controller 仅补充请求入口和结果日志。日志使用 SLF4J 参数化模板，所有秘钥、签名、会话及原始回调正文保持不可见。

**Tech Stack:** Java 17、Spring Boot 3、SLF4J、JUnit 5、Mockito。

---

### Task 1: 建立支付日志的安全约束与订单生命周期日志

**Files:**
- Modify: `backend/src/main/java/io/ztoken/portal/payment/order/PaymentOrderService.java`
- Modify: `backend/src/main/java/io/ztoken/portal/payment/provider/PayPalPaymentProvider.java`
- Modify: `backend/src/main/java/io/ztoken/portal/payment/provider/Trc20PaymentProvider.java`
- Test: `backend/src/test/java/io/ztoken/portal/payment/order/PaymentOrderServiceTest.java`

- [ ] **Step 1: 写出失败的日志事件测试**

```java
assertThat(capturedLog.getFormattedMessage())
        .contains("支付订单创建成功")
        .contains("订单号=")
        .doesNotContain("Access-Token");
```

- [ ] **Step 2: 运行测试并确认因缺少日志事件失败**

Run: `mvn -q -Dskip.frontend=true -Dtest=PaymentOrderServiceTest test`

Expected: FAIL，断言找不到“支付订单创建成功”。

- [ ] **Step 3: 添加最小的参数化中文日志**

```java
log.info("支付订单创建成功：订单号={}，支付方式={}，用户ID={}，金额分={}，计划入账额度={}，过期时间={}",
        order.getOrderNo(), order.getPaymentMethod(), order.getNewApiUserId(),
        order.getAmountUsdMinor(), order.getQuotaToCredit(), order.getExpiresAt());
```

- [ ] **Step 4: 运行测试并确认通过**

Run: `mvn -q -Dskip.frontend=true -Dtest=PaymentOrderServiceTest test`

Expected: PASS。

### Task 2: 为 PayPal 创建、捕获、验签和 HTTP 调用补齐日志

**Files:**
- Modify: `backend/src/main/java/io/ztoken/portal/payment/paypal/PayPalPaymentService.java`
- Modify: `backend/src/main/java/io/ztoken/portal/payment/paypal/PayPalWebhookService.java`
- Modify: `backend/src/main/java/io/ztoken/portal/payment/paypal/HttpPayPalClient.java`
- Modify: `backend/src/main/java/io/ztoken/portal/payment/api/PayPalWebhookController.java`
- Test: `backend/src/test/java/io/ztoken/portal/payment/paypal/PayPalPaymentServiceTest.java`
- Test: `backend/src/test/java/io/ztoken/portal/payment/paypal/PayPalWebhookServiceTest.java`

- [ ] **Step 1: 写出失败的 PayPal 日志断言**

```java
assertThat(capturedLog.getFormattedMessage())
        .contains("PayPal 捕获确认成功")
        .contains("订单号=PO-1")
        .doesNotContain("client-secret");
```

- [ ] **Step 2: 运行定向测试确认失败**

Run: `mvn -q -Dskip.frontend=true -Dtest=PayPalPaymentServiceTest,PayPalWebhookServiceTest test`

Expected: FAIL，断言找不到 PayPal 中文事件。

- [ ] **Step 3: 在关键状态变更和外部调用结果旁添加日志**

```java
log.info("PayPal 捕获确认成功：订单号={}，PayPal订单号={}，捕获号={}，金额分={}，订单状态={}",
        order.getOrderNo(), transaction.getProviderOrderId(), captureId,
        order.getAmountUsdMinor(), order.getStatus());
```

同时记录订单复用、订单过期、金额不匹配、验签失败、重复事件、安全忽略、回调确认和 PayPal HTTP 非预期响应；不得记录 Webhook 原文、签名头或 OAuth 令牌。

- [ ] **Step 4: 运行定向测试确认通过**

Run: `mvn -q -Dskip.frontend=true -Dtest=PayPalPaymentServiceTest,PayPalWebhookServiceTest,HttpPayPalClientTest test`

Expected: PASS。

### Task 3: 为 TRC20 地址池、扫描和 TxID 核验补齐日志

**Files:**
- Modify: `backend/src/main/java/io/ztoken/portal/payment/trc20/Trc20AddressPoolService.java`
- Modify: `backend/src/main/java/io/ztoken/portal/payment/trc20/Trc20PaymentScanner.java`
- Modify: `backend/src/main/java/io/ztoken/portal/payment/trc20/TxidVerificationService.java`
- Modify: `backend/src/main/java/io/ztoken/portal/payment/trc20/TransferVerificationService.java`
- Modify: `backend/src/main/java/io/ztoken/portal/payment/trc20/HttpTronGridTransferClient.java`
- Test: `backend/src/test/java/io/ztoken/portal/payment/trc20/Trc20PaymentScannerTest.java`
- Test: `backend/src/test/java/io/ztoken/portal/payment/trc20/TxidVerificationServiceTest.java`

- [ ] **Step 1: 写出失败的重试与确认日志断言**

```java
assertThat(capturedLog.getFormattedMessage())
        .contains("TRC20 交易暂未索引，已安排复查")
        .contains("订单号=PO_TRON_1")
        .doesNotContain("api-key");
```

- [ ] **Step 2: 运行定向测试确认失败**

Run: `mvn -q -Dskip.frontend=true -Dtest=Trc20PaymentScannerTest,TxidVerificationServiceTest,TransferVerificationServiceTest test`

Expected: FAIL，断言找不到 TRC20 中文事件。

- [ ] **Step 3: 添加最小且有状态变化的日志**

```java
log.info("TRC20 交易暂未索引，已安排复查：订单号={}，交易哈希={}，复查次数={}，下次复查时间={}",
        order.getOrderNo(), order.getSubmittedTxid(), order.getTxidCheckCount(), order.getNextTxidCheckAt());
```

记录地址分配、扫描发现转账、匹配/不匹配原因、确认数不足、重复交易、确认成功、查询失败、TxID 提交、重试和订单过期；不记录 TronGrid API Key。

- [ ] **Step 4: 运行定向测试确认通过**

Run: `mvn -q -Dskip.frontend=true -Dtest=Trc20PaymentScannerTest,TxidVerificationServiceTest,TransferVerificationServiceTest,HttpTronGridTransferClientTest test`

Expected: PASS。

### Task 4: 为确认后入账、NewAPI 调用和恢复任务补齐日志

**Files:**
- Modify: `backend/src/main/java/io/ztoken/portal/payment/credit/PaymentCreditListener.java`
- Modify: `backend/src/main/java/io/ztoken/portal/payment/credit/PaymentCreditService.java`
- Modify: `backend/src/main/java/io/ztoken/portal/payment/credit/HttpNewApiCreditClient.java`
- Modify: `backend/src/main/java/io/ztoken/portal/payment/credit/PaymentCreditRecovery.java`
- Test: `backend/src/test/java/io/ztoken/portal/payment/credit/PaymentCreditServiceTest.java`
- Test: `backend/src/test/java/io/ztoken/portal/payment/credit/HttpNewApiCreditClientTest.java`

- [ ] **Step 1: 写出失败的入账成功和未知结果日志断言**

```java
assertThat(capturedLog.getFormattedMessage())
        .contains("NewAPI 额度入账成功")
        .contains("订单号=PO_CREDIT")
        .doesNotContain("Bearer ");
```

- [ ] **Step 2: 运行定向测试确认失败**

Run: `mvn -q -Dskip.frontend=true -Dtest=PaymentCreditServiceTest,HttpNewApiCreditClientTest,PaymentCreditRecoveryTest test`

Expected: FAIL，断言找不到 NewAPI 中文事件。

- [ ] **Step 3: 添加领取、调用、结果落库与恢复日志**

```java
log.info("NewAPI 额度入账成功：订单号={}，用户ID={}，入账额度={}，订单状态={}",
        order.getOrderNo(), order.getNewApiUserId(), order.getQuotaToCredit(), order.getStatus());
```

分别记录无法领取、额度上限拦截、请求发起、HTTP 状态分类、成功、业务拒绝、结果未知、持久化状态变更和启动恢复；不记录 Authorization Header 或请求响应正文。

- [ ] **Step 4: 运行定向测试确认通过**

Run: `mvn -q -Dskip.frontend=true -Dtest=PaymentCreditServiceTest,HttpNewApiCreditClientTest,PaymentCreditRecoveryTest test`

Expected: PASS。

### Task 5: 回归支付测试并检查日志语言与敏感信息

**Files:**
- Verify: `backend/src/main/java/io/ztoken/portal/payment/**/*.java`
- Verify: `backend/src/test/java/io/ztoken/portal/payment/**/*.java`

- [ ] **Step 1: 运行完整支付模块测试**

Run: `mvn -q -Dskip.frontend=true -Dtest='io.ztoken.portal.payment.**.*Test' test`

Expected: PASS。

- [ ] **Step 2: 静态检查日志安全性**

Run: `rg -n 'log\\.(info|warn|error).*?(token|secret|signature|rawBody|authorization|api-key)' backend/src/main/java/io/ztoken/portal/payment`

Expected: 没有将敏感字段作为日志参数的结果；若命中仅为中文说明或安全声明，人工确认不含值。

- [ ] **Step 3: 提交变更**

```bash
git add backend/src/main/java/io/ztoken/portal/payment backend/src/test/java/io/ztoken/portal/payment docs/superpowers
git commit -m "为支付流程补充中文日志"
```
