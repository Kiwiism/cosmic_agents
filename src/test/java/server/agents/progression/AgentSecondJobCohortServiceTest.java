package server.agents.progression;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentSecondJobCohortServiceTest {
    @Test
    void rosterCoversEachExplorerFamilyOnceWithValidNames() {
        var roster = AgentSecondJobCohortService.roster();
        assertEquals(5, roster.size());
        assertEquals(Set.of(AgentSecondJobCatalog.Family.values()), roster.stream()
                .map(member -> AgentSecondJobCatalog.require(member.branchId()).family())
                .collect(Collectors.toSet()));
        assertEquals(5, roster.stream().map(AgentSecondJobCohortService.CohortMember::name)
                .collect(Collectors.toSet()).size());
        assertTrue(roster.stream().allMatch(member -> member.name().length() <= 12));
    }

    @Test
    void allBranchRosterCoversEverySecondJobWithUniqueValidNames() {
        var roster = AgentSecondJobCohortService.allBranchRoster();
        assertEquals(AgentSecondJobCatalog.all().keySet(), roster.stream()
                .map(AgentSecondJobCohortService.CohortMember::branchId)
                .collect(Collectors.toSet()));
        assertEquals(12, roster.stream().map(AgentSecondJobCohortService.CohortMember::name)
                .collect(Collectors.toSet()).size());
        assertTrue(roster.stream().allMatch(member -> member.name().length() <= 12));
    }

    @Test
    void branchCanBeSelectedWithoutAnExplicitSeed() {
        var selection = AgentSecondJobCohortService.parseStart(
                new String[]{"start", "brawler"}, 1234L);
        assertEquals(1234L, selection.seed());
        assertEquals(List.of("brawler"), selection.roster().stream()
                .map(AgentSecondJobCohortService.CohortMember::branchId).toList());
    }

    @Test
    void explicitSeedAndRequestedBranchesArePreserved() {
        var selection = AgentSecondJobCohortService.parseStart(
                new String[]{"start", "20260826", "spearman", "crossbowman", "fp-wizard"}, 1L);
        assertEquals(20260826L, selection.seed());
        assertEquals(List.of("spearman", "fp-wizard", "crossbowman"), selection.roster().stream()
                .map(AgentSecondJobCohortService.CohortMember::branchId).toList());
    }

    @Test
    void allSelectorLaunchesEveryBranch() {
        var selection = AgentSecondJobCohortService.parseStart(
                new String[]{"start", "all"}, 55L);
        assertEquals(55L, selection.seed());
        assertEquals(12, selection.roster().size());
    }
}
