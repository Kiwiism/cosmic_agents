# Phase 4 Parity

- Legacy scheduling remains unchanged and is still the default.
- The central scheduler still invokes the same guarded full Agent tick.
- Current production ticks are visible gameplay, preserving their relative
  precedence and avoiding learned-cost rejection.
- Work beyond a count or time budget is retained in a bounded ready queue and
  receives an immediate continuation; it is neither dropped nor replayed.
- Equal-priority ready work retains due-time and registration-sequence order.
- Missed periodic cadence still coalesces.
- failure isolation, pause/resume, wake, replacement, cancellation, stale
  generation checks, and mailbox ownership remain unchanged.
- starvation aging never promotes ordinary work into critical lifecycle
  priority.

No visible gameplay behavior change is intended. Live-client parity remains a
gate before any default switch.
