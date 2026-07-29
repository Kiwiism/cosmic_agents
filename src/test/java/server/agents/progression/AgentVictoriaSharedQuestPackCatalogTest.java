package server.agents.progression;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentVictoriaSharedQuestPackCatalogTest {
    private static final Set<String> EXPECTED_PACKS = Set.of(
            "perion-pre15", "ellinia-pre15", "henesys-pre15",
            "kerning-pre15", "nautilus-pre15");

    @Test
    void everyCareerReferencesTheSameSharedPackCatalog() {
        AgentVictoriaLevel15Catalog catalog =
                AgentVictoriaLevel15CatalogRepository.defaultRepository().catalog();

        Set<String> referenced = catalog.careers().stream()
                .flatMap(career -> List.of(
                        career.catchUpPlan().homePackId(),
                        career.catchUpPlan().rotationPackId()).stream())
                .collect(Collectors.toSet());

        assertEquals(EXPECTED_PACKS, referenced);
        for (String packId : referenced) {
            AgentVictoriaSharedQuestPackCatalog.Pack pack =
                    AgentVictoriaSharedQuestPackCatalog.require(packId);
            assertEquals(packId, pack.packId());
            assertFalse(pack.steps().isEmpty());
        }
    }

    @Test
    void careerOrderMatchesTheSharedHomeAndRotationDesign() {
        AgentVictoriaLevel15CatalogRepository repository =
                AgentVictoriaLevel15CatalogRepository.defaultRepository();

        assertPacks(repository, "warrior-standard-v1", "perion-pre15", "ellinia-pre15");
        assertPacks(repository, "magician-standard-v1", "ellinia-pre15", "nautilus-pre15");
        assertPacks(repository, "bowman-standard-v1", "henesys-pre15", "kerning-pre15");
        assertPacks(repository, "thief-claw-standard-v1", "kerning-pre15", "perion-pre15");
        assertPacks(repository, "thief-dagger-standard-v1", "kerning-pre15", "perion-pre15");
        assertPacks(repository, "pirate-gun-standard-v1", "nautilus-pre15", "henesys-pre15");
        assertPacks(repository, "pirate-knuckle-standard-v1", "nautilus-pre15", "henesys-pre15");
    }

    @Test
    void checkpoint2ExistsForEveryBundleAndOnlyThiefDaggerIsCaptured() {
        Set<String> bundleIds = AgentCareerBuildBundleRepository.defaultRepository()
                .all().stream()
                .map(AgentCareerBuildBundle::bundleId)
                .collect(Collectors.toSet());

        assertEquals(bundleIds, VictoriaCheckpointBaseline.bundleIds());
        for (String bundleId : bundleIds) {
            VictoriaCheckpointBaseline.Snapshot snapshot =
                    VictoriaCheckpointBaseline.require(bundleId);
            assertNotNull(snapshot.character());
            assertFalse(snapshot.completedQuestIds().isEmpty());
            assertTrue(snapshot.resetQuestIds().containsAll(
                    Set.of(2082, 2089, 2090, 2091)));
            assertEquals("thief-dagger-standard-v1".equals(bundleId), snapshot.captured());
        }
    }

    @Test
    void thiefDaggerCheckpointKeepsTheGreenBeginnerTopEquipped() {
        VictoriaCheckpointBaseline.Snapshot snapshot =
                VictoriaCheckpointBaseline.require("thief-dagger-standard-v1");

        assertTrue(snapshot.items().stream().anyMatch(item ->
                item.itemId() == 1041010
                        && item.inventoryType().equals("EQUIPPED")
                        && item.position() == -5));
        assertFalse(snapshot.items().stream().anyMatch(item -> item.itemId() == 1041002));
    }

    private static void assertPacks(
            AgentVictoriaLevel15CatalogRepository repository,
            String bundleId,
            String expectedHome,
            String expectedRotation) {
        AgentCareerBuildBundle bundle =
                AgentCareerBuildBundleRepository.defaultRepository().find(bundleId).orElseThrow();
        AgentVictoriaLevel15Catalog.CatchUpPlan plan =
                repository.careerFor(bundle).catchUpPlan();
        assertEquals(expectedHome, plan.homePackId());
        assertEquals(expectedRotation, plan.rotationPackId());
    }
}
