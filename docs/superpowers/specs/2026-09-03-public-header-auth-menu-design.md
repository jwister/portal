# Public Header Auth Menu Design

## Goal

Make the portal header reflect authentication state clearly: the primary navigation never contains Console, authenticated users get Console plus their account and logout controls on the right, and anonymous users get only a Login action on the right.

## Behavior

- Shared navigation contains Home, Models, Docs, and Purchase only.
- Authenticated footer shows language switch, Console button, avatar with the first character of the username, username, and Logout button.
- Anonymous footer shows language switch and Login button only.
- Logout posts to the existing `/api/auth/sign-out` endpoint. On success, reload the current page so the header is rendered as anonymous. On failure, show an error toast and keep the authenticated controls.

## Visuals and accessibility

Use a compact account cluster with a mint avatar, readable username, and a text logout control. Avatar has an accessible label derived from the username; all controls remain keyboard reachable and retain existing button hit areas. Responsive styling lets the account cluster wrap cleanly on narrow screens.

## Scope

Modify `PublicHeader.tsx`, its focused tests, the two locale files, and public-header styles in `styles.css`. Reuse the existing auth API and `useAuthStatus`; no routing or backend changes.
