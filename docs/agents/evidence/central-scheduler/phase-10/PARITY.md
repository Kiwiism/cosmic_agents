# Phase 10 Parity

- Normal ticks and mailbox FIFO behavior are unchanged without an explicit
  quiescence request.
- The legacy per-Agent timer remains the default and uses the same barrier.
- A request never abandons a partially executed bounded tick frame.
- Ordinary actions accepted during a barrier remain queued for resume.
- Already-running async work returns through its existing generation/request-
  stamped completion handler before the token succeeds.
- Timeout and lifecycle closure return structured failures, never a fake token.
- Session invalidation between request and scheduler execution returns
  `STALE_SESSION` and restores ordinary mailbox execution.
- No gameplay outcome, Cosmic schema, WZ data, profile data, or command behavior
  changed.

Focused and broad scheduler/lifecycle/capability tests pass. Live profile
exchange parity is not applicable because that production feature is absent.
