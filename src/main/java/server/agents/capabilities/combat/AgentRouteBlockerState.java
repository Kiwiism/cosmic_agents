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
    private long travelCooldownUntilMs;

    public synchronized boolean canInterrupt(Point target, long nowMs) {
        if (target == null) {
            return false;
        }
        if (travelCooldownUntilMs > nowMs) {
            return false;
        }
        if (travelCooldownUntilMs != 0L) {
            kills = 0;
            travelCooldownUntilMs = 0L;
        }
        if (routeTarget == null || routeTarget.distanceSq(target) > 16.0) {
            routeTarget = new Point(target);
            startedAtMs = nowMs;
        }
        return nowMs - startedAtMs < AgentCombatPolicyConfig.routeBlockerTimeoutMs()
                && kills < AgentCombatPolicyConfig.routeBlockerMaxKills();
    }

    public synchronized void killed(long nowMs) {
        kills++;
        if (kills >= AgentCombatPolicyConfig.routeBlockerMaxKills()) {
            routeTarget = null;
            startedAtMs = 0L;
            travelCooldownUntilMs = nowMs
                    + AgentCombatPolicyConfig.routeBlockerTravelCooldownMs();
        }
    }

    public synchronized void resumeTravel() {
        routeTarget = null;
        startedAtMs = 0L;
    }

    public synchronized Snapshot snapshot(long nowMs) {
        return new Snapshot(routeTarget == null ? null : new Point(routeTarget),
                startedAtMs, kills, travelCooldownUntilMs, routeTarget != null
                && travelCooldownUntilMs <= nowMs
                && nowMs - startedAtMs < AgentCombatPolicyConfig.routeBlockerTimeoutMs()
                && kills < AgentCombatPolicyConfig.routeBlockerMaxKills());
    }

    public synchronized void clear() {
        routeTarget = null;
        startedAtMs = 0L;
        kills = 0;
        travelCooldownUntilMs = 0L;
    }

    public record Snapshot(Point routeTarget,
                           long startedAtMs,
                           int kills,
                           long travelCooldownUntilMs,
                           boolean budgetAvailable) {
    }
}
