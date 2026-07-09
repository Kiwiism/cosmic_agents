package server.agents.integration;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapleMap;

import java.awt.Point;

/**
 * Boundary adapter from Agent runtime entries to live Cosmic Character and map
 * identity.
 */
public final class AgentRuntimeIdentityRuntime {
    private AgentRuntimeIdentityRuntime() {
    }

    public static Character bot(AgentRuntimeEntry entry) {
        return entry == null ? null : entry.identityState().agent();
    }

    public static Character owner(AgentRuntimeEntry entry) {
        return entry == null ? null : entry.identityState().leader();
    }

    public static int botId(AgentRuntimeEntry entry) {
        Character bot = bot(entry);
        return bot == null ? -1 : bot.getId();
    }

    public static int botAccountId(AgentRuntimeEntry entry) {
        Character bot = bot(entry);
        return bot == null ? -1 : bot.getAccountID();
    }

    public static String botName(AgentRuntimeEntry entry) {
        Character bot = bot(entry);
        return bot == null ? null : bot.getName();
    }

    public static int ownerId(AgentRuntimeEntry entry) {
        Character owner = owner(entry);
        return owner == null ? -1 : owner.getId();
    }

    public static boolean hasBot(AgentRuntimeEntry entry) {
        return bot(entry) != null;
    }

    public static boolean botIs(AgentRuntimeEntry entry, int characterId) {
        return botId(entry) == characterId;
    }

    public static boolean botNameEquals(AgentRuntimeEntry entry, String name) {
        Character bot = bot(entry);
        return bot != null && name != null && bot.getName().equalsIgnoreCase(name);
    }

    public static int botMapId(AgentRuntimeEntry entry) {
        Character bot = bot(entry);
        return bot == null ? -1 : bot.getMapId();
    }

    public static MapleMap botMap(AgentRuntimeEntry entry) {
        Character bot = bot(entry);
        return bot == null ? null : bot.getMap();
    }

    public static boolean botHasMap(AgentRuntimeEntry entry) {
        return botMap(entry) != null;
    }

    public static Point botPosition(AgentRuntimeEntry entry) {
        Character bot = bot(entry);
        Point position = bot == null ? null : bot.getPosition();
        return position == null ? null : new Point(position);
    }
}
