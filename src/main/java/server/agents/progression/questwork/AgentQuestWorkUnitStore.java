package server.agents.progression.questwork;

import java.util.List;
import java.util.Optional;

/** Durable storage port for individually resumable quest work. */
public interface AgentQuestWorkUnitStore {
    void save(AgentQuestWorkUnit workUnit);

    Optional<AgentQuestWorkUnit> load(String workUnitId);

    List<AgentQuestWorkUnit> loadAll();

    void delete(String workUnitId);
}
