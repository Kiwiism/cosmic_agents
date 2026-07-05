package server.agents.integration;

import server.agents.capabilities.combat.AgentAttackExecutionProvider;
import server.agents.capabilities.movement.AgentMovementPoseService;
import server.agents.runtime.AgentRuntimeEntry;

public final class AgentBotCombatFacingRuntime {
    private AgentBotCombatFacingRuntime() {
    }

    public static void rememberAttackFacing(AgentRuntimeEntry entry, int attackPacketStance) {
        AgentBotMovementStateRuntime.setFacingDirection(entry,
                AgentAttackExecutionProvider.facingDirFromAttackPacketStance(attackPacketStance));
        AgentMovementPoseService.syncCharacterState(entry);
    }
}
