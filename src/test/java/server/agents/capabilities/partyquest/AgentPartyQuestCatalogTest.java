package server.agents.capabilities.partyquest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPartyQuestCatalogTest {
    @Test
    void catalogsLocalEntryContractsWithoutSharingStageBehavior() {
        assertEquals(List.of("hpq", "kpq", "lpq", "opq"),
                AgentPartyQuestCatalog.definitions().stream()
                        .map(AgentPartyQuestDefinition::questKey).toList());

        AgentPartyQuestDefinition hpq = AgentPartyQuestCatalog.require(" HPQ ");
        assertEquals("HenesysPQ", hpq.eventManagerName());
        assertEquals(100_000_200, hpq.recruitMapId());
        assertEquals(910_010_000, hpq.entryMapId());
        assertTrue(hpq.acceptsPartySize(3));
        assertTrue(hpq.acceptsPartySize(6));
        assertFalse(hpq.acceptsPartySize(2));

        AgentPartyQuestDefinition lpq = AgentPartyQuestCatalog.require("lpq");
        assertTrue(lpq.acceptsLevel(35));
        assertTrue(lpq.acceptsLevel(50));
        assertFalse(lpq.acceptsLevel(51));

        AgentPartyQuestDefinition opq = AgentPartyQuestCatalog.require("opq");
        assertEquals(200_080_101, opq.recoveryMapId());
    }

    @Test
    void routingExposesOnlySystemsThatHaveAnImplementation() {
        assertEquals("hpq", AgentPartyQuestRuntime.requireSystem("hpq").definition().questKey());
        assertEquals("kpq", AgentPartyQuestRuntime.requireSystem("kpq").definition().questKey());
        assertEquals("lpq", AgentPartyQuestRuntime.requireSystem("lpq").definition().questKey());
        assertThrows(IllegalArgumentException.class,
                () -> AgentPartyQuestRuntime.requireSystem("opq"));
    }

    @Test
    void rejectsInvalidDefinitions() {
        assertThrows(IllegalArgumentException.class, () -> new AgentPartyQuestDefinition(
                "bad", "BadPQ", 1, 2, 3, 4, 5, 50, 40, 4, 3));
        assertThrows(IllegalArgumentException.class,
                () -> AgentPartyQuestCatalog.require("unknown"));
    }
}
