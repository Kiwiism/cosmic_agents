package client;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuddyListPersistenceDirtyTest {
    @Test
    void listAndEntryMutationsUseTheSameDirtyBoundary() {
        BuddyList buddies = new BuddyList(20);
        BuddylistEntry entry = new BuddylistEntry("friend", "Default Group", 7, -1, true);
        buddies.put(entry);
        AtomicInteger marks = new AtomicInteger();
        buddies.setPersistenceDirtyMarker(marks::incrementAndGet);

        entry.changeGroup("Party");
        entry.setVisible(false);
        buddies.setCapacity(30);
        buddies.remove(7);
        entry.changeGroup("Detached");

        assertEquals(4, marks.get());
    }
}
