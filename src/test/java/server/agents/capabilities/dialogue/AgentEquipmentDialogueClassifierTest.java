package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentEquipmentDialogueClassifierTest {
    @Test
    void shouldParseUnequipSlotCommands() {
        assertEquals("weapon", AgentEquipmentDialogueClassifier.matchUnequipSlotName("unequip weapon"));
        assertEquals("ring 2", AgentEquipmentDialogueClassifier.matchUnequipSlotName("take off ring 2"));
        assertEquals("eye accessory", AgentEquipmentDialogueClassifier.matchUnequipSlotName("remove eye accessory"));
        assertNull(AgentEquipmentDialogueClassifier.matchUnequipSlotName("unequip everything"));
    }

    @Test
    void shouldClassifyUnequipAllCommands() {
        assertTrue(AgentEquipmentDialogueClassifier.isUnequipAllCommand("unequip all"));
        assertTrue(AgentEquipmentDialogueClassifier.isUnequipAllCommand("remove all your gear"));
        assertTrue(AgentEquipmentDialogueClassifier.isUnequipAllCommand("strip down"));
        assertFalse(AgentEquipmentDialogueClassifier.isUnequipAllCommand("unequip weapon"));
    }

    @Test
    void shouldClassifyAutoEquipCommands() {
        assertTrue(AgentEquipmentDialogueClassifier.isAutoEquipCommand("autoequip"));
        assertTrue(AgentEquipmentDialogueClassifier.isAutoEquipCommand("optimize gear"));
        assertTrue(AgentEquipmentDialogueClassifier.isAutoEquipDebugCommand("autoequip debug"));
        assertTrue(AgentEquipmentDialogueClassifier.isAutoEquipDebugCommand("optimize equipment explain"));
        assertTrue(AgentEquipmentDialogueClassifier.isAutoEquipCommand("autoequip debug"));
        assertFalse(AgentEquipmentDialogueClassifier.isAutoEquipCommand("auto attack"));
    }
}
