# Portal PayPal 与 TRC20-USDT 支付 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不破坏既有 PayPal 支付闭环的前提下，为 Ztoken Portal 增加具备地址池、多地址、金额尾数、自动扫描和 TxID 即时核验的 TRC20-USDT 充值渠道。

**Architecture:** `PaymentOrder` 仍是所有渠道唯一的订单与入账状态机；每个渠道在 `payment/provider` 下实现独立适配器，渠道确认后统一发布 `PaymentConfirmedEvent` 交给现有 `PaymentCreditService`。TRC20 通过地址池、金额占用表和链上事件唯一键防止误匹配及重复入账；PayPal API 和前端 SDK 流程保持兼容。

**Tech Stack:** Java 17、Spring Boot 3.3、Spring Data JPA、Flyway、WebClient、MockWebServer、MySQL/H2、React 19、TypeScript、Semi Design、Vitest、TronGrid、PayPal JS SDK。

---

## 文件结构与职责

| 路径 | 职责 |
| --- | --- |
| `backend/src/main/resources/db/migration/V3__add_trc20_payment_schema.sql` | 对当前 PayPal 表做兼容扩展，创建地址池、金额占用、链上交易与扫描游标表 |
| `backend/src/main/java/io/ztoken/portal/payment/config/PaymentProperties.java` | PayPal、NewAPI、TRC20、扫描间隔和初始地址池配置 |
| `backend/src/main/java/io/ztoken/portal/payment/domain/` | 渠道无关订单状态与 TRC20 持久化实体 |
| `backend/src/main/java/io/ztoken/portal/payment/provider/` | 渠道注册表以及 PayPal/TRC20 的明确渠道边界 |
| `backend/src/main/java/io/ztoken/portal/payment/trc20/` | 地址分配、TronGrid 适配、链上核验、扫描与 TxID 重试 |
| `backend/src/main/java/io/ztoken/portal/payment/api/` | 当前 Portal Session 授权的 TRC20 API 与响应 DTO |
| `frontend/src/features/payments/Trc20Checkout.tsx` | 转账地址、精确金额、倒计时、TxID 核验与状态轮询 |
| `frontend/src/features/payments/PaymentSelectionPanel.tsx` | 复用金额选择，按用户选择创建 PayPal 或 TRC20 订单 |

执行前：不要改动 `F:\WorkSpace\study\AIProject\New-api` 或 `F:\WorkSpace\study\AIProject\New-api\usdt`；后者只作为迁移参考。Portal 工作区已有与本功能无关的未提交修改，每一次提交只能暂存本计划列出的精确文件。

### Task 1: 配置、枚举与 Flyway 数据模型

**Files:**
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/test/resources/application.yml`
- Modify: `backend/src/main/java/io/ztoken/portal/payment/config/PaymentProperties.java`
- Modify: `backend/src/main/java/io/ztoken/portal/payment/domain/PaymentMethod.java`
- Modify: `backend/src/main/java/io/ztoken/portal/payment/domain/PaymentOrder.java`
- Create: `backend/src/main/java/io/ztoken/portal/payment/domain/PaymentAddress.java`
- Create: `backend/src/main/java/io/ztoken/portal/payment/domain/PaymentAmountRegistry.java`
- Create: `backend/src/main/java/io/ztoken/portal/payment/domain/ChainTransfer.java`
- Create: `backend/src/main/java/io/ztoken/portal/payment/domain/PaymentScanCursor.java`
- Create: `backend/src/main/resources/db/migration/V3__add_trc20_payment_schema.sql`
- Test: `backend/src/test/java/io/ztoken/portal/payment/config/PaymentPropertiesTest.java`
- Test: `backend/src/test/java/io/ztoken/portal/payment/domain/PaymentOrderTest.java`

- [ ] **Step 1: 编写失败的配置和订单渠道测试**

```java
@Test
void exposesTrc20DefaultsAndConfiguredAddressPool() {
    PaymentProperties properties = new PaymentProperties();

    assertThat(properties.getTrc20().getUsdtContract()).isEqualTo("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t");
    assertThat(properties.getTrc20().getConfirmationCount()).isEqualTo(20);
    assertThat(properties.getTrc20().getAddresses()).isEmpty();
}

@Test
void trc20OrderRetainsExactPaymentInstruction() {
    PaymentAddress address = new PaymentAddress("TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE", Instant.parse("2026-09-08T00:00:00Z"));
    PaymentOrder order = PaymentOrder.usdtTrc20("PO-TRON-1", 7L, 2_500L, 12_500_000L,
            address, 2_500_017L, Instant.parse("2026-09-08T00:00:00Z"), Instant.parse("2026-09-08T00:30:00Z"));

    assertThat(order.getPaymentMethod()).isEqualTo(PaymentMethod.USDT_TRC20);
    assertThat(order.getReceiveAddress()).isEqualTo(address.getAddress());
    assertThat(order.getPayableMinor()).isEqualTo(2_500_017L);
    assertThat(order.getPayableCurrency()).isEqualTo("USDT");
    assertThat(order.getPayableScale()).isEqualTo(6);
}
```

- [ ] **Step 2: 运行测试并确认因缺少 TRC20 模型失败**

Run: `mvn -f backend/pom.xml -Dskip.frontend=true -Dtest=PaymentPropertiesTest,PaymentOrderTest test`

Expected: FAIL，缺少 `getTrc20`、`USDT_TRC20` 或 `PaymentOrder.usdtTrc20`。

- [ ] **Step 3: 写入最小配置、实体字段和迁移**

将 `PaymentMethod` 改为：

```java
public enum PaymentMethod {
    PAYPAL,
    USDT_TRC20
}
```

在 `PaymentProperties` 新增 `Trc20` 内嵌配置，字段为 `usdtContract`、`confirmationCount`、`amountSuffixEnabled`、`scanFixedDelayMs`、`txidRetryFixedDelayMs`、`orderExpiryFixedDelayMs`、`TronGrid trongrid` 和 `List<String> addresses`；为每个字段提供 getter/setter，默认合约为 `TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t`、确认数为 `20`、三个间隔为 `5000`、金额尾数启用。

在主 `application.yml` 的 `payment` 下写入实际部署配置项（用户要求配置及密钥位于该文件）：

```yaml
trc20:
  usdt-contract: TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t
  confirmation-count: 20
  amount-suffix-enabled: true
  scan-fixed-delay-ms: 5000
  txid-retry-fixed-delay-ms: 5000
  order-expiry-fixed-delay-ms: 5000
  trongrid:
    base-url: https://api.trongrid.io
    api-key: ""
  addresses: []
```

在测试 YAML 使用 `base-url: http://localhost`、`api-key: test-key` 和一个 Base58 TRON 地址。创建 V3，并使用当前 Portal 复数表名：

```sql
ALTER TABLE payment_orders
    ADD COLUMN payment_address_id BIGINT NULL,
    ADD COLUMN receive_address VARCHAR(64) NULL,
    ADD COLUMN payable_minor BIGINT NULL,
    ADD COLUMN payable_currency VARCHAR(8) NULL,
    ADD COLUMN payable_scale INT NULL,
    ADD COLUMN submitted_txid VARCHAR(128) NULL,
    ADD COLUMN txid_check_count INT NOT NULL DEFAULT 0,
    ADD COLUMN last_txid_checked_at DATETIME(3) NULL,
    ADD COLUMN next_txid_check_at DATETIME(3) NULL,
    ADD COLUMN last_txid_check_result VARCHAR(64) NULL;

CREATE TABLE payment_addresses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    address VARCHAR(64) NOT NULL,
    active_order_count INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_addresses_address UNIQUE (address)
);

CREATE TABLE payment_amount_registries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    payment_address_id BIGINT NOT NULL,
    payable_minor BIGINT NOT NULL,
    payment_order_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_amount_address_amount UNIQUE (payment_address_id, payable_minor),
    CONSTRAINT uk_payment_amount_order UNIQUE (payment_order_id)
);
```

追加 `chain_transfers` 的 `(txid, log_index)` 唯一约束、`payment_scan_cursors` 的 `(provider, payment_address_id)` 唯一约束，以及 `payment_orders(payment_address_id, payable_minor)`、`payment_orders(status, next_txid_check_at)` 索引和外键。`PaymentOrder.usdtTrc20(...)` 必须固化 `receiveAddress`、`payableMinor`、`USDT` 与 `6`；PayPal 的这些可选字段保持 `null`，不得伪造 TRC20 数据。为后续任务新增 `boolean isWaitingForTrc20Payment()`（仅当方式为 `USDT_TRC20` 且状态为 `WAITING_PAYMENT` 时为真）、`void submitTxid(String, Instant)`、`void scheduleTxidRetry(Instant, String, Instant)`、`void finishTxidCheck(String, Instant)`，并让 `confirm` 和 `expireIfPast` 在 TRC20 订单首次离开待支付态时恰好一次调用地址的 `decrementActiveOrderCount()`。

- [ ] **Step 4: 运行领域和 Flyway 回归测试**

Run: `mvn -f backend/pom.xml -Dskip.frontend=true -Dtest=PaymentPropertiesTest,PaymentOrderTest,PortalApplicationTest test`

Expected: PASS，H2 正常执行 V1、V2、V3，PayPal 订单测试继续通过。

- [ ] **Step 5: 提交数据模型**

```powershell
git add -- backend/src/main/resources/application.yml backend/src/test/resources/application.yml backend/src/main/resources/db/migration/V3__add_trc20_payment_schema.sql backend/src/main/java/io/ztoken/portal/payment/config/PaymentProperties.java backend/src/main/java/io/ztoken/portal/payment/domain/PaymentMethod.java backend/src/main/java/io/ztoken/portal/payment/domain/PaymentOrder.java backend/src/main/java/io/ztoken/portal/payment/domain/PaymentAddress.java backend/src/main/java/io/ztoken/portal/payment/domain/PaymentAmountRegistry.java backend/src/main/java/io/ztoken/portal/payment/domain/ChainTransfer.java backend/src/main/java/io/ztoken/portal/payment/domain/PaymentScanCursor.java backend/src/test/java/io/ztoken/portal/payment/config/PaymentPropertiesTest.java backend/src/test/java/io/ztoken/portal/payment/domain/PaymentOrderTest.java
git commit -m "feat: 扩展Portal TRC20支付数据模型"
```

### Task 2: 地址池初始化和精确金额分配

**Files:**
- Create: `backend/src/main/java/io/ztoken/portal/payment/repository/PaymentAddressRepository.java`
- Create: `backend/src/main/java/io/ztoken/portal/payment/repository/PaymentAmountRegistryRepository.java`
- Modify: `backend/src/main/java/io/ztoken/portal/payment/repository/PaymentOrderRepository.java`
- Create: `backend/src/main/java/io/ztoken/portal/payment/trc20/Trc20AddressPoolService.java`
- Modify: `backend/src/main/java/io/ztoken/portal/payment/order/PaymentOrderService.java`
- Test: `backend/src/test/java/io/ztoken/portal/payment/trc20/Trc20AddressPoolServiceTest.java`
- Test: `backend/src/test/java/io/ztoken/portal/payment/order/PaymentOrderServiceTest.java`

- [ ] **Step 1: 编写失败的地址负载和尾数冲突测试**

```java
@Test
void createsUsdtOrderAtLeastLoadedAddressWithFirstFreeSuffix() {
    PaymentAddress leastLoaded = savedAddress("TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE", 0);
    savedAddress("TLa2f6VPqDgRE67v1736s7bJ8Ray5wYjU7", 2);

    PaymentOrder order = service.createForUser(principal(7L), new BigDecimal("2.50"), PaymentMethod.USDT_TRC20).order();

    assertThat(order.getReceiveAddress()).isEqualTo(leastLoaded.getAddress());
    assertThat(order.getPayableMinor()).isBetween(2_500_001L, 2_509_999L);
    assertThat(registries.existsByPaymentAddressIdAndPayableMinor(leastLoaded.getId(), order.getPayableMinor())).isTrue();
}

@Test
void retriesNextSuffixAfterDatabaseUniqueConflict() {
    occupy(address, 2_500_001L);

    PaymentOrder order = pool.createOrder(7L, 2_500L, 12_500_000L, now);

    assertThat(order.getPayableMinor()).isEqualTo(2_500_002L);
}
```

- [ ] **Step 2: 运行测试并确认缺少地址池服务而失败**

Run: `mvn -f backend/pom.xml -Dskip.frontend=true -Dtest=Trc20AddressPoolServiceTest,PaymentOrderServiceTest test`

Expected: FAIL，找不到 `Trc20AddressPoolService` 或带支付方式的 `createForUser`。

- [ ] **Step 3: 实现事务安全的地址池和渠道化订单创建**

在地址 repository 定义：

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select a from PaymentAddress a where a.enabled = true order by a.activeOrderCount asc, a.id asc")
List<PaymentAddress> lockEnabledOrderedByLoad();
```

`Trc20AddressPoolService.createOrder` 在单个 `@Transactional` 内锁定地址池，按 `activeOrderCount` 顺序尝试地址；基础金额 `amountUsdMinor * 10_000`，尾数范围 `1..9999`。先保存 `PaymentOrder.usdtTrc20`，再保存 `PaymentAmountRegistry`；捕获唯一约束冲突后继续下一个尾数。成功后 `address.incrementActiveOrderCount()`。没有地址或所有尾数耗尽抛出带中文日志的 `IllegalStateException`。

新增启动导入组件：遍历 `properties.getTrc20().getAddresses()`，只接受非空字符串，并调用 `findByAddress` 后 `save(new PaymentAddress(address, Instant.now()))`；重复配置不得修改现有地址的启用状态或负载。

将 `PaymentOrderService` 签名改为：

```java
public PaymentOrderView createForUser(PortalPrincipal principal, BigDecimal amount, PaymentMethod method) {
    long amountUsdMinor = amountInUsdMinor(amount);
    long quota = quotaFor(amountUsdMinor, quotaPerUsdMinor());
    return switch (method) {
        case PAYPAL -> PaymentOrderView.from(createPayPalOrder(userId, amountUsdMinor, quota, now));
        case USDT_TRC20 -> PaymentOrderView.from(trc20AddressPool.createOrder(userId, amountUsdMinor, quota, now));
    };
}
```

其中 PayPal 保持当前工厂方法和行为不变。`PaymentOrderController.create` 必须将 `request.method()` 传递到此方法。

- [ ] **Step 4: 运行测试并确认通过**

Run: `mvn -f backend/pom.xml -Dskip.frontend=true -Dtest=Trc20AddressPoolServiceTest,PaymentOrderServiceTest,PaymentOrderControllerTest test`

Expected: PASS；同一地址的并发/冲突场景不会生成重复 `payable_minor`。

- [ ] **Step 5: 提交地址池能力**

```powershell
git add -- backend/src/main/java/io/ztoken/portal/payment/repository/PaymentAddressRepository.java backend/src/main/java/io/ztoken/portal/payment/repository/PaymentAmountRegistryRepository.java backend/src/main/java/io/ztoken/portal/payment/repository/PaymentOrderRepository.java backend/src/main/java/io/ztoken/portal/payment/trc20/Trc20AddressPoolService.java backend/src/main/java/io/ztoken/portal/payment/trc20/Trc20AddressPoolInitializer.java backend/src/main/java/io/ztoken/portal/payment/order/PaymentOrderService.java backend/src/main/java/io/ztoken/portal/payment/api/PaymentOrderController.java backend/src/test/java/io/ztoken/portal/payment/trc20/Trc20AddressPoolServiceTest.java backend/src/test/java/io/ztoken/portal/payment/order/PaymentOrderServiceTest.java backend/src/test/java/io/ztoken/portal/payment/api/PaymentOrderControllerTest.java
git commit -m "feat: 添加TRC20地址池和金额识别码"
```

### Task 3: 抽取渠道注册表并保护 PayPal 回归

**Files:**
- Create: `backend/src/main/java/io/ztoken/portal/payment/provider/PaymentProvider.java`
- Create: `backend/src/main/java/io/ztoken/portal/payment/provider/PaymentProviderRegistry.java`
- Create: `backend/src/main/java/io/ztoken/portal/payment/provider/PayPalPaymentProvider.java`
- Create: `backend/src/main/java/io/ztoken/portal/payment/provider/Trc20PaymentProvider.java`
- Modify: `backend/src/main/java/io/ztoken/portal/payment/paypal/PayPalPaymentService.java`
- Test: `backend/src/test/java/io/ztoken/portal/payment/provider/PaymentProviderRegistryTest.java`
- Test: `backend/src/test/java/io/ztoken/portal/payment/paypal/PayPalPaymentServiceTest.java`

- [ ] **Step 1: 编写失败的注册表测试**

```java
@Test
void resolvesProviderByPaymentMethod() {
    PaymentProviderRegistry registry = new PaymentProviderRegistry(List.of(paypal, trc20));

    assertThat(registry.require(PaymentMethod.PAYPAL)).isSameAs(paypal);
    assertThat(registry.require(PaymentMethod.USDT_TRC20)).isSameAs(trc20);
}

@Test
void rejectsDuplicateProvidersForTheSameMethod() {
    assertThatThrownBy(() -> new PaymentProviderRegistry(List.of(paypal, anotherPaypal)))
            .isInstanceOf(IllegalStateException.class);
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `mvn -f backend/pom.xml -Dskip.frontend=true -Dtest=PaymentProviderRegistryTest test`

Expected: FAIL，`PaymentProvider` 与 `PaymentProviderRegistry` 不存在。

- [ ] **Step 3: 实现最小渠道注册边界**

```java
public interface PaymentProvider {
    PaymentMethod method();
}

public final class PaymentProviderRegistry {
    private final Map<PaymentMethod, PaymentProvider> providers;
    public PaymentProvider require(PaymentMethod method) {
        return Optional.ofNullable(providers.get(method))
                .orElseThrow(() -> new IllegalStateException("未注册的支付渠道：" + method));
    }
}
```

`PayPalPaymentProvider` 包装现有 `PayPalPaymentService`，只声明 `PAYPAL`；`Trc20PaymentProvider` 包装 `Trc20AddressPoolService`、核验服务和扫描服务，只声明 `USDT_TRC20`。不要让 Controller 使用 `if (method == ...)` 来判断支付渠道。保留现有 PayPal Create、Capture、Webhook URL 及响应，现有 PayPal 测试不得修改为 mock 新注册表行为。

- [ ] **Step 4: 运行注册表和 PayPal 回归**

Run: `mvn -f backend/pom.xml -Dskip.frontend=true -Dtest=PaymentProviderRegistryTest,PayPalPaymentServiceTest,PayPalWebhookServiceTest,PayPalPaymentControllerTest test`

Expected: PASS。

- [ ] **Step 5: 提交渠道边界**

```powershell
git add -- backend/src/main/java/io/ztoken/portal/payment/provider/PaymentProvider.java backend/src/main/java/io/ztoken/portal/payment/provider/PaymentProviderRegistry.java backend/src/main/java/io/ztoken/portal/payment/provider/PayPalPaymentProvider.java backend/src/main/java/io/ztoken/portal/payment/provider/Trc20PaymentProvider.java backend/src/main/java/io/ztoken/portal/payment/paypal/PayPalPaymentService.java backend/src/test/java/io/ztoken/portal/payment/provider/PaymentProviderRegistryTest.java backend/src/test/java/io/ztoken/portal/payment/paypal/PayPalPaymentServiceTest.java
git commit -m "refactor: 建立Portal支付渠道注册边界"
```

### Task 4: TronGrid 查询和严格链上核验

**Files:**
- Create: `backend/src/main/java/io/ztoken/portal/payment/trc20/ObservedTransfer.java`
- Create: `backend/src/main/java/io/ztoken/portal/payment/trc20/TransferQueryResult.java`
- Create: `backend/src/main/java/io/ztoken/portal/payment/trc20/TronGridTransferClient.java`
- Create: `backend/src/main/java/io/ztoken/portal/payment/trc20/HttpTronGridTransferClient.java`
- Create: `backend/src/main/java/io/ztoken/portal/payment/trc20/TransferVerificationService.java`
- Create: `backend/src/main/java/io/ztoken/portal/payment/repository/ChainTransferRepository.java`
- Test: `backend/src/test/java/io/ztoken/portal/payment/trc20/HttpTronGridTransferClientTest.java`
- Test: `backend/src/test/java/io/ztoken/portal/payment/trc20/TransferVerificationServiceTest.java`

- [ ] **Step 1: 编写失败的合约、金额、确认数和重复交易测试**

```java
@Test
void confirmsOnlyExactUsdtTransferWithTwentyConfirmations() {
    VerificationResult result = verifier.verify(order, transfer("tx-1", 0L, USDT_CONTRACT,
            order.getReceiveAddress(), order.getPayableMinor(), 20));

    assertThat(result).isEqualTo(VerificationResult.CONFIRMED);
    assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.CONFIRMED);
}

@Test
void keepsOrderWaitingWhenTransferHasNineteenConfirmations() {
    assertThat(verifier.verify(order, transfer("tx-2", 0L, USDT_CONTRACT,
            order.getReceiveAddress(), order.getPayableMinor(), 19)))
            .isEqualTo(VerificationResult.PENDING_CONFIRMATION);
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `mvn -f backend/pom.xml -Dskip.frontend=true -Dtest=HttpTronGridTransferClientTest,TransferVerificationServiceTest test`

Expected: FAIL，TRC20 查询/核验类不存在。

- [ ] **Step 3: 迁移并适配 TronGrid 客户端与核验器**

从旧项目迁移 `TronGridTransferProvider` 的无密钥泄漏查询模式：每次调用 `/v1/transactions/{txid}/events`，读取当前区块高度，解析 `Transfer` event，并且只在设置时附加 `TRON-PRO-API-KEY`。`ObservedTransfer` 至少包括 `txid`、`logIndex`、`contractAddress`、`toAddress`、`amountMinor`、`blockNumber`、`blockTime`、`executionSuccess`、`confirmations`。

核验顺序必须固定为：

```java
if (chainTransfers.existsByTxidAndLogIndex(transfer.txid(), transfer.logIndex())) return DUPLICATE;
if (!transfer.executionSuccess() || !properties.getTrc20().getUsdtContract().equals(transfer.contractAddress())) return UNMATCHED;
if (!order.isWaitingForTrc20Payment() || !order.getReceiveAddress().equals(transfer.toAddress())
        || order.getPayableMinor() != transfer.amountMinor()
        || transfer.blockTime().isBefore(order.getCreatedAt()) || transfer.blockTime().isAfter(order.getExpiresAt())) return UNMATCHED;
if (transfer.confirmations() < properties.getTrc20().getConfirmationCount()) return PENDING_CONFIRMATION;
if (!order.confirm(Instant.now())) return DUPLICATE;
chainTransfers.save(ChainTransfer.from(transfer, order, Instant.now()));
eventPublisher.publishEvent(new PaymentConfirmedEvent(order.getOrderNo()));
return CONFIRMED;
```

所有分支输出中文业务日志，但不得打印配置密钥。

- [ ] **Step 4: 运行核验测试和全量支付领域回归**

Run: `mvn -f backend/pom.xml -Dskip.frontend=true -Dtest=HttpTronGridTransferClientTest,TransferVerificationServiceTest,PaymentCreditServiceTest test`

Expected: PASS；19 确认、错误合约、错误地址、错误金额、过期转账和重复 `(txid, logIndex)` 均不能进入入账。

- [ ] **Step 5: 提交链上核验能力**

```powershell
git add -- backend/src/main/java/io/ztoken/portal/payment/trc20 backend/src/main/java/io/ztoken/portal/payment/repository/ChainTransferRepository.java backend/src/test/java/io/ztoken/portal/payment/trc20
git commit -m "feat: 添加TRC20链上转账核验"
```

### Task 5: 自动扫描、TxID 即时核验、重试与过期回收

**Files:**
- Create: `backend/src/main/java/io/ztoken/portal/payment/trc20/Trc20PaymentScanner.java`
- Create: `backend/src/main/java/io/ztoken/portal/payment/trc20/TxidVerificationService.java`
- Create: `backend/src/main/java/io/ztoken/portal/payment/trc20/Trc20OrderExpiryService.java`
- Create: `backend/src/main/java/io/ztoken/portal/payment/repository/PaymentScanCursorRepository.java`
- Modify: `backend/src/main/java/io/ztoken/portal/payment/repository/PaymentOrderRepository.java`
- Test: `backend/src/test/java/io/ztoken/portal/payment/trc20/TxidVerificationServiceTest.java`
- Test: `backend/src/test/java/io/ztoken/portal/payment/trc20/Trc20PaymentScannerTest.java`
- Test: `backend/src/test/java/io/ztoken/portal/payment/trc20/Trc20OrderExpiryServiceTest.java`

- [ ] **Step 1: 编写失败的双通道和过期回收测试**

```java
@Test
void submittedTxidSchedulesRetryUntilIndexedTransferReachesConfirmationThreshold() {
    when(client.findByTxid("tx-pending")).thenReturn(TransferQueryResult.success(List.of(pendingTransfer)));

    TxidVerificationView result = service.submit(owner, order.getOrderNo(), "tx-pending");

    assertThat(result.queryStatus()).isEqualTo("PENDING_CONFIRMATION");
    assertThat(order.getNextTxidCheckAt()).isAfter(now);
}

@Test
void expiryDecrementsAddressLoadOnceAndStopsTxidRetries() {
    order.expireIfPast(now);

    expiryService.expireDueOrders();

    assertThat(address.getActiveOrderCount()).isZero();
    assertThat(order.getNextTxidCheckAt()).isNull();
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `mvn -f backend/pom.xml -Dskip.frontend=true -Dtest=TxidVerificationServiceTest,Trc20PaymentScannerTest,Trc20OrderExpiryServiceTest test`

Expected: FAIL，三个服务及其响应模型不存在。

- [ ] **Step 3: 实现自动扫描与 TxID 双通道**

`Trc20PaymentScanner` 使用 `@Scheduled(fixedDelayString = "${payment.trc20.scan-fixed-delay-ms}")` 读取启用地址，按每个 `PaymentScanCursor` 查询地址收款事件，逐条调用 Task 4 的 `TransferVerificationService`。扫描游标只在成功读取并持久化可见事件后前移；网络失败保留旧游标。

`TxidVerificationService.submit(PortalPrincipal principal, String orderNo, String txid)` 必须先验证订单归属和 `USDT_TRC20` 渠道，再保存 TxID 并立即调用客户端。`NOT_INDEXED`、`QUERY_FAILED`、`PENDING_CONFIRMATION` 按 `{5,10,15,30,60}` 秒安排下一次查询；`CONFIRMED`、`UNMATCHED`、`DUPLICATE`、`EXPIRED` 终止重试。不得因重试次数耗尽将合法待确认交易标为已支付或失败。

`Trc20OrderExpiryService` 每 5 秒锁定并处理到期的 `WAITING_PAYMENT` TRC20 订单；状态转到 `EXPIRED` 时仅递减一次地址负载、清空 `nextTxidCheckAt`。金额注册和链上交易绝不删除。

- [ ] **Step 4: 运行行为和并发回归测试**

Run: `mvn -f backend/pom.xml -Dskip.frontend=true -Dtest=TxidVerificationServiceTest,Trc20PaymentScannerTest,Trc20OrderExpiryServiceTest,PaymentCreditListenerTest test`

Expected: PASS；扫描与 TxID 同时确认时，`PaymentCreditService` 只发起一次 NewAPI 入账。

- [ ] **Step 5: 提交双通道确认**

```powershell
git add -- backend/src/main/java/io/ztoken/portal/payment/trc20/Trc20PaymentScanner.java backend/src/main/java/io/ztoken/portal/payment/trc20/TxidVerificationService.java backend/src/main/java/io/ztoken/portal/payment/trc20/Trc20OrderExpiryService.java backend/src/main/java/io/ztoken/portal/payment/repository/PaymentScanCursorRepository.java backend/src/main/java/io/ztoken/portal/payment/repository/PaymentOrderRepository.java backend/src/test/java/io/ztoken/portal/payment/trc20/TxidVerificationServiceTest.java backend/src/test/java/io/ztoken/portal/payment/trc20/Trc20PaymentScannerTest.java backend/src/test/java/io/ztoken/portal/payment/trc20/Trc20OrderExpiryServiceTest.java
git commit -m "feat: 添加TRC20自动扫描和TxID核验"
```

### Task 6: TRC20 HTTP API、订单视图与授权

**Files:**
- Create: `backend/src/main/java/io/ztoken/portal/payment/api/SubmitTrc20TxidRequest.java`
- Create: `backend/src/main/java/io/ztoken/portal/payment/api/Trc20PaymentController.java`
- Create: `backend/src/main/java/io/ztoken/portal/payment/api/Trc20PaymentInstructionResponse.java`
- Create: `backend/src/main/java/io/ztoken/portal/payment/api/TxidVerificationResponse.java`
- Modify: `backend/src/main/java/io/ztoken/portal/payment/order/PaymentOrderView.java`
- Modify: `backend/src/main/java/io/ztoken/portal/payment/api/PaymentOrderResponse.java`
- Test: `backend/src/test/java/io/ztoken/portal/payment/api/Trc20PaymentControllerTest.java`
- Test: `backend/src/test/java/io/ztoken/portal/payment/api/PaymentOrderControllerTest.java`

- [ ] **Step 1: 编写失败的控制器授权测试**

```java
@Test
void returnsTrc20InstructionOnlyForTheOrderOwner() throws Exception {
    mockMvc.perform(get("/api/payments/orders/PO-TRON-1/trc20/status").cookie(ownerSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.receiveAddress").value("TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE"))
            .andExpect(jsonPath("$.payableAmount").value("2.500017"));
}

@Test
void rejectsTxidSubmissionFromAnotherPortalUser() throws Exception {
    mockMvc.perform(post("/api/payments/orders/PO-TRON-1/trc20/txid")
                    .cookie(otherUserSession).contentType(APPLICATION_JSON).content("{\"txid\":\"tx-1\"}"))
            .andExpect(status().isNotFound());
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `mvn -f backend/pom.xml -Dskip.frontend=true -Dtest=Trc20PaymentControllerTest,PaymentOrderControllerTest test`

Expected: FAIL，TRC20 路由不存在或响应没有支付指引字段。

- [ ] **Step 3: 实现最小会话授权 API**

提供如下端点并在每个端点调用 `sessions.require(sessionId)`：

```java
@GetMapping("/{orderNo}/trc20/status")
public ResponseEntity<Trc20PaymentInstructionResponse> status(...)

@PostMapping("/{orderNo}/trc20/txid")
public ResponseEntity<TxidVerificationResponse> submitTxid(...)
```

响应中的 `payableAmount` 由 `payableMinor / 1_000_000` 用 `BigDecimal` 固定六位格式化；不得经由 `double`。`PaymentOrderView` 和 `PaymentOrderResponse` 统一增加 `method`、`receiveAddress`、`payableAmount`、`payableCurrency`、`txidCheckResult`、`nextTxidCheckAt`，PayPal 的 TRC20 专有字段是 `null`。任何非所有者和非 TRC20 订单均返回现有的 `PaymentApiException.orderNotFound()`。

- [ ] **Step 4: 运行 API、PayPal 与额度入账回归**

Run: `mvn -f backend/pom.xml -Dskip.frontend=true -Dtest=Trc20PaymentControllerTest,PaymentOrderControllerTest,PayPalPaymentControllerTest,PaymentCreditServiceTest test`

Expected: PASS；浏览器不发送额度、用户 ID、地址或支付金额以外的可信支付字段。

- [ ] **Step 5: 提交 API**

```powershell
git add -- backend/src/main/java/io/ztoken/portal/payment/api/SubmitTrc20TxidRequest.java backend/src/main/java/io/ztoken/portal/payment/api/Trc20PaymentController.java backend/src/main/java/io/ztoken/portal/payment/api/Trc20PaymentInstructionResponse.java backend/src/main/java/io/ztoken/portal/payment/api/TxidVerificationResponse.java backend/src/main/java/io/ztoken/portal/payment/order/PaymentOrderView.java backend/src/main/java/io/ztoken/portal/payment/api/PaymentOrderResponse.java backend/src/test/java/io/ztoken/portal/payment/api/Trc20PaymentControllerTest.java backend/src/test/java/io/ztoken/portal/payment/api/PaymentOrderControllerTest.java
git commit -m "feat: 提供TRC20支付订单接口"
```

### Task 7: 前端渠道选择和 TRC20 转账工作区

**Files:**
- Modify: `frontend/src/api/portal.ts`
- Modify: `frontend/src/features/payments/PaymentSelectionPanel.tsx`
- Create: `frontend/src/features/payments/Trc20Checkout.tsx`
- Modify: `frontend/src/features/payments/PurchasePage.tsx`
- Modify: `frontend/src/features/payments/RechargePage.tsx`
- Modify: `frontend/src/i18n/locales/en.json`
- Modify: `frontend/src/i18n/locales/zh-CN.json`
- Modify: `frontend/src/styles.css`
- Test: `frontend/src/features/payments/__tests__/trc20-checkout.test.tsx`
- Test: `frontend/src/features/payments/__tests__/purchase-page.test.tsx`

- [ ] **Step 1: 编写失败的前端渠道与 TxID 测试**

```tsx
it('creates an USDT_TRC20 order without sending quota, user id, or recipient address', async () => {
  render(<PurchasePage />)
  await user.click(screen.getByRole('button', { name: 'Pay with TRC20 USDT' }))

  expect(fetchMock).toHaveBeenCalledWith('/api/payments/orders', expect.objectContaining({
    method: 'POST', credentials: 'include', body: expect.stringContaining('"method":"USDT_TRC20"'),
  }))
  expect(String(fetchMock.mock.calls[0][1].body)).not.toMatch(/quota|userId|receiveAddress/)
})

it('copies the exact server payment amount and submits a transaction id', async () => {
  render(<Trc20Checkout order={trc20Order} onCompleted={vi.fn()} />)
  await user.click(screen.getByRole('button', { name: 'Copy amount' }))
  await user.type(screen.getByLabelText('Transaction ID'), 'tx-1')
  await user.click(screen.getByRole('button', { name: 'Verify transaction' }))

  expect(fetchMock).toHaveBeenLastCalledWith('/api/payments/orders/PO-TRON-1/trc20/txid', expect.objectContaining({ method: 'POST' }))
})
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `npm test -- --run src/features/payments/__tests__/trc20-checkout.test.tsx src/features/payments/__tests__/purchase-page.test.tsx`

Expected: FAIL，未提供 TRC20 卡片或 `Trc20Checkout`。

- [ ] **Step 3: 实现受服务端订单驱动的前端**

在 `portal.ts` 扩展：

```ts
export type PaymentMethod = 'PAYPAL' | 'USDT_TRC20'
export interface PaymentOrder { method: PaymentMethod; receiveAddress: string | null; payableAmount: string | null; payableCurrency: string | null; txidCheckResult: string | null; nextTxidCheckAt: string | null /* 保留既有字段 */ }
export function submitTrc20Txid(orderNo: string, txid: string): Promise<TxidVerification> { /* POST */ }
export function getTrc20PaymentStatus(orderNo: string): Promise<Trc20PaymentInstruction> { /* GET */ }
```

将 `PaymentSelectionPanel` 的 `handlePayPal` 统一为 `handleMethod(method: PaymentMethod)`，使用 `createPaymentOrder` API，而不是直接 `fetch`。PayPal 卡片的按钮与现有文本保持不变；TRC20 卡片显示 `Tron.png`、TRC20 网络和可点击的 `Pay with TRC20 USDT`。

`Trc20Checkout` 只显示服务器返回的精确金额、地址、过期倒计时、复制按钮和 TxID 输入。每 5 秒调用 `getPaymentOrder(orderNo)`；`PAID` 显示额度已到账，`CONFIRMED/CREDITING` 显示处理中，`EXPIRED` 停止轮询，`CREDIT_FAILED/CREDIT_UNKNOWN` 显示人工处理提示。复制使用 `navigator.clipboard.writeText`，并为不可用浏览器显示失败提示。不得把链上确认结果视作客户端成功。

在购买页和控制台充值页根据 `order.method` 渲染 `PayPalCheckout` 或 `Trc20Checkout`。新增中英文翻译键及窄屏样式，确保长地址以等宽字体换行而不是裁断金额。

- [ ] **Step 4: 运行付款前端测试与构建**

Run: `npm test -- --run src/features/payments/__tests__/trc20-checkout.test.tsx src/features/payments/__tests__/purchase-page.test.tsx src/features/payments/__tests__/recharge-page.test.tsx src/features/payments/__tests__/paypal-checkout.test.tsx`

Expected: PASS。

Run: `npm run build`

Expected: exit code 0。

- [ ] **Step 5: 提交支付界面**

```powershell
git add -- frontend/src/api/portal.ts frontend/src/features/payments/PaymentSelectionPanel.tsx frontend/src/features/payments/Trc20Checkout.tsx frontend/src/features/payments/PurchasePage.tsx frontend/src/features/payments/RechargePage.tsx frontend/src/i18n/locales/en.json frontend/src/i18n/locales/zh-CN.json frontend/src/styles.css frontend/src/features/payments/__tests__/trc20-checkout.test.tsx frontend/src/features/payments/__tests__/purchase-page.test.tsx
git commit -m "feat: 增加Portal TRC20充值界面"
```

### Task 8: 订单历史展示、全量验证与部署说明

**Files:**
- Modify: `frontend/src/features/orders/OrdersPage.tsx`
- Modify: `frontend/src/features/orders/__tests__/orders-page.test.tsx`
- Modify: `README.md`
- Test: `backend/src/test/java/io/ztoken/portal/payment/repository/PaymentOrderRepositoryTest.java`

- [ ] **Step 1: 编写失败的订单方式展示测试**

```tsx
it('renders a Tron badge and the USDT payment amount for a TRC20 order', async () => {
  render(<OrdersPage />)

  expect(await screen.findByText('TRC20 USDT')).toBeVisible()
  expect(screen.getByText('2.500017 USDT')).toBeVisible()
})
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `npm test -- --run src/features/orders/__tests__/orders-page.test.tsx`

Expected: FAIL，当前订单表只显示 PayPal 图标或原始枚举。

- [ ] **Step 3: 完成订单页和部署文档**

订单表按 `method` 显示 PayPal logo 或 `Tron.png` 与 `TRC20 USDT`；TRC20 行显示 `payableAmount payableCurrency`，PayPal 行继续使用 USD 金额。状态色复用现有映射，不能新增前端自定义状态机。

README 增加以下可执行配置说明：PayPal 的 `mode/client-id/client-secret/webhook-id`、TRC20 的 USDT 合约、TronGrid base URL/API Key、确认数 20、扫描间隔、地址池 `payment.trc20.addresses`、NewAPI credit token；说明修改地址池后重启服务以导入新地址，并说明密钥虽然按本期要求存入 YAML，生产部署仍应限制文件读取权限且不纳入公开仓库。

- [ ] **Step 4: 运行完整验证**

Run: `mvn -f backend/pom.xml -Dskip.frontend=true test`

Expected: PASS。

Run: `npm test`

Expected: PASS。

Run: `npm run build`

Expected: exit code 0。

- [ ] **Step 5: 提交验收与文档**

```powershell
git add -- frontend/src/features/orders/OrdersPage.tsx frontend/src/features/orders/__tests__/orders-page.test.tsx README.md backend/src/test/java/io/ztoken/portal/payment/repository/PaymentOrderRepositoryTest.java
git commit -m "docs: 补充Portal双渠道支付部署说明"
```

## 计划自检

- 规格覆盖：Task 1 覆盖配置、迁移、订单字段；Task 2 覆盖地址池和尾数；Task 3 保证扩展边界与 PayPal 兼容；Task 4-5 覆盖 TronGrid、20 确认、扫描与 TxID 双通道；Task 6 覆盖会话授权；Task 7-8 覆盖前端、订单历史、测试与部署。
- 无未完成项：地址池没有虚构生产地址，配置显式要求部署者提供受控地址与密钥；所有业务实现步骤均给出确切类型、方法、端点和验证命令。
- 类型一致性：所有层使用 `PaymentMethod.USDT_TRC20`、`payableMinor`（`long`）、`payableAmount`（格式化字符串）、`PaymentConfirmedEvent` 和现有 `PaymentOrderStatus`。
