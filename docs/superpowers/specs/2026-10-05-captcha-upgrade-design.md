# Design Spec: Captcha Upgrade (AJ-Captcha)

## 1. Overview
The current simple image CAPTCHA for user registration is vulnerable to basic OCR bot attacks. This project replaces the existing `easy-captcha` implementation with a secure, behavioral CAPTCHA solution using the open-source library `Anji-Plus Captcha` (aj-captcha). This requires changes to both the Spring Boot backend and the React frontend.

## 2. Backend Architecture

### 2.1 Dependency Changes
- **Remove**: `com.github.whvcse:easy-captcha`
- **Add**: `com.anji-plus:captcha-spring-boot-starter:1.3.0` (or latest compatible version)
- **Configuration**: Use LocalCache for storing CAPTCHA tokens. No external Redis requirement is introduced.
```yaml
aj.captcha:
  cache-type: local
  water-mark: "Portal"
  # Support both block puzzle (slider) and click word
  type: default
```

### 2.2 API Changes
- **Auto-configured Endpoints**: 
  - `POST /captcha/get`
  - `POST /captcha/check`
- **Manual Endpoints Removed**: 
  - `GET /captcha` in `AuthController`
- **Registration Request Update**:
  - `RegisterRequest` will drop `captchaId` and `captchaCode`.
  - Introduce `captchaVerification` string parameter.

### 2.3 Registration Flow
In `AuthController.register()`:
1. Extract `captchaVerification` from the payload.
2. Call `CaptchaService.verification(captchaVO)` provided by Anji-Plus.
3. If valid, proceed with standard user registration logic.
4. If invalid, reject with "Invalid or expired CAPTCHA" exception.

## 3. Frontend Architecture

### 3.1 Component Addition
- Inject the standard React component for `aj-captcha` into `src/components/verifition`.
- It will consist of UI elements for sliders and word-clicks, relying on the backend APIs (`/captcha/get` and `/captcha/check`).

### 3.2 UI Flow (Registration Page)
1. **Form Input**: User enters registration details (Email, Password, etc.). The manual text-input field for the CAPTCHA is removed.
2. **Action**: User clicks the "Register" button.
3. **Popup Triggered**: The Anji-Plus Verification component intercepts the flow and displays a popup puzzle (e.g., sliding puzzle).
4. **Behavior Validation**: Upon successful puzzle completion, the component receives a `captchaVerification` token.
5. **Final Submission**: The frontend appends `captchaVerification` to the `RegisterRequest` payload and sends it to `POST /register`.

## 4. Testing & Edge Cases
- **Missing Token**: Registration API must robustly reject requests without `captchaVerification`.
- **Replay Attacks**: Once a token is consumed by the registration endpoint, the backend must invalidate it to prevent reuse.
- **Frontend Fallbacks**: If the CAPTCHA server endpoints fail, the component should gracefully handle the error and alert the user.
