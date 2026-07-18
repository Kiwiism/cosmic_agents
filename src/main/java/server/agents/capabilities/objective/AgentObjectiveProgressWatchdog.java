package server.agents.capabilities.objective;

import client.Character;
import server.agents.capabilities.navigation.AgentNavigationDebugStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

public final class AgentObjectiveProgressWatchdog {
    private static final int MEANINGFUL_APPROACH_PX = 12;
    private AgentObjectiveProgressWatchdog() {
    }

    public enum Action {
        NONE,
        NUDGE,
        RECOVER
    }

    public record Evaluation(Action action, long stalledMs) {
    }

    public static final class State {
        private boolean initialized;
        private boolean nudgeIssued;
        private long progressAtMs;
        private long lastNudgeAtMs;
        private long journalSequence;
        private int mapId;
        private Point navigationTarget;
        private Point previousNavigationTarget;
        private double bestNavigationDistanceSq;
        private int level;
        private int exp;

        public void reset() {
            initialized = false;
            nudgeIssued = false;
            progressAtMs = 0L;
            lastNudgeAtMs = 0L;
            journalSequence = 0L;
            mapId = 0;
            navigationTarget = null;
            previousNavigationTarget = null;
            bestNavigationDistanceSq = Double.MAX_VALUE;
            level = 0;
            exp = 0;
        }

        public long lastNudgeAtMs() {
            return lastNudgeAtMs;
        }
    }

    public static void start(State state,
                             AgentRuntimeEntry entry,
                             Character agent,
                             long nowMs) {
        recordProgress(state, entry, agent, nowMs);
    }

    public static Evaluation evaluate(State state,
                                      AgentRuntimeEntry entry,
                                      Character agent,
                                      long nowMs,
                                      AgentObjectiveRecoveryPolicy policy) {
        if (madeProgress(state, entry, agent)) {
            recordProgress(state, entry, agent, nowMs);
            return new Evaluation(Action.NONE, 0L);
        }
        long stalledMs = Math.max(0L, nowMs - state.progressAtMs);
        if (policy.recoverAfterMs() > 0L && stalledMs >= policy.recoverAfterMs()) {
            return new Evaluation(Action.RECOVER, stalledMs);
        }
        if (policy.nudgeAfterMs() > 0L
                && stalledMs >= policy.nudgeAfterMs()
                && !state.nudgeIssued) {
            state.nudgeIssued = true;
            state.lastNudgeAtMs = nowMs;
            return new Evaluation(Action.NUDGE, stalledMs);
        }
        return new Evaluation(Action.NONE, stalledMs);
    }

    private static boolean madeProgress(State state,
                                        AgentRuntimeEntry entry,
                                        Character agent) {
        if (!state.initialized
                || state.journalSequence != entry.capabilityRuntimeState().journalSequence()
                || state.mapId != agent.getMapId()
                || state.level != agent.getLevel()
                || state.exp != agent.getExp()) {
            return true;
        }
        Point target = navigationTarget(entry);
        if (target == null) {
            return false;
        }
        Point position = agent.getPosition();
        double distanceSq = position == null ? Double.MAX_VALUE : position.distanceSq(target);
        if (!target.equals(state.navigationTarget)) {
            boolean oscillating = target.equals(state.previousNavigationTarget);
            state.previousNavigationTarget = copy(state.navigationTarget);
            state.navigationTarget = new Point(target);
            state.bestNavigationDistanceSq = distanceSq;
            return !oscillating;
        }
        double improvement = (double) MEANINGFUL_APPROACH_PX * MEANINGFUL_APPROACH_PX;
        if (distanceSq + improvement < state.bestNavigationDistanceSq) {
            state.bestNavigationDistanceSq = distanceSq;
            return true;
        }
        return false;
    }

    private static void recordProgress(State state,
                                       AgentRuntimeEntry entry,
                                       Character agent,
                                       long nowMs) {
        Point position = agent.getPosition();
        Point target = navigationTarget(entry);
        state.initialized = true;
        state.nudgeIssued = false;
        state.progressAtMs = nowMs;
        state.lastNudgeAtMs = 0L;
        state.journalSequence = entry.capabilityRuntimeState().journalSequence();
        state.mapId = agent.getMapId();
        if (target != null && !target.equals(state.navigationTarget)) {
            state.previousNavigationTarget = copy(state.navigationTarget);
        }
        state.navigationTarget = copy(target);
        state.bestNavigationDistanceSq = position == null || target == null
                ? Double.MAX_VALUE : position.distanceSq(target);
        state.level = agent.getLevel();
        state.exp = agent.getExp();
    }

    private static Point navigationTarget(AgentRuntimeEntry entry) {
        Point planned = AgentNavigationDebugStateRuntime.plannedNavigationTargetPosition(entry);
        return planned == null ? AgentNavigationDebugStateRuntime.navTargetPosition(entry) : planned;
    }

    private static Point copy(Point point) {
        return point == null ? null : new Point(point);
    }
}
