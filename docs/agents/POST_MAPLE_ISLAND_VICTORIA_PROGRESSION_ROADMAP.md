# Post-Maple-Island Victoria Progression Roadmap

Current implementation sequencing and the consolidated level-30/level-70
autonomy gate are defined in
`docs/agents/TIER_1_INDEPENDENT_AGENT_PROGRESSION_IMPLEMENTATION_PLAN.md`.
This document remains authoritative for the detailed Explorer career/AP/SP/gear
composition and build matrix referenced by that plan.

## Status

This roadmap is intentionally deferred until the following Maple Island gates
are complete:

1. current post-Amherst Southperry MVP validation;
2. full clean level-1 Maple Island-to-Southperry plan;
3. profile-driven realism validation and the staged 100-Agent completion run.

The purpose of recording it now is to preserve the intended direction without
mixing Victoria progression changes into the active Maple Island work.

Related documents:

- `docs/agents/MAPLE_ISLAND_REALISM_AND_100_AGENT_VALIDATION_PLAN.md`
- `docs/agents/VICTORIA_LT30_QUEST_EDGE_CASE_REVIEW.md`
- `docs/agents/catalog-overrides/VICTORIA_LT30_QUEST_STATUS_CATALOG.md`
- `docs/agents/llm-autonomy/AGENT_PROFILE_SCHEMA.md`
- `docs/agents/llm-autonomy/PROFILE_PLAN_SET_SYSTEM.md`
- `docs/agents/profile-platform/AGENT_PROFILE_SYSTEM_DESIGN_SPECIFICATION.md`
- `docs/agents/profile-platform/PROFILE_RUNTIME_ARCHITECTURE.md`

## Long-Term Milestone

```text
A fresh Agent completes Maple Island, chooses a configured Explorer career and
build, advances through the real first-job flow, allocates AP and SP correctly,
equips compatible gear, quests or grinds according to profile, and reaches
level 30 ready for the configured second job.
```

This should work without an LLM. An LLM may later assign goals or explain
choices, but deterministic profile, catalog, plan, and capability behavior must
be sufficient.

## Current Foundation

### Already Available Or Partially Available

- proven Maple Island objective, navigation, NPC, quest, combat, loot,
  inventory, reactor, portal, reset, and progress-journal paths;
- legacy-derived `AgentBuildService` AP assignment using a primary stat,
  secondary stat, and fixed secondary target;
- static SP build tables for Warrior, Bowman, the claw Assassin path, and
  selected Magician paths;
- direct chat-driven job changes and first-job starter-kit grants;
- equipment compatibility and auto-equip services;
- combat, potion, ammo, buff, skill, shop, and recovery behavior that can be
  reused after capability and profile validation;
- a Victoria `<30` catalog with `104` quests currently classified as normal and
  additional quests behind known capability or review gates.

### Important Limitations

- current AP/SP selections are live runtime state rather than durable,
  data-driven Agent build-profile bindings;
- first-job advancement is a direct chat-triggered job change, not an
  autonomous NPC/quest plan with live prerequisites and route validation;
- AP allocation supports a fixed secondary target but not level formulas,
  equipment-aware caps, staged targets, or multiple named variants;
- SP build tables are Java code, not editable profile data;
- Bandit, Fire/Poison, and Pirate SP paths are not covered by the current static
  build tables;
- the current Thief table represents one claw-oriented first-job order and
  cannot express Sindit or dagger-first variants as data;
- gear intent is not yet a separate durable policy linked to AP and career
  requirements;
- the Amherst-named objective runtime still contains milestone-specific
  concepts that should be generalized before many Victoria plan families are
  added;
- plan selection does not yet combine career, build, quest, grind, farm, idle,
  economy, and personality preferences into one autonomous lifecycle.

The current build implementation is useful reference behavior. It should be
wrapped and data-driven before it becomes the source of truth for autonomous
progression.

## Recommended Composition Model

Do not create one giant profile JSON for every possible combination. Compose a
character build from smaller reusable profiles.

```text
career profile
  target job path and advancement decisions

AP build profile
  stat allocation rules and caps

SP build profile
  ordered skill targets with prerequisites

gear policy
  compatible weapon/armor requirement tags and acquisition preferences

behavior profile
  quest/grind/farm/idle preferences, timing, movement, risk, and social style

plan set
  plans that this Agent may select at its current lifecycle stage
```

An assigned Agent build bundle references those components:

```json
{
  "buildBundleId": "dexless-assassin-quester-v1",
  "careerProfileId": "explorer-assassin-v1",
  "apBuildProfileId": "thief-dexless-luk-v1",
  "spBuildProfileId": "rogue-claw-first-v1",
  "gearPolicyId": "thief-dexless-claw-v1",
  "behaviorProfileId": "careful-quester-v1",
  "planSetId": "victoria-assassin-level-10-30-v1"
}
```

This composition naturally creates Sindit and low-stat variants without
duplicating all skill, gear, and behavior data.

## Why Gear Should Be Separate

AP and SP profiles should declare requirements, not an exact shopping list.

Good AP profile output:

```text
base DEX remains 25
effective DEX must satisfy the selected weapon
prefer weapons with no DEX requirement
do not spend AP merely because a currently unavailable item would need DEX
```

Good gear policy output:

```text
weapon family: claw
required build tags: dexless-compatible
acceptable sources: quest, drop, shop, market, trade
quality tolerance: profile-dependent
fallback: keep current compatible weapon and postpone upgrade
```

The acquisition planner can then choose a real item from the catalog and live
economy. This avoids hard-linking one AP build to one item ID and allows the
same build to survive different server drop tables or market conditions.

## Build Profile Data Contracts

### Career Profile

```json
{
  "careerProfileId": "explorer-assassin-v1",
  "jobPath": ["BEGINNER", "THIEF", "ASSASSIN", "HERMIT", "NIGHTLORD"],
  "firstJobLevel": 10,
  "secondJobLevel": 30,
  "firstJobPlanId": "first-job-thief-v1",
  "secondJobPlanId": "second-job-assassin-v1"
}
```

### AP Build Profile

```json
{
  "apBuildProfileId": "thief-dexless-luk-v1",
  "primaryStat": "LUK",
  "secondaryStat": "DEX",
  "secondaryRule": {
    "mode": "fixed-cap",
    "baseTarget": 25
  },
  "validation": {
    "minimumStatsFromServer": true,
    "neverSpendPastCap": true,
    "holdApWhenRuleCannotResolve": true
  }
}
```

Future secondary rules may include:

- `fixed-cap`;
- `level-offset`;
- `equipment-requirement`;
- `staged-cap-by-level`;
- `effective-stat-target` using equipped stat bonuses.

### SP Build Profile

```json
{
  "spBuildProfileId": "rogue-claw-first-v1",
  "job": "THIEF",
  "steps": [
    { "skill": "LUCKY_SEVEN", "target": 1 },
    { "skill": "NIMBLE_BODY", "target": 3, "reason": "Keen Eyes prerequisite" },
    { "skill": "KEEN_EYES", "target": 8 },
    { "skill": "LUCKY_SEVEN", "target": 20 },
    { "skill": "NIMBLE_BODY", "target": 20 },
    { "skill": "DISORDER", "target": 3, "reason": "Dark Sight prerequisite" },
    { "skill": "DARK_SIGHT", "target": 10 }
  ]
}
```

This example captures the requested claw-first direction while inserting the
known prerequisite steps. Exact skill IDs, maximum levels, prerequisites, and
total available SP must be validated from the server's Skill/WZ data before
the JSON becomes executable.

SP execution rules:

- validate every step against the configured job and skill tree;
- validate prerequisite skills before spending;
- never spend into a later job book;
- never lose or create SP during profile changes;
- reconcile already learned skills and continue from the first unmet target;
- return a clear blocker when an existing character cannot satisfy the target
  without a respec;
- keep respec as an explicit test/GM operation, not normal autonomy.

### Gear Policy

```json
{
  "gearPolicyId": "thief-dexless-claw-v1",
  "weaponFamilies": ["CLAW"],
  "requirementPolicy": "effective-stats-must-pass",
  "preferredTags": ["no-dex-requirement", "low-dex-requirement"],
  "acquisitionOrder": ["quest", "drop", "shop", "market", "trade"],
  "allowTemporaryCompatibleGear": true,
  "doNotSpendApForOneTemporaryUpgrade": true
}
```

## Explorer Build Matrix

The matrix below defines the minimum profile families. Exact AP formulas and SP
orders remain editable data and must be validated before use.

### Warrior

AP variants:

- `warrior-full-str`: minimum DEX only;
- `warrior-low-dex`: capped base DEX with gear-assisted accuracy;
- `warrior-regular-dex`: level/equipment-aware DEX target.

Career profiles:

- Fighter;
- Page;
- Spearman.

Gear policies should be separate for one-handed sword, two-handed sword,
blunt weapon, spear, polearm, and shield choices where relevant.

### Magician

AP variants:

- `mage-full-int`: minimum LUK only;
- `mage-low-luk`: capped LUK with gear assistance;
- `mage-regular-luk`: level/equipment-aware LUK target.

Career profiles:

- Fire/Poison Wizard;
- Ice/Lightning Wizard;
- Cleric.

All three may share a validated first-job Magician SP profile initially, then
branch at level 30.

### Bowman

AP variants:

- `bowman-strless`: minimum STR only;
- `bowman-low-str`: capped STR with gear assistance;
- `bowman-regular-str`: weapon-requirement-aware STR target.

Career profiles:

- Hunter;
- Crossbowman.

Bow and crossbow gear policies must remain distinct even when the first-job SP
profile is shared.

### Thief

AP variants:

- `thief-dexless`: base DEX cap, commonly 25 after live validation;
- `thief-low-dex`: capped base DEX with equipment assistance;
- `thief-regular-dex`: equipment/level-aware DEX target.

First-job SP profiles:

- `rogue-claw-first`: Lucky Seven and Keen Eyes path;
- `rogue-dagger-first`: Double Stab path;
- optional balanced/test profile only if there is a clear gameplay purpose.

Career profiles:

- Assassin;
- Bandit.

Composition examples:

| Name | AP | First-job SP | Career |
| --- | --- | --- | --- |
| Dexless Sin | dexless | claw-first | Assassin |
| Low-DEX Sin | low DEX | claw-first | Assassin |
| Regular Sin | regular DEX | claw-first | Assassin |
| Dexless Dit | dexless | dagger-first | Bandit |
| Low-DEX Dit | low DEX | dagger-first | Bandit |
| Regular Dit | regular DEX | dagger-first | Bandit |
| Sindit | dexless, low, or regular DEX | claw-first | Bandit |

`Sindit` is therefore not a separate AP allocator. It is a claw-first Rogue SP
profile combined with a Bandit career target and a compatible AP/gear policy.

### Pirate

Gunslinger AP variants:

- `gunslinger-full-dex`: minimum STR only;
- `gunslinger-low-str`: capped STR with equipment assistance;
- `gunslinger-regular-str`: gun-requirement-aware STR target.

Brawler AP variants:

- `brawler-full-str`: minimum DEX only;
- `brawler-low-dex`: capped DEX with equipment assistance;
- `brawler-regular-dex`: knuckle-requirement-aware DEX target.

Career profiles:

- Gunslinger;
- Brawler.

Pirate needs new first-job and second-job SP profile data because the current
static Agent build tables do not cover that branch.

## Progression Plan Families

### Phase 1: Generalize The Proven Plan Runtime

Extract a reusable plan runtime from the successful Maple Island path:

- generic plan card loader and validator;
- generic progress journal and reconciliation;
- generic objective handler registry;
- generic command/status/report surface;
- segment composition without copying Amherst-specific code;
- profile and plan-set binding at Agent spawn/resume.

Maple Island remains the regression fixture for every extraction.

### Phase 2: Victoria Arrival

Create a small plan that:

- completes the permitted transport from Southperry;
- reaches Lith Harbor normally;
- completes the active Biggs/Victoria handoff when appropriate;
- records the Agent's selected career and build bundle;
- travels toward the correct first-job town;
- does not advance the job directly from a profile setter.

### Phase 3: Five First-Job Plans

Create one functional plan for each Explorer first job:

- Warrior at level 10;
- Magician at level 8;
- Bowman at level 10;
- Thief at level 10;
- Pirate at level 10.

Each plan must use the real route, NPC, quest/script, requirement, and job
change path supported by the server. The profile chooses the target; the job
advancement capability validates and performs it.

### Phase 4: Build Profile Platform V1

- JSON schemas for career, AP, SP, gear, and build bundle;
- loader and validation report;
- durable Agent-to-profile assignment;
- runtime snapshot and versioning;
- AP and SP capability adapters;
- reconciliation after relog/restart;
- editable starter templates for every matrix row;
- migration adapter from current `AgentBuildService` static behavior.

### Phase 5: Level 8/10 To 30 Plan Sets

Build one progression plan set per first job, then branch by profile.

Each plan set may mix:

- quest chains classified runnable by the Victoria catalog;
- safe training maps by level and job;
- resupply/shop plans;
- equipment acquisition plans;
- travel and return-to-town plans;
- bounded idle/social plans;
- recovery plans.

The first version should be deterministic and narrow. Weighted quest/grind/
farm/idle choices come after each job reaches level 30 reliably.

### Phase 6: Victoria Quest Library

Turn cataloged quests into reusable plan cards or quest-chain segments.

Scheduling rules:

- `normal`: may run after core capability validation;
- known capability gate: run only when the matching capability is enabled;
- outside-current-region: accept/postpone only when profile policy allows;
- manual/script/source review: never schedule autonomously;
- event/admin: disabled unless a world event policy enables it.

Start with quests shared across jobs, then add town and class-relevant sets.

### Phase 7: Level-30 And Second-Job Readiness

The level-30 milestone should verify:

- selected career is still feasible;
- AP and SP match the assigned build versions;
- compatible weapon and required consumables are available;
- second-job advancement prerequisites are met;
- the Agent can complete the configured second-job path;
- the next plan set is assigned atomically after advancement.

## Missing Or Incomplete Capabilities

| Area | Required outcome |
| --- | --- |
| Generic plan runtime | Maple Island and Victoria plans share one loader, runner, journal, resume, and handler model. |
| Career selection | Profile resolves a valid target job path and persists it. |
| Job advancement | Navigate to real NPC/test flow, validate level/job/prerequisites, perform normal job transition, and verify result. |
| AP allocation | Execute named fixed, staged, level-based, or equipment-aware stat rules without hidden respec. |
| SP allocation | Execute data-driven ordered targets with prerequisite, skill-book, and existing-skill reconciliation. |
| Build persistence | Store profile IDs, versions, decisions, and current reconciliation state across relog/restart. |
| Equipment planning | Query requirements, select compatible gear, acquire it through allowed sources, and equip normally. |
| Training planner | Select feasible maps/mobs by level, job, damage, travel cost, potion/ammo state, and profile. |
| Quest scheduler | Resolve dependencies, region policy, reward choices, shared objectives, and postponed quests. |
| Resupply | Buy or obtain potions, stars, bullets, and basic gear before combat becomes impossible. |
| Class combat parity | Prove melee, ranged, magic, ammo, support, movement skill, and buff behavior for all five first jobs. |
| Death/recovery | Resume the same plan safely after death, disconnect, map reset, or resource shortage. |
| Population policy | Spread Agents across jobs, plans, channels, and maps without synchronized choices or resource collapse. |

## Personality And Behavior Timeline

Personality should be introduced in layers.

### Layer 1: Assigned Presentation Profile

After full Maple Island correctness:

- timing ranges;
- NPC approach preference;
- fidget frequency and type weights;
- route variation;
- incidental mob style;
- rest-point preference.

This is the 100-Agent Maple Island realism milestone.

### Layer 2: Assigned Progression Profile

After each first-job route works deterministically:

- target career;
- AP/SP/gear build bundle;
- quest versus grind preference;
- risk and resource thresholds;
- preferred town/training style.

### Layer 3: Weighted Plan Sets

After level-30 correctness:

- choose among quest, grind, farm, resupply, idle, and social plans;
- apply archetype, mood, memory, and opportunity weights;
- retain hard constraints and progress guarantees;
- journal why a plan was selected.

### Layer 4: Economy And Adaptation

- react to item availability and price;
- farm or buy build-required equipment;
- learn bounded preferences from success/failure;
- keep build and safety policy immutable without an approved profile patch.

### Layer 5: LLM Direction

An LLM may propose goals, profile patches, or explanations only after the
deterministic planner can reject unsafe or impossible choices.

## Test Strategy

### Data Validation

- all referenced jobs and skills exist;
- skill target levels and prerequisites are legal;
- total SP through each milestone is neither over- nor under-allocated unless
  the profile explicitly holds points;
- AP rules never violate server stat floors or profile caps;
- gear tags resolve to at least one catalog option or a clear postponed state;
- every build bundle references compatible career, AP, SP, and gear profiles.

### Level Simulation

For every build bundle:

```text
start from a clean Maple Island exit snapshot
simulate normal level gains one level at a time
apply AP/SP through the same capability path
verify state at first job and every level through 30
verify relog/reconcile at selected checkpoints
```

### Functional Ladder

1. one Agent for each of the five first jobs;
2. three AP variants for one job while sharing career/SP data;
3. Thief composition test covering Sin, Dit, and Sindit;
4. all minimum variant bundles;
5. five-job mixed cohort to level 30;
6. larger profile-weighted Victoria population.

## Recommended First Deliverables After Maple Island

1. plan-runtime generalization proposal and regression boundary;
2. build-profile schemas plus loader in validation-only mode;
3. five career profile files;
4. AP profile templates for the matrix above;
5. first-job SP profiles, including missing Bandit and Pirate data;
6. gear-policy templates using requirement tags rather than fixed item IDs;
7. one complete Thief bundle set as the reference implementation;
8. one real first-job advancement plan;
9. one deterministic level-10-to-30 plan set;
10. tests that prove current Agent behavior is unchanged when no profile is
    assigned.

## Completion Definition

This roadmap reaches its first major completion point when:

- all five Explorer first-job advances use normal validated gameplay paths;
- every Agent has a durable career/AP/SP/gear/behavior profile composition;
- at least two or three build variants per first-job family are data-driven;
- each supported bundle allocates legal AP/SP and equips compatible gear;
- profile plan sets can choose quest or grind work without bypassing safety;
- every supported Agent can progress from a clean Maple Island character to
  level 30 and reach the configured second-job decision point; and
- all decisions remain replayable, explainable, and independent of an LLM.
