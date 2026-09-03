package server.agents.field;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPpqTestFixtureServiceTest {
    @Test
    void everyAgentHasALegalDistinctClassHatAndACompleteLevel67SpPlan() {
        Set<Integer> hats = new HashSet<>();
        for (String buildId : AgentPpqTestFixtureService.BUILD_IDS) {
            var build = AgentPpqTestFixtureService.build(buildId);
            Map<Integer, Integer> finalLevels = new HashMap<>();
            build.spBuild().forEach(step -> finalLevels.merge(
                    step.skillId(), step.targetLevel(), Math::max));
            assertEquals(112, finalLevels.values().stream().mapToInt(Integer::intValue).sum(), buildId);
            for (int gender = 0; gender <= 1; gender++) {
                var equipment = AgentPpqTestFixtureService.LOADOUTS.get(buildId).equipment(gender);
                int hat = equipment.stream().filter(item -> item / 10_000 == 100)
                        .findFirst().orElseThrow();
                assertTrue(hats.add(hat) || gender == 1,
                        buildId + " duplicates another roster hat");
                assertEquals(build.weaponItemId(), equipment.getLast(), buildId);
            }
        }
    }

    @Test
    void visibleClothingIsUniqueApartFromTheRequiredSharedHat() {
        for (int gender = 0; gender <= 1; gender++) {
            Set<Set<Integer>> outfits = new HashSet<>();
            for (String buildId : AgentPpqTestFixtureService.BUILD_IDS) {
                Set<Integer> outfit = new HashSet<>(AgentPpqTestFixtureService.LOADOUTS.get(buildId)
                        .equipment(gender).stream()
                        .filter(item -> Set.of(104, 105, 106).contains(item / 10_000)).toList());
                assertTrue(outfits.add(outfit), buildId + " duplicates visible clothing");
            }
        }
    }

    @Test
    void requestedWeaponsStayWithinTheLevel55To67Selection() {
        assertEquals(Set.of(1_432_022, 1_372_021, 1_452_030,
                        1_332_036, 1_492_018, 1_382_023),
                AgentPpqTestFixtureService.LOADOUTS.values().stream()
                        .map(AgentPpqTestFixtureService.Loadout::weapon)
                        .collect(java.util.stream.Collectors.toSet()));
    }
}
