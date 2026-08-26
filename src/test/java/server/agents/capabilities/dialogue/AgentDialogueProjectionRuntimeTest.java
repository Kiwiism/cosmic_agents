package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;
import server.agents.progression.events.AgentProgressionDialogueReactionService;
import server.agents.progression.events.AgentQuestProgressDialogueReactionService;
import server.agents.resources.events.AgentResourceDialogueReactionService;
import server.agents.operations.events.AgentOperationalDialogueReactionService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDialogueProjectionRuntimeTest {
    @Test
    void suppressesInternalCombatPostureChatEverywhere() {
        AgentDialogueIntentEvent posture = new AgentDialogueIntentEvent(
                1, 1L, AgentFieldNarrationService.POSTURE_INTENT,
                AgentDialogueAudience.NEARBY_REAL_PLAYER, "posture", 0L,
                java.util.Map.of("phase", "KITING"));

        assertFalse(AgentDialogueProjectionRuntime.shouldProject(posture, true));
        assertFalse(AgentDialogueProjectionRuntime.shouldProject(posture, false));
    }

    @Test
    void suppressesGenericDialogueDuringAnActiveHpqSession() {
        AgentDialogueIntentEvent level = new AgentDialogueIntentEvent(
                1, 1L, AgentProgressionDialogueReactionService.LEVEL_INTENT,
                AgentDialogueAudience.NEARBY_REAL_PLAYER, "level", 0L,
                Map.of("level", "30"));

        assertFalse(AgentDialogueProjectionRuntime.shouldProject(level, true));
        assertTrue(AgentDialogueProjectionRuntime.shouldProject(level, false));
    }

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
    void rendersQuestProgressMilestonesWithCounts() {
        AgentDialogueIntentEvent halfway = new AgentDialogueIntentEvent(
                1, 100L, AgentQuestProgressDialogueReactionService.INTENT_KEY,
                AgentDialogueAudience.NEARBY_REAL_PLAYER, "quest-progress", 1_000L,
                Map.of("targetId", "1210100", "currentCount", "15",
                        "targetName", "Ribbon Pig", "requiredCount", "30",
                        "milestonePercent", "50"));
        AgentDialogueIntentEvent nearlyDone = new AgentDialogueIntentEvent(
                1, 100L, AgentQuestProgressDialogueReactionService.INTENT_KEY,
                AgentDialogueAudience.NEARBY_REAL_PLAYER, "quest-progress", 1_000L,
                Map.of("targetId", "4000004", "currentCount", "27",
                        "targetName", "Squishy Liquid", "requiredCount", "30",
                        "milestonePercent", "90"));

        assertEquals("Quest progress: 15/30 Ribbon Pig - halfway there.",
                AgentDialogueProjectionRuntime.render(halfway));
        assertEquals("Quest progress: 27/30 Squishy Liquid - almost done.",
                AgentDialogueProjectionRuntime.render(nearlyDone));
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

    @Test
    void rendersOperationalLifeStateIntent() {
        AgentDialogueIntentEvent respawn = new AgentDialogueIntentEvent(
                1, 100L, AgentOperationalDialogueReactionService.LIFE_STATE_INTENT,
                AgentDialogueAudience.NEARBY_REAL_PLAYER, "respawn", 1_000L,
                Map.of("state", "ALIVE"));

        assertEquals("back!", AgentDialogueProjectionRuntime.render(respawn));
    }
}
