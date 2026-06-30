package server.agents.capabilities.build;

import client.Character;
import client.Job;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotBuildStatusRuntime;
import server.agents.capabilities.build.AgentBuildService;
import server.bots.BotEntry;
import server.bots.BotEquipManager;

import java.util.List;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentStarterKitServiceTest {
    @Test
    void shouldExposeExplorerFirstJobStarterKits() {
        assertEquals(List.of(new AgentStarterItemGrant(1302077, (short) 1)),
                AgentStarterKitService.starterKitFor(Job.WARRIOR));
        assertEquals(List.of(new AgentStarterItemGrant(1372043, (short) 1)),
                AgentStarterKitService.starterKitFor(Job.MAGICIAN));
        assertEquals(List.of(
                        new AgentStarterItemGrant(1452051, (short) 1),
                        new AgentStarterItemGrant(2060000, (short) 1000)),
                AgentStarterKitService.starterKitFor(Job.BOWMAN));
        assertEquals(List.of(
                        new AgentStarterItemGrant(1472061, (short) 1),
                        new AgentStarterItemGrant(1332063, (short) 1),
                        new AgentStarterItemGrant(2070015, (short) 500)),
                AgentStarterKitService.starterKitFor(Job.THIEF));
        assertEquals(List.of(
                        new AgentStarterItemGrant(1492000, (short) 1),
                        new AgentStarterItemGrant(1482000, (short) 1),
                        new AgentStarterItemGrant(2330000, (short) 1000)),
                AgentStarterKitService.starterKitFor(Job.PIRATE));
    }

    @Test
    void shouldOnlyGrantKitsForBeginnerToFirstJobAdvancements() {
        assertTrue(AgentStarterKitService.isFirstJobAdvancement(Job.BEGINNER, Job.WARRIOR));
        assertTrue(AgentStarterKitService.isFirstJobAdvancement(Job.BEGINNER, Job.MAGICIAN));
        assertFalse(AgentStarterKitService.isFirstJobAdvancement(Job.WARRIOR, Job.FIGHTER));
        assertFalse(AgentStarterKitService.isFirstJobAdvancement(Job.BEGINNER, Job.FIGHTER));
    }

    @Test
    void advanceJobAlwaysReevaluatesAutoEquip() {
        Character bot = mock(Character.class);
        Character owner = mock(Character.class);
        BotEntry entry = new BotEntry(bot, owner, mock(ScheduledFuture.class));

        when(bot.getJob()).thenReturn(Job.BOWMAN);

        try (MockedStatic<AgentBuildService> buildManager = mockStatic(AgentBuildService.class);
             MockedStatic<AgentBotBuildStatusRuntime> statusRuntime = mockStatic(AgentBotBuildStatusRuntime.class);
             MockedStatic<BotEquipManager> equipManager = mockStatic(BotEquipManager.class)) {
            AgentStarterKitService.advanceJob(entry, Job.HUNTER);

            verify(bot).changeJob(Job.HUNTER);
            buildManager.verify(() -> AgentBuildService.handleJobAdvance(entry, bot, Job.BOWMAN, Job.HUNTER));
            equipManager.verify(() -> BotEquipManager.autoEquip(bot, owner, null));
            statusRuntime.verify(() -> AgentBotBuildStatusRuntime.checkBuildStatus(entry, bot));
        }
    }
}
