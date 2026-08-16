# Agent OS system architecture

This document is the canonical ownership specification for the Agent runtime. When older design
documents describe a universal plan as the owner of every foreground action, this document takes
precedence: the plan executor is the internal engine of Questing, not the owner of TownLife,
Hunting, or Commerce.

## System model

The World Director selects goals. The Activity Host admits and advances one foreground execution
owner. TownLife, Hunting, Questing, and Commerce are independent primary systems. Inventory and
Socials are support systems and do not compete for foreground ownership. Agent Context is a
read-only foundation for identity, personality, relationships, and memory.

```text
operator / schedule / autonomous policy
                  |
                  v
             World Director
                  |
                  v
             Activity Host
       +----------+----------+----------+
       |          |          |          |
   TownLife    Hunting    Questing   Commerce
       +----------+----------+----------+
                  |
       typed capability commands
                  |
       neutral integration gateways
                  |
       authoritative Cosmic mutations

Inventory ------------------ support and remediation
Socials -------------------- interaction leases and protocols
Agent Context -------------- read-only context for every layer
```

Primary systems never start siblings. They publish a typed requirement or successor proposal;
the World Director owns selection, source suspension, transfer, admission, and resumption.

## Migration status

The Activity Host and admission coordinator are the live scheduler boundary. Existing Questing
steps may still retain a suspended plan while a bounded TownLife or Hunting visit executes; those
visit leases are registered compatibility controllers and preserve checkpoint behavior. Commerce
still uses a process-local roster lease, but it is non-interruptible by ordinary activity commands
and can only be released by the Commerce run lifecycle. These are explicit migration seams, not
general APIs for new features.

## Ownership levels

1. **Foreground session** -- the Activity Host exposes one current execution owner. A suspended
   session may retain a checkpoint, but it does not execute.
2. **Capability resource** -- bounded locks serialize movement, combat, NPC, inventory, shop, and
   trade work.
3. **Authoritative mutation** -- only Cosmic gateways mutate character, map, quest, item, meso,
   or transaction state. Retried mutations require idempotent receipts.

## Primary systems

| System | Owns | Does not own |
|---|---|---|
| TownLife | Map-local ambient activities, venues, dwell, encounters, graceful visit exit | Cross-town travel, quest cursors, market policy |
| Hunting | Field allocation, territory, formation, hunt objectives, local combat/loot intent | Quest-chain selection, item disposition, market goals |
| Questing | Quest-driven plan cursor, checkpoint, retry, completion, successors | Combat mechanics, navigation mechanics, TownLife or Commerce internals |
| Commerce | Market intent, valuation, browsing, economic buying/selling/trading | Inventory mutation, farming, quest selection, world travel |

## Support systems

Inventory owns item snapshots, reservations, capacity, supplies, equipment/loadout policy,
auto-equip, known-NPC restocking, disposition, storage, upgrading, scrolling, and crafting. It may
perform a bounded local action or request a foreground activity; it may not start one.

Socials owns chat and interaction protocols, fame, buddy/party/guild negotiation, and social trade
lifecycle. Short work uses an interaction lease. Long social goals may become a primary system in
the future, but Socials is not a foreground owner in this baseline.

Trade crosses three explicit boundaries: Socials owns negotiation and the interaction window,
Inventory owns reservations and transfer eligibility, and Commerce owns economic intent and value.

## Agent Context

Agent Context exposes immutable identity, personality, relationship, memory, and preference views.
It never ticks a capability or owns a session. Systems publish facts; context projections decide
what becomes durable memory. Personality may tune a legal policy choice but cannot bypass safety,
mechanics, resource locks, or mutation validation.

## Lifecycle and handoff

The process lifecycle remains CREATED, LOADING, ACTIVE, QUIESCING, SUSPENDED, RELOGIN_BACKOFF,
QUARANTINED, STOPPING, OFFLINE, or FAILED. While ACTIVE, activity handoff is:

```text
preflight destination
  -> request safe source exit
  -> observe exact source release or suspension
  -> transfer and verify arrival
  -> request destination admission
  -> confirm the admitted session identity
```

An activity session projects IDLE, ACTIVE, SUSPENDING, SUSPENDED, DRAINING, COMPLETED, FAILED, or
CANCELLED. Short presentation pauses use the shared effective clock. Maintenance that replaces
foreground work suspends the parent session and later resumes the same correlation.

## Dependency direction

```text
profiles / catalogs / plans
          -> policies and system contracts
          -> primary or support orchestration
          -> typed capabilities and resource locks
          -> neutral gateways
          -> Cosmic adapters
```

Events report facts; proposals ask the World Director to consider work; commands request a bounded
capability mutation. No event listener may silently start a sibling system.

## Change and removal governance

Every production package, entry point, tick owner, state, command, event, configuration namespace,
checkpoint, and compatibility adapter must have one owner in
`src/main/resources/agents/architecture/system-ownership.json`.

A compatibility path is removed only after its callers, commands, checkpoints, configuration,
tests, and rollback window have migrated. Shadow implementations are non-mutating. A subsystem
change must pass its own tests and the declared consumer matrix for shared capabilities.

## Compatibility retirement targets

- replace priority-named foreground types with the Activity Host vocabulary;
- replace the process-level Commerce roster lease with per-Agent durable Commerce sessions;
- reduce the live tick gate to lifecycle, common safety, Activity Host, capability, and recovery;
- migrate feature-specific objective authorities into Questing proposals;
- route capability-to-plan reverse imports through neutral contracts;
- move direct capability state out of `AgentRuntimeEntry` one owner at a time;
- keep observation fixtures, test scenarios, and compatibility leases outside primary ownership;
- retire misleading callback facades only after all callers use Inventory or Socials.
