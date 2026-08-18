package server.agents.field;

import org.junit.jupiter.api.Test;
import server.agents.model.AgentPosition;

import java.awt.Point;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentFieldAssignmentPlannerTest {
    private final AgentFieldAssignmentPlanner planner = new AgentFieldAssignmentPlanner();

    @Test
    void partyModeLeasesOneDistinctPlatformPerAgent() {
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
            assertEquals(1, assignment.cellIds().size());
            assertTrue(all.addAll(assignment.cellIds()), "party territories must not overlap");
        }
        assertEquals(2, all.size());
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
    void partyCoversEveryPlatformBeforeSharingAHighCapacityPlatform() {
        List<AgentFarmingCell> cells = List.of(
                cellWithCapacity("bottom", 1, 0, 3),
                cellWithCapacity("middle", 2, 500, 3),
                cellWithCapacity("top", 3, 1_000, 3));
        List<AgentFieldParticipant> participants = List.of(
                participant(10, 0, Set.of()),
                participant(20, 0, Set.of()),
                participant(30, 0, Set.of()));

        Map<Integer, AgentFieldAssignment> result = planner.plan(
                "session", AgentFieldMode.PARTY, cells, participants,
                List.of(), 1_000L, 10_000L, 5L);

        assertEquals(Set.of(0, 500, 1_000), result.values().stream()
                .map(assignment -> assignment.anchor().x)
                .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void releasedEmptyLeaseMovesToDenseUnoccupiedPlatformWithoutDisplacingLockedAgent() {
        List<AgentFarmingCell> cells = List.of(
                cell("left", 1, 0, Map.of(100, 3), Set.of("middle")),
                cell("middle", 2, 500, Map.of(), Set.of("left", "right")),
                cell("right", 3, 1_000, Map.of(100, 6), Set.of("middle")));
        AgentFieldParticipant locked = new AgentFieldParticipant(
                10, -1, new Point(0, 0), AgentFieldIntent.freeGrind("free"),
                Set.of("left"), 20_000L, 0L);
        AgentFieldParticipant released = new AgentFieldParticipant(
                20, -1, new Point(500, 0), AgentFieldIntent.freeGrind("free"),
                Set.of("middle"), 0L, 0L);

        Map<Integer, AgentFieldAssignment> result = planner.plan(
                "session", AgentFieldMode.PARTY, cells, List.of(locked, released),
                List.of(), 10_000L, 15_000L, 6L);

        assertEquals(Set.of("left"), result.get(10).cellIds());
        assertEquals(Set.of("right"), result.get(20).cellIds());
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

    @Test
    void sharedPlatformAgentsReceiveDistinctStationTerritories() {
        AgentFarmingCell platform = new AgentFarmingCell(
                "wide", 100, Set.of(1), Map.of(100, 8), Map.of(100, 8),
                List.of(
                        new AgentFarmingAnchor("left", new Point(200, 0), 100, 0, 499),
                        new AgentFarmingAnchor("right", new Point(800, 0), 100, 500, 1_000)),
                Set.of(), 2, false, false);
        Map<Integer, AgentFieldAssignment> assignments = planner.plan(
                "session", AgentFieldMode.PARTY, List.of(platform),
                List.of(participant(10, 100, Set.of()), participant(20, 900, Set.of())),
                List.of(), 1_000L, 10_000L, 7L);

        assertEquals(2, assignments.values().stream()
                .map(AgentFieldAssignment::stationId).distinct().count());
        assertEquals(Set.of(200, 800), assignments.values().stream()
                .map(assignment -> assignment.anchor().x)
                .collect(java.util.stream.Collectors.toSet()));
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

    private static AgentFarmingCell cellWithCapacity(String id, int regionId, int x, int capacity) {
        return new AgentFarmingCell(id, 100, Set.of(regionId), Map.of(100, 4), Map.of(100, 4),
                List.of(new AgentFarmingAnchor(id + "-anchor", new Point(x, 0), 100)),
                Set.of(), capacity, false, false);
    }
}
