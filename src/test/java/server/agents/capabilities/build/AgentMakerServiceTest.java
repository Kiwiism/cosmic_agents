package server.agents.capabilities.build;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentMakerRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.lang.reflect.Field;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentMakerServiceTest {
    @Test
    void makeCrystalsBusyReplyUsesAgentReplyAdapter() throws Exception {
        Character bot = mock(Character.class);
        when(bot.getId()).thenReturn(100);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        Set<Integer> active = activeMakerSet();
        active.add(100);

        try (MockedStatic<AgentMakerRuntime> replies = mockStatic(AgentMakerRuntime.class)) {
            AgentMakerService.handleMakeCrystals(entry);

            replies.verify(() -> AgentMakerRuntime.replyNow(entry, "still working on the last batch, hang on"));
        } finally {
            active.remove(100);
        }
    }

    @Test
    void disassembleTrashBusyReplyUsesAgentReplyAdapter() throws Exception {
        Character bot = mock(Character.class);
        when(bot.getId()).thenReturn(200);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        Set<Integer> active = activeMakerSet();
        active.add(200);

        try (MockedStatic<AgentMakerRuntime> replies = mockStatic(AgentMakerRuntime.class)) {
            AgentMakerService.handleDisassembleTrash(entry);

            replies.verify(() -> AgentMakerRuntime.replyNow(entry, "still working on the last batch, hang on"));
        } finally {
            active.remove(200);
        }
    }

    @SuppressWarnings("unchecked")
    private static Set<Integer> activeMakerSet() throws ReflectiveOperationException {
        Field active = AgentMakerService.class.getDeclaredField("ACTIVE");
        active.setAccessible(true);
        return (Set<Integer>) active.get(null);
    }
}
