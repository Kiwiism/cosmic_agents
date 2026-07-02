# Agent Foundation Structure

The clean Agent base is intentionally neutral. It preserves old behavior during reconstruction, but does not encode old bot architecture as the final design.

Target package groups:

- `server.agents.api`: stable public service/query/command surfaces.
- `server.agents.runtime`: sessions, lifecycle, registry, scheduler, snapshots.
  Current reconstruction runtime boundaries include lifecycle command wiring
  such as `AgentLifecycleChatCommandRuntime` and
  `AgentFormationCommandRuntime`, plus live tick context preparation through
  `AgentLiveTickContextRuntime`, while BotManager remains only a temporary
  compatibility caller for legacy entry points.
- `server.agents.model`: identity, mode, profile, leader reference.
- `server.agents.commands`: command parsing/routing/result boundaries.
- `server.agents.plans`: objective and plan execution framework.
- `server.agents.capabilities`: domain-specific action bins.
- `server.agents.events`: event bus and listener interfaces.
- `server.agents.policy`: replaceable decision rules.
- `server.agents.profiles`: configurable behavior profiles.
- `server.agents.legacy`: exact legacy bot behavior adapters while reconstructing.
- `server.agents.integration`: server-agnostic gateways.
- `server.agents.integration.cosmic`: Cosmic-specific gateway implementations.

Capability bins:

- `movement`
- `navigation`
- `combat`
- `looting`
- `inventory`
- `equipment`
- `supplies`
- `trade`
- `shop`
- `quest`
- `npc`
- `party`
- `dialogue`
- `social`
- `build`

The intended flow is:

`AgentPlan` -> capability -> gateway -> Cosmic server write.

Events describe what happened after actions complete. Listeners may handle dialogue, metrics, plan progress, safety, or UI updates.

