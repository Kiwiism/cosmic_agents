package server.agents.runtime.activity.control;

import client.Character;
import server.agents.capabilities.supplies.AgentSupplyProcurementRuntime;
import server.agents.capabilities.supplies.AgentSupplyProcurementState;
import server.agents.runtime.AgentModeService;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.AgentActivityBootstrap;
import server.agents.runtime.activity.control.facade.AgentLiveActivityFacade;
import server.agents.runtime.activity.control.facade.AgentLiveActivityFacadeRegistry;
import server.agents.runtime.activity.control.binding.AgentVictoriaWorldMapTransfer;
import server.agents.runtime.activity.control.binding.AgentWorldMapTransfer;
import server.agents.runtime.activity.session.AgentActivityExitResult;
import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.activity.session.AgentActivityPhase;
import server.agents.runtime.activity.session.AgentActivityRollbackPort;
import server.agents.runtime.activity.session.AgentActivitySessionSnapshot;
import server.agents.runtime.activity.world.AgentWorldDirective;
import server.agents.runtime.activity.world.AgentWorldDirectiveType;
import server.agents.runtime.activity.world.AgentWorldDirectorSession;
import server.agents.runtime.field.AgentFieldSafeSpotPolicy;

import java.awt.Point;
import java.util.Set;

/** Standard exact-session lifecycle routing plus normal, walk-only safe parking. */
public final class AgentStandardWorldActivityLifecycleHandler
        implements AgentWorldActivityLifecycleHandler {
    private static final long DEFAULT_DEADLINE_MS = config.AgentTuning.longValue(
            "server.agents.runtime.activity.control.AgentStandardWorldActivityLifecycleHandler.DEFAULT_DEADLINE_MS");
    private static final long SAFE_SPOT_ARRIVAL_DISTANCE_SQ = 48L * 48L;

    private final AgentLiveActivityFacadeRegistry facades;
    private final AgentWorldMapTransfer mapTransfer;

    public AgentStandardWorldActivityLifecycleHandler(AgentLiveActivityFacadeRegistry facades) {
        this(facades, new AgentVictoriaWorldMapTransfer());
    }

    AgentStandardWorldActivityLifecycleHandler(
            AgentLiveActivityFacadeRegistry facades,
            AgentWorldMapTransfer mapTransfer) {
        if (facades == null) throw new IllegalArgumentException("live facades are required");
        if (mapTransfer == null) throw new IllegalArgumentException("map transfer is required");
        this.facades = facades;
        this.mapTransfer = mapTransfer;
    }

    @Override
    public Result advance(
            AgentWorldDirective directive,
            AgentWorldDirectorSession directorSession,
            AgentRuntimeEntry entry,
            Character agent,
            AgentActivityKind sourceKind,
            String sourceSessionId,
            long nowMs) {
        AgentActivityKind kind = sourceKind != null ? sourceKind
                : directorSession.observedActivityKind();
        String retainedSessionId = text(sourceSessionId).isEmpty()
                ? directorSession.observedSessionId() : text(sourceSessionId);
        return switch (directive.type()) {
            case SUSPEND_ACTIVITY -> suspend(
                    directive, entry, agent, kind, retainedSessionId, nowMs);
            case RESUME_ACTIVITY -> resume(entry, agent, kind, retainedSessionId, nowMs);
            case STOP_ACTIVITY -> stop(entry, agent, kind, retainedSessionId,
                    directive.reason(), nowMs);
            case ABANDON_ACTIVITY -> abandon(entry, agent, kind, retainedSessionId,
                    directive.reason(), nowMs);
            case REQUEST_SUPPLY_MAINTENANCE -> requestSupply(entry, agent, nowMs);
            default -> Result.rejected(
                    "directive is not a lifecycle operation", kind, retainedSessionId);
        };
    }

    private Result suspend(
            AgentWorldDirective directive,
            AgentRuntimeEntry entry,
            Character agent,
            AgentActivityKind kind,
            String retainedSessionId,
            long nowMs) {
        if (kind == null) {
            AgentModeService.startStop(entry);
            return park(entry, agent, null, "Agent had no active activity", nowMs,
                    deadline(directive));
        }
        AgentLiveActivityFacade facade = facades.bind(kind, entry, agent);
        AgentActivitySessionSnapshot snapshot = facade.source().snapshot(nowMs);
        String sessionId = snapshot.sessionId().isEmpty()
                ? retainedSessionId : snapshot.sessionId();
        if (snapshot.phase().terminal() || snapshot.phase() == AgentActivityPhase.IDLE) {
            return park(entry, agent, kind, "activity already released", nowMs,
                    deadline(directive), sessionId);
        }
        if (snapshot.phase() != AgentActivityPhase.SUSPENDED) {
            AgentActivityExitResult exit = facade.source().requestGracefulExit(
                    directive.reason(), nowMs, deadline(directive));
            if (exit.status() == AgentActivityExitResult.Status.REJECTED) {
                return Result.rejected(exit.reason(), kind, sessionId);
            }
            return Result.progressed(
                    exit.reason().isEmpty() ? "waiting for a safe activity boundary" : exit.reason(),
                    kind, sessionId);
        }
        return park(entry, agent, kind, "activity suspended", nowMs,
                deadline(directive), sessionId);
    }

    private Result park(
            AgentRuntimeEntry entry,
            Character agent,
            AgentActivityKind kind,
            String reason,
            long nowMs,
            long deadlineMs) {
        return park(entry, agent, kind, reason, nowMs, deadlineMs, "");
    }

    private Result park(
            AgentRuntimeEntry entry,
            Character agent,
            AgentActivityKind kind,
            String reason,
            long nowMs,
            long deadlineMs,
            String sessionId) {
        Point target = AgentFieldSafeSpotPolicy.select(entry, agent, Set.of());
        Point current = agent.getPosition();
        if (target == null || current == null) {
            if (nowMs < deadlineMs) {
                AgentModeService.startStop(entry);
                return Result.progressed(
                        reason + "; waiting for a reachable safe-spot graph", kind, sessionId);
            }
            return retreatToReturnMap(entry, agent, kind, reason, sessionId, nowMs);
        }
        if (current.distanceSq(target) <= SAFE_SPOT_ARRIVAL_DISTANCE_SQ) {
            AgentModeService.startStop(entry);
            return Result.completed(reason + "; parked at a spawn-free safe spot", kind, sessionId);
        }
        AgentModeService.startMoveTo(entry, target, true);
        return Result.progressed(reason + "; walking normally to a spawn-free safe spot",
                kind, sessionId);
    }

    private Result retreatToReturnMap(
            AgentRuntimeEntry entry,
            Character agent,
            AgentActivityKind kind,
            String reason,
            String sessionId,
            long nowMs) {
        int destinationMapId = agent.getMap() == null || agent.getMap().getReturnMap() == null
                ? -1 : agent.getMap().getReturnMap().getId();
        if (destinationMapId <= 0 || destinationMapId == agent.getMapId()) {
            AgentModeService.startStop(entry);
            return Result.rejected(
                    reason + "; no reachable safe spot or normal safe-map route is available",
                    kind, sessionId);
        }
        var travel = mapTransfer.travel(entry, agent, destinationMapId, nowMs);
        return switch (travel.status()) {
            case READY -> Result.progressed(
                    reason + "; reached the return map and is locating its safe spot",
                    kind, sessionId);
            case PENDING -> Result.progressed(
                    reason + "; retreating normally to return map " + destinationMapId,
                    kind, sessionId);
            case FAILED -> Result.rejected(
                    reason + "; " + travel.reason(), kind, sessionId);
        };
    }

    private Result resume(
            AgentRuntimeEntry entry,
            Character agent,
            AgentActivityKind kind,
            String sessionId,
            long nowMs) {
        if (kind == null || sessionId.isEmpty()) {
            return Result.rejected("no exact suspended activity is retained", kind, sessionId);
        }
        AgentLiveActivityFacade facade = facades.bind(kind, entry, agent);
        AgentActivityRollbackPort.Result resumed = facade.rollback().requestResume(sessionId, nowMs);
        return switch (resumed.status()) {
            case RESUMED -> Result.completed(resumed.reason(), kind, sessionId);
            case DEFERRED -> Result.progressed(resumed.reason(), kind, sessionId);
            case REJECTED -> Result.rejected(resumed.reason(), kind, sessionId);
        };
    }

    private Result stop(
            AgentRuntimeEntry entry,
            Character agent,
            AgentActivityKind kind,
            String sessionId,
            String reason,
            long nowMs) {
        if (kind == null) {
            AgentModeService.startStop(entry);
            return Result.completed("Agent is already idle", null, "");
        }
        boolean stopped = AgentActivityBootstrap.requestPrimaryStop(
                entry, agent, kind, reason, nowMs);
        if (!stopped) {
            return Result.progressed("waiting for protected work to finish before stopping",
                    kind, sessionId);
        }
        AgentModeService.startStop(entry);
        return Result.completed("activity stopped at its graceful boundary", kind, "");
    }

    private Result abandon(
            AgentRuntimeEntry entry,
            Character agent,
            AgentActivityKind kind,
            String sessionId,
            String reason,
            long nowMs) {
        if (kind == null) return Result.completed("Agent is already idle", null, "");
        try {
            boolean stopped = AgentActivityBootstrap.forcePrimaryStop(
                    entry, agent, kind, reason, nowMs);
            if (!stopped) {
                return Result.rejected(
                        "activity refused immediate abandon because it protects live state",
                        kind, sessionId);
            }
            AgentModeService.startStop(entry);
            return Result.completed("activity was explicitly abandoned", kind, "");
        } catch (RuntimeException protectedState) {
            return Result.rejected(protectedState.getMessage(), kind, sessionId);
        }
    }

    private Result requestSupply(AgentRuntimeEntry entry, Character agent, long nowMs) {
        boolean consumed = AgentSupplyProcurementRuntime.tick(entry, agent, nowMs);
        boolean active = entry.capabilityStates().require(
                AgentSupplyProcurementState.STATE_KEY).isActive();
        if (!active) {
            return Result.rejected(
                    "no critical NPC-shop supply request is currently available", null, "");
        }
        return Result.completed(consumed
                ? "route-aware supply maintenance started"
                : "route-aware supply maintenance is active", null, "");
    }

    private static long deadline(AgentWorldDirective directive) {
        return directive.expiresAtMs() > 0L
                ? directive.expiresAtMs() : directive.createdAtMs() + DEFAULT_DEADLINE_MS;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
