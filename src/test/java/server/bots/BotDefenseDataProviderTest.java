package server.bots;

import client.Job;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.combat.data.AgentDefenseDataProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentDefenseDataProviderTest {
    private final AgentDefenseDataProvider provider = AgentDefenseDataProvider.getInstance();

    @Test
    void shouldFloorWarriorLevelLookupToLowerBucket() {
        assertEquals(57, provider.getStandardPdd(Job.WARRIOR, 14));
    }

    @Test
    void shouldCapLookupsAboveLevel100() {
        assertEquals(309, provider.getStandardPdd(Job.PIRATE, 150));
    }

    @Test
    void shouldPreserveExactTableValues() {
        assertEquals(57, provider.getStandardPdd(Job.WARRIOR, 13));
        assertEquals(116, provider.getStandardPdd(Job.THIEF, 33));
        assertEquals(57, provider.getStandardPdd(Job.WARRIOR, 12));
        assertEquals(85, provider.getStandardPdd(Job.THIEF, 22));
        assertEquals(356, provider.getStandardPdd(Job.WARRIOR, 75));
        assertEquals(331, provider.getStandardPdd(Job.THIEF, 100));
        assertEquals(266, provider.getStandardPdd(Job.MAGICIAN, 100));
    }

    @Test
    void shouldMapCygnusAndAranFamiliesToBaseTables() {
        assertEquals(54, provider.getStandardPdd(Job.DAWNWARRIOR1, 10));
        assertEquals(31, provider.getStandardPdd(Job.BLAZEWIZARD1, 10));
        assertEquals(298, provider.getStandardPdd(Job.WINDARCHER4, 100));
        assertEquals(42, provider.getStandardPdd(Job.NIGHTWALKER1, 10));
        assertEquals(24, provider.getStandardPdd(Job.THUNDERBREAKER1, 10));
        assertEquals(54, provider.getStandardPdd(Job.ARAN1, 10));
        assertEquals(25, provider.getStandardPdd(Job.EVAN1, 8));
    }
}
