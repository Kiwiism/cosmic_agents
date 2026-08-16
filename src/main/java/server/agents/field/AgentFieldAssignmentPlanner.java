package server.agents.field;

import server.agents.model.AgentPosition;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure, deterministic party-size allocator with sticky seeds and contiguous-cell preference. */
public final class AgentFieldAssignmentPlanner {
    public Map<Integer, AgentFieldAssignment> plan(
            String sessionId,
            AgentFieldMode mode,
            List<AgentFarmingCell> cells,
            List<AgentFieldParticipant> participants,
            List<AgentPosition> realPlayers,
            long nowMs,
            long leaseMs,
            long revision) {
        if (sessionId == null || sessionId.isBlank() || mode == null || cells == null
                || participants == null || realPlayers == null || nowMs < 0 || leaseMs <= 0 || revision < 0) {
            throw new IllegalArgumentException("Valid field planning scope, geometry, participants, and lease are required");
        }
        if (cells.isEmpty() || participants.isEmpty()) {
            return Map.of();
        }
        List<AgentFarmingCell> usable = cells.stream()
                .filter(cell -> !cell.transitOnly())
                .toList();
        if (usable.isEmpty()) {
            return Map.of();
        }
        List<AgentFieldParticipant> ordered = participants.stream()
                .sorted(Comparator
                        .comparingInt((AgentFieldParticipant participant) -> intentPriority(participant.intent()))
                        .thenComparingLong(AgentFieldParticipant::joinedAtMs)
                        .thenComparingInt(AgentFieldParticipant::agentId))
                .toList();
        if (mode == AgentFieldMode.SOLO) {
            return soloAssignments(sessionId, usable, ordered, realPlayers, nowMs, leaseMs, revision);
        }
        return partyAssignments(sessionId, usable, ordered, realPlayers, nowMs, leaseMs, revision);
    }

    private Map<Integer, AgentFieldAssignment> soloAssignments(
            String sessionId,
            List<AgentFarmingCell> cells,
            List<AgentFieldParticipant> participants,
            List<AgentPosition> realPlayers,
            long nowMs,
            long leaseMs,
            long revision) {
        Map<Integer, AgentFieldAssignment> assignments = new LinkedHashMap<>();
        for (int slot = 0; slot < participants.size(); slot++) {
            AgentFieldParticipant participant = participants.get(slot);
            List<AgentFarmingCell> relevant = relevantCells(cells, participant.intent());
            if (relevant.isEmpty()) {
                relevant = cells;
            }
            AgentFarmingCell seed = bestCell(participant, relevant, Set.of(), realPlayers, nowMs, Map.of());
            assignments.put(participant.agentId(), assignment(
                    sessionId, participant, slot, relevant, seed,
                    nowMs + leaseMs, revision, "solo map coverage"));
        }
        return Map.copyOf(assignments);
    }

    private Map<Integer, AgentFieldAssignment> partyAssignments(
            String sessionId,
            List<AgentFarmingCell> cells,
            List<AgentFieldParticipant> participants,
            List<AgentPosition> realPlayers,
            long nowMs,
            long leaseMs,
            long revision) {
        Map<Integer, AgentFarmingCell> seeds = new LinkedHashMap<>();
        Map<String, Integer> seedUse = new HashMap<>();
        Set<String> claimedSeeds = new LinkedHashSet<>();
        for (AgentFieldParticipant participant : participants) {
            List<AgentFarmingCell> relevant = relevantCells(cells, participant.intent());
            if (relevant.isEmpty()) {
                relevant = cells;
            }
            AgentFarmingCell seed = bestCell(
                    participant, relevant, claimedSeeds, realPlayers, nowMs, seedUse);
            if (seed == null) {
                continue;
            }
            seeds.put(participant.agentId(), seed);
            seedUse.merge(seed.cellId(), 1, Integer::sum);
            if (seedUse.get(seed.cellId()) >= seed.capacity()) {
                claimedSeeds.add(seed.cellId());
            }
        }
        if (seeds.isEmpty()) {
            return Map.of();
        }

        Map<Integer, LinkedHashSet<AgentFarmingCell>> territories = new LinkedHashMap<>();
        seeds.forEach((agentId, seed) -> territories
                .computeIfAbsent(agentId, ignored -> new LinkedHashSet<>()).add(seed));
        Set<String> assignedCellIds = new LinkedHashSet<>();
        seeds.values().forEach(seed -> assignedCellIds.add(seed.cellId()));
        List<AgentFarmingCell> remaining = cells.stream()
                .filter(cell -> !assignedCellIds.contains(cell.cellId()))
                .sorted(Comparator.comparing(AgentFarmingCell::cellId))
                .toList();
        Map<Integer, AgentFieldParticipant> participantById = participants.stream()
                .collect(java.util.stream.Collectors.toMap(
                        AgentFieldParticipant::agentId, participant -> participant));
        for (AgentFarmingCell cell : remaining) {
            int owner = seeds.keySet().stream()
                    .max(Comparator
                            .comparingLong((Integer agentId) -> territoryScore(
                                    participantById.get(agentId), cell,
                                    territories.get(agentId), realPlayers, nowMs))
                            .thenComparingInt(agentId -> -agentId))
                    .orElse(seeds.keySet().iterator().next());
            territories.computeIfAbsent(owner, ignored -> new LinkedHashSet<>()).add(cell);
        }

        Map<Integer, AgentFieldAssignment> assignments = new LinkedHashMap<>();
        for (int slot = 0; slot < participants.size(); slot++) {
            AgentFieldParticipant participant = participants.get(slot);
            AgentFarmingCell seed = seeds.get(participant.agentId());
            Set<AgentFarmingCell> territory = territories.get(participant.agentId());
            if (seed == null || territory == null || territory.isEmpty()) {
                continue;
            }
            boolean retained = participant.previousLeaseExpiresAtMs() > nowMs
                    && participant.previousCellIds().contains(seed.cellId());
            assignments.put(participant.agentId(), assignment(
                    sessionId, participant, slot, List.copyOf(territory), seed,
                    nowMs + leaseMs, revision,
                    retained ? "retained leased territory" : "party-size coverage rebalance"));
        }
        return Map.copyOf(assignments);
    }

    private static List<AgentFarmingCell> relevantCells(
            List<AgentFarmingCell> cells, AgentFieldIntent intent) {
        return cells.stream()
                .filter(cell -> cell.relevantPopulation(intent.requiredMobIds()) > 0)
                .toList();
    }

    private static AgentFarmingCell bestCell(
            AgentFieldParticipant participant,
            List<AgentFarmingCell> cells,
            Set<String> unavailable,
            List<AgentPosition> realPlayers,
            long nowMs,
            Map<String, Integer> seedUse) {
        return cells.stream()
                .filter(cell -> !unavailable.contains(cell.cellId()))
                .max(Comparator
                        .comparingLong((AgentFarmingCell cell) -> cellScore(
                                participant, cell, realPlayers, nowMs,
                                seedUse.getOrDefault(cell.cellId(), 0)))
                        .thenComparing(AgentFarmingCell::cellId, Comparator.reverseOrder()))
                .orElseGet(() -> cells.stream()
                        .filter(cell -> seedUse.getOrDefault(cell.cellId(), 0) < cell.capacity())
                        .max(Comparator.comparingLong(cell -> cellScore(
                                participant, cell, realPlayers, nowMs,
                                seedUse.getOrDefault(cell.cellId(), 0))))
                        .orElse(null));
    }

    private static long territoryScore(
            AgentFieldParticipant participant,
            AgentFarmingCell cell,
            Set<AgentFarmingCell> territory,
            List<AgentPosition> realPlayers,
            long nowMs) {
        boolean adjacent = territory.stream().anyMatch(owned ->
                owned.adjacentCellIds().contains(cell.cellId())
                        || cell.adjacentCellIds().contains(owned.cellId()));
        return cellScore(participant, cell, realPlayers, nowMs, 0)
                + (adjacent ? AgentFieldPolicyConfig.adjacencyBonus() : 0L)
                - (long) territory.size() * AgentFieldPolicyConfig.territorySizePenalty();
    }

    private static long cellScore(
            AgentFieldParticipant participant,
            AgentFarmingCell cell,
            List<AgentPosition> realPlayers,
            long nowMs,
            int currentUsers) {
        int population = cell.relevantPopulation(participant.intent().requiredMobIds());
        int coverage = cell.objectiveCoverage(participant.intent().requiredMobIds());
        Point anchor = cell.anchors().getFirst().position();
        long distancePenalty = Math.round(Math.sqrt(participant.position().distanceSq(anchor)) * 10.0d);
        long score = population * AgentFieldPolicyConfig.objectivePopulationWeight()
                + coverage * AgentFieldPolicyConfig.objectiveCoverageWeight()
                - distancePenalty
                - (long) currentUsers * AgentFieldPolicyConfig.territorySizePenalty();
        score += capabilityScore(participant.combatProfile(), cell, population);
        if (participant.previousLeaseExpiresAtMs() > nowMs
                && participant.previousCellIds().contains(cell.cellId())) {
            score += AgentFieldPolicyConfig.retainedSeedBonus();
        }
        for (AgentPosition player : realPlayers) {
            if (anchor.distanceSq(new Point(player.x(), player.y())) <= 500L * 500L) {
                score -= AgentFieldPolicyConfig.playerProximityPenalty();
            }
        }
        if (cell.deadEnd() && population <= 0) {
            score -= AgentFieldPolicyConfig.retainedSeedBonus();
        }
        return score;
    }

    private static long capabilityScore(
            AgentFieldCombatProfile profile,
            AgentFarmingCell cell,
            int population) {
        if (profile == null) {
            return 0L;
        }
        int anchorSpan = cell.anchors().stream().mapToInt(anchor -> anchor.position().x)
                .max().orElse(0) - cell.anchors().stream().mapToInt(anchor -> anchor.position().x)
                .min().orElse(0);
        return (long) population * profile.densityPreference()
                * AgentFieldPolicyConfig.capabilityDensityWeight()
                + (long) Math.max(0, anchorSpan) * profile.rangePreference()
                * AgentFieldPolicyConfig.capabilityRangeWeight()
                + (long) cell.adjacentCellIds().size() * profile.mobilityPreference()
                * AgentFieldPolicyConfig.capabilityMobilityWeight()
                + (long) cell.adjacentCellIds().size() * profile.supportPreference()
                * AgentFieldPolicyConfig.capabilitySupportWeight()
                - (cell.deadEnd() && profile.role() == AgentFieldRole.ROAMER
                ? AgentFieldPolicyConfig.roamerDeadEndPenalty() : 0L)
                - (population > 0 && profile.role() == AgentFieldRole.RESERVE
                ? AgentFieldPolicyConfig.reservePopulationPenalty() : 0L);
    }

    private static AgentFieldAssignment assignment(
            String sessionId,
            AgentFieldParticipant participant,
            int slot,
            List<AgentFarmingCell> cells,
            AgentFarmingCell seed,
            long expiresAtMs,
            long revision,
            String reason) {
        Set<String> cellIds = cells.stream().map(AgentFarmingCell::cellId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<Integer> regionIds = cells.stream().flatMap(cell -> cell.regionIds().stream())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return new AgentFieldAssignment(
                sessionId + ':' + revision + ':' + participant.agentId(),
                seed.mapId(), participant.agentId(), slot,
                cellIds, regionIds, seed.anchors().getFirst().position(),
                expiresAtMs, revision, reason);
    }

    private static int intentPriority(AgentFieldIntent intent) {
        return switch (intent.type()) {
            case QUEST_VISITOR -> 0;
            case PARTY_COVERAGE -> 1;
            case SUPPORT -> 2;
            case FREE_GRIND, ANCHOR -> 3;
            case TRANSIT -> 4;
        };
    }
}
