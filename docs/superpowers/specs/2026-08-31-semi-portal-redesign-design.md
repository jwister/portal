# Semi Design Portal Redesign

## Goal

Replace the current prototype presentation with a Semi Design-based customer portal. The new portal uses the information hierarchy of the deployed NewAPI home page and model pricing page as a reference while keeping Ztoken branding, copy, assets, and implementation independent.

## Visual direction

The public portal adopts a warm off-white surface, restrained blue-to-mint atmospheric glow, near-black display typography, pale-gray rounded content areas, and a saturated blue primary action. Semi Design supplies the interactive primitives; custom CSS is limited to the hero composition, background atmosphere, code example, and model-card density.

The reference sites inform hierarchy and interaction patterns only. No logos, icons, copy, product names, or assets are copied from them.

## Component system

- Use Semi Design for navigation, buttons, forms, inputs, cards, tabs, popovers, dropdowns, tables, pagination, empty states, loading skeletons, messages, and modals.
- Configure Semi theme tokens for the public palette and shared border radius.
- Retire the existing bespoke form, token-table, and button visual styling where Semi components provide an equivalent.
- Retain custom layout components where Semi has no equivalent: the public hero, API request/response code panel, ambient gradients, and model pricing-card content.

## Public navigation and authentication state

Public navigation contains Home, Models, Documentation, and Purchase.

- On application bootstrap, call `GET /api/auth/me` through the portal backend.
- If the request succeeds, the right-side action is Console and directs to the authenticated dashboard.
- If it returns 401, the right-side action is Sign in and directs to `/sign-in`.
- Registration is never a top-level navigation item. The sign-in page contains the link to `/sign-up`; the sign-up page contains the reciprocal sign-in link.
- A protected console route redirects anonymous visitors to `/sign-in` and preserves no upstream access token in browser storage.

## Home page

The home page follows the NewAPI home page's content hierarchy:

1. compact top navigation;
2. two-column hero with a Ztoken API gateway proposition, primary sign-in/get-started action, models action, and a request/response code panel;
3. compact capability statistics;
4. developer-focused features;
5. three onboarding steps: create account, fund balance, call the API;
6. final call to action and restrained footer.

All copy is rewritten for Ztoken and is available in Chinese and English.

## Models page

The models page follows the deployed NewAPI pricing page's interaction pattern:

- atmospheric heading with current enabled-model count;
- search field, group/provider filters, price-unit switch, and sorting;
- responsive three-column grid of model cards;
- each card displays model name, provider/group, input/output/cache price when available, billing type, capabilities, a copy-model-name action, and details action;
- Semi Design `Input`, `Select`, `Button`, `Tag`, `Card`, `Empty`, and `Skeleton` components are used for interactive and stateful elements.

The portal BFF exposes a customer-safe catalog DTO. It is derived from NewAPI's user-available-model endpoint and its public pricing/ratio configuration only when that configuration is enabled. The browser never receives administrator-only channel, model-management, or configuration data. If a model's price cannot be safely resolved, the card presents an explicit unavailable state rather than inventing a price.

## Error handling and tests

- All remote page data has Semi loading, empty, and error states.
- NewAPI failures are mapped to safe portal errors; raw upstream messages are not sent to the browser.
- Frontend tests cover authentication-state navigation, sign-in/sign-up links, models search/filter behavior, and model-card pricing states.
- Backend tests cover catalog user scoping, safe DTO mapping, and pricing-unavailable behavior.

## Out of scope

- Changing NewAPI source code, database schema, or administrator frontend.
- Introducing a Vue runtime or Element Plus into the React portal.
- Copying reference-site branding, proprietary copy, or visual assets.
