package server.agents.progression;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class AgentVictoriaQuestRuntimeCatalogRepository {
    private static final String RESOURCE =
            "/agents/catalogs/victoria-lt30-quest-runtime-catalog.json";
    private static final AgentVictoriaQuestRuntimeCatalogRepository DEFAULT = load();

    private final AgentVictoriaQuestRuntimeCatalog catalog;
    private final Map<Integer, AgentVictoriaQuestRuntimeCatalog.Entry> byQuestId;

    AgentVictoriaQuestRuntimeCatalogRepository(AgentVictoriaQuestRuntimeCatalog catalog) {
        this.catalog = catalog;
        Map<Integer, AgentVictoriaQuestRuntimeCatalog.Entry> index = new HashMap<>();
        for (AgentVictoriaQuestRuntimeCatalog.Entry entry : catalog.entries()) {
            if (index.putIfAbsent(entry.questId(), entry) != null) {
                throw new IllegalArgumentException("duplicate Victoria quest " + entry.questId());
            }
        }
        byQuestId = Map.copyOf(index);
    }

    static AgentVictoriaQuestRuntimeCatalogRepository defaultRepository() {
        return DEFAULT;
    }

    AgentVictoriaQuestRuntimeCatalog catalog() {
        return catalog;
    }

    Optional<AgentVictoriaQuestRuntimeCatalog.Entry> find(int questId) {
        return Optional.ofNullable(byQuestId.get(questId));
    }

    List<AgentVictoriaQuestRuntimeCatalog.Entry> eligibleAtLevel(int level) {
        return catalog.entries().stream().filter(entry -> entry.levelEligible(level)).toList();
    }

    private static AgentVictoriaQuestRuntimeCatalogRepository load() {
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try (InputStream input = AgentVictoriaQuestRuntimeCatalogRepository.class
                .getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("missing Victoria quest runtime catalog: " + RESOURCE);
            }
            return new AgentVictoriaQuestRuntimeCatalogRepository(
                    mapper.readValue(input, AgentVictoriaQuestRuntimeCatalog.class));
        } catch (IOException failure) {
            throw new IllegalStateException("could not load Victoria quest runtime catalog", failure);
        }
    }
}
