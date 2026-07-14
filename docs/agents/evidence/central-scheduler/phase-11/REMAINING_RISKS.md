# Phase 11 Remaining Risks

Do not change the production default until all of these are recorded:

- one-client visible movement, navigation, climb, combat, loot, dialogue,
  trade, death, recovery, and map-transition parity;
- two-client packet and position consistency;
- player login, map change, combat, NPC, trade, and shop responsiveness under
  500/1,000/1,500/2,000-Agent load;
- mixed presentation/background transition and materialization validation;
- load-shedding escalation and recovery without a wake-up storm;
- 2,000-Agent 8-hour, 24-hour, and multi-day heap/GC/queue/latency evidence;
- live shutdown and restart within the configured deadline;
- restart rollback rehearsal in legacy mode.

The repository-wide baseline test issues recorded in Phase 10 remain separate
from the scheduler-focused release gate.
