package server.agents.capabilities.combat;

import client.Character;
import client.Client;
import client.Skill;
import io.netty.buffer.Unpooled;
import net.PacketHandler;
import net.PacketProcessor;
import net.packet.ByteBufInPacket;
import net.packet.InPacket;

public final class AgentSupportSpecialMoveExecutor {
    private AgentSupportSpecialMoveExecutor() {
    }

    public static boolean dispatch(Character agent, Skill skill, int skillLevel) {
        Client client = agent.getClient();
        if (client == null) {
            return false;
        }

        byte[] packetBytes = AgentSupportSpecialMovePacketBuilder.build(
                agent,
                skill.getId(),
                skillLevel,
                net.server.Server.getInstance().getCurrentTimestamp());
        InPacket packet = new ByteBufInPacket(Unpooled.wrappedBuffer(packetBytes));
        short packetId = packet.readShort();
        PacketHandler handler = PacketProcessor.getProcessor(agent.getWorld(), client.getChannel()).getHandler(packetId);
        if (handler == null || !handler.validateState(client)) {
            return false;
        }

        handler.handlePacket(packet, client);
        return true;
    }
}
