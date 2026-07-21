package server.agents.capabilities.dialogue.semantic;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Deterministic text templates registered by semantic and conversation models. */
public final class AgentDialogueTemplateRegistry {
    private static final Map<String, List<String>> templates = new ConcurrentHashMap<>();

    static {
        register(AgentDialogueTopicRegistry.OBJECTIVE_INTENTION, "announce", List.of("{message}"));
        register(AgentDialogueTopicRegistry.GREETING, "open", List.of(
                "Hey, {listenerName}.",
                "hi {listenerName}",
                "hey hey",
                "oh hi",
                "yo {listenerName}",
                "o/ {listenerName}",
                "hiya :D",
                "howdy",
                "Oh hey {listenerName}!",
                "yoooo {listenerName} :D",
                "\\o hey {listenerName}",
                "ayy whats up"));
        register(AgentDialogueTopicRegistry.FAREWELL, "close", List.of(
                "See you around.",
                "cya in a bit",
                "alright, later",
                "see ya",
                "gl with the quest",
                "later o/",
                "good luck :D",
                "cya cya",
                "Good luck out there!",
                "Catch you later, {listenerName}!",
                "later {listenerName} \\o",
                "ok back to questing xD"));
    }

    private AgentDialogueTemplateRegistry() {
    }

    public static void register(String topicId, String actKey, List<String> values) {
        if (topicId == null || topicId.isBlank() || actKey == null || actKey.isBlank()
                || values == null || values.isEmpty()
                || values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Dialogue topic, act, and templates are required");
        }
        templates.put(key(topicId, actKey), List.copyOf(values));
    }

    public static List<String> templates(String topicId, String actKey) {
        return templates.getOrDefault(key(topicId, actKey), List.of());
    }

    private static String key(String topicId, String actKey) {
        return topicId.trim().toLowerCase() + ':' + actKey.trim().toLowerCase();
    }
}
