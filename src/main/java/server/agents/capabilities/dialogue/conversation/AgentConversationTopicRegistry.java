package server.agents.capabilities.dialogue.conversation;

import server.agents.capabilities.dialogue.semantic.AgentDialogueTemplateRegistry;
import server.agents.capabilities.dialogue.semantic.AgentDialogueMetrics;
import server.agents.capabilities.dialogue.semantic.AgentDialogueTopicRegistry;
import server.agents.capabilities.dialogue.semantic.AgentSemanticDialogueAct;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;

/** Loads generic and content-owned topic models without coupling the dialogue engine to a region. */
public final class AgentConversationTopicRegistry {
    private static final Map<String, AgentConversationTopicModel> models = loadModels();

    private AgentConversationTopicRegistry() {
    }

    public static void ensureLoaded() {
        // Class initialization performs provider discovery and registration.
    }

    public static String selectTopic(AgentConversationSelectionContext context) {
        String selected = null;
        int selectedUtility = AgentConversationTopicModel.UNAVAILABLE;
        for (AgentConversationTopicModel model : models.values()) {
            try {
                if (!AgentDialogueTopicRegistry.enabled(model.definition().topicId())) {
                    continue;
                }
                int utility = model.utility(context);
                if (utility > selectedUtility
                        || (utility == selectedUtility && selected != null
                        && model.definition().topicId().compareTo(selected) < 0)) {
                    selected = model.definition().topicId();
                    selectedUtility = utility;
                }
            } catch (RuntimeException ignored) {
                AgentDialogueMetrics.recordFailure();
            }
        }
        return selectedUtility == AgentConversationTopicModel.UNAVAILABLE ? null : selected;
    }

    public static AgentSemanticDialogueAct produce(String topicId,
                                                    AgentConversationModelContext context) {
        AgentConversationTopicModel model = models.get(topicId);
        if (model == null) {
            return null;
        }
        try {
            return model.produce(context);
        } catch (RuntimeException ignored) {
            AgentDialogueMetrics.recordFailure();
            return null;
        }
    }

    private static Map<String, AgentConversationTopicModel> loadModels() {
        Map<String, AgentConversationTopicModel> loaded = new LinkedHashMap<>();
        register(new AgentCoreConversationTopicProvider(), loaded);
        Iterator<AgentConversationTopicProvider> providers =
                ServiceLoader.load(AgentConversationTopicProvider.class).iterator();
        while (true) {
            try {
                if (!providers.hasNext()) {
                    break;
                }
                register(providers.next(), loaded);
            } catch (ServiceConfigurationError | RuntimeException ignored) {
                AgentDialogueMetrics.recordFailure();
            }
        }
        return Map.copyOf(loaded);
    }

    private static void register(AgentConversationTopicProvider provider,
                                 Map<String, AgentConversationTopicModel> target) {
        List<AgentConversationTopicModel> providerModels = List.copyOf(provider.topicModels());
        Set<String> providerIds = new HashSet<>();
        for (AgentConversationTopicModel model : providerModels) {
            String topicId = model.definition().topicId();
            if (!providerIds.add(topicId) || target.containsKey(topicId)) {
                throw new IllegalStateException("Duplicate Agent conversation topic: " + topicId);
            }
        }
        for (AgentConversationTopicModel model : providerModels) {
            String topicId = model.definition().topicId();
            target.put(topicId, model);
            AgentDialogueTopicRegistry.register(model.definition());
            model.templates().forEach((actKey, templates) ->
                    AgentDialogueTemplateRegistry.register(topicId, actKey, templates));
        }
    }
}
