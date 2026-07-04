package server.agents.capabilities.supplies;

import client.BotClient;
import client.Character;
import client.Client;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotPotionStateRuntime;
import server.agents.runtime.AgentRuntimeConfig;
import server.agents.runtime.AgentRuntimeRegistry;
import server.bots.BotEntry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentPotionCheckRequestServiceTest {
    @Test
    void ignoresNonAgentCharacters() {
        Character player = mock(Character.class);
        when(player.getClient()).thenReturn(mock(Client.class));
        AgentRuntimeRegistry.entriesByLeaderId().clear();

        AgentPotionCheckRequestService.requestPotionCheckSoon(player);

        assertEquals(0, AgentRuntimeRegistry.entriesByLeaderId().size());
    }

    @Test
    void requestsPotionCheckSoonForActiveAgentEntry() {
        Character leader = mock(Character.class);
        Character agent = mock(Character.class);
        BotEntry entry = new BotEntry(agent, leader, null);
        int oldDelay = AgentRuntimeConfig.cfg.POT_CHECK_RETRY_SOON_MS;

        when(leader.getId()).thenReturn(77);
        when(agent.getId()).thenReturn(88);
        when(agent.getClient()).thenReturn(new BotClient(0, 0));
        AgentRuntimeRegistry.entriesByLeaderId().clear();
        AgentRuntimeRegistry.entriesByLeaderId().put(leader.getId(), List.of(entry));
        AgentRuntimeConfig.cfg.POT_CHECK_RETRY_SOON_MS = 123;

        try {
            AgentPotionCheckRequestService.requestPotionCheckSoon(agent);

            assertEquals(123, AgentBotPotionStateRuntime.potCheckTimerMs(entry));
        } finally {
            AgentRuntimeConfig.cfg.POT_CHECK_RETRY_SOON_MS = oldDelay;
            AgentRuntimeRegistry.entriesByLeaderId().clear();
        }
    }
}
