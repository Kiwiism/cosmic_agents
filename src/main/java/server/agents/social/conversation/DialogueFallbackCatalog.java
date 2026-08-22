package server.agents.social.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import server.agents.social.contracts.DialogueStyleSnapshot;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/** Versioned predefined replies that remain available without a model. */
public final class DialogueFallbackCatalog {
    private static final String RESOURCE = "/agents/social/generic-dialogue-catalog.json";
    private static final DialogueFallbackCatalog DEFAULT = load();

    private final Catalog catalog;

    DialogueFallbackCatalog(Catalog catalog) {
        if (catalog == null || catalog.schemaVersion() <= 0 || catalog.intents() == null
                || !catalog.intents().containsKey("casual.general")) {
            throw new IllegalArgumentException("Valid generic dialogue catalog is required");
        }
        this.catalog = catalog;
    }

    public static DialogueFallbackCatalog defaultCatalog() {
        return DEFAULT;
    }

    public List<String> replies(String intentKey, DialogueStyleSnapshot style) {
        Map<String, List<String>> variants = catalog.intents().getOrDefault(
                intentKey, catalog.intents().get("casual.general"));
        List<String> replies = variants.get(style.styleId());
        if (replies == null || replies.isEmpty()) {
            replies = variants.get("default");
        }
        if (replies == null || replies.isEmpty()) {
            replies = catalog.intents().get("casual.general").get("default");
        }
        return List.copyOf(replies);
    }

    public int version() {
        return catalog.schemaVersion();
    }

    private static DialogueFallbackCatalog load() {
        try (InputStream input = DialogueFallbackCatalog.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("missing generic dialogue catalog: " + RESOURCE);
            }
            return new DialogueFallbackCatalog(new ObjectMapper().readValue(input, Catalog.class));
        } catch (IOException failure) {
            throw new IllegalStateException("could not load generic dialogue catalog", failure);
        }
    }

    record Catalog(int schemaVersion, Map<String, Map<String, List<String>>> intents) {
    }
}
