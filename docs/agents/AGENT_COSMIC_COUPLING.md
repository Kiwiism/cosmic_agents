# Agent Cosmic Coupling Inventory

Status: audited on `reconstruction/source-master-agent-base` after runtime
classification and scheduler/packet/inventory/shop gateway extraction.

## Enforced Operational Boundaries

Outside `server.agents.integration.cosmic`, Agent production code does not use:

- `server.bots` packages or imports;
- `DatabaseConnection` or direct JDBC persistence;
- `Character.getClient()` or `Character.saveCharToDB()`;
- `PacketCreator`, `ByteBufInPacket`, `ByteBufOutPacket`, or packet opcodes;
- `UseItemHandler` or `ShopFactory`;
- `TimerManager`.

These invariants are enforced by `AgentCosmicBoundaryAuditTest`. The generic
integration interfaces expose semantic operations; Cosmic adapters own the
server-specific implementation.

## Intentionally Retained Domain Coupling

The reconstruction deliberately retains Cosmic domain objects in capability
signatures where replacing them would require a broad gameplay-model rewrite:

| Coupling | Current use | Why retained now | Future adapter direction |
| --- | --- | --- | --- |
| `client.Character`, `Job`, `Skill`, `BuffStat` | Live Agent identity, stats, skills, and policy inputs | They are the authoritative game-domain model and parity depends on their exact semantics. | Introduce read-only snapshots capability by capability only when another host implementation is required. |
| `Inventory`, `Item`, `Equip`, `InventoryType`, `WeaponType` | Inventory/equipment planning and scoring | Selection logic needs exact slot, quantity, requirement, and weapon behavior. Mutations already use gateways. | Add neutral inventory/equipment snapshots after parity fixtures cover every relevant field. |
| `MapleMap`, `Foothold`, `Rope`, `Portal`, `MapItem`, `Monster`, `Reactor`, `Mist` | Navigation, movement, combat targeting, loot, and reactor decisions | These are live world entities; copying them would risk stale identity and map-object semantics. | Add read-only world snapshots while keeping entity handles inside Cosmic adapters. |
| `StatEffect`, `CombatFormulaProvider` | Buff/skill effects and combat scoring | They encode Cosmic gameplay formulas, not infrastructure side effects. | Extract formula interfaces only when supporting a server fork with different formulas. |
| `Shop`, `ShopItem`, `Shop.TransactionResult` | Shop planning and transaction result handling | Lookup and mutations are gateway-owned; these remain read/handle types. | Replace with `AgentShopSnapshot` and an Agent transaction-result enum in a dedicated compatibility milestone. |
| `AbstractDealDamageHandler.AttackInfo` | Attack construction/effect dispatch carrier | Packet dispatch and handler execution are gateway-owned; replacing this carrier would be high-risk combat churn. | Introduce a neutral attack request after full combat packet parity fixtures exist. |
| `PlayerBuffValueHolder` | Read-only active-buff reporting and selection | Used only to inspect authoritative active effect/duration data. | Add an `AgentActiveBuffSnapshot` gateway projection. |
| `ItemInformationProvider` | Legacy equipment optimizer hooks and compatibility overloads | Existing optimizer parity tests and callers use this provider extensively. | Complete migration to `InventoryGateway`/optimizer hooks, then remove provider overloads. |
| `AssignAPProcessor` | Minimum-stat-floor rule | Used as a pure authoritative gameplay rule; it performs no client or persistence mutation here. | Add a build-rules gateway only if AP rules diverge between hosts. |
| WZ `DataProvider` APIs | Navigation/combat data loaders | Capability-specific loaders need exact source data and cache behavior. | Move data-source construction into Cosmic data gateways while retaining parsed Agent models. |
| `EventInstanceManager` | KPQ stage behavior | Exact event-instance variables and script behavior are parity-sensitive. | Introduce a party-quest event gateway as a separate tested extraction. |
| `client.Client` in `AgentClientGateway` and `AgentSpawnCommandExecutor` | Headless creation handle and Cosmic GM command entry signature | Concrete creation is adapter-owned, but the current compatibility contract still returns the established client type. | Introduce an opaque `AgentClientHandle`, then split generic spawn requests from the Cosmic command adapter. |

## Adapter Coverage

The server adapter currently exposes client, character, map, packet, combat,
inventory, shop, trade, party, life, skill, Maker, quest-sync, persistence, and
scheduler gateways. Cosmic implementations own concrete server singletons,
handlers, packet formats, client access, persistence, and timers.

This inventory distinguishes attachability from a full independent game-domain
model. The Agent module is operationally separated, while capabilities still
consume Cosmic's authoritative domain entities to preserve exact behavior.
