# Agent Fix TODO

Behavior correctness fixes to review after or during reconstruction. These are
not server-scale items.

Related server/economy hardening item: `docs/SERVER_SCALE_TODO.md`.

## Upstream nutnnut Branch Items To Carry Forward

- [ ] Port/review `nutnnut/Cosmic` `fix-queststatus-notstarted-bloat`.
  - Category: agent quest hardening, server save pressure, DB bloat reduction.
  - Source branch: `source/fix-queststatus-notstarted-bloat`.
  - Useful patch: skip saving empty `QuestStatus.Status.NOT_STARTED`
    placeholders when they have no forfeits, no progress, and no medal map
    data.
  - Why it matters for agents: catalog lookups, quest eligibility checks, and
    plan-card requirement scans can touch many quests. If those lookups create
    placeholder quest records, agent saves can bloat the `queststatus` tables
    and increase autosave/database pressure.
  - Fix direction:
    - keep normal STARTED/COMPLETED quest persistence unchanged.
    - skip only pure placeholder NOT_STARTED statuses.
    - add a cleanup/migration note for existing bloated rows if the database
      has already accumulated them.
  - Validation later:
    - looking up a quest without starting it does not create a saved row.
    - forfeited NOT_STARTED records still save.
    - NOT_STARTED records with progress or medal map data still save.
    - started/completed quest saves remain unchanged.
- [ ] Review `nutnnut/Cosmic` dev navigation / physics fixes before finalizing
  reconstructed movement capability.
  - Category: agent navigation correctness and movement realism.
  - Source branch: `source/dev`.
  - Useful upstream commits and defect classes:
    - `7c0d287015` - `fix(bot): ground-walk follows foothold chain
      across joined forks`.
      - Defect: coordinate-based foothold lookup snaps to the wrong fork arm
        while the real client keeps walking along the current foothold
        `prev`/`next` chain.
      - Agent port target: movement ground-step service and graph builder.
    - `59c5288994` - `fix(bot-nav): skip phantom cross-region jumps onto
      shared ground`.
      - Defect: overlapping foothold chains can share the same physical
        ground, causing A* to author impossible region-changing jump edges.
      - Agent port target: navigation graph edge validation and current-region
        continuity.
    - `235b6aefa4` - `Fix overlapped foothold ground continuity`.
      - Defect: navigation may preserve the correct region, but the ground
        motor can still raw-snap to the wrong overlapping foothold.
      - Agent port target: movement runtime should pass the resolved standing
        foothold/region into ground-step preview.
    - `43b9f06c8c` - `fix(bot-physics): client-true wall collidability
      (zMass groups) + stale-route/watchdog fixes`.
      - Defect: vertical footholds/walls can be treated as hard blockers in
        places where the client would keep walking the foothold chain.
      - Agent port target: airborne wall collision by `zMass`, ground walking
        by chain/region, stale route watchdog.
    - `f5fe4b0a4d` - `Fix foothold detour oscillation`.
      - Defect: detour waypoint can disarm too early and fight the legal climb
        approach.
      - Agent port target: regression tests and waypoint hold policy.
    - `f78f85d4bd` - `Recover off-graph travel falls`.
      - Defect: travel can fall off the nav graph and remain wedged.
      - Agent port target: recovery policy when current region is unknown.
    - `8a7e7b3a55` - `fix(bot-nav): directional walk-off drops author landing
      via execution walk-off sim; unpark -pos gate wedges`.
      - Defect: graph-authored drop edges can be impossible to reproduce at
        execution time.
      - Agent port target: graph-builder/executor SSOT tests for walk-off/drop
        landing.
    - teleport movement fragment layout.
    - hardened bot world-map WZ loading.
  - Why it matters for agents: Amherst/Maple Island MVP can pass with simple
    navigation, but future Victoria Island pathing, vertical maps, ropes,
    portals, Teleport, Flash Jump, and abstract movement tiers need movement
    behavior that matches client/server geometry closely.
  - Fix direction:
    - mine these as tests and reference behavior after reconstruction.
    - do not merge the legacy bot branch wholesale.
    - convert useful pieces into movement/navigation capability services,
      catalog validation checks, or graph-builder tests.
  - Validation later:
    - overlapped footholds do not create false stuck states.
    - shared-ground overlapping foothold chains do not produce phantom
      cross-region jump/teleport/flash-jump edges.
    - joined fork walks follow the client-like foothold `prev`/`next` chain.
    - high vertical footholds near map entrances do not make agents park at an
      impossible `jump-pos`, `drop-pos`, or `climb-pos` gate.
    - wall collision matches the client on representative maps.
    - teleport/flash-jump route fragments are packet-safe.
    - fall recovery returns to a valid foothold without infinite loops.
- [ ] Review `nutnnut/Cosmic` dev combat / buff fixes before finalizing combat
  capability parity.
  - Category: agent combat correctness, buff parity, and capability tests.
  - Source branch: `source/dev` plus already-merged upstream experimental
    combat fixes.
  - Useful upstream areas:
    - Magic Guard MP/HP damage split for bot damage.
    - Shadow Partner damage-line handling.
    - magic passive damage cache behavior.
    - special-scroll/combat valuation side effects only where they affect
      combat decisions.
  - Why it matters for agents: reconstructed agents should not merely cast
    buffs; their damage taken, damage dealt, and survivability decisions must
    match what the server would do for a comparable player.
  - Fix direction:
    - use upstream behavior as reference, not as a direct merge target.
    - fold defensive buff behavior into the planned incoming damage policy.
    - add per-buff and per-skill regression tests before enabling autonomous
      combat plans that depend on those buffs.
  - Validation later:
    - Magic Guard reduces HP damage and consumes MP.
    - Meso Guard, Power Guard, Achilles, Combo Barrier, and similar mitigation
      paths match the chosen player-parity rules.
    - Shadow Partner adds the expected extra lines without rerolling original
      damage incorrectly.
    - supported attack/buff skills have explicit capability status.

## Server/Economy Fixes To Coordinate

- [x] Manually port the useful parts of `P0nk/Cosmic` PR [#277](https://github.com/P0nk/Cosmic/pull/277): hired merchant duplicate guard and Store Remote Controller validation.
  - Category: server hardening, economy hardening, and future agent economy hardening.
  - Do not apply PR #277 as-is.
  - Current risk: hired merchant creation blindly registers a new `HiredMerchant` into character/world/channel state, which can overwrite existing owner-keyed merchant references in abnormal state.
  - Current risk: `RemoteStoreHandler` lets an owner visit a merchant from anywhere in the same channel when `hasMerchant()` is true.
  - Useful upstream idea: make channel merchant registration reject duplicate owner ids.
  - Useful upstream idea: require Store Remote Controller item `5470000` for remote merchant access when the owner is not on the merchant map.
  - Problems in upstream patch:
    - leaves `System.out.println("new shop creation.")`.
    - silently does nothing when duplicate add fails; client may not get an error or `enableActions`.
    - only checks duplicate state in channel merchant map.
    - does not consistently check `chr.getHiredMerchant()`, `chr.hasMerchant()`, `World.getHiredMerchant(chr.getId())`, and persisted Fredrick merchant state.
    - remote-controller check is applied only for same-channel different-map access, not before cross-channel remote change.
    - formatting regression in `Channel.java`.
  - Recommended fix direction:
    - add a clean `tryAddHiredMerchant(ownerId, merchant)` or equivalent atomic registration API.
    - before creating merchant, reject if `chr.getHiredMerchant() != null`.
    - reject if `chr.hasMerchant()` is already true.
    - reject if `world.getHiredMerchant(chr.getId()) != null`.
    - reject if channel already has a merchant for the owner id.
    - on rejection, send a proper miniroom/store error and `enableActions`.
    - register character, world, and channel merchant state in a consistent order.
    - remove rough logging; use structured debug logging only if needed.
    - enforce Store Remote Controller before remote same-channel visit and before cross-channel remote switch unless design decides cross-channel should remain free.
    - decide whether item `5470000` is permanent permission or consumable; current PR treats it as permanent.
  - Validation later:
    - repeated merchant create packet cannot create or orphan a second merchant.
    - failed duplicate creation leaves character/world/channel merchant state unchanged.
    - owner on merchant map can open merchant normally.
    - owner off merchant map without Store Remote Controller cannot remote-open.
    - owner off merchant map with Store Remote Controller can remote-open or channel-switch according to final policy.
    - merchant close/force-close/Fredrick retrieval clears state correctly.
- [x] Review and port `P0nk/Cosmic` PR [#279](https://github.com/P0nk/Cosmic/pull/279): mob skill probability integer-division fix.
  - Category: server hardening, gameplay correctness, and agent hardening.
  - Current risk: `MobSkillFactory` reads mob skill `prop` into `iprop`, then computes `float prop = iprop / 100`, which performs integer division before assignment.
  - Effect: values below `100` become `0.0`, so mob skills with partial success rates may never apply.
  - Fix direction: use `(float) iprop / 100`.
  - Agent impact: agents relying on server-side danger/perception should face the same mob skill behavior as players; incorrect mob debuff frequency affects training risk and potion/debuff planning.
  - Validation later: test `prop` values such as `1`, `50`, and `100` produce `0.01`, `0.50`, and `1.00`.
- [x] Review and port `P0nk/Cosmic` PR [#287](https://github.com/P0nk/Cosmic/pull/287): drop chance and quantity range fixes.
  - Category: server hardening, gameplay correctness, economy hardening, and agent hardening.
  - Current risk: drop chance uses `Randomizer.nextInt(999999)` against million-scale drop chances.
  - Current risk: meso/item quantity rolls use `nextInt(max - min) + min`, excluding the maximum value and failing when min equals max unless special-cased.
  - Fix direction:
    - use `Randomizer.nextInt(1000000)` for million-scale drop chance.
    - use `Randomizer.nextInt(max - min + 1) + min` for inclusive quantity ranges.
  - Agent impact: farming, drop catalogs, item valuation, and economy simulation need accurate drop/quantity behavior.
  - Validation later: regression test inclusive min/max quantity coverage, including `min == max`.
- [x] Review and port `P0nk/Cosmic` PR [#295](https://github.com/P0nk/Cosmic/pull/295): quest 6225 NPC condition fix.
  - Category: gameplay correctness and agent quest hardening.
  - Affected script: `scripts/npc/2041023.js`.
  - Current risk: quest gating around element-based Thanatos appears too broad/incorrect for quest 6225/6315 chain.
  - Fix direction: allow interaction when quest `6225` is started and `6226` is not completed, or quest `6315` is started and `6316` is not completed.
  - Agent impact: future quest-capable agents need quest scripts to expose correct start/complete paths.
  - Validation later: test both affected quest paths and confirm unrelated players are rejected.

## Damage And Buff Parity

- [ ] Create shared incoming damage policy for player-like Agent damage.
  - Goal: one small service that can be called by visible bot touch/fall damage,
    abstract combat simulation, and future agent combat tests.
  - Proposed name: `AgentIncomingDamagePolicy` or shared
    `IncomingDamageMitigationService`.
  - Inputs:
    - attacker mob id / attack kind.
    - raw damage.
    - damage source: touch, mob magic, fall, poison/environment, script.
    - agent current HP/MP/mesos.
    - active buffs and passive skills.
    - map and foothold context when relevant.
  - Output:
    - final HP loss.
    - final MP loss.
    - meso loss.
    - reflected damage.
    - cancelled/blocked state.
    - consumed buffs/items.
    - reason codes for logs and tests.
  - Rule: do not direct-copy the packet handler. Extract the gameplay math and
    keep packet/client side effects outside the policy.
- [ ] Bot server-side damage should respect Magic Guard.
  - Current behavior: bot mob-touch/fall damage calls `bot.addMPHPAndTriggerAutopot(-dmg, 0)`.
  - Effect: full damage is taken from HP and MP absorbs nothing, even if `BuffStat.MAGIC_GUARD` is active.
  - Player path: `TakeDamageHandler` splits damage into HP/MP when `BuffStat.MAGIC_GUARD` is active and `mpattack == 0`.
  - Fix direction: extract shared mitigation logic or apply equivalent Magic Guard split in the bot damage path.
- [ ] Check Meso Guard parity for bot server-side damage.
  - Player path reduces HP damage and consumes mesos when `BuffStat.MESOGUARD` is active.
  - Bot path currently appears to bypass `TakeDamageHandler`, so Meso Guard likely does not reduce mob-touch/fall damage.
  - Confirm with a Chief Bandit bot or unit test before fixing.
- [ ] Review and implement full player damage-mitigation parity for bot server-side damage.
  - Bot mob-touch/fall damage currently bypasses `TakeDamageHandler`.
  - Player path includes mitigation/reaction behavior that the bot path does not currently apply:
    - `BuffStat.POWERGUARD`: reduces touch damage, bounces damage to attacker, and adds aggro.
    - `BuffStat.BODY_PRESSURE`: may apply monster neutralise on touch.
    - `BuffStat.COMBO_BARRIER`: multiplies incoming damage by the skill effect ratio.
    - `Hero/Paladin/DarkKnight.ACHILLES`: passive damage reduction except fall/environment damage.
    - `Aran.HIGH_DEFENSE`: passive damage reduction except fall/environment damage.
    - `BuffStat.MANA_REFLECTION`: may reflect mob magic damage to non-boss attackers.
    - Battleship HP routing: damage decreases battleship HP before normal HP loss.
  - Decide whether these belong in a shared `AgentIncomingDamagePolicy` / server combat helper instead of copying `TakeDamageHandler`.
- [ ] Review Cleric Invincible behavior.
  - `StatEffect` applies `BuffStat.INVINCIBLE` for `Cleric.INVINCIBLE`.
  - Current player `TakeDamageHandler` does not appear to read `BuffStat.INVINCIBLE` directly.
  - Confirm whether Cosmic handles Invincible through another stat path, whether it is currently ineffective, or whether the bot damage provider already accounts for it indirectly.
- [ ] Add regression coverage for bot defensive buffs.
  - Magic Guard: HP loss should be reduced and MP should absorb according to buff value.
  - Meso Guard: HP loss should be reduced and mesos should be consumed according to buff value.
  - Power Guard: touch damage should be reduced and reflected.
  - Combo Barrier/Achilles/High Defense: incoming damage should match player mitigation.
  - Invincible: expected behavior should be defined before adding coverage.
  - No-buff path should preserve current bot damage behavior.
- [ ] Add agent damage telemetry.
  - Log or journal compact damage decisions:
    - raw damage.
    - mitigation sources.
    - HP/MP/meso loss.
    - potion/autopot trigger.
    - death/near-death state.
  - Use this for profile learning, risk catalog updates, and tuning abstract
    combat simulation.

## Movement Skill Capability Gaps

- [ ] Add movement capability command/result model.
  - Commands:
    - `MOVE_TO_POINT`
    - `NAVIGATE_TO_MAP`
    - `NAVIGATE_TO_NPC`
    - `USE_PORTAL`
    - `TELEPORT_TO_POINT`
    - `FLASH_JUMP_TOWARD`
  - Results:
    - success.
    - blocked by reachability.
    - blocked by missing skill/MP.
    - blocked by cooldown/state.
    - partial progress.
    - retryable stuck.
  - This should match the direct LLM navigation plan-card contract.
- [ ] Implement agent Teleport movement capability.
  - Skill constants exist for mage classes, and bot builds learn Teleport.
  - Current bot movement only uses walking/jumping/climbing/portals plus recovery teleports.
  - No agent capability was found that casts/uses Teleport as a movement skill.
  - Future design should model Teleport as a validated short-range movement capability with MP cost, cooldown/action delay, collision/foothold validation, and path planner integration.
- [ ] Implement agent Flash Jump movement capability.
  - Skill constants exist for Hermit/Night Walker, and thief builds learn Flash Jump.
  - No agent capability was found that casts/uses Flash Jump as a movement skill.
  - Future design should model Flash Jump as an airborne movement extension with MP cost, skill level distance scaling, movement packet/action timing, and navigation graph support.

## Spell And Skill Capability Coverage

- [ ] Build a full agent skill capability matrix from WZ + server handlers.
  - Current agent combat classifies learned skills into attack, summon, support buff, and heal buckets.
  - This does not mean every spell/skill has correct behavior.
  - Required output: skill id, name, class, intended capability, supported status, handler route, known gaps, tests.
- [ ] Audit active attack spell coverage.
  - Current active attack detection is generic: offensive WZ fields, not over-time, paid cost, then route via close/ranged/magic handlers.
  - Likely works for many direct damage skills.
  - Needs explicit verification for special-case attacks with unusual packet shape, targeting, charges, combo requirements, ammo/projectile rules, or movement coupling.
- [ ] Audit support buff spell coverage.
  - Current support-buff cache covers over-time statup skills and casts through `SPECIAL_MOVE`.
  - Verify self-only buffs, party buffs, class buffs, cooldown buffs, and blacklisted unsafe buffs individually.
  - Known applied/consumed examples from scan: Haste affects movement stats; Rage/WATK affects recalculated attack stats.
- [ ] Audit summon and puppet spell coverage.
  - Summon-like effects are classified into a summon bucket.
  - Need to verify actual summon creation, lifetime, targeting, damage, cleanup, and recast behavior for agent-owned execution.
- [ ] Audit status/debuff/control spell coverage.
  - Examples to verify: Slow, Seal, Doom, Threaten, Armor/Magic/Power Crash, Hamstring, Blind, Body Pressure, monster debuffs.
  - These may not behave like direct attacks or normal support buffs and may need separate capability commands.
- [ ] Audit utility spell coverage.
  - Examples: Dispel, Mystic Door, Dark Sight, Meso Up, Pickpocket, Shadow Partner, Soul Arrow, Shadow Claw, Chakra, Smokescreen, Hero's Will, Echo, mounts/morphs.
  - Some are intentionally blacklisted or not useful for current bot behavior, but full autonomy needs explicit support/gating decisions.
- [ ] Audit movement spell coverage.
  - Teleport and Flash Jump are separate movement capability gaps.
  - Also verify Dash, Recoil Shot, Corkscrew Blow, Wings, rope/jump-related skills, and mount movement effects.
- [ ] Add per-skill regression tests for supported categories.
  - Direct attack spell sends correct attack route/packet and applies handler effects.
  - Buff spell applies expected `BuffStat` and downstream stats consume it.
  - Defensive spell changes incoming damage as players would expect.
  - Movement spell changes position only through validated movement rules.

## NPC / Quest Capability Gaps

- [x] Prepare unwired NPC interaction validator/capability services.
  - Added `AgentNpcInteractionCapability`, request/result models, interaction
    type enum, and `AgentNpcInteractionValidator`.
  - Validates NPC id, map id, catalog presence, map placement, optional
    quest-start/quest-complete action, interaction range, approach point, and
    dialogue-delay estimate.
  - Uses future `NpcGateway` only when provided; no current runtime behavior is
    changed.
- [x] Prepare unwired quest start/complete validator/capability services.
  - Added `AgentQuestStartCapability`, `AgentQuestCompleteCapability`,
    `AgentQuestRequirementValidator`, `AgentQuestSnapshot`,
    `AgentQuestRequirement`, and typed result/request/status models.
  - Validates current status, level, job, prerequisites, required items, mob
    kills, progress values, NPC/range, and auto-complete cases.
  - Amherst quest metadata is used as the first default requirement source.
  - Uses future `QuestGateway` only when provided; no current runtime behavior
    is changed.
- [ ] Implement live `QuestCapability` read APIs.
  - Read live quest status.
  - Check start requirements.
  - Check complete requirements.
  - Explain unmet requirements in structured reason codes.
  - Never use `forceStart` / `forceComplete` for normal runtime behavior.
- [ ] Wire `NpcQuestInteractionCapability` to live Cosmic gateways after reconstruction.
  - Validate map, live NPC presence, interaction range/box, reachability,
    quest requirements, manual-review flags, and blocking agent states.
  - Execute direct quest start/complete only after validation passes.
  - Apply dialogue delay simulation before execution when presentation mode is
    enabled.
  - Return structured result:
    - `STARTED`
    - `COMPLETED`
    - `BLOCKED_REQUIREMENT`
    - `BLOCKED_MANUAL_REVIEW`
    - `NPC_MISSING`
    - `OUT_OF_RANGE`
    - `UNREACHABLE`
    - `FAILED_SCRIPT`
- [ ] Wire prepared reactor capability to live Cosmic reactor execution after reconstruction.
  - `AgentReactorInteractionCapability` can already plan reactor objectives and
    delegates through `AgentReactorExecutionPort` only when supplied.
  - Add the live adapter after movement/loot/inventory capability wiring is
    stable so reactor rewards can be verified through inventory state.
- [ ] Implement shop interaction capability.
  - Validate NPC shop mapping.
  - Validate agent range/reachability.
  - Buy/sell only through protected item and budget policies.
  - Return structured buy/sell result with item ids, quantities, mesos, and
    blocked reason.
- [ ] Implement reward choice policy.
  - Use quest reward choice catalog where available.
  - Fall back to profile/economy rules:
    - class useful.
    - market value.
    - quest/crafting protection.
    - inventory space.
    - default first valid choice only when explicitly safe.
- [ ] Add NPC/quest integration tests.
  - Unit tests now cover unwired NPC and quest validators.
  - Start quest from valid NPC/range.
  - Reject start from wrong NPC.
  - Reject start when level/prerequisite/item requirement fails.
  - Complete quest and apply rewards.
  - Choose reward according to reward policy.
  - Reject manual-review/script-sensitive NPC.
  - Resume after relog and do not duplicate quest actions.

## Inventory / Item Capability Gaps

- [ ] Add protected item policy for all autonomous sell/drop/trade actions.
  - Protect quest items.
  - Protect plan-required items.
  - Protect build milestone items.
  - Protect crafting/materials according to profile.
  - Protect market-candidate items above value threshold.
- [ ] Add inventory capacity planning.
  - Before quest/farm/shop objective, verify free slots by inventory type.
  - If insufficient, request sell/store/drop/resupply plan before continuing.
- [ ] Add acquisition option service.
  - Query catalog for shop, drop, quest, crafting, market, and reward sources.
  - Let profile/economy choose buy/farm/craft/postpone.

## Runtime Control / Safety Gaps

- [ ] Add capability-level cancellation.
  - Planner/LLM should be able to cancel navigation, combat, shop, NPC, and
    sidetrack objectives without leaving stale state.
- [ ] Add per-capability timeout and stuck policy.
  - Navigation timeout.
  - NPC approach timeout.
  - Quest kill/drop objective timeout.
  - Shop visit timeout.
  - Combat danger timeout.
- [ ] Add structured capability audit log.
  - agent id, plan id, objective id, capability, command, result, reason,
    duration, retry count, relevant entity ids.
- [ ] Add live validation before every mutating action.
  - Catalog is advisory.
  - Live server state decides whether the action is currently allowed.
