package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.supplies.AgentSupplyDialogueReactionService;
import server.agents.progression.events.AgentProgressionDialogueReactionService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDialogueProjectionRuntimeTest {
    @Test
    void rendersSupportedSupplyIntentFromStructuredParameters() {
        AgentDialogueIntentEvent intent = new AgentDialogueIntentEvent(
                1,
                100L,
                AgentSupplyDialogueReactionService.INTENT_KEY,
                AgentDialogueAudience.NEARBY_REAL_PLAYER,
                "supply:HP_POTION",
                1_000L,
                Map.of("category", "HP_POTION", "urgency", "CRITICAL"));

        assertFalse(AgentDialogueProjectionRuntime.render(intent).isBlank());
    }

    @Test
    void ignoresMalformedOrUnknownIntentInsteadOfProjectingRawData() {
        AgentDialogueIntentEvent malformed = new AgentDialogueIntentEvent(
                1,
                100L,
                AgentSupplyDialogueReactionService.INTENT_KEY,
                AgentDialogueAudience.NEARBY_REAL_PLAYER,
                "malformed",
                1_000L,
                Map.of("category", "UNKNOWN", "urgency", "CRITICAL"));

        assertTrue(AgentDialogueProjectionRuntime.render(malformed).isBlank());
    }

    @Test
    void rendersProgressionIntentsWithoutExposingTechnicalContext() {
        AgentDialogueIntentEvent level = new AgentDialogueIntentEvent(
                1, 100L, AgentProgressionDialogueReactionService.LEVEL_INTENT,
                AgentDialogueAudience.NEARBY_REAL_PLAYER, "level", 1_000L,
                Map.of("level", "15"));
        AgentDialogueIntentEvent quest = new AgentDialogueIntentEvent(
                1, 100L, AgentProgressionDialogueReactionService.QUEST_INTENT,
                AgentDialogueAudience.NEARBY_REAL_PLAYER, "quest", 1_000L,
                Map.of("questId", "1001"));

        assertTrue(AgentDialogueProjectionRuntime.render(level).contains("15"));
        assertEquals("quest complete!", AgentDialogueProjectionRuntime.render(quest));
    }
}
