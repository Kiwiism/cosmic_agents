package server.agents.capabilities.build;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.build.AgentMakerRuntime;
import server.agents.integration.InventoryGateway;
import server.agents.integration.MakerGateway;
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
            AgentMakerService.handleMakeCrystals(entry, mock(InventoryGateway.class));

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
            AgentMakerService.handleDisassembleTrash(entry, mock(InventoryGateway.class));

            replies.verify(() -> AgentMakerRuntime.replyNow(entry, "still working on the last batch, hang on"));
        } finally {
            active.remove(200);
        }
    }

    @Test
    void makeCrystalsNoMakerSkillReplyUsesMakerGateway() {
        Character bot = mock(Character.class);
        when(bot.getId()).thenReturn(300);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        MakerGateway maker = mock(MakerGateway.class);
        when(maker.getMakerSkillLevel(bot)).thenReturn(0);

        try (MockedStatic<AgentMakerRuntime> replies = mockStatic(AgentMakerRuntime.class)) {
            AgentMakerService.handleMakeCrystals(entry, mock(InventoryGateway.class), maker);

            replies.verify(() -> AgentMakerRuntime.replyNow(entry, "I can't - I don't have the Maker skill"));
        }
    }

    @SuppressWarnings("unchecked")
    private static Set<Integer> activeMakerSet() throws ReflectiveOperationException {
        Field active = AgentMakerService.class.getDeclaredField("ACTIVE");
        active.setAccessible(true);
        return (Set<Integer>) active.get(null);
    }
}
