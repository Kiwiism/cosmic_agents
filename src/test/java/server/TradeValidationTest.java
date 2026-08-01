package server;

import client.Character;
import client.inventory.Item;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeValidationTest {
    @Test
    void negativeMesoOfferCannotCreditOrDebitTheCharacter() {
        Character player = mock(Character.class);
        Trade trade = new Trade((byte) 0, player);

        trade.setMeso(-1);

        assertFalse(trade.hasAnyOffer());
        verify(player, never()).gainMeso(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void duplicateInventorySlotCannotBeAddedTwice() {
        Trade trade = new Trade((byte) 0, mock(Character.class));

        assertTrue(trade.addItem(new Item(2000000, (short) 1, (short) 1)));
        assertFalse(trade.addItem(new Item(2000001, (short) 1, (short) 1)));
        assertEquals(1, trade.getItems().size());
    }

    @Test
    void tradeWindowCannotContainMoreThanNineItems() {
        Trade trade = new Trade((byte) 0, mock(Character.class));
        for (short position = 1; position <= 9; position++) {
            assertTrue(trade.addItem(new Item(2000000 + position, position, (short) 1)));
        }

        assertFalse(trade.addItem(new Item(2000010, (short) 10, (short) 1)));
        assertEquals(9, trade.getItems().size());
    }

    @Test
    void feeCalculationUsesLongArithmeticAtMaximumMeso() {
        assertEquals(128_849_018, Trade.getFee(Integer.MAX_VALUE));
    }
}
