package server.agents.field;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentFieldSafeSpotCatalogRepositoryTest {
    @Test
    void providesMobFreeHenesysHayRoofSpotsAndWrapsOrdinal() {
        AgentFieldSafeSpotCatalogRepository repository =
                AgentFieldSafeSpotCatalogRepository.defaultRepository();

        Point first = repository.spot(104040000, 0).orElseThrow();
        Point wrapped = repository.spot(104040000, 7).orElseThrow();

        assertEquals(new Point(1045, 37), first);
        assertEquals(first, wrapped);
        assertNotSame(first, wrapped);
        assertTrue(repository.spot(100000000, 0).isEmpty());
    }
}
