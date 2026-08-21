package server.agents.progression.questcatalog;

import java.util.List;

/** Versioned universal quest catalog assembled from independently maintained inputs. */
public record AgentQuestCatalog(
        int schemaVersion,
        String catalogId,
        String generatedRevision,
        String guidanceRevision,
        List<AgentQuestDefinition> entries) {

    public AgentQuestCatalog {
        catalogId = text(catalogId);
        generatedRevision = text(generatedRevision);
        guidanceRevision = text(guidanceRevision);
        entries = List.copyOf(entries == null ? List.of() : entries);
        if (schemaVersion <= 0 || catalogId.isEmpty() || generatedRevision.isEmpty()
                || guidanceRevision.isEmpty() || entries.isEmpty()) {
            throw new IllegalArgumentException("complete universal quest catalog metadata is required");
        }
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
