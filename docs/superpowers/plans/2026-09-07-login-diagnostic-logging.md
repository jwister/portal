# Login Diagnostic Logging Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make failed Portal-to-NewAPI login attempts diagnosable from backend logs without recording passwords, access tokens, or configuration secrets.

**Architecture:** Keep the browser-facing `NEWAPI_AUTH_FAILED` response unchanged. Add a narrowly scoped warning log in `NewApiHttpClient` when NewAPI returns a business-level login rejection, and diagnostic warnings for HTTP and transport failures on that login route. The logged upstream value is only scheme, host, and port; upstream messages are whitespace-normalized and length-capped.

**Tech Stack:** Java 17, Spring Boot 3, WebClient, SLF4J/Logback, JUnit 5, MockWebServer.

---

### Task 1: Define the safe business-failure logging behavior

**Files:**
- Modify: `backend/src/test/java/io/ztoken/portal/newapi/NewApiHttpClientTest.java`
- Modify: `backend/src/main/java/io/ztoken/portal/newapi/NewApiHttpClient.java`

- [ ] **Step 1: Write the failing test**

Add a Logback `ListAppender` around `NewApiHttpClient` and enqueue this NewAPI response:

```java
{"success":false,"message":"账号不存在; diagnostic=expected-detail"}
```

Call `client.login("alice", "do-not-log-this-password")`. Assert that the captured warning contains `NewAPI 登录被拒绝`, the expected upstream message, and does not contain `alice` or `do-not-log-this-password`.

- [ ] **Step 2: Run the test to verify it fails**

Run:

```powershell
mvn -f backend/pom.xml -Dskip.frontend=true -Dtest=NewApiHttpClientTest#failedLoginEmitsSanitizedDiagnosticLog test
```

Expected: FAIL because no login-rejection diagnostic log currently exists.

- [ ] **Step 3: Implement the minimal logging behavior**

In `NewApiHttpClient.login`, when the response is absent or `success` is false, log only:

```java
log.warn("NewAPI 登录被拒绝: upstream={}, httpStatus=200, success={}, message={}",
        upstreamTarget, success, safeUpstreamMessage(root));
```

Derive `upstreamTarget` from the configured URL as `scheme://host[:port]`. Implement `safeUpstreamMessage` to replace line breaks with spaces, trim it, use `"<empty>"` when blank, and limit it to 200 characters. Do not log request bodies, usernames, passwords, tokens, or complete configured URLs.

- [ ] **Step 4: Run the test to verify it passes**

Run:

```powershell
mvn -f backend/pom.xml -Dskip.frontend=true -Dtest=NewApiHttpClientTest#failedLoginEmitsSanitizedDiagnosticLog test
```

Expected: PASS.

### Task 2: Cover HTTP and transport login failures

**Files:**
- Modify: `backend/src/test/java/io/ztoken/portal/newapi/NewApiHttpClientTest.java`
- Modify: `backend/src/main/java/io/ztoken/portal/newapi/NewApiHttpClient.java`

- [ ] **Step 1: Write the failing test**

Enqueue an HTTP 502 response for `client.login("alice", "do-not-log-this-password")`, capture `NewApiHttpClient` logs, and assert the warning contains `NewAPI 登录请求失败`, `httpStatus=502`, and excludes the username, password, and response body.

- [ ] **Step 2: Run the test to verify it fails**

Run:

```powershell
mvn -f backend/pom.xml -Dskip.frontend=true -Dtest=NewApiHttpClientTest#loginHttpFailureEmitsSanitizedDiagnosticLog test
```

Expected: FAIL because HTTP failure diagnostics are not logged.

- [ ] **Step 3: Implement the minimal logging behavior**

In `send`, before rethrowing the generic `NewApiException`, detect the `/api/user/login` path and emit a warning containing only `upstreamTarget`, the HTTP status, and the fixed failure category. In the non-HTTP runtime-exception branch, emit a warning containing only `upstreamTarget` and the exception class name. Keep existing response mapping unchanged.

- [ ] **Step 4: Run the test to verify it passes**

Run:

```powershell
mvn -f backend/pom.xml -Dskip.frontend=true -Dtest=NewApiHttpClientTest#loginHttpFailureEmitsSanitizedDiagnosticLog test
```

Expected: PASS.

### Task 3: Verify the focused suite and running service

**Files:**
- Modify: `backend/src/main/java/io/ztoken/portal/newapi/NewApiHttpClient.java`
- Modify: `backend/src/test/java/io/ztoken/portal/newapi/NewApiHttpClientTest.java`

- [ ] **Step 1: Run the complete NewAPI client test class**

Run:

```powershell
mvn -f backend/pom.xml -Dskip.frontend=true -Dtest=NewApiHttpClientTest test
```

Expected: PASS.

- [ ] **Step 2: Restart the local Portal run configuration**

Stop the current process listening on port 8084, then start `io.ztoken.portal.PortalApplication` with its existing user-scoped environment configuration.

- [ ] **Step 3: Verify a deliberately invalid login**

POST an invalid username and password to `http://127.0.0.1:8084/api/auth/login` and confirm that the response remains HTTP 401 with `NEWAPI_AUTH_FAILED`, while the backend console prints a `NewAPI 登录被拒绝` warning without the supplied credentials.
