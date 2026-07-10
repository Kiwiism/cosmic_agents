package server;

import client.inventory.Item;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CashShopPersistenceDirtyTest {
    @Test
    void persistedCashShopMutationsNotifyTheOwner() {
        CashShop cashShop = new CashShop(1, 2);
        AtomicInteger dirtySignals = new AtomicInteger();
        cashShop.setPersistenceDirtyMarker(dirtySignals::incrementAndGet);

        cashShop.gainCash(CashShop.NX_CREDIT, 100);
        Item item = new Item(2000000, (short) 1, (short) 1);
        cashShop.addToInventory(item);
        item.setQuantity((short) 2);
        cashShop.addToWishList(1000);
        cashShop.clearWishList();
        cashShop.removeFromInventory(item);
        item.setQuantity((short) 3);

        assertEquals(6, dirtySignals.get());
        assertThrows(UnsupportedOperationException.class, () -> cashShop.getInventory().add(item));
        assertThrows(UnsupportedOperationException.class, () -> cashShop.getWishList().add(2000));
    }

    @Test
    void missingAccountRowFailsTheCharacterTransaction() throws SQLException {
        CashShop cashShop = new CashShop(1, 2);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(0);

        assertThrows(SQLException.class, () -> cashShop.save(connection));
    }
}
