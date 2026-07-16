package server.agents.capabilities.movement;

import client.Character;
import constants.game.CharacterStance;
import server.agents.capabilities.combat.AgentCombatCooldownStateRuntime;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned movement snapshot seam while physics internals migrate.
 */
public final class AgentMovementSnapshotService {
    private AgentMovementSnapshotService() {
    }

    public static AgentMovementPacketSnapshot currentSnapshot(AgentRuntimeEntry entry) {
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        int stance = resolveSnapshotStance(entry, agent);
        if (agent != null && agent.getStance() != stance) {
            agent.setStance(stance);
        }
        return new AgentMovementPacketSnapshot(
                AgentMovementStateRuntime.movementVelocityX(entry),
                AgentMovementStateRuntime.movementVelocityY(entry),
                broadcastStance(entry, stance));
    }

    private static int resolveSnapshotStance(AgentRuntimeEntry entry, Character agent) {
        if (agent != null && agent.getChair() > 0) {
            return AgentMovementStateRuntime.facingDirectionSign(entry) < 0
                    ? CharacterStance.SIT_LEFT_STANCE
                    : CharacterStance.SIT_RIGHT_STANCE;
        }
        return AgentMovementPoseService.resolveStance(entry);
    }

    private static int broadcastStance(AgentRuntimeEntry entry, int baseStance) {
        if (System.currentTimeMillis() >= AgentCombatCooldownStateRuntime.alertedUntilMs(entry)) {
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
