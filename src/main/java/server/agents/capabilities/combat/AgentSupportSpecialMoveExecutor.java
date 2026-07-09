package server.agents.capabilities.combat;

import client.Character;
import client.Skill;
import server.agents.integration.cosmic.CosmicAgentServerAdapter;

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
        return CosmicAgentServerAdapter.INSTANCE.combat().dispatchSyntheticPacket(agent, packetBytes);
    }
}
