# Victoria Level-15 Shared Checkpoint Plan

All Explorer careers use the single universal plan `victoria-level15-mvp`.
Instructor details remain career-specific, while the five home/rotation packs
are authored once in
`src/main/resources/agents/catalogs/victoria-shared-quest-packs.json`.
A correction to a shared pack therefore applies to every career that uses it.

## Commands and reset evidence

```text
!victoria run <IGN> <career> <lv10|lv9-olaf|lv9-grind> checkpoint1
!victoria run <IGN> <career> checkpoint2
```

`checkpoint1`, `checkpoint2`, `checkpoint3`, `cp1`, `cp2`, and `cp3` are
recognized. Checkpoint 3 intentionally rejects reset attempts until the
preceding pack has been run and its exact level, EXP, inventory, quest state,
AP/SP, equipment, and mesos are captured.

Checkpoint-2 reset data is stored in
`src/main/resources/agents/fixtures/victoria-checkpoint2-baselines.json`.
The validated thief-dagger entry is `CAPTURED`. The other entries are
`PREDICTED` temporary baselines derived from the instructor quest requirements,
quest EXP, expected minimum kills, build profile, starter kit, supplies, and
conservative expected drops. Each predicted entry must be replaced with a
capture after its checkpoint-1 test.

## Shared execution order

| Career bundle | Checkpoint 1 | Checkpoint 2 home pack | Checkpoint 3 rotation pack |
|---|---|---|---|
| Warrior | Power B. Fore entrance, Warrior Training Center, supplies | Perion | Ellinia |
| Magician | Power B. Fore entrance, Magician Training Center, supplies plus one Ellinia scroll | Ellinia | Nautilus |
| Bowman | Power B. Fore entrance, Bowman Training Center, supplies | Henesys | Kerning |
| Thief claw/dagger | Power B. Fore entrance, Thief Training Center, supplies plus one Kerning scroll | Kerning | Perion |
| Pirate gun/knuckle | Power B. Fore entrance, Pirate Training Center, supplies | Nautilus | Henesys |

Checkpoint 1 always finishes instructor training before the initial shop. The
plan then advances directly into its home pack. It does not return to the job
instructor after shopping.

## Perion pack

1. Take Ayan's **Sweep the Snails!** and **The Stump Horror Story**.
2. At Perion Street Corner, defeat 10 Blue Snails, 10 Red Snails, and 50
   Stumps.
3. Return to Ayan and complete both quests.
4. If below level 12, continue fighting at Perion Street Corner until
   Blackbull's request is available.
5. Take Blackbull's **Preparations for the Traditional Ceremony**.
6. At Rocky Road II, prioritize Dark Stumps until 20 Leaves are held and kill
   obstructing Stumps opportunistically.
7. Use a Perion return scroll when present; otherwise navigate back. Complete
   the quest with Blackbull.

## Ellinia pack

1. Take Wing's **Eww, It's Slimy!** and **I Need Help on My Homework!**
2. At The Field South of Ellinia, kill Slimes and Stumps until the inventory
   holds at least 50 Squishy Liquids, 10 Slime Bubbles, and 30 Tree Branches.
3. Return to Wing and complete both quests.
4. If below level 12, train locally until Rowen's request is available.
5. Take Rowen's **Why Are Dark Stumps So Dark?**
6. At The Tree That Grew II, defeat 40 Dark Stumps and treat Slimes as
   incidental targets.
7. Use an Ellinia return scroll when present; otherwise navigate back. Complete
   the quest with Rowen.

## Henesys pack

1. Take Bruce's **The Reason Behind the Mushroom Studies**.
2. Reach level 14 before taking level-gated requests from Rina; this guard
   prevents a low-level Agent from repeatedly attempting an unavailable quest.
3. Take Rina's **I Need an Umbrella!**, Jay's **The Terrorizing Red Ribbon
   Pigs**, and Camila's **The Pigs Are Ruining the Produce!**
4. At The Hill East of Henesys, hunt for 10 Mushroom Spores and 20 Green
   Mushroom Caps while counting Pigs and Ribbon Pigs encountered.
5. At The Forest East of Henesys, collect 40 Orange Mushroom Caps and continue
   counting Pigs and Ribbon Pigs.
6. Enter Henesys Pig Farm only if either kill objective remains. Inside, kill
   everything until 30 Pigs and 40 Ribbon Pigs are complete.
7. Leave the mini-dungeon, return to Henesys, and complete all four quests.

## Kerning pack

1. Reach level 12 if needed, then take Icarus' **Intimidating Octopuses**,
   complete **I'm Bored 1**, and take **I'm Bored 2**.
2. At Construction Site North of Kerning City, defeat 30 Octopuses and collect
   40 Tree Branches and 40 Squishy Liquids. Orange Mushrooms are incidental.
3. Return to Kerning and complete both active Icarus requests.
4. Take Nella's **Pigs at the Corner** and Alex's **That Red Isn't For
   Everyone!**
5. Ensure one Kerning return scroll is held, take the taxi to Henesys, and
   enter Henesys Pig Farm.
6. Kill everything in the Pig Farm until both 30-Pig objectives are complete.
7. Leave, use the reserved Kerning scroll, and complete both quests.

## Nautilus pack

1. Buy one Nautilus return scroll.
2. Take Bonnie's **Destructively Strong Pigs**.
3. Reach level 12 if needed, then take Rolonay's **Red Ribbons Around the
   Pig's Neck**, Calico's **Drowsiness from the Orange Mushrooms?**, and
   Bartol's **Camouflaging Slimes**.
4. At The Forest East of Henesys, defeat 30 Orange Mushrooms and 30 Pigs and
   kill Ribbon Pigs until 20 Pig's Ribbons are held.
5. Use an Ellinia scroll when present. Otherwise travel to Henesys and take the
   taxi to Ellinia.
6. At The Field Up North of Ellinia, defeat 30 Slimes.
7. Use the reserved Nautilus scroll and complete all four quests.

## Runtime and recovery contract

- The universal executor owns the top-level lifecycle. The shared pack runtime
  only executes the current catalog step.
- Each step is idempotent: existing quest status, counters, inventory, map, and
  level are checked before mutation.
- The durable progression state stores the career bundle, stage, training
  index, shared-pack index, and next-action time, allowing relog and recovery to
  resume the same step.
- Intention chat is keyed by stage, pack, and step, so automatic retries do not
  repeat the same line.
- Remediation may suspend movement/combat for death, supplies, inventory, or
  navigation recovery and then resume the unchanged plan cursor.
- Fixed pack maps remain authoritative for the MVP. Adaptive quest-map
  selection may run in shadow/fallback mode but does not silently rewrite these
  validated checkpoint routes.
