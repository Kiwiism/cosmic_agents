package server.agents.capabilities.build.profiles;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentSpBuildProfileRepositoryTest {
    private final AgentSpBuildProfileRepository repository = AgentSpBuildProfileRepository.defaultRepository();

    @Test
    void catalogContainsFirstJobAndSecondJobHandoffProfiles() {
        assertEquals(66, repository.all().size());
        assertTargets("fighter-second-job-lv30-v1", 1, Map.of(1100000, 1));
        assertTargets("cleric-second-job-lv30-v1", 1, Map.of(2300000, 1));
        assertTargets("gunslinger-second-job-lv30-v1", 1, Map.of(5200000, 1));
    }

    @Test
    void mapleRoyalsOptimal2026ProfilesCoverEveryExplorerPathThroughFourthJob() {
        List<AgentSpBuildProfile> profiles = repository.all().stream()
                .filter(AgentSpBuildProfile::isMapleRoyalsOptimal2026)
                .toList();
        assertEquals(45, profiles.size());
        assertEquals(7, profiles.stream().filter(profile -> profile.startingLevel() <= 10).count());
        assertEquals(12, profiles.stream().filter(profile -> profile.startingLevel() == 30).count());
        assertEquals(13, profiles.stream().filter(profile -> profile.startingLevel() == 70).count());
        assertEquals(13, profiles.stream().filter(profile -> profile.startingLevel() == 120).count());
    }

    @Test
    void localWzAdaptationsRemainExplicitAndLegal() {
        AgentSpBuildProfile page = repository.find("mapleroyals-optimal-2026-page").orElseThrow();
        assertBefore(page, 1201006, 1201007);
        AgentSpBuildProfile whiteKnight = repository.find(
                "mapleroyals-optimal-2026-white-knight").orElseThrow();
        assertBefore(whiteKnight, 1211002, 1211009);
        assertEquals(5, repository.skill(1121011).maxLevel());
    }

    @Test
    void intentionalSaveBreakpointsAreRepresentedAsMinimumLevels() {
        AgentSpBuildProfile dragonKnight = repository.find(
                "mapleroyals-optimal-2026-dragon-knight-hybrid").orElseThrow();
        assertEquals(90, dragonKnight.segments().stream()
                .filter(segment -> segment.skillId() == 1311001)
                .reduce((first, second) -> second).orElseThrow().minimumLevel());
        AgentSpBuildProfile outlaw = repository.find(
                "mapleroyals-optimal-2026-outlaw").orElseThrow();
        assertEquals(74, outlaw.segments().stream()
                .filter(segment -> segment.skillId() == 5210000)
                .findFirst().orElseThrow().minimumLevel());
    }

    @Test
    void assassinFollowsTheExecutableCriticalHasteBoosterMasteryBuild() {
        assertTargets("assassin-second-job-lv70-v1", 121, Map.of(
                4100000, 20,
                4100001, 30,
                4100002, 3,
                4101003, 20,
                4101004, 20,
                4101005, 28));
    }

    @Test
    void spearmanFollowsTheDualWeaponHyperBodyBuild() {
        assertEquals(Map.of(1000001, 10), repository.find("spearman-second-job-lv70-v1")
                .orElseThrow().inheritedSkillLevels());
        assertTargets("spearman-second-job-lv70-v1", 121, Map.of(
                1000002, 2,
                1300000, 20,
                1300001, 20,
                1301004, 20,
                1301005, 20,
                1301006, 9,
                1301007, 30));
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

    private static void assertBefore(AgentSpBuildProfile profile, int firstSkillId, int secondSkillId) {
        List<Integer> order = profile.segments().stream()
                .map(AgentSpBuildProfile.AllocationSegment::skillId)
                .toList();
        org.junit.jupiter.api.Assertions.assertTrue(
                order.indexOf(firstSkillId) < order.indexOf(secondSkillId));
    }
}
