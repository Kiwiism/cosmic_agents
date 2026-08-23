package server.agents.progression;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentSecondJobCatalogTest {
    @Test
    void containsEveryExplorerSecondJobBranch() {
        assertEquals(12, AgentSecondJobCatalog.all().size());
        assertEquals(Set.of(110, 120, 130, 210, 220, 230, 310, 320, 410, 420, 510, 520),
                AgentSecondJobCatalog.all().values().stream()
                        .map(AgentSecondJobCatalog.Branch::targetJobId)
                        .collect(Collectors.toSet()));
    }

    @Test
    void standardAndPirateTrialContractsStayDistinct() {
        var fighter = AgentSecondJobCatalog.require("fighter");
        assertEquals(30, fighter.requiredCount());
        assertEquals(4031013, fighter.collectionItemId());
        assertEquals(0, fighter.requiredSkillId());

        var gunslinger = AgentSecondJobCatalog.require("gunslinger");
        assertEquals(15, gunslinger.requiredCount());
        assertEquals(4031857, gunslinger.collectionItemId());
        assertEquals(5001003, gunslinger.requiredSkillId());
    }

    @Test
    void buildBundleDefaultsAreDeterministicButExplicitTargetWins() {
        assertEquals("assassin", AgentSecondJobCatalog.defaultBranch("thief-claw-standard-v1", 400));
        assertEquals("bandit", AgentSecondJobCatalog.defaultBranch("thief-dagger-standard-v1", 400));
        assertEquals("mapleroyals-optimal-2026-assassin",
                AgentSecondJobCatalog.require("assassin").spProfileId());
        assertEquals("mapleroyals-optimal-2026-spearman",
                AgentSecondJobCatalog.require("spearman").spProfileId());
        assertEquals(520, AgentSecondJobCatalog.forTargetJob(520).targetJobId());
        assertThrows(IllegalArgumentException.class,
                () -> AgentSecondJobCatalog.forTargetJob(999));
        AgentSecondJobCatalog.all().values().forEach(branch ->
                assertEquals(true, branch.spProfileId().startsWith("mapleroyals-optimal-2026-")));
    }
}
