package client;

import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.Equip;
import client.inventory.Pet;
import client.keybind.KeyBinding;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import net.packet.Packet;
import server.TimerManager;
import server.life.MobSkill;
import tools.Pair;
import tools.PacketCreator;

import java.awt.Point;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CharacterProfileExchangeTest {
    @Test
    void partnerSessionSkillMutatesProfileAndSendsNormalSkillPackets() throws Exception {
        Character character = character(10, "Pio", 28, Job.ASSASSIN, 30030);
        Client client = mock(Client.class);
        character.setClient(client);
        Skill shadowPartner = new Skill(4111002);

        character.applyPartnerSessionSkill(10, shadowPartner, (byte) 30, 0, -1L);

        assertEquals(30, character.getSkills().get(shadowPartner).skillevel);
        ArgumentCaptor<Packet> granted = ArgumentCaptor.forClass(Packet.class);
        verify(client).sendPacket(granted.capture());
        assertArrayEquals(
                PacketCreator.updateSkill(4111002, 30, 0, -1L).getBytes(),
                granted.getValue().getBytes());

        org.mockito.Mockito.clearInvocations(client);
        character.restorePartnerSessionSkill(10, shadowPartner, null);

        assertFalse(character.getSkills().containsKey(shadowPartner));
        ArgumentCaptor<Packet> removed = ArgumentCaptor.forClass(Packet.class);
        verify(client).sendPacket(removed.capture());
        assertArrayEquals(
                PacketCreator.updateSkill(4111002, -1, 0, -1L).getBytes(),
                removed.getValue().getBytes());
    }

    @Test
    void exchangesProfileOwnedStateWithoutMovingActors() throws Exception {
        Character first = character(10, "Pio", 28, Job.ASSASSIN, 30030);
        Character second = character(20, "Yoona", 17, Job.MAGICIAN, 20020);
        first.setPosition(new Point(100, 50));
        second.setPosition(new Point(400, 80));
        first.getInventory(InventoryType.USE).addItemFromDB(new Item(2000000, (short) 1, (short) 5));
        second.getInventory(InventoryType.USE).addItemFromDB(new Item(2000001, (short) 1, (short) 7));
        addInventorySentinels(first, 0);
        addInventorySentinels(second, 1);
        first.getInventory(InventoryType.ETC).setSlotLimit(30);
        second.getInventory(InventoryType.ETC).setSlotLimit(60);
        first.changeKeybinding(42, new KeyBinding(1, 1002));
        second.changeKeybinding(42, new KeyBinding(1, 2001002));
        byte[] firstQuickslots = {1, 2, 3, 4, 5, 6, 7, 8};
        byte[] secondQuickslots = {8, 7, 6, 5, 4, 3, 2, 1};
        first.changeQuickslotKeybinding(firstQuickslots);
        second.changeQuickslotKeybinding(secondQuickslots);
        first.updateMacros(0, new SkillMacro(1000, 1001, 1002, "Pio macro", 0, 0));
        second.updateMacros(0, new SkillMacro(2000, 2001, 2002, "Yoona macro", 1, 0));
        first.setClient(mock(Client.class));
        second.setClient(mock(Client.class));
        first.gainMeso(1_000, false);
        second.gainMeso(2_000, false);
        Skill firstSkill = mock(Skill.class);
        Skill secondSkill = mock(Skill.class);
        when(firstSkill.getId()).thenReturn(4_111_001);
        when(secondSkill.getId()).thenReturn(2_101_001);
        setField(first, "skills", new LinkedHashMap<>(Map.of(
                firstSkill, new Character.SkillEntry((byte) 11, 20, -1L))));
        setField(second, "skills", new LinkedHashMap<>(Map.of(
                secondSkill, new Character.SkillEntry((byte) 7, 10, -1L))));
        QuestStatus firstQuest = mock(QuestStatus.class);
        QuestStatus secondQuest = mock(QuestStatus.class);
        questMap(first).put((short) 100, firstQuest);
        questMap(second).put((short) 200, secondQuest);
        MonsterBook firstBook = new MonsterBook();
        MonsterBook secondBook = new MonsterBook();
        setField(first, "monsterbook", firstBook);
        setField(second, "monsterbook", secondBook);
        Pet firstPet = mock(Pet.class);
        Pet secondPet = mock(Pet.class);
        first.addPet(firstPet);
        second.addPet(secondPet);
        first.addCooldown(4_111_001, 1_000L, 60_000L);
        second.addCooldown(2_101_001, 2_000L, 90_000L);
        MobSkill firstDiseaseSkill = mock(MobSkill.class);
        MobSkill secondDiseaseSkill = mock(MobSkill.class);
        first.silentApplyDiseases(Map.of(
                Disease.SLOW, new Pair<>(60_000L, firstDiseaseSkill)));
        second.silentApplyDiseases(Map.of(
                Disease.SEAL, new Pair<>(90_000L, secondDiseaseSkill)));

        Character.ProfileExchangeResult result = Character.exchangeProfileState(first, second, false);

        assertEquals(10, first.getId());
        assertEquals(20, second.getId());
        assertEquals("Pio", first.getName());
        assertEquals("Yoona", second.getName());
        assertEquals(new Point(100, 50), first.getPosition());
        assertEquals(new Point(400, 80), second.getPosition());
        assertEquals(20, first.getProfileOwnerCharacterId());
        assertEquals(10, second.getProfileOwnerCharacterId());
        assertEquals(Job.MAGICIAN, first.getJob());
        assertEquals(Job.ASSASSIN, second.getJob());
        assertEquals(17, first.getLevel());
        assertEquals(28, second.getLevel());
        assertEquals(20020, first.getHair());
        assertEquals(30030, second.getHair());
        assertNotNull(first.getInventory(InventoryType.USE).findById(2000001));
        assertNotNull(second.getInventory(InventoryType.USE).findById(2000000));
        assertInventorySentinels(first, 1);
        assertInventorySentinels(second, 0);
        assertEquals(60, first.getInventory(InventoryType.ETC).getSlotLimit());
        assertEquals(30, second.getInventory(InventoryType.ETC).getSlotLimit());
        assertEquals(2001002, first.getKeymap().get(42).getAction());
        assertEquals(1002, second.getKeymap().get(42).getAction());
        assertArrayEquals(secondQuickslots,
                first.getQuickslotBindingForPresentation().GetKeybindings());
        assertArrayEquals(firstQuickslots,
                second.getQuickslotBindingForPresentation().GetKeybindings());
        assertEquals("Yoona macro", first.getMacros()[0].getName());
        assertEquals("Pio macro", second.getMacros()[0].getName());
        assertEquals(2_000, first.getMeso());
        assertEquals(1_000, second.getMeso());
        assertTrue(first.getSkills().containsKey(secondSkill));
        assertTrue(second.getSkills().containsKey(firstSkill));
        assertSame(secondQuest, first.getQuestStatusesSnapshot().getFirst());
        assertSame(firstQuest, second.getQuestStatusesSnapshot().getFirst());
        assertSame(secondBook, first.getMonsterBook());
        assertSame(firstBook, second.getMonsterBook());
        assertSame(secondPet, first.getPet(0));
        assertSame(firstPet, second.getPet(0));
        assertEquals(2_101_001, first.getAllCooldowns().getFirst().skillId);
        assertEquals(4_111_001, second.getAllCooldowns().getFirst().skillId);
        assertTrue(first.getAllDiseases().containsKey(Disease.SEAL));
        assertTrue(second.getAllDiseases().containsKey(Disease.SLOW));
        assertEquals(20, result.leftProfileOwnerCharacterId());
        assertEquals(10, result.rightProfileOwnerCharacterId());
        assertEquals(1L, result.leftBindingGeneration());
        assertEquals(1L, result.rightBindingGeneration());
    }

    private static void addInventorySentinels(Character character, int suffix) {
        Equip equipped = mock(Equip.class);
        when(equipped.getItemId()).thenReturn(1_001_000 + suffix);
        when(equipped.getPosition()).thenReturn((short) -5);
        character.getInventory(InventoryType.EQUIPPED).addItemFromDB(equipped);
        character.getInventory(InventoryType.EQUIP).addItemFromDB(
                new Item(1000000 + suffix, (short) 2, (short) 1));
        character.getInventory(InventoryType.SETUP).addItemFromDB(
                new Item(3000000 + suffix, (short) 3, (short) 2));
        character.getInventory(InventoryType.ETC).addItemFromDB(
                new Item(4000000 + suffix, (short) 4, (short) 3));
        character.getInventory(InventoryType.CASH).addItemFromDB(
                new Item(5000000 + suffix, (short) 5, (short) 1));
    }

    private static void assertInventorySentinels(Character character, int suffix) {
        assertNotNull(character.getInventory(InventoryType.EQUIPPED).findById(1_001_000 + suffix));
        assertNotNull(character.getInventory(InventoryType.EQUIP).findById(1000000 + suffix));
        assertNotNull(character.getInventory(InventoryType.SETUP).findById(3000000 + suffix));
        assertNotNull(character.getInventory(InventoryType.ETC).findById(4000000 + suffix));
        assertNotNull(character.getInventory(InventoryType.CASH).findById(5000000 + suffix));
    }

    @SuppressWarnings("unchecked")
    private static Map<Short, QuestStatus> questMap(Character character) throws Exception {
        var field = Character.class.getDeclaredField("quests");
        field.setAccessible(true);
        Map<Short, QuestStatus> quests = (Map<Short, QuestStatus>) field.get(character);
        if (quests == null) {
            quests = new LinkedHashMap<>();
            field.set(character, quests);
        }
        return quests;
    }

    private static void setField(Character character, String name, Object value) throws Exception {
        var field = Character.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(character, value);
    }

    @Test
    void secondExchangeRestoresCanonicalOrientation() throws Exception {
        Character first = character(10, "Pio", 28, Job.ASSASSIN, 30030);
        Character second = character(20, "Yoona", 17, Job.MAGICIAN, 20020);

        Character.exchangeProfileState(first, second, false);
        Character.exchangeProfileState(first, second, false);

        assertEquals(10, first.getProfileOwnerCharacterId());
        assertEquals(20, second.getProfileOwnerCharacterId());
        assertEquals(Job.ASSASSIN, first.getJob());
        assertEquals(Job.MAGICIAN, second.getJob());
        assertEquals(2L, first.getProfileBindingGeneration());
        assertEquals(2L, second.getProfileBindingGeneration());
    }

    @Test
    void profileTransitionWindowRejectsNestingAndClearsAfterExit() throws Exception {
        Character character = character(10, "Pio", 28, Job.ASSASSIN, 30030);

        character.enterProfileTransitionWindow();
        try {
            assertTrue(character.isProfileTransitioning());
            assertThrows(IllegalStateException.class, character::enterProfileTransitionWindow);
        } finally {
            character.exitProfileTransitionWindow();
        }

        assertFalse(character.isProfileTransitioning());
        assertThrows(IllegalStateException.class, character::exitProfileTransitionWindow);
    }

    @Test
    void staleCooldownCallbackCannotMutateTheNewlyAttachedProfile() throws Exception {
        Character first = character(10, "Pio", 28, Job.ASSASSIN, 30030);
        Character second = character(20, "Yoona", 17, Job.MAGICIAN, 20020);
        first.setClient(mock(Client.class));
        second.setClient(mock(Client.class));
        first.addCooldown(1001, 0L, 1L);
        second.addCooldown(2001, 0L, 1L);
        TimerManager.getInstance().start();
        try {
            first.skillCooldownTask();

            Character.exchangeProfileBindings(first, second);
            TimeUnit.MILLISECONDS.sleep(2_000L);

            assertTrue(first.skillIsCooling(2001));
            assertTrue(second.skillIsCooling(1001));
        } finally {
            first.cancelSkillCooldownTask();
            second.cancelSkillCooldownTask();
            TimerManager.getInstance().stop();
        }
    }

    private static Character character(int id,
                                       String name,
                                       int level,
                                       Job job,
                                       int hair) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("id")).thenReturn(id);
        when(rs.getInt("accountid")).thenReturn(1);
        when(rs.getString("name")).thenReturn(name);
        when(rs.getInt("level")).thenReturn(level);
        when(rs.getInt("job")).thenReturn(job.getId());
        when(rs.getInt("hair")).thenReturn(hair);
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
        return Character.loadCharacterEntryFromDB(rs, null);
    }
}
