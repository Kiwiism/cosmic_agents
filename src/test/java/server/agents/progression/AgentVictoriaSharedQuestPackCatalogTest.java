package server.agents.progression;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
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
    void nautilusPackMovesTheElliniaScrollPurchaseIntoTheMagicianQuestPlan() {
        AgentVictoriaSharedQuestPackCatalog.Pack pack =
                AgentVictoriaSharedQuestPackCatalog.require("nautilus-pre15");

        AgentVictoriaSharedQuestPackCatalog.Step purchase = pack.steps().stream()
                .filter(step -> "SHOP_ITEM".equals(step.type()) && step.itemId() == 2030002)
                .findFirst()
                .orElseThrow();

        assertEquals(101000002, purchase.mapId());
        assertEquals(List.of("magician-standard-v1"), purchase.bundleIds());
    }

    @Test
    void perionPackVerifiesItsReturnScrollImmediatelyBeforeTheLeavesTrip() {
        AgentVictoriaSharedQuestPackCatalog.Pack pack =
                AgentVictoriaSharedQuestPackCatalog.require("perion-pre15");

        int purchaseIndex = indexOf(pack, step ->
                "SHOP_ITEM".equals(step.type()) && step.itemId() == 2030003);
        int huntIndex = indexOf(pack, step ->
                "HUNT".equals(step.type()) && step.mapId() == 101030200);
        int useIndex = indexOf(pack, step ->
                "USE_SCROLL".equals(step.type()) && step.itemId() == 2030003);

        assertEquals(huntIndex - 1, purchaseIndex);
        assertEquals(huntIndex + 1, useIndex);
    }

    @Test
    void elliniaMaterialHuntsReserveThePurchasedScrollForTheFinalDarkStumpReturn() {
        AgentVictoriaSharedQuestPackCatalog.Pack pack =
                AgentVictoriaSharedQuestPackCatalog.require("ellinia-pre15");

        List<AgentVictoriaSharedQuestPackCatalog.Step> hunts = pack.steps().stream()
                .filter(step -> "HUNT".equals(step.type()))
                .toList();

        assertEquals(3, hunts.size());
        assertTrue(hunts.get(0).skipReturnScrollPreparation());
        assertTrue(hunts.get(1).skipReturnScrollPreparation());
        assertFalse(hunts.get(2).skipReturnScrollPreparation());
    }

    @Test
    void kerningPackPreparesForItsLongMaterialReturnBeforeTheFirstGroupedHunt() {
        AgentVictoriaSharedQuestPackCatalog.Pack pack =
                AgentVictoriaSharedQuestPackCatalog.require("kerning-pre15");
        int octopusHuntIndex = indexOf(pack, step ->
                "HUNT".equals(step.type()) && step.mapId() == 102040000);

        assertEquals(100050000,
                AgentVictoriaSharedQuestPackRuntime.returnPreparationMapId(
                        pack, octopusHuntIndex));
        assertTrue(AgentQuestReturnScrollPolicy.qualifies(
                100050000, pack.homeTownMapId()));
    }

    @Test
    void henesysPackAcceptsThreeAvailableQuestsBeforeItsAuthoredHunts() {
        AgentVictoriaSharedQuestPackCatalog.Pack pack =
                AgentVictoriaSharedQuestPackCatalog.require("henesys-pre15");

        assertEquals(List.of(2088, 28269, 28268), pack.steps().stream()
                .filter(step -> "QUEST".equals(step.type()) && !step.complete())
                .limit(3)
                .map(AgentVictoriaSharedQuestPackCatalog.Step::questId)
                .toList());
        assertFalse(pack.steps().stream().anyMatch(step -> "LEVEL_GRIND".equals(step.type())));

        AgentVictoriaSharedQuestPackCatalog.Step caps = pack.steps().get(4);
        assertEquals("HUNT", caps.type());
        assertEquals(100000002, caps.mapId());
        assertTrue(caps.skipReturnScrollPreparation());
        assertEquals(List.of(1210102), caps.preferredMobIds());
        assertEquals(List.of(1110100), caps.incidentalMobIds());

        AgentVictoriaSharedQuestPackCatalog.Step spores = pack.steps().get(5);
        assertEquals(100010000, spores.mapId());
        assertTrue(spores.skipReturnScrollPreparation());
        assertEquals(List.of(120100), spores.preferredMobIds());
        assertEquals(Set.of(100101, 130101, 1210100), Set.copyOf(spores.incidentalMobIds()));

        int rinaStart = indexOf(pack, step -> step.questId() == 28267 && !step.complete());
        int camilaComplete = indexOf(pack, step -> step.questId() == 28268 && step.complete());
        assertEquals(camilaComplete + 1, rinaStart);
    }

    @Test
    void checkpoint2ExistsForEveryBundleAndRecordsValidatedCaptures() {
        Set<String> bundleIds = AgentCareerBuildBundleRepository.defaultRepository()
                .all().stream()
                .map(AgentCareerBuildBundle::bundleId)
                .collect(Collectors.toSet());
        Set<String> capturedBundleIds = Set.of(
                "bowman-standard-v1",
                "magician-standard-v1",
                "thief-dagger-standard-v1");

        assertEquals(bundleIds, VictoriaCheckpointBaseline.bundleIds());
        for (String bundleId : bundleIds) {
            VictoriaCheckpointBaseline.Snapshot snapshot =
                    VictoriaCheckpointBaseline.require(bundleId);
            assertNotNull(snapshot.character());
            assertFalse(snapshot.completedQuestIds().isEmpty());
            assertTrue(snapshot.resetQuestIds().containsAll(
                    Set.of(2082, 2089, 2090, 2091)));
            assertEquals(capturedBundleIds.contains(bundleId), snapshot.captured());
        }
    }

    @Test
    void magicianCheckpointCapturesCurrentPostInstructorState() {
        VictoriaCheckpointBaseline.Snapshot snapshot =
                VictoriaCheckpointBaseline.require("magician-standard-v1");

        assertEquals("KiwiAgent", snapshot.sourceCharacterName());
        assertEquals(101000002, snapshot.character().mapId());
        assertEquals(11, snapshot.character().level());
        assertEquals(1386, snapshot.character().exp());
        assertEquals(63, snapshot.character().intelligence());
        assertEquals(5457, snapshot.character().mesos());
        assertTrue(snapshot.items().stream().anyMatch(item ->
                item.itemId() == 2030000 && item.quantity() == 10));
        assertTrue(snapshot.skills().stream().anyMatch(skill ->
                skill.skillId() == 2001004 && skill.level() == 1));
    }

    @Test
    void bowmanCheckpointCapturesTheLevelThirteenSnapshotAtHenesys() {
        VictoriaCheckpointBaseline.Snapshot snapshot =
                VictoriaCheckpointBaseline.require("bowman-standard-v1");

        assertEquals("KiwiAgent", snapshot.sourceCharacterName());
        assertEquals(100000000, snapshot.character().mapId());
        assertEquals(13, snapshot.character().level());
        assertEquals(296, snapshot.character().exp());
        assertEquals(73, snapshot.character().dex());
        assertEquals(1502, snapshot.character().mesos());
        assertTrue(snapshot.resetQuestIds().containsAll(Set.of(2088, 28268, 28269)));
        assertTrue(snapshot.skills().stream().anyMatch(skill ->
                skill.skillId() == 3000002 && skill.level() == 6));
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
        assertEquals(12, checkpoint.questPackIndex());
        assertEquals(1052000, pack.steps().get(checkpoint.questPackIndex()).npcId());
        assertEquals(28271, pack.steps().get(checkpoint.questPackIndex()).questId());
        assertTrue(checkpoint.activeQuests().stream().anyMatch(
                quest -> quest.questId() == 28270 && quest.npcId() == 1052103));
    }

    @Test
    void capturedBowmanHuntCheckpointStartsAfterAllThreeHenesysAccepts() {
        VictoriaResumeCheckpointBaseline.ResumeCheckpoint checkpoint =
                VictoriaResumeCheckpointBaseline.require(
                        "bowman-standard-v1", "checkpoint2-henesys-hunt");
        AgentVictoriaSharedQuestPackCatalog.Pack pack =
                AgentVictoriaSharedQuestPackCatalog.require(checkpoint.questPackId());

        assertEquals("CAPTURED", checkpoint.snapshot().provenance());
        assertEquals(4, checkpoint.questPackIndex());
        assertEquals(100000002, pack.steps().get(checkpoint.questPackIndex()).mapId());
        assertEquals(100000000, checkpoint.snapshot().character().mapId());
        assertEquals(5870, checkpoint.position().x());
        assertEquals(Set.of(2088, 28268, 28269), checkpoint.activeQuests().stream()
                .map(VictoriaResumeCheckpointBaseline.ActiveQuest::questId)
                .collect(Collectors.toSet()));
    }

    @Test
    void capturedCheckpoint3StartsTheSharedPerionPackBesideTheTaxi() {
        VictoriaResumeCheckpointBaseline.ResumeCheckpoint checkpoint =
                VictoriaResumeCheckpointBaseline.require(
                        "thief-dagger-standard-v1", "checkpoint3");
        AgentVictoriaSharedQuestPackCatalog.Pack pack =
                AgentVictoriaSharedQuestPackCatalog.require(checkpoint.questPackId());

        assertEquals("CAPTURED", checkpoint.snapshot().provenance());
        assertEquals("perion-pre15", pack.packId());
        assertEquals(0, checkpoint.questPackIndex());
        assertEquals(102000000, checkpoint.snapshot().character().mapId());
        assertEquals(700, checkpoint.position().x());
        assertEquals(1875, checkpoint.position().y());
        assertEquals(14, checkpoint.snapshot().character().level());
        assertEquals(3127, checkpoint.snapshot().character().exp());
        assertEquals(93, checkpoint.snapshot().character().mesos());
        assertTrue(checkpoint.snapshot().completedQuestIds().containsAll(
                Set.of(28270, 28271, 28272)));
        assertTrue(checkpoint.snapshot().resetQuestIds().containsAll(
                Set.of(2082, 28280, 28281)));
        assertTrue(checkpoint.activeQuests().isEmpty());
    }

    @Test
    void capturedMagicianCheckpoint3StartsTheNautilusPackAtTaxiArrival() {
        VictoriaResumeCheckpointBaseline.ResumeCheckpoint checkpoint =
                VictoriaResumeCheckpointBaseline.require(
                        "magician-standard-v1", "checkpoint3");
        AgentVictoriaSharedQuestPackCatalog.Pack pack =
                AgentVictoriaSharedQuestPackCatalog.require(checkpoint.questPackId());

        assertEquals("CAPTURED", checkpoint.snapshot().provenance());
        assertEquals("nautilus-pre15", pack.packId());
        assertEquals(2, checkpoint.questPackIndex());
        assertEquals(120000000, checkpoint.snapshot().character().mapId());
        assertEquals(2156, checkpoint.position().x());
        assertEquals(-406, checkpoint.position().y());
        assertEquals(14, checkpoint.snapshot().character().level());
        assertEquals(257, checkpoint.snapshot().character().exp());
        assertEquals(200, checkpoint.snapshot().character().jobId());
        assertEquals(78, checkpoint.snapshot().character().intelligence());
        assertEquals(4866, checkpoint.snapshot().character().mesos());
        assertTrue(checkpoint.snapshot().items().stream().anyMatch(item ->
                item.itemId() == 2030019 && item.quantity() == 1));
        assertTrue(checkpoint.snapshot().completedQuestIds().containsAll(
                Set.of(28273, 28274)));
        assertTrue(checkpoint.snapshot().resetQuestIds().containsAll(
                Set.of(28276, 28277, 28278, 28279)));
        assertTrue(checkpoint.activeQuests().isEmpty());
    }

    @Test
    void capturedMagicianCheckpoint3HuntResumesAfterAllNautilusQuestsAreAccepted() {
        VictoriaResumeCheckpointBaseline.ResumeCheckpoint checkpoint =
                VictoriaResumeCheckpointBaseline.require(
                        "magician-standard-v1", "checkpoint3-hunt");

        assertEquals("nautilus-pre15", checkpoint.questPackId());
        assertEquals(8, checkpoint.questPackIndex());
        assertEquals(120000000, checkpoint.snapshot().character().mapId());
        assertEquals(2156, checkpoint.position().x());
        assertEquals(-406, checkpoint.position().y());
        assertEquals(14, checkpoint.snapshot().character().level());
        assertEquals(257, checkpoint.snapshot().character().exp());
        assertEquals(200, checkpoint.snapshot().character().jobId());
        assertEquals(4866, checkpoint.snapshot().character().mesos());
        assertEquals(Set.of(28276, 28277, 28278, 28279),
                checkpoint.activeQuests().stream()
                        .map(VictoriaResumeCheckpointBaseline.ActiveQuest::questId)
                        .collect(java.util.stream.Collectors.toSet()));
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

    private static int indexOf(
            AgentVictoriaSharedQuestPackCatalog.Pack pack,
            Predicate<AgentVictoriaSharedQuestPackCatalog.Step> predicate) {
        for (int index = 0; index < pack.steps().size(); index++) {
            if (predicate.test(pack.steps().get(index))) {
                return index;
            }
        }
        throw new AssertionError("expected quest-pack step was not found in " + pack.packId());
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
