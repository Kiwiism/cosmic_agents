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
