# NPC relevance audit

This tool inventories every Cosmic NPC asset and cross-references map placement,
NPC scripts, quest giver/recipient data, database shops, warp behavior, rewards,
and seasonal/event evidence. It produces review recommendations, not automatic
deletions: decorative NPCs and event systems still require a design decision.

```powershell
powershell -ExecutionPolicy Bypass -File tools/npc-audit/Export-NpcRelevanceAudit.ps1
```

Outputs are written to `tmp/npc-audit`:

- `npc-relevance-all.csv` — every NPC;
- `npc-relevance-review.csv` — rows needing review or apparently unused;
- `npc-placed-review.csv` — only placed NPCs needing a decision;
- `npc-event-review.csv` — all NPCs with seasonal or expired-event evidence;
- `npc-regular-town-review.csv` — actionable review limited to non-event town placements;
- `npc-map-placements.csv` — exact map life nodes and coordinates for removal planning;
- `summary.json` — category counts and run metadata.

Generate the visual regular-town reviewer after running the audit:

```powershell
powershell -ExecutionPolicy Bypass -File tools/npc-audit/Export-NpcReviewSite.ps1
```

Open `tmp/npc-audit/npc-review.html`. Selections persist in browser storage and
can be exported as an exact JSON or CSV map-placement removal plan.
