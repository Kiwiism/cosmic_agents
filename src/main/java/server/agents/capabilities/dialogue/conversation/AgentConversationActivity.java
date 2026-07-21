package server.agents.capabilities.dialogue.conversation;

/** Region-neutral activity facts exposed to conversation topic models. */
public record AgentConversationActivity(
        boolean objectiveActive,
        boolean hunting,
        long objectiveAgeMs,
        boolean recentlyCompleted,
        String objectiveKey) {
    public static final AgentConversationActivity NONE =
            new AgentConversationActivity(false, false, 0L, false, "");

    public AgentConversationActivity(boolean objectiveActive,
                                     boolean hunting,
                                     long objectiveAgeMs,
                                     boolean recentlyCompleted) {
        this(objectiveActive, hunting, objectiveAgeMs, recentlyCompleted, "");
    }

    public AgentConversationActivity {
        objectiveAgeMs = Math.max(0L, objectiveAgeMs);
        objectiveKey = objectiveKey == null ? "" : objectiveKey.trim();
    }

    public AgentConversationActivity merge(AgentConversationActivity other) {
        if (other == null || other == NONE) {
            return this;
        }
        return new AgentConversationActivity(
                objectiveActive || other.objectiveActive,
                hunting || other.hunting,
                Math.max(objectiveAgeMs, other.objectiveAgeMs),
                recentlyCompleted || other.recentlyCompleted,
                objectiveKey.isBlank() ? other.objectiveKey : objectiveKey);
    }
}
