package server.agents.capabilities.supplies;

import client.Character;
import client.keybind.KeyBinding;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class AgentAutopotCleanupService {
    private static final Set<Integer> PRESERVE_ON_NEXT_CLEANUP = ConcurrentHashMap.newKeySet();

    private AgentAutopotCleanupService() {
    }

    public static void clearAgentAutopotState(Character agent) {
        if (PRESERVE_ON_NEXT_CLEANUP.remove(agent.getId())) {
            return;
        }
        agent.setAutopotHpAlert(0f);
        agent.setAutopotMpAlert(0f);
        normalizeAutopotKey(agent, 91);
        normalizeAutopotKey(agent, 92);
    }

    public static void preserveOnNextCleanup(int characterId) {
        PRESERVE_ON_NEXT_CLEANUP.add(characterId);
    }

    public static void cancelPreservation(int characterId) {
        PRESERVE_ON_NEXT_CLEANUP.remove(characterId);
    }

    private static void normalizeAutopotKey(Character agent, int key) {
        KeyBinding binding = agent.getKeymap().get(key);
        if (binding != null && binding.getType() != 7 && binding.getAction() > 0) {
            agent.changeKeybinding(key, new KeyBinding(7, binding.getAction()));
        }
    }
}
