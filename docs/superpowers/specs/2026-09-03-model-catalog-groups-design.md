# Model catalog grouped navigation design

## Goal

Keep the portal's model catalogue sourced from NewAPI while presenting its model groups in a left navigation and the selected group's models on the right. A model belonging to multiple NewAPI groups must appear in every applicable group.

## Data contract

The portal backend continues to fetch NewAPI's `GET /api/pricing` endpoint. It maps each model's `enable_groups` array to a customer-safe `groups: string[]` field in the portal's existing `GET /api/catalog/models` response.

An absent or empty upstream group list becomes `["default"]`. Price fields and the availability calculation remain unchanged. The portal remains the only caller of NewAPI, so browser clients never receive NewAPI credentials or depend on its network location.

## UI behavior

The model catalogue is a responsive two-column page:

- The left navigation contains `All models` followed by the groups derived from every model's `groups` array. Each entry has a model count and an unambiguous selected state.
- The right panel contains the search field, selected group heading, result count, and the existing model cards.
- Selecting a group filters to models whose `groups` contains that group. `All models` includes every model exactly once.
- Search further filters the selected set by model name, case-insensitively. A model in several groups appears in each selected group but never twice in one result set.
- Existing loading, failure, no-results, price, and copy-name behavior is preserved.
- On narrow screens, group navigation is displayed as a horizontally scrollable selector above the results so it remains usable without a fixed sidebar.

## Error handling

The page keeps its existing remote-state behavior. If the portal API fails, it shows the existing error state. An empty catalogue or a search/group combination with no models uses the existing empty-state component.

## Verification

- Backend controller/client tests cover preservation of all `enable_groups` values and fallback to `default`.
- Frontend tests cover group navigation, a model shown through more than one group, and filtering combined with search.
- Run the backend Maven suite and the frontend test/build commands.
