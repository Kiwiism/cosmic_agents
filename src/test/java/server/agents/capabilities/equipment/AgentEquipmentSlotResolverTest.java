package server.agents.capabilities.equipment;

import client.inventory.Equip;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentEquipmentSlotResolverTest {
    @Test
    void shouldResolveLegacySlotAliases() {
        assertArrayEquals(new short[]{-1}, AgentEquipmentSlotResolver.slotsFromName("helm"));
        assertArrayEquals(new short[]{-5}, AgentEquipmentSlotResolver.slotsFromName("over all"));
        assertArrayEquals(new short[]{-10}, AgentEquipmentSlotResolver.slotsFromName("offhand"));
        assertArrayEquals(new short[]{-11}, AgentEquipmentSlotResolver.slotsFromName("wep"));
        assertArrayEquals(new short[]{-49}, AgentEquipmentSlotResolver.slotsFromName("medal"));
        assertArrayEquals(new short[0], AgentEquipmentSlotResolver.slotsFromName("not-a-slot"));
    }

    @Test
    void shouldReturnFreshRingSlotArray() {
        short[] first = AgentEquipmentSlotResolver.slotsFromName("ring");
        short[] second = AgentEquipmentSlotResolver.slotsFromName("ring");

        assertArrayEquals(new short[]{-12, -13, -15, -16}, first);
        assertArrayEquals(first, second);
        assertNotSame(first, second);
    }

    @Test
    void shouldRecognizeLegacyRingSlots() {
        assertTrue(AgentEquipmentSlotResolver.isRingSlot((short) -12));
        assertTrue(AgentEquipmentSlotResolver.isRingSlot((short) -16));
        assertFalse(AgentEquipmentSlotResolver.isRingSlot((short) -11));
    }

    @Test
    void shouldBuildDpSlotsWithExpandedRingSlotsInDescendingOrder() {
        Map<Short, List<Equip>> bySlot = Map.of(
                (short) -11, List.of(),
                (short) -12, List.of(mock(Equip.class)),
                (short) -8, List.of(),
                (short) -5, List.of());
        Map<Short, Equip> currentBySlot = new HashMap<>();
        currentBySlot.put((short) -9, null);

        assertEquals(List.of((short) -5, (short) -8, (short) -9,
                        (short) -12, (short) -13, (short) -15, (short) -16),
                AgentEquipmentSlotResolver.buildDpSlots(bySlot, currentBySlot));
    }

    @Test
    void shouldFormatLegacySlotLabels() {
        assertEquals("weapon", AgentEquipmentSlotResolver.slotLabel((short) -11));
        assertEquals("ring", AgentEquipmentSlotResolver.slotLabel((short) -15));
        assertEquals("slot -49", AgentEquipmentSlotResolver.slotLabel((short) -49));
    }

}
