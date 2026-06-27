package server.bots;

import server.agents.capabilities.dialogue.AgentChatOrchestrator;

/**
 * Temporary bot-side chat runtime bridge while BotManager still invokes chat
 * handling from the legacy bot package.
 */
final class BotChatRuntime {
    // Set true on entry; cleared to false only if we fall off the natural end of handleChat
    // (no command pattern matched). Every match path returns early, leaving this true. Caller
    // (BotManager) reads via wasLastChatHandled() to gate the LLM fallback.
    private static final ThreadLocal<Boolean> LAST_CHAT_HANDLED = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private BotChatRuntime() {
    }

    static boolean wasLastChatHandled() {
        return LAST_CHAT_HANDLED.get();
    }

    static void handleChat(BotEntry entry, String message) {
        LAST_CHAT_HANDLED.set(AgentChatOrchestrator.handle(message, new BotChatOrchestratorContext(entry)));
    }
}
