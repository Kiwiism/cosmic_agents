package server.agents.economy.persistence;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DecisionEvidenceTest {
    @Test
    void rejectsAChosenActionWithoutAnyRejectedAlternative() {
        assertThrows(IllegalArgumentException.class, () -> evidence(List.of()));
    }

    @Test
    void retainsExplicitRejectedAlternativesForDashboardExplanation() {
        var alternatives = List.of(Map.<String, Object>of(
                "action", "HOLD", "rejectionReason", "LIQUIDITY_REQUIRED"));

        assertEquals(alternatives, evidence(alternatives).alternatives());
    }

    private static DecisionEvidence evidence(List<Map<String, Object>> alternatives) {
        return new DecisionEvidence(UUID.randomUUID(), UUID.randomUUID(), "agent-1", Instant.EPOCH,
                "NPC_DISPOSITION", Map.of("action", "SELL_TO_NPC"), alternatives,
                Map.of(), Map.of(), Map.of(), null, null, "0".repeat(64), "catalog");
    }
}
