# Agent Gameplay Track

This track contains Agent gameplay capability, correctness, and autonomy work.
It is intentionally separated from the scaling track. The current
post-reconstruction order is capability proof first, then scaling.

## Gameplay Scope

Gameplay packages include:

- plan runtime.
- capability runtime.
- NPC quest interaction.
- Maple Island MVP.
- recovery policy.
- combat and loot objective behavior.
- inventory/item policy.
- skill and buff parity.
- shop/trade/economy behavior.
- profile-driven plan selection.
- LLM control.
- population behavior.

## First Gameplay Milestone

Phased implementation authority:

- `docs/agents/AMHERST_MVP_PHASED_IMPLEMENTATION_PLAN.md`

Before scaling starts, the first gameplay milestone is:

```text
Amherst MVP smoke test, then Maple Island questline MVP
```

Required package cut:

- plan card loader/progress/objective runner.
- capability command/result/audit model.
- Maple Island catalog runtime slice.
- quest read/start/complete capability.
- NPC quest interaction validator/executor.
- navigation objective adapters.
- portal verification and Shanks/Lith Harbor guard.
- inventory count/free-slot/protected quest item reads.
- Roger apple use-item objective.
- combat kill stop condition.
- loot item stop condition.
- recovery retry/block policy.
- Pio reactor support or clean blocker/debug-only resolver.
- Yoona scripted item support.
- resume/reconcile.
- objective journal.
- full one-Agent integration test.

## Capability-First Baseline

The capability-first baseline is the required bridge between reconstruction and
scaling.

Phases 1 and 2 of this baseline are implemented. Phase 1 provides the
capability runtime, primitive Amherst adapters, live Cosmic gateways, guarded
reset fixtures, and parity/regression tests. Phase 2 adds typed objective
parents, the validated Amherst card loader, durable progress and reconciliation,
declarative handlers, and a deterministic one-objective-at-a-time runner. The
full Amherst run remains Phase 3.

Implementation order:

1. common capability command/result/status/reason-code contracts.
2. capability frame runtime with validation, timeout, retry, cancellation, and
   explicit child-capability handoff.
3. live state reader and server adapter boundaries.
4. Amherst static catalog/runtime slice.
5. primitive capability adapters that delegate to existing reconstructed
   behavior.
6. objective capabilities and minimal Plan Runtime loader/objective runner.
7. Amherst MVP smoke run.
8. Maple Island MVP full run.
9. NuTNNuT-original behavior review and gating.
10. stable Agent engine baseline for scaling.

Capability adapter rule:

```text
Capability = typed command/result wrapper around existing reconstructed
behavior. Do not rewrite movement, combat, loot, or quest internals during the
first extraction unless a test proves a behavior gap.
```

Amherst validates:

- reset/create test Agent flow.
- NPC quest start/complete.
- Roger apple item use.
- combat kill objectives.
- loot item objectives.
- Pio reactor boxes or a clean explicit blocker.
- auto-complete quest handling.
- Shanks/off-island travel guard.
- objective journal and blocker reasons.

Maple Island validates:

- full selected quest sequence.
- Biggs quest start-only behavior.
- Yoona before Mai ordering.
- Southperry stop condition.
- no Shanks/Lith Harbor transition before MVP exit.
- death, no potion, no meso, no item drop, full inventory, and stuck fallbacks.

NuTNNuT-original behavior review happens after the MVPs prove the capability
path. Behaviors should be classified as:

- core retained behavior.
- profile/config-gated behavior.
- legacy-off by default.
- removal candidate after replacement.

## Gameplay Backlog

### Double Agent Profile Switching

Primary doc:

- `docs/agents/DOUBLE_AGENT_POST_RECONSTRUCTION_SPECIFICATION.md`

This is a post-reconstruction review item, separate from the Amherst and Maple
Island capability proofs. The intended production design keeps the player and
Agent actors in place while atomically exchanging owner-aware gameplay profile
bindings, then refreshing client and Agent runtime state. Do not expand the
removed field-copy POC before the reconstruction gates in the specification are
met. Historical findings are retained in
`docs/agents/DOUBLE_AGENT_POC_RETROSPECTIVE.md`.

### Account Quest Inheritance

Primary doc:

- `docs/agents/ACCOUNT_QUEST_INHERITANCE_SPECIFICATION.md`

This post-refactor candidate allows an eligible quest completed by one account
character to be inherited by another profile. The recommended MVP is
status-only, explicitly classified, non-repeatable quest completion with no
duplicated rewards or quest points. Agent integration follows the generalized
quest capability and must retain a fresh-account bypass for capability and
Maple Island testing.

### Maple Island MVP

Primary docs:

- `docs/agents/MAPLE_ISLAND_MVP_DESIGN_SPECIFICATION.md`
- `docs/agents/MAPLE_ISLAND_MVP_TECHNICAL_SPECIFICATION.md`
- `docs/agents/MAPLE_ISLAND_MVP_AGENT_TODO_ALIGNMENT.md`

### Damage And Buff Parity

Includes:

- Magic Guard.
- Meso Guard.
- Power Guard.
- Combo Barrier.
- Achilles.
- High Defense.
- Mana Reflection.
- Battleship HP routing.
- Invincible review.

### Skill Capability Matrix

Includes:

- active attacks.
- support buffs.
- summons/puppets.
- debuffs/control skills.
- utility skills.
- movement skills such as Teleport and Flash Jump.

### NPC / Quest / Shop

Includes:

- generalized quest capability.
- generalized NPC action validation.
- shop interaction.
- reward choice policy.
- script-sensitive review flags.

Prepared but currently unwired capability services:

- `server.agents.capabilities.npc.AgentNpcInteractionCapability`
  - Validates NPC id, map id, catalog placement, optional quest action, range,
    approach point, and dialogue-delay estimate.
  - Uses `NpcCatalogQuery` when available so future plans can choose realistic
    interaction spots without hardcoding map/NPC facts in the plan.
  - Executes only through future `NpcGateway`; without a gateway it returns a
    validated plan result and does not touch live agent/server behavior.
- `server.agents.capabilities.quest.AgentQuestStartCapability`
  and `AgentQuestCompleteCapability`
  - Validate quest status, level, job, prerequisites, required items, required
    kills, progress values, NPC/range, and auto-complete cases from a typed
    `AgentQuestSnapshot` plus `AgentQuestRequirement`.
  - Amherst definitions are available as default requirement metadata for the
    first MVP phase.
  - Execute only through future `QuestGateway`; without a gateway they remain
    dry-run validators.
- `server.agents.capabilities.reactor.AgentReactorInteractionCapability`
  - Plans reactor target selection and required item count, then executes only
    through an optional `AgentReactorExecutionPort`.
  - This keeps Pio-style reactor objectives testable before live runtime wiring.

Post-reconstruction wiring target:

`Objective -> Navigation -> NPC/Quest/Reactor capability -> Cosmic gateway adapter -> QuestState/Inventory validation`

### Inventory / Item Policy

Includes:

- protected item policy.
- inventory capacity planning.
- acquisition option service.
- sell/drop/trade safety.

### Economy / LLM / Population

Includes:

- economy engine runtime.
- LLM gateway.
- profile adaptation.
- relationship memory.
- population director.

## Gameplay Work Deferred During Scaling Track

While focusing on 2000-Agent scaling, avoid starting:

- generalized shop automation.
- full economy runtime.
- LLM gateway.
- full spell matrix.
- advanced defensive buff parity.
- relationship/social graph split.
- population director.

Allowed exceptions:

- minimal gameplay hooks needed to test scaling modes.
- bug fixes that prevent Agent cleanup or cause runaway loops.
- small validation hooks needed for safe background simulation.
