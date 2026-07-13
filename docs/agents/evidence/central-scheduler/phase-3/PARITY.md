# Phase 3 Parity

- Legacy per-Agent scheduling is unchanged and remains the default.
- Central-sequential still invokes the same guarded full tick callback.
- First due time and periodic cadence remain unchanged.
- Equal due times retain stable registration order.
- A long pause executes once and advances to the next future cadence; missed
  periods are not replayed.
- A positive work-count cap continues with an overdue Agent on the next cycle.
- pause, resume, wake, replacement, cancellation, stale-session checks, mailbox
  close checks, and failure isolation remain in place.
- cancellation and replacement now retain internal ownership until queued heap
  cleanup is consumed; the public handle still completes immediately.

The heap orders records by due time before registration sequence. In normal
periodic operation due times are equal and observable order is unchanged. When
different records are already overdue, the oldest due record is selected first;
this is the required due-time scheduler policy and does not alter capability or
gameplay outcomes. Live-client parity remains required before any default mode
change.
