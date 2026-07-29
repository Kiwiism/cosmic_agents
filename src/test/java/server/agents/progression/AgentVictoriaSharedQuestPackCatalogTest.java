package server.agents.progression;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentVictoriaSharedQuestPackCatalogTest {
    private static final int NEAREST_TOWN_SCROLL = 2030000;
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

    @Test
    void capturedPostNellaResumeStartsAtSharedAlexStep() {
        VictoriaResumeCheckpointBaseline.ResumeCheckpoint checkpoint =
                VictoriaResumeCheckpointBaseline.require(
                        "thief-dagger-standard-v1", "checkpoint2-nella");
        AgentVictoriaSharedQuestPackCatalog.Pack pack =
                AgentVictoriaSharedQuestPackCatalog.require(checkpoint.questPackId());

        assertEquals("CAPTURED", checkpoint.snapshot().provenance());
        assertEquals(11, checkpoint.questPackIndex());
        assertEquals(1052000, pack.steps().get(checkpoint.questPackIndex()).npcId());
        assertEquals(28271, pack.steps().get(checkpoint.questPackIndex()).questId());
        assertTrue(checkpoint.activeQuests().stream().anyMatch(
                quest -> quest.questId() == 28270 && quest.npcId() == 1052103));
    }

    @Test
    void authoredScrollsReachTheirDeclaredTownAndRequiredScrollsArePurchased()
            throws IOException {
        for (String packId : EXPECTED_PACKS) {
            AgentVictoriaSharedQuestPackCatalog.Pack pack =
                    AgentVictoriaSharedQuestPackCatalog.require(packId);
            Set<Integer> purchasedItems = new HashSet<>();
            int lastOperationalMapId = 0;

            for (AgentVictoriaSharedQuestPackCatalog.Step step : pack.steps()) {
                if (step.mapId() > 0 && !"SHOP_ITEM".equals(step.type())) {
                    lastOperationalMapId = step.mapId();
                }
                if ("SHOP_ITEM".equals(step.type())) {
                    assertShopSells(step.npcId(), step.itemId());
                    purchasedItems.add(step.itemId());
                    continue;
                }
                if (!"USE_SCROLL".equals(step.type())
                        && !"OPTIONAL_SCROLL".equals(step.type())) {
                    continue;
                }

                if (step.itemId() == NEAREST_TOWN_SCROLL) {
                    assertEquals(step.destinationMapId(), returnMap(lastOperationalMapId),
                            "nearest-town scroll would return to the wrong town in " + packId);
                } else {
                    assertEquals(step.destinationMapId(), fixedScrollDestination(step.itemId()),
                            "fixed return-scroll destination drift in " + packId);
                }
                if ("USE_SCROLL".equals(step.type())
                        && step.itemId() != NEAREST_TOWN_SCROLL) {
                    assertTrue(purchasedItems.contains(step.itemId()),
                            "required town scroll is not purchased before use in " + packId);
                }
            }
        }
    }

    @Test
    void initialCareerShopScrollsMatchTheCareerTown() throws IOException {
        for (AgentVictoriaLevel15Catalog.Career career
                : AgentVictoriaLevel15CatalogRepository.defaultRepository().catalog().careers()) {
            if (career.initialShopRequiredItemId() == 0) {
                continue;
            }
            assertEquals(career.townMapId(),
                    fixedScrollDestination(career.initialShopRequiredItemId()),
                    "initial return-scroll destination drift for job " + career.firstJobId());
            assertShopSells(career.shopNpcId(), career.initialShopRequiredItemId());
        }
    }

    private static int fixedScrollDestination(int itemId) throws IOException {
        String consume = Files.readString(
                Path.of("wz", "Item.wz", "Consume", "0203.img.xml"));
        String nodeName = String.format("%08d", itemId);
        Pattern item = Pattern.compile("<imgdir name=\"" + nodeName
                + "\">.*?<imgdir name=\"spec\">.*?<int name=\"moveTo\" value=\"(\\d+)\"",
                Pattern.DOTALL);
        Matcher matcher = item.matcher(consume);
        assertTrue(matcher.find(), "missing moveTo for scroll " + itemId);
        return Integer.parseInt(matcher.group(1));
    }

    private static int returnMap(int mapId) throws IOException {
        String map = Files.readString(Path.of("wz", "Map.wz", "Map", "Map1",
                mapId + ".img.xml"));
        Matcher matcher = Pattern.compile("<int name=\"returnMap\" value=\"(\\d+)\"")
                .matcher(map);
        assertTrue(matcher.find(), "missing returnMap for " + mapId);
        return Integer.parseInt(matcher.group(1));
    }

    private static void assertShopSells(int npcId, int itemId) throws IOException {
        String shops = Files.readString(
                Path.of("src", "main", "resources", "db", "data", "102-shopitems-data.sql"));
        Pattern listing = Pattern.compile("\\(\\s*" + npcId + "\\s*,\\s*"
                + itemId + "\\s*,");
        assertTrue(listing.matcher(shops).find(),
                "shop NPC " + npcId + " does not sell required item " + itemId);
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
