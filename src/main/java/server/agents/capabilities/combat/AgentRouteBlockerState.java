package server.agents.capabilities.combat;

import server.agents.runtime.state.AgentCapabilityStateKey;

import java.awt.Point;

/** Bounded interruption budget for combat encountered during scripted travel. */
public final class AgentRouteBlockerState {
    public static final AgentCapabilityStateKey<AgentRouteBlockerState> STATE_KEY =
            new AgentCapabilityStateKey<>("combat.route-blocker",
                    AgentRouteBlockerState.class, AgentRouteBlockerState::new);

    private Point routeTarget;
    private long startedAtMs;
    private int kills;

    public synchronized boolean canInterrupt(Point target, long nowMs) {
        if (target == null) {
            clear();
            return false;
        }
        if (routeTarget == null || routeTarget.distanceSq(target) > 16.0) {
            routeTarget = new Point(target);
            startedAtMs = nowMs;
            kills = 0;
        }
        return nowMs - startedAtMs < AgentCombatPolicyConfig.routeBlockerTimeoutMs()
                && kills < AgentCombatPolicyConfig.routeBlockerMaxKills();
    }

    public synchronized void killed() {
        kills++;
    }

    public synchronized Snapshot snapshot(long nowMs) {
        return new Snapshot(routeTarget == null ? null : new Point(routeTarget),
                startedAtMs, kills, routeTarget != null
                && nowMs - startedAtMs < AgentCombatPolicyConfig.routeBlockerTimeoutMs()
                && kills < AgentCombatPolicyConfig.routeBlockerMaxKills());
    }

    public synchronized void clear() {
        routeTarget = null;
        startedAtMs = 0L;
        kills = 0;
    }

    public record Snapshot(Point routeTarget, long startedAtMs, int kills, boolean budgetAvailable) {
    }
}
