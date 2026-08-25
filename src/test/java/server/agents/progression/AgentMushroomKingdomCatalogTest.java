package server.agents.progression;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentMushroomKingdomCatalogTest {
    @Test
    void explorerEntryQuestsCoverAllFiveFamilies() {
        assertEquals(2300, AgentMushroomKingdomCatalog.entryQuestForJob(110));
        assertEquals(2301, AgentMushroomKingdomCatalog.entryQuestForJob(230));
        assertEquals(2302, AgentMushroomKingdomCatalog.entryQuestForJob(410));
        assertEquals(2303, AgentMushroomKingdomCatalog.entryQuestForJob(320));
        assertEquals(2304, AgentMushroomKingdomCatalog.entryQuestForJob(520));
        assertEquals(102000003, AgentMushroomKingdomCatalog.entryLeaderMap(2300));
        assertEquals(120000101, AgentMushroomKingdomCatalog.entryLeaderMap(2304));
        assertTrue(AgentMushroomKingdomCatalog.supportedSecondJob(110));
        assertTrue(AgentMushroomKingdomCatalog.supportedSecondJob(520));
        assertFalse(AgentMushroomKingdomCatalog.supportedSecondJob(100));
        assertFalse(AgentMushroomKingdomCatalog.supportedSecondJob(111));
    }

    @Test
    void mainlineIsUniqueAndEndsAtTruthQuest() {
        var nodes = AgentMushroomKingdomCatalog.mainline();
        Set<Integer> ids = new HashSet<>();
        nodes.forEach(node -> assertTrue(ids.add(node.questId()),
                () -> "duplicate quest " + node.questId()));
        assertEquals(AgentMushroomKingdomCatalog.FINAL_QUEST_ID,
                nodes.getLast().questId());
        assertTrue(ids.containsAll(Set.of(2320, 2325, 2326, 2327, 2328, 2329)),
                "non-repeatable side branches belong to the full test journey");
    }

    @Test
    void poisonCapsRemainBeforeTheFollowupBarrierMaterialQuest() {
        var ids = AgentMushroomKingdomCatalog.mainline().stream()
                .map(AgentMushroomKingdomCatalog.QuestNode::questId).toList();
        assertTrue(ids.indexOf(2317) < ids.indexOf(2318));
        assertEquals(106020300, AgentMushroomKingdomCatalog.require(2317).huntMapId());
    }
}
