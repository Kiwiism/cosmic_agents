# Phase 9 Parity

- Load shedding is disabled by default.
- Legacy and central scheduling retain their existing compact/sliced tick when
  no load level is active.
- Visible and lifecycle-critical registrations always execute.
- A background session with mailbox/completion work executes at every level.
- Leader-directed replies remain admitted.
- Navigation graph work remains admitted while LLM/catalog/economy work can be
  rejected at the documented level.
- Replacement sessions bypass new-population admission to preserve lifecycle
  recovery.
- Intentionally shed ready work waits for the periodic loop and cannot create
  an immediate-wake storm.

Automated policy, scheduler, dialogue, async, registry, lifecycle, and broad
Agent parity tests pass. Live-player responsiveness under overload remains a
required rollout gate.
