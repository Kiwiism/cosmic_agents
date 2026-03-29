package server.bots;

import client.Job;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotStarterKitManagerTest {
    @Test
    void shouldExposeExplorerFirstJobStarterKits() {
        assertEquals(List.of(new BotStarterKitManager.ItemGrant(1302077, (short) 1)),
                BotStarterKitManager.starterKitFor(Job.WARRIOR));
        assertEquals(List.of(new BotStarterKitManager.ItemGrant(1372043, (short) 1)),
                BotStarterKitManager.starterKitFor(Job.MAGICIAN));
        assertEquals(List.of(
                        new BotStarterKitManager.ItemGrant(1452051, (short) 1),
                        new BotStarterKitManager.ItemGrant(2060000, (short) 1000)),
                BotStarterKitManager.starterKitFor(Job.BOWMAN));
        assertEquals(List.of(
                        new BotStarterKitManager.ItemGrant(1472061, (short) 1),
                        new BotStarterKitManager.ItemGrant(1332063, (short) 1),
                        new BotStarterKitManager.ItemGrant(2070015, (short) 500)),
                BotStarterKitManager.starterKitFor(Job.THIEF));
        assertEquals(List.of(
                        new BotStarterKitManager.ItemGrant(1492000, (short) 1),
                        new BotStarterKitManager.ItemGrant(1482000, (short) 1),
                        new BotStarterKitManager.ItemGrant(2330000, (short) 1000)),
                BotStarterKitManager.starterKitFor(Job.PIRATE));
    }

    @Test
    void shouldOnlyGrantKitsForBeginnerToFirstJobAdvancements() {
        assertTrue(BotStarterKitManager.isFirstJobAdvancement(Job.BEGINNER, Job.WARRIOR));
        assertTrue(BotStarterKitManager.isFirstJobAdvancement(Job.BEGINNER, Job.MAGICIAN));
        assertFalse(BotStarterKitManager.isFirstJobAdvancement(Job.WARRIOR, Job.FIGHTER));
        assertFalse(BotStarterKitManager.isFirstJobAdvancement(Job.BEGINNER, Job.FIGHTER));
    }
}
