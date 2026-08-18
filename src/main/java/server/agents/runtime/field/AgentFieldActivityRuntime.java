package server.agents.runtime.field;

import client.Character;
import server.agents.capabilities.combat.AgentGrindTargetStateRuntime;
import server.agents.capabilities.looting.AgentGrindLootStateRuntime;
import server.agents.capabilities.movement.AgentMovementStateResetService;
import server.agents.events.AgentEventPriority;
import server.agents.field.AgentFieldObservationState;
import server.agents.field.AgentFieldRuntime;
import server.agents.field.AgentFieldSnapshot;
import server.agents.field.events.AgentFieldLifecycleEvent;
import server.agents.field.events.AgentFieldRestEvent;
import server.agents.runtime.AgentModeService;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentSessionEventRuntime;
import server.agents.runtime.activity.AgentActivityBootstrap;
import server.agents.runtime.activity.AgentActivityTick;

import java.awt.Point;

/** Per-Agent lifecycle wrapper around the existing map allocator and grind mode. */
public final class AgentFieldActivityRuntime {
    public static final String ACTIVITY_ID = "field-activity";
    public static final long DEFAULT_GRACEFUL_EXIT_TIMEOUT_MS = config.AgentTuning.longValue(
            "server.agents.runtime.field.AgentFieldActivityRuntime.DEFAULT_GRACEFUL_EXIT_TIMEOUT_MS");
    private static final int REST_ARRIVAL_DISTANCE_PX = config.AgentTuning.intValue(
            "server.agents.runtime.field.AgentFieldActivityRuntime.REST_ARRIVAL_DISTANCE_PX");

    private AgentFieldActivityRuntime() {
    }

    public static AgentFieldSessionResult requestSession(
            AgentRuntimeEntry entry,
            Character agent,
            AgentFieldEntryRequest request,
            AgentFieldAdmissionMode admissionMode,
            long nowMs) {
        if (entry == null || agent == null || request == null || admissionMode == null) {
            return rejected(AgentFieldSessionResult.Status.REJECTED_INVALID_REQUEST,
                    "entry, Agent, request, and admission mode are required");
        }
        AgentFieldActivityState state = entry.capabilityStates()
                .require(AgentFieldActivityState.STATE_KEY);
        AgentFieldActivityState.Snapshot current = state.snapshot();
        if (current.active()) {
            if (current.handle().requestId().equals(request.requestId())
                    && current.handle().callerId().equals(request.callerId())) {
                return new AgentFieldSessionResult(
                        AgentFieldSessionResult.Status.ALREADY_ACTIVE_SAME_REQUEST,
                        current.handle(), "field request is already active");
            }
            return rejected(AgentFieldSessionResult.Status.REJECTED_ALREADY_ACTIVE,
                    "another field request already owns this Agent");
        }
        if (agent.getMapId() != request.visit().mapId()) {
            return rejected(AgentFieldSessionResult.Status.REJECTED_WRONG_MAP,
                    "travel must place the Agent in the requested field first");
        }
        if (admissionMode == AgentFieldAdmissionMode.JOIN_EXISTING
                && !AgentFieldRuntime.hasSession(agent)) {
            return rejected(AgentFieldSessionResult.Status.REJECTED_NO_SESSION,
                    "no field session exists in this map instance");
        }
        if (!AgentActivityBootstrap.admission().prepare(
                AgentActivityBootstrap.HUNTING_CONTROLLER_ID,
                entry, agent, "starting managed field visit", nowMs)) {
            return rejected(AgentFieldSessionResult.Status.REJECTED_FOREGROUND_BUSY,
                    "another foreground activity is still draining");
        }
        publish(entry, agent, request, "pending", AgentFieldLifecycleEvent.Phase.REQUESTED,
                "field admission requested", nowMs);
        AgentFieldRuntime.AdmissionResult admitted = AgentFieldRuntime.admit(
                agent, entry, request.visit().intent(), request.visit().acceptingQuestVisitors(),
                request.visit().maximumParticipants(), nowMs);
        if (!admitted.success()) {
            publish(entry, agent, request, "rejected", AgentFieldLifecycleEvent.Phase.FAILED,
                    admitted.message(), nowMs);
            return rejected(admitted.message().contains("capacity")
                    ? AgentFieldSessionResult.Status.REJECTED_CAPACITY
                    : AgentFieldSessionResult.Status.REJECTED_INVALID_REQUEST, admitted.message());
        }
        AgentFieldSessionHandle handle = new AgentFieldSessionHandle(
                admitted.sessionId(), request.requestId(), request.callerId(),
                agent.getId(), agent.getMapId(), nowMs);
        state.start(handle, request.visit(), agent.getLevel(), agent.getExp());
        entry.capabilityStates().require(AgentFieldObservationState.STATE_KEY)
                .narrationLevel(request.visit().narrationLevel());
        AgentModeService.startGrind(entry, AgentMovementStateResetService::clearNavigationState);
        publish(entry, agent, handle, request.visit(), AgentFieldLifecycleEvent.Phase.ADMITTED,
                admitted.message(), nowMs);
        publish(entry, agent, handle, request.visit(), AgentFieldLifecycleEvent.Phase.FORMING,
                "field allocator is assigning a reachable territory", nowMs);
        AgentFieldRuntime.refresh(entry, agent, nowMs);
        publish(entry, agent, handle, request.visit(), AgentFieldLifecycleEvent.Phase.GRINDING,
                "managed field activity owns the local grind", nowMs);
        AgentFieldCheckpointRuntime.persist(entry, agent, nowMs);
        return new AgentFieldSessionResult(AgentFieldSessionResult.Status.STARTED, handle, "");
    }

    public static boolean active(AgentRuntimeEntry entry) {
        return entry != null && entry.capabilityStates()
                .find(AgentFieldActivityState.STATE_KEY)
                .map(AgentFieldActivityState::active).orElse(false);
    }

    public static AgentActivityTick tick(
            AgentRuntimeEntry entry, Character agent, long nowMs) {
        AgentFieldActivityState state = entry.capabilityStates()
                .require(AgentFieldActivityState.STATE_KEY);
        AgentFieldActivityState.Snapshot snapshot = state.snapshot();
        if (!snapshot.active()) return AgentActivityTick.PASS;
        if (agent == null || agent.getMapId() != snapshot.handle().mapId()) {
            terminate(entry, agent, AgentFieldLifecycleEvent.Phase.FAILED,
                    "Agent left the managed field map", nowMs);
            return AgentActivityTick.CONSUMED;
        }
        if (snapshot.phase() == AgentFieldActivityState.Phase.SUSPENDED) {
            return AgentActivityTick.IDLE;
        }
        if (snapshot.phase() == AgentFieldActivityState.Phase.DRAINING) {
            if (nowMs >= snapshot.exitDeadlineMs() || readyToExit(entry, agent)) {
                terminate(entry, agent, AgentFieldLifecycleEvent.Phase.EXITED,
                        snapshot.exitReason(), nowMs);
                return AgentActivityTick.CONSUMED;
            }
            return AgentActivityTick.IDLE;
        }
        if (snapshot.phase() == AgentFieldActivityState.Phase.RESTING) {
            return tickRest(entry, agent, state, snapshot, nowMs);
        }
        if (AgentFieldRuntime.isDisplaced(agent)) {
            return AgentActivityTick.IDLE;
        }
        if (!entry.modeState().grinding()) {
            AgentModeService.startGrind(entry, AgentMovementStateResetService::clearNavigationState);
        }
        AgentFieldRuntime.refresh(entry, agent, nowMs);
        return AgentActivityTick.IDLE;
    }

    public static boolean requestExit(
            AgentRuntimeEntry entry, Character agent, AgentFieldExitRequest request) {
        if (entry == null || request == null) return false;
        AgentFieldActivityState state = entry.capabilityStates()
                .require(AgentFieldActivityState.STATE_KEY);
        AgentFieldActivityState.Snapshot snapshot = state.snapshot();
        if (!snapshot.active() || !snapshot.handle().sessionId().equals(request.sessionId())
                || !snapshot.handle().callerId().equals(request.callerId())) return false;
        if (request.mode() == AgentFieldExitMode.FORCE_NOW) {
            terminate(entry, agent, AgentFieldLifecycleEvent.Phase.EXITED, request.reason(),
                    request.requestedAtMs());
            return true;
        }
        state.drain(request.reason(), request.deadlineMs());
        publish(entry, agent, snapshot.handle(), snapshot.visit(),
                AgentFieldLifecycleEvent.Phase.DRAINING, request.reason(), request.requestedAtMs());
        AgentFieldCheckpointRuntime.persist(entry, agent, request.requestedAtMs());
        return true;
    }

    public static boolean requestGracefulStop(
            AgentRuntimeEntry entry, Character agent, String reason, long nowMs) {
        AgentFieldActivityState.Snapshot snapshot = state(entry);
        if (!snapshot.active()) return true;
        requestExit(entry, agent, AgentFieldExitRequest.graceful(
                snapshot.handle(), reason, nowMs, nowMs + DEFAULT_GRACEFUL_EXIT_TIMEOUT_MS));
        return !active(entry);
    }

    public static void forceStop(
            AgentRuntimeEntry entry, Character agent, String reason, long nowMs) {
        AgentFieldActivityState.Snapshot snapshot = state(entry);
        if (snapshot.active()) {
            requestExit(entry, agent, AgentFieldExitRequest.force(
                    snapshot.handle(), reason, nowMs));
        }
    }

    public static boolean suspend(
            AgentRuntimeEntry entry, Character agent, String reason, long nowMs) {
        AgentFieldActivityState.Snapshot snapshot = state(entry);
        if (!snapshot.active() || snapshot.phase() == AgentFieldActivityState.Phase.DRAINING) return false;
        entry.capabilityStates().require(AgentFieldActivityState.STATE_KEY).suspend();
        AgentModeService.startStop(entry);
        publish(entry, agent, snapshot.handle(), snapshot.visit(),
                AgentFieldLifecycleEvent.Phase.SUSPENDED, reason, nowMs);
        AgentFieldCheckpointRuntime.persist(entry, agent, nowMs);
        return true;
    }

    public static boolean resume(
            AgentRuntimeEntry entry, Character agent, String reason, long nowMs) {
        AgentFieldActivityState.Snapshot snapshot = state(entry);
        if (!snapshot.active() || snapshot.phase() != AgentFieldActivityState.Phase.SUSPENDED) return false;
        entry.capabilityStates().require(AgentFieldActivityState.STATE_KEY).resume();
        AgentModeService.startGrind(entry, AgentMovementStateResetService::clearNavigationState);
        publish(entry, agent, snapshot.handle(), snapshot.visit(),
                AgentFieldLifecycleEvent.Phase.RESUMED, reason, nowMs);
        AgentFieldCheckpointRuntime.persist(entry, agent, nowMs);
        return true;
    }

    public static boolean requestRest(
            AgentRuntimeEntry entry, Character agent, long durationMs, String reason, long nowMs) {
        AgentFieldActivityState.Snapshot snapshot = state(entry);
        if (!snapshot.active() || !snapshot.visit().restAllowed()
                || snapshot.phase() != AgentFieldActivityState.Phase.GRINDING || durationMs <= 0L) {
            return false;
        }
        Point target = AgentFieldSafeSpotPolicy.select(
                entry, agent, snapshot.visit().intent().requiredMobIds());
        if (target == null) return false;
        entry.capabilityStates().require(AgentFieldActivityState.STATE_KEY)
                .rest(target, nowMs + durationMs, reason);
        AgentModeService.startMoveTo(entry, target, false);
        publishRest(entry, agent, snapshot.handle(), snapshot.visit(),
                AgentFieldRestEvent.Phase.STARTED, target, durationMs, reason, nowMs);
        publish(entry, agent, snapshot.handle(), snapshot.visit(),
                AgentFieldLifecycleEvent.Phase.RESTING, reason, nowMs);
        AgentFieldCheckpointRuntime.persist(entry, agent, nowMs);
        return true;
    }

    private static AgentActivityTick tickRest(
            AgentRuntimeEntry entry, Character agent, AgentFieldActivityState state,
            AgentFieldActivityState.Snapshot snapshot, long nowMs) {
        Point target = snapshot.restTarget();
        if (target == null) {
            state.completeRest();
            AgentModeService.startGrind(entry, AgentMovementStateResetService::clearNavigationState);
            return AgentActivityTick.IDLE;
        }
        if (!state.restArrived() && close(agent.getPosition(), target)) {
            state.arriveRest();
            AgentModeService.startStop(entry);
            publishRest(entry, agent, snapshot.handle(), snapshot.visit(),
                    AgentFieldRestEvent.Phase.ARRIVED, target,
                    Math.max(0L, snapshot.restUntilMs() - nowMs), snapshot.restReason(), nowMs);
        }
        if (nowMs >= snapshot.restUntilMs()) {
            state.completeRest();
            AgentModeService.startGrind(entry, AgentMovementStateResetService::clearNavigationState);
            publishRest(entry, agent, snapshot.handle(), snapshot.visit(),
                    AgentFieldRestEvent.Phase.COMPLETED, target, 0L, snapshot.restReason(), nowMs);
            publish(entry, agent, snapshot.handle(), snapshot.visit(),
                    AgentFieldLifecycleEvent.Phase.GRINDING,
                    "rest complete; returning to assigned territory", nowMs);
            AgentFieldCheckpointRuntime.persist(entry, agent, nowMs);
        }
        return AgentActivityTick.IDLE;
    }

    private static boolean readyToExit(AgentRuntimeEntry entry, Character agent) {
        return AgentGrindTargetStateRuntime.activeTargetInMap(entry, agent.getMap()) == null
                && !AgentGrindLootStateRuntime.hasGrindLootTarget(entry);
    }

    private static boolean close(Point left, Point right) {
        return left != null && right != null
                && Math.abs(left.x - right.x) <= REST_ARRIVAL_DISTANCE_PX
                && Math.abs(left.y - right.y) <= REST_ARRIVAL_DISTANCE_PX;
    }

    private static void terminate(
            AgentRuntimeEntry entry, Character agent, AgentFieldLifecycleEvent.Phase phase,
            String reason, long nowMs) {
        AgentFieldActivityState state = entry.capabilityStates()
                .require(AgentFieldActivityState.STATE_KEY);
        AgentFieldActivityState.Snapshot snapshot = state.snapshot();
        if (!snapshot.active()) return;
        AgentFieldSnapshot fieldSnapshot = agent == null ? null
                : AgentFieldRuntime.snapshot(agent, nowMs);
        AgentFieldSnapshot.Participant participant = fieldSnapshot == null ? null
                : fieldSnapshot.participants().stream()
                .filter(value -> value.agentId() == snapshot.handle().characterId())
                .findFirst().orElse(null);
        AgentFieldOutcome.Status outcomeStatus = phase == AgentFieldLifecycleEvent.Phase.FAILED
                ? AgentFieldOutcome.Status.FAILED
                : fieldSnapshot != null && fieldSnapshot.objectiveComplete()
                ? AgentFieldOutcome.Status.COMPLETED
                : AgentFieldOutcome.Status.EXITED;
        state.recordOutcome(new AgentFieldOutcome(
                snapshot.handle(), outcomeStatus, reason,
                outcomeStatus == AgentFieldOutcome.Status.FAILED,
                Math.max(0L, nowMs - snapshot.handle().startedAtMs()),
                participant == null ? 0L : participant.kills(),
                state.startingLevel(), state.startingExp(),
                agent == null ? state.startingLevel() : agent.getLevel(),
                agent == null ? state.startingExp() : agent.getExp(),
                fieldSnapshot == null ? 0 : fieldSnapshot.liveMobs(),
                fieldSnapshot != null && fieldSnapshot.objectiveComplete(),
                fieldSnapshot == null ? java.util.Map.of() : fieldSnapshot.completedKills(),
                java.util.Map.of()));
        if (agent != null) AgentFieldRuntime.removeManaged(
                agent, snapshot.handle().characterId(), nowMs);
        AgentModeService.startStop(entry);
        entry.capabilityStates().require(AgentFieldTerminalState.STATE_KEY)
                .record(snapshot.handle().sessionId(), phase, reason, nowMs);
        publish(entry, agent, snapshot.handle(), snapshot.visit(), phase, reason, nowMs);
        state.clear();
        AgentFieldCheckpointRuntime.delete(agent);
    }

    public static AgentFieldOutcome outcome(AgentRuntimeEntry entry) {
        return entry == null ? null : entry.capabilityStates()
                .find(AgentFieldActivityState.STATE_KEY)
                .map(AgentFieldActivityState::lastOutcome).orElse(null);
    }

    private static AgentFieldActivityState.Snapshot state(AgentRuntimeEntry entry) {
        return entry == null ? new AgentFieldActivityState().snapshot()
                : entry.capabilityStates().require(AgentFieldActivityState.STATE_KEY).snapshot();
    }

    private static AgentFieldSessionResult rejected(
            AgentFieldSessionResult.Status status, String reason) {
        return new AgentFieldSessionResult(status, null, reason);
    }

    private static void publish(
            AgentRuntimeEntry entry, Character agent, AgentFieldEntryRequest request,
            String sessionId, AgentFieldLifecycleEvent.Phase phase, String reason, long nowMs) {
        if (entry == null || agent == null) return;
        AgentSessionEventRuntime.bus(entry).publish(new AgentFieldLifecycleEvent(
                agent.getId(), nowMs, agent.getMapId(), sessionId,
                request.requestId(), request.callerId(), phase, reason,
                request.visit().intent().objectiveId()), AgentEventPriority.IMPORTANT);
    }

    private static void publish(
            AgentRuntimeEntry entry, Character agent, AgentFieldSessionHandle handle,
            AgentFieldVisitRequest visit, AgentFieldLifecycleEvent.Phase phase,
            String reason, long nowMs) {
        if (entry == null || agent == null) return;
        AgentSessionEventRuntime.bus(entry).publish(new AgentFieldLifecycleEvent(
                agent.getId(), nowMs, handle.mapId(), handle.sessionId(), handle.requestId(),
                handle.callerId(), phase, reason, visit.intent().objectiveId()),
                AgentEventPriority.IMPORTANT);
    }

    private static void publishRest(
            AgentRuntimeEntry entry, Character agent, AgentFieldSessionHandle handle,
            AgentFieldVisitRequest visit, AgentFieldRestEvent.Phase phase,
            Point target, long durationMs, String reason, long nowMs) {
        AgentSessionEventRuntime.bus(entry).publish(new AgentFieldRestEvent(
                agent.getId(), nowMs, handle.mapId(), handle.sessionId(), phase,
                target, durationMs, reason, visit.intent().objectiveId()), AgentEventPriority.AMBIENT);
    }
}
