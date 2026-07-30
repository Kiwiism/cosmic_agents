package server.agents.progression;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class AgentVictoriaQuestHuntIndexRepository {
    private static final String RESOURCE =
            "/agents/catalogs/adaptive/victoria-quest-hunt-index.json";
    private static final AgentVictoriaQuestHuntIndexRepository DEFAULT = load();

    private final AgentVictoriaQuestHuntIndex index;
    private final Map<Integer, AgentVictoriaQuestHuntIndex.Entry> byQuestId;

    AgentVictoriaQuestHuntIndexRepository(AgentVictoriaQuestHuntIndex index) {
        this.index = index;
        Map<Integer, AgentVictoriaQuestHuntIndex.Entry> entries = new HashMap<>();
        for (AgentVictoriaQuestHuntIndex.Entry entry : index.entries()) {
            if (entries.putIfAbsent(entry.questId(), entry) != null) {
                throw new IllegalArgumentException("duplicate adaptive quest " + entry.questId());
            }
        }
        byQuestId = Map.copyOf(entries);
    }

    static AgentVictoriaQuestHuntIndexRepository defaultRepository() {
        return DEFAULT;
    }

    AgentVictoriaQuestHuntIndex index() {
        return index;
    }

    Optional<AgentVictoriaQuestHuntIndex.Objective> findObjective(
            int questId,
            String objectiveId) {
        AgentVictoriaQuestHuntIndex.Entry entry = byQuestId.get(questId);
        if (entry == null) {
            return Optional.empty();
        }
        return entry.objectives().stream()
                .filter(objective -> objective.objectiveId().equals(objectiveId))
                .findFirst();
    }

    List<ObjectiveReference> findObjectivesForTarget(
            Set<Integer> questIds,
            int targetId) {
        if (questIds == null || questIds.isEmpty() || targetId <= 0) {
            return List.of();
        }
        return questIds.stream()
                .sorted()
                .map(byQuestId::get)
                .filter(java.util.Objects::nonNull)
                .flatMap(entry -> entry.objectives().stream()
                        .filter(objective -> objective.targetId() == targetId)
                        .map(objective -> new ObjectiveReference(entry.questId(), objective)))
                .toList();
    }

    record ObjectiveReference(
            int questId,
            AgentVictoriaQuestHuntIndex.Objective objective) {
    }

    private static AgentVictoriaQuestHuntIndexRepository load() {
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try (InputStream input = AgentVictoriaQuestHuntIndexRepository.class
                .getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("missing adaptive quest hunt index: " + RESOURCE);
            }
            return new AgentVictoriaQuestHuntIndexRepository(
                    mapper.readValue(input, AgentVictoriaQuestHuntIndex.class));
        } catch (IOException failure) {
            throw new IllegalStateException("could not load adaptive quest hunt index", failure);
        }
    }
}
