package server.partner;

import client.Character;
import client.BuffStat;
import client.Client;
import client.Job;
import client.MonsterBook;
import client.QuestStatus;
import client.Skill;
import config.YamlConfig;
import net.opcodes.SendOpcode;
import net.packet.Packet;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import server.maps.MapleMap;
import server.quest.Quest;
import server.StatEffect;
import tools.PacketCreator;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CosmicProfilePresentationServiceTest {
    @Test
    void soloSharedBuffSkillIsRegisteredOnceAndRemovedWhenSessionEnds() throws Exception {
        CosmicProfilePresentationService presentation = CosmicProfilePresentationService.INSTANCE;
        Character human = character(890_001, "OverlayA");
        Character partner = character(890_002, "OverlayB");
        Client humanClient = mock(Client.class);
        human.setClient(humanClient);
        int shadowPartner = 4111002;
        attachSkill(human, shadowPartner, 30, 0, -1L);

        boolean previousPublicPresentation = YamlConfig.config.adventurerPartner.PUBLIC_PRESENTATION;
        YamlConfig.config.adventurerPartner.PUBLIC_PRESENTATION = false;
        try {
            presentation.prepare(human, partner);
            Character.ProfileExchangeResult firstExchange =
                    Character.exchangeProfileBindings(human, partner);
            attachBuff(human, shadowPartner, BuffStat.SHADOWPARTNER, 50);

            presentation.refresh(human, partner, PartnerMode.SOLO_TAG, firstExchange);

            ArgumentCaptor<Packet> firstPackets = ArgumentCaptor.forClass(Packet.class);
            verify(humanClient, atLeastOnce()).sendPacket(firstPackets.capture());
            List<Packet> sent = firstPackets.getAllValues();
            int skillIndex = packetIndex(sent, PacketCreator.updateSkill(
                    shadowPartner, 30, 0, -1L));
            int buffIndex = firstOpcodeIndex(sent, SendOpcode.GIVE_BUFF);
            assertTrue(skillIndex >= 0);
            assertTrue(buffIndex > skillIndex);
            assertFalse(human.getSkills().keySet().stream()
                    .anyMatch(skill -> skill.getId() == shadowPartner));

            org.mockito.Mockito.clearInvocations(humanClient);
            Character.ProfileExchangeResult secondExchange =
                    Character.exchangeProfileBindings(human, partner);
            presentation.refresh(human, partner, PartnerMode.SOLO_TAG, secondExchange);
            org.mockito.Mockito.clearInvocations(humanClient);
            Character.ProfileExchangeResult thirdExchange =
                    Character.exchangeProfileBindings(human, partner);
            presentation.refresh(human, partner, PartnerMode.SOLO_TAG, thirdExchange);

            ArgumentCaptor<Packet> activePackets = ArgumentCaptor.forClass(Packet.class);
            verify(humanClient, atLeastOnce()).sendPacket(activePackets.capture());
            assertEquals(-1, packetIndex(activePackets.getAllValues(),
                    PacketCreator.updateSkill(shadowPartner, -1, 0, -1L)));

            org.mockito.Mockito.clearInvocations(humanClient);
            presentation.clearTemporarySkills(human);

            ArgumentCaptor<Packet> releasePacket = ArgumentCaptor.forClass(Packet.class);
            verify(humanClient).sendPacket(releasePacket.capture());
            assertArrayEquals(PacketCreator.updateSkill(shadowPartner, -1, 0, -1L).getBytes(),
                    releasePacket.getValue().getBytes());
        } finally {
            YamlConfig.config.adventurerPartner.PUBLIC_PRESENTATION = previousPublicPresentation;
            presentation.clearTemporarySkills(human);
            presentation.discardPrepared(human, partner);
        }
    }

    @Test
    void partnerRefreshOnlySendsSkillRecordsThatDifferBetweenProfiles() throws Exception {
        CosmicProfilePresentationService presentation = CosmicProfilePresentationService.INSTANCE;
        Character human = character(900_001, "SkillA");
        Character partner = character(900_002, "SkillB");
        Client humanClient = mock(Client.class);
        human.setClient(humanClient);

        attachSkill(human, 1000000, 1, 0, -1L);       // unchanged
        attachSkill(partner, 1000000, 1, 0, -1L);
        attachSkill(human, 1000001, 1, 0, -1L);       // removed
        attachSkill(partner, 1000002, 1, 0, -1L);     // added
        attachSkill(human, 1000003, 1, 0, -1L);       // level changed
        attachSkill(partner, 1000003, 2, 0, -1L);
        attachSkill(human, 1000004, 1, 10, -1L);      // master level changed
        attachSkill(partner, 1000004, 1, 20, -1L);
        attachSkill(human, 1000005, 1, 0, 100L);      // expiration changed
        attachSkill(partner, 1000005, 1, 0, 200L);

        boolean previousPublicPresentation = YamlConfig.config.adventurerPartner.PUBLIC_PRESENTATION;
        YamlConfig.config.adventurerPartner.PUBLIC_PRESENTATION = false;
        try {
            presentation.prepare(human, partner);
            Character.ProfileExchangeResult exchange =
                    Character.exchangeProfileBindings(human, partner);

            presentation.refresh(human, partner, PartnerMode.SOLO_TAG, exchange);

            ArgumentCaptor<Packet> packets = ArgumentCaptor.forClass(Packet.class);
            verify(humanClient, atLeastOnce()).sendPacket(packets.capture());
            List<byte[]> skillPackets = packets.getAllValues().stream()
                    .filter(packet -> opcode(packet.getBytes()) == SendOpcode.UPDATE_SKILLS.getValue())
                    .map(Packet::getBytes)
                    .toList();
            assertEquals(5, skillPackets.size());
            assertTrue(skillPackets.stream().anyMatch(bytes -> java.util.Arrays.equals(
                    bytes, PacketCreator.updateSkill(1000001, -1, 0, -1L).getBytes())));
            assertTrue(skillPackets.stream().anyMatch(bytes -> java.util.Arrays.equals(
                    bytes, PacketCreator.updateSkill(1000002, 1, 0, -1L).getBytes())));
            assertTrue(skillPackets.stream().anyMatch(bytes -> java.util.Arrays.equals(
                    bytes, PacketCreator.updateSkill(1000003, 2, 0, -1L).getBytes())));
            assertTrue(skillPackets.stream().anyMatch(bytes -> java.util.Arrays.equals(
                    bytes, PacketCreator.updateSkill(1000004, 1, 20, -1L).getBytes())));
            assertTrue(skillPackets.stream().anyMatch(bytes -> java.util.Arrays.equals(
                    bytes, PacketCreator.updateSkill(1000005, 1, 0, 200L).getBytes())));
            assertFalse(skillPackets.stream().anyMatch(bytes -> java.util.Arrays.equals(
                    bytes, PacketCreator.updateSkill(1000000, 1, 0, -1L).getBytes())));
        } finally {
            YamlConfig.config.adventurerPartner.PUBLIC_PRESENTATION = previousPublicPresentation;
            presentation.discardPrepared(human, partner);
        }
    }

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
        Quest quest = mock(Quest.class);
        when(quest.getId()).thenReturn((short) 1000);
        attachQuest(partner, new QuestStatus(quest, QuestStatus.Status.COMPLETED));
        addMonsterBookCard(partner, 2380000, 1);
        when(map.broadcastUpdateCharLookMessage(any(Character.class), any(Character.class)))
                .thenReturn(new MapleMap.PacketBroadcastMetrics(2, 64L));
        boolean previousPublicPresentation = YamlConfig.config.adventurerPartner.PUBLIC_PRESENTATION;
        YamlConfig.config.adventurerPartner.PUBLIC_PRESENTATION = true;
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
            assertTrue(sent.stream().anyMatch(packet -> java.util.Arrays.equals(
                    packet.getBytes(), PacketCreator.showSpecialEffect(8).getBytes())));
            assertFalse(opcodes.contains(SendOpcode.SHOW_STATUS_INFO.getValue()));
            assertFalse(opcodes.contains(SendOpcode.MONSTER_BOOK_SET_CARD.getValue()));
            assertFalse(opcodes.contains(SendOpcode.MONSTER_BOOK_SET_COVER.getValue()));
            assertFalse(opcodes.contains(SendOpcode.UPDATE_QUEST_INFO.getValue()));
            assertEquals(1, human.getQuestStatusesSnapshot().size());
            assertEquals(1, human.getMonsterBook().getCards().get(2380000));
            verify(map).broadcastUpdateCharLookMessage(human, human);
            verify(map).broadcastUpdateCharLookMessage(partner, partner);
            verify(map).broadcastMessage(eq(human), any(Packet.class), eq(false));
            verify(map).broadcastMessage(eq(partner), any(Packet.class), eq(false));
            assertEquals(sent.size() + 6, metrics.packetCount());
            assertTrue(metrics.packetBytes() > 0L);
        } finally {
            YamlConfig.config.adventurerPartner.PUBLIC_PRESENTATION = previousPublicPresentation;
            presentation.discardPrepared(human, partner);
        }
    }

    private static int opcode(byte[] packet) {
        return Byte.toUnsignedInt(packet[0]) | Byte.toUnsignedInt(packet[1]) << 8;
    }

    private static int firstOpcodeIndex(List<Packet> packets, SendOpcode opcode) {
        for (int index = 0; index < packets.size(); index++) {
            if (opcode(packets.get(index).getBytes()) == opcode.getValue()) {
                return index;
            }
        }
        return -1;
    }

    private static int packetIndex(List<Packet> packets, Packet expected) {
        for (int index = 0; index < packets.size(); index++) {
            if (java.util.Arrays.equals(packets.get(index).getBytes(), expected.getBytes())) {
                return index;
            }
        }
        return -1;
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

    @SuppressWarnings("unchecked")
    private static void attachSkill(Character character, int skillId, int level,
                                    int masterLevel, long expiration) throws Exception {
        var field = Character.class.getDeclaredField("skills");
        field.setAccessible(true);
        ((Map<Skill, Character.SkillEntry>) field.get(character)).put(
                new Skill(skillId),
                new Character.SkillEntry((byte) level, masterLevel, expiration));
    }

    @SuppressWarnings("unchecked")
    private static void attachBuff(Character character, int sourceId,
                                   BuffStat stat, int value) throws Exception {
        StatEffect effect = mock(StatEffect.class);
        when(effect.getBuffSourceId()).thenReturn(sourceId);
        long now = System.currentTimeMillis();
        Class<?> holderType = Class.forName("client.Character$BuffStatValueHolder");
        var constructor = holderType.getDeclaredConstructor(
                StatEffect.class, long.class, int.class);
        constructor.setAccessible(true);
        Object holder = constructor.newInstance(effect, now, value);
        var effectsField = Character.class.getDeclaredField("buffEffects");
        effectsField.setAccessible(true);
        ((Map<Integer, Map<BuffStat, Object>>) effectsField.get(character)).put(
                sourceId, Map.of(stat, holder));
        var expirationsField = Character.class.getDeclaredField("buffExpires");
        expirationsField.setAccessible(true);
        ((Map<Integer, Long>) expirationsField.get(character)).put(sourceId, now + 30_000L);
    }

    @SuppressWarnings("unchecked")
    private static void attachQuest(Character character, QuestStatus status) throws Exception {
        var field = Character.class.getDeclaredField("quests");
        field.setAccessible(true);
        ((Map<Short, QuestStatus>) field.get(character)).put(status.getQuestID(), status);
    }

    @SuppressWarnings("unchecked")
    private static void addMonsterBookCard(Character character, int cardId, int count) throws Exception {
        var cards = MonsterBook.class.getDeclaredField("cards");
        cards.setAccessible(true);
        ((Map<Integer, Integer>) cards.get(character.getMonsterBook())).put(cardId, count);
    }
}
