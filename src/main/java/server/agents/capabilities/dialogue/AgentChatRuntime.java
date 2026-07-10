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

    public static boolean handleChat(String message, AgentChatOrchestrator.Context context) {
        boolean handled = AgentChatOrchestrator.handle(message, context);
        recordLastChatHandled(handled);
        return handled;
    }

    public static void recordLastChatHandled(boolean handled) {
        LAST_CHAT_HANDLED.set(handled);
    }
}
