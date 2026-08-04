package server.agents.capabilities.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AgentSpawnPressurePolicyTest {
    private static final int SLIME = 210100;
    private static final int DARK_STUMP = 1110101;

    @Test
    void clearsOverrepresentedFillerWhenPreferredSpeciesFallsBelowAuthoredShare() {
        assertEquals(Set.of(SLIME), AgentSpawnPressurePolicy.selectFallbackMobIds(
                Map.of(SLIME, 10, DARK_STUMP, 3),
                Map.of(SLIME, 20, DARK_STUMP, 2),
                Set.of(DARK_STUMP),
                Set.of(SLIME),
                80));
    }

    @Test
    void preservesAuthoredRatioWhenPreferredSpeciesIsNotUnderrepresented() {
        assertEquals(Set.of(), AgentSpawnPressurePolicy.selectFallbackMobIds(
                Map.of(SLIME, 10, DARK_STUMP, 8),
                Map.of(SLIME, 10, DARK_STUMP, 8),
                Set.of(DARK_STUMP),
                Set.of(SLIME),
                80));
    }

    @Test
    void clearsConfiguredFillerWhenNoPreferredMonsterIsAlive() {
        assertEquals(Set.of(SLIME), AgentSpawnPressurePolicy.selectFallbackMobIds(
                Map.of(SLIME, 10, DARK_STUMP, 3),
                Map.of(SLIME, 6),
                Set.of(DARK_STUMP),
                Set.of(SLIME),
                80));
    }

    @Test
    void ignoresFallbackSpeciesThatIsNotOverrepresented() {
        assertEquals(Set.of(), AgentSpawnPressurePolicy.selectFallbackMobIds(
                Map.of(SLIME, 10, DARK_STUMP, 3),
                Map.of(SLIME, 10, DARK_STUMP, 3),
                Set.of(DARK_STUMP),
                Set.of(SLIME),
                100));
    }
}
