package server.agents.field;

import org.junit.jupiter.api.Test;
import server.agents.model.AgentPosition;

import java.awt.Point;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentFieldAssignmentPlannerTest {
    private final AgentFieldAssignmentPlanner planner = new AgentFieldAssignmentPlanner();

    @Test
    void partyModePartitionsEveryCellAndKeepsTerritoriesDisjoint() {
        List<AgentFarmingCell> cells = lineCells();
        List<AgentFieldParticipant> participants = List.of(
                participant(10, 0, Set.of()),
                participant(20, 1_000, Set.of()));

        Map<Integer, AgentFieldAssignment> result = planner.plan(
                "session", AgentFieldMode.PARTY, cells, participants,
                List.of(), 1_000L, 10_000L, 1L);

        assertEquals(Set.of(10, 20), result.keySet());
        Set<String> all = new HashSet<>();
        for (AgentFieldAssignment assignment : result.values()) {
            assertFalse(assignment.cellIds().isEmpty());
            assertTrue(all.addAll(assignment.cellIds()), "party territories must not overlap");
        }
        assertEquals(Set.of("left", "middle", "right"), all);
    }

    @Test
    void activeLeaseMakesPreviousSeedStickyAcrossSmallDistanceChanges() {
        List<AgentFarmingCell> cells = lineCells();
        AgentFieldParticipant participant = new AgentFieldParticipant(
                10, -1, new Point(900, 0),
                AgentFieldIntent.freeGrind("free"), Set.of("left"),
                20_000L, 0L);

        AgentFieldAssignment assignment = planner.plan(
                "session", AgentFieldMode.PARTY, cells, List.of(participant),
                List.of(), 1_000L, 10_000L, 2L).get(10);

        assertTrue(assignment.reason().contains("retained"));
        assertEquals(0, assignment.anchor().x);
    }

    @Test
    void objectiveParticipantSeedsWhereRequiredSpeciesExists() {
        List<AgentFarmingCell> cells = lineCells();
        AgentFieldParticipant participant = new AgentFieldParticipant(
                10, -1, new Point(0, 0),
                AgentFieldIntent.partyCoverage("quest", Set.of(200), Map.of(200, 5)),
                Set.of(), 0L, 0L);

        AgentFieldAssignment assignment = planner.plan(
                "session", AgentFieldMode.PARTY, cells, List.of(participant),
                List.of(), 1_000L, 10_000L, 3L).get(10);

        assertEquals(1_000, assignment.anchor().x);
    }

    @Test
    void rangedHolderPrefersAPlatformWithUsableHorizontalSpan() {
        AgentFarmingCell densePocket = new AgentFarmingCell(
                "dense", 100, Set.of(1), Map.of(100, 6), Map.of(100, 6),
                List.of(new AgentFarmingAnchor("dense-a", new Point(0, 0), 100)),
                Set.of("wide"), 1, false, false);
        AgentFarmingCell widePlatform = new AgentFarmingCell(
                "wide", 100, Set.of(2), Map.of(100, 2), Map.of(100, 2),
                List.of(
                        new AgentFarmingAnchor("wide-a", new Point(100, 0), 100),
                        new AgentFarmingAnchor("wide-b", new Point(800, 0), 100)),
                Set.of("dense"), 1, false, false);
        AgentFieldParticipant ranged = new AgentFieldParticipant(
                10, -1, new Point(0, 0), AgentFieldIntent.freeGrind("free"),
                new AgentFieldCombatProfile(AgentFieldRole.RANGED_HOLDER, 100, 0, 0, 0),
                Set.of(), 0L, 0L);

        AgentFieldAssignment assignment = planner.plan(
                "session", AgentFieldMode.SOLO, List.of(densePocket, widePlatform),
                List.of(ranged), List.of(), 1_000L, 10_000L, 4L).get(10);

        assertEquals(100, assignment.anchor().x);
    }

    private static AgentFieldParticipant participant(int id, int x, Set<String> previous) {
        return new AgentFieldParticipant(id, -1, new Point(x, 0),
                AgentFieldIntent.freeGrind("free"), previous, 0L, 0L);
    }

    private static List<AgentFarmingCell> lineCells() {
        return List.of(
                cell("left", 1, 0, Map.of(100, 4), Set.of("middle")),
                cell("middle", 2, 500, Map.of(100, 3), Set.of("left", "right")),
                cell("right", 3, 1_000, Map.of(200, 4), Set.of("middle")));
    }

    private static AgentFarmingCell cell(
            String id, int regionId, int x, Map<Integer, Integer> mobs, Set<String> adjacent) {
        return new AgentFarmingCell(id, 100, Set.of(regionId), mobs, mobs,
                List.of(new AgentFarmingAnchor(id + "-anchor", new Point(x, 0), 100)),
                adjacent, 1, adjacent.size() <= 1, false);
    }
}
