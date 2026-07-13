# Phase 8 Parity

- `AgentTickCoreService.tickCore` runs the same frame to completion, preserving
  the compact and legacy test seam.
- Slice order is preflight, lifecycle, plan/gates, then capability/movement.
- Existing early returns complete the frame without calling later hooks.
- Mailbox work drains once at frame creation, not between continuations.
- Movement settlement and consecutive-failure reset occur once after complete
  success, matching the prior guarded tick.
- A slice exception clears frame state and enters the existing isolated Agent
  failure policy.
- Despawn or replacement clears incomplete frame references.

Automated focused and broad parity suites pass. Live-client movement, combat,
loot, dialogue, and lifecycle parity with slicing enabled remains required
before rollout.
