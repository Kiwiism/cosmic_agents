package server.agents.integration;

import client.Character;
import client.inventory.Item;
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

    @Test
    void broadcastCloseRangeAttackBuildsPacketAndBroadcastsThroughMap() {
        Character agent = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        Packet packet = mock(Packet.class);

        when(agent.getMap()).thenReturn(map);

        try (MockedStatic<PacketCreator> packets = mockStatic(PacketCreator.class)) {
            packets.when(() -> PacketCreator.closeRangeAttack(agent, 1, 2, 3, 4, java.util.Map.of(), 5, 6, 7))
                    .thenReturn(packet);

            CosmicPacketGateway.INSTANCE.broadcastCloseRangeAttack(agent, 1, 2, 3, 4, java.util.Map.of(), 5, 6, 7);

            packets.verify(() -> PacketCreator.closeRangeAttack(agent, 1, 2, 3, 4, java.util.Map.of(), 5, 6, 7));
            verify(map).broadcastMessage(agent, packet, false);
        }
    }

    @Test
    void sendRemoveMistBuildsPacketAndSendsToRecipient() {
        Character recipient = mock(Character.class);
        Packet packet = mock(Packet.class);

        try (MockedStatic<PacketCreator> packets = mockStatic(PacketCreator.class)) {
            packets.when(() -> PacketCreator.removeMist(456)).thenReturn(packet);

            CosmicPacketGateway.INSTANCE.sendRemoveMist(recipient, 456);

            packets.verify(() -> PacketCreator.removeMist(456));
            verify(recipient).sendPacket(packet);
        }
    }

    @Test
    void sendRemoveItemFromMapBuildsPacketAndSendsToRecipient() {
        Character recipient = mock(Character.class);
        Packet packet = mock(Packet.class);

        try (MockedStatic<PacketCreator> packets = mockStatic(PacketCreator.class)) {
            packets.when(() -> PacketCreator.removeItemFromMap(7, 1, 0)).thenReturn(packet);

            CosmicPacketGateway.INSTANCE.sendRemoveItemFromMap(recipient, 7, 1, 0);

            packets.verify(() -> PacketCreator.removeItemFromMap(7, 1, 0));
            verify(recipient).sendPacket(packet);
        }
    }

    @Test
    void sendTradeItemAddBuildsPacketAndSendsToRecipient() {
        Character recipient = mock(Character.class);
        Item item = mock(Item.class);
        Packet packet = mock(Packet.class);

        try (MockedStatic<PacketCreator> packets = mockStatic(PacketCreator.class)) {
            packets.when(() -> PacketCreator.getTradeItemAdd((byte) 1, item)).thenReturn(packet);

            CosmicPacketGateway.INSTANCE.sendTradeItemAdd(recipient, (byte) 1, item);

            packets.verify(() -> PacketCreator.getTradeItemAdd((byte) 1, item));
            verify(recipient).sendPacket(packet);
        }
    }

    @Test
    void broadcastDamagePlayerBuildsPacketAndBroadcastsThroughMap() {
        Character agent = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        Packet packet = mock(Packet.class);

        when(agent.getId()).thenReturn(123);
        when(agent.getMap()).thenReturn(map);

        try (MockedStatic<PacketCreator> packets = mockStatic(PacketCreator.class)) {
            packets.when(() -> PacketCreator.damagePlayer(-1, 222, 123, 50, 0, -1, false, 0, false, 0, 0, 0))
                    .thenReturn(packet);

            CosmicPacketGateway.INSTANCE.broadcastDamagePlayer(agent, -1, 222, 50, 0, -1, false, 0, false, 0, 0, 0);

            packets.verify(() -> PacketCreator.damagePlayer(-1, 222, 123, 50, 0, -1, false, 0, false, 0, 0, 0));
            verify(map).broadcastMessage(agent, packet, false);
        }
    }
}
