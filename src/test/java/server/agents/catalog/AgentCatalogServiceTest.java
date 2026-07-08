package server.agents.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCatalogServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void loaderLoadsCurrentGeneratedCatalogs() {
        AgentCatalogService service = loadCatalog();

        assertTrue(service.bundle().hasFile("maps"));
        assertTrue(service.bundle().hasFile("npcs"));
        assertTrue(service.bundle().hasFile("mapleIslandMvp"));
        assertTrue(service.bundle().hasFile("reactors"));
    }

    @Test
    void loaderAllowsMissingOptionalReactorCatalog() {
        Path repoRoot = Path.of(".").toAbsolutePath().normalize();
        CatalogLoadOptions options = new CatalogLoadOptions(
                repoRoot,
                repoRoot.resolve("tmp/game-catalog"),
                repoRoot.resolve("tmp/npc-catalog"),
                repoRoot.resolve("tmp/agent-llm-catalog"),
                repoRoot.resolve("docs/agents/catalog-overrides"),
                tempDir.resolve("missing-reactor-catalog"));

        AgentCatalogService service = AgentCatalogService.load(options);

        assertFalse(service.bundle().hasFile("reactors"));
        assertTrue(service.queries().reactor().allReactors().isEmpty());
    }

    @Test
    void loaderFailsClearlyWhenRequiredCatalogIsMissing() {
        CatalogLoadOptions options = CatalogLoadOptions.fromRepoRoot(tempDir);

        CatalogLookupException exception = assertThrows(CatalogLookupException.class,
                () -> new CatalogBundleLoader().load(options));

        assertTrue(exception.getMessage().contains("Missing required catalog file"));
        assertTrue(exception.getMessage().contains("maps"));
    }

    @Test
    void npcQueriesFindPlacementActionsApproachSpotsAndDialogueTiming() {
        CatalogQueryService queries = loadCatalog().queries();

        CatalogRecord heena = queries.npc().findById(2101).orElseThrow();
        assertEquals("Heena", heena.stringValue("name").orElseThrow());
        assertFalse(queries.npc().findByName("Heena").isEmpty());

        List<CatalogRecord> placements = queries.npc().placementsForNpc(2101);
        assertTrue(placements.stream().anyMatch(placement -> placement.intValue("mapId").orElse(-1) == 10000));
        assertTrue(queries.npc().npcsInMap(10000).stream()
                .anyMatch(placement -> placement.intValue("npcId").orElse(-1) == 2101));

        assertFalse(queries.npc().questStartActionsForNpc(2101).isEmpty());
        assertFalse(queries.npc().actionsForQuest(1000).isEmpty());
        assertTrue(queries.npc().shopOrServiceDetailsForNpc(2000).isPresent());
        assertFalse(queries.npc().approachCandidates(2101, 10000).isEmpty());
        assertTrue(queries.npc().seededApproachCandidate(2101, 10000, 12345L).isPresent());
        assertTrue(queries.npc().dialogueTiming(1000, "start").isPresent());
    }

    @Test
    void mapQueriesFindMapsPortalsNpcsMobsAndMapleIslandRelevance() {
        CatalogQueryService queries = loadCatalog().queries();

        CatalogRecord town = queries.map().findById(10000).orElseThrow();
        assertEquals("Mushroom Town", town.stringValue("mapName").orElseThrow());
        assertFalse(queries.map().findByName("Mushroom Town").isEmpty());
        assertTrue(queries.map().summary(10000).isPresent());
        assertFalse(queries.map().npcsInMap(10000).isEmpty());
        assertFalse(queries.map().portalEdgesFrom(10000).isEmpty());
        assertFalse(queries.map().connectedMapIds(10000).isEmpty());
        assertTrue(queries.map().isMapleIslandRelevantMap(10000));

        assertTrue(queries.map().mobSpawnSummary(50000).isPresent());
        assertTrue(queries.map().mobsInMap(50000).stream()
                .anyMatch(mob -> mob.intValue("mobId").orElse(-1) == 100100));
    }

    @Test
    void mobAndItemQueriesFindSpawnDropsAndItemSources() {
        CatalogQueryService queries = loadCatalog().queries();

        CatalogRecord snail = queries.mob().findById(100100).orElseThrow();
        assertEquals("Snail", snail.stringValue("name").orElseThrow());
        assertFalse(queries.mob().findByName("Snail").isEmpty());
        assertFalse(queries.mob().spawnMaps(100100).isEmpty());
        assertFalse(queries.mob().dropsForMob(100100).isEmpty());

        CatalogRecord shell = queries.item().findById(4000019).orElseThrow();
        assertEquals("Snail Shell", shell.stringValue("name").orElseThrow());
        assertFalse(queries.item().findByName("Snail Shell").isEmpty());
        assertTrue(queries.item().sourcesForItem(4000019).isPresent());
        assertTrue(queries.item().mobsDroppingItem(4000019).stream()
                .anyMatch(drop -> drop.intValue("sourceId").orElse(-1) == 100100));
        assertFalse(queries.item().dropEntriesForItem(4000019).isEmpty());
        assertFalse(queries.item().dropSourceClassifications().isEmpty());
    }

    @Test
    void questQueriesFindQuestObjectivePlanAndNpcActions() {
        CatalogQueryService queries = loadCatalog().queries();

        CatalogRecord quest = queries.quest().findById(1000).orElseThrow();
        assertEquals(2101, quest.intValue("startNpcId").orElseThrow());
        assertEquals(2100, quest.intValue("completeNpcId").orElseThrow());

        CatalogRecord objectivePlan = queries.quest().objectivePlan(1000).orElseThrow();
        assertFalse(objectivePlan.recordList("objectives").isEmpty());
        assertFalse(queries.quest().startActionsForQuest(1000).isEmpty());
        assertFalse(queries.quest().completeActionsForQuest(1000).isEmpty());
    }

    @Test
    void mapleIslandMvpQueriesExposeRulesObjectivesForbiddenActionsAndFastIndexes() {
        CatalogQueryService queries = loadCatalog().queries();
        MapleIslandMvpCatalogQuery mvp = queries.mapleIslandMvp();

        assertEquals("maple-island-mvp", mvp.plan().stringValue("planId").orElseThrow());
        assertFalse(mvp.questIdsInMvpSequence().isEmpty());
        assertTrue(mvp.questRule(1000).isPresent());
        assertTrue(mvp.objective("1000:start").isPresent());
        assertFalse(mvp.objectivesForQuest(1000).isEmpty());
        assertTrue(mvp.specialRule("pio-reactor-boxes").isPresent());
        assertTrue(mvp.specialRule("yoona-cash-shop-shopping-guide").isPresent());
        assertTrue(mvp.isForbiddenNpcTravel(22000));
        assertTrue(mvp.isForbiddenQuestComplete(1046));
        assertTrue(mvp.routeFactsForMap(10000).isPresent());
    }

    @Test
    void reactorQueriesExposeMapIdReactorIdQuestItemAndPioLookups() {
        CatalogQueryService queries = loadCatalog().queries();
        ReactorCatalogQuery reactors = queries.reactor();

        assertFalse(reactors.allReactors().isEmpty());
        assertFalse(reactors.reactorsInMap(1000000).isEmpty());
        assertFalse(reactors.findReactorById(2001).isEmpty());
        assertFalse(reactors.findReactorsForQuest(1008).isEmpty());
        assertFalse(reactors.findReactorsDroppingItem(4031161).isEmpty());
        assertFalse(reactors.mapleIslandPioReactors().isEmpty());
    }

    @Test
    void queryResultsDoNotExposeMutableCollections() {
        CatalogQueryService queries = loadCatalog().queries();

        List<CatalogRecord> placements = queries.npc().placementsForNpc(2101);
        assertThrows(UnsupportedOperationException.class, () -> placements.add(placements.getFirst()));

        CatalogRecord heena = queries.npc().findById(2101).orElseThrow();
        assertThrows(UnsupportedOperationException.class, () -> heena.fields().put("name", "Changed"));

        Object placementsField = heena.fields().get("placements");
        if (placementsField instanceof List<?> list && !list.isEmpty()) {
            assertThrows(UnsupportedOperationException.class, list::clear);
        }

        List<CatalogRecord> reactors = queries.reactor().reactorsInMap(1000000);
        assertFalse(reactors.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> reactors.add(reactors.getFirst()));
    }

    private static AgentCatalogService loadCatalog() {
        return AgentCatalogService.loadFromRepoRoot(Path.of("."));
    }
}
