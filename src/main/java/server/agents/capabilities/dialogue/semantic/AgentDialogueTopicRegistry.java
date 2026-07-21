package server.agents.capabilities.dialogue.semantic;

import config.YamlConfig;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** Topic catalog plus reversible live overrides used by models and GM diagnostics. */
public final class AgentDialogueTopicRegistry {
    public static final String OBJECTIVE_INTENTION = "objective_intention";
    public static final String GREETING = "greeting";
    public static final String QUEST_PROGRESS = "quest_progress";
    public static final String HUNTING = "hunting";
    public static final String TRAVEL = "travel";
    public static final String ENCOURAGEMENT = "encouragement";
    public static final String FAREWELL = "farewell";

    private static final Map<String, AgentDialogueTopicDefinition> definitions =
            new ConcurrentHashMap<>(builtIns());
    private static final Map<String, Boolean> liveOverrides = new ConcurrentHashMap<>();

    private AgentDialogueTopicRegistry() {
    }

    public static boolean systemEnabled() {
        return YamlConfig.config.server.AGENT_DIALOGUE_SYSTEM_ENABLED;
    }

    public static boolean enabled(String topicId) {
        String normalized = normalize(topicId);
        if (!systemEnabled() || !definitions.containsKey(normalized)) {
            return false;
        }
        Boolean override = liveOverrides.get(normalized);
        return override != null ? override : configuredTopics().contains(normalized);
    }

    public static boolean configuredEnabled(String topicId) {
        String normalized = normalize(topicId);
        return definitions.containsKey(normalized) && configuredTopics().contains(normalized);
    }

    public static Boolean liveOverride(String topicId) {
        return liveOverrides.get(normalize(topicId));
    }

    public static void setOverride(String topicId, Boolean enabled) {
        String normalized = normalize(topicId);
        if (!definitions.containsKey(normalized)) {
            throw new IllegalArgumentException("Unknown dialogue topic: " + topicId);
        }
        if (enabled == null) {
            liveOverrides.remove(normalized);
        } else {
            liveOverrides.put(normalized, enabled);
        }
    }

    public static Map<String, AgentDialogueTopicDefinition> definitions() {
        return Map.copyOf(definitions);
    }

    /** Registers a topic supplied by a conversation-model provider. */
    public static void register(AgentDialogueTopicDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("Dialogue topic definition is required");
        }
        definitions.put(definition.topicId(), definition);
    }

    public static Map<String, Boolean> liveOverrides() {
        return Map.copyOf(liveOverrides);
    }

    static void resetForTests() {
        liveOverrides.clear();
    }

    private static Set<String> configuredTopics() {
        String configured = YamlConfig.config.server.AGENT_DIALOGUE_ENABLED_TOPICS;
        if (configured == null || configured.isBlank()) {
            return Set.of();
        }
        return java.util.Arrays.stream(configured.split(","))
                .map(AgentDialogueTopicRegistry::normalize)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String normalize(String topicId) {
        return topicId == null ? "" : topicId.trim().toLowerCase(Locale.ROOT);
    }

    private static Map<String, AgentDialogueTopicDefinition> builtIns() {
        Map<String, AgentDialogueTopicDefinition> result = new LinkedHashMap<>();
        register(result, OBJECTIVE_INTENTION, "One-speaker announcement of the next objective");
        register(result, GREETING, "Opening acknowledgement between nearby Agents");
        register(result, QUEST_PROGRESS, "Short exchange about current quest progress");
        register(result, HUNTING, "Short exchange about nearby hunting");
        register(result, TRAVEL, "Short exchange about the current route or destination");
        register(result, ENCOURAGEMENT, "Supportive acknowledgement during shared activity");
        register(result, FAREWELL, "Bounded conversation closing");
        return result;
    }

    private static void register(Map<String, AgentDialogueTopicDefinition> target,
                                 String topicId,
                                 String description) {
        target.put(topicId, new AgentDialogueTopicDefinition(topicId, description));
    }
}
