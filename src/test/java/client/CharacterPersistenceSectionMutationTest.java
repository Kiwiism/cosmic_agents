package client;

import client.inventory.InventoryType;
import client.inventory.Inventory;
import client.inventory.Equip;
import client.inventory.Item;
import client.inventory.Pet;
import client.keybind.KeyBinding;
import constants.id.MapId;
import org.junit.jupiter.api.Test;
import server.maps.MapObjectType;
import server.persistence.DirtySectionTracker;
import server.quest.Quest;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.awt.Point;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

class CharacterPersistenceSectionMutationTest {
    @Test
    void representativeMutationsDirtyEveryPersistenceSection() throws Exception {
        Character character = newCharacter();
        DirtySectionTracker<Character.PersistenceSection> tracker = tracker(character);

        clear(tracker);
        character.addFame(1);
        assertOnlyDirty(tracker, Character.PersistenceSection.STATS);

        clear(tracker);
        character.setPosition(new Point(10, 20));
        assertOnlyDirty(tracker, Character.PersistenceSection.STATS);

        Mount mount = new Mount(character, 1902000, 1004);
        setField(character, "maplemount", mount);
        attachChildren(character);
        clear(tracker);
        mount.setExp(1);
        assertOnlyDirty(tracker, Character.PersistenceSection.STATS);

        clear(tracker);
        character.getInventory(InventoryType.USE).addItem(new Item(2000000, (short) 0, (short) 1));
        assertOnlyDirty(tracker, Character.PersistenceSection.INVENTORY);

        Item useItem = character.getInventory(InventoryType.USE).findById(2000000);
        clear(tracker);
        useItem.setQuantity((short) 2);
        assertOnlyDirty(tracker, Character.PersistenceSection.INVENTORY);

        Equip equip = equipWithoutMetadata(1002000, (short) 0);
        character.getInventory(InventoryType.EQUIP).addItem(equip);
        clear(tracker);
        equip.setStr((short) 5);
        assertOnlyDirty(tracker, Character.PersistenceSection.INVENTORY);

        clear(tracker);
        character.getInventory(InventoryType.USE).setSlotLimit(30);
        assertEquals(EnumSet.of(Character.PersistenceSection.INVENTORY, Character.PersistenceSection.STATS),
                tracker.dirtySnapshot());

        clear(tracker);
        Inventory replacement = new Inventory(character, InventoryType.USE, (byte) 30);
        character.setInventory(InventoryType.USE, replacement);
        assertEquals(EnumSet.of(Character.PersistenceSection.INVENTORY, Character.PersistenceSection.STATS),
                tracker.dirtySnapshot());
        clear(tracker);
        replacement.addItem(new Item(2000001, (short) 0, (short) 1));
        assertOnlyDirty(tracker, Character.PersistenceSection.INVENTORY);

        clear(tracker);
        Skill skill = mock(Skill.class);
        when(skill.getId()).thenReturn(1000);
        character.changeSkillLevel(skill, (byte) 1, 0, -1);
        assertOnlyDirty(tracker, Character.PersistenceSection.SKILLS);

        clear(tracker);
        Quest quest = mock(Quest.class);
        when(quest.getId()).thenReturn((short) 1000);
        QuestStatus questStatus = new QuestStatus(quest, QuestStatus.Status.NOT_STARTED);
        attachQuestStatus(character, questStatus);
        questStatus.setCustomData("changed");
        assertOnlyDirty(tracker, Character.PersistenceSection.QUESTS);

        clear(tracker);
        BuddyList buddies = new BuddyList(20);
        setField(character, "buddylist", buddies);
        attachChildren(character);
        buddies.put(new BuddylistEntry("friend", "Default Group", 7, -1, true));
        assertOnlyDirty(tracker, Character.PersistenceSection.SOCIAL);

        clear(tracker);
        character.changeKeybinding(2, new KeyBinding(1, 100));
        assertOnlyDirty(tracker, Character.PersistenceSection.KEYMAP);

        trockMaps(character).add(MapId.NONE);
        clear(tracker);
        character.addTrockMap();
        assertOnlyDirty(tracker, Character.PersistenceSection.LOCATIONS);

        clear(tracker);
        character.addPet(newPet());
        assertOnlyDirty(tracker, Character.PersistenceSection.PETS);

        clear(tracker);
        character.setUsedStorage();
        assertOnlyDirty(tracker, Character.PersistenceSection.RELATED);

        FamilyEntry familyEntry = new FamilyEntry(mock(Family.class), 1, "Family", 1, Job.BEGINNER);
        familyEntry.setCharacter(character);
        clear(tracker);
        familyEntry.setReputation(1);
        assertOnlyDirty(tracker, Character.PersistenceSection.RELATED);
    }

    @Test
    void familyAcknowledgementClearsOnlyTheSavedReputationVersion() {
        FamilyEntry familyEntry = new FamilyEntry(mock(Family.class), 1, "Family", 1, Job.BEGINNER);
        familyEntry.setReputation(1);
        long firstVersion = familyEntry.getReputationVersion();
        familyEntry.setReputation(2);

        familyEntry.savedSuccessfully(firstVersion);
        assertTrue(familyEntry.hasUnsavedReputation());

        familyEntry.savedSuccessfully(familyEntry.getReputationVersion());
        assertFalse(familyEntry.hasUnsavedReputation());
    }

    @Test
    void familyDatabaseFailureKeepsReputationUnsaved() throws SQLException {
        FamilyEntry familyEntry = new FamilyEntry(mock(Family.class), 1, "Family", 1, Job.BEGINNER);
        familyEntry.setReputation(1);
        Connection connection = mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenThrow(new SQLException("write failed"));

        assertFalse(familyEntry.saveReputation(connection));
        assertTrue(familyEntry.hasUnsavedReputation());
    }

    @Test
    void fullyMergedInventoryItemStopsDirtyingItsFormerOwner() throws Exception {
        Character character = newCharacter();
        DirtySectionTracker<Character.PersistenceSection> tracker = tracker(character);
        Inventory inventory = character.getInventory(InventoryType.USE);
        Item source = new Item(2000000, (short) 0, (short) 2);
        Item target = new Item(2000000, (short) 0, (short) 3);
        short sourceSlot = inventory.addItem(source);
        short targetSlot = inventory.addItem(target);
        clear(tracker);

        inventory.move(sourceSlot, targetSlot, (short) 100);

        assertOnlyDirty(tracker, Character.PersistenceSection.INVENTORY);
        assertEquals(5, target.getQuantity());
        clear(tracker);

        source.setQuantity((short) 9);

        assertTrue(tracker.dirtySnapshot().isEmpty());
    }

    @Test
    void spOnlyMutationDispatchesPersistentStatUpdate() {
        AtomicInteger statUpdates = new AtomicInteger();
        TestCharacterObject character = new TestCharacterObject(statUpdates);

        character.gainSp(1, 0, true);

        assertEquals(1, statUpdates.get());
    }

    private static final class TestCharacterObject extends AbstractCharacterObject {
        private TestCharacterObject(AtomicInteger statUpdates) {
            setListener(new AbstractCharacterListener() {
                @Override public void onHpChanged(int oldHp) {}
                @Override public void onHpmpPoolUpdate() {}
                @Override public void onStatUpdate() { statUpdates.incrementAndGet(); }
                @Override public void onAnnounceStatPoolUpdate() {}
            });
        }

        @Override public MapObjectType getType() { return MapObjectType.PLAYER; }
        @Override public void sendSpawnData(Client client) {}
        @Override public void sendDestroyData(Client client) {}
    }

    private static Character newCharacter() throws Exception {
        Constructor<Character> constructor = Character.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        Character character = constructor.newInstance();
        setField(character, "client", mock(Client.class));
        return character;
    }

    private static Pet newPet() throws Exception {
        Constructor<Pet> constructor = Pet.class.getDeclaredConstructor(int.class, short.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(5000000, (short) 1, 1);
    }

    private static Equip equipWithoutMetadata(int itemId, short position) throws Exception {
        Constructor<Equip> constructor = Equip.class.getDeclaredConstructor(
                int.class, short.class, int.class, boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(itemId, position, 0, false);
    }

    @SuppressWarnings("unchecked")
    private static DirtySectionTracker<Character.PersistenceSection> tracker(Character character) throws Exception {
        Field field = Character.class.getDeclaredField("persistenceDirty");
        field.setAccessible(true);
        return (DirtySectionTracker<Character.PersistenceSection>) field.get(character);
    }

    private static void attachQuestStatus(Character character, QuestStatus status) throws Exception {
        Method method = Character.class.getDeclaredMethod("attachQuestStatus", QuestStatus.class);
        method.setAccessible(true);
        method.invoke(character, status);
    }

    private static void attachChildren(Character character) throws Exception {
        Method method = Character.class.getDeclaredMethod("attachPersistenceChildren");
        method.setAccessible(true);
        method.invoke(character);
    }

    private static void setField(Character character, String name, Object value) throws Exception {
        Field field = Character.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(character, value);
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> trockMaps(Character character) throws Exception {
        Field field = Character.class.getDeclaredField("trockmaps");
        field.setAccessible(true);
        return (List<Integer>) field.get(character);
    }

    private static void clear(DirtySectionTracker<Character.PersistenceSection> tracker) {
        tracker.complete(tracker.plan(true));
    }

    private static void assertOnlyDirty(DirtySectionTracker<Character.PersistenceSection> tracker,
                                        Character.PersistenceSection expected) {
        assertEquals(EnumSet.of(expected), tracker.dirtySnapshot());
    }
}
