package server.agents.integration;

import client.Character;
import server.bots.BotEntry;
import server.maps.MapleMap;

import java.awt.Point;

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

    public static int botAccountId(BotEntry entry) {
        Character bot = bot(entry);
        return bot == null ? -1 : bot.getAccountID();
    }

    public static String botName(BotEntry entry) {
        Character bot = bot(entry);
        return bot == null ? null : bot.getName();
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

    public static int botMapId(BotEntry entry) {
        Character bot = bot(entry);
        return bot == null ? -1 : bot.getMapId();
    }

    public static MapleMap botMap(BotEntry entry) {
        Character bot = bot(entry);
        return bot == null ? null : bot.getMap();
    }

    public static boolean botHasMap(BotEntry entry) {
        return botMap(entry) != null;
    }

    public static Point botPosition(BotEntry entry) {
        Character bot = bot(entry);
        Point position = bot == null ? null : bot.getPosition();
        return position == null ? null : new Point(position);
    }
}
