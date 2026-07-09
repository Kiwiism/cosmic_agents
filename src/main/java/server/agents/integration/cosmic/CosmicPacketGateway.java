package server.agents.integration.cosmic;

import client.Character;
import io.netty.buffer.Unpooled;
import net.packet.ByteBufInPacket;
import net.packet.InPacket;
import net.packet.Packet;
import server.agents.integration.PacketGateway;
import tools.PacketCreator;

/**
 * Cosmic packet boundary for Agent packet construction and broadcast.
 */
public enum CosmicPacketGateway implements PacketGateway {
    INSTANCE;

    @Override
    public void broadcastMovePlayer(Character agent, byte[] movementData) {
        if (agent == null || agent.getMap() == null || movementData == null) {
            return;
        }
        InPacket packet = new ByteBufInPacket(Unpooled.wrappedBuffer(movementData));
        Packet movePacket = PacketCreator.movePlayer(agent.getId(), packet, movementData.length);
        agent.getMap().broadcastMessage(agent, movePacket, false);
    }
}
