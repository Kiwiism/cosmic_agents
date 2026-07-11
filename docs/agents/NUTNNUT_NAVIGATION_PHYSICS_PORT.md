# NuTNNuT Navigation And Physics Port

This ledger tracks correctness and presentation fixes selected from NuTNNuT
branches for the reconstructed Agent runtime. Simulation LOD is deliberately
excluded; see `simulation-tier-runtime/NUTNNUT_LOD_COMPARISON.md`.

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
