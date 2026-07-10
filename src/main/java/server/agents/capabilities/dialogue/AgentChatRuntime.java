package server.agents.capabilities.dialogue;

/**
 * Owns per-chat handled state for the Agent dialogue orchestrator. Integration
 * bridges adapt their runtime context into {@link AgentChatOrchestrator.Context}
 * and delegate here without owning chat routing state themselves.
 */
public final class AgentChatRuntime {
    private static final ThreadLocal<Boolean> LAST_CHAT_HANDLED = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private AgentChatRuntime() {
    }

    public static boolean wasLastChatHandled() {
        return LAST_CHAT_HANDLED.get();
    }

    public static void handleChat(String message, AgentChatOrchestrator.Context context) {
        LAST_CHAT_HANDLED.set(AgentChatOrchestrator.handle(message, context));
    }
}
