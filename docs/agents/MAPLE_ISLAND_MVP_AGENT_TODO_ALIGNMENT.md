# Maple Island MVP Agent TODO Alignment

This document checks current Agent engine TODOs, hardening items, and fixes
against the Maple Island questline MVP package.

MVP target:

```text
Spawn one Agent with maple-island-mvp plan card.
Agent completes selected Maple Island questline.
Agent stops at Southperry.
Agent does not use Shanks to leave Maple Island.
```

## Alignment Summary

```text
Required for MVP:
  must be implemented before the first full Maple Island run can pass.

MVP hardening:
  strongly recommended for reliable runs, but can start minimal and harden.

Post-MVP:
  useful for full autonomy, Victoria progression, LLM, economy, or scale, but
  should not block the first deterministic Maple Island questline.
```

## Required For Maple Island MVP

### Plan Runtime

Aligned: yes.

Required:

- plan card JSON loader.
- `maple-island-mvp.plan.json` assignment.
- objective dependency/status model.
- objective runner.
- plan progress persistence.
- resume/reconcile against live quest/inventory/map state.
- exit criteria check.
- forbidden action gate for Shanks/off-island travel.

### Shared Capability Command / Result Model

Aligned: yes.

Required:

- common command/result/status/reason-code DTOs.
- structured blocker reasons.
- objective audit/journal rows.
- duration, retry count, entity ids.

### Quest Capability

Aligned: yes.

Required:

- read live quest state.
- check start requirements.
- check complete requirements.
- explain unmet requirements.
- start quest through normal server quest API.
- complete quest through normal server quest API.
- reject `1028` completion.
- reject `1046` completion while allowing `1046` start.

### NPC Quest Interaction Capability

Aligned: yes.

Required:

- live NPC presence check.
- map check.
- NPC placement/catalog check.
- range or interaction-box check.
- active plan allowlist check.
- quest requirement validation.
- start/complete execution wrapper.
- result codes like `NPC_MISSING`, `OUT_OF_RANGE`, `BLOCKED_REQUIREMENT`,
  `FORBIDDEN_ACTION`.

### Catalog Runtime Slice

Aligned: yes.

Required:

- Maple Island maps.
- portals.
- NPC placements.
- NPC approach points or fallback interaction boxes.
- quest requirements.
- quest start/complete NPC indexes.
- mobs and spawn maps.
- item sources.
- reactor box metadata for Pio.
- forbidden action index for Shanks/Lith Harbor.

### Navigation / Portal Objective Adapters

Aligned: yes.

Required:

- navigate to map.
- navigate to NPC.
- navigate to portal.
- navigate to point.
- arrival verification.
- stuck retry/block policy.
- hard block on off-island portal/NPC travel before MVP exit.

### Combat Objective Mode

Aligned: yes.

Required:

- quest-focused mob targeting.
- stop when live quest kill count is satisfied.
- stop when item count is satisfied for loot objectives.
- no-mob backoff.
- danger result that can request recovery.

First MVP setting:

```text
spawnPressureClearing = OFF
futureQuestLootPriority = OFF
```

Spawn-pressure/future-loot policy is aligned, but should be enabled after the
first deterministic pass works.

### Loot Objective Mode

Aligned: yes.

Required:

- current quest item priority.
- loot stop condition.
- full inventory blocker.
- unreachable loot blocker.

### Inventory / Item Use

Aligned: yes.

Required:

- item count read.
- inventory free slot read.
- protected quest item policy.
- use item for Roger's Apple `2010007`.
- MVP-safe handling for Yoona guide `4031180`.

### Reactor Interaction

Aligned: yes.

Required for 100% MVP:

- minimal reactor-box interaction for Pio `1008`.
- hit/activate box.
- loot `4031161` and `4031162`.
- retry or block with `capability-missing: reactor-interaction`.

Can be phased:

- If reactor capability is not ready, MVP can block cleanly or use explicit
  debug resolver only in test mode.

### Recovery Policy

Aligned: yes.

Required:

- low HP handling.
- low MP handling.
- death recovery.
- stuck recovery.
- no-progress timeout.
- no potion/no meso rest fallback.
- no Shanks/off-island recovery route.

### Plan Journal / Observability

Aligned: yes.

Required:

- every objective records start/end/status/reason.
- debug resolver usage is recorded.
- blocker reasons are inspectable.

## MVP Hardening But Not Strict First-Pass Blockers

### Protected Item Policy

Aligned: yes.

Minimal MVP:

- protect active quest items.
- protect future selected Maple Island quest items.
- protect Roger apple and Pio/Yoona special items.

Later:

- protect build milestone items.
- protect crafting materials by profile.
- protect market-candidate items by economy value.

### Capability Cancellation

Aligned: partial.

MVP need:

- objective runner can stop current objective when plan is cancelled or blocked.

Full capability-level cancellation for LLM/shop/social sidetracks can wait.

### Per-Capability Timeout / Stuck Policy

Aligned: yes.

MVP needs this in minimal form for:

- navigation.
- NPC approach.
- quest kill/drop.
- reactor.
- recovery.

Shop timeout can wait because generalized shopping is out of MVP scope.

### Live Validation Before Mutating Action

Aligned: yes.

Required for all quest/NPC/item/reactor mutating actions.

### Agent Damage Telemetry

Aligned: useful.

MVP minimum:

- objective journal records death/near-death/recovery.

Full raw/final damage mitigation telemetry can wait unless early runs are hard
to debug.

### Drop Chance / Quantity Server Fix

Aligned: yes, but server-side.

Impact:

- More accurate quest item farming.
- More accurate future economy/catalog validation.

MVP can run without it if current server behavior is accepted, but reliable
drop behavior is better for long test runs.

## Not Required For Maple Island MVP

### Magic Guard / Meso Guard / Defensive Buff Parity

Aligned with future agent combat correctness, not MVP blocker.

Reason:

- Maple Island MVP is beginner/early questline.
- Magic Guard, Meso Guard, Power Guard, Achilles, Combo Barrier, Mana
  Reflection, Battleship routing, and similar advanced mitigation are not
  required for Maple Island completion.

### Full Skill Capability Matrix

Aligned with full autonomy, not MVP blocker.

Reason:

- Maple Island MVP can use basic attacks and simple existing combat.
- Full spell audit is needed for later job progression, Victoria Island,
  level 30 path, and LLM autonomy.

### Teleport / Flash Jump

Aligned with future movement capability, not MVP blocker.

Reason:

- Maple Island MVP does not require mage/thief movement skills.
- Direct `navigate-to-point` is required, but skill-based movement can wait.

### Shop Interaction Capability

Mostly out of MVP scope.

MVP only needs:

- optional recovery uses potions if already available.
- no generalized shop resupply.

General shop buy/sell belongs post-MVP or economy package.

### Economy Engine

Out of MVP scope.

Reason:

- Maple Island MVP explicitly excludes Free Market, trading, and general
  economy behavior.

### LLM Gateway

Out of MVP scope.

Reason:

- First milestone should be deterministic plan execution.
- LLM control can use the plan/capability/profile foundation later.

### Simulation Tiers / Background Abstract Runtime

Out of first MVP scope.

Reason:

- First MVP should run one visible/debuggable Agent with full fidelity.
- Tiered simulation becomes valuable for many concurrent agents later.

### Population Director

Out of MVP scope.

Reason:

- MVP is one assigned Agent, not population behavior.

### Relationship / Social Graph

Out of MVP scope.

Reason:

- Maple Island MVP focus mode disallows normal social sidetracks.

## Small Misalignments To Watch

### Shop Interaction Is Listed In General Agent TODO

Resolution:

- Keep shop capability in general Agent TODO.
- Do not include generalized shop automation in Maple Island MVP.
- MVP recovery can rest/chair instead of shopping if out of potions/no mesos.

### Interaction Realism Is Documented But Disabled For First Run

Resolution:

- Keep realism package and NPC delay/approach policy.
- First MVP config uses `interactionRealism: OFF`.
- Add `LIGHT` only after deterministic completion works.

### Spawn Pressure Clearing Is Valuable But Should Be Off First

Resolution:

- Implement combat stop conditions first.
- Add spawn-pressure/future-loot scoring after deterministic success.

### Debug Resolvers Exist But Must Stay Disabled By Default

Resolution:

- Keep debug resolvers explicit and audited.
- Do not allow arbitrary grant/force-complete.
- Never debug-resolve Shanks travel, `1028`, or `1046` completion.

## Recommended MVP Implementation Cut

Implement only these first:

1. plan card loader/progress/objective runner.
2. capability command/result/audit model.
3. Maple Island catalog runtime slice.
4. quest read/start/complete capability.
5. NPC quest interaction validator/executor.
6. navigation objective adapters.
7. portal verification and Shanks/Lith Harbor guard.
8. inventory count/free-slot/protected quest item reads.
9. Roger apple use-item objective.
10. combat kill stop condition.
11. loot item stop condition.
12. recovery retry/block policy.
13. Pio reactor support or clean blocker/debug-only resolver.
14. Yoona scripted item support.
15. resume/reconcile.
16. objective journal.
17. full one-agent integration test.

Everything else should remain in the broader Agent hardening backlog.

## Conclusion

The current Agent engine TODOs align with Maple Island MVP, but not all of them
belong inside the first MVP implementation package.

The MVP should stay narrow:

```text
Plan runtime + catalog lookup + validated NPC/quest + navigation + combat/loot
stop conditions + inventory/item use + recovery + journal.
```

Advanced skills, defensive buff parity, economy, LLM control, simulation tiers,
population behavior, generalized shopping, and social relationship behavior are
valid future hardening packages, but they should not block the first Maple
Island questline milestone.
