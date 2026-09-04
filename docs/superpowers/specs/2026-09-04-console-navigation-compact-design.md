# Console Navigation Compact Design

## Goal

Make the authenticated console navigation more compact and remove redundant routes back to the public portal.

## Layout and navigation

- Reduce the desktop console sidebar from 248px to 216px while retaining the Ztoken logo and brand name.
- Remove the `门户首页` entry beneath the console brand.
- Keep every existing console destination, its current icon, selection state, keyboard navigation, and responsive behavior unchanged.

## Account menu

- Remove the `门户首页` entry from the account menu in the console top bar.
- Keep the username as a non-interactive account label and prefix it with a user/avatar icon.
- Keep the existing sign-out action and behavior, and prefix its label with a sign-out icon.
- Preserve keyboard access to the account trigger and sign-out action. The menu remains visible on hover and focus-within.

## Scope and non-goals

- Modify `ConsoleLayout.tsx`, its focused test, and the related console styles only.
- Do not change public-header navigation, routing, authentication API calls, localization text, or console page content.

## Verification

- Add a focused failing test that proves neither console "门户首页" link is rendered, while the account label and sign-out control remain available.
- Run the focused test, the full frontend test suite, and the frontend production build.
