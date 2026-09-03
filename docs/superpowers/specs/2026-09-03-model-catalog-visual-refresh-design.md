# Model Catalog Visual Refresh Design

## Goal

Refresh the `/models` page into a clean technology-oriented catalog with a stronger header hierarchy, clearer model-card information, and responsive behavior while preserving existing search, grouping, copy, detail-modal, loading, and error interactions.

## Direction

Use a light “clean technology” visual system: warm-white canvas, cool blue and mint accents, subtle grid/radial atmosphere, dark navy typography, restrained borders, and small elevation changes. The page should feel like a polished developer catalog rather than a generic centered marketing section.

## Layout

- Header: two-column hero on desktop. Left side contains badge, title, supporting copy, and a short descriptor. Right side contains a compact catalog summary with total model count and active group count. Collapse to one column on screens below 760px.
- Content: existing sticky group navigation and results column remain, but are visually unified as translucent white panels with consistent 8px spacing rhythm.
- Toolbar: search control and result count stay above the cards, with a subtle “catalog index” label and responsive wrapping.
- Cards: three-column desktop grid, two-column tablet, one-column mobile. Each card uses a small accent rail, model identity row, pricing row, and metadata footer. Existing info and copy buttons remain accessible and keyboard reachable.

## Visual tokens

- Canvas: `#f7faff` with radial blue/mint lights and a low-opacity grid texture.
- Ink: `#10233f`; muted text: `#60708a`.
- Accent blue: `#2563eb`; accent mint: `#0fa97b`; pale mint surface: `#e8f8f2`.
- Borders: cool gray-blue at low opacity; shadows use blue-gray tint and low blur.
- Typography: retain system compatibility but use a distinctive serif display treatment for the hero title and a monospace treatment for model identifiers/prices.

## Interaction and accessibility

- Preserve all current actions and labels.
- Add visible hover/focus states without layout shift; action controls maintain at least a 40px hit area.
- Respect reduced-motion preferences by disabling card entrance/hover transforms when requested.
- Keep contrast at WCAG-friendly levels for body text and interactive states.

## Scope

Modify `ModelsPage.tsx` only where semantic wrapper elements are needed for the refreshed hierarchy, and update the model-page styles in `styles.css`. Do not alter API contracts, translations, routing, or unrelated pages.

## Verification

Run the catalog page tests and the full frontend test suite, then run a production build. Manually inspect desktop, tablet, and 375px layouts for clipping, focus visibility, and modal behavior.
