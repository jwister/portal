# Console Workspace Design

## Goal

Replace the current dark standalone dashboard and token page with a unified customer console workspace: fixed left navigation, top utility bar, and right-side content area. The layout takes structural inspiration from the supplied reference image while using original Ztoken branding and Semi Design components.

## Layout

Desktop layout uses Semi `Layout`, `Sider`, `Nav`, `Header`, and `Content`.

- The left sidebar is 248px wide with a warm-white background and right divider.
- The content header is 64px tall with page title on the left and notification, language, display, profile, and account-menu actions on the right.
- The main content area uses a very light blue-gray surface and 24px desktop gutters.
- At widths below 768px, the sidebar is replaced by a Semi `Drawer` opened from the content header. Main content becomes a single column with 16px gutters.

## Navigation

The sidebar uses a single Semi icon family and text labels with WCAG AA contrast.

1. Dashboard
2. Account recharge
3. Token management
4. Usage logs
5. Account information

Sections may add non-clickable group labels only when they improve scanning. The active item uses pale mint background, blue-green icon/text, and a visible selected state. Inactive items use dark slate text, 44px minimum touch targets, focus outlines, and a clear hover state.

## Dashboard

The dashboard contains:

- Four responsive statistic cards: available balance, total requests, quota consumption, and token consumption.
- Cards use separate pale semantic surfaces (mint, periwinkle, amber, blush) and always pair color with a text label.
- The first three cards map to safe BFF dashboard data. Token consumption remains a clear zero/empty state until the logs-stat BFF field is implemented.
- A primary consumption workspace below the cards contains date/model filters, summary cells, and a trend panel that explicitly states time-series data is not available yet.
- A right-side “How to use” card gives three actionable onboarding steps linking to models, recharge, and token creation.

## Component boundaries

- `ConsoleLayout` owns desktop sidebar, mobile drawer, header, active-route state, and user profile action.
- `DashboardPage` owns dashboard data loading and dashboard-only content.
- `TokensPage`, future recharge, logs, and profile pages render inside `ConsoleLayout`; they do not recreate navigation or global background styles.

## Accessibility and interaction

- Sidebar and mobile drawer controls use visible labels and keyboard-operable Semi Nav/Button controls.
- Icon-only utility actions have `aria-label` values.
- Text, borders, active states, and card content meet light-theme contrast expectations; status is never conveyed by color alone.
- Buttons and nav rows meet 44px touch target minimum.
- Hover and selected transitions use opacity/color changes within 150–250ms and respect reduced-motion preferences.

## Testing

- Test that dashboard and token routes render within `ConsoleLayout`.
- Test anonymous console navigation redirects to sign-in.
- Test the active sidebar item changes with the route.
- Test mobile-width layout exposes the menu button and drawer navigation.
- Test dashboard cards display real BFF values and a clear token-consumption empty state.

## Out of scope

- Adding payment provider behavior, chart data aggregation, or new NewAPI schema changes.
- Copying the reference product's brand assets, copy, or account-specific data.
