package server.agents.integration;

import server.agents.capabilities.combat.AgentAttackExecutionProvider;
import server.agents.capabilities.movement.AgentMovementPoseService;
import server.bots.BotEntry;

public final class AgentBotCombatFacingRuntime {
    private AgentBotCombatFacingRuntime() {
    }

    public static void rememberAttackFacing(BotEntry entry, int attackPacketStance) {
        AgentBotMovementStateRuntime.setFacingDirection(entry,
                AgentAttackExecutionProvider.facingDirFromAttackPacketStance(attackPacketStance));
        AgentMovementPoseService.syncCharacterState(entry);
    }
}
