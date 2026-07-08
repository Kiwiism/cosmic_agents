package server.agents.capabilities.combat;

import server.agents.integration.AgentMovementStateRuntime;
import server.agents.capabilities.movement.AgentMovementPoseService;
import server.agents.runtime.AgentRuntimeEntry;

public final class AgentCombatFacingRuntime {
    private AgentCombatFacingRuntime() {
    }

    public static void rememberAttackFacing(AgentRuntimeEntry entry, int attackPacketStance) {
        AgentMovementStateRuntime.setFacingDirection(entry,
                AgentAttackExecutionProvider.facingDirFromAttackPacketStance(attackPacketStance));
        AgentMovementPoseService.syncCharacterState(entry);
    }
}
