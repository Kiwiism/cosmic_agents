package server.agents.capabilities.movement;

import server.agents.capabilities.combat.AgentCombatCooldownStateRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.monitoring.AgentPerformanceMonitor;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

public final class AgentSwimMovementService {
    private AgentSwimMovementService() {
    }

    public static void tickSwimming(AgentRuntimeEntry entry, Point targetPos) {
        long startedAt = System.nanoTime();
        try {
            AgentMotionTimerService.tickMotionTimers(entry);
            computeSwimIntents(entry, targetPos);
            AgentSwimPhysicsService.applySwimMotion(entry);
            AgentMovementBroadcastService.broadcastMovement(entry);
        } finally {
            AgentPerformanceMonitor.record("move-swim", System.nanoTime() - startedAt);
        }
    }

    static void computeSwimIntents(AgentRuntimeEntry entry, Point targetPos) {
        int prevVerticalHold = AgentSwimStateRuntime.swimVerticalHold(entry);
        AgentSwimStateRuntime.clearSwimInput(entry);

        if (AgentCombatCooldownStateRuntime.hasAttackCooldown(entry)) {
            return;
        }

        if (targetPos == null) {
            AgentSwimStateRuntime.setSwimVerticalHold(entry, -1);
            return;
        }

        Point pos = AgentRuntimeIdentityRuntime.bot(entry).getPosition();
        int dx = targetPos.x - pos.x;
        int dy = targetPos.y - pos.y;

        int hRadius = AgentMovementPhysicsConfig.configuredSwimArrivalRadiusPx();
        if (dx > hRadius) {
            AgentSwimStateRuntime.setSwimMoveDirection(entry, 1);
        } else if (dx < -hRadius) {
            AgentSwimStateRuntime.setSwimMoveDirection(entry, -1);
        }

        int levelBand = AgentMovementPhysicsConfig.configuredSwimLevelBandPx();
        if (Math.abs(dx) <= hRadius && Math.abs(dy) <= levelBand) {
            AgentSwimStateRuntime.setSwimMoveDirection(entry, 0);
            AgentSwimStateRuntime.setSwimVerticalHold(entry, -1);
            return;
        }

        long now = System.currentTimeMillis();
        int jumpTrigger = AgentMovementPhysicsConfig.configuredSwimJumpTriggerDyPx();
        int downBand = AgentMovementPhysicsConfig.configuredSwimDownBandPx();
        if (dy <= -jumpTrigger && now >= AgentSwimStateRuntime.swimNextJumpAtMs(entry)) {
            AgentSwimStateRuntime.setSwimJumpRequested(entry, true);
            AgentSwimStateRuntime.setSwimNextJumpAtMs(entry, now + AgentMovementPhysicsConfig.configuredSwimJumpCooldownMs());
            AgentSwimStateRuntime.setSwimVerticalHold(entry, -1);
        } else if (dy <= levelBand) {
            AgentSwimStateRuntime.setSwimVerticalHold(entry, -1);
        } else if (dy > downBand) {
            AgentSwimStateRuntime.setSwimVerticalHold(entry, 1);
        } else {
            AgentSwimStateRuntime.setSwimVerticalHold(entry, prevVerticalHold > 0 ? 1 : 0);
        }

        if (AgentSwimStateRuntime.swimWallBlocked(entry)
                && AgentSwimStateRuntime.swimMoveDirection(entry) != 0) {
            if (now >= AgentSwimStateRuntime.swimNextJumpAtMs(entry)) {
                AgentSwimStateRuntime.setSwimJumpRequested(entry, true);
                AgentSwimStateRuntime.setSwimNextJumpAtMs(
                        entry, now + AgentMovementPhysicsConfig.configuredSwimJumpCooldownMs());
            }
            AgentSwimStateRuntime.setSwimVerticalHold(entry, -1);
        }
    }
}
