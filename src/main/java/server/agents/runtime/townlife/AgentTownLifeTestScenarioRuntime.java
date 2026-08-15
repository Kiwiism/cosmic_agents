package server.agents.runtime.townlife;

import client.Character;
import server.agents.capabilities.townlife.AgentTownLifeAdmissionMode;
import server.agents.capabilities.townlife.AgentTownLifeEntryRequest;
import server.agents.capabilities.townlife.AgentTownLifeExitRequest;
import server.agents.capabilities.townlife.AgentTownLifeProfile;
import server.agents.capabilities.townlife.AgentTownLifeProfileRepository;
import server.agents.capabilities.townlife.AgentTownLifeRuntime;
import server.agents.capabilities.townlife.AgentTownLifeSessionResult;
import server.agents.capabilities.townlife.AgentTownLifeState;
import server.agents.capabilities.townlife.AgentTownLifeVisitRequest;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentSessionEventRuntime;
import server.agents.runtime.activity.AgentForegroundActivityTick;

import java.awt.Point;
import java.util.Comparator;
import java.util.List;

/** External deterministic enter/exit/stage/re-enter coordinator used only by operator tests. */
public final class AgentTownLifeTestScenarioRuntime {
    private static final String TUNING_PREFIX =
            "server.agents.runtime.townlife.AgentTownLifeTestScenarioRuntime.";
    private static final int STANDBY_ARRIVAL_DISTANCE_PX = tuningInt("STANDBY_ARRIVAL_DISTANCE_PX");
    private static final int STANDBY_VERTICAL_DISTANCE_PX = tuningInt("STANDBY_VERTICAL_DISTANCE_PX");
    private static final int SAFE_OFFSET_PX = tuningInt("SAFE_OFFSET_PX");

    private AgentTownLifeTestScenarioRuntime() {
    }

    public static AgentTownLifeSessionResult start(
            AgentRuntimeEntry entry,
            Character agent,
            AgentTownLifeTestScenarioRequest request,
            long nowMs) {
        if (entry == null || agent == null || request == null) {
            return new AgentTownLifeSessionResult(
                    AgentTownLifeSessionResult.Status.REJECTED_INVALID_REQUEST, 0,
                    "entry, agent, and scenario are required");
        }
        if (agent.getMapId() != request.townMapId()) {
            return new AgentTownLifeSessionResult(
                    AgentTownLifeSessionResult.Status.REJECTED_NOT_LOCAL,
                    request.townMapId(), "test Agent must already be in the requested town");
        }
        AgentTownLifeProfileRepository.defaultRepository().require(request.townMapId());
        AgentTownLifeTestScenarioState state = entry.capabilityStates()
                .require(AgentTownLifeTestScenarioState.STATE_KEY);
        if (state.active()) {
            return new AgentTownLifeSessionResult(
                    AgentTownLifeSessionResult.Status.REJECTED_ALREADY_ACTIVE_OTHER_REQUEST,
                    request.townMapId(), "another TownLife test scenario owns this Agent");
        }
        state.start(request);
        entry.capabilityStates().require(AgentTownLifeTestObservationState.STATE_KEY)
                .enable(request.scenarioId(), false);
        AgentTownLifeSessionResult result = startVisit(entry, agent, state, nowMs);
        if (!result.started()
                && result.status() != AgentTownLifeSessionResult.Status.ALREADY_ACTIVE_SAME_REQUEST) {
            state.fail(result.reason());
            publish(entry, agent, state.snapshot(), AgentTownLifeTestScenarioEvent.Phase.FAILED,
                    result.reason(), nowMs);
            entry.capabilityStates().require(AgentTownLifeTestObservationState.STATE_KEY).disable();
        }
        return result;
    }

    public static boolean active(AgentRuntimeEntry entry) {
        return entry != null && entry.capabilityStates()
                .find(AgentTownLifeTestScenarioState.STATE_KEY)
                .map(AgentTownLifeTestScenarioState::active).orElse(false);
    }

    public static AgentForegroundActivityTick tick(
            AgentRuntimeEntry entry, Character agent, long nowMs) {
        AgentTownLifeTestScenarioState state = entry.capabilityStates()
                .require(AgentTownLifeTestScenarioState.STATE_KEY);
        AgentTownLifeTestScenarioState.Snapshot snapshot = state.snapshot();
        if (!snapshot.active()) {
            return AgentForegroundActivityTick.PASS;
        }
        if (agent.getMapId() != snapshot.request().townMapId()) {
            fail(entry, agent, state, "Agent left the test town", nowMs);
            return AgentForegroundActivityTick.PASS;
        }
        return switch (snapshot.phase()) {
            case IN_TOWN_LIFE -> tickTownLife(entry, agent, state, snapshot, nowMs);
            case STAGING -> tickStaging(entry, agent, state, snapshot, nowMs);
            case OUTSIDE_IDLE -> tickOutsideIdle(entry, agent, state, snapshot, nowMs);
            case STOPPING -> tickStopping(entry, agent, state, snapshot, nowMs);
            case INACTIVE, COMPLETED, FAILED -> AgentForegroundActivityTick.PASS;
        };
    }

    public static boolean requestStop(
            AgentRuntimeEntry entry, Character agent, String reason, long nowMs) {
        if (!active(entry)) {
            return false;
        }
        AgentTownLifeTestScenarioState state = entry.capabilityStates()
                .require(AgentTownLifeTestScenarioState.STATE_KEY);
        state.stopping();
        publish(entry, agent, state.snapshot(), AgentTownLifeTestScenarioEvent.Phase.STOP_REQUESTED,
                reason, nowMs);
        requestTownLifeExit(entry, agent, state.snapshot(), reason, nowMs);
        return true;
    }

    public static AgentTownLifeTestScenarioState.Snapshot snapshot(AgentRuntimeEntry entry) {
        return entry == null ? null : entry.capabilityStates()
                .require(AgentTownLifeTestScenarioState.STATE_KEY).snapshot();
    }

    private static AgentForegroundActivityTick tickTownLife(
            AgentRuntimeEntry entry,
            Character agent,
            AgentTownLifeTestScenarioState state,
            AgentTownLifeTestScenarioState.Snapshot snapshot,
            long nowMs) {
        if (AgentTownLifeRuntime.active(entry)) {
            return AgentForegroundActivityTick.PASS;
        }
        AgentTownLifeVisitLeaseRuntime.clear(entry, agent);
        Point standby = resolveStandby(agent, snapshot.request(), snapshot.cyclesCompleted());
        state.visitCompleted(standby);
        publish(entry, agent, state.snapshot(), AgentTownLifeTestScenarioEvent.Phase.EXITED_VISIT,
                "TownLife visit exited", nowMs);
        publish(entry, agent, state.snapshot(), AgentTownLifeTestScenarioEvent.Phase.STAGING,
                snapshot.request().standbyTarget().display(), nowMs);
        return AgentForegroundActivityTick.IDLE;
    }

    private static AgentForegroundActivityTick tickStaging(
            AgentRuntimeEntry entry,
            Character agent,
            AgentTownLifeTestScenarioState state,
            AgentTownLifeTestScenarioState.Snapshot snapshot,
            long nowMs) {
        Point target = snapshot.standbyPoint();
        if (target == null) {
            fail(entry, agent, state, "no safe standby point is available", nowMs);
            return AgentForegroundActivityTick.PASS;
        }
        PrimitiveCapabilityGateway gateway = AgentPrimitiveCapabilityGatewayRuntime.gateway();
        Point position = gateway.position(agent);
        boolean arrived = position != null && gateway.grounded(agent)
                && Math.abs(position.y - target.y) <= STANDBY_VERTICAL_DISTANCE_PX
                && position.distanceSq(target)
                <= (long) STANDBY_ARRIVAL_DISTANCE_PX * STANDBY_ARRIVAL_DISTANCE_PX;
        if (!arrived) {
            gateway.navigate(entry, target, false);
            return AgentForegroundActivityTick.IDLE;
        }
        gateway.stop(entry);
        state.outsideIdle(nowMs + snapshot.request().outsideDurationMs());
        publish(entry, agent, state.snapshot(), AgentTownLifeTestScenarioEvent.Phase.OUTSIDE_IDLE,
                snapshot.request().standbyTarget().display(), nowMs);
        return AgentForegroundActivityTick.CONSUMED;
    }

    private static AgentForegroundActivityTick tickOutsideIdle(
            AgentRuntimeEntry entry,
            Character agent,
            AgentTownLifeTestScenarioState state,
            AgentTownLifeTestScenarioState.Snapshot snapshot,
            long nowMs) {
        AgentPrimitiveCapabilityGatewayRuntime.gateway().stop(entry);
        if (nowMs < snapshot.nextActionAtMs()) {
            return AgentForegroundActivityTick.CONSUMED;
        }
        if (snapshot.cyclesCompleted() >= snapshot.request().cycles()) {
            complete(entry, agent, state, "all requested cycles completed", nowMs);
            return AgentForegroundActivityTick.PASS;
        }
        publish(entry, agent, snapshot, AgentTownLifeTestScenarioEvent.Phase.REENTERING,
                "starting the next TownLife visit", nowMs);
        AgentTownLifeSessionResult result = startVisit(entry, agent, state, nowMs);
        if (!result.started()
                && result.status() != AgentTownLifeSessionResult.Status.ALREADY_ACTIVE_SAME_REQUEST) {
            fail(entry, agent, state, result.reason(), nowMs);
            return AgentForegroundActivityTick.PASS;
        }
        return AgentForegroundActivityTick.PASS;
    }

    private static AgentForegroundActivityTick tickStopping(
            AgentRuntimeEntry entry,
            Character agent,
            AgentTownLifeTestScenarioState state,
            AgentTownLifeTestScenarioState.Snapshot snapshot,
            long nowMs) {
        if (AgentTownLifeRuntime.active(entry)) {
            requestTownLifeExit(entry, agent, snapshot, "test scenario stopped", nowMs);
            return AgentForegroundActivityTick.PASS;
        }
        complete(entry, agent, state, "test scenario stopped", nowMs);
        return AgentForegroundActivityTick.PASS;
    }

    private static AgentTownLifeSessionResult startVisit(
            AgentRuntimeEntry entry,
            Character agent,
            AgentTownLifeTestScenarioState state,
            long nowMs) {
        AgentTownLifeTestScenarioState.Snapshot snapshot = state.snapshot();
        int cycle = snapshot.cyclesStarted() + 1;
        AgentTownLifeTestScenarioRequest request = snapshot.request();
        String requestId = request.scenarioId() + ":cycle:" + cycle + ":agent:" + agent.getId();
        AgentTownLifeVisitRequest visit = new AgentTownLifeVisitRequest(
                request.townMapId(), AgentTownLifeVisitRequest.Purpose.SYSTEM,
                "cyclic TownLife observation test", 0L);
        AgentTownLifeVisitLeaseRequest lease = new AgentTownLifeVisitLeaseRequest(
                AgentTownLifeEntryRequest.external(requestId, request.callerId(), visit),
                AgentTownLifeAdmissionMode.MANUAL_ONLY,
                nowMs + request.visitDurationMs(), request.gracefulTimeoutMs(),
                "TownLife test cycle " + cycle + " duration elapsed");
        AgentTownLifeSessionResult result = AgentTownLifeVisitLeaseRuntime.start(
                entry, agent, lease, nowMs, agent.getId() + cycle);
        if (result.started()
                || result.status() == AgentTownLifeSessionResult.Status.ALREADY_ACTIVE_SAME_REQUEST) {
            state.visitStarted();
            publish(entry, agent, state.snapshot(), AgentTownLifeTestScenarioEvent.Phase.STARTED_VISIT,
                    "visit " + cycle + " of " + request.cycles(), nowMs);
        }
        return result;
    }

    private static void requestTownLifeExit(
            AgentRuntimeEntry entry,
            Character agent,
            AgentTownLifeTestScenarioState.Snapshot snapshot,
            String reason,
            long nowMs) {
        if (!AgentTownLifeRuntime.active(entry)) {
            return;
        }
        AgentTownLifeState townState = entry.capabilityStates()
                .require(AgentTownLifeState.STATE_KEY);
        var handle = townState.sessionHandle(agent.getId());
        if (handle != null) {
            AgentTownLifeRuntime.requestExit(entry, agent,
                    AgentTownLifeExitRequest.graceful(handle, reason, nowMs,
                            nowMs + snapshot.request().gracefulTimeoutMs()));
        }
    }

    private static Point resolveStandby(
            Character agent, AgentTownLifeTestScenarioRequest request, int cycle) {
        AgentTownLifeProfile profile = AgentTownLifeProfileRepository.defaultRepository()
                .require(request.townMapId());
        PrimitiveCapabilityGateway gateway = AgentPrimitiveCapabilityGatewayRuntime.gateway();
        AgentTownLifeStandbyTarget target = request.standbyTarget();
        Point requested = switch (target.type()) {
            case FALLBACK -> fallback(profile, agent.getId() + cycle);
            case PORTAL -> gateway.portalPosition(agent, target.value());
            case NPC -> npcStandby(agent, profile, gateway, Integer.parseInt(target.value()));
            case FACILITY -> profile.facilities().stream()
                    .filter(facility -> facility.id().equals(target.value()))
                    .flatMap(facility -> facility.approachPoints().stream())
                    .map(AgentTownLifeProfile.PointSpec::point)
                    .findFirst().orElse(null);
        };
        Point safe = safeGroundPoint(agent, profile, gateway, requested, agent.getId());
        return safe != null ? safe : fallback(profile, agent.getId() + cycle);
    }

    private static Point npcStandby(Character agent,
                                    AgentTownLifeProfile profile,
                                    PrimitiveCapabilityGateway gateway,
                                    int npcId) {
        Point npc = gateway.npcPosition(agent, npcId);
        if (npc == null) {
            return null;
        }
        int offset = profile.npcSpots().stream()
                .filter(spot -> spot.npcId() == npcId)
                .mapToInt(AgentTownLifeProfile.NpcSpot::offsetX)
                .findFirst().orElse(agent.getId() % 2 == 0 ? SAFE_OFFSET_PX : -SAFE_OFFSET_PX);
        return new Point(npc.x + offset, npc.y);
    }

    private static Point safeGroundPoint(Character agent,
                                         AgentTownLifeProfile profile,
                                         PrimitiveCapabilityGateway gateway,
                                         Point requested,
                                         int seed) {
        if (requested == null || agent.getMap() == null) {
            return null;
        }
        List<Point> candidates = List.of(
                requested,
                new Point(requested.x + SAFE_OFFSET_PX, requested.y),
                new Point(requested.x - SAFE_OFFSET_PX, requested.y),
                new Point(requested.x + SAFE_OFFSET_PX * 2, requested.y),
                new Point(requested.x - SAFE_OFFSET_PX * 2, requested.y));
        return candidates.stream()
                .map(candidate -> gateway.groundPoint(agent.getMap(), candidate))
                .filter(profile::allowsOccupancy)
                .min(Comparator.comparingInt(candidate ->
                        Math.abs(candidate.x - requested.x) + Math.floorMod(seed, 3)))
                .orElse(null);
    }

    private static Point fallback(AgentTownLifeProfile profile, int seed) {
        List<Point> points = profile.roamFallbackPoints();
        return points.isEmpty() ? null : new Point(points.get(Math.floorMod(seed, points.size())));
    }

    private static void complete(AgentRuntimeEntry entry,
                                 Character agent,
                                 AgentTownLifeTestScenarioState state,
                                 String reason,
                                 long nowMs) {
        AgentTownLifeVisitLeaseRuntime.clear(entry, agent);
        state.complete();
        publish(entry, agent, state.snapshot(), AgentTownLifeTestScenarioEvent.Phase.COMPLETED,
                reason, nowMs);
    }

    private static void fail(AgentRuntimeEntry entry,
                             Character agent,
                             AgentTownLifeTestScenarioState state,
                             String reason,
                             long nowMs) {
        AgentTownLifeVisitLeaseRuntime.clear(entry, agent);
        state.fail(reason);
        publish(entry, agent, state.snapshot(), AgentTownLifeTestScenarioEvent.Phase.FAILED,
                reason, nowMs);
    }

    private static void publish(AgentRuntimeEntry entry,
                                Character agent,
                                AgentTownLifeTestScenarioState.Snapshot snapshot,
                                AgentTownLifeTestScenarioEvent.Phase phase,
                                String detail,
                                long nowMs) {
        AgentSessionEventRuntime.bus(entry).publish(new AgentTownLifeTestScenarioEvent(
                agent.getId(), nowMs, snapshot.request().scenarioId(),
                snapshot.cyclesStarted(), phase, detail));
    }

    private static int tuningInt(String name) {
        return config.AgentTuning.intValue(TUNING_PREFIX + name);
    }
}
