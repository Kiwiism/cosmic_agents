package server.agents.capabilities.build.profiles;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentSpBuildProfileRepositoryTest {
    private final AgentSpBuildProfileRepository repository = AgentSpBuildProfileRepository.defaultRepository();

    @Test
    void catalogContainsAllIndependentFirstJobProfiles() {
        assertEquals(7, repository.all().size());
    }

    @Test
    void ordinaryFirstJobProfilesProduceRequestedLevelThirtyResults() {
        assertTargets("bowman-first-job-lv30-v1", 61, Map.of(
                3001004, 1, 3000000, 3, 3000002, 8,
                3000001, 20, 3001005, 20, 3001003, 9));
        assertTargets("thief-claw-first-job-lv30-v1", 61, Map.of(
                4001344, 20, 4000000, 10, 4000001, 8,
                4001002, 3, 4001003, 20));
        assertTargets("thief-dagger-first-job-lv30-v1", 61, Map.of(
                4001334, 20, 4000000, 20, 4001002, 3, 4001003, 18));
        assertTargets("pirate-gun-first-job-lv30-v1", 61, Map.of(
                5001003, 20, 5001005, 10, 5000000, 20, 5001002, 11));
        assertTargets("pirate-knuckle-first-job-lv30-v1", 61, Map.of(
                5001002, 20, 5001005, 1, 5000000, 20, 5001001, 20));
    }

    @Test
    void magicianUsesAllSixtySevenAvailableFirstJobPoints() {
        assertTargets("magician-first-job-lv30-v1", 67, Map.of(
                2001004, 1, 2000000, 16, 2000001, 10,
                2001005, 20, 2001002, 20));
    }

    @Test
    void warriorUsesUnspecifiedRemainderOnRecoveryAfterRequestedCoreTargets() {
        assertTargets("warrior-first-job-lv30-v1", 61, Map.of(
                1001004, 20, 1000000, 11, 1000001, 10, 1001005, 20));
    }

    private void assertTargets(String profileId, int availableSp, Map<Integer, Integer> expected) {
        AgentSpBuildProfile profile = repository.find(profileId).orElseThrow();
        Map<Integer, Integer> levels = new HashMap<>();
        int remaining = availableSp;
        for (AgentSpBuildProfile.LevelPlan levelPlan : profile.levels()) {
            for (AgentSpBuildProfile.SkillPoints allocation : levelPlan.allocations()) {
                int gain = Math.min(remaining, allocation.points());
                levels.merge(allocation.skillId(), gain, Integer::sum);
                remaining -= gain;
            }
        }
        assertEquals(0, remaining);
        assertEquals(expected, levels);
    }
}
