package server.bots;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatOrchestrator;

public class BotChatManager {
    // Set true on entry; cleared to false only if we fall off the natural end of handleChat
    // (no command pattern matched). Every match path returns early, leaving this true. Caller
    // (BotManager) reads via wasLastChatHandled() to gate the LLM fallback.
    private static final ThreadLocal<Boolean> LAST_CHAT_HANDLED = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public static boolean wasLastChatHandled() {
        return LAST_CHAT_HANDLED.get();
    }

    static void handleChat(BotEntry entry, String message) {
        LAST_CHAT_HANDLED.set(AgentChatOrchestrator.handle(message, new BotChatOrchestratorContext(entry)));
    }

    // -------------------------------------------------------------------------
    // Message queue - 5-second spacing between consecutive bot messages
    // -------------------------------------------------------------------------

    public static void queueBotSay(BotEntry entry, String message) {
        BotChatReplyRuntime.queueSay(entry, message);
    }

    static void queueBotReply(BotEntry entry, String message) {
        BotChatReplyRuntime.queueReply(entry, message);
    }

    static long queueBotSayWithEstimatedDelay(BotEntry entry, String message) {
        return BotChatReplyRuntime.queueSayWithEstimatedDelay(entry, message);
    }

    static long queueBotReplyWithEstimatedDelay(BotEntry entry, String message) {
        return BotChatReplyRuntime.queueReplyWithEstimatedDelay(entry, message);
    }

    // Status check - called on spawn, grind start, greeting, and level-up
    static void checkBotStatus(BotEntry entry, Character bot) {
        BotChatStatusRuntime.checkBotStatus(entry, bot);
    }

    /**
     * Announces the bot's town location via party chat after the owner reconnects
     * (or revives) following a 5+ min offline-or-dead window during which the bot
     * scrolled to town. Party chat reaches the owner even if they spawn back into
     * a different map.
     */
    static void announceOwnerReturnedFromOffline(BotEntry entry) {
        BotChatStatusRuntime.announceOwnerReturnedFromOffline(entry);
    }

    /** Detects owner AFK (same position >=5 min) and says "wb" when they return. */
    static void tickAfkCheck(BotEntry entry, Character owner) {
        BotChatStatusRuntime.tickAfkCheck(entry, owner);
    }

    /** Returns true when the owner hasn't moved in >=5 min (AFK). Skip chat interactions. */
    static boolean isOwnerIdle(BotEntry entry) {
        return BotChatStatusRuntime.isOwnerIdle(entry);
    }
}
