# Dashboard Token Range Design

## Goal

Make the dashboard's selected range apply consistently to Token usage: the
summary card, Token trend series, and Token trend title must all reflect either
the last 7 or the last 30 Shanghai business days.

## Current behavior

The analytics endpoint requests the selected 7- or 30-day upstream window, but
`DashboardAnalytics.from` always emits only seven Token dates. The frontend
sums that fixed series for the Token summary card. Consequently, the 30-day
selection incorrectly displays a seven-day Token total and a seven-day chart.

## Design

Pass the validated `rangeDays` value from `NewApiHttpClient` to
`DashboardAnalytics.from`. Build the Token series from `rangeDays - 1` through
zero, filling dates without calls with zero. Keep the empty-data behavior: when
the upstream returned no real rows, return no Token points so the UI continues
to show its empty state rather than invented activity.

The frontend already owns the currently selected, validated range. Render the
Token chart title from that state and continue deriving the Token metric card
from the returned Token series. This makes both controls and all Token displays
share the same window without changing the response contract.

## Error handling and compatibility

The controller continues to accept only `7d` and `30d`. The backend validates
the corresponding day count before it reaches the aggregator. All non-Token
analytics fields retain their existing contract and aggregation behavior.

## Tests

Add a backend regression test using real aggregation code that demonstrates a
30-day response includes 30 Token dates and totals data older than seven days.
Update the frontend dashboard test to assert the 30-day Token chart title and
summary use the selected analytics window. Run the focused backend and
frontend test suites, then the relevant broader module tests.
