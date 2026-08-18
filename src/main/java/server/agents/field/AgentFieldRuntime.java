package server.agents.field;

import client.Character;
import server.agents.capabilities.combat.AgentCombatDirective;
import server.agents.capabilities.combat.AgentCombatDirectiveRuntime;
import server.agents.capabilities.combat.AgentGrindTargetStateRuntime;
import server.agents.capabilities.looting.AgentGrindLootStateRuntime;
import server.agents.capabilities.looting.AgentLootCollectionContextRuntime;
import server.agents.catalog.AgentMapRegionAssignment;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.cosmic.CosmicAgentPerceptionSnapshotFactory;
import server.agents.model.AgentPosition;
import server.agents.events.AgentEventPriority;
import server.agents.field.events.AgentFieldAssignmentChangedEvent;
import server.agents.field.events.AgentFieldLifecycleEvent;
import server.agents.field.events.AgentFieldPopulationChangedEvent;
import server.agents.operations.events.AgentMobKilledEvent;
import server.agents.operations.events.AgentOperationalEventProjectionState;
import server.agents.perception.AgentPerceptionSnapshot;
import server.agents.runtime.AgentMailboxRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentSessionEventRuntime;
import server.agents.runtime.field.AgentFieldDisplacementRuntime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.awt.Point;

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

    public record AdmissionResult(
            boolean success, String message, String sessionId, int population) {
    }

    /** Managed single-Agent admission used by FieldActivity; existing group commands remain unchanged. */
    public static AdmissionResult admit(
            Character agent,
            AgentRuntimeEntry entry,
            AgentFieldIntent intent,
            boolean acceptingQuestVisitors,
            int maximum,
            long nowMs) {
        if (agent == null || entry == null || intent == null || agent.getMap() == null) {
            return new AdmissionResult(false, "Agent, map, and field intent are required.", "", 0);
        }
        int capacity = Math.max(1, Math.min(12, maximum));
        FieldSession session;
        boolean created = false;
        synchronized (sessions) {
            FieldKey key = FieldKey.of(agent);
            session = sessions.get(key);
            if (session == null) {
                session = new FieldSession("field-managed-" + agent.getId() + '-'
                        + UUID.randomUUID().toString().substring(0, 8), key,
                        AgentFieldMode.PARTY, acceptingQuestVisitors,
                        intent.requiredKills(), capacity, nowMs);
                sessions.put(key, session);
                created = true;
            }
        }
        int population;
        synchronized (session) {
            if (session.participants.containsKey(agent.getId())) {
                return new AdmissionResult(true, "Agent is already admitted.",
                        session.sessionId, session.participants.size());
            }
            if (session.participants.size() >= capacity) {
                return new AdmissionResult(false, "Field session is at capacity.",
                        session.sessionId, session.participants.size());
            }
            AgentCombatDirective baseline = AgentCombatDirectiveRuntime.directive(entry);
            session.participants.put(agent.getId(), new ParticipantBinding(
                    agent.getId(), intent, baseline, true, true, nowMs));
            if (!intent.requiredMobIds().isEmpty()) {
                AgentCombatDirectiveRuntime.assignPreferences(entry, intent.requiredMobIds(), Set.of());
            }
            session.structureFingerprint = 0L;
            session.lastActivityAtMs = nowMs;
            population = session.participants.size();
        }
        publishPopulation(session, agent.getId(), AgentFieldPopulationChangedEvent.Change.JOINED,
                population, created ? "created field session" : "joined existing field session", nowMs);
        publishRebalance(session, agent.getName() + " joined", nowMs);
        refresh(entry, agent, nowMs);
        return new AdmissionResult(true, created ? "Created and joined field session."
                : "Joined field session.", session.sessionId, population);
    }

    public static String sessionId(Character agent) {
        if (agent == null || agent.getMap() == null) {
            return "";
        }
        FieldSession session = sessions.get(FieldKey.of(agent));
        if (session == null) {
            return "";
        }
        synchronized (session) {
            return session.participants.containsKey(agent.getId()) ? session.sessionId : "";
        }
    }

    public static boolean hasSession(Character agent) {
        return agent != null && agent.getMap() != null && sessions.containsKey(FieldKey.of(agent));
    }

    /** True while an incumbent hunter has lent its station to a temporary quest visitor. */
    public static boolean isDisplaced(Character agent) {
        if (agent == null || agent.getMap() == null) {
            return false;
        }
        FieldSession session = sessions.get(FieldKey.of(agent));
        if (session == null) {
            return false;
        }
        synchronized (session) {
            ParticipantBinding binding = session.participants.get(agent.getId());
            return binding != null && binding.displaced;
        }
    }

    public static StartResult start(
            Character operator,
            List<AgentRuntimeEntry> requestedEntries,
            AgentFieldMode mode,
            Set<Integer> objectiveMobIds,
            int killsPerMob,
            boolean acceptingQuestVisitors,
            long nowMs) {
        return start(operator, requestedEntries, mode, objectiveMobIds, killsPerMob,
                acceptingQuestVisitors, AgentFieldPolicyConfig.maximumParticipants(), nowMs);
    }

    static StartResult startObservation(
            Character operator,
            List<AgentRuntimeEntry> requestedEntries,
            Set<Integer> allowedMobIds,
            long nowMs) {
        return start(operator, requestedEntries, AgentFieldMode.PARTY, allowedMobIds, Integer.MAX_VALUE,
                false, AgentFieldPolicyConfig.maximumObservationParticipants(), nowMs);
    }

    private static StartResult start(
            Character operator,
            List<AgentRuntimeEntry> requestedEntries,
            AgentFieldMode mode,
            Set<Integer> objectiveMobIds,
            int killsPerMob,
            boolean acceptingQuestVisitors,
            int maximum,
            long nowMs) {
        if (operator == null || operator.getMap() == null || requestedEntries == null
                || requestedEntries.isEmpty() || mode == null) {
            return new StartResult(false, "Operator, map, participants, and mode are required.", "");
        }
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
        FieldSession existing = sessions.get(key);
        if (existing != null && hasManagedParticipants(existing)) {
            return new StartResult(false,
                    "A managed field visit is active in this map instance; drain it before replacing the session.",
                    existing.sessionId);
        }
        stop(key, nowMs);
        String sessionId = "field-" + operator.getId() + '-'
                + UUID.randomUUID().toString().substring(0, 8);
        Set<Integer> normalizedMobIds = objectiveMobIds == null ? Set.of() : Set.copyOf(objectiveMobIds);
        Map<Integer, Integer> requiredKills = new LinkedHashMap<>();
        int normalizedKills = Math.max(1, killsPerMob);
        normalizedMobIds.stream().sorted().forEach(mobId -> requiredKills.put(mobId, normalizedKills));
        FieldSession session = new FieldSession(
                sessionId, key, mode, acceptingQuestVisitors,
                requiredKills, maximum, nowMs);
        for (AgentRuntimeEntry entry : entries) {
            AgentCombatDirective baseline = AgentCombatDirectiveRuntime.directive(entry);
            AgentFieldIntent intent = normalizedMobIds.isEmpty()
                    ? AgentFieldIntent.freeGrind(sessionId)
                    : AgentFieldIntent.partyCoverage(sessionId, normalizedMobIds, requiredKills);
            Character agent = AgentRuntimeIdentityRuntime.bot(entry);
            session.participants.put(agent.getId(), new ParticipantBinding(
                    agent.getId(), intent, baseline, true, false, nowMs));
            if (!normalizedMobIds.isEmpty()) {
                AgentCombatDirectiveRuntime.assignPreferences(entry, normalizedMobIds, Set.of());
            }
        }
        sessions.put(key, session);
        for (ParticipantBinding binding : session.participants.values()) {
            publishLifecycle(session, binding, AgentFieldLifecycleEvent.Phase.REQUESTED,
                    "group field session requested", nowMs);
            publishLifecycle(session, binding, AgentFieldLifecycleEvent.Phase.ADMITTED,
                    "joined initial field formation", nowMs);
            publishLifecycle(session, binding, AgentFieldLifecycleEvent.Phase.FORMING,
                    "field allocator is assigning territory", nowMs);
            publishPopulation(session, binding.agentId, AgentFieldPopulationChangedEvent.Change.JOINED,
                    session.participants.size(), "initial field formation", nowMs);
        }
        refresh(entries.getFirst(), AgentRuntimeIdentityRuntime.bot(entries.getFirst()), nowMs);
        for (ParticipantBinding binding : session.participants.values()) {
            publishLifecycle(session, binding, AgentFieldLifecycleEvent.Phase.GRINDING,
                    "group field assignment is active", nowMs);
        }
        return new StartResult(true,
                "Started " + mode.name().toLowerCase() + " field exercise with "
                        + entries.size() + " Agent(s).", sessionId);
    }

    public static boolean add(
            Character operator, AgentRuntimeEntry entry, AgentFieldIntent intent, long nowMs) {
        return add(operator, entry, intent, AgentFieldPolicyConfig.maximumParticipants(), nowMs);
    }

    static boolean addObservation(
            Character operator, AgentRuntimeEntry entry, AgentFieldIntent intent, long nowMs) {
        return add(operator, entry, intent,
                AgentFieldPolicyConfig.maximumObservationParticipants(), nowMs);
    }

    private static boolean add(
            Character operator, AgentRuntimeEntry entry, AgentFieldIntent intent, int maximum, long nowMs) {
        if (operator == null || entry == null || intent == null) {
            return false;
        }
        FieldSession session = sessions.get(FieldKey.of(operator));
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (session == null || agent == null || agent.getMap() != operator.getMap()) {
            return false;
        }
        int population;
        synchronized (session) {
            if (session.participants.size() >= maximum) {
                return false;
            }
            if (session.participants.containsKey(agent.getId())) {
                return false;
            }
            session.participants.put(agent.getId(), new ParticipantBinding(
                    agent.getId(), intent, AgentCombatDirectiveRuntime.directive(entry),
                    true, false, nowMs));
            if (!intent.requiredMobIds().isEmpty()) {
                AgentCombatDirectiveRuntime.assignPreferences(entry, intent.requiredMobIds(), Set.of());
            }
            session.structureFingerprint = 0L;
            population = session.participants.size();
        }
        publishPopulation(session, agent.getId(), AgentFieldPopulationChangedEvent.Change.JOINED,
                population, "joined field formation", nowMs);
        ParticipantBinding added = session.participants.get(agent.getId());
        publishLifecycle(session, added, AgentFieldLifecycleEvent.Phase.REQUESTED,
                "group field admission requested", nowMs);
        publishLifecycle(session, added, AgentFieldLifecycleEvent.Phase.ADMITTED,
                "joined field formation", nowMs);
        publishLifecycle(session, added, AgentFieldLifecycleEvent.Phase.FORMING,
                "field allocator is assigning territory", nowMs);
        publishRebalance(session, agent.getName() + " joined", nowMs);
        refresh(entry, agent, nowMs);
        publishLifecycle(session, added, AgentFieldLifecycleEvent.Phase.GRINDING,
                "group field assignment is active", nowMs);
        return true;
    }

    public static boolean remove(Character operator, int agentId, long nowMs) {
        return remove(operator, agentId, nowMs, true);
    }

    public static boolean removeManaged(Character operator, int agentId, long nowMs) {
        return remove(operator, agentId, nowMs, false);
    }

    private static boolean remove(
            Character operator, int agentId, long nowMs, boolean publishGroupLifecycle) {
        if (operator == null) {
            return false;
        }
        FieldSession session = sessions.get(FieldKey.of(operator));
        if (session == null) {
            return false;
        }
        ParticipantBinding removed;
        int population;
        synchronized (session) {
            removed = session.participants.remove(agentId);
            session.assignments.remove(agentId);
            session.structureFingerprint = 0L;
            session.lastActivityAtMs = nowMs;
            population = session.participants.size();
        }
        if (removed != null && population == 0) {
            sessions.remove(session.key, session);
        }
        if (removed != null) {
            publishPopulation(session, removed.agentId, AgentFieldPopulationChangedEvent.Change.LEFT,
                    population, "left field formation", nowMs);
            publishRebalance(session, "participant left", nowMs);
            if (publishGroupLifecycle) {
                publishLifecycle(session, removed, AgentFieldLifecycleEvent.Phase.EXITED,
                        "left field formation", nowMs);
            }
            release(removed);
            synchronized (session) {
                restoreDisplacedIncumbent(session, removed.agentId, nowMs);
                if (session.displacement != null
                        && session.displacement.incumbentAgentId == removed.agentId) {
                    session.displacement = null;
                }
            }
        }
        return removed != null;
    }

    public static boolean stop(Character operator, long nowMs) {
        if (operator == null) return false;
        FieldKey key = FieldKey.of(operator);
        FieldSession session = sessions.get(key);
        return session != null && !hasManagedParticipants(session) && stop(key, nowMs);
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
        for (ParticipantBinding binding : bindings) {
            publishPopulation(removed, binding.agentId, AgentFieldPopulationChangedEvent.Change.LEFT,
                    0, "field session stopped", nowMs);
            if (!binding.managed) {
                publishLifecycle(removed, binding, AgentFieldLifecycleEvent.Phase.EXITED,
                        "field session stopped", nowMs);
            }
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
            pruneAbsentParticipants(session, nowMs);
            int activeParticipants = (int) session.participants.values().stream()
                    .filter(binding -> !binding.displaced).count();
            boolean assignmentMissing = session.assignments.size() < activeParticipants;
            boolean leaseExpired = session.assignments.values().stream()
                    .anyMatch(assignment -> assignment.expiresAtMs() <= nowMs);
            boolean prospectiveVisitor = session.acceptingQuestVisitors
                    && !session.participants.containsKey(caller.getId())
                    && hasRequiredCombatObjective(callerEntry)
                    && nowMs - session.lastVisitorAdmissionAtMs
                            .getOrDefault(caller.getId(), 0L)
                            >= AgentFieldPolicyConfig.refreshIntervalMs();
            if (!assignmentMissing && !leaseExpired && !prospectiveVisitor
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
            autoEnrollVisitor(session, callerEntry, caller, perception, nowMs);
            activeParticipants = (int) session.participants.values().stream()
                    .filter(binding -> !binding.displaced).count();
            assignmentMissing = session.assignments.size() < activeParticipants;
            leaseExpired = session.assignments.values().stream()
                    .anyMatch(assignment -> assignment.expiresAtMs() <= nowMs);
            if (!assignmentMissing && !leaseExpired
                    && nowMs - session.lastPlannedAtMs < AgentFieldPolicyConfig.refreshIntervalMs()) {
                return;
            }
            session.lastPlannedAtMs = nowMs;
            if (session.cells.isEmpty() || session.participants.isEmpty()) {
                return;
            }
            Set<Integer> releasable = releasableParticipants(session, nowMs);
            boolean vacancyRebalance = !releasable.isEmpty()
                    && nowMs - session.lastRebalanceAtMs >= AgentFieldPolicyConfig.rebalanceIntervalMs();
            long fingerprint = structureFingerprint(session, perception);
            if (fingerprint == session.structureFingerprint && !leaseExpired && !vacancyRebalance) {
                return;
            }
            List<AgentFieldParticipant> participants = plannerParticipants(
                    session, vacancyRebalance ? releasable : Set.of(), nowMs);
            List<AgentPosition> realPlayerPositions = perception.characters().stream()
                    .filter(character -> !character.agent())
                    .map(character -> character.position())
                    .toList();
            long nextRevision = session.revision + 1L;
            Map<Integer, AgentFieldAssignment> planned = planner.plan(
                    session.sessionId, session.mode, List.copyOf(session.cells.values()),
                    participants, realPlayerPositions, nowMs,
                    AgentFieldPolicyConfig.assignmentLeaseMs(), nextRevision);
            Map<Integer, AgentFieldAssignment> previous = Map.copyOf(session.assignments);
            session.assignments.clear();
            session.assignments.putAll(planned);
            session.revision = nextRevision;
            session.structureFingerprint = fingerprint;
            if (vacancyRebalance) {
                session.lastRebalanceAtMs = nowMs;
            }
            for (Map.Entry<Integer, AgentFieldAssignment> assignment : planned.entrySet()) {
                ParticipantBinding binding = session.participants.get(assignment.getKey());
                if (binding != null) {
                    AgentFieldAssignment old = previous.get(assignment.getKey());
                    if (old == null || !old.cellIds().equals(assignment.getValue().cellIds())) {
                        binding.platformLease.reset();
                        binding.stationAssignedAtMs = nowMs;
                    }
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
        AgentFieldObservationState.Snapshot observation = entry == null
                ? new AgentFieldObservationState().snapshot(nowMs)
                : entry.capabilityStates().require(AgentFieldObservationState.STATE_KEY).snapshot(nowMs);
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
                observation.role(),
                observation.lifecycle(),
                observation.posture().name(),
                observation.postureTimeMs().entrySet().stream().collect(
                        java.util.stream.Collectors.toUnmodifiableMap(
                                value -> value.getKey().name(), Map.Entry::getValue)),
                observation.attacks(),
                observation.hitLines(),
                observation.missLines(),
                observation.damage(),
                observation.assignmentChanges(),
                observation.targetMobId(),
                observation.targetPosition().x,
                observation.targetPosition().y,
                assignment == null ? List.of() : assignment.cellIds().stream().sorted().toList(),
                assignment == null ? List.of() : assignment.regionIds().stream().sorted().toList(),
                assignment == null ? 0 : assignment.anchor().x,
                assignment == null ? 0 : assignment.anchor().y,
                assignment == null ? 0L : Math.max(0L, assignment.expiresAtMs() - nowMs),
                assignment == null ? "awaiting navigation graph" : assignment.reason(),
                observation.timeline());
    }

    private static void autoEnrollVisitor(
            FieldSession session,
            AgentRuntimeEntry entry,
            Character agent,
            AgentPerceptionSnapshot perception,
            long nowMs) {
        if (!session.acceptingQuestVisitors || session.participants.containsKey(agent.getId())) {
            return;
        }
        session.lastVisitorAdmissionAtMs.put(agent.getId(), nowMs);
        AgentCombatDirective directive = AgentCombatDirectiveRuntime.directive(entry);
        if (directive == null || directive.requiredMobIds().isEmpty()) {
            return;
        }
        int compatibleCapacity = session.cells.values().stream()
                .filter(cell -> cell.objectiveCoverage(directive.requiredMobIds()) > 0)
                .mapToInt(AgentFarmingCell::capacity).sum();
        long compatibleAssignments = session.assignments.values().stream()
                .filter(assignment -> assignment.cellIds().stream()
                        .map(session.cells::get)
                        .filter(java.util.Objects::nonNull)
                        .anyMatch(cell -> cell.objectiveCoverage(directive.requiredMobIds()) > 0))
                .count();
        long activeParticipants = session.participants.values().stream()
                .filter(binding -> !binding.displaced).count();
        if (activeParticipants < session.maximumParticipants
                && compatibleAssignments < compatibleCapacity) {
            addQuestVisitor(session, entry, agent, directive, nowMs);
            return;
        }
        if (session.displacement != null || !AgentFieldPolicyConfig.questVisitorPreemptionEnabled()) {
            return;
        }
        AgentFieldPreemptionPolicy.Selection selection = AgentFieldPreemptionPolicy.select(
                new AgentFieldPreemptionPolicy.Request(
                        agent.getId(), directive.objectiveId(), nowMs),
                preemptionCandidates(session, agent, directive.requiredMobIds(), perception, nowMs),
                new AgentFieldPreemptionPolicy.Policy(
                        true,
                        AgentFieldPolicyConfig.minimumPreemptionLeaseAgeMs(),
                        AgentFieldPolicyConfig.preemptionLivePopulationWeight(),
                        AgentFieldPolicyConfig.preemptionSafeRestPenalty(),
                        AgentFieldPolicyConfig.maximumDisplacementScore()));
        if (!selection.approved()) {
            return;
        }
        preemptForVisitor(session, entry, agent, directive, selection, nowMs);
    }

    private static boolean hasRequiredCombatObjective(AgentRuntimeEntry entry) {
        AgentCombatDirective directive = AgentCombatDirectiveRuntime.directive(entry);
        return directive != null && !directive.requiredMobIds().isEmpty();
    }

    private static void addQuestVisitor(
            FieldSession session,
            AgentRuntimeEntry entry,
            Character agent,
            AgentCombatDirective directive,
            long nowMs) {
        AgentCombatDirective baseline = withoutRegion(directive);
        session.participants.put(agent.getId(), new ParticipantBinding(
                agent.getId(), AgentFieldIntent.questVisitor(
                        directive.objectiveId(), directive.requiredMobIds()),
                baseline, false, false, nowMs));
        session.structureFingerprint = 0L;
        publishPopulation(session, agent.getId(), AgentFieldPopulationChangedEvent.Change.JOINED,
                session.participants.size(), "quest visitor joined field formation", nowMs);
        publishRebalance(session, "quest visitor joined", nowMs);
        ParticipantBinding visitor = session.participants.get(agent.getId());
        publishLifecycle(session, visitor, AgentFieldLifecycleEvent.Phase.ADMITTED,
                "quest visitor received available field capacity", nowMs);
    }

    private static List<AgentFieldPreemptionPolicy.Candidate> preemptionCandidates(
            FieldSession session,
            Character visitor,
            Set<Integer> requiredMobIds,
            AgentPerceptionSnapshot perception,
            long nowMs) {
        List<AgentFieldPreemptionPolicy.Candidate> candidates = new ArrayList<>();
        Point visitorPosition = visitor.getPosition();
        List<AgentPosition> playerPositions = perception.characters().stream()
                .filter(character -> !character.agent())
                .map(character -> character.position()).toList();
        for (ParticipantBinding binding : session.participants.values()) {
            AgentFieldAssignment assignment = session.assignments.get(binding.agentId);
            if (assignment == null || binding.displaced) {
                continue;
            }
            AgentRuntimeEntry incumbentEntry =
                    AgentRuntimeRegistry.findByAgentCharacterId(binding.agentId);
            Character incumbent = AgentRuntimeIdentityRuntime.bot(incumbentEntry);
            if (incumbentEntry == null || incumbent == null) {
                continue;
            }
            int objectiveCoverage = assignment.cellIds().stream()
                    .map(session.cells::get).filter(java.util.Objects::nonNull)
                    .mapToInt(cell -> cell.objectiveCoverage(requiredMobIds)).sum();
            int livePopulation = assignment.cellIds().stream()
                    .map(session.cells::get).filter(java.util.Objects::nonNull)
                    .mapToInt(cell -> cell.relevantPopulation(binding.intent.requiredMobIds())).sum();
            boolean busy = AgentGrindTargetStateRuntime.activeTargetInMap(
                    incumbentEntry, incumbent.getMap()) != null
                    || AgentGrindLootStateRuntime.hasGrindLootTarget(incumbentEntry);
            boolean playerOccupied = playerPositions.stream().anyMatch(position ->
                    squaredDistance(assignment.anchor(), position)
                            <= squared(AgentFieldPolicyConfig.preemptionPlayerExclusionRadiusPx()));
            long cooldownAt = session.displacementCooldownAtMs
                    .getOrDefault(binding.agentId, 0L);
            long cooldownRemaining = Math.max(0L,
                    cooldownAt + AgentFieldPolicyConfig.preemptionCooldownMs() - nowMs);
            candidates.add(new AgentFieldPreemptionPolicy.Candidate(
                    binding.agentId, binding.intent.type(), assignment.stationId(),
                    objectiveCoverage, livePopulation,
                    distance(visitorPosition, assignment.anchor()),
                    Math.max(0L, nowMs - Math.max(binding.joinedAtMs,
                            binding.stationAssignedAtMs)),
                    cooldownRemaining, busy, playerOccupied,
                    hasReplacementCell(session, binding, assignment)));
        }
        return List.copyOf(candidates);
    }

    private static void preemptForVisitor(
            FieldSession session,
            AgentRuntimeEntry visitorEntry,
            Character visitor,
            AgentCombatDirective directive,
            AgentFieldPreemptionPolicy.Selection selection,
            long nowMs) {
        ParticipantBinding incumbent = session.participants.get(selection.incumbentAgentId());
        AgentFieldAssignment incumbentAssignment = session.assignments.remove(
                selection.incumbentAgentId());
        if (incumbent == null || incumbentAssignment == null) {
            return;
        }
        addQuestVisitor(session, visitorEntry, visitor, directive, nowMs);
        ParticipantBinding visitorBinding = session.participants.get(visitor.getId());
        AgentFieldAssignment visitorAssignment = copyAssignment(
                incumbentAssignment, visitor.getId(), session.revision + 1L,
                nowMs + AgentFieldPolicyConfig.assignmentLeaseMs(),
                "temporary quest visitor station lease");
        session.assignments.put(visitor.getId(), visitorAssignment);
        incumbent.displaced = true;
        session.displacement = new Displacement(
                visitor.getId(), incumbent.agentId, incumbentAssignment, nowMs,
                visitorDamage(visitorEntry, nowMs));
        session.structureFingerprint = 0L;
        suspendIncumbent(session, incumbent, selection.reason(), nowMs);
        apply(session, visitorBinding, visitorAssignment, nowMs);
        publishLifecycle(session, visitorBinding, AgentFieldLifecycleEvent.Phase.GRINDING,
                "temporary quest station lease admitted", nowMs);
    }

    private static void pruneAbsentParticipants(FieldSession session, long nowMs) {
        List<Integer> absent = new ArrayList<>();
        updateVisitorProgress(session, nowMs);
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
                    if (session.displacement != null
                            && session.displacement.visitorAgentId == binding.agentId) {
                        absent.add(binding.agentId);
                    } else {
                        binding.baselineDirective = withoutRegion(directive);
                        binding.intent = AgentFieldIntent.questVisitor(
                                directive.objectiveId(), directive.requiredMobIds());
                        session.structureFingerprint = 0L;
                    }
                }
            }
        }
        if (session.displacement != null
                && nowMs - session.displacement.lastProgressAtMs
                >= AgentFieldPolicyConfig.visitorProgressTimeoutMs()) {
            absent.add(session.displacement.visitorAgentId);
        }
        for (Integer agentId : absent) {
            ParticipantBinding removed = session.participants.remove(agentId);
            session.assignments.remove(agentId);
            if (removed != null) {
                publishPopulation(session, removed.agentId,
                        AgentFieldPopulationChangedEvent.Change.LEFT,
                        session.participants.size(), "visitor left field formation",
                        nowMs);
                release(removed);
                restoreDisplacedIncumbent(session, agentId, nowMs);
            }
        }
        if (!absent.isEmpty()) {
            session.structureFingerprint = 0L;
        }
    }

    private static List<AgentFieldParticipant> plannerParticipants(
            FieldSession session, Set<Integer> releasable, long nowMs) {
        List<AgentFieldParticipant> participants = new ArrayList<>();
        for (ParticipantBinding binding : session.participants.values()) {
            if (binding.displaced) {
                continue;
            }
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(binding.agentId);
            Character agent = AgentRuntimeIdentityRuntime.bot(entry);
            if (agent == null || agent.getPosition() == null) {
                continue;
            }
            AgentFieldAssignment previous = session.assignments.get(binding.agentId);
            long previousLease = previous == null || releasable.contains(binding.agentId)
                    ? 0L : Math.max(previous.expiresAtMs(), nowMs + 1L);
            participants.add(new AgentFieldParticipant(
                    binding.agentId, agent.getPartyId(), agent.getPosition(), binding.intent,
                    AgentFieldRolePolicy.resolve(agent),
                    previous == null ? Set.of() : previous.cellIds(),
                    previous == null ? "" : previous.stationId(),
                    previousLease, binding.joinedAtMs));
        }
        return List.copyOf(participants);
    }

    private static Set<Integer> releasableParticipants(FieldSession session, long nowMs) {
        java.util.LinkedHashSet<Integer> releasable = new java.util.LinkedHashSet<>();
        for (ParticipantBinding binding : session.participants.values()) {
            if (binding.displaced) {
                continue;
            }
            AgentFieldAssignment assignment = session.assignments.get(binding.agentId);
            if (assignment == null) {
                binding.platformLease.reset();
                continue;
            }
            int livePopulation = assignment.cellIds().stream()
                    .map(session.cells::get)
                    .filter(java.util.Objects::nonNull)
                    .mapToInt(cell -> cell.relevantPopulation(binding.intent.requiredMobIds()))
                    .sum();
            if (binding.platformLease.releasable(
                    livePopulation, nowMs, AgentFieldPolicyConfig.emptyPlatformReleaseMs())) {
                releasable.add(binding.agentId);
            }
        }
        return Set.copyOf(releasable);
    }

    private static boolean hasReplacementCell(
            FieldSession session,
            ParticipantBinding incumbent,
            AgentFieldAssignment current) {
        Set<String> occupied = session.assignments.entrySet().stream()
                .filter(indexed -> indexed.getKey() != incumbent.agentId)
                .flatMap(indexed -> indexed.getValue().cellIds().stream())
                .collect(java.util.stream.Collectors.toSet());
        return session.cells.values().stream()
                .filter(cell -> !current.cellIds().contains(cell.cellId()))
                .filter(cell -> !occupied.contains(cell.cellId()))
                .filter(cell -> cell.objectiveCoverage(incumbent.intent.requiredMobIds()) > 0)
                .anyMatch(cell -> !cell.transitOnly());
    }

    private static void suspendIncumbent(
            FieldSession session,
            ParticipantBinding binding,
            String reason,
            long nowMs) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(binding.agentId);
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (entry == null || agent == null) {
            return;
        }
        AgentMailboxRuntime.dispatch(entry, ignored -> {
            AgentFieldDisplacementRuntime.suspendAtSafeSpot(
                    entry, agent, binding.baselineDirective);
            return null;
        });
        publishLifecycle(session, binding, AgentFieldLifecycleEvent.Phase.SUSPENDED,
                reason, nowMs);
    }

    private static void restoreDisplacedIncumbent(
            FieldSession session,
            int departingVisitorId,
            long nowMs) {
        Displacement displacement = session.displacement;
        if (displacement == null || displacement.visitorAgentId != departingVisitorId) {
            return;
        }
        ParticipantBinding incumbent = session.participants.get(displacement.incumbentAgentId);
        session.displacement = null;
        session.displacementCooldownAtMs.put(displacement.incumbentAgentId, nowMs);
        if (incumbent == null) {
            return;
        }
        incumbent.displaced = false;
        incumbent.stationAssignedAtMs = nowMs;
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(incumbent.agentId);
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (entry == null || agent == null || agent.getMap() == null
                || !FieldKey.of(agent).equals(session.key)) {
            return;
        }
        AgentFieldAssignment restored = copyAssignment(
                displacement.incumbentAssignment, incumbent.agentId,
                session.revision + 1L, nowMs + AgentFieldPolicyConfig.assignmentLeaseMs(),
                "restored after temporary quest visitor lease");
        session.assignments.put(incumbent.agentId, restored);
        AgentMailboxRuntime.dispatch(entry, ignored -> {
            if (!incumbent.intent.requiredMobIds().isEmpty()) {
                AgentCombatDirectiveRuntime.assignPreferences(
                        entry, incumbent.intent.requiredMobIds(), Set.of());
            }
            AgentFieldDisplacementRuntime.resumeGrinding(entry);
            return null;
        });
        apply(session, incumbent, restored, nowMs);
        publishLifecycle(session, incumbent, AgentFieldLifecycleEvent.Phase.RESUMED,
                "quest visitor departed; previous station is preferred again", nowMs);
        session.structureFingerprint = 0L;
    }

    private static void updateVisitorProgress(FieldSession session, long nowMs) {
        Displacement displacement = session.displacement;
        if (displacement == null) {
            return;
        }
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(
                displacement.visitorAgentId);
        long damage = visitorDamage(entry, nowMs);
        if (damage > displacement.lastDamage) {
            displacement.lastDamage = damage;
            displacement.lastProgressAtMs = nowMs;
        }
    }

    private static long visitorDamage(AgentRuntimeEntry entry, long nowMs) {
        return entry == null ? 0L : entry.capabilityStates()
                .require(AgentFieldObservationState.STATE_KEY).snapshot(nowMs).damage();
    }

    private static AgentFieldAssignment copyAssignment(
            AgentFieldAssignment source,
            int agentId,
            long revision,
            long expiresAtMs,
            String reason) {
        return new AgentFieldAssignment(
                source.assignmentId() + ":lease:" + agentId + ':' + revision,
                source.mapId(), agentId, source.partySlot(), source.cellIds(),
                source.regionIds(), source.stationId(), source.anchor(),
                source.territoryMinX(), source.territoryMaxX(), expiresAtMs,
                revision, reason);
    }

    private static int distance(Point left, Point right) {
        if (left == null || right == null) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.round(left.distance(right)));
    }

    private static long squaredDistance(Point point, AgentPosition position) {
        long deltaX = (long) point.x - position.x();
        long deltaY = (long) point.y - position.y();
        return deltaX * deltaX + deltaY * deltaY;
    }

    private static long squared(int value) {
        return (long) value * value;
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
                assignment.expiresAtMs(), AgentFieldPolicyConfig.emptyPlatformReleaseMs(),
                assignment.territoryMinX(), assignment.territoryMaxX(), true);
        AgentMailboxRuntime.dispatch(entry, ignored -> {
            AgentCombatDirectiveRuntime.assignRegion(entry, region);
            AgentLootCollectionContextRuntime.enterFieldGrind(entry, binding.agentId);
            boolean changed = entry.capabilityStates().require(AgentFieldAssignmentState.STATE_KEY)
                    .update(session.sessionId, binding.intent, assignment, nowMs);
            if (changed) {
                AgentFieldCombatProfile profile = AgentFieldRolePolicy.resolve(
                        AgentRuntimeIdentityRuntime.bot(entry));
                AgentSessionEventRuntime.bus(entry).publish(new AgentFieldAssignmentChangedEvent(
                        binding.agentId, nowMs, assignment.mapId(), session.sessionId,
                        assignment.revision(), profile.role(), assignment.partySlot(),
                        assignment.cellIds().stream().sorted().toList(),
                        assignment.regionIds().stream().sorted().toList(), assignment.anchor(),
                        assignment.reason(), binding.intent.objectiveId()), AgentEventPriority.IMPORTANT);
            }
            return null;
        });
    }

    private static void release(ParticipantBinding binding) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(binding.agentId);
        if (entry == null) {
            return;
        }
        AgentMailboxRuntime.dispatch(entry, ignored -> {
            AgentLootCollectionContextRuntime.leaveFieldGrind(entry);
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
            result = result * 31L + (binding.displaced ? 1 : 0);
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
                for (ParticipantBinding binding : bindings) {
                    publishPopulation(session, binding.agentId,
                            AgentFieldPopulationChangedEvent.Change.LEFT, 0,
                            "stale field session expired", nowMs);
                    release(binding);
                }
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

    private static void publishRebalance(FieldSession session, String reason, long nowMs) {
        List<Integer> participantIds;
        int population;
        synchronized (session) {
            participantIds = session.participants.keySet().stream().sorted().toList();
            population = participantIds.size();
        }
        for (Integer participantId : participantIds) {
            publishPopulation(session, participantId,
                    AgentFieldPopulationChangedEvent.Change.REBALANCED,
                    population, reason, nowMs);
        }
    }

    private static void publishPopulation(
            FieldSession session,
            int agentId,
            AgentFieldPopulationChangedEvent.Change change,
            int population,
            String reason,
            long nowMs) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(agentId);
        ParticipantBinding binding = session.participants.get(agentId);
        String objectiveId = binding == null ? "" : binding.intent.objectiveId();
        if (entry != null) {
            AgentSessionEventRuntime.bus(entry).publish(new AgentFieldPopulationChangedEvent(
                    agentId, nowMs, session.key.mapId, session.sessionId, change,
                    population, reason, objectiveId), AgentEventPriority.IMPORTANT);
        }
    }

    private static void publishLifecycle(
            FieldSession session,
            ParticipantBinding binding,
            AgentFieldLifecycleEvent.Phase phase,
            String reason,
            long nowMs) {
        if (session == null || binding == null || phase == null) return;
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(binding.agentId);
        if (entry != null) {
            AgentSessionEventRuntime.bus(entry).publish(new AgentFieldLifecycleEvent(
                    binding.agentId, nowMs, session.key.mapId, session.sessionId,
                    session.sessionId + ':' + binding.agentId, "field-runtime", phase,
                    reason, binding.intent.objectiveId()), AgentEventPriority.IMPORTANT);
        }
    }

    private static boolean hasManagedParticipants(FieldSession session) {
        synchronized (session) {
            return session.participants.values().stream().anyMatch(binding -> binding.managed);
        }
    }

    private static final class FieldSession {
        private final String sessionId;
        private final FieldKey key;
        private final AgentFieldMode mode;
        private final boolean acceptingQuestVisitors;
        private final int maximumParticipants;
        private final Map<Integer, Integer> requiredKills;
        private final Map<Integer, Integer> completedKills = new LinkedHashMap<>();
        private final Map<Integer, ParticipantBinding> participants = new LinkedHashMap<>();
        private final Map<String, AgentFarmingCell> cells = new LinkedHashMap<>();
        private final Map<Integer, AgentFieldAssignment> assignments = new LinkedHashMap<>();
        private final Map<Integer, Long> displacementCooldownAtMs = new LinkedHashMap<>();
        private final Map<Integer, Long> lastVisitorAdmissionAtMs = new LinkedHashMap<>();
        private Displacement displacement;
        private long revision;
        private long structureFingerprint;
        private long lastObservedAtMs;
        private long lastPlannedAtMs;
        private long lastRebalanceAtMs;
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
                int maximumParticipants,
                long nowMs) {
            this.sessionId = sessionId;
            this.key = key;
            this.mode = mode;
            this.acceptingQuestVisitors = acceptingQuestVisitors;
            this.requiredKills = Map.copyOf(requiredKills);
            this.maximumParticipants = Math.max(1, maximumParticipants);
            this.lastActivityAtMs = nowMs;
        }
    }

    private static final class ParticipantBinding {
        private final int agentId;
        private AgentFieldIntent intent;
        private AgentCombatDirective baselineDirective;
        private final boolean explicit;
        private final boolean managed;
        private final long joinedAtMs;
        private final AgentFieldPlatformLeaseState platformLease = new AgentFieldPlatformLeaseState();
        private boolean displaced;
        private long stationAssignedAtMs;

        private ParticipantBinding(
                int agentId,
                AgentFieldIntent intent,
                AgentCombatDirective baselineDirective,
                boolean explicit,
                boolean managed,
                long joinedAtMs) {
            this.agentId = agentId;
            this.intent = intent;
            this.baselineDirective = baselineDirective;
            this.explicit = explicit;
            this.managed = managed;
            this.joinedAtMs = joinedAtMs;
            this.stationAssignedAtMs = joinedAtMs;
        }
    }

    private static final class Displacement {
        private final int visitorAgentId;
        private final int incumbentAgentId;
        private final AgentFieldAssignment incumbentAssignment;
        private long lastProgressAtMs;
        private long lastDamage;

        private Displacement(
                int visitorAgentId,
                int incumbentAgentId,
                AgentFieldAssignment incumbentAssignment,
                long startedAtMs,
                long lastDamage) {
            this.visitorAgentId = visitorAgentId;
            this.incumbentAgentId = incumbentAgentId;
            this.incumbentAssignment = incumbentAssignment;
            this.lastProgressAtMs = startedAtMs;
            this.lastDamage = lastDamage;
        }
    }
}
