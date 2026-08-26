# Deterministic HP/MP Progression and No-Washing Policy

Status: implemented and verified on 2026-08-25.

## Purpose

Permanent HP and MP progression is deterministic. Two characters following the
same Explorer branch, reaching the same level, and holding the same relevant
passive-skill rank receive the same base Max HP and Max MP.

This replaces Cosmic's random level and job-advancement gains and removes the
INT-derived permanent MP gain that enabled MP washing. Ability Points cannot be
assigned to HP or MP, either normally or through AP Reset items.

The initial release balance is authoritative for the five Explorer families.
Compatibility values for disabled Cygnus, Aran, and Evan jobs prevent random
growth if one of those jobs exists in a development database, but those values
are not release balance targets.

## Runtime authority

`client.HpMpGrowthPolicy` is the single policy for:

- natural HP/MP gained on level-up;
- permanent HP/MP granted by job advancement;
- retroactive Warrior, Magician, and Brawler passive bonuses;
- identifying the HP and MP AP-stat packet encodings.

`client.Character.levelUp` adds the natural growth for the character's current
job and the one-level change in any applicable retroactive passive. It does not
read INT and does not use random numbers for permanent HP/MP.

`client.Character.changeJob` accepts only the direct, one-way advancement
defined by `client.JobProgressionPolicy`. It applies the fixed advancement
grant plus a timing correction when the advancement is late. The level-up gain
occurs before a same-level advancement: for example, the level 10 gain is
Beginner growth and the first Warrior grant is then applied by the advancement
transaction.

## Advancement integrity and late advancement

An advancement is valid only when all of these are true:

1. The requested job is different from the current job.
2. The current job is the requested job's direct parent.
3. The character has reached that advancement's minimum level.

Explorer minimum levels are 8 for Magician, 10 for the other first jobs, then
30, 70, and 120. Replaying the current job, skipping a tier, moving sideways to
another branch, moving backward, or changing family is rejected before any HP,
MP, SP, inventory expansion, quest, or announcement reward is granted.

Late advancement remains allowed. To prevent a character from benefiting from
the old job's per-level growth and then switching to a more favorable curve,
the advancement transaction adds this signed correction:

```text
(new-job level gain - old-job level gain) * (current level - minimum level)
```

The correction can add or remove HP/MP. Consequently, advancing at the minimum
level or advancing late produces the same permanent natural-growth total at a
given level. Every intermediate advancement still has to occur in order.

### Administrative `!job` override

The GM-only `!job <job-id>` command deliberately bypasses the normal transition
and minimum-level checks. It rebuilds base HP/MP from level 1 for the selected
job's complete lineage, then applies any currently learned retroactive passive
that belongs to the selected job tree.

Every fixed advancement grant through the selected job is included even when
the character is below that advancement's normal level. Natural per-level
growth changes at the normal advancement thresholds; growth is not incorrectly
treated as though the final job had been held since level 1. Running `!job` for
the current job is therefore also a valid way to recalculate its formula pools.

The override does not grant AP, SP, inventory slots, quests, or other repeatable
advancement rewards. Current HP and MP are preserved when possible and clamped
down if the rebuilt maximum is lower. Normal NPC and Agent advancements continue
to use the Explorer-only validated path.

## Starting values and natural growth

The progression calculations assume Cosmic's existing level 1 starting values:

| Level 1 base | HP | MP |
|---|---:|---:|
| All new characters | 50 | 5 |

Natural growth granted for each newly reached level is:

| Current job or branch | HP per level | MP per level |
|---|---:|---:|
| Beginner | 14 | 11 |
| Warrior | 45 | 10 |
| Magician | 20 | 55 |
| Bowman | 38 | 22 |
| Thief before second job | 38 | 22 |
| Assassin / Hermit / Night Lord | 38 | 22 |
| Bandit / Chief Bandit / Shadower | 44 | 19 |
| Pirate before second job | 50 | 25 |
| Brawler / Marauder / Buccaneer | 35 | 20 |
| Gunslinger / Outlaw / Corsair | 42 | 28 |

The Brawler branch has lower natural HP growth because Improve Max HP supplies
up to another 30 HP for every eligible level. This is included in the final
Buccaneer target below.

## Fixed job-advancement grants

Advancement grants are permanent and are added once when `changeJob` commits the
new job.

| Family or branch | First job | Second job | Third job | Fourth job |
|---|---:|---:|---:|---:|
| Warrior | +350 HP / +20 MP | +600 / +100 | +1,000 / +300 | +1,500 / +500 |
| Magician | +100 HP / +300 MP | +250 / +300 | +350 / +700 | +600 / +1,200 |
| Bowman | +200 HP / +75 MP | +300 / +150 | +450 / +300 | +650 / +500 |
| Assassin | +200 HP / +75 MP | +300 / +150 | +450 / +300 | +650 / +500 |
| Bandit | +200 HP / +75 MP | +350 / +150 | +500 / +250 | +650 / +450 |
| Brawler | +200 HP / +75 MP | +500 / +150 | +700 / +250 | +1,000 / +400 |
| Gunslinger | +200 HP / +75 MP | +300 / +200 | +450 / +350 | +650 / +550 |

In cells after the first-job column, the two unlabeled values are HP and MP in
that order.

## Retroactive passive skills

The following passives depend only on current character level, current skill
rank, and the skill's WZ `x` value. They no longer depend on when the SP was
allocated.

| Skill | ID | Eligible levels at character level `L` | Permanent bonus |
|---|---:|---:|---|
| Improved Max HP Increase | `1000001` | `max(0, L - 10)` | `eligible levels * x` Max HP |
| Improved Max MP Increase | `2000001` | `max(0, L - 8)` | `eligible levels * x` Max MP |
| Improve Max HP | `5100000` | `max(0, L - 30)` | `eligible levels * x` Max HP |

Examples at level 160 with each skill maxed:

| Skill | Max-rank WZ `x` | Eligible levels | Total passive bonus |
|---|---:|---:|---:|
| Warrior Improved Max HP Increase | 40 | 150 | +6,000 HP |
| Magician Improved Max MP Increase | 20 | 152 | +3,040 MP |
| Brawler Improve Max HP | 30 | 130 | +3,900 HP |

When a passive is raised, `Character.changeSkillLevel` calculates the complete
bonus at the old and new ranks and applies only the difference. Learning the
skill late therefore catches the character up immediately. Reducing or removing
the skill subtracts the corresponding difference. On later level-ups, only the
newly eligible level's passive amount is added.

The Paladin skill Improving MP Recovery (`1210000`) remains an MP-recovery skill
and does not increase permanent Max MP.

## Level 160 acceptance targets

These values include natural growth, all four job-advancement grants, and any
applicable max-rank passive. They exclude equipment, buffs such as Hyper Body,
and any future vitality progression system.

| Explorer outcome | Base Max HP | Base Max MP |
|---|---:|---:|
| Hero / Paladin / Dark Knight | 16,376 | 2,524 |
| F/P Arch Mage / I/L Arch Mage / Bishop | 4,488 | 13,982 |
| Bowmaster / Marksman | 7,476 | 4,429 |
| Night Lord | 7,476 | 4,429 |
| Shadower | 8,356 | 3,939 |
| Buccaneer | 12,026 | 4,079 |
| Corsair | 8,236 | 5,419 |

The Magician calculation uses a level 8 first advancement. All other Explorer
families use level 10. Second, third, and fourth advancements are calculated at
levels 30, 70, and 120.

Level 160 is a balance checkpoint, not a progression cap. The same natural and
passive gains continue linearly to the server's level 200 cap. With maxed
applicable passives, the level 200 reference values are:

| Explorer outcome | Base Max HP | Base Max MP |
|---|---:|---:|
| Hero / Paladin / Dark Knight | 19,776 | 2,924 |
| F/P Arch Mage / I/L Arch Mage / Bishop | 5,288 | 16,982 |
| Bowmaster / Marksman | 8,996 | 5,309 |
| Night Lord | 8,996 | 5,309 |
| Shadower | 10,116 | 4,699 |
| Buccaneer | 14,626 | 4,879 |
| Corsair | 9,916 | 6,539 |

## HP/MP washing prohibition

HP and MP use AP-stat encodings `2048` and `8192`. Both are rejected before a
stat mutation occurs:

1. Direct AP assignment rejects either encoding and restores client actions.
2. AP Reset rejects a request when either the source or destination is HP/MP.
3. `AbstractCharacterObject.assignHP` and `assignMP` reject every call whose
   `deltaAp` is nonzero, providing a second enforcement layer beneath packet
   handlers.

The early AP Reset validation is important: a primary stat is never removed
before an invalid HP/MP destination is discovered. Rejected AP Reset requests
return failure to the cash-item handler and therefore do not consume the item.

Trusted non-AP systems may still modify permanent HP/MP through their dedicated
server-side paths. The prohibition is specifically against converting Ability
Points into or out of the HP/MP pools.

`USE_RANDOMIZE_HPMP_GAIN` remains in `config.yaml` for configuration-file
compatibility and is set to `false`. It is not a feature toggle for the new
Explorer progression: the active level-up policy is deterministic regardless
of that legacy field, and INT is never included in its calculation.

## Skill mutation and SP Reset integrity

All positive changes to one of the three retroactive HP/MP passives are checked
below the packet handlers as well as at normal SP assignment:

- the skill must exist and its requested rank cannot exceed its WZ maximum;
- the passive must belong to the character's current job tree;
- Warrior Improved Max HP Increase requires rank 5 Improved HP Recovery;
- Magician Improved Max MP Increase requires rank 5 Improved MP Recovery.

SP Reset transfers are validated atomically before either skill is changed or
the cash item is consumed. Source and destination must be different skills,
must both exist, must both belong to the character's current job tree, and must
both match the reset item's job tier. A reset also cannot remove the fifth
prerequisite point while its dependent HP/MP passive is learned. The destination
is applied first; if the source change unexpectedly fails, the destination is
rolled back before the item can be consumed. This closes the same-skill
free-rank case, cross-tier transfers, packet-supplied foreign skills, and
partial-transfer failures.

Only passives in the current job tree contribute new per-level retroactive
growth. A legacy foreign passive can still be lowered or removed so damaged
characters can be repaired, but it cannot be raised.

Captured Agent progression checkpoints are restored as exact snapshots. Their
stored HP/MP pools already represent the captured state, so checkpoint skill
ranks are inserted without running the normal skill-learning mutation or
applying a second retroactive passive delta. Subsequent leveling and genuine
skill-rank changes use the deterministic policy normally.

## Client cap and persistence

The client-visible base pools remain capped at 30,000. The server also retains
an uncapped base total (`rawmaxhp` and `rawmaxmp`) and persists that total in the
existing `characters.maxhp` and `characters.maxmp` columns. No schema migration
is required.

This separation matters when a passive is changed near the cap. For example,
an underlying 30,200 HP displays as 30,000; removing a 400 HP passive now leaves
29,800 rather than incorrectly subtracting from the already-clamped 30,000.
Saving, loading, character copying, and progression snapshots preserve the raw
total while packets and combat continue to use the capped value.

`Character.levelUp` now exits before awarding AP, SP, HP/MP, or other level-up
effects when the character is already at its effective cap. Normal Explorer
characters have a class cap of 200, Cygnus compatibility characters 120, and GM
jobs retain 255.

The GM `!job` command uses a separate administrative identity override. It can
set an arbitrary test job and rebuilds the character's base HP/MP to the
target job's deterministic curve at the current level. The rebuild is not an
additive advancement reward and grants no AP, SP, or inventory slots; normal
player advancement remains subject to the one-way Explorer progression policy.

## Disabled legacy character creation

Maple Life cards are rejected both in Cash Shop purchasing and when consumed.
The legacy cards created pre-leveled characters with hard-coded randomized
HP/MP and preloaded passive ranks, which could double-apply the retroactive
policy. Extra Character Slot remains a separate item. Maple Life should stay
disabled until its recipes are rebuilt entirely on this progression policy.

## Client and server String assets

The descriptions for skills `1000001`, `2000001`, and `5100000` now explain the
retroactive per-level behavior and no longer mention applying AP to HP/MP. AP
Reset `5050000` now lists only STR, DEX, INT, and LUK as valid source and target
stats.

Canonical server XML files:

- `wz/String.wz/Skill.img.xml`
- `wz/String.wz/Cash.img.xml`

The repository ignores `wz/`, so these XML changes and generated client IMG
files are distributed as data assets rather than ordinary tracked source unless
they are explicitly force-added.

The verified client output for this implementation is under:

`tmp/string-cleanup/client-hpmp-v2/Data/String`

The full String pack was generated and then converted back to server XML. The
six primary files (`Cash`, `Consume`, `Eqp`, `Etc`, `Ins`, and `Skill`) matched
their source XML with zero node or attribute differences. The validation report
is:

`tmp/string-cleanup/client-roundtrip-validation-hpmp-v2.json`

## Existing-character migration boundary

Max HP and Max MP are persisted in the character row. This implementation does
not infer and replace the complete historical growth of characters created
under an older formula. Fresh characters follow the deterministic curve
exactly, and later passive-rank changes receive the correct retroactive delta,
but an existing character retains its previously stored base pool.

This is intentional for the clean alpha database. If legacy character migration
is needed later, it must be an explicit, auditable operation because blindly
rebuilding Max HP/MP could erase legitimate permanent grants from other systems.

## Verification

The policy tests simulate each Explorer path from level 1 through level 160,
including advancement timing and maxed passives. They also verify late versus
on-time advancement equivalence, the legal direct-parent transition graph, the
three retroactive totals, both forbidden AP encodings, and SP Reset tier/tree
validation.

Run the focused checks from the repository root:

```powershell
.\mvnw.cmd -q '-Dtest=client.HpMpGrowthPolicyTest,client.HpMpPoolPolicyTest,client.JobProgressionPolicyTest,client.processor.stat.AssignAPProcessorTest,client.processor.stat.SpResetPolicyTest' test
```

Expected result: all focused tests pass with 0 failures and 0 errors.

Relevant implementation and test files:

- `src/main/java/client/HpMpGrowthPolicy.java`
- `src/main/java/client/HpMpPoolPolicy.java`
- `src/main/java/client/JobProgressionPolicy.java`
- `src/main/java/client/Character.java`
- `src/main/java/client/AbstractCharacterObject.java`
- `src/main/java/client/processor/stat/AssignAPProcessor.java`
- `src/main/java/client/processor/stat/SpResetPolicy.java`
- `src/test/java/client/HpMpGrowthPolicyTest.java`
- `src/test/java/client/HpMpPoolPolicyTest.java`
- `src/test/java/client/JobProgressionPolicyTest.java`
- `src/test/java/client/processor/stat/AssignAPProcessorTest.java`
- `src/test/java/client/processor/stat/SpResetPolicyTest.java`
