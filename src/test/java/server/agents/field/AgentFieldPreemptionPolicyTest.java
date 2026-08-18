package server.agents.field;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentFieldPreemptionPolicyTest {
    private static final AgentFieldPreemptionPolicy.Request REQUEST =
            new AgentFieldPreemptionPolicy.Request(99, "quest:test", 10_000L);
    private static final AgentFieldPreemptionPolicy.Policy POLICY =
            new AgentFieldPreemptionPolicy.Policy(true, 15_000L, 5_000, 20_000, 80_000L);

    @Test
    void disabledPolicyCannotDisplaceACompatibleHunter() {
        AgentFieldPreemptionPolicy.Selection result = AgentFieldPreemptionPolicy.select(
                REQUEST, List.of(candidate(1, 1, 100, true)),
                new AgentFieldPreemptionPolicy.Policy(false, 0L, 0, 0, 100_000L));

        assertFalse(result.approved());
        assertEquals("preemption disabled", result.reason());
    }

    @Test
    void selectsTheLowestDisplacementCostInsteadOfTheClosestHunterAlone() {
        AgentFieldPreemptionPolicy.Selection result = AgentFieldPreemptionPolicy.select(
                REQUEST, List.of(
                        candidate(1, 6, 50, false),
                        candidate(2, 1, 500, true)), POLICY);

        assertTrue(result.approved());
        assertEquals(2, result.incumbentAgentId());
        assertEquals("station-2", result.stationId());
    }

    @Test
    void activeLootPlayerPresenceAndCooldownProtectIncumbents() {
        List<AgentFieldPreemptionPolicy.Candidate> protectedHunters = List.of(
                candidate(1, 1, 100, true, true, false, 0L),
                candidate(2, 1, 100, true, false, true, 0L),
                candidate(3, 1, 100, true, false, false, 5_000L));

        AgentFieldPreemptionPolicy.Selection result = AgentFieldPreemptionPolicy.select(
                REQUEST, protectedHunters, POLICY);

        assertFalse(result.approved());
    }

    @Test
    void questVisitorsAndYoungLeasesAreNeverPreempted() {
        AgentFieldPreemptionPolicy.Candidate visitor = new AgentFieldPreemptionPolicy.Candidate(
                1, AgentFieldIntent.Type.QUEST_VISITOR, "visitor", 1, 0, 0,
                20_000L, 0L, false, false, true);
        AgentFieldPreemptionPolicy.Candidate youngHunter = new AgentFieldPreemptionPolicy.Candidate(
                2, AgentFieldIntent.Type.FREE_GRIND, "young", 1, 0, 0,
                14_999L, 0L, false, false, true);

        assertFalse(AgentFieldPreemptionPolicy.select(
                REQUEST, List.of(visitor, youngHunter), POLICY).approved());
    }

    private static AgentFieldPreemptionPolicy.Candidate candidate(
            int agentId, int population, int distance, boolean replacement) {
        return candidate(agentId, population, distance, replacement, false, false, 0L);
    }

    private static AgentFieldPreemptionPolicy.Candidate candidate(
            int agentId,
            int population,
            int distance,
            boolean replacement,
            boolean busy,
            boolean playerOccupied,
            long cooldownMs) {
        return new AgentFieldPreemptionPolicy.Candidate(
                agentId, AgentFieldIntent.Type.FREE_GRIND, "station-" + agentId,
                1, population, distance, 20_000L, cooldownMs,
                busy, playerOccupied, replacement);
    }
}
