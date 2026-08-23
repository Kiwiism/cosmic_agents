# Explorer MapleRoyals optimal 2026 SP profiles

## Scope and source

The default Explorer SP policy is the source-attributed
`mapleroyals-optimal-2026` family, based on the MapleRoyals forum guide
"Optimal SP build order for every class (2026)":

<https://royals.ms/forum/threads/optimal-sp-build-order-for-every-class-2026.258253/>

The catalog covers the seven distinct first-job paths, all twelve second-,
third-, and fourth-job Explorer paths, and separate spear-only and hybrid
Dragon Knight/Dark Knight alternatives. This is 45 profiles in total.
Existing `*-v1` profiles remain legacy alternatives, so persisted tests and
explicit selections still resolve.

## Naming and defaults

| Tier | Example default IDs |
|---|---|
| First job | `mapleroyals-optimal-2026-warrior-first-job`, `mapleroyals-optimal-2026-rogue-assassin-first-job` |
| Second job | `mapleroyals-optimal-2026-fighter`, `mapleroyals-optimal-2026-assassin`, `mapleroyals-optimal-2026-gunslinger` |
| Third job | `mapleroyals-optimal-2026-crusader`, `mapleroyals-optimal-2026-hermit`, `mapleroyals-optimal-2026-outlaw` |
| Fourth job | `mapleroyals-optimal-2026-hero`, `mapleroyals-optimal-2026-night-lord`, `mapleroyals-optimal-2026-corsair` |

Career bundles select the source-named first-job profile. The second-job
advancement catalog selects the source-named branch profile. Once an Agent owns
one of these profiles, a real job-ID change advances it to the exact matching
third- or fourth-job default. A legacy or explicitly selected custom profile is
never silently converted.

Spearman defaults to dual mastery and the hybrid polearm/spear route. The
source-named spear-only alternatives are
`mapleroyals-optimal-2026-dragon-knight-spear` and
`mapleroyals-optimal-2026-dark-knight-spear`.

## Local v83 adaptations

The forum guide targets MapleRoyals and is not copied blindly over local data.
The generated catalog reads local `Skill.wz` names, maxima, and prerequisites;
repository startup rejects an illegal profile.

- Page learns Threaten 3 before Power Guard because local Power Guard requires
  Threaten 3.
- White Knight learns Charged Blow 3 before Magic Crash because that is the
  local prerequisite.
- Local Hero's Will-style skills cap at 5, so `5/10` alternatives use level 5.
- Local Paladin Holy Charge caps at 20 rather than the guide's 30. Remaining
  points use Improved MP Recovery, then Guardian.
- Late Night Lord and Shadower SP-reset suggestions are not executed
  automatically. Their legal core order is retained and remaining SP uses
  documented dump skills without mutating learned skills.

These are compatibility adaptations, not combat-policy changes.

## Representation and ownership

Legacy profiles keep their level-by-level representation. Source-backed
profiles use ordered allocation segments with an exact job ID, advancement
level and grant, optional minimum level, inherited prerequisite snapshot, and
bounded dump list. Minimum levels represent intentional saving, notably Dragon
Knight's Crusher and Outlaw's Burst Fire breakpoints.

The profile service owns SP assignment only. Job advancement, mastery-book
acquisition, AP policy, equipment, and combat skill selection remain separate
capabilities. Fourth-job skills still require normal server-side mastery levels
and books before a live Agent can raise them beyond those limits.

Regenerate the source catalog after a deliberate order or WZ change:

```powershell
.\tools\scripts\Export-MapleRoyalsOptimal2026SpProfiles.ps1
```
