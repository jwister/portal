# 默认 API Key 自动创建实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在用户通过普通注册（账号密码/邮箱）或第三方 OAuth（GitHub / Google）首次登录后，全自动为该用户创建名为 `default`、无限额度且永不过期的 API Key，并提供登录自愈与异常容错保障。

**Architecture:** 新增专门的 `DefaultTokenService`，封装“创建默认令牌”的所有逻辑与异常保护。普通注册成功后使用注册账号密码临时登录并创建令牌后登出；第三方 OAuth 登录与常规登录时通过凭证检查若无令牌则自动补建，且异常绝不阻断认证主流程。

**Tech Stack:** Java 17, Spring Boot 3, Spring WebClient, JUnit 5, Mockito, MockWebServer.

---

### Task 1: 创建 `DefaultTokenService` 及其单元测试

**Files:**
- Create: `portal/backend/src/main/java/io/ztoken/portal/auth/DefaultTokenService.java`
- Create: `portal/backend/src/test/java/io/ztoken/portal/auth/DefaultTokenServiceTest.java`

- [x] **Step 1: 编写测试用例 `DefaultTokenServiceTest`**

编写包含三种场景的单元测试：普通注册时自动创建并注销临时会话、已有令牌时的幂等跳过、发生异常时的非阻塞捕获。

```java
package io.ztoken.portal.auth;

import io.ztoken.portal.console.TokenList;
import io.ztoken.portal.console.TokenWriteRequest;
import io.ztoken.portal.newapi.NewApiClient;
import io.ztoken.portal.newapi.NewApiException;
import io.ztoken.portal.newapi.NewApiIdentity;
import io.ztoken.portal.newapi.NewApiLogin;
import io.ztoken.portal.session.PortalPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultTokenServiceTest {

    @Mock
    private NewApiClient newApiClient;

    private DefaultTokenService service;

    @BeforeEach
    void setUp() {
        service = new DefaultTokenService(newApiClient);
    }

    @Test
    void initializeDefaultTokenOnRegisterCreatesTokenAndLogsOut() {
        NewApiIdentity identity = new NewApiIdentity(10L, "alice");
        NewApiLogin login = new NewApiLogin(identity, "test-access-token", "test-refresh-token", "test-sid",
                Instant.now().plusSeconds(3600));

        when(newApiClient.login("alice", "secret123")).thenReturn(login);
        when(newApiClient.getTokens(any(PortalPrincipal.class), eq(1), eq(1)))
                .thenReturn(new TokenList(1, 1, 0L, List.of()));

        service.initializeDefaultTokenOnRegister("alice", "secret123");

        ArgumentCaptor<TokenWriteRequest> captor = ArgumentCaptor.forClass(TokenWriteRequest.class);
        verify(newApiClient).createToken(any(PortalPrincipal.class), captor.capture());
        TokenWriteRequest request = captor.getValue();
        assertThat(request.name()).isEqualTo("default");
        assertThat(request.unlimited()).isTrue();
        assertThat(request.expiredTime()).isEqualTo(-1L);
        assertThat(request.group()).isEqualTo("default");

        verify(newApiClient).logout(any(PortalPrincipal.class));
    }

    @Test
    void ensureDefaultTokenExistsSkipsWhenTokensAlreadyExist() {
        PortalPrincipal principal = new PortalPrincipal(10L, "alice", "test-token");
        when(newApiClient.getTokens(principal, 1, 1))
                .thenReturn(new TokenList(1, 1, 1L, List.of()));

        service.ensureDefaultTokenExists(principal);

        verify(newApiClient, never()).createToken(any(), any());
    }

    @Test
    void initializeDefaultTokenOnRegisterSuppressesExceptionsWithoutFailing() {
        when(newApiClient.login("alice", "secret123"))
                .thenThrow(new NewApiException("NewAPI 连接超时"));

        assertThatCode(() -> service.initializeDefaultTokenOnRegister("alice", "secret123"))
                .doesNotThrowAnyException();

        verify(newApiClient, never()).createToken(any(), any());
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

执行命令：
```powershell
mvn -f portal/backend/pom.xml test -Dtest=DefaultTokenServiceTest
```
预期结果：编译失败（`DefaultTokenService` 尚不存在）。

- [ ] **Step 3: 编写 `DefaultTokenService` 实现代码**

```java
package io.ztoken.portal.auth;

import io.ztoken.portal.console.TokenList;
import io.ztoken.portal.console.TokenWriteRequest;
import io.ztoken.portal.newapi.NewApiClient;
import io.ztoken.portal.newapi.NewApiLogin;
import io.ztoken.portal.session.PortalPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 负责新用户初始化及会话接入时默认 API Key（令牌）的自动创建与幂等保障。
 */
@Service
public class DefaultTokenService {

    private static final Logger log = LoggerFactory.getLogger(DefaultTokenService.class);

    public static final String DEFAULT_TOKEN_NAME = "default";
    public static final String DEFAULT_TOKEN_GROUP = "default";

    private final NewApiClient newApiClient;

    public DefaultTokenService(NewApiClient newApiClient) {
        this.newApiClient = newApiClient;
    }

    /**
     * 在新用户通过账号密码或邮箱注册成功后，使用注册凭据临时登录并自动创建名为 default 的默认令牌。
     * 无论创建成功或失败，均在 finally 中安全注销临时会话，异常绝不抛出打断注册主流程。
     *
     * @param username 用户名
     * @param password 注册密码
     */
    public void initializeDefaultTokenOnRegister(String username, String password) {
        PortalPrincipal principal = null;
        try {
            log.info("开始为新注册用户 {} 初始化默认令牌", username);
            NewApiLogin login = newApiClient.login(username, password);
            principal = toPrincipal(login);
            ensureDefaultTokenExists(principal);
        } catch (Exception exception) {
            log.warn("为新注册用户 {} 自动初始化默认令牌失败: {}", username, exception.getMessage());
        } finally {
            if (principal != null) {
                try {
                    newApiClient.logout(principal);
                } catch (Exception ignored) {
                    // 登出仅为回收临时上游凭据，异常不予处理
                }
            }
        }
    }

    /**
     * 检查并确保该用户存在默认 API Key。如果令牌总数为 0，则自动为其创建名为 default 的无限额度令牌。
     *
     * @param principal 当前认证用户
     */
    public void ensureDefaultTokenExists(PortalPrincipal principal) {
        if (principal == null) {
            return;
        }
        try {
            TokenList tokens = newApiClient.getTokens(principal, 1, 1);
            if (tokens != null && tokens.total() > 0) {
                log.debug("用户 {} 已存在 {} 个令牌，跳过默认令牌创建", principal.username(), tokens.total());
                return;
            }
            TokenWriteRequest request = new TokenWriteRequest(
                    DEFAULT_TOKEN_NAME,
                    true,      // 无限额度
                    0L,        // 额度值（在无限额度模式下无效）
                    -1L,       // 永不过期
                    DEFAULT_TOKEN_GROUP
            );
            newApiClient.createToken(principal, request);
            log.info("已成功为用户 {} 自动创建默认 API Key: {}", principal.username(), DEFAULT_TOKEN_NAME);
        } catch (Exception exception) {
            log.warn("为用户 {} 检查或创建默认令牌失败: {}", principal.username(), exception.getMessage());
        }
    }

    /**
     * 将 NewApiLogin 登录信息转换为请求级别的 PortalPrincipal 凭证
     *
     * @param login 上游登录响应
     * @return 临时用户主体凭据
     */
    public PortalPrincipal toPrincipal(NewApiLogin login) {
        return new PortalPrincipal(
                "temp-session-" + login.identity().userId(),
                login.identity().userId(),
                login.identity().username(),
                login.accessToken(),
                login.refreshToken(),
                login.sessionId(),
                login.accessExpiresAt()
        );
    }
}
```

- [x] **Step 2: 运行测试验证失败**

- [x] **Step 3: 编写 `DefaultTokenService` 实现代码**

- [x] **Step 4: 运行测试验证通过**

执行命令：
```powershell
mvn -f portal/backend/pom.xml test -Dtest=DefaultTokenServiceTest
```
预期结果：`BUILD SUCCESS`，3 个测试全部 PASS。

- [x] **Step 5: 提交 Task 1 代码**

```powershell
git add portal/backend/src/main/java/io/ztoken/portal/auth/DefaultTokenService.java portal/backend/src/test/java/io/ztoken/portal/auth/DefaultTokenServiceTest.java
git commit -m "feat(auth): 新增 DefaultTokenService 用于用户注册与首次接入时自动创建默认 API Key"
```

---

### Task 2: 在 `AuthController` 中集成 `DefaultTokenService`

**Files:**
- Modify: `portal/backend/src/main/java/io/ztoken/portal/auth/AuthController.java`
- Modify: `portal/backend/src/test/java/io/ztoken/portal/auth/AuthControllerTest.java`

- [x] **Step 1: 在 `AuthController` 中注入并调用 `DefaultTokenService`**

1. 修改 `AuthController` 构造方法，注入 `DefaultTokenService defaultTokenService`。
2. 在 `register` 方法中，调用 `defaultTokenService.initializeDefaultTokenOnRegister(request.username(), request.password());`。
3. 在 `completeOAuth` 方法中，通过 `defaultTokenService.toPrincipal(login)` 提取凭据并调用 `defaultTokenService.ensureDefaultTokenExists(principal)`。

- [x] **Step 2: 更新 `AuthControllerTest` 适配模拟响应**

- [x] **Step 3: 运行控制器测试**

执行命令：
```powershell
mvn -f portal/backend/pom.xml test -Dtest=AuthControllerTest
```
预期结果：`BUILD SUCCESS`。

- [x] **Step 4: 提交 Task 2 代码**

```powershell
git add portal/backend/src/main/java/io/ztoken/portal/auth/AuthController.java portal/backend/src/test/java/io/ztoken/portal/auth/AuthControllerTest.java
git commit -m "feat(auth): 在注册与OAuth首次登录中集成默认 API Key 自动创建"
```

---

### Task 3: 全量构建与端到端回归验证

**Files:**
- None (验证任务)

- [x] **Step 1: 运行后端全量测试套件**

执行命令：
```powershell
mvn -f portal/backend/pom.xml test "-Dskip.frontend=true"
```
预期结果：所有测试全部通过（0 failures, 0 errors, 0 skipped）。

- [x] **Step 2: 验证 Git 状态**

执行命令：
```powershell
git status
```
预期结果：工作区整洁，代码及测试均已正确提交。
