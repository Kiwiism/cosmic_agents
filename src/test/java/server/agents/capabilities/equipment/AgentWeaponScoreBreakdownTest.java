package server.agents.capabilities.equipment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentWeaponScoreBreakdownTest {

    @Test
    void exposesWeaponBranchScoreDiagnostics() {
        AgentWeaponScoreBreakdown breakdown = new AgentWeaponScoreBreakdown(10, 20, 30, 40);

        assertEquals(10, breakdown.rawMax());
        assertEquals(20, breakdown.preCycleDamage());
        assertEquals(30, breakdown.cycleMs());
        assertEquals(40, breakdown.normalizedDamage());
    }
}
