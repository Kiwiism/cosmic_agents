package server.agents.capabilities.build;

import client.Job;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentStarterKitCatalogTest {
    @Test
    void exposesLegacyExplorerFirstJobStarterKits() {
        assertEquals(List.of(new AgentStarterItemGrant(1302077, (short) 1)),
                AgentStarterKitCatalog.firstJobKitFor(Job.WARRIOR));
        assertEquals(List.of(new AgentStarterItemGrant(1372043, (short) 1)),
                AgentStarterKitCatalog.firstJobKitFor(Job.MAGICIAN));
        assertEquals(List.of(
                        new AgentStarterItemGrant(1452051, (short) 1),
                        new AgentStarterItemGrant(2060000, (short) 1000)),
                AgentStarterKitCatalog.firstJobKitFor(Job.BOWMAN));
        assertEquals(List.of(
                        new AgentStarterItemGrant(1472061, (short) 1),
                        new AgentStarterItemGrant(1332063, (short) 1),
                        new AgentStarterItemGrant(2070015, (short) 500)),
                AgentStarterKitCatalog.firstJobKitFor(Job.THIEF));
        assertEquals(List.of(
                        new AgentStarterItemGrant(1492000, (short) 1),
                        new AgentStarterItemGrant(1482000, (short) 1),
                        new AgentStarterItemGrant(2330000, (short) 1000)),
                AgentStarterKitCatalog.firstJobKitFor(Job.PIRATE));
    }

    @Test
    void onlyBeginnerToExplorerFirstJobUsesStarterKit() {
        assertTrue(AgentStarterKitCatalog.isFirstJobAdvancement(Job.BEGINNER, Job.WARRIOR));
        assertTrue(AgentStarterKitCatalog.isFirstJobAdvancement(Job.BEGINNER, Job.MAGICIAN));
        assertFalse(AgentStarterKitCatalog.isFirstJobAdvancement(Job.WARRIOR, Job.FIGHTER));
        assertFalse(AgentStarterKitCatalog.isFirstJobAdvancement(Job.BEGINNER, Job.FIGHTER));
    }
}
