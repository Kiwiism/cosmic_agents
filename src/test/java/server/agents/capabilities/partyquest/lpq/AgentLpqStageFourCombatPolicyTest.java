package server.agents.capabilities.partyquest.lpq;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentLpqStageFourCombatPolicyTest {
    @Test
    void roomsUseTheirAuthoredEyeMonsterInsteadOfDependingOnSpawnMetadata() {
        assertEquals(Set.of(9_300_008), AgentLpqCoordinator.stageFourCombatTargets(922_010_401));
        assertEquals(Set.of(9_300_008), AgentLpqCoordinator.stageFourCombatTargets(922_010_403));
        assertEquals(Set.of(9_300_014), AgentLpqCoordinator.stageFourCombatTargets(922_010_404));
        assertEquals(Set.of(9_300_014), AgentLpqCoordinator.stageFourCombatTargets(922_010_405));
        assertEquals(Set.of(), AgentLpqCoordinator.stageFourCombatTargets(922_010_400));
    }
}
