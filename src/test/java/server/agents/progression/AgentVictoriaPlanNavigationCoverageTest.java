package server.agents.progression;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards every authored Victoria progression destination at the shared route boundary. */
class AgentVictoriaPlanNavigationCoverageTest {

    @Test
    void everyCareerRouteIsReachableThroughTheSharedVictoriaGraph() {
        AgentVictoriaLevel15Catalog catalog =
                AgentVictoriaLevel15CatalogRepository.defaultRepository().catalog();
        int lithHarbor = catalog.islandHandoff().lithHarborMapId();

        for (AgentVictoriaLevel15Catalog.Career career : catalog.careers()) {
            assertRoute(lithHarbor, career.townMapId(), "Lith Harbor to job town");
            assertRoute(career.townMapId(), career.instructorMapId(), "job instructor");
            assertRoute(career.instructorMapId(), career.trainingGround().entranceMapId(),
                    "instructor to training entrance");
            assertRoute(career.trainingGround().entranceMapId(), career.shopMapId(),
                    "training entrance to supply shop");
            assertRoute(career.shopMapId(), career.milestoneGrind().huntingMapId(),
                    "shop to milestone grind");
            assertRoute(career.townMapId(), career.catchUpPlan().fallbackGrind().huntingMapId(),
                    "town to fallback grind");

            for (int instanceMapId : career.trainingGround().instanceMapIds()) {
                assertRoute(instanceMapId, career.trainingGround().entranceMapId(),
                        "training instance exit");
            }
        }
    }

    @Test
    void everySharedQuestPackStepIsReachableThroughTheSharedVictoriaGraph() {
        for (AgentVictoriaSharedQuestPackCatalog.Pack pack
                : AgentVictoriaSharedQuestPackCatalog.packs()) {
            Set<Integer> possibleMaps = Set.of(pack.homeTownMapId());
            for (AgentVictoriaSharedQuestPackCatalog.Step step : pack.steps()) {
                possibleMaps = advance(pack.packId(), possibleMaps, step);
            }
        }
    }

    @Test
    void everyAutonomousTrainingMapIsReachableFromAVictoriaTown() {
        List<Integer> towns = AgentVictoriaLevel15CatalogRepository.defaultRepository().catalog()
                .careers().stream().map(AgentVictoriaLevel15Catalog.Career::townMapId).toList();
        for (AgentVictoriaTrainingCatalog.TrainingMap map
                : AgentVictoriaTrainingCatalogRepository.defaultRepository().catalog().trainingMaps()) {
            assertTrue(isScriptedMiniDungeon(map.mapId())
                            || isConditionallyGatedTrainingMap(map.mapId())
                            || towns.stream().anyMatch(town ->
                            AgentVictoriaTrainingRouteCatalog.canRoute(town, map.mapId())),
                    () -> "autonomous training map is unreachable from every job town: "
                            + map.mapId() + " (" + map.mapName() + ")");
        }
    }

    @Test
    void everyExecutableAutonomousQuestHuntCanReachACompletionNpcMap() {
        for (AgentVictoriaQuestRuntimeCatalog.Entry quest
                : AgentVictoriaQuestRuntimeCatalogRepository.defaultRepository().catalog().entries()) {
            for (AgentVictoriaQuestRuntimeCatalog.HuntingObjective objective
                    : quest.huntingObjectives()) {
                objective.huntMaps().stream()
                        .filter(hunt -> quest.startMapIds().stream().anyMatch(start ->
                                AgentVictoriaTrainingRouteCatalog.canRoute(start, hunt.mapId())))
                        .forEach(hunt -> assertTrue(quest.completeMapIds().stream()
                                        .anyMatch(completion -> AgentVictoriaTrainingRouteCatalog.canRoute(
                                                hunt.mapId(), completion)),
                                () -> "reachable quest hunt map cannot return to a completion NPC: "
                                        + quest.questId() + " (" + quest.questName() + ") objective="
                                        + objective.objectiveId() + " huntMap=" + hunt.mapId()));
            }
        }
    }

    private static boolean isConditionallyGatedTrainingMap(int mapId) {
        List<AgentVictoriaTrainingCatalog.TrainingChoice> choices =
                AgentVictoriaTrainingCatalogRepository.defaultRepository().catalog().levelPlans()
                        .stream()
                        .flatMap(plan -> plan.choices().stream())
                        .filter(choice -> choice.mapId() == mapId)
                        .toList();
        return !choices.isEmpty() && choices.stream().allMatch(choice -> !choice.conditions().isEmpty());
    }

    private static boolean isScriptedMiniDungeon(int mapId) {
        return AgentVictoriaSharedQuestPackCatalog.packs().stream()
                .anyMatch(pack -> pack.steps().stream()
                        .anyMatch(step -> "MINI_DUNGEON_HUNT".equals(step.type())
                                && mapId >= step.instanceMapIdMin()
                                && mapId <= step.instanceMapIdMax()
                                && AgentVictoriaTrainingRouteCatalog.canRoute(
                                pack.homeTownMapId(), step.destinationMapId())));
    }

    private static Set<Integer> advance(
            String packId,
            Set<Integer> currentMaps,
            AgentVictoriaSharedQuestPackCatalog.Step step) {
        String label = packId + " step " + step.type() + " (quest=" + step.questId() + ")";
        if ("TAXI".equals(step.type())) {
            assertReachableFromAll(currentMaps, step.mapId(), label + " taxi source");
            return Set.of(step.destinationMapId());
        }
        if ("USE_SCROLL".equals(step.type())) {
            return Set.of(step.destinationMapId());
        }
        if ("OPTIONAL_SCROLL".equals(step.type())) {
            Set<Integer> result = new LinkedHashSet<>(currentMaps);
            result.add(step.destinationMapId());
            return Set.copyOf(result);
        }
        if ("TRAVEL".equals(step.type())) {
            assertReachableFromAll(currentMaps, step.destinationMapId(), label);
            return Set.of(step.destinationMapId());
        }
        if ("MINI_DUNGEON_HUNT".equals(step.type())) {
            assertReachableFromAll(currentMaps, step.destinationMapId(), label + " exterior");
            // The shared-pack runtime owns instance entry/exit portal IDs. The ordinary
            // Victoria graph is responsible only for reaching the exterior map.
            return Set.of(step.destinationMapId());
        }
        if (step.mapId() > 0) {
            assertReachableFromAll(currentMaps, step.mapId(), label);
            return Set.of(step.mapId());
        }
        return currentMaps;
    }

    private static void assertReachableFromAll(
            Set<Integer> sources, int destination, String label) {
        for (int source : sources) {
            assertRoute(source, destination, label);
        }
    }

    private static void assertRoute(int source, int destination, String label) {
        assertTrue(AgentVictoriaTrainingRouteCatalog.canRoute(source, destination),
                () -> label + " is not routable: " + source + " -> " + destination);
    }
}
