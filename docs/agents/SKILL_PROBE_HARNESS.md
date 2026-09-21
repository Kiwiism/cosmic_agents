# Skill probe harness (`!skillprobe`)

GM-only readiness probe that answers one question per skill: *can an Agent of this job actually
execute it through the normal combat path?* It is the first oracle of the bot-playtester system
(see the "Bots as playtesters" design): every skill becomes a `(intent, expected, observed, reason)`
record, and the per-job report is the coverage cell for "combat-complete".

Code: `server.agents.diagnostics.skillprobe.*`, command `client.command.commands.gm6.SkillProbeCommand`
(rank 6, registered as `skillprobe`).

## Usage

```
!skillprobe <agent> [job=<id|name>] [level=<n>] [mob=<id>] [map=<id>] [weapon=<itemId>] [build=max|profile] [nobuffs] [noshape] [keepmobs]
!skillprobe report
```

- `<agent>`: a spawned companion of yours (`!spawnbot <name> confirm` first), or any online Agent.
- `job=` / `level=`: shape the Agent first via `ResidentShaper.shapeTo` (levels one step at a time,
  advances along the legal chain). `level` defaults to the lowest level that unlocks the whole tree
  (10 / 30 / 70 / 120). Shaping never lowers a level. **Destructive** - use a throwaway Agent.
- Every skill of every job on the advancement chain is set to max level, then probed.
- `mob=` dummy monster (default 100100 Snail); `map=` probe map (default 180000000 GM Map).
  You and the Agent are warped there.
- `weapon=` item to equip; without it a plain shop weapon for the branch is provisioned when the
  Agent has none (plus ammo for bow/crossbow/claw/gun).
- `build=max` (default) maxes every skill in the tree; `build=profile` spends SP through the
  catalog's default SP profiles instead, i.e. exactly what a shaped resident has.
- `nobuffs` skips support skills; `keepmobs` leaves dummies alive; `noshape` probes as-is.

Runs on its own thread; the summary arrives in chat and the full report is written to
`docs/agents/evidence/skillprobe/<job>-L<level>-<utc stamp>.md`. `!skillprobe report` reprints the
last summary.

## Running it without a game client

`!skillprobe` is an ordinary rank-6 GM command, so any host-side GM command driver works. The
FriendServer host drives it over its admin port (an allowlisted `gm-command` action that brings a
GM character online on a `BotClient` and captures its chat output); on this repository the
director panel or a headless GM session serves the same purpose. A companion can only be shaped
*forward* along its job chain, so use a fresh `!spawnbot <name> confirm` per job family.

Ops note: never rebuild the jar under a running server - the JVM loads classes lazily and shutdown
then dies with `NoClassDefFoundError`. Run from a copy of the jar.

## What one probe does

Per skill, in skill-id order:

1. Classify with `AgentCombatSkillClassifier.classifySkillCacheBucket` (the same bucket the combat
   cache uses). `IGNORE` skills that nevertheless `declaresOffense` are flagged as a
   **CLASSIFIER GAP** without being executed.
2. Attack skills: heal the Agent, clear the attack cooldown, spawn a fresh dummy 60px to the right
   (and to the left on a second try), plan with `AgentSkillAttackPlanRuntime.planSkillAttack`, then
   commit with `AgentCombatAttackRuntime.attackMonster` (retries deferred results 8 x 150 ms).
3. Buff skills: `AgentCombatBuffRuntime.tryCastExplicitUtilityBuff`, then check that one of the
   effect's statups is active on the Agent.
4. Read `SecurityEventRuntime` for `AUTOBAN_SIGNAL` events on the Agent since the attempt started.
   `USE_AUTOBAN` is off, so these are recorded, not enforced - they are exactly the server-vs-bot
   disagreements we want to see.

## Stages

| Stage | Meaning | Where to look |
|---|---|---|
| `OK` | server accepted and applied it | - |
| `PLAN_SKILL_COOLDOWN` / `PLAN_CANNOT_PAY_COST` | planner gate | `AgentSkillAttackPlanner.skillAttackReadiness` |
| `PLAN_WEAPON_INCOMPATIBLE` | skill/weapon table | `AgentCombatWeaponPolicy` |
| `PLAN_COMBO_ORBS_REQUIRED` | Panic/Coma without `BuffStat.COMBO` | `AgentComboFinisherPolicy` (needs a combo prerequisite) |
| `PLAN_INSUFFICIENT_AMMO` | ranged cost | `AgentCombatAmmoCounter` |
| `PLAN_NO_HITBOX` / `PLAN_TARGET_UNREACHABLE` | hitbox shape vs dummy | `AgentCombatSkillHitboxPolicy` |
| `PLAN_RANGED_ROUTE_BLOCKED` | degenerate ranged distance | `AgentAttackExecutionProvider` |
| `PLAN_REJECTED_UNKNOWN` | planner returned null past every mirrored gate | a gate the harness does not replay (e.g. Dragon Roar ally rule) |
| `EXEC_DEFERRED` / `EXEC_REJECTED` | attack transaction refused | `AgentAttackTransactionResult.Reason` in detail |
| `EXEC_HANDLER_REJECTED` | Cosmic `applyAttack` refused the AttackInfo | `AbstractDealDamageHandler` skill branches |
| `EXEC_AUTOBAN_SIGNAL` | applied, but the server flagged it (MOB_COUNT, MPCON, FIX_DAMAGE, ...) | the named `AutobanFactory` check |
| `EXEC_NO_DAMAGE` | committed with zero landed lines and no HP change | damage profile / `makeTarget` |
| `BUFF_NOT_CAST` / `BUFF_NOT_APPLIED` | support cast refused or had no visible statup | `AgentCombatBuffRuntime` |
| `NOT_PROBED` | heal/summon/passive - listed for coverage only | - |

## Caveats

- The Agent's own AI keeps ticking during the probe; it may also hit the dummy. Hit/miss counts
  come from the harness's own transaction, HP delta is only a secondary signal.
- Mirrors the planner's gate order in `diagnosePlanFailure`; when a gate is added to
  `planSkillAttack`, add it there too or it will surface as `PLAN_REJECTED_UNKNOWN`.
- Not a load test and not a unit test; `SkillProbeOptionsTest` covers parsing and rendering only.

## Findings so far (2026-09-21)

- Crusader L70/L90 (max build) and L85 (profile build): every executable skill OK, including
  Panic/Coma once orbs exist. F/P Mage L90: all attacks and buffs OK. The combat pipeline is not
  where 2nd/3rd-job skills were failing.
- Root cause of "bots cannot use 2nd-job skills": `ResidentShaper.shapeTo` never spent SP.
  Every citizen sat at 200+ unspent SP with zero skills. Fixed: shaping now calls
  `AgentSpBuildProfileService.assignOffline`, and `repairSp` tops up residents shaped earlier.
- `AgentSpBuildProfileService.learnOne` paid SP before `changeSkillLevel` and ignored a refusal;
  its callers' loops then drained the whole pool one refused point at a time. Fixed: pay only on
  an accepted level change, stop the loop otherwise.
- `Server.shutdown` ran on a TimerManager pool that swallows exceptions; a failing shutdown left
  the server half-up with nothing logged. Fixed: the runnable logs, and each pre-agent stop stage
  is individually non-fatal.
- `ResidentShaper.advancementChain` skipped the third job for a fourth-job target, so a Hero
  persona stalled at Fighter forever. Fixed (`ResidentShaperTest`).
- Harness lessons folded in: combo finishers need a warm-up hit (the server grants orbs per
  close-range hit), a level-70 warm-up one-shots a Snail so the dummy is respawned, beginner
  event/mount skills are excluded, and the classifier-gap flag is limited to zero-cost non-passive
  offense.


## Coverage matrix: all twelve Explorer 4th jobs at L120, max build (2026-09-21, full coverage)

**392/393 skills OK across every skill in every tree, 0 "not probed"**. The one non-OK entry is
Wings (Corsair glide, a client-side movement mode with nothing to apply server-side), reported as
`UNSUPPORTED` rather than skipped. Per job: Hero 36/36, Paladin 39/39, Dark Knight 36/36, F/P Arch Mage
31/31, I/L Arch Mage 31/31, Bishop 32/32, Bowmaster 31/31, Marksman 31/31, Night Lord 31/31, Shadower
31/31, Buccaneer 32/32, Corsair 31/32. Reports: `evidence/skillprobe/*-L120-*.md`.

What "probed" means per kind now:
- ATTACK: planned and committed against a dummy (combo orbs, energy charge, meso drops, weapon swaps,
  taller dummy, healer ally as preconditions where the skill needs them).
- BUFF: cast through the combat runtime and the stat verified; buffs the loop never casts by design
  (Dark Sight, Battleship, Oak Barrel, Dash, beginner buffs, Hero's Will) are cast straight through
  the special-move gateway and marked "direct cast"/"blacklisted for automatic use".
- HEAL / SUMMON / DEBUFF / UTILITY: heal verified on HP, summon verified on the map, debuff verified as a
  status on the dummy (Hypnotize needed a server fix: no WZ box, and it lands late - the probe waits),
  Chakra / MP Recovery verified on HP / MP, Time Leap / Smokescreen / Dispel / Monster Magnet on dispatch,
  Resurrection verified by reviving the dead operator.
- MOVEMENT: Teleport and Flash Jump checked against `AgentMovementSkillPolicy` (eligible; both modes are
  `OFF` in `agent-engine.yaml` today, so the navigation does not route through them). Wings: unsupported.
- PASSIVE: verified as learned (masteries, HP/MP boosts, Final Attack, Energy Charge, Berserk, the
  Beholder auras the summon applies on its own schedule, ...).

## Combat loop and supply follow-ups (2026-09-21)

- `AgentCombatSpecialMoveTickRuntime` (hooked into the common tick right after skill buffs): keeps one
  summon up, opens on the nearest hostile in reach with a debuff it does not carry yet (4s pacing), and
  casts Chakra under half HP / MP Recovery under 30% MP. Attacks keep priority: the hook runs before
  the attack decision and would take every cooldown window, so a special move only spends a window
  after two attacks have landed since the last one (measured with the loop smoke: without that rule a
  Hero attacked 0 times in 30s; with it 3 attacks / 12 kills alongside 2 special moves).
- `!skillprobe ... loop=<seconds> [special=off]` is the combat-loop smoke: grinding among respawning
  Orange Mushrooms with Elixirs stocked (potion resupply would otherwise suspend the foreground in the
  GM map), reporting own attacks / special moves / summons out / statuses seen. Observed: Bahamut,
  Beholder, Elquines out; Ninja Ambush, Slow, Seal, Threaten, Crash statuses applied.
- Supply: Summoning Rock / Magic Rock are not sold anywhere in v83, so
  the Liquibase extension `2026-09-21-skill-consumables-in-town-shops.xml` stocks them (800 mesos) in the
  five Victoria town potion shops the supply planner already routes to. `AgentSkillConsumablePolicy`
  derives per-item targets (30 casts) from learned skills; `AgentShopService` buys the shortfall on any
  shop visit and treats a low stock as a reason to visit a shop that sells it. The probe prints
  "Skill consumable ... stocked by supplier NPCs [...]" so the wiring is checked live.
- Still open: automatic Meso Explosion (drop mesos, then explode) for Shadowers; Time Leap / Smokescreen
  triggers; movement-skill modes are a config decision (`TELEPORT_MODE` / `FLASH_JUMP_MODE`).

## Next steps this feeds

- **Skill execution overlay**: stages `PLAN_COMBO_ORBS_REQUIRED`, `EXEC_HANDLER_REJECTED` and
  `EXEC_AUTOBAN_SIGNAL` are the skills that need declared prerequisites (combo orbs, WK charge,
  dropped mesos, `charge` value, keydown repetition) before the planner picks them.
- **Coverage matrix**: one report per job x level band marks the cell; failures dedupe on
  `(skillId, stage)` and go into the findings pipeline.
