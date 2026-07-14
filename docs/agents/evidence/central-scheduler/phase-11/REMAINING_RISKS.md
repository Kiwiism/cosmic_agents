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
- live shutdown and restart within the configured deadline, confirming the
  structured Agent shutdown report has no failed sessions, remaining
  registrations, or unterminated async lanes;
- restart rollback rehearsal in legacy mode.

The repository-wide baseline test issues recorded in Phase 10 remain separate
from the scheduler-focused release gate.

## Local Validation Boundary

On 2026-07-14, the focused scheduler/runtime suite, the broad Agent regression
suite, package build, and explicit central-sequential/four-shard 2,000-session
gates passed after the final observability changes.

An optional full `mvnw test` run was stopped after exceeding the local
validation window. Before it was stopped it reported failures outside the
scheduler diff: dialogue/supply tests expecting queued messages, one quest
capability expectation, missing generated `tmp/game-catalog` fixtures in this
independent worktree, and a randomized fidget movement assertion. No scheduler
test failed. These repository-wide issues are not accepted as scheduler parity
evidence and should be resolved or supplied in their owning tracks before a
release-wide green-test claim.

## Live-Gate Preflight Boundary

The 2026-07-14 read-only preflight found the packaged server artifact, shared
WZ junction, free login/channel/diagnostics ports, external runtime/cache
redirects, and a running MapleStory client. It correctly rejected the current
configuration because it targets the normal `cosmic` database. Live parity and
soak work must not begin until `config.yaml` points to an explicitly named
disposable database and the preflight passes. No server process or database
connection was started by this check.

A subsequent isolated smoke used a verified disposable clone and passed all
preflight checks. The server accepted a localhost v83 login connection and
shut down with no remaining Agent work or unterminated executors. Windows
capture timed out before character authentication, so the complete live parity
script and every populated soak stage remain required.
