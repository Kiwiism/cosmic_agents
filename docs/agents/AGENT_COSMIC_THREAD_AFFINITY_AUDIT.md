# Agent Cosmic Thread-Affinity Audit

## Scope

This Phase 6 audit classifies every Agent integration gateway contract that is
reachable from the centralized scheduler. The classification is executable:
`AgentGatewayAffinityCatalog` is a closed inventory, gateway types and method
exceptions carry `@AgentGatewayAffinity`, and a source-structure test prevents a
new root gateway contract from silently escaping the inventory.

The classifications mean:

| Classification | Required execution boundary |
| --- | --- |
| `SHARD_SAFE_DIRECT` | May run on the owning Agent shard. The operation uses an existing concurrent Cosmic API and retains one Agent-session writer. |
| `SERVER_EXECUTOR_REQUIRED` | Must be handed to an owning Cosmic executor before mutation. |
| `READ_ONLY_SNAPSHOT` | Reads initialized/template data or an immutable snapshot and performs no live mutation. |
| `ASYNC_EXTERNAL` | May block or outlive a tick and therefore runs on a bounded external lane with stamped mailbox completion. |
| `UNSAFE_PENDING_REFACTOR` | Cannot be reached in multi-shard mode. |

`CENTRAL_SHARDED` refuses registration while the closed catalog contains a
`SERVER_EXECUTOR_REQUIRED` or `UNSAFE_PENDING_REFACTOR` operation. There are no
such operations in the current catalog.

## Gateway Inventory

| Gateway | Classification | Reason |
| --- | --- | --- |
| `AgentClientGateway` | `ASYNC_EXTERNAL` | Headless client/backing-character creation and loading are lifecycle and SQL work. |
| `AgentPersistenceGateway` | `ASYNC_EXTERNAL` | Registry/account queries and writes use the bounded persistence lane. |
| `AgentQuestSyncGateway`, `AgentQuestSyncHandle` | `SHARD_SAFE_DIRECT` | Quest actions target the owning Agent through authoritative quest APIs. |
| `CharacterGateway` | `SHARD_SAFE_DIRECT` | Online lookup, heartbeat, disconnect, and identity use existing concurrent Cosmic services. |
| `CharacterGateway.loadStoredDiseases`, `CharacterGateway.save` | `ASYNC_EXTERNAL` | These method overrides perform persistence work. |
| `CombatGateway` | `SHARD_SAFE_DIRECT` | Synthetic attacks use normal packet handlers and one writer for the acting Agent. |
| `InventoryGateway` | `SHARD_SAFE_DIRECT` | Mutations target the owning Agent and use Cosmic inventory manipulators. |
| `LifeGateway` | `READ_ONLY_SNAPSHOT` | Factory/template monster access does not mutate a live map. |
| `MakerGateway` | `SHARD_SAFE_DIRECT` | Maker validation and inventory mutation target the owning Agent. |
| `MapGateway` | `SHARD_SAFE_DIRECT` | Registration, portal, and map transfer use the same concurrent APIs as client handlers. |
| `MovementGateway` | `READ_ONLY_SNAPSHOT` | Empty extension seam; it currently exposes no mutation. |
| `NpcGateway` | `SHARD_SAFE_DIRECT` | NPC execution validates and mutates the owning Agent session. |
| `PacketGateway` | `SHARD_SAFE_DIRECT` | Channel writes and map broadcasts are thread-safe producer operations. |
| `PartyGateway` | `SHARD_SAFE_DIRECT` | Party mutations use synchronized world/party services. |
| `PrimitiveCapabilityGateway` | `SHARD_SAFE_DIRECT` | Primitive actions preserve live validation and the owning Agent writer. |
| `QuestGateway` | `SHARD_SAFE_DIRECT` | Quest lifecycle mutation is scoped to the owning Agent. |
| `SchedulerGateway` | `ASYNC_EXTERNAL` | It accepts callbacks; delayed work re-enters the generation-bound mailbox. |
| `ShopGateway` | `SHARD_SAFE_DIRECT` | Transactions validate and mutate the owning Agent inventory and meso state. |
| `SkillGateway` | `READ_ONLY_SNAPSHOT` | Skill-provider data is read-only after startup. |
| `TradeGateway`, `AgentTradeInviteGateway` | `SHARD_SAFE_DIRECT` | Trade operations use Cosmic trade synchronization and one acting-Agent writer. |
| `AgentCombatStanceGateway` | `SHARD_SAFE_DIRECT` | It broadcasts the owning Agent's current presentation state. |

## Cross-Session Ownership

Phase 6 routes the known cross-session write families through the destination
Agent mailbox:

- formation offset application;
- inactive-leader safe-mode changes; and
- away/logout sibling stop and delayed disconnect setup.

Sibling scans used for follow-target, potion/ammo donor, combat target, social,
and trade selection remain reads. Any resulting mutation is scheduled or
dispatched against the selected Agent entry.

## Remaining Direct Cosmic Coupling

The affinity catalog covers integration gateway contracts, not every Cosmic
type imported by Agent capabilities. Capability code still directly uses
`client.Character`, inventory types, `server.maps`, monsters, quests, trades,
and packet structures. This is an architectural coupling and remains a future
SPI extraction concern. It is not hidden by the Phase 6 annotations.

The local safety argument is deliberately narrow: those direct paths already
execute concurrently under `LEGACY_PER_AGENT`, the centralized runtime adds one
writer per Agent session, and known sibling writes now enter destination
mailboxes. This does not replace live same-map combat/loot/trade validation or
a sustained race/soak run.

## Rollout Decision

`CENTRAL_SHARDED` is implemented as an explicit opt-in. It is not the default
and is not production-approved. Keep `agents.scheduler.mode=legacy` for normal
operation until live-client parity, same-map capability stress, shutdown,
rollback, and staged 500/1000/1500/2000-Agent soak gates pass.

