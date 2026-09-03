package server.agents.field;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.build.profiles.BuildStep;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentOpqTestFixtureServiceTest {
    private static final int LEVEL_65_SECOND_JOB_SP = 106;

    @Test
    void everyRosterBuildHasTargetsForAllLevel65SkillPoints() {
        for (String buildId : AgentOpqTestFixtureService.BUILD_IDS) {
            AgentBalrogTestFixtureService.Build base = AgentBalrogTestFixtureService.ALL_BUILDS.stream()
                    .filter(candidate -> candidate.buildId().equals(buildId))
                    .findFirst().orElseThrow();
            AgentBalrogTestFixtureService.Build build = AgentOpqTestFixtureService.level65Build(base);

            Map<Integer, Integer> levels = new HashMap<>();
            int spendable = 0;
            for (BuildStep step : build.spBuild()) {
                int before = levels.getOrDefault(step.skillId(), 0);
                spendable += Math.max(0, step.targetLevel() - before);
                levels.put(step.skillId(), Math.max(before, step.targetLevel()));
            }
            assertEquals(LEVEL_65_SECOND_JOB_SP, spendable, buildId);
        }
    }
}
