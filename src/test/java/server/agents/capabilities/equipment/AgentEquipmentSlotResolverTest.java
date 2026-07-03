package server.agents.capabilities.equipment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

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
}
