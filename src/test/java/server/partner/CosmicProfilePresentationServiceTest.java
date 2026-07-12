package server.partner;

import client.Character;
import client.Client;
import client.Job;
import client.MonsterBook;
import config.YamlConfig;
import net.opcodes.SendOpcode;
import net.packet.Packet;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import server.maps.MapleMap;
import tools.PacketCreator;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CosmicProfilePresentationServiceTest {
    @Test
    void preparedSnapshotsAreDiscardedAtSessionEnd() throws Exception {
        CosmicProfilePresentationService presentation = CosmicProfilePresentationService.INSTANCE;
        Character first = character(910_001, "CacheA");
        Character second = character(910_002, "CacheB");
        int initialSize = preparedProfileCount(presentation);

        presentation.prepare(first, second);

        assertEquals(initialSize + 2, preparedProfileCount(presentation));

        presentation.discardPrepared(first, second);

        assertEquals(initialSize, preparedProfileCount(presentation));
    }

    @Test
    void doubleRefreshSendsDeterministicLocalPacketsAndUpdatesBothPublicActors() throws Exception {
        CosmicProfilePresentationService presentation = CosmicProfilePresentationService.INSTANCE;
        Character human = character(920_001, "PublicA");
        Character partner = character(920_002, "PublicB");
        Client humanClient = mock(Client.class);
        Client partnerClient = mock(Client.class);
        MapleMap map = mock(MapleMap.class);
        human.setClient(humanClient);
        partner.setClient(partnerClient);
        human.setMap(map);
        partner.setMap(map);
        when(map.broadcastUpdateCharLookMessage(any(Character.class), any(Character.class)))
                .thenReturn(new MapleMap.PacketBroadcastMetrics(2, 64L));
        boolean previousPublicPresentation = YamlConfig.config.adventurerPartner.publicPresentation;
        YamlConfig.config.adventurerPartner.publicPresentation = true;
        try {
            presentation.prepare(human, partner);
            Character.ProfileExchangeResult exchange =
                    Character.exchangeProfileBindings(human, partner);

            ProfilePresentationService.RefreshMetrics metrics = presentation.refresh(
                    human, partner, PartnerMode.DOUBLE_PARTNER, exchange);

            ArgumentCaptor<Packet> packets = ArgumentCaptor.forClass(Packet.class);
            verify(humanClient, atLeastOnce()).sendPacket(packets.capture());
            List<Packet> sent = packets.getAllValues();
            List<Integer> opcodes = sent.stream().map(packet -> opcode(packet.getBytes())).toList();
            assertEquals(SendOpcode.STAT_CHANGED.getValue(), opcodes.getFirst());
            assertTrue(opcodes.indexOf(SendOpcode.KEYMAP.getValue())
                    < opcodes.indexOf(SendOpcode.QUICKSLOT_INIT.getValue()));
            assertTrue(opcodes.indexOf(SendOpcode.QUICKSLOT_INIT.getValue())
                    < opcodes.indexOf(SendOpcode.MACRO_SYS_DATA_INIT.getValue()));
            assertTrue(opcodes.indexOf(SendOpcode.MACRO_SYS_DATA_INIT.getValue())
                    < opcodes.indexOf(SendOpcode.INVENTORY_GROW.getValue()));
            assertArrayEquals(PacketCreator.enableActions().getBytes(), sent.getLast().getBytes());
            verify(map).broadcastUpdateCharLookMessage(human, human);
            verify(map).broadcastUpdateCharLookMessage(partner, partner);
            assertEquals(sent.size() + 4, metrics.packetCount());
            assertTrue(metrics.packetBytes() > 0L);
        } finally {
            YamlConfig.config.adventurerPartner.publicPresentation = previousPublicPresentation;
            presentation.discardPrepared(human, partner);
        }
    }

    private static int opcode(byte[] packet) {
        return Byte.toUnsignedInt(packet[0]) | Byte.toUnsignedInt(packet[1]) << 8;
    }

    private static int preparedProfileCount(CosmicProfilePresentationService presentation)
            throws Exception {
        var field = CosmicProfilePresentationService.class.getDeclaredField("preparedByProfileOwner");
        field.setAccessible(true);
        return ((Map<?, ?>) field.get(presentation)).size();
    }

    private static Character character(int id, String name) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("id")).thenReturn(id);
        when(rs.getInt("accountid")).thenReturn(1);
        when(rs.getString("name")).thenReturn(name);
        when(rs.getInt("level")).thenReturn(20);
        when(rs.getInt("job")).thenReturn(Job.WARRIOR.getId());
        when(rs.getInt("skincolor")).thenReturn(0);
        when(rs.getInt("str")).thenReturn(12);
        when(rs.getInt("dex")).thenReturn(5);
        when(rs.getInt("int")).thenReturn(4);
        when(rs.getInt("luk")).thenReturn(4);
        when(rs.getInt("hp")).thenReturn(50);
        when(rs.getInt("maxhp")).thenReturn(50);
        when(rs.getInt("mp")).thenReturn(5);
        when(rs.getInt("maxmp")).thenReturn(5);
        when(rs.getString("sp")).thenReturn("0,0,0,0,0,0,0,0,0,0");
        when(rs.getByte("world")).thenReturn((byte) 0);
        Character character = Character.loadCharacterEntryFromDB(rs, null);
        var monsterBook = Character.class.getDeclaredField("monsterbook");
        monsterBook.setAccessible(true);
        monsterBook.set(character, new MonsterBook());
        return character;
    }
}
