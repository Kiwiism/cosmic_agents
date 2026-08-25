package server.agents.capabilities.partyquest.hpq;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentHpqDefinitionTest {
    @Test
    void mapsEverySeedToItsOwnMoonflowerReactor() {
        assertEquals(List.of(4_001_095, 4_001_096, 4_001_097, 4_001_098, 4_001_099, 4_001_100),
                AgentHpqDefinition.seedBeds().stream()
                        .map(AgentHpqDefinition.SeedBed::seedItemId).toList());
        assertEquals(List.of(9_108_000, 9_108_001, 9_108_002, 9_108_003, 9_108_004, 9_108_005),
                AgentHpqDefinition.seedBeds().stream()
                        .map(AgentHpqDefinition.SeedBed::reactorId).toList());
        assertEquals("moonflower1", AgentHpqDefinition.seedBed(4_001_095).reactorName());
        assertTrue(AgentHpqDefinition.isSeed(4_001_100));
        assertThrows(IllegalArgumentException.class,
                () -> AgentHpqDefinition.seedBed(AgentHpqDefinition.RICE_CAKE));
    }
}
