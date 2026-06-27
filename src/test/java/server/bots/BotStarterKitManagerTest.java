package server.bots;

import client.Character;
import client.Job;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotBuildStatusRuntime;

import java.util.List;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void advanceJobAlwaysReevaluatesAutoEquip() {
        Character bot = mock(Character.class);
        Character owner = mock(Character.class);
        BotEntry entry = new BotEntry(bot, owner, mock(ScheduledFuture.class));

        when(bot.getJob()).thenReturn(Job.BOWMAN);

        try (MockedStatic<BotBuildManager> buildManager = mockStatic(BotBuildManager.class);
             MockedStatic<AgentBotBuildStatusRuntime> statusRuntime = mockStatic(AgentBotBuildStatusRuntime.class);
             MockedStatic<BotEquipManager> equipManager = mockStatic(BotEquipManager.class)) {
            BotStarterKitManager.advanceJob(entry, Job.HUNTER);

            verify(bot).changeJob(Job.HUNTER);
            buildManager.verify(() -> BotBuildManager.handleJobAdvance(entry, bot, Job.BOWMAN, Job.HUNTER));
            equipManager.verify(() -> BotEquipManager.autoEquip(bot, owner, null));
            statusRuntime.verify(() -> AgentBotBuildStatusRuntime.checkBuildStatus(entry, bot));
        }
    }
}
