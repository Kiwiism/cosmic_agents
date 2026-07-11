# NuTNNuT Navigation And Physics Port

This ledger tracks correctness and presentation fixes selected from NuTNNuT
branches for the reconstructed Agent runtime. Simulation LOD is deliberately
excluded; see `simulation-tier-runtime/NUTNNUT_LOD_COMPARISON.md`.

## Search And Warmup Cost Controls

Source references: `e8f7b9e23e`, `6c8896f987`, `46b1d9a9f5`, and
`509e53707f`.

- Path search exits immediately across disconnected graph components and caps
  edge checks. Committed movement can retain its closest reached frontier at
  the cap; scoring and reachability probes never accept partial routes.
- Retreat and approach probes use the goal heuristic while ordinary committed
  route behavior and target scoring retain their established search modes.
- Test graph caches live under Maven's build directory, isolated from runtime
  caches. Warmup workers run at minimum Java thread priority.

## Directional Walk-Off Reliability

Source references: `a9c33a1a4f`, `f5787ab0c6`, and `362926fdf9`.

- Fallback ledge waypoints retain an explicit walk-off marker, so the ground
  planner uses zero stop and follow distance and walks through the ledge.
- A committed directional drop is accepted only when a live walk-off
  simulation reaches its target region. Merely crossing the authored runway's
  x-coordinate is insufficient because the Agent may be on another foothold.
- Graph generation samples fractional position, ground-step carry, standing,
  and terminal-speed launch states. It authors the edge only when every sample
  lands in the same target region.
- Navigation graph cache version 52 invalidates older single-sample drop edges.

These changes preserve valid route costs and ordinary ground movement while
removing edges and execution shortcuts that cannot be reproduced by live
physics.

## Climb And Swim Recovery

Source references: `e9788de7cf`, `f424904a88`, and `3516aa238b`.

- A climbing Agent retains committed CLIMB exits across ambiguous ground
  readings, but drops a stale ground JUMP whose source is another region.
- A grounded Agent on a swim map jumps into the water when a wall blocks the
  direct ground step.
- Swim collision records a wall hit only while horizontal steering is active.
  The following intent tick requests a cooldown-gated upward burst and holds up
  between bursts, preventing repeated horizontal pressure into the wall.

## Observer-Aware Presentation And Central Settle

Source references: `ddfd9cb51e` and `03622d9d18`.

- Each map maintains an O(1) count of real-client observers. Headless Agent
  clients do not count; hidden GMs do.
- Agent-originated map packets are skipped when no real player can render them.
  Movement invalidates its dedup snapshot so the first observed tick publishes
  fresh state. Null-source Agent chat and cosmetic airshow trails are gated at
  their call sites before packet or trail-object allocation.
- The common Agent tick resets a movement-reconciliation bit and runs one
  grounded idle step only when a previously moving Agent completes a tick
  without any movement reconciliation. This clears stale WALK presentation
  without adding handler-specific settle calls or steady-state packet spam.

## Portal Approach And Spawn Fall

Source references: `569862626d` and `49cec7e234`.

- Collision portals resolve a real rope or platform point within their trigger
  box instead of assuming the WZ portal center is standable. The Agent graph
  uses that point and its rope/ground region for same-map portal edges. The
  service is also the reusable approach boundary for a future cross-map travel
  capability; the removed legacy travel manager is not recreated.
- Map transitions retain a portal spawn point when the floor is more than 12
  pixels below it, initialize zero-velocity airborne state, and let ordinary
  Agent gravity land the character. Near-ground and missing-ground transitions
  preserve the existing snap behavior.
- Navigation graph cache version 53 invalidates portal edges authored against
  unstandable collision-portal centers.
