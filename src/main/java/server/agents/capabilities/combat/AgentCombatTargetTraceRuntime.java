package server.agents.capabilities.combat;

import client.Character;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.progression.events.AgentProgressionEventPublisher;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.capabilities.reactor.AgentReactorTargetReservationRuntime;
import server.life.Monster;
import server.maps.Reactor;

import java.awt.Point;

/** Projects existing combat state without affecting target selection. */
public final class AgentCombatTargetTraceRuntime {
    private AgentCombatTargetTraceRuntime() {
    }

    public static AgentCombatTargetTraceSnapshot snapshot(AgentRuntimeEntry entry, long nowMs) {
        if (entry == null || !AgentRuntimeIdentityRuntime.hasBot(entry)) {
            return null;
        }
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        Monster target = AgentGrindTargetStateRuntime.activeTargetInMap(entry, agent.getMap());
        Reactor reactorTarget = target == null
                ? AgentReactorTargetReservationRuntime
                .reservedObjectId(agent.getId(), agent.getMap())
                .stream()
                .mapToObj(agent.getMap()::getReactorByOid)
                .filter(reactor -> reactor != null && reactor.isAlive() && reactor.isActive())
                .findFirst()
                .orElse(null)
                : null;
        AgentCombatTacticalState.Snapshot tactical =
                AgentCombatDirectiveRuntime.tacticalSnapshot(entry);
        AgentCombatDecisionTraceState.Snapshot decision = entry.capabilityStates()
                .find(AgentCombatDecisionTraceState.STATE_KEY)
                .map(AgentCombatDecisionTraceState::snapshot)
                .orElse(null);
        AgentCombatTargetSearchModeState.Snapshot searchMode = entry.capabilityStates()
                .find(AgentCombatDecisionState.STATE_KEY)
                .map(state -> state.targetSearch().snapshot())
                .orElse(null);
        boolean exactDecision = target != null && decision != null
                && decision.selectedObjectId() == target.getObjectId();
        AgentCombatDecisionReason reason = exactDecision && tactical != null
                && tactical.lastSelectedMobId() == target.getId()
                ? tactical.lastDecision()
                : AgentCombatDecisionReason.LEGACY_CLOSEST;
        AgentCombatCandidateClass candidateClass = exactDecision && tactical != null
                && tactical.lastSelectedMobId() == target.getId()
                ? tactical.selectedClass()
                : AgentCombatCandidateClass.UNRELATED;
        long selectedAtMs = exactDecision
                ? decision.recordedAtMs()
                : 0L;

        if (reactorTarget != null) {
            String reactorName = reactorTarget.getName();
            if (reactorName == null || reactorName.isBlank()) {
                reactorName = "Reactor " + reactorTarget.getId();
            }
            return new AgentCombatTargetTraceSnapshot(
                    agent.getId(), agent.getName(), agent.getMapId(), nowMs,
                    position(agent.getPosition()), true,
                    reactorTarget.getObjectId(), reactorTarget.getId(), reactorName,
                    position(reactorTarget.getPosition()), 0, "hit reactor",
                    "REACTOR_OBJECTIVE", "Selected reactor for the current objective",
                    AgentProgressionEventPublisher.objectiveId(entry),
                    "REACTOR", 0L, 0);
        }

        return new AgentCombatTargetTraceSnapshot(
                agent.getId(), agent.getName(), agent.getMapId(), nowMs,
                position(agent.getPosition()), target != null,
                target == null ? 0 : target.getObjectId(),
                target == null ? 0 : target.getId(),
                target == null ? "" : target.getName(),
                position(target == null ? null : target.getPosition()),
                hpPercent(target), action(target, decision, exactDecision, searchMode),
                reason.name(), reasonText(reason, searchMode),
                AgentProgressionEventPublisher.objectiveId(entry),
                candidateClass.name(), selectedAtMs,
                AgentGrindTargetStateRuntime.targetSwitchCount(entry));
    }

    private static String action(Monster target,
                                 AgentCombatDecisionTraceState.Snapshot decision,
                                 boolean exactDecision,
                                 AgentCombatTargetSearchModeState.Snapshot searchMode) {
        if (target == null) {
            return "idle";
        }
        if (!exactDecision) {
            return "engage" + searchModeSuffix(searchMode);
        }
        return decision.mode().name().toLowerCase() + " / "
                + decision.outcome().name().toLowerCase().replace('_', ' ')
                + searchModeSuffix(searchMode);
    }

    private static String searchModeSuffix(AgentCombatTargetSearchModeState.Snapshot searchMode) {
        return searchMode == null ? "" : " / " + searchMode.mode().name().toLowerCase();
    }

    private static String reasonText(AgentCombatDecisionReason reason,
                                     AgentCombatTargetSearchModeState.Snapshot searchMode) {
        String base = reasonText(reason);
        return searchMode == null || searchMode.transitionReason().isBlank()
                ? base
                : base + "; " + searchMode.mode().name() + ": " + searchMode.transitionReason();
    }

    static String reasonText(AgentCombatDecisionReason reason) {
        return switch (reason) {
            case REQUIRED_LOCAL -> "Required objective target on this platform";
            case PLATFORM_BATCH_CLEAR -> "Committed local batch of objective and incidental targets";
            case INCIDENTAL_PLATFORM_SWEEP -> "Incidental target for local spawn pressure";
            case REQUIRED_DEBT -> "Required target after an incidental sweep";
            case INCIDENTAL_NO_REQUIRED_AVAILABLE -> "Incidental target; no required mob is available";
            case CLOSEST_REACHABLE_FALLBACK -> "Closest reachable fallback target";
            case ROUTE_BLOCKER -> "Target is blocking the current route";
            case EVADE_BLOCKER -> "Avoiding a route-blocking target";
            case LEGACY_CLOSEST -> "Nearest eligible combat target";
        };
    }

    private static int hpPercent(Monster target) {
        if (target == null || target.getMaxHp() <= 0) {
            return 0;
        }
        return (int) Math.max(0L, Math.min(100L,
                Math.round(target.getHp() * 100.0d / target.getMaxHp())));
    }

    private static AgentCombatTargetTraceSnapshot.Position position(Point point) {
        return point == null
                ? AgentCombatTargetTraceSnapshot.Position.missing()
                : new AgentCombatTargetTraceSnapshot.Position(true, point.x, point.y);
    }
}
