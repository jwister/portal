# Captcha Upgrade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the easily bypassed `easy-captcha` image CAPTCHA with `aj-captcha` (Anji-Plus) behavioral sliding/click CAPTCHA to stop automated bot registrations.

**Architecture:** We will replace the backend dependency with `captcha-spring-boot-starter` which automatically provides `/captcha/get` and `/captcha/check` APIs backed by local cache. The frontend will use `aj-captcha-react` to display the puzzle widget and pass the acquired `captchaVerification` token to the `/register` endpoint.

**Tech Stack:** Java 17, Spring Boot 3, React 19, aj-captcha

---

### Task 1: Backend Dependencies & Configuration

**Files:**
- Modify: `portal/backend/pom.xml`
- Modify: `portal/backend/src/main/resources/application.yml`

- [ ] **Step 1: Update pom.xml**
  Remove the `easy-captcha` dependency:
  ```xml
  <dependency>
      <groupId>com.github.whvcse</groupId>
      <artifactId>easy-captcha</artifactId>
      <version>1.6.2</version>
  </dependency>
  ```
  Add the `captcha-spring-boot-starter` dependency:
  ```xml
  <dependency>
      <groupId>com.anji-plus</groupId>
      <artifactId>captcha-spring-boot-starter</artifactId>
      <version>1.3.0</version>
  </dependency>
  ```

- [ ] **Step 2: Add Configuration to application.yml**
  Add the following block to `application.yml` under the root level:
  ```yaml
  aj:
    captcha:
      cache-type: local
      water-mark: "Portal"
      type: default
  ```

- [ ] **Step 3: Commit**
  ```bash
  git add pom.xml src/main/resources/application.yml
  git commit -m "build(backend): replace easy-captcha with aj-captcha starter"
  ```

### Task 2: Backend Logic Updates

**Files:**
- Modify: `portal/backend/src/main/java/io/ztoken/portal/auth/AuthController.java`
- Delete: `portal/backend/src/main/java/io/ztoken/portal/auth/CaptchaService.java`
- Delete: `portal/backend/src/main/java/io/ztoken/portal/auth/CaptchaResponse.java`

- [ ] **Step 1: Update AuthController.java API**
  Remove the manual `GET /captcha` endpoint and inject Anji's `com.anji.captcha.service.CaptchaService`.
  Update the `GET /verification` endpoint to accept `captchaVerification` instead of `captchaId` and `captchaCode`.

  ```java
  // Add these imports
  import com.anji.captcha.model.common.ResponseModel;
  import com.anji.captcha.model.vo.CaptchaVO;
  import com.anji.captcha.service.CaptchaService;

  // In AuthController constructor, replace old CaptchaService with the Anji one:
  private final CaptchaService captchaService;

  public AuthController(NewApiClient newApiClient,
                        PortalSessionService sessions, PortalProperties properties, CaptchaService captchaService) {
      this.newApiClient = newApiClient;
      this.sessions = sessions;
      this.properties = properties;
      this.captchaService = captchaService;
  }

  // Remove public CaptchaResponse captcha()

  // Replace public ResponseEntity<Void> verification(...) with:
  @org.springframework.web.bind.annotation.GetMapping("/verification")
  public org.springframework.http.ResponseEntity<Void> verification(
          @org.springframework.web.bind.annotation.RequestParam String email,
          @org.springframework.web.bind.annotation.RequestParam String captchaVerification) {
      if (captchaVerification == null || captchaVerification.isBlank()) {
          throw new IllegalArgumentException("Captcha is required");
      }
      CaptchaVO captchaVO = new CaptchaVO();
      captchaVO.setCaptchaVerification(captchaVerification);
      ResponseModel response = captchaService.verification(captchaVO);
      if (!response.isSuccess()) {
          throw new IllegalArgumentException("Captcha is invalid or expired");
      }
      newApiClient.sendEmailVerification(email);
      return org.springframework.http.ResponseEntity.noContent().build();
  }
  ```

- [ ] **Step 2: Delete old captcha files**
  Delete `CaptchaService.java` and `CaptchaResponse.java`.

- [ ] **Step 3: Commit**
  ```bash
  git add src/main/java/io/ztoken/portal/auth/
  git commit -m "feat(backend): implement aj-captcha verification in AuthController"
  ```

### Task 3: Backend Tests Cleanup

**Files:**
- Delete: `portal/backend/src/test/java/io/ztoken/portal/auth/CaptchaServiceTest.java`

- [ ] **Step 1: Delete CaptchaServiceTest**
  Delete the file `CaptchaServiceTest.java` as the old service is gone.

- [ ] **Step 2: Verify Backend Builds**
  ```bash
  mvn clean test
  ```
  Expected: PASS

- [ ] **Step 3: Commit**
  ```bash
  git rm src/test/java/io/ztoken/portal/auth/CaptchaServiceTest.java
  git commit -m "test(backend): remove deprecated CaptchaService tests"
  ```

### Task 4: Frontend Dependencies & API Updates

**Files:**
- Modify: `portal/frontend/package.json`
- Modify: `portal/frontend/src/api/auth.ts`

- [ ] **Step 1: Install aj-captcha-react**
  Run:
  ```bash
  npm install aj-captcha-react
  ```

- [ ] **Step 2: Update api/auth.ts**
  Replace `captchaId` and `captchaCode` with `captchaVerification` in `sendEmailVerification`:
  ```typescript
  // Replace the old export async function sendEmailVerification...
  export async function sendEmailVerification(email: string, captchaVerification: string): Promise<void> {
    await request(`/api/auth/verification?email=${encodeURIComponent(email)}&captchaVerification=${encodeURIComponent(captchaVerification)}`)
  }
  ```

- [ ] **Step 3: Commit**
  ```bash
  git add package.json package-lock.json src/api/auth.ts
  git commit -m "build(frontend): add aj-captcha-react and update auth api"
  ```

### Task 5: Frontend Integration in SignUpPage

**Files:**
- Modify: `portal/frontend/src/features/auth/SignUpPage.tsx`

- [ ] **Step 1: Import and configure AjCaptcha**
  At the top of `SignUpPage.tsx`:
  ```tsx
  import { AjCaptcha } from 'aj-captcha-react';
  ```
  Replace `captcha`, `captchaCode`, `captchaError`, `captchaLoading`, `captchaVisible`, `refreshCaptcha`, `openCaptcha`, `verifyAndSend` and the `AuthDialog` JSX with:
  ```tsx
  const captchaRef = useRef<any>(null);

  const openCaptcha = async () => {
    if (coolingDown || submitting) return;
    setError(null);
    if (!emailPattern.test(emailValue.trim())) {
      const errors = { email: 'auth.emailInvalid' };
      setFieldErrors(errors);
      if (formRef.current) focusInvalidField(formRef.current, errors);
      return;
    }
    setFieldErrors((errors) => ({ ...errors, email: '' }));
    captchaRef.current?.verify();
  };

  const onCaptchaSuccess = async (data: any) => {
    if (sendingCode) return;
    setSendingCode(true);
    try {
      await sendEmailVerification(emailValue.trim(), data.captchaVerification);
      setVerificationSent(true);
      setCountdown(60);
    } catch (cause) {
      setError(cause instanceof AuthApiError && cause.status > 0 && cause.status < 500 ? cause.message : t('register.verificationSendError'));
    } finally {
      setSendingCode(false);
    }
  };
  ```

- [ ] **Step 2: Update JSX**
  In the `return` statement:
  1. For the "Send Code" button, change `disabled={coolingDown || !emailValue || captchaLoading || submitting}` to `disabled={coolingDown || !emailValue || submitting}` and remove `aria-busy={captchaLoading}`.
  2. Remove `{captchaLoading && ...}` from the button content.
  3. Remove the entire `{captchaVisible && <AuthDialog>...</AuthDialog>}` section at the end.
  4. Add the `AjCaptcha` component at the end of the `return`:
  ```tsx
      <p className="zt-auth-switch">{t('register.haveAccount')} <a href={authSwitchUrl('/sign-in')}>{t('auth.submit')}</a></p>
      <AjCaptcha
        ref={captchaRef}
        path="/api" // Our backend API proxies /api
        type="blockPuzzle"
        onSuccess={onCaptchaSuccess}
      />
    </AuthLayout>
  ```

- [ ] **Step 3: Test Frontend Changes**
  ```bash
  npm run build
  ```
  Expected: Successful compilation.

- [ ] **Step 4: Commit**
  ```bash
  git add src/features/auth/SignUpPage.tsx
  git commit -m "feat(frontend): integrate aj-captcha puzzle into signup flow"
  ```
