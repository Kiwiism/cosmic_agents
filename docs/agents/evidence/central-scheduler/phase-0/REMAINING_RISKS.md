# Central Scheduler Phase 0 Remaining Risks

## Blocking operations

| Location | Classification | Required phase |
| --- | --- | --- |
| `AgentChatMailboxDispatcher.result.get` | packet/event-loop compatibility wait | Phase 2: asynchronous reply |
| `AgentNavigationGraphService.join` | blocking graph API | Phase 5: async completion and reachability guard |
| `AgentTickScheduler.Registration.get` sleeps | compatibility `Future` wait; must never run on scheduler worker | Phase 1/3: nonblocking handle contract |

## Delayed callbacks and timers

| Owner | Classification | Required phase |
| --- | --- | --- |
| `AgentTickScheduler` central loop | global Agent scheduler loop | Phase 3 replacement |
| `AgentPopulationScheduler` steady/fast-start work | global Agent maintenance | retain behind scheduler maintenance work |
| `AgentNavigationDebugOverlay` auto-clear | presentation cleanup | documented server-timer exception or presentation lane |
| `AgentAirshowService` frames/trail cleanup | session-scoped presentation mutation | Phase 2 mailbox/scoped wake migration |
| `AmherstPlanCommandService` delayed showcase start | session-scoped plan action | Phase 2 mailbox/scoped wake migration |
| `CosmicSchedulerGateway` | TimerManager adapter | retained for legacy and non-session server timing during rollout |

## Ownership and lookup

- `AgentRuntimeRegistry.isActiveSession`, `findByAgentCharacterId`, and active
  leader lookup scan leader-owned lists. Phase 1 must add an O(1)
  generation-safe session index and lifecycle-owned registration API.
- callers can currently mutate `entriesByLeaderId()` and
  `mutableEntriesForLeader()` directly. Phase 1 must migrate production
  lifecycle writes without breaking read compatibility.

## Mailbox ingress

- the mailbox is bounded and generation-stamped.
- mailbox mode is disabled by default.
- only chat currently submits through `AgentMailboxRuntime`.
- enqueue does not wake a central scheduler owner.
- external command, follow, navigation, trade, shop, party, plan, and async
  completion mutation families require a Phase 2 inventory and migration.

## ScheduledFuture ownership

Per-Agent lifecycle tick futures, delayed-task scopes, population timers,
navigation overlay cleanup, the central dispatcher loop, and the Cosmic timer
adapter still expose `ScheduledFuture`. This is expected at baseline; final
central-sharded mode must not allocate one repeating future per Agent.

## Validation gaps

- no live-client run was performed for Phase 0.
- no 500-player/2000-Agent server load was attempted.
- no thread-affinity claim is made for Cosmic mutation gateways.
