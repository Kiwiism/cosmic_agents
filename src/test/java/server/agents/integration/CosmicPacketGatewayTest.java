package server.agents.integration;

import client.Character;
import net.packet.InPacket;
import net.packet.Packet;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.cosmic.CosmicPacketGateway;
import server.maps.MapleMap;
import tools.PacketCreator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CosmicPacketGatewayTest {
    @Test
    void broadcastMovePlayerBuildsPacketAndBroadcastsThroughMap() {
        Character agent = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        Packet packet = mock(Packet.class);
        byte[] movementData = new byte[] {1, 0, 10, 0};

        when(agent.getId()).thenReturn(123);
        when(agent.getMap()).thenReturn(map);

        try (MockedStatic<PacketCreator> packets = mockStatic(PacketCreator.class)) {
            packets.when(() -> PacketCreator.movePlayer(eq(123), any(InPacket.class), eq((long) movementData.length)))
                    .thenReturn(packet);

            CosmicPacketGateway.INSTANCE.broadcastMovePlayer(agent, movementData);

            packets.verify(() -> PacketCreator.movePlayer(eq(123), any(InPacket.class), eq((long) movementData.length)));
            verify(map).broadcastMessage(agent, packet, false);
        }
    }

    @Test
    void broadcastMovePlayerIgnoresMissingBoundaryObjects() {
        Character agent = mock(Character.class);
        when(agent.getMap()).thenReturn(null);

        try (MockedStatic<PacketCreator> packets = mockStatic(PacketCreator.class)) {
            CosmicPacketGateway.INSTANCE.broadcastMovePlayer(null, new byte[] {1});
            CosmicPacketGateway.INSTANCE.broadcastMovePlayer(agent, null);
            CosmicPacketGateway.INSTANCE.broadcastMovePlayer(agent, new byte[] {1});

            packets.verifyNoInteractions();
        }
    }
}
