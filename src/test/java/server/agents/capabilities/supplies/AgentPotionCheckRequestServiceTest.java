package server.agents.capabilities.supplies;

import client.BotClient;
import client.Character;
import client.Client;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentPotionStateRuntime;
import server.agents.runtime.AgentRuntimeConfig;
import server.agents.runtime.AgentRuntimeHandle;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentPotionCheckRequestRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentPotionCheckRequestServiceTest {
    @Test
    void serviceIgnoresNullAgent() {
        List<String> calls = new ArrayList<>();

        AgentPotionCheckRequestService.requestPotionCheckSoon(null, hooks(new TestHandle(), calls));

        assertEquals(List.of(), calls);
    }

    @Test
    void serviceRequestsPotionCheckSoonThroughHooks() {
        Character agent = mock(Character.class);
        TestHandle handle = new TestHandle();
        int oldDelay = AgentRuntimeConfig.cfg.POT_CHECK_RETRY_SOON_MS;
        List<String> calls = new ArrayList<>();
        AgentRuntimeConfig.cfg.POT_CHECK_RETRY_SOON_MS = 123;

        try {
            AgentPotionCheckRequestService.requestPotionCheckSoon(agent, hooks(handle, calls));

            assertEquals(List.of("resolve", "request:123"), calls);
        } finally {
            AgentRuntimeConfig.cfg.POT_CHECK_RETRY_SOON_MS = oldDelay;
        }
    }

    @Test
    void runtimeIgnoresNonAgentCharacters() {
        Character player = mock(Character.class);
        when(player.getClient()).thenReturn(mock(Client.class));
        AgentRuntimeRegistry.entriesByLeaderId().clear();

        AgentPotionCheckRequestRuntime.requestPotionCheckSoon(player);

        assertEquals(0, AgentRuntimeRegistry.entriesByLeaderId().size());
    }

    @Test
    void runtimeRequestsPotionCheckSoonForActiveAgentEntry() {
        Character leader = mock(Character.class);
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, leader, null);
        int oldDelay = AgentRuntimeConfig.cfg.POT_CHECK_RETRY_SOON_MS;

        when(leader.getId()).thenReturn(77);
        when(agent.getId()).thenReturn(88);
        when(agent.getClient()).thenReturn(new BotClient(0, 0));
        AgentRuntimeRegistry.entriesByLeaderId().clear();
        AgentRuntimeRegistry.entriesByLeaderId().put(leader.getId(), List.of(entry));
        AgentRuntimeConfig.cfg.POT_CHECK_RETRY_SOON_MS = 123;

        try {
            AgentPotionCheckRequestRuntime.requestPotionCheckSoon(agent);

            assertEquals(123, AgentPotionStateRuntime.potCheckTimerMs(entry));
        } finally {
            AgentRuntimeConfig.cfg.POT_CHECK_RETRY_SOON_MS = oldDelay;
            AgentRuntimeRegistry.entriesByLeaderId().clear();
        }
    }

    private static AgentPotionCheckRequestService.Hooks<TestHandle> hooks(TestHandle resolved, List<String> calls) {
        return new AgentPotionCheckRequestService.Hooks<>(
                agent -> {
                    calls.add("resolve");
                    return resolved;
                },
                (entry, delay) -> calls.add("request:" + delay));
    }

    private static final class TestHandle implements AgentRuntimeHandle {
    }
}
