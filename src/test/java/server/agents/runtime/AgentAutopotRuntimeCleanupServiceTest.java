package server.agents.runtime;

import server.agents.capabilities.supplies.AgentAutopotCleanupService;

import client.Character;
import client.keybind.KeyBinding;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentAutopotRuntimeCleanupServiceTest {
    @Test
    void clearsAlertsAndNormalizesPetAutopotKeys() {
        Character agent = mock(Character.class);
        Map<Integer, KeyBinding> keymap = new LinkedHashMap<>();
        keymap.put(91, new KeyBinding(2, 2000002));
        keymap.put(92, new KeyBinding(7, 2000003));
        keymap.put(93, new KeyBinding(2, 2000004));

        when(agent.getKeymap()).thenReturn(keymap);
        doAnswer(invocation -> {
            int key = invocation.getArgument(0);
            KeyBinding binding = invocation.getArgument(1);
            keymap.put(key, binding);
            return null;
        }).when(agent).changeKeybinding(anyInt(), any(KeyBinding.class));

        AgentAutopotCleanupService.clearAgentAutopotState(agent);

        verify(agent).setAutopotHpAlert(0f);
        verify(agent).setAutopotMpAlert(0f);
        assertEquals(7, keymap.get(91).getType());
        assertEquals(2000002, keymap.get(91).getAction());
        assertEquals(7, keymap.get(92).getType());
        assertEquals(2000003, keymap.get(92).getAction());
        assertEquals(2, keymap.get(93).getType());
        assertEquals(2000004, keymap.get(93).getAction());
    }
}
