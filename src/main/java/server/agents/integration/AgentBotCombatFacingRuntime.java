package server.agents.integration;

import server.agents.capabilities.combat.AgentAttackExecutionProvider;
import server.bots.BotEntry;
import server.bots.BotPhysicsEngine;

public final class AgentBotCombatFacingRuntime {
    private AgentBotCombatFacingRuntime() {
    }

    public static void rememberAttackFacing(BotEntry entry, int attackPacketStance) {
        AgentBotMovementStateRuntime.setFacingDirection(entry,
                AgentAttackExecutionProvider.facingDirFromAttackPacketStance(attackPacketStance));
        BotPhysicsEngine.syncCharacterState(entry);
    }
}
