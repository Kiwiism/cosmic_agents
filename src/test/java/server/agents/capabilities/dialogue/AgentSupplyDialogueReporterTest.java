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

    @Test
    void shouldFormatGrindStartPotionWarningsLikeLegacyChat() {
        assertEquals("ok", AgentSupplyDialogueReporter.grindStartMessage("ok", 100, 100, 100));
        assertEquals("ok, but only 3 HP pots left",
                AgentSupplyDialogueReporter.grindStartMessage("ok", 3, 100, 100));
        assertEquals("ok, but only 4 MP pots left",
                AgentSupplyDialogueReporter.grindStartMessage("ok", 100, 4, 100));
        assertEquals("ok, but only 3 HP pots and only 4 MP pots left",
                AgentSupplyDialogueReporter.grindStartMessage("ok", 3, 4, 100));
    }

    @Test
    void shouldFormatAutopotDebugReportLikeLegacyChat() {
        assertEquals(
                "pots: 12 hp / 8 mp | hp slot: Red Potion (FLAT_SINGLE/50) | mp slot: Blue Potion (RATE_SINGLE/30%)",
                AgentSupplyDialogueReporter.autopotDebugReport(
                        12,
                        8,
                        AgentSupplyDialogueReporter.autopotChoice("Red Potion", 2000000, "FLAT_SINGLE", 50.0d),
                        AgentSupplyDialogueReporter.autopotChoice("Blue Potion", 2000003, "RATE_SINGLE", 0.30d)));
    }

    @Test
    void shouldFormatMissingAutopotChoiceAsNone() {
        assertEquals("none", AgentSupplyDialogueReporter.autopotChoice(null, 0, null, 0));
        assertEquals("2000000 (FLAT_MIXED/100)",
                AgentSupplyDialogueReporter.autopotChoice(null, 2000000, "FLAT_MIXED", 100.0d));
    }
}
