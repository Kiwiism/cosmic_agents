package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentUtilityDialogueClassifierTest {
    @Test
    void shouldClassifyBareTradeInviteWithoutConsumingItemTradeRequests() {
        assertTrue(AgentUtilityDialogueClassifier.isTradeInviteCommand("trade"));
        assertTrue(AgentUtilityDialogueClassifier.isTradeInviteCommand("trade me"));
        assertTrue(AgentUtilityDialogueClassifier.isTradeInviteCommand("trade please!"));
        assertFalse(AgentUtilityDialogueClassifier.isTradeInviteCommand("trade chaos scroll"));
    }

    @Test
    void shouldClassifyShopAndMakerUtilityCommands() {
        assertTrue(AgentUtilityDialogueClassifier.isSellTrashCommand("sell trash"));
        assertTrue(AgentUtilityDialogueClassifier.isSellTrashCommand("vendor my junk"));
        assertFalse(AgentUtilityDialogueClassifier.isSellTrashCommand("trade trash"));

        assertTrue(AgentUtilityDialogueClassifier.isMakeCrystalsCommand("make mob crystals"));
        assertTrue(AgentUtilityDialogueClassifier.isMakeCrystalsCommand("craft some monster crystal"));
        assertFalse(AgentUtilityDialogueClassifier.isMakeCrystalsCommand("make chaos scroll"));

        assertTrue(AgentUtilityDialogueClassifier.isDisassembleTrashCommand("disassemble trash"));
        assertTrue(AgentUtilityDialogueClassifier.isDisassembleTrashCommand("breakdown my junk gear"));
        assertTrue(AgentUtilityDialogueClassifier.isDisassembleTrashCommand("scrap your trash equips"));
        assertFalse(AgentUtilityDialogueClassifier.isDisassembleTrashCommand("sell trash"));
    }
}
