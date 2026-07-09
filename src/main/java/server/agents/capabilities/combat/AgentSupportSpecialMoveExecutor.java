package server.agents.capabilities.combat;

import client.Character;
import client.Skill;
import server.agents.integration.AgentCombatGatewayRuntime;

public final class AgentSupportSpecialMoveExecutor {
    private AgentSupportSpecialMoveExecutor() {
    }

    public static boolean dispatch(Character agent, Skill skill, int skillLevel) {
        if (agent == null || skill == null || agent.getClient() == null) {
            return false;
        }

        byte[] packetBytes = AgentSupportSpecialMovePacketBuilder.build(
                agent,
                skill.getId(),
                skillLevel,
                net.server.Server.getInstance().getCurrentTimestamp());
        return AgentCombatGatewayRuntime.combat().dispatchSyntheticPacket(agent, packetBytes);
    }
}
