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
    private int availableKills = AgentCombatPolicyConfig.routeBlockerMaxKills();
    private long lastRefillAtMs;

    public synchronized boolean canInterrupt(Point target, long nowMs) {
        if (target == null) {
            return false;
        }
        refill(nowMs);
        if (availableKills <= 0) {
            resumeTravel();
            return false;
        }
        if (routeTarget == null || routeTarget.distanceSq(target) > 16.0) {
            routeTarget = new Point(target);
            startedAtMs = nowMs;
        }
        return nowMs - startedAtMs < AgentCombatPolicyConfig.routeBlockerTimeoutMs();
    }

    public synchronized void killed(long nowMs) {
        refill(nowMs);
        if (availableKills > 0) {
            availableKills--;
        }
        if (availableKills <= 0) {
            routeTarget = null;
            startedAtMs = 0L;
        }
    }

    public synchronized void resumeTravel() {
        routeTarget = null;
        startedAtMs = 0L;
    }

    public synchronized Snapshot snapshot(long nowMs) {
        refill(nowMs);
        return new Snapshot(routeTarget == null ? null : new Point(routeTarget),
                startedAtMs, availableKills, nextRefillAtMs(), routeTarget != null
                && nowMs - startedAtMs < AgentCombatPolicyConfig.routeBlockerTimeoutMs()
                && availableKills > 0);
    }

    public synchronized void clear() {
        routeTarget = null;
        startedAtMs = 0L;
        availableKills = AgentCombatPolicyConfig.routeBlockerMaxKills();
        lastRefillAtMs = 0L;
    }

    private void refill(long nowMs) {
        int maximum = AgentCombatPolicyConfig.routeBlockerMaxKills();
        if (maximum <= 0) {
            availableKills = 0;
            lastRefillAtMs = nowMs;
            return;
        }
        if (lastRefillAtMs == 0L) {
            availableKills = maximum;
            lastRefillAtMs = nowMs;
            return;
        }
        long intervalMs = Math.max(1L, AgentCombatPolicyConfig.routeBlockerRefillIntervalMs());
        long elapsedIntervals = Math.max(0L, nowMs - lastRefillAtMs) / intervalMs;
        if (elapsedIntervals <= 0L) {
            return;
        }
        availableKills = (int) Math.min(maximum, availableKills + elapsedIntervals);
        lastRefillAtMs += elapsedIntervals * intervalMs;
    }

    private long nextRefillAtMs() {
        return availableKills >= AgentCombatPolicyConfig.routeBlockerMaxKills()
                || lastRefillAtMs == 0L
                ? 0L
                : lastRefillAtMs + AgentCombatPolicyConfig.routeBlockerRefillIntervalMs();
    }

    public record Snapshot(Point routeTarget,
                           long startedAtMs,
                           int availableKills,
                           long nextRefillAtMs,
                           boolean budgetAvailable) {
    }
}
