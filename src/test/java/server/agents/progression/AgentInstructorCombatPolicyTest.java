package server.agents.progression;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentInstructorCombatPolicyTest {
    @Test
    void exposesOnlyPositiveNonRequiredLocalSpawnsAsIncidentals() {
        Set<Integer> incidentals = AgentInstructorCombatPolicy.localIncidentalMobIds(
                Set.of(1110100),
                Map.of(1110100, 2, 1210100, 5, 130101, 0),
                Map.of(1210101, 3, 1210100, 1));

        assertEquals(Set.of(1210100, 1210101), incidentals);
    }
}
