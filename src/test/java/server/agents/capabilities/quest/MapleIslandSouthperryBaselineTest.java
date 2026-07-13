package server.agents.capabilities.quest;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class MapleIslandSouthperryBaselineTest {
    @Test
    void loadsCapturedAmherstRunCompletionState() {
        MapleIslandSouthperryBaseline.Snapshot snapshot = MapleIslandSouthperryBaseline.snapshot();

        assertEquals(1, snapshot.schemaVersion());
        assertEquals("AmherstRun", snapshot.sourceCharacterName());
        assertEquals(1000000, snapshot.character().mapId());
        assertEquals(6, snapshot.character().level());
        assertEquals(79, snapshot.character().exp());
        assertEquals(37, snapshot.character().str());
        assertEquals(121, snapshot.character().maxHp());
        assertEquals(58, snapshot.character().maxMp());
        assertEquals(224, snapshot.character().mesos());
        assertEquals(0, snapshot.character().remainingAp());
        assertEquals(0, snapshot.character().jobId());
        assertEquals(5, snapshot.character().dex());
        assertEquals(4, snapshot.character().intelligence());
        assertEquals(4, snapshot.character().luk());
        assertEquals(121, snapshot.character().hp());
        assertEquals(58, snapshot.character().mp());
        assertEquals(0, snapshot.character().skinColorId());
        assertEquals(1, snapshot.character().gender());
        assertEquals(31000, snapshot.character().hair());
        assertEquals(21001, snapshot.character().face());
        assertArrayEquals(new int[10], snapshot.character().remainingSp());
        assertEquals(List.of(
                item(1302000, "EQUIPPED", -11, 1),
                item(1072005, "EQUIPPED", -7, 1),
                item(1061002, "EQUIPPED", -6, 1),
                item(1041011, "EQUIPPED", -5, 1),
                item(1002068, "EQUIP", 1, 1),
                item(2010000, "USE", 1, 3),
                item(2010009, "USE", 2, 3),
                item(2022253, "USE", 3, 2),
                item(2000000, "USE", 4, 5),
                item(2000003, "USE", 5, 6),
                item(3010000, "SETUP", 1, 1),
                item(4000019, "ETC", 1, 6)), snapshot.items());
        assertEquals(Set.of(
                1008, 1009, 1010, 1011, 1012, 1013, 1014, 1015, 1020,
                1021, 1031, 1032, 1033, 1034, 1035, 1036, 1037, 1038),
                snapshot.completedQuestIds());
        assertTrue(snapshot.completedQuestIds().containsAll(AmherstQuestCatalog.requiredQuestIdSet()));
        assertTrue(snapshot.resetQuestIds().contains(1046));
        assertTrue(snapshot.resetQuestIds().contains(1045));
        assertTrue(snapshot.resetQuestIds().contains(1028));
    }

    private static MapleIslandSouthperryBaseline.ItemState item(
            int itemId, String inventoryType, int position, int quantity) {
        return new MapleIslandSouthperryBaseline.ItemState(
                itemId, inventoryType, (short) position, (short) quantity);
    }
}
