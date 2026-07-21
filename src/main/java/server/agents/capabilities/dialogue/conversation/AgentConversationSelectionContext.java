package server.agents.capabilities.dialogue.conversation;

import server.agents.runtime.AgentRuntimeEntry;

public record AgentConversationSelectionContext(
        AgentRuntimeEntry first,
        AgentRuntimeEntry second,
        long nowMs,
        long variationSeed,
        AgentConversationActivity firstActivity,
        AgentConversationActivity secondActivity) {
    public AgentConversationSelectionContext(AgentRuntimeEntry first,
                                             AgentRuntimeEntry second,
                                             long nowMs,
                                             long variationSeed) {
        this(first, second, nowMs, variationSeed,
                AgentConversationActivityRegistry.snapshot(first, nowMs),
                AgentConversationActivityRegistry.snapshot(second, nowMs));
    }

    public AgentConversationSelectionContext {
        firstActivity = firstActivity == null ? AgentConversationActivity.NONE : firstActivity;
        secondActivity = secondActivity == null ? AgentConversationActivity.NONE : secondActivity;
    }
}
