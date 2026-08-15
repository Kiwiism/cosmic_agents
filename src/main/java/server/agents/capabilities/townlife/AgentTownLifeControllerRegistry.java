package server.agents.capabilities.townlife;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Town-scoped policy adapters with an optional process-wide fallback. */
final class AgentTownLifeControllerRegistry {
    private static final AgentTownLifeController NONE = context -> java.util.Optional.empty();

    private final Map<Integer, AgentTownLifeController> byTownMapId = new ConcurrentHashMap<>();
    private volatile AgentTownLifeController fallback = NONE;

    AgentTownLifeController resolve(int townMapId) {
        return byTownMapId.getOrDefault(townMapId, fallback);
    }

    void installFallback(AgentTownLifeController controller) {
        fallback = controller == null ? NONE : controller;
    }

    void install(int townMapId, AgentTownLifeController controller) {
        if (controller == null) {
            byTownMapId.remove(townMapId);
        } else {
            byTownMapId.put(townMapId, controller);
        }
    }

    void clear(int townMapId) {
        byTownMapId.remove(townMapId);
    }

    void clear() {
        byTownMapId.clear();
        fallback = NONE;
    }
}
