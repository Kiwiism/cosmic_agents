# Phase 2 External Mutation Inventory

| External source | Final Phase 2 route | Classification |
| --- | --- | --- |
| map/party chat handlers | `AgentChatRouteCoordinator` -> per-session mailbox | ordinary session mutation |
| whisper handler | `CosmicAgentWhisperCommandBridge` -> chat mailbox | ordinary session mutation |
| Agent equipment packet handler | immutable equip request -> mailbox -> async packet reply | ordinary session mutation |
| HP/MP and autopot notifications | coalesced potion-check mailbox action | coalescible session mutation |
| follow and target commands | one resolved mailbox action per selected session | ordinary session mutation |
| formation chat command | mailbox action on the selected session set | cross-session; Phase 6 split required |
| pending trade-offer response | classification at ingress, mutation in mailbox | ordinary session mutation |
| gained-item offer notification | generation-scoped zero-delay mailbox delivery | ordinary session mutation |
| scroll result notification | global read/fanout, then generation-scoped mailbox delivery | event fanout plus session mutation |
| Amherst mutating verbs | mailbox action; delayed showcase is generation scoped | ordinary session mutation; blocking-work audit remains |
| airshow command | async mailbox start; frames are generation scoped | ordinary session mutation |
| spawn/relogin/replacement/despawn/delete cleanup | lifecycle facade | critical lifecycle control |
| navigation overlay and performance/config commands | presentation or global configuration state | not mutable Agent-session state |
| population reconciliation | bounded global population scheduler and lifecycle facade | global maintenance/lifecycle |

Direct production imports of `AgentRuntimeEntry` outside `server.agents` are
limited to the Agent equipment packet adapter, which resolves an active session
and submits an immutable mailbox action rather than mutating it directly.
