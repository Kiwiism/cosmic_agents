package server.bots;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotReplyRuntime;

import java.lang.reflect.Field;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class BotMakerManagerTest {
    @Test
    void makeCrystalsBusyReplyUsesAgentReplyAdapter() throws Exception {
        Character bot = mock(Character.class);
        when(bot.getId()).thenReturn(100);
        BotEntry entry = new BotEntry(bot, null, null);
        Set<Integer> active = activeMakerSet();
        active.add(100);

        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class)) {
            BotMakerManager.handleMakeCrystals(entry);

            replies.verify(() -> AgentBotReplyRuntime.replyNow(entry, "still working on the last batch, hang on"));
        } finally {
            active.remove(100);
        }
    }

    @Test
    void disassembleTrashBusyReplyUsesAgentReplyAdapter() throws Exception {
        Character bot = mock(Character.class);
        when(bot.getId()).thenReturn(200);
        BotEntry entry = new BotEntry(bot, null, null);
        Set<Integer> active = activeMakerSet();
        active.add(200);

        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class)) {
            BotMakerManager.handleDisassembleTrash(entry);

            replies.verify(() -> AgentBotReplyRuntime.replyNow(entry, "still working on the last batch, hang on"));
        } finally {
            active.remove(200);
        }
    }

    @SuppressWarnings("unchecked")
    private static Set<Integer> activeMakerSet() throws ReflectiveOperationException {
        Field active = BotMakerManager.class.getDeclaredField("ACTIVE");
        active.setAccessible(true);
        return (Set<Integer>) active.get(null);
    }
}
