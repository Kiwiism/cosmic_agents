package server.agents.capabilities.movement;

import client.Character;
import constants.game.CharacterStance;
import server.agents.integration.AgentBotCombatCooldownStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.bots.BotEntry;

/**
 * Agent-owned movement snapshot seam while physics internals migrate.
 */
public final class AgentMovementSnapshotService {
    private AgentMovementSnapshotService() {
    }

    public static AgentMovementPacketSnapshot currentSnapshot(BotEntry entry) {
        int stance = AgentMovementPoseService.resolveStance(entry);
        Character agent = AgentBotRuntimeIdentityRuntime.bot(entry);
        if (agent != null && agent.getStance() != stance) {
            agent.setStance(stance);
        }
        return new AgentMovementPacketSnapshot(
                AgentBotMovementStateRuntime.movementVelocityX(entry),
                AgentBotMovementStateRuntime.movementVelocityY(entry),
                broadcastStance(entry, stance));
    }

    private static int broadcastStance(BotEntry entry, int baseStance) {
        if (System.currentTimeMillis() >= AgentBotCombatCooldownStateRuntime.alertedUntilMs(entry)) {
            return baseStance;
        }
        if (baseStance == CharacterStance.STAND_RIGHT_STANCE) {
            return CharacterStance.ALERT_RIGHT_STANCE;
        }
        if (baseStance == CharacterStance.STAND_LEFT_STANCE) {
            return CharacterStance.ALERT_LEFT_STANCE;
        }
        return baseStance;
    }
}
