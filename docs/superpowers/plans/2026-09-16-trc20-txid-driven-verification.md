# TRC20 TxID 驱动核验 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 使 TRC20-USDT 链上查询只由用户提交的 TxID 触发，并仅重试已提交且尚未完成核验的订单。

**Architecture:** 删除地址池全量扫描调度及其渠道依赖；保留 `TxidVerificationService` 的即时查询、退避重试和过期回收。订单未提交 TxID 时不调用 TronGrid，用户可从订单管理补交 TxID 后进入既有核验流程。

**Tech Stack:** Spring Boot 3、Spring Data JPA、Spring Scheduler、JUnit 5、Mockito。

---

## 文件结构

- `backend/src/main/java/io/ztoken/portal/payment/trc20/Trc20PaymentScanner.java`：删除，不再扫描收款地址池。
- `backend/src/main/java/io/ztoken/portal/payment/provider/Trc20PaymentProvider.java`：移除已删除扫描器的构造器依赖。
- `backend/src/main/java/io/ztoken/portal/payment/config/PaymentProperties.java`：移除无调用方的地址扫描间隔配置。
- `backend/src/main/resources/application.yml`：移除本地配置中的地址扫描间隔；此文件中的 API Key 与地址池仍保持用户本地配置，禁止提交。
- `backend/src/test/java/io/ztoken/portal/payment/trc20/Trc20PaymentScannerTest.java`：删除，扫描器已不属于系统行为。
- `backend/src/test/java/io/ztoken/portal/payment/trc20/TxidVerificationServiceTest.java`：验证未提交 TxID 的订单不会成为定时链上查询对象。

### Task 1: 用例固定 TxID 驱动边界

**Files:**

- Modify: `backend/src/test/java/io/ztoken/portal/payment/trc20/TxidVerificationServiceTest.java`
- Modify: `backend/src/main/java/io/ztoken/portal/payment/trc20/Trc20PaymentScanner.java`

- [ ] **Step 1: 写失败测试，证明无已提交 TxID 时不会调用 TronGrid**

在 `TxidVerificationServiceTest` 中 mock `orders.findDueTxidChecks(Instant)` 返回空列表，调用 `retryDueTxids()`，断言：

```java
verify(orders).findDueTxidChecks(any(Instant.class));
verifyNoInteractions(client);
```

- [ ] **Step 2: 确认测试失败原因是旧地址池扫描器仍存在**

Run: `mvn '-Dskip.frontend=true' '-Dtest=Trc20PaymentScannerTest,TxidVerificationServiceTest' test`

Expected: 旧扫描器测试仍能构造并调用 `findByReceiveAddress`，表明系统仍保留与参考项目不一致的地址池扫描行为。

- [ ] **Step 3: 删除地址池扫描器及其测试**

删除 `Trc20PaymentScanner.java` 和 `Trc20PaymentScannerTest.java`，不替换为任何按地址或按金额的后台查询。保留 `TxidVerificationService` 的 `submit`、`retryDueTxids`、`expireDueOrders`。

- [ ] **Step 4: 验证 TxID 定时重查边界**

Run: `mvn '-Dskip.frontend=true' '-Dtest=TxidVerificationServiceTest' test`

Expected: 未提交 TxID 时只查询订单仓库且不调用 `client.findByTxid` 或 `client.findByReceiveAddress`；提交 TxID 后既有即时查询与退避重试测试继续通过。

### Task 2: 移除调度依赖与死配置

**Files:**

- Modify: `backend/src/main/java/io/ztoken/portal/payment/provider/Trc20PaymentProvider.java`
- Modify: `backend/src/main/java/io/ztoken/portal/payment/config/PaymentProperties.java`
- Modify: `backend/src/main/resources/application.yml`

- [ ] **Step 1: 移除扫描器注入**

将渠道构造器收敛为：

```java
public Trc20PaymentProvider(Trc20AddressPoolService addressPool, TxidVerificationService txids) {
    this.addressPool = addressPool;
    this.txids = txids;
}
```

保留 `TxidVerificationService` 依赖以确保定时任务和即时核验组件可被 Spring 管理；不得重新引入地址查询。

- [ ] **Step 2: 移除地址扫描周期配置**

删除 `PaymentProperties.Trc20.scanFixedDelayMs` 的字段、getter 和 setter，并从 `application.yml` 删除 `scan-fixed-delay-ms`。不修改用户的 API Key、链上合约或收款地址列表。

- [ ] **Step 3: 编译并验证没有地址扫描调用方**

Run: `rg -n "Trc20PaymentScanner|findByReceiveAddress|scan-fixed-delay-ms" backend/src/main backend/src/test`

Expected: 仅 `TronGridTransferClient` 的接口方法可保留，业务服务、调度和测试不再调用 `findByReceiveAddress`；没有 `Trc20PaymentScanner` 或 `scan-fixed-delay-ms`。

- [ ] **Step 4: 运行针对性回归**

Run: `mvn '-Dskip.frontend=true' '-Dtest=TxidVerificationServiceTest,Trc20PaymentControllerTest,HttpTronGridTransferClientTest' test`

Expected: 提交 TxID 仍即时核验，未索引交易仍安排退避重试，用户归属校验不变，TronGrid 按 TxID 查询保持可用。

### Task 3: 全量验证与交付

**Files:**

- Modify: `docs/superpowers/plans/2026-09-16-trc20-txid-driven-verification.md`（仅勾选实际完成步骤）

- [ ] **Step 1: 后端全量回归**

Run: `mvn '-Dskip.frontend=true' test`

Expected: Surefire 报告无失败或错误。

- [ ] **Step 2: 检查提交范围**

Run: `git diff --check; git status --short`

Expected: 提交仅包含 Java 代码、对应测试删除/新增和计划文档；`backend/src/main/resources/application.yml` 保持未暂存的本地配置。

- [ ] **Step 3: 提交功能**

Run:

```powershell
git add -- backend/src/main/java/io/ztoken/portal/payment/provider/Trc20PaymentProvider.java backend/src/main/java/io/ztoken/portal/payment/config/PaymentProperties.java backend/src/main/java/io/ztoken/portal/payment/trc20/Trc20PaymentScanner.java backend/src/test/java/io/ztoken/portal/payment/trc20/Trc20PaymentScannerTest.java backend/src/test/java/io/ztoken/portal/payment/trc20/TxidVerificationServiceTest.java docs/superpowers/plans/2026-09-16-trc20-txid-driven-verification.md
git commit -m "改为TRC20交易哈希驱动核验"
```

Expected: 代码提交不包含 `backend/src/main/resources/application.yml`。
