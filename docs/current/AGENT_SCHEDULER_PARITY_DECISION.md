# Agent Scheduler Parity Decision

Status: current safety decision as of 2026-07-31.

## Decision

`CENTRAL_SHARDED` remains the no-property production default. `LEGACY_PER_AGENT` remains an explicit restart-time rollback mode and is not removed yet.

The complete deterministic scheduler suite passes for legacy, central-sequential, and central-sharded control paths. Existing evidence also covers deterministic 2,000-session execution and populated 250/500 server-only gates. This is enough to operate the sharded default and continue validation; it is not enough to irreversibly delete the rollback implementation.

## Evidence still required before legacy removal

- two-client packet and position consistency across movement, combat, loot, dialogue, transfer, death, and recovery;
- repeated five-job production-plan parity under the sharded scheduler;
- populated 1,000/1,500/2,000-Agent convergence gates;
- 8-hour, 24-hour, and multi-day heap, GC, queue, latency, shutdown, and restart evidence;
- a populated rollback rehearsal proving a sharded deployment can restart safely in legacy mode.

After those gates pass, remove `LEGACY_PER_AGENT`, its scheduler handle, compatibility property parsing, mode-specific mailbox branches, diagnostics branches, and their tests in one focused migration. Until then the rollback path is deliberate safety infrastructure rather than dead compatibility code.

## Local verification

On 2026-07-31 all 22 scheduler, lifecycle, load-shedding, quiescence, slicing, simulation, diagnostics, and scale-gate test classes passed together. The result proves deterministic implementation parity only; it does not substitute for the live gates above.
