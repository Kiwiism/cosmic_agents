package server.agents.integration;

import client.Character;
import server.bots.BotEntry;

/**
 * Agent-owned adapter for temporary BotEntry-backed live runtime identity.
 */
public final class AgentBotRuntimeIdentityRuntime {
    private AgentBotRuntimeIdentityRuntime() {
    }

    public static Character bot(BotEntry entry) {
        return entry == null ? null : entry.bot();
    }

    public static Character owner(BotEntry entry) {
        return entry == null ? null : entry.owner();
    }

    public static int botId(BotEntry entry) {
        Character bot = bot(entry);
        return bot == null ? -1 : bot.getId();
    }

    public static int ownerId(BotEntry entry) {
        Character owner = owner(entry);
        return owner == null ? -1 : owner.getId();
    }

    public static boolean hasBot(BotEntry entry) {
        return bot(entry) != null;
    }

    public static boolean botIs(BotEntry entry, int characterId) {
        return botId(entry) == characterId;
    }

    public static boolean botNameEquals(BotEntry entry, String name) {
        Character bot = bot(entry);
        return bot != null && name != null && bot.getName().equalsIgnoreCase(name);
    }
}
