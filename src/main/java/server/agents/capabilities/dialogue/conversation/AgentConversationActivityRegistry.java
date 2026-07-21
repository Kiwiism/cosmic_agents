package server.agents.capabilities.dialogue.conversation;

import server.agents.capabilities.dialogue.semantic.AgentDialogueMetrics;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/** Aggregates optional plan-owned activity adapters without coupling the dialogue engine to them. */
public final class AgentConversationActivityRegistry {
    private static final List<AgentConversationActivityProvider> providers = loadProviders();

    private AgentConversationActivityRegistry() {
    }

    public static AgentConversationActivity snapshot(AgentRuntimeEntry entry, long nowMs) {
        if (entry == null) {
            return AgentConversationActivity.NONE;
        }
        AgentConversationActivity result = AgentConversationActivity.NONE;
        for (AgentConversationActivityProvider provider : providers) {
            try {
                result = result.merge(provider.snapshot(entry, nowMs));
            } catch (RuntimeException ignored) {
                AgentDialogueMetrics.recordFailure();
            }
        }
        return result;
    }

    private static List<AgentConversationActivityProvider> loadProviders() {
        List<AgentConversationActivityProvider> loaded = new ArrayList<>();
        var providers = ServiceLoader.load(AgentConversationActivityProvider.class).iterator();
        while (true) {
            try {
                if (!providers.hasNext()) {
                    return List.copyOf(loaded);
                }
                loaded.add(providers.next());
            } catch (ServiceConfigurationError | RuntimeException ignored) {
                AgentDialogueMetrics.recordFailure();
            }
        }
    }
}
