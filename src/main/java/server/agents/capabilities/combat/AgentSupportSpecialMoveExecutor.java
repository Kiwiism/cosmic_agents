package server.agents.capabilities.combat;

import client.Character;
import client.Skill;
import server.agents.integration.AgentCombatGatewayRuntime;
import server.agents.integration.AgentClientGatewayRuntime;

public final class AgentSupportSpecialMoveExecutor {
    private AgentSupportSpecialMoveExecutor() {
    }

    public static boolean dispatch(Character agent, Skill skill, int skillLevel) {
        if (agent == null || skill == null || !AgentClientGatewayRuntime.clients().hasClient(agent)) {
            return false;
        }

        byte[] packetBytes = AgentSupportSpecialMovePacketBuilder.build(
                agent,
                skill.getId(),
                skillLevel,
                AgentCombatGatewayRuntime.combat().currentTimestamp());
        return AgentCombatGatewayRuntime.combat().dispatchSyntheticPacket(agent, packetBytes);
    }
}
