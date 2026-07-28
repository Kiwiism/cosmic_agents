package server.agents.capabilities.inventory.demand;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Read-only repository for the generated demand index. */
public final class AgentQuestItemDemandIndexRepository {
    private static final String RESOURCE =
            "/agents/catalogs/adaptive/victoria-quest-item-demand-index.json";
    private static final AgentQuestItemDemandIndexRepository DEFAULT = load();

    private final AgentQuestItemDemandIndex index;
    private final Map<Integer, AgentQuestItemDemandIndex.Entry> byItemId;

    public AgentQuestItemDemandIndexRepository(AgentQuestItemDemandIndex index) {
        this.index = index;
        Map<Integer, AgentQuestItemDemandIndex.Entry> entries = new HashMap<>();
        for (AgentQuestItemDemandIndex.Entry entry : index.entries()) {
            if (entries.putIfAbsent(entry.itemId(), entry) != null) {
                throw new IllegalArgumentException("duplicate quest-demand item " + entry.itemId());
            }
        }
        byItemId = Map.copyOf(entries);
    }

    public static AgentQuestItemDemandIndexRepository defaultRepository() {
        return DEFAULT;
    }

    public AgentQuestItemDemandIndex index() {
        return index;
    }

    public Optional<AgentQuestItemDemandIndex.Entry> findItem(int itemId) {
        return Optional.ofNullable(byItemId.get(itemId));
    }

    private static AgentQuestItemDemandIndexRepository load() {
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try (InputStream input = AgentQuestItemDemandIndexRepository.class
                .getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("missing quest-item demand index: " + RESOURCE);
            }
            return new AgentQuestItemDemandIndexRepository(
                    mapper.readValue(input, AgentQuestItemDemandIndex.class));
        } catch (IOException failure) {
            throw new IllegalStateException("could not load quest-item demand index", failure);
        }
    }
}
