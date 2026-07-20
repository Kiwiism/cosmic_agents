package server.agents.progression;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentVictoriaLevel15CatalogRepositoryTest {
    @Test
    void loadsEveryCareerBundleAndCrossValidatesItsInstructorChain() {
        AgentVictoriaLevel15CatalogRepository repository =
                AgentVictoriaLevel15CatalogRepository.defaultRepository();

        assertEquals("victoria-level15-mvp-catalog-v2", repository.catalog().catalogId());
        assertEquals(5, repository.catalog().careers().size());
        for (AgentCareerBuildBundle bundle : AgentCareerBuildBundleRepository.defaultRepository().all()) {
            AgentVictoriaLevel15Catalog.Career career = repository.careerFor(bundle);
            assertEquals(bundle.firstJobId(), career.firstJobId());
            assertEquals(bundle.instructorNpcId(), career.instructorNpcId());
            assertEquals(bundle.instructorMapId(), career.instructorMapId());
            assertEquals(bundle.instructorTrainingQuestIds(), career.trainingSteps().stream()
                    .map(AgentVictoriaLevel15Catalog.TrainingStep::questId).toList());
        }
    }

    @Test
    void recordsBiggsOlafCareerChoiceAndAllThreeLevelEntryVariants() {
        AgentVictoriaLevel15CatalogRepository repository =
                AgentVictoriaLevel15CatalogRepository.defaultRepository();
        AgentVictoriaLevel15Catalog.IslandHandoff handoff = repository.catalog().islandHandoff();

        assertEquals(104000000, handoff.lithHarborMapId());
        assertEquals(1002101, handoff.olafNpcId());
        assertEquals(1046, handoff.biggsQuestId());
        assertEquals(2081, handoff.olafLessonQuestId());
        assertEquals(104000100, handoff.grindMapId());
        assertEquals(List.of(100100, 100101), handoff.grindMobIds());
        assertEquals(List.of("lv10", "lv9-olaf", "lv9-grind"), repository.catalog().startVariants().stream()
                .map(AgentVictoriaLevel15Catalog.StartVariant::variantId).toList());
        assertEquals(579, repository.startVariant("lv9-olaf").exp());
        assertEquals(0, repository.startVariant("lv9-grind").exp());
        assertTrue(repository.startVariant("lv9-grind").expectsPreJobGrind());

        assertEquals(2077, repository.careerForFirstJob(100).olafPathQuestId());
        assertEquals(2080, repository.careerForFirstJob(200).olafPathQuestId());
        assertEquals(2078, repository.careerForFirstJob(300).olafPathQuestId());
        assertEquals(2079, repository.careerForFirstJob(400).olafPathQuestId());
        assertEquals(2212, repository.careerForFirstJob(500).olafPathQuestId());
    }

    @Test
    void recordsAllVerifiedQuestRequirementsRewardsShopsAndTaxiSelections() {
        AgentVictoriaLevel15CatalogRepository repository =
                AgentVictoriaLevel15CatalogRepository.defaultRepository();

        assertCareer(repository, 100, 0, 102000000, 1022000, 102000003, 1021100, 102000002,
                List.of(2128, 2129, 2130, 2131), List.of(20, 50, 80, 15), 2685);
        assertCareer(repository, 200, 2, 101000000, 1032001, 101000003, 1031100, 101000002,
                List.of(2132, 2133, 2134, 2135), List.of(8, 20, 35, 10), 1428);
        assertCareer(repository, 300, 1, 100000000, 1012100, 100000201, 1011100, 100000102,
                List.of(2136, 2137, 2138, 2139), List.of(16, 40, 65, 15), 2685);
        assertCareer(repository, 400, 3, 103000000, 1052001, 103000003, 1051002, 103000002,
                List.of(2140, 2141, 2142, 2143), List.of(20, 50, 80, 10), 2685);
        assertCareer(repository, 500, 4, 120000000, 1090000, 120000101, 1091002, 120000200,
                List.of(2193, 2194, 2195, 2196), List.of(11, 26, 43, 10), 2685);

        AgentVictoriaLevel15Catalog.Career pirate = repository.careerForFirstJob(500);
        AgentVictoriaLevel15Catalog.TrainingStep fourth = pirate.trainingSteps().get(3);
        assertEquals(100040003, fourth.huntingMapId());
        assertEquals(List.of(1110100), fourth.mobIds());
        assertTrue(pirate.starterKitItemIds().contains(2330000));
        assertTrue(pirate.verifiedShopItemIds().contains(2330000));
        assertEquals(1492000, pirate.preferredStarterWeaponByBundleId().get("pirate-gun-standard-v1"));
        assertEquals(1482000, pirate.preferredStarterWeaponByBundleId().get("pirate-knuckle-standard-v1"));
        AgentVictoriaLevel15Catalog.Career thief = repository.careerForFirstJob(400);
        assertEquals(1472061, thief.preferredStarterWeaponByBundleId().get("thief-claw-standard-v1"));
        assertEquals(1332063, thief.preferredStarterWeaponByBundleId().get("thief-dagger-standard-v1"));
    }

    private static void assertCareer(AgentVictoriaLevel15CatalogRepository repository,
                                     int jobId,
                                     int taxiSelection,
                                     int townMapId,
                                     int instructorNpcId,
                                     int instructorMapId,
                                     int shopNpcId,
                                     int shopMapId,
                                     List<Integer> questIds,
                                     List<Integer> counts,
                                     int totalRewardExp) {
        AgentVictoriaLevel15Catalog.Career career = repository.careerForFirstJob(jobId);
        assertEquals(taxiSelection, career.taxiSelection());
        assertEquals(townMapId, career.townMapId());
        assertEquals(instructorNpcId, career.instructorNpcId());
        assertEquals(instructorMapId, career.instructorMapId());
        assertEquals(shopNpcId, career.shopNpcId());
        assertEquals(shopMapId, career.shopMapId());
        assertEquals(questIds, career.trainingSteps().stream()
                .map(AgentVictoriaLevel15Catalog.TrainingStep::questId).toList());
        assertEquals(counts, career.trainingSteps().stream()
                .flatMap(step -> step.requiredCounts().stream()).toList());
        assertEquals(totalRewardExp, career.trainingSteps().stream()
                .mapToInt(AgentVictoriaLevel15Catalog.TrainingStep::rewardExp).sum());
        assertEquals(Set.copyOf(career.starterKitItemIds()).size(), career.starterKitItemIds().size());
        assertEquals(Set.copyOf(career.verifiedShopItemIds()).size(), career.verifiedShopItemIds().size());
    }
}
