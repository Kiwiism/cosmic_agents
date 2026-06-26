package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentChatCommandClassifierTest {
    @Test
    void shouldParseFollowTargetCommandsWithoutBreakingPlainFollow() {
        assertEquals("clawer", AgentChatCommandClassifier.matchFollowTarget("follow clawer"));
        assertEquals("Clawer", AgentChatCommandClassifier.matchFollowTarget("follow Clawer please"));
        assertNull(AgentChatCommandClassifier.matchFollowTarget("follow me"));
        assertNull(AgentChatCommandClassifier.matchFollowTarget("follow here"));
        assertNull(AgentChatCommandClassifier.matchFollowTarget("follow pls"));
    }

    @Test
    void shouldOnlyMatchMovementModeCommandsAsWholeCommands() {
        assertTrue(AgentChatCommandClassifier.isMoveHereCommand("here"));
        assertTrue(AgentChatCommandClassifier.isMoveHereCommand("move here!"));
        assertFalse(AgentChatCommandClassifier.isMoveHereCommand("some random chat message here"));

        assertTrue(AgentChatCommandClassifier.isGrindCommand("farm"));
        assertTrue(AgentChatCommandClassifier.isGrindCommand("go grind"));
        assertFalse(AgentChatCommandClassifier.isGrindCommand("Im going to the farm today"));

        assertTrue(AgentChatCommandClassifier.isFarmHereCommand("farm here"));
        assertTrue(AgentChatCommandClassifier.isFarmHereCommand("grind here please"));
        assertFalse(AgentChatCommandClassifier.isFarmHereCommand("Im going to farm here today"));

        assertTrue(AgentChatCommandClassifier.isFarmHereCommand("sentry"));
        assertTrue(AgentChatCommandClassifier.isFarmHereCommand("go sentry"));
        assertTrue(AgentChatCommandClassifier.isFarmHereCommand("sentry here"));
        assertTrue(AgentChatCommandClassifier.isFarmHereCommand("sentry mode"));
        assertTrue(AgentChatCommandClassifier.isFarmHereCommand("go sentry mode"));
        assertTrue(AgentChatCommandClassifier.isFarmHereCommand("camp"));
        assertTrue(AgentChatCommandClassifier.isFarmHereCommand("camp here"));
        assertTrue(AgentChatCommandClassifier.isFarmHereCommand("guard mode"));
        assertTrue(AgentChatCommandClassifier.isFarmHereCommand("go defend mode"));
        assertTrue(AgentChatCommandClassifier.isFarmHereCommand("post up"));
        assertTrue(AgentChatCommandClassifier.isFarmHereCommand("post up here"));
        assertTrue(AgentChatCommandClassifier.isFarmHereCommand("anchor here"));
        assertTrue(AgentChatCommandClassifier.isFarmHereCommand("anchor"));
        assertFalse(AgentChatCommandClassifier.isFarmHereCommand("Im going to camp today"));
        assertFalse(AgentChatCommandClassifier.isFarmHereCommand("setting up camp"));

        assertTrue(AgentChatCommandClassifier.isFidgetCommand("fidget"));
        assertTrue(AgentChatCommandClassifier.isFidgetCommand("fidget!"));
        assertFalse(AgentChatCommandClassifier.isFidgetCommand("please fidget"));
    }

    @Test
    void shouldClassifySupplyRequests() {
        assertTrue(AgentChatCommandClassifier.isNeedPotCommand("nned pot"));
        assertTrue(AgentChatCommandClassifier.isNeedPotCommand("need some pots"));
        assertTrue(AgentChatCommandClassifier.isNeedPotCommand("anybody got pot"));
        assertTrue(AgentChatCommandClassifier.isNeedPotCommand("low on pots"));
        assertTrue(AgentChatCommandClassifier.isNeedHpPotCommand("anyone have hp pots"));
        assertTrue(AgentChatCommandClassifier.isNeedMpPotCommand("running low on mana potions"));
        assertTrue(AgentChatCommandClassifier.isNeedAmmoCommand("anybody got arrows"));
        assertTrue(AgentChatCommandClassifier.isNeedAmmoCommand("low on ammo"));

        assertTrue(AgentChatCommandClassifier.isGroupSupplyRequest("anybody got arrows"));
        assertFalse(AgentChatCommandClassifier.isGroupSupplyRequest("trade mesos"));
    }
}
