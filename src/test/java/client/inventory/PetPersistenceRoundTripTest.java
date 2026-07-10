package client.inventory;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PetPersistenceRoundTripTest {
    @Test
    void petRoundTripsEveryPersistedColumn() {
        Pet.PersistenceSnapshot snapshot = new Pet.PersistenceSnapshot(
                "Jr.", 12, 3456, 78, true, Pet.PetAttribute.OWNER_SPEED.getValue(), 9001);

        Pet restored = snapshot.restore(5000000, (short) 3);

        assertEquals("Jr.", restored.getName());
        assertEquals(12, restored.getLevel());
        assertEquals(3456, restored.getTameness());
        assertEquals(78, restored.getFullness());
        assertTrue(restored.isSummoned());
        assertEquals(Pet.PetAttribute.OWNER_SPEED.getValue(), restored.getPetAttribute());
        assertEquals(9001, restored.getUniqueId());
    }

    @Test
    void missingPetRowFailsTheEnclosingTransaction() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(0);
        Pet.PersistenceSnapshot snapshot = new Pet.PersistenceSnapshot(
                "Jr.", 1, 0, 100, false, 0, 9001);

        assertThrows(SQLException.class, () -> snapshot.saveToDb(connection));
    }
}
