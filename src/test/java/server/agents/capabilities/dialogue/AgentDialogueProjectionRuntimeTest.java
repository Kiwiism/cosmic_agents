package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.supplies.AgentSupplyDialogueReactionService;
import server.agents.progression.events.AgentProgressionDialogueReactionService;
import server.agents.resources.events.AgentResourceDialogueReactionService;

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

    @Test
    void rendersResourceIntentsWithoutExposingTechnicalContext() {
        AgentDialogueIntentEvent inventory = new AgentDialogueIntentEvent(
                1, 100L, AgentResourceDialogueReactionService.INVENTORY_FULL_INTENT,
                AgentDialogueAudience.NEARBY_REAL_PLAYER, "inventory", 1_000L,
                Map.of("inventoryType", "USE"));
        AgentDialogueIntentEvent scroll = new AgentDialogueIntentEvent(
                1, 100L, AgentResourceDialogueReactionService.SCROLL_INTENT,
                AgentDialogueAudience.NEARBY_REAL_PLAYER, "scroll", 1_000L,
                Map.of("result", "SUCCESS"));

        assertEquals("use inventory is full!", AgentDialogueProjectionRuntime.render(inventory));
        assertEquals("the scroll worked!", AgentDialogueProjectionRuntime.render(scroll));
    }
}
