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

/** Pure, deterministic greedy capacitated auction with sticky platform leases. */
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
            AgentFarmingAnchor station = stationFor(participant, seed, Set.of(), true);
            assignments.put(participant.agentId(), assignment(
                    sessionId, participant, slot, relevant, seed, station,
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
        Map<Integer, AgentFarmingAnchor> stations = new LinkedHashMap<>();
        Map<String, Integer> seedUse = new HashMap<>();
        Set<String> usedStationIds = new LinkedHashSet<>();
        participants.stream()
                .filter(participant -> participant.previousLeaseExpiresAtMs() > nowMs)
                .sorted(Comparator.comparingInt(AgentFieldParticipant::agentId))
                .forEach(participant -> cells.stream()
                        .filter(cell -> participant.previousCellIds().contains(cell.cellId()))
                        .filter(cell -> seedUse.getOrDefault(cell.cellId(), 0) < cell.capacity())
                        .max(Comparator.comparingLong(cell -> cellScore(
                                participant, cell, realPlayers, nowMs,
                                seedUse.getOrDefault(cell.cellId(), 0))))
                        .ifPresent(cell -> reserve(participant, cell, seeds, stations,
                                seedUse, usedStationIds, false)));

        auction(participants, cells, seeds, stations, seedUse, usedStationIds,
                realPlayers, nowMs, true);
        auction(participants, cells, seeds, stations, seedUse, usedStationIds,
                realPlayers, nowMs, false);
        for (AgentFieldParticipant participant : participants) {
            if (seeds.containsKey(participant.agentId())) {
                continue;
            }
            AgentFarmingCell overflow = cells.stream()
                    .min(Comparator
                            .comparingInt((AgentFarmingCell cell) ->
                                    seedUse.getOrDefault(cell.cellId(), 0))
                            .thenComparing(Comparator.comparingLong(
                                    (AgentFarmingCell cell) -> cellScore(
                                            participant, cell, realPlayers, nowMs,
                                            seedUse.getOrDefault(cell.cellId(), 0))).reversed()))
                    .orElse(null);
            if (overflow != null) {
                reserve(participant, overflow, seeds, stations,
                        seedUse, usedStationIds, true);
            }
        }

        Map<Integer, AgentFieldAssignment> assignments = new LinkedHashMap<>();
        for (int slot = 0; slot < participants.size(); slot++) {
            AgentFieldParticipant participant = participants.get(slot);
            AgentFarmingCell seed = seeds.get(participant.agentId());
            AgentFarmingAnchor station = stations.get(participant.agentId());
            if (seed == null || station == null) {
                continue;
            }
            boolean retained = participant.previousLeaseExpiresAtMs() > nowMs
                    && participant.previousCellIds().contains(seed.cellId());
            assignments.put(participant.agentId(), assignment(
                    sessionId, participant, slot, List.of(seed), seed, station,
                    nowMs + leaseMs, revision,
                    retained ? "retained platform lease" : "density-and-distance platform auction"));
        }
        return Map.copyOf(assignments);
    }

    private static void auction(
            List<AgentFieldParticipant> participants,
            List<AgentFarmingCell> cells,
            Map<Integer, AgentFarmingCell> seeds,
            Map<Integer, AgentFarmingAnchor> stations,
            Map<String, Integer> seedUse,
            Set<String> usedStationIds,
            List<AgentPosition> realPlayers,
            long nowMs,
            boolean unusedOnly) {
        ArrayList<Bid> bids = new ArrayList<>();
        for (AgentFieldParticipant participant : participants) {
            if (seeds.containsKey(participant.agentId())) {
                continue;
            }
            List<AgentFarmingCell> relevant = relevantCells(cells, participant.intent());
            for (AgentFarmingCell cell : relevant.isEmpty() ? cells : relevant) {
                int users = seedUse.getOrDefault(cell.cellId(), 0);
                if ((unusedOnly && users > 0) || users >= cell.capacity()) {
                    continue;
                }
                bids.add(new Bid(participant, cell,
                        cellScore(participant, cell, realPlayers, nowMs, users)));
            }
        }
        bids.sort(Comparator.comparingLong(Bid::score).reversed()
                .thenComparingInt(bid -> bid.participant().agentId())
                .thenComparing(bid -> bid.cell().cellId()));
        for (Bid bid : bids) {
            if (seeds.containsKey(bid.participant().agentId())) {
                continue;
            }
            int users = seedUse.getOrDefault(bid.cell().cellId(), 0);
            if ((unusedOnly && users > 0) || users >= bid.cell().capacity()) {
                continue;
            }
            reserve(bid.participant(), bid.cell(), seeds, stations,
                    seedUse, usedStationIds, false);
        }
    }

    private static void reserve(
            AgentFieldParticipant participant,
            AgentFarmingCell cell,
            Map<Integer, AgentFarmingCell> seeds,
            Map<Integer, AgentFarmingAnchor> stations,
            Map<String, Integer> seedUse,
            Set<String> usedStationIds,
            boolean allowReuse) {
        AgentFarmingAnchor station = stationFor(participant, cell, usedStationIds, allowReuse);
        if (station == null) {
            return;
        }
        seeds.put(participant.agentId(), cell);
        stations.put(participant.agentId(), station);
        usedStationIds.add(station.anchorId());
        seedUse.merge(cell.cellId(), 1, Integer::sum);
    }

    private static AgentFarmingAnchor stationFor(
            AgentFieldParticipant participant,
            AgentFarmingCell cell,
            Set<String> usedStationIds,
            boolean allowReuse) {
        if (!participant.previousStationId().isBlank()) {
            AgentFarmingAnchor retained = cell.anchors().stream()
                    .filter(station -> station.anchorId().equals(participant.previousStationId()))
                    .filter(station -> allowReuse || !usedStationIds.contains(station.anchorId()))
                    .findFirst().orElse(null);
            if (retained != null) {
                return retained;
            }
        }
        return cell.anchors().stream()
                .filter(station -> allowReuse || !usedStationIds.contains(station.anchorId()))
                .min(Comparator
                        .comparingDouble((AgentFarmingAnchor station) ->
                                participant.position().distanceSq(station.position()))
                        .thenComparing(AgentFarmingAnchor::anchorId))
                .orElseGet(() -> allowReuse ? cell.anchors().getFirst() : null);
    }

    private record Bid(AgentFieldParticipant participant, AgentFarmingCell cell, long score) {
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

    private static long cellScore(
            AgentFieldParticipant participant,
            AgentFarmingCell cell,
            List<AgentPosition> realPlayers,
            long nowMs,
            int currentUsers) {
        int population = cell.relevantPopulation(participant.intent().requiredMobIds());
        int coverage = cell.objectiveCoverage(participant.intent().requiredMobIds());
        Point anchor = cell.centralAnchor().position();
        long distancePenalty = Math.round(Math.sqrt(participant.position().distanceSq(anchor)) * 10.0d);
        long score = population * AgentFieldPolicyConfig.objectivePopulationWeight()
                + coverage * AgentFieldPolicyConfig.objectiveCoverageWeight()
                - distancePenalty
                - (long) currentUsers * AgentFieldPolicyConfig.sharedPlatformPenalty();
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
            AgentFarmingAnchor station,
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
                cellIds, regionIds, station.anchorId(), station.position(),
                station.territoryMinX(), station.territoryMaxX(),
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
