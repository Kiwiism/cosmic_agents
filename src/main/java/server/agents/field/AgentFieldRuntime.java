package server.agents.field;

import client.Character;
import server.agents.capabilities.combat.AgentCombatDirective;
import server.agents.capabilities.combat.AgentCombatDirectiveRuntime;
import server.agents.catalog.AgentMapRegionAssignment;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.cosmic.CosmicAgentPerceptionSnapshotFactory;
import server.agents.model.AgentPosition;
import server.agents.operations.events.AgentMobKilledEvent;
import server.agents.operations.events.AgentOperationalEventProjectionState;
import server.agents.perception.AgentPerceptionSnapshot;
import server.agents.runtime.AgentMailboxRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Map-instance coordinator. It assigns territory but never executes combat or navigation. */
public final class AgentFieldRuntime {
    private static final Map<FieldKey, FieldSession> sessions = new ConcurrentHashMap<>();
    private static final AgentFieldAssignmentPlanner planner = new AgentFieldAssignmentPlanner();
    private static final AgentFarmingCellCatalog cellCatalog =
            AgentNavigationFarmingCellCatalog.INSTANCE;

    private AgentFieldRuntime() {
    }

    public record StartResult(boolean success, String message, String sessionId) {
    }

    public static StartResult start(
            Character operator,
            List<AgentRuntimeEntry> requestedEntries,
            AgentFieldMode mode,
            Set<Integer> objectiveMobIds,
            int killsPerMob,
            boolean acceptingQuestVisitors,
            long nowMs) {
        if (operator == null || operator.getMap() == null || requestedEntries == null
                || requestedEntries.isEmpty() || mode == null) {
            return new StartResult(false, "Operator, map, participants, and mode are required.", "");
        }
        int maximum = AgentFieldPolicyConfig.maximumParticipants();
        if (requestedEntries.size() > maximum) {
            return new StartResult(false, "Field exercises support at most " + maximum + " Agents.", "");
        }
        List<AgentRuntimeEntry> entries = requestedEntries.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .filter(entry -> {
                    Character agent = AgentRuntimeIdentityRuntime.bot(entry);
                    return agent != null && agent.getMap() == operator.getMap();
                })
                .toList();
        if (entries.size() != requestedEntries.size()) {
            return new StartResult(false, "Every selected Agent must already be in the operator's map instance.", "");
        }
        FieldKey key = FieldKey.of(operator);
        stop(key, nowMs);
        String sessionId = "field-" + operator.getId() + '-'
                + UUID.randomUUID().toString().substring(0, 8);
        Set<Integer> normalizedMobIds = objectiveMobIds == null ? Set.of() : Set.copyOf(objectiveMobIds);
        Map<Integer, Integer> requiredKills = new LinkedHashMap<>();
        int normalizedKills = Math.max(1, killsPerMob);
        normalizedMobIds.stream().sorted().forEach(mobId -> requiredKills.put(mobId, normalizedKills));
        FieldSession session = new FieldSession(
                sessionId, key, mode, acceptingQuestVisitors,
                requiredKills, nowMs);
        for (AgentRuntimeEntry entry : entries) {
            AgentCombatDirective baseline = AgentCombatDirectiveRuntime.directive(entry);
            AgentFieldIntent intent = normalizedMobIds.isEmpty()
                    ? AgentFieldIntent.freeGrind(sessionId)
                    : AgentFieldIntent.partyCoverage(sessionId, normalizedMobIds, requiredKills);
            Character agent = AgentRuntimeIdentityRuntime.bot(entry);
            session.participants.put(agent.getId(), new ParticipantBinding(
                    agent.getId(), intent, baseline, true, nowMs));
            if (!normalizedMobIds.isEmpty()) {
                AgentCombatDirectiveRuntime.assignPreferences(entry, normalizedMobIds, Set.of());
            }
        }
        sessions.put(key, session);
        refresh(entries.getFirst(), AgentRuntimeIdentityRuntime.bot(entries.getFirst()), nowMs);
        return new StartResult(true,
                "Started " + mode.name().toLowerCase() + " field exercise with "
                        + entries.size() + " Agent(s).", sessionId);
    }

    public static boolean add(
            Character operator, AgentRuntimeEntry entry, AgentFieldIntent intent, long nowMs) {
        if (operator == null || entry == null || intent == null) {
            return false;
        }
        FieldSession session = sessions.get(FieldKey.of(operator));
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (session == null || agent == null || agent.getMap() != operator.getMap()) {
            return false;
        }
        synchronized (session) {
            if (session.participants.size() >= AgentFieldPolicyConfig.maximumParticipants()) {
                return false;
            }
            if (session.participants.containsKey(agent.getId())) {
                return false;
            }
            session.participants.put(agent.getId(), new ParticipantBinding(
                    agent.getId(), intent, AgentCombatDirectiveRuntime.directive(entry), true, nowMs));
            if (!intent.requiredMobIds().isEmpty()) {
                AgentCombatDirectiveRuntime.assignPreferences(entry, intent.requiredMobIds(), Set.of());
            }
            session.structureFingerprint = 0L;
        }
        refresh(entry, agent, nowMs);
        return true;
    }

    public static boolean remove(Character operator, int agentId, long nowMs) {
        if (operator == null) {
            return false;
        }
        FieldSession session = sessions.get(FieldKey.of(operator));
        if (session == null) {
            return false;
        }
        ParticipantBinding removed;
        synchronized (session) {
            removed = session.participants.remove(agentId);
            session.assignments.remove(agentId);
            session.structureFingerprint = 0L;
            session.lastActivityAtMs = nowMs;
        }
        if (removed != null) {
            release(removed);
        }
        return removed != null;
    }

    public static boolean stop(Character operator, long nowMs) {
        return operator != null && stop(FieldKey.of(operator), nowMs);
    }

    private static boolean stop(FieldKey key, long nowMs) {
        FieldSession removed = sessions.remove(key);
        if (removed == null) {
            return false;
        }
        List<ParticipantBinding> bindings;
        synchronized (removed) {
            bindings = List.copyOf(removed.participants.values());
            removed.participants.clear();
            removed.assignments.clear();
            removed.lastActivityAtMs = nowMs;
        }
        bindings.forEach(AgentFieldRuntime::release);
        return true;
    }

    /** Called from the live mode orchestrator before grind target acquisition. */
    public static void refresh(AgentRuntimeEntry callerEntry, Character caller, long nowMs) {
        if (callerEntry == null || caller == null || caller.getMap() == null) {
            return;
        }
        pruneStale(nowMs);
        FieldSession session = sessions.get(FieldKey.of(caller));
        if (session == null) {
            return;
        }
        synchronized (session) {
            session.lastActivityAtMs = nowMs;
            autoEnrollVisitor(session, callerEntry, caller, nowMs);
            pruneAbsentParticipants(session);
            boolean assignmentMissing = session.assignments.size() < session.participants.size();
            boolean leaseExpired = session.assignments.values().stream()
                    .anyMatch(assignment -> assignment.expiresAtMs() <= nowMs);
            if (!assignmentMissing && !leaseExpired
                    && nowMs - session.lastObservedAtMs < AgentFieldPolicyConfig.refreshIntervalMs()) {
                return;
            }
            session.lastObservedAtMs = nowMs;
            List<AgentFarmingCell> observedCells = cellCatalog.cells(callerEntry, caller);
            if (!observedCells.isEmpty()) {
                session.cells.clear();
                observedCells.forEach(cell -> session.cells.put(cell.cellId(), cell));
            }
            AgentPerceptionSnapshot perception =
                    CosmicAgentPerceptionSnapshotFactory.capture(caller, nowMs);
            session.realPlayers = perception.realPlayerObservers();
            session.liveMobs = (int) perception.mobs().stream().filter(mob -> mob.alive()).count();
            if (session.cells.isEmpty() || session.participants.isEmpty()) {
                return;
            }
            long fingerprint = structureFingerprint(session, perception);
            if (fingerprint == session.structureFingerprint && !leaseExpired) {
                return;
            }
            List<AgentFieldParticipant> participants = plannerParticipants(session);
            List<AgentPosition> realPlayerPositions = perception.characters().stream()
                    .filter(character -> !character.agent())
                    .map(character -> character.position())
                    .toList();
            long nextRevision = session.revision + 1L;
            Map<Integer, AgentFieldAssignment> planned = planner.plan(
                    session.sessionId, session.mode, List.copyOf(session.cells.values()),
                    participants, realPlayerPositions, nowMs,
                    AgentFieldPolicyConfig.assignmentLeaseMs(), nextRevision);
            session.assignments.clear();
            session.assignments.putAll(planned);
            session.revision = nextRevision;
            session.structureFingerprint = fingerprint;
            for (Map.Entry<Integer, AgentFieldAssignment> assignment : planned.entrySet()) {
                ParticipantBinding binding = session.participants.get(assignment.getKey());
                if (binding != null) {
                    apply(session, binding, assignment.getValue(), nowMs);
                }
            }
        }
    }

    public static void recordKill(AgentRuntimeEntry entry, AgentMobKilledEvent event) {
        if (entry == null || event == null) {
            return;
        }
        AgentFieldAssignmentState.Snapshot assignmentState = entry.capabilityStates()
                .find(AgentFieldAssignmentState.STATE_KEY)
                .map(AgentFieldAssignmentState::snapshot)
                .orElse(null);
        if (assignmentState == null || assignmentState.sessionId().isBlank()) {
            return;
        }
        FieldSession session = sessions.values().stream()
                .filter(candidate -> candidate.sessionId.equals(assignmentState.sessionId()))
                .findFirst().orElse(null);
        if (session == null || session.key.mapId != event.mapId()) {
            return;
        }
        synchronized (session) {
            Integer required = session.requiredKills.get(event.mobId());
            if (required == null || session.objectiveComplete) {
                return;
            }
            session.completedKills.merge(event.mobId(), 1,
                    (left, right) -> Math.min(required, left + right));
            session.objectiveComplete = session.requiredKills.entrySet().stream()
                    .allMatch(requirement -> session.completedKills
                            .getOrDefault(requirement.getKey(), 0) >= requirement.getValue());
            session.lastActivityAtMs = event.occurredAtMs();
            Set<Integer> remainingMobIds = session.requiredKills.entrySet().stream()
                    .filter(requirement -> session.completedKills
                            .getOrDefault(requirement.getKey(), 0) < requirement.getValue())
                    .map(Map.Entry::getKey)
                    .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
            if (session.objectiveComplete) {
                for (ParticipantBinding binding : session.participants.values()) {
                    if (!binding.explicit || binding.intent.type() == AgentFieldIntent.Type.QUEST_VISITOR) {
                        continue;
                    }
                    binding.intent = AgentFieldIntent.freeGrind(session.sessionId + ":complete");
                    AgentRuntimeEntry participant = AgentRuntimeRegistry.findByAgentCharacterId(binding.agentId);
                    if (participant != null) {
                        AgentMailboxRuntime.dispatch(participant, ignored -> {
                            AgentCombatDirectiveRuntime.assignAllowed(participant, Set.of());
                            return null;
                        });
                    }
                }
                session.structureFingerprint = 0L;
            } else {
                for (ParticipantBinding binding : session.participants.values()) {
                    if (!binding.explicit || binding.intent.type() == AgentFieldIntent.Type.QUEST_VISITOR) {
                        continue;
                    }
                    binding.intent = AgentFieldIntent.partyCoverage(
                            session.sessionId, remainingMobIds, remainingRequirements(session));
                    AgentRuntimeEntry participant = AgentRuntimeRegistry.findByAgentCharacterId(binding.agentId);
                    if (participant != null) {
                        AgentMailboxRuntime.dispatch(participant, ignored -> {
                            AgentCombatDirectiveRuntime.assignPreferences(participant, remainingMobIds, Set.of());
                            return null;
                        });
                    }
                }
                session.structureFingerprint = 0L;
            }
        }
    }

    public static AgentFieldSnapshot snapshot(Character observer, long nowMs) {
        if (observer == null) {
            return null;
        }
        FieldSession session = sessions.get(FieldKey.of(observer));
        return session == null ? null : snapshot(session, nowMs);
    }

    public static List<AgentFieldSnapshot> snapshotsForMapId(int mapId, long nowMs) {
        return sessions.values().stream()
                .filter(session -> session.key.mapId == mapId)
                .sorted(Comparator.comparing(session -> session.sessionId))
                .map(session -> snapshot(session, nowMs))
                .toList();
    }

    private static AgentFieldSnapshot snapshot(FieldSession session, long nowMs) {
        synchronized (session) {
            List<AgentFieldSnapshot.Cell> cells = session.cells.values().stream()
                    .sorted(Comparator.comparing(AgentFarmingCell::cellId))
                    .map(cell -> new AgentFieldSnapshot.Cell(
                            cell.cellId(), cell.regionIds().stream().sorted().toList(),
                            cell.mobCounts(), cell.expectedMobCounts(), cell.capacity(), cell.deadEnd(),
                            cell.adjacentCellIds().stream().sorted().toList()))
                    .toList();
            List<AgentFieldSnapshot.Participant> participants = session.participants.values().stream()
                    .sorted(Comparator.comparingInt(binding -> binding.agentId))
                    .map(binding -> participantSnapshot(
                            binding, session.assignments.get(binding.agentId), nowMs))
                    .toList();
            return new AgentFieldSnapshot(
                    session.sessionId, session.key.mapId, session.mode, session.revision,
                    nowMs, session.realPlayers, session.liveMobs,
                    session.acceptingQuestVisitors, session.objectiveComplete,
                    session.requiredKills, session.completedKills, cells, participants);
        }
    }

    private static AgentFieldSnapshot.Participant participantSnapshot(
            ParticipantBinding binding, AgentFieldAssignment assignment, long nowMs) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(binding.agentId);
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        AgentOperationalEventProjectionState.Snapshot operations = entry == null
                ? new AgentOperationalEventProjectionState().snapshot()
                : entry.capabilityStates().require(AgentOperationalEventProjectionState.STATE_KEY).snapshot();
        java.awt.Point position = agent == null ? null : agent.getPosition();
        return new AgentFieldSnapshot.Participant(
                binding.agentId,
                agent == null ? "#" + binding.agentId : agent.getName(),
                agent == null ? -1 : agent.getPartyId(),
                agent == null ? -1 : agent.getJob().getId(),
                agent == null ? 0 : agent.getLevel(),
                agent == null ? 0 : agent.getExp(),
                position == null ? 0 : position.x,
                position == null ? 0 : position.y,
                operations.kills(),
                operations.targetTransitions(),
                operations.routeFailures(),
                operations.stuckDetections(),
                operations.recoveries(),
                operations.lifeTransitions(),
                binding.intent.type(),
                assignment == null ? List.of() : assignment.cellIds().stream().sorted().toList(),
                assignment == null ? List.of() : assignment.regionIds().stream().sorted().toList(),
                assignment == null ? 0 : assignment.anchor().x,
                assignment == null ? 0 : assignment.anchor().y,
                assignment == null ? 0L : Math.max(0L, assignment.expiresAtMs() - nowMs),
                assignment == null ? "awaiting navigation graph" : assignment.reason());
    }

    private static void autoEnrollVisitor(
            FieldSession session, AgentRuntimeEntry entry, Character agent, long nowMs) {
        if (!session.acceptingQuestVisitors || session.participants.containsKey(agent.getId())
                || session.participants.size() >= AgentFieldPolicyConfig.maximumParticipants()) {
            return;
        }
        AgentCombatDirective directive = AgentCombatDirectiveRuntime.directive(entry);
        if (directive == null || directive.requiredMobIds().isEmpty()) {
            return;
        }
        AgentCombatDirective baseline = withoutRegion(directive);
        session.participants.put(agent.getId(), new ParticipantBinding(
                agent.getId(), AgentFieldIntent.questVisitor(
                        directive.objectiveId(), directive.requiredMobIds()),
                baseline, false, nowMs));
        session.structureFingerprint = 0L;
    }

    private static void pruneAbsentParticipants(FieldSession session) {
        List<Integer> absent = new ArrayList<>();
        for (ParticipantBinding binding : session.participants.values()) {
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(binding.agentId);
            Character agent = AgentRuntimeIdentityRuntime.bot(entry);
            if (agent == null || agent.getMap() == null || !FieldKey.of(agent).equals(session.key)) {
                absent.add(binding.agentId);
                continue;
            }
            if (!binding.explicit) {
                AgentCombatDirective directive = AgentCombatDirectiveRuntime.directive(entry);
                if (directive == null || directive.requiredMobIds().isEmpty()) {
                    absent.add(binding.agentId);
                } else if (!directive.requiredMobIds().equals(binding.intent.requiredMobIds())
                        || !directive.objectiveId().equals(binding.intent.objectiveId())) {
                    binding.baselineDirective = withoutRegion(directive);
                    binding.intent = AgentFieldIntent.questVisitor(
                            directive.objectiveId(), directive.requiredMobIds());
                    session.structureFingerprint = 0L;
                }
            }
        }
        for (Integer agentId : absent) {
            ParticipantBinding removed = session.participants.remove(agentId);
            session.assignments.remove(agentId);
            if (removed != null) {
                release(removed);
            }
        }
        if (!absent.isEmpty()) {
            session.structureFingerprint = 0L;
        }
    }

    private static List<AgentFieldParticipant> plannerParticipants(FieldSession session) {
        List<AgentFieldParticipant> participants = new ArrayList<>();
        for (ParticipantBinding binding : session.participants.values()) {
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(binding.agentId);
            Character agent = AgentRuntimeIdentityRuntime.bot(entry);
            if (agent == null || agent.getPosition() == null) {
                continue;
            }
            AgentFieldAssignment previous = session.assignments.get(binding.agentId);
            participants.add(new AgentFieldParticipant(
                    binding.agentId, agent.getPartyId(), agent.getPosition(), binding.intent,
                    previous == null ? Set.of() : previous.cellIds(),
                    previous == null ? 0L : previous.expiresAtMs(), binding.joinedAtMs));
        }
        return List.copyOf(participants);
    }

    private static void apply(
            FieldSession session,
            ParticipantBinding binding,
            AgentFieldAssignment assignment,
            long nowMs) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(binding.agentId);
        if (entry == null) {
            return;
        }
        AgentMapRegionAssignment region = new AgentMapRegionAssignment(
                assignment.assignmentId(), assignment.mapId(),
                assignment.regionIds().stream().sorted().map(String::valueOf).toList(),
                assignment.partySlot(), Math.max(1, assignment.cellIds().size()),
                assignment.expiresAtMs());
        AgentMailboxRuntime.dispatch(entry, ignored -> {
            AgentCombatDirectiveRuntime.assignRegion(entry, region);
            entry.capabilityStates().require(AgentFieldAssignmentState.STATE_KEY)
                    .update(session.sessionId, binding.intent, assignment, nowMs);
            return null;
        });
    }

    private static void release(ParticipantBinding binding) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(binding.agentId);
        if (entry == null) {
            return;
        }
        AgentMailboxRuntime.dispatch(entry, ignored -> {
            if (binding.explicit) {
                AgentCombatDirectiveRuntime.assignExact(entry, binding.baselineDirective);
            } else {
                AgentCombatDirectiveRuntime.assignExact(
                        entry, withoutRegion(AgentCombatDirectiveRuntime.directive(entry)));
            }
            entry.capabilityStates().remove(AgentFieldAssignmentState.STATE_KEY)
                    .ifPresent(AgentFieldAssignmentState::clear);
            return null;
        });
    }

    private static AgentCombatDirective withoutRegion(AgentCombatDirective directive) {
        return directive == null ? null : new AgentCombatDirective(
                directive.directiveId(), directive.objectiveId(), directive.requiredMobIds(),
                directive.requiredKills(), directive.incidentalPolicy(), null,
                directive.deadlineMs());
    }

    private static long structureFingerprint(
            FieldSession session, AgentPerceptionSnapshot perception) {
        long result = 17L;
        for (ParticipantBinding binding : session.participants.values().stream()
                .sorted(Comparator.comparingInt(value -> value.agentId)).toList()) {
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(binding.agentId);
            Character agent = AgentRuntimeIdentityRuntime.bot(entry);
            result = result * 31L + binding.agentId;
            result = result * 31L + (agent == null ? -1 : agent.getPartyId());
            result = result * 31L + binding.intent.hashCode();
        }
        for (String cellId : session.cells.keySet().stream().sorted().toList()) {
            result = result * 31L + cellId.hashCode();
        }
        result = result * 31L + perception.realPlayerObservers();
        result = result * 31L + (session.objectiveComplete ? 1 : 0);
        return result;
    }

    private static Map<Integer, Integer> remainingRequirements(FieldSession session) {
        Map<Integer, Integer> remaining = new LinkedHashMap<>();
        session.requiredKills.forEach((mobId, required) -> {
            int count = Math.max(0, required - session.completedKills.getOrDefault(mobId, 0));
            if (count > 0) {
                remaining.put(mobId, count);
            }
        });
        return Map.copyOf(remaining);
    }

    private static void pruneStale(long nowMs) {
        long staleMs = AgentFieldPolicyConfig.staleSessionMs();
        for (Map.Entry<FieldKey, FieldSession> indexed : sessions.entrySet()) {
            FieldSession session = indexed.getValue();
            List<ParticipantBinding> bindings;
            synchronized (session) {
                if (nowMs - session.lastActivityAtMs < staleMs) {
                    continue;
                }
                bindings = List.copyOf(session.participants.values());
            }
            if (sessions.remove(indexed.getKey(), session)) {
                bindings.forEach(AgentFieldRuntime::release);
            }
        }
    }

    static void clearForTests() {
        sessions.clear();
    }

    private record FieldKey(int world, int channel, int mapId, int instanceToken) {
        static FieldKey of(Character character) {
            var clients = AgentClientGatewayRuntime.clients();
            if (character == null || character.getMap() == null || !clients.hasClient(character)) {
                return new FieldKey(-1, -1, -1, 0);
            }
            return new FieldKey(
                    clients.world(character), clients.channel(character), character.getMapId(),
                    System.identityHashCode(character.getMap()));
        }
    }

    private static final class FieldSession {
        private final String sessionId;
        private final FieldKey key;
        private final AgentFieldMode mode;
        private final boolean acceptingQuestVisitors;
        private final Map<Integer, Integer> requiredKills;
        private final Map<Integer, Integer> completedKills = new LinkedHashMap<>();
        private final Map<Integer, ParticipantBinding> participants = new LinkedHashMap<>();
        private final Map<String, AgentFarmingCell> cells = new LinkedHashMap<>();
        private final Map<Integer, AgentFieldAssignment> assignments = new LinkedHashMap<>();
        private long revision;
        private long structureFingerprint;
        private long lastObservedAtMs;
        private long lastActivityAtMs;
        private int realPlayers;
        private int liveMobs;
        private boolean objectiveComplete;

        private FieldSession(
                String sessionId,
                FieldKey key,
                AgentFieldMode mode,
                boolean acceptingQuestVisitors,
                Map<Integer, Integer> requiredKills,
                long nowMs) {
            this.sessionId = sessionId;
            this.key = key;
            this.mode = mode;
            this.acceptingQuestVisitors = acceptingQuestVisitors;
            this.requiredKills = Map.copyOf(requiredKills);
            this.lastActivityAtMs = nowMs;
        }
    }

    private static final class ParticipantBinding {
        private final int agentId;
        private AgentFieldIntent intent;
        private AgentCombatDirective baselineDirective;
        private final boolean explicit;
        private final long joinedAtMs;

        private ParticipantBinding(
                int agentId,
                AgentFieldIntent intent,
                AgentCombatDirective baselineDirective,
                boolean explicit,
                long joinedAtMs) {
            this.agentId = agentId;
            this.intent = intent;
            this.baselineDirective = baselineDirective;
            this.explicit = explicit;
            this.joinedAtMs = joinedAtMs;
        }
    }
}
