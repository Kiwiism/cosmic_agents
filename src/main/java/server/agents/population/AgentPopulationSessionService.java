package server.agents.population;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Atomically guards the lifecycle backend from duplicate population sessions. */
public final class AgentPopulationSessionService {
    public interface Backend {
        boolean isEligibleAgent(int characterId);

        boolean isLive(int characterId);

        boolean spawnSelfDirected(AgentPopulationRecord record) throws Exception;

        boolean stop(int characterId) throws Exception;
    }

    private final Backend backend;
    private final Set<Integer> transitions = ConcurrentHashMap.newKeySet();

    public AgentPopulationSessionService(Backend backend) {
        this.backend = backend;
    }

    public boolean isLive(int characterId) {
        return backend.isLive(characterId);
    }

    public boolean start(AgentPopulationRecord record) throws Exception {
        if (!transitions.add(record.characterId())) {
            return false;
        }
        try {
            if (!backend.isEligibleAgent(record.characterId()) || backend.isLive(record.characterId())) {
                return false;
            }
            return backend.spawnSelfDirected(record);
        } finally {
            transitions.remove(record.characterId());
        }
    }

    public boolean stop(AgentPopulationRecord record) throws Exception {
        if (!transitions.add(record.characterId())) {
            return false;
        }
        try {
            return backend.isLive(record.characterId()) && backend.stop(record.characterId());
        } finally {
            transitions.remove(record.characterId());
        }
    }

    int transitionsInProgress() {
        return transitions.size();
    }
}
