package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentSupplyDialogueReporterTest {
    @Test
    void shouldFormatPotionCountsLikeLegacyChat() {
        assertEquals("I have 2 hp pots and 3 mp pots", AgentSupplyDialogueReporter.potionReport(2, 3));
        assertEquals("I have 1 hp pot, no mp pots", AgentSupplyDialogueReporter.potionReport(new int[]{1, 0}));
        assertEquals("no hp pots, 1 mp pot", AgentSupplyDialogueReporter.potionReport(new int[]{0, 1}));
        assertEquals("no pots on me rn", AgentSupplyDialogueReporter.potionReport(new int[]{0, 0}));
    }

    @Test
    void shouldTreatMissingCountArrayAsEmptyPotions() {
        assertEquals("no pots on me rn", AgentSupplyDialogueReporter.potionReport(null));
        assertEquals("I have 4 hp pots, no mp pots", AgentSupplyDialogueReporter.potionReport(new int[]{4}));
    }
}
