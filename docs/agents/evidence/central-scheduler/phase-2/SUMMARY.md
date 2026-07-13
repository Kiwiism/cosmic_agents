# Phase 2 Summary

Baseline commit: `49fc336de9`

Phase 2 establishes mandatory single-writer mailbox ownership for central
scheduler modes without changing Agent gameplay decisions or cadence.

- `AgentActionMailbox` remains bounded FIFO by default and now exposes typed
  submission status, failure reasons, deterministic expiry, and opt-in
  same-key latest-value coalescing.
- Accepted work wakes the owning scheduler registration; pending immediate
  wake requests coalesce.
- Legacy mode still executes commands inline unless
  `agents.mailbox.enabled=true`; central modes always use the mailbox.
- Chat, whisper, reply-channel selection, follow targeting, formation,
  pending offers, potion requests, Agent equipment packets, mutating Amherst
  commands, and airshow start now cross the immutable mailbox boundary.
- Entry-scoped delayed callbacks validate generation at timer delivery and
  enqueue on the owning mailbox before mutating the session.
- Capability timer bridges no longer expose unused unscoped mutation methods.
- The chat packet path no longer waits up to two seconds for a mailbox result.
- Scheduler handles no longer poll with `Thread.sleep` while awaiting
  cancellation.

Lifecycle admission, replacement, relogin, and removal remain critical facade
operations rather than ordinary mailbox work. Global configuration,
population maintenance, and presentation overlay state are not live-session
mutations.

No gameplay behavior or delay range was intentionally changed. Legacy mode is
the rollback path. Live-client parity remains required before any default mode
change.
