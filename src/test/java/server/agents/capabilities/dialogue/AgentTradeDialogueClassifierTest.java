package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentTradeDialogueClassifierTest {
    @Test
    void shouldParseTradeMesosCategories() {
        assertEquals("mesos", AgentTradeDialogueClassifier.matchTradeCategory("trade mesos"));
        assertEquals("mesos", AgentTradeDialogueClassifier.matchTradeCategory("trade me all your mesos"));
        assertEquals("mesos:1000000", AgentTradeDialogueClassifier.matchTradeCategory("trade 1m mesos"));
        assertEquals("mesos:1250000", AgentTradeDialogueClassifier.matchTradeCategory("trade 1,250,000 mesos"));
        assertEquals("mesos:1500000", AgentTradeDialogueClassifier.matchTradeCategory("trade 1.5m mesos"));
        assertEquals("mesos:5000000", AgentTradeDialogueClassifier.matchTradeCategory("give me 5m"));
        assertEquals("mesos:200000", AgentTradeDialogueClassifier.matchTradeCategory("gimme 200000"));
        assertEquals("mesos:10000000", AgentTradeDialogueClassifier.matchTradeCategory("give meso 10m"));
        assertEquals("mesos:10000000", AgentTradeDialogueClassifier.matchTradeCategory("trade 10m"));
    }

    @Test
    void shouldParseTradeCategoriesAndNamedItems() {
        assertEquals("name:chaos scroll", AgentTradeDialogueClassifier.matchTradeCategory("trade chaos scroll"));
        assertEquals("name:chaos scroll", AgentTradeDialogueClassifier.matchTradeCategory("trade chaos scrolls"));
        assertEquals("name:hat", AgentTradeDialogueClassifier.matchTradeCategory("show me your hat"));
        assertEquals("name:ring 2", AgentTradeDialogueClassifier.matchTradeCategory("let me see ur ring 2"));
        assertEquals("name:weapon", AgentTradeDialogueClassifier.matchTradeCategory("can i c yo weapon"));
        assertEquals("recommended", AgentTradeDialogueClassifier.matchTradeCategory("trade recommended gear"));
        assertEquals("ammo", AgentTradeDialogueClassifier.matchTradeCategory("trade me your arrows"));
        assertEquals("equips:reserved:1", AgentTradeDialogueClassifier.matchTradeCategory("trade reserve"));
        assertEquals("equips:reserved:12", AgentTradeDialogueClassifier.matchTradeCategory("trade me your reserve 12"));
        assertEquals("trash", AgentTradeDialogueClassifier.matchTradeCategory("show ur junk"));
        assertNull(AgentTradeDialogueClassifier.matchTradeCategory("sell trash"));
    }

    @Test
    void shouldParseChoiceAndItemQueries() {
        assertEquals("name:flaming feather", AgentTradeDialogueClassifier.matchChoiceCategory("give me flaming feather"));
        assertEquals("name:flaming feather", AgentTradeDialogueClassifier.matchChoiceCategory("give flaming feather"));
        assertEquals("name:warrior potion", AgentTradeDialogueClassifier.matchChoiceCategory("drop warrior potions?"));
        assertEquals("warrior potion", AgentTradeDialogueClassifier.matchItemQuery("anybody got warrior potions?"));
        assertNull(AgentTradeDialogueClassifier.matchItemQuery("got trash?"));
        assertNull(AgentTradeDialogueClassifier.matchItemQuery("got pot?"));
    }
}
