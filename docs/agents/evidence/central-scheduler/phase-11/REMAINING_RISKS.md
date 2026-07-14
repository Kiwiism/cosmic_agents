# Phase 11 Remaining Risks

Do not change the production default until all of these are recorded:

- one-client climb, dialogue, trade, death, recovery, and cross-map-transition
  parity; spawn, same-map movement/navigation, basic combat, and basic
  loot/meso behavior have one authenticated smoke pass;
- two-client packet and position consistency;
- player login, map change, combat, NPC, trade, and shop responsiveness under
  500/1,000/1,500/2,000-Agent load;
- mixed presentation/background transition and materialization validation;
- load-shedding escalation and recovery without a wake-up storm;
- 2,000-Agent 8-hour, 24-hour, and multi-day heap/GC/queue/latency evidence;
- loaded shutdown/restart behavior beyond one Agent; the one-Agent live
  shutdown drained one session with zero remaining registrations, pending
  async work, or unterminated lanes in 631 ms;
- restart rollback rehearsal in legacy mode.

Server-only legacy, central-sequential, and four-shard runs now provide a
250-session loaded-shutdown sample. All three cancelled 250 sessions and left
zero scheduler registrations, pending async work, or unterminated lanes. The
formal 250 gate still requires observing clients, and restart/rollback remains
unrehearsed. A planned central-sharded-to-legacy restart rehearsal was not
started after preflight observed less than the new 4 GiB populated-stage memory
floor; the normal configuration was restored without starting a server.

The current population runtime only reconciles explicitly managed, eligible
backing characters from the external `population.json`. The checked-in soak
population preset is data-only, and the dedicated soak-harness specification
forbids live integration until its simulation, background-action,
observability, cleanup, and test-Agent identification gates exist. A guarded
test-only roster provisioner now prepares real Agent-only backing characters
through the normal creation gateways on an explicitly named disposable
database. It is not a synthetic population path, is not production-exposed,
and does not by itself count as live 2,000-Agent evidence.

A read-only 2026-07-14 audit of `cosmic_scheduler_soak_20260714` initially found
30 characters but zero characters on Agent-only backing accounts. The live
smoke safely provisioned one Agent-only backing character through the normal
guarded GM command, which is enough for the single-Agent smoke but not any
staged population gate. The guarded provisioner removes the tooling gap, but
the roster still must be created and verified before each populated gate. A
500-character roster has now been created and exercised server-side in both
central-sequential and four-shard modes. A 1,000-character roster is also
provisioned, but its first runtime gate was aborted at 220 sessions by the host
memory guard. The server itself remained below 1 GiB; a separate desktop
process held most physical memory. The 1,000 gate remains open, and 1,500 and
larger rosters remain unprovisioned.

Population convergence starts at most 20 sessions per sweep. The fast-start
window launches the first 140 sessions, then steady reconciliation runs once
per minute. Population lifecycle I/O now executes on a bounded single-worker
async lane, and repeated timer wake-ups coalesce while work is outstanding.
The default action limit and ordering are unchanged. Larger stages still need
measured convergence evidence before any rate tuning is considered.

One short 500-Agent process sample showed materially higher working/private
memory in the four-shard process even though its periodic heap sample was
lower. The run was not normalized for cache state, GC, or paging, so this is
not evidence of a scheduler leak. Retain it as a risk until repeated sustained
stages show a heap plateau and comparable post-GC process memory.

The next 1,000-Agent attempt requires a clean host with at least 8 GiB reliably
free after tooling is started. The live-gate preflight now enforces that floor
automatically for 1,000+ Agent stages, applies smaller safety floors to lower
stages, and reports the measured host memory. Do not weaken the 2 GiB in-run
abort threshold or continue to larger stages merely to obtain a nominal
population count.

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

A subsequent isolated smoke used the verified disposable clone, passed all
preflight checks, authenticated a v83 client, and ran one Agent under
central-sharded scheduling. Spawn/party visibility, same-map follow/navigation,
basic combat, meso pickup feedback, diagnostics, and live-Agent shutdown passed.
The remaining one-client capabilities, two-client consistency, every populated
soak stage, and rollback rehearsal are still required.
