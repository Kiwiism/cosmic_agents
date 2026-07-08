# Capability Extraction Prep

Purpose:

```text
Record the prep work for extracting reconstructed Agent behavior into typed
capabilities while preserving NuTNNuT-original behavior until Amherst and Maple
Island MVP tests prove the new boundary.
```

## Extraction Rule

```text
Capability = typed command/result wrapper around existing reconstructed
behavior.
```

The first capability implementation should delegate to current reconstructed
services. Do not rewrite movement, combat, loot, shop, quest, or social
internals during the first extraction unless a focused test proves a behavior
gap.

## Prep Work

1. Define common contracts:
   - `CapabilityCommand`
   - `CapabilityResult`
   - `CapabilityStatus`
   - `CapabilityReasonCode`
   - `CapabilityFamily`
   - `CapabilityContext`
   - `CapabilityFrame`
   - `CapabilityValidator`
   - `CapabilityExecutor`

2. Define capability families:
   - `NAVIGATION`
   - `PORTAL_TRAVEL`
   - `NPC_INTERACTION`
   - `QUEST`
   - `COMBAT`
   - `LOOT`
   - `INVENTORY`
   - `ITEM_USE`
   - `REACTOR`
   - `RECOVERY`
   - `SHOP`
   - `SKILL`
   - `SOCIAL`
   - `TRADE`
   - `DIRECT_CONTROL`

3. Define shared reason codes:
   - `TARGET_MAP_NOT_REACHABLE`
   - `NPC_NOT_FOUND`
   - `NPC_OUT_OF_RANGE`
   - `QUEST_NOT_AVAILABLE`
   - `QUEST_REQUIREMENT_MISSING`
   - `ITEM_MISSING`
   - `INVENTORY_FULL`
   - `MOB_NOT_FOUND`
   - `LOOT_NOT_FOUND`
   - `REACTOR_NOT_FOUND`
   - `FORBIDDEN_BY_SCOPE`
   - `TIMEOUT`
   - `AGENT_DEAD`
   - `AGENT_STUCK`
   - `SERVER_REJECTED_ACTION`

4. Create runtime skeleton:
   - receive command.
   - static validation.
   - live validation.
   - active frame tracking.
   - timeout and retry policy.
   - cancellation.
   - explicit child-capability handoff.
   - structured result mapping.
   - audit event output.

5. Create live state reader interfaces:
   - `AgentLiveStateReader`
   - `QuestStateReader`
   - `InventoryStateReader`
   - `MapStateReader`
   - `NpcStateReader`
   - `MobStateReader`
   - `ReactorStateReader`

6. Create static catalog lookup interfaces:
   - `QuestCatalogLookup`
   - `NpcCatalogLookup`
   - `MapCatalogLookup`
   - `MobCatalogLookup`
   - `ReactorCatalogLookup`
   - `ItemCatalogLookup`

7. Create adapter shells around existing behavior:
   - navigation adapter.
   - quest/NPC adapter.
   - inventory/item-use adapter.
   - combat/loot adapter.
   - reactor adapter.
   - recovery adapter.

8. Add capability audit records:
   - agent id.
   - plan id.
   - objective id.
   - capability family.
   - command id.
   - start/end time.
   - status.
   - reason code.
   - live-state-changed flag.
   - evidence map.

9. Add feature flags:
   - `ENABLE_AGENT_CAPABILITY_RUNTIME=false`
   - `ENABLE_AGENT_PLAN_RUNTIME=false`
   - `ENABLE_AGENT_AMHERST_MVP=false`
   - `ENABLE_AGENT_CAPABILITY_AUDIT=true`

10. Prepare Amherst MVP tests:
    - start and complete NPC quests.
    - use Roger apple.
    - kill required mobs.
    - loot required items.
    - hit or resolve Pio reactor boxes.
    - block Shanks/off-island behavior.
    - produce objective journal and blocker reasons.

## Components To Wrap As Capabilities

Core gameplay:

- navigation.
- portal travel.
- combat.
- loot.
- inventory.
- item use.
- NPC interaction.
- quest start/complete.
- reactor interaction.
- recovery.

Support and later capabilities:

- skill and buff.
- supplies.
- equipment.
- build/AP/SP.
- shop.
- trade.
- maker/craft.
- social/dialogue.
- party.
- party quest.
- LLM/direct control.

## Components That Stay Runtime Infrastructure

Do not turn these into capabilities:

- tick core and tick scheduler.
- runtime registry and runtime entry identity.
- lifecycle/spawn/relogin/offline load.
- scheduled task runtime.
- performance monitor and diagnostics.
- server adapter and gateways.
- low-level state adapters.

Capabilities may use these systems, but these systems should not become the
Plan/LLM command API.

## Post-MVP Legacy Review

After Amherst and Maple Island MVP pass through capabilities, classify
NuTNNuT-original behavior:

- retain as core behavior.
- keep behind profile/config gates.
- disable by default as legacy.
- remove later after replacement.

Likely behavior gates:

- social chatter.
- fidget and airshow.
- owner-follow assumptions.
- auto-equip.
- potion/ammo sharing.
- gear offers.
- trade helpers.
- shop visit behavior.
- LLM reply behavior.
