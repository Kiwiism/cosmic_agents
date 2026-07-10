package server.agents.capabilities.supplies;

import client.Character;
import client.keybind.KeyBinding;

public final class AgentAutopotCleanupService {
    private AgentAutopotCleanupService() {
    }

    public static void clearAgentAutopotState(Character agent) {
        agent.setAutopotHpAlert(0f);
        agent.setAutopotMpAlert(0f);
        normalizeAutopotKey(agent, 91);
        normalizeAutopotKey(agent, 92);
    }

    private static void normalizeAutopotKey(Character agent, int key) {
        KeyBinding binding = agent.getKeymap().get(key);
        if (binding != null && binding.getType() != 7 && binding.getAction() > 0) {
            agent.changeKeybinding(key, new KeyBinding(7, binding.getAction()));
        }
    }
}
