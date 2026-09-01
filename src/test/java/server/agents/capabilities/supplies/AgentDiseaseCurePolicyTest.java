package server.agents.capabilities.supplies;

import client.Disease;
import client.inventory.Item;
import org.junit.jupiter.api.Test;
import server.StatEffect;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentDiseaseCurePolicyTest {
    @Test
    void prefersTheOwnedItemThatCuresAllActiveDiseases() {
        Item holyWater = item(2_050_003, (short) 2);
        Item allCure = item(2_050_004, (short) 5);
        StatEffect holyWaterEffect = mock(StatEffect.class);
        StatEffect allCureEffect = mock(StatEffect.class);
        when(holyWaterEffect.curesDisease(Disease.SEAL)).thenReturn(true);
        when(allCureEffect.curesDisease(Disease.SEAL)).thenReturn(true);
        when(allCureEffect.curesDisease(Disease.DARKNESS)).thenReturn(true);

        AgentDiseaseCurePolicy.CureChoice choice = AgentDiseaseCurePolicy.select(
                List.of(holyWater, allCure),
                Map.of(2_050_003, holyWaterEffect, 2_050_004, allCureEffect)::get,
                Set.of(Disease.SEAL, Disease.DARKNESS));

        assertEquals(2_050_004, choice.itemId());
        assertEquals((short) 5, choice.slot());
    }

    @Test
    void ignoresDiseasesThatConsumablesCannotCure() {
        assertEquals(0L, AgentDiseaseCurePolicy.signature(Set.of(Disease.STUN)));
    }

    @Test
    void skipsProjectileItemsBeforeLookingUpConsumableEffects() {
        Item arrow = item(2_060_000, (short) 100);
        Item allCure = item(2_050_004, (short) 5);
        StatEffect allCureEffect = mock(StatEffect.class);
        when(allCureEffect.curesDisease(Disease.DARKNESS)).thenReturn(true);

        AgentDiseaseCurePolicy.CureChoice choice = assertDoesNotThrow(() ->
                AgentDiseaseCurePolicy.select(List.of(arrow, allCure), itemId -> {
                    if (itemId == 2_060_000) {
                        throw new NullPointerException("projectiles have no consumable effect");
                    }
                    return allCureEffect;
                }, Set.of(Disease.DARKNESS)));

        assertEquals(2_050_004, choice.itemId());
    }

    private static Item item(int itemId, short slot) {
        Item item = mock(Item.class);
        when(item.getItemId()).thenReturn(itemId);
        when(item.getQuantity()).thenReturn((short) 1);
        when(item.getPosition()).thenReturn(slot);
        return item;
    }
}
