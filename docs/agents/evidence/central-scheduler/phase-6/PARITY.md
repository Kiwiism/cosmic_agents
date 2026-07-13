# Phase 6 Parity

Local deterministic checks cover:

- exactly one update per registered Agent across concurrent shards;
- Agents sharing one map and Agents on distinct maps;
- one failing Agent without stopping other Agents;
- concurrent cancellation and complete registration retirement;
- stable session-to-shard ownership;
- legacy inline behavior for cross-session operations; and
- central-mode destination-mailbox ownership for sibling mutations.

The guarded gameplay tick, period calculation, missed-period coalescing, and
capability order are unchanged. Live packet-visible movement, combat, loot,
trade, and shutdown parity remain required before production rollout.

