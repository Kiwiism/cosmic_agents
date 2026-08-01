package net.server.channel.handlers;

import client.inventory.Item;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MTSValidationTest {
    @Test
    void listingMustMatchTheOwnedSlotItemAndQuantity() {
        Item item = new Item(2000000, (short) 3, (short) 10);

        assertTrue(MTSHandler.isValidListingRequest(2000000, (short) 10, 110, 10, item));
        assertFalse(MTSHandler.isValidListingRequest(2000001, (short) 10, 110, 10, item));
        assertFalse(MTSHandler.isValidListingRequest(2000000, (short) 11, 110, 10, item));
        assertFalse(MTSHandler.isValidListingRequest(2000000, (short) 1, 109, 10, item));
        assertFalse(MTSHandler.isValidListingRequest(2000000, (short) 0, 110, 10, item));
        assertFalse(MTSHandler.isValidListingRequest(2000000, (short) 1, 110, 10, null));
    }
}
