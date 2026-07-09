package server.agents.integration.cosmic;

import client.Character;
import client.Client;
import io.netty.buffer.Unpooled;
import net.PacketHandler;
import net.PacketProcessor;
import net.packet.ByteBufInPacket;
import net.packet.InPacket;
import net.server.Server;
import server.agents.integration.CombatGateway;

/**
 * Cosmic combat boundary for dispatching Agent-built combat packets through the
 * normal server packet handlers.
 */
public enum CosmicCombatGateway implements CombatGateway {
    INSTANCE;

    @Override
    public int currentTimestamp() {
        return Server.getInstance().getCurrentTimestamp();
    }

    @Override
    public boolean dispatchSyntheticPacket(Character agent, byte[] packetBytes) {
        if (agent == null || packetBytes == null) {
            return false;
        }
        Client client = agent.getClient();
        if (client == null) {
            return false;
        }

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
