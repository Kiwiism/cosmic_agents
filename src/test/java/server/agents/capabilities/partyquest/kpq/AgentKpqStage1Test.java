package server.agents.capabilities.partyquest.kpq;

import client.Character;
import client.inventory.InventoryType;
import org.junit.jupiter.api.Test;
import scripting.event.EventInstanceManager;
import server.agents.integration.InventoryGateway;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentKpqStage1Test {
    @Test
    void exchangeCouponsForPassUsesInventoryGatewayAndResetsGridQuestion() {
        Character bot = mock(Character.class);
        EventInstanceManager eim = mock(EventInstanceManager.class);
        InventoryGateway inventory = mock(InventoryGateway.class);
        when(bot.getItemQuantity(4001007, false)).thenReturn(10);
        when(bot.getEventInstance()).thenReturn(eim);

        assertTrue(AgentKpqStage1.exchangeCouponsForPass(bot, 10, inventory));

        verify(inventory).removeById(bot, InventoryType.ETC, 4001007, 10, false, false);
        verify(inventory).addItem(bot, 4001008, (short) 1);
        verify(eim).gridInsert(bot, 0);
    }

    @Test
    void exchangeCouponsForPassDoesNothingWhenCouponCountIsShort() {
        Character bot = mock(Character.class);
        InventoryGateway inventory = mock(InventoryGateway.class);
        when(bot.getItemQuantity(4001007, false)).thenReturn(9);

        assertFalse(AgentKpqStage1.exchangeCouponsForPass(bot, 10, inventory));

        verify(inventory, never()).removeById(
                bot, InventoryType.ETC, 4001007, 10, false, false);
        verify(inventory, never()).addItem(bot, 4001008, (short) 1);
    }
}
