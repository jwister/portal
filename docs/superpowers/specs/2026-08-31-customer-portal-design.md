# Customer Portal Design

## Goal

Build an independent customer-facing portal without changing NewAPI source code. The portal replaces the customer pages and user console while NewAPI continues to provide the administrator console, AI gateway, user account, balance, API keys, usage logs, model configuration, and model request endpoint.

The first release includes Chinese and English public pages, email registration and login, GitHub OAuth, Google OIDC, a customer console, PayPal and TRC20-USDT payment, and payment-order management.

## Scope

### Public navigation

- **Home**: product introduction and selected model/price cards.
- **Models**: model groups, available models, and prices.
- **Documentation**: configurable external link.
- **Purchase**: preset recharge amounts of USD 5, 10, 50, 100, 200, and 500 plus a server-validated custom amount.
- **Console**: authenticated entry point.

### Authenticated console

- Dashboard: available balance, total request count, quota consumption, token consumption, and other safe user statistics supplied by NewAPI.
- Balance recharge: the same checkout experience as the Purchase page.
- Order management: customer-owned order history and status details.
- Token management: create, view, edit, disable, delete, and view usage of NewAPI API keys.
- Usage logs: personal usage log list, filtering, pagination, and statistics.
- Profile: safe NewAPI profile fields and portal-only preferences.

## Architecture

```text
Browser
  |-- Customer management requests --> Portal Spring Boot BFF
  |-- AI API calls (/v1) -----------> NewAPI directly
  |
Portal Spring Boot (one port, one executable JAR)
  |-- serves React static assets
  |-- provides /api/**
  |-- forwards an allowlisted set of user-scoped NewAPI API calls
  |-- owns payment orders, provider callbacks, and credit attempts
  |
  |-- NewAPI public management APIs
  |-- PayPal APIs and webhook
  |-- TronGrid / TRC20 verification
  |-- Portal MySQL database
```

The portal resides in `portal/`, alongside but independent from the NewAPI checkout. It must not modify NewAPI source code or access the NewAPI database.

### Technology choices

- Frontend: React, TypeScript, Vite, React Router, React Query, and an i18n library.
- Backend: Java 17 and Spring Boot 3.3, Maven, Spring Data JPA, Flyway, and WebClient.
- Database: a dedicated MySQL database for portal sessions and payment data only.
- Packaging: Maven builds the React application and packages its distribution into the Spring Boot JAR. The application serves the SPA and `/api/**` from one configurable port.

## Identity and authorization

NewAPI remains the only account authority.

- Email registration and login use NewAPI public user-auth endpoints.
- GitHub login uses NewAPI GitHub OAuth.
- Google login uses NewAPI OIDC configured for Google.
- The portal does not store passwords, OAuth access tokens, or a duplicate customer identity table.
- After a successful NewAPI login, the portal creates an encrypted, HttpOnly, Secure, SameSite portal session that is associated with the authenticated NewAPI session or access token.
- The BFF resolves the current NewAPI user server-side. It never accepts a user ID, NewAPI authorization header, or `New-Api-User` header from the browser as proof of identity.

The BFF exposes only explicit customer routes; it must not become a generic NewAPI proxy. NewAPI administrator credentials are held only in deployment secrets and may only be used by the payment credit service.

For GitHub and Google OAuth, configure NewAPI `ServerAddress` to the public portal URL. GitHub and Google then return to `PORTAL_PUBLIC_URL/oauth/github` or `PORTAL_PUBLIC_URL/oauth/oidc`; the React callback calls the BFF, which forwards the one-time code/state to the corresponding documented NewAPI OAuth endpoint and converts the returned NewAPI auth bundle into a portal session.

## NewAPI integration

The BFF uses documented NewAPI management APIs and sends the appropriate NewAPI authorization headers server-side.

| Portal capability | NewAPI capability |
| --- | --- |
| Current account and profile | current-user read/update endpoints |
| User groups and usable models | current-user group and available-model endpoints |
| API keys | token management endpoints and token-usage endpoint |
| Usage logs and dashboard | personal log list/search/statistics and personal quota-data endpoints |
| Payment credit | administrator user-management quota increment endpoint |
| Authentication | email auth, GitHub OAuth, and OIDC endpoints |

The public model catalog is a safe projection. Before login it displays the configured default-group catalog. After login it uses the authenticated user's available models and group pricing. The portal must not expose an administrator-only model endpoint to the browser. Exact price fields and group semantics are validated against the deployed NewAPI version during integration testing.

## Pages and localization

The public pages are Home, Models, Documentation, Purchase, and Console. Console routes are protected and contain Dashboard, Recharge, Orders, Tokens, Logs, and Profile.

Chinese is selected when the browser language starts with `zh`; English is selected otherwise. The user can switch languages. The selected locale is stored as a portal browser preference, so it does not depend on NewAPI custom-setting fields.

All public UI strings have Chinese and English translations. The design is responsive and mobile-first. Documentation is a configurable external URL rather than portal-managed documentation in the first release.

## Payments and orders

### Common order lifecycle

The backend creates a payment order for the currently authenticated NewAPI user. The target NewAPI user ID is resolved from the server session rather than supplied by the request.

Order states are:

```text
WAITING_PAYMENT -> PAYMENT_CONFIRMED -> CREDITING -> COMPLETED
                 -> EXPIRED / FAILED / MANUAL_REVIEW
```

Every provider event is stored and deduplicated by its provider event identifier. Each attempt to credit NewAPI is recorded separately. If a NewAPI credit request times out or returns an indeterminate result, the order moves to `MANUAL_REVIEW`; the system does not retry blindly and risk double crediting.

### PayPal

- The backend creates the PayPal order and supplies only safe checkout data to the frontend.
- Capture and webhook processing are verified server-side.
- A verified, idempotently processed PayPal event advances the internal order to `PAYMENT_CONFIRMED`.

### TRC20-USDT

- A USDT order snapshots the recipient address, payable amount, token contract, creation time, expiry time, and expected confirmation count.
- The backend validates a submitted transaction hash and/or observes the transfer through TronGrid.
- The transfer must match the snapshot address, USDT contract, amount, and confirmation threshold before it can confirm the order.
- Unpaid orders can be refreshed. A customer may submit a transaction hash only for their own eligible order.

The implementation follows the payment-state-machine, webhook, chain-confirmation, and NewAPI-credit patterns in `F:\WorkSpace\study\AIProject\New-api\usdt`, but remains a separate application with a separate database and deployment.

### Order management

Customers can view only their own orders. The list displays order number, amount, payment method, creation time, payment time, credit time, and status. The order detail reveals provider-specific details only when appropriate: PayPal order metadata or the USDT address, amount, expiry, transaction hash, and confirmation status.

The order page supports refresh and transaction-hash submission for eligible unfinished USDT orders. It never lets a user alter the recipient user ID, order amount, or payment confirmation state.

## Persistence

The portal database contains only portal-owned data, including:

- encrypted portal-session metadata;
- payment orders and immutable payment snapshots;
- PayPal and chain provider events;
- chain-transfer observations;
- NewAPI credit attempts and audit metadata.

It does not replicate NewAPI user profiles, wallets, API keys, model settings, or usage logs.

## Security and configuration

All secrets are injected at deployment time. No development configuration may contain real secrets. Configuration includes the NewAPI base URL, an administrator access token for the credit service, portal session encryption/signing secrets, database settings, PayPal credentials and webhook ID, TronGrid credentials, USDT contract/address settings, and public portal URL.

The portal validates every amount server-side, uses a allowlisted set of preset prices plus configurable custom minimum/maximum bounds, and rate-limits sensitive order and payment status routes. Administrative NewAPI credentials are never returned to the frontend or written to application logs.

## Error handling

- The UI presents actionable error, empty, loading, and retry states for all remote data.
- Invalid or expired sessions return the user to sign-in without leaking upstream responses.
- Provider verification failures preserve an audit trail and show a safe failure message.
- NewAPI unavailable errors are distinguishable from payment failures and do not mark money as credited.
- Payment webhook and chain verification handlers are idempotent and safe under concurrent delivery.

## Testing and verification

Backend tests cover order transitions, authorization of order access, custom amount validation, webhook signature checks, duplicate provider events, USDT transaction validation, NewAPI credit success/failure/unknown outcomes, and retry boundaries.

Frontend tests cover browser-language fallback, language switching, protected console navigation, model/price empty and error states, token and log interactions, order status presentation, and eligible USDT transaction-hash submission.

Integration tests use a mock HTTP server for NewAPI, PayPal, and TronGrid. They verify that the portal uses documented NewAPI APIs and never needs database access to NewAPI.

Build verification runs frontend tests/build as part of `mvn clean package`, checks that the JAR includes the frontend assets, and starts the JAR to verify the SPA fallback and `/api/**` routing on one port.

## Out of scope for the first release

- Altering NewAPI source code, routes, schema, or administrator console.
- Proxying actual model requests through the portal.
- Additional payment providers, refunds, invoices, affiliate commissions, and subscription billing.
- A standalone portal user directory or password system.
