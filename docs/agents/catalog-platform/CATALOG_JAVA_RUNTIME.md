# Agent Catalog Java Runtime

Purpose:

```text
Provide a read-only Java loader/query API over generated Agent catalog JSON
artifacts without wiring into live Agent ticks while reconstruction is ongoing.
```

## Package

```text
src/main/java/server/agents/catalog
src/test/java/server/agents/catalog
```

The package is intentionally independent from live Agent runtime execution. It
does not mutate server state, does not scan WZ/XML/SQL during decisions, and
does not call movement, combat, quest, NPC, or loot execution.

## Loading

Default repo-root load:

```java
AgentCatalogService catalog = AgentCatalogService.loadFromRepoRoot(Path.of("."));
CatalogQueryService queries = catalog.queries();
```

Explicit directories:

```java
CatalogLoadOptions options = new CatalogLoadOptions(
        repoRoot,
        repoRoot.resolve("tmp/game-catalog"),
        repoRoot.resolve("tmp/npc-catalog"),
        repoRoot.resolve("tmp/agent-llm-catalog"),
        repoRoot.resolve("docs/agents/catalog-overrides"),
        repoRoot.resolve("tmp/reactor-catalog"));

AgentCatalogService catalog = AgentCatalogService.load(options);
```

Missing required files fail with `CatalogLookupException`. The reactor catalog
is optional/deferred and does not fail loading when absent.

## Query Areas

NPC:

- find NPC by id/name.
- list NPC placements by NPC id.
- list NPCs in a map.
- list NPC actions.
- list quest start/complete actions.
- list shop/service actions.
- read direct shop/service interaction details from NPC records.
- return generated interaction approach candidates.
- return seeded approach candidate for varied Agent positioning.
- return quest dialogue timing hints.

Map:

- find map by id/name.
- read map summary.
- list NPCs and mobs in map.
- list portal edges and connected map ids.
- detect Maple Island MVP route maps.

Mob:

- find mob by id/name.
- list spawn maps.
- read map spawn summary.
- list mob drops.
- list mob drop sources for an item.

Item:

- find item by id/name.
- read item source index.
- list mobs that drop item.
- list drop entries.
- read reviewed non-mob source classifications.

Quest:

- find quest by id.
- read generated objective plan.
- list NPC actions for quest.
- list start/complete actions.
- list kill/item objective records where present.
- read Victoria `< lv30` review status when the override catalog exists.

Reactor:

- list all generated reactor placements.
- list reactors in a map.
- list placements by reactor id.
- list reactors linked to a quest id when SQL/script hints expose the link.
- list reactors that can drop an item id when SQL/script hints expose the link.
- list Maple Island/Pio candidate box reactors for quest `1008` / items
  `4031161` and `4031162`.

Maple Island MVP:

- read full MVP plan catalog.
- query MVP rule by quest id.
- query objective by objective id.
- list objectives for quest.
- read special rules such as Pio reactor boxes, Yoona scripted item, Roger apple,
  and Biggs start-only.
- check forbidden Shanks/off-island travel.
- check forbidden quest completion.
- read route facts by map id.

## Data Shape Boundary

The runtime uses Jackson internally but does not expose `JsonNode` through the
public query API. Query methods return immutable `CatalogRecord` values or
immutable lists.

This keeps future Agent capabilities and LLM gateway code independent from raw
generated JSON shape.

## Reactor Catalog

The optional reactor catalog is generated at:

```text
tmp/reactor-catalog/generated_reactor_catalog.json
```

Source inputs:

- `wz/Map.wz/Map/**/*.img.xml` for reactor placements.
- `wz/Reactor.wz/*.img.xml` for reactor metadata/action hints.
- `src/main/resources/db/data/131-reactordrops-data.sql` for reactor item and
  quest drop links.
- `scripts/reactor/*.js` for script-source and conservative item/quest hints.

The loader treats this catalog as optional so clean setups can still boot the
catalog runtime before reactor export runs. When present, the runtime builds
map, reactor-id, quest-id, and item-id indexes. Amherst/Maple Island Reactor
Capability should use these query methods later, then validate live map reactor
state before hitting anything.

## Validation

Catalog artifact checks:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\catalog\Get-CatalogRuntimeReadiness.ps1 -Json
powershell -ExecutionPolicy Bypass -File .\tools\catalog\Test-CatalogBundlePrep.ps1 -Json
powershell -ExecutionPolicy Bypass -File .\tools\reactor-catalog\Test-ReactorCatalog.ps1 -Json
```

Focused Java tests:

```powershell
.\mvnw.cmd -q "-Dtest=server.agents.catalog.*Test" test
```

If Maven compilation is blocked by unrelated reconstruction work, the catalog
package can still be compile-checked independently against Jackson until the
active reconstruction branch compiles again.
