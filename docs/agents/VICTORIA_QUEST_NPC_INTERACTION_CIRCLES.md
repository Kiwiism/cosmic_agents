# Victoria Quest NPC Interaction Circles

This catalog is the review surface for quest-NPC cohort placement on Victoria
Island. It contains one independently adjustable entry for every NPC placement
that starts or completes a quest in the Victoria level-30-and-below status
catalog.

## Scope

Included:

- Victoria mainland towns, roads, and dungeons;
- Mushroom Castle;
- Hut in the Swamp;
- Florina Beach;
- Nautilus Harbor and The Nautilus;
- all 183 reviewed quest records, including capability-gated and manual-review
  records, so the diagrams remain useful before runtime automation is enabled.

Excluded:

- Maple Island, which retains its hand-tuned Java catalog;
- Ereve, Rien, and Aran's past;
- event-map NPC placements.

The catalog joins the WZ-derived NPC placements with
`victoria-lt30-quest-status.catalog.json`. A quest NPC means an NPC referenced
by a start or completion endpoint in that reviewed scope. This is a data
relationship, not a claim that every quest is currently automated.

## Adjusting a Circle

Edit
[`catalog-overrides/victoria-quest-npc-interaction-circles.json`](catalog-overrides/victoria-quest-npc-interaction-circles.json).
Each entry is keyed by:

```text
mapId:lifeIndex:npcId
```

The editable fields are:

- `centerOffset.x`: move the circle right with a positive value, left with a
  negative value;
- `centerOffset.y`: move the circle down with a positive value, up with a
  negative value;
- `radiusPx`: cohort spread radius in game pixels;
- `dynamicSpread`: whether runtime may sample spread points inside the circle;
- `notes`: reviewer notes that survive regeneration.

NPC position, map identity, quest-link counts, and the matching start/complete
quest ID lists are regenerated from source data. Do not hand-edit those derived
fields.

## Regenerating

First generate the base NPC catalog if it is absent or WZ data changed:

```powershell
powershell -ExecutionPolicy Bypass -File tools\npc-catalog\Export-NpcCatalog.ps1
```

Then refresh the circle catalog and all diagrams:

```powershell
powershell -ExecutionPolicy Bypass -File tools\npc-catalog\Export-VictoriaQuestNpcInteractionCircles.ps1
```

Existing offsets, radii, dynamic-spread flags, and notes are retained by
placement key. To deliberately restore every circle to the default:

```powershell
powershell -ExecutionPolicy Bypass -File tools\npc-catalog\Export-VictoriaQuestNpcInteractionCircles.ps1 -ResetAdjustments
```

For diagnostics only, the former all-quest-level view remains available with
`-AllQuestLevels`. It should not be used when reviewing the level-30 MVP.

The per-map diagrams and index are generated under
[`images/victoria-quest-npc-radii`](images/victoria-quest-npc-radii/README.md).
They show footholds, ropes/ladders, portals, NPC positions, and the adjustable
circles on the same game-coordinate projection.

## Runtime Boundary

This catalog is intentionally not wired into Agent runtime yet. It lets the
placement geometry be reviewed and tuned before a future Victoria quest plan
consumes it. Maple Island remains governed by
`MapleIslandNpcInteractionRadiusCatalog` and
`MapleIslandNpcInteractionPlacementPolicy`.
