package server.agents.capabilities.movement;

import server.agents.integration.AgentBotCombatCooldownStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.agents.integration.AgentBotSwimStateRuntime;
import server.agents.runtime.AgentPerformanceMonitor;
import server.bots.BotEntry;

import java.awt.Point;

public final class AgentSwimMovementService {
    private AgentSwimMovementService() {
    }

    public static void tickSwimming(BotEntry entry, Point targetPos) {
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

    static void computeSwimIntents(BotEntry entry, Point targetPos) {
        int prevVerticalHold = AgentBotSwimStateRuntime.swimVerticalHold(entry);
        AgentBotSwimStateRuntime.clearSwimInput(entry);

        if (AgentBotCombatCooldownStateRuntime.hasAttackCooldown(entry)) {
            return;
        }

        if (targetPos == null) {
            AgentBotSwimStateRuntime.setSwimVerticalHold(entry, -1);
            return;
        }

        Point pos = AgentBotRuntimeIdentityRuntime.bot(entry).getPosition();
        int dx = targetPos.x - pos.x;
        int dy = targetPos.y - pos.y;

        int hRadius = AgentMovementPhysicsConfig.configuredSwimArrivalRadiusPx();
        if (dx > hRadius) {
            AgentBotSwimStateRuntime.setSwimMoveDirection(entry, 1);
        } else if (dx < -hRadius) {
            AgentBotSwimStateRuntime.setSwimMoveDirection(entry, -1);
        }

        int levelBand = AgentMovementPhysicsConfig.configuredSwimLevelBandPx();
        if (Math.abs(dx) <= hRadius && Math.abs(dy) <= levelBand) {
            AgentBotSwimStateRuntime.setSwimMoveDirection(entry, 0);
            AgentBotSwimStateRuntime.setSwimVerticalHold(entry, -1);
            return;
        }

        long now = System.currentTimeMillis();
        int jumpTrigger = AgentMovementPhysicsConfig.configuredSwimJumpTriggerDyPx();
        int downBand = AgentMovementPhysicsConfig.configuredSwimDownBandPx();
        if (dy <= -jumpTrigger && now >= AgentBotSwimStateRuntime.swimNextJumpAtMs(entry)) {
            AgentBotSwimStateRuntime.setSwimJumpRequested(entry, true);
            AgentBotSwimStateRuntime.setSwimNextJumpAtMs(entry, now + AgentMovementPhysicsConfig.configuredSwimJumpCooldownMs());
            AgentBotSwimStateRuntime.setSwimVerticalHold(entry, -1);
        } else if (dy <= levelBand) {
            AgentBotSwimStateRuntime.setSwimVerticalHold(entry, -1);
        } else if (dy > downBand) {
            AgentBotSwimStateRuntime.setSwimVerticalHold(entry, 1);
        } else {
            AgentBotSwimStateRuntime.setSwimVerticalHold(entry, prevVerticalHold > 0 ? 1 : 0);
        }
    }
}
