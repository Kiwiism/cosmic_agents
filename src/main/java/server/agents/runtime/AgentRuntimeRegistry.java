package server.agents.runtime;

import client.Character;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.bots.BotEntry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Agent-owned lookup helpers over the temporary BotEntry-backed runtime map.
 * Storage remains in BotManager during reconstruction; lookup behavior lives here.
 */
public final class AgentRuntimeRegistry {
    private static final Map<Integer, List<BotEntry>> entriesByLeaderId = new ConcurrentHashMap<>();

    private AgentRuntimeRegistry() {
    }

    public static Map<Integer, List<BotEntry>> entriesByLeaderId() {
        return entriesByLeaderId;
    }

    public static List<BotEntry> mutableEntriesForLeader(int leaderCharId) {
        return entriesByLeaderId.computeIfAbsent(leaderCharId, ignored -> new CopyOnWriteArrayList<>());
    }

    public static BotEntry findByCharacterId(Map<Integer, List<BotEntry>> entriesByLeaderId,
                                             int leaderCharId,
                                             int agentCharId) {
        List<BotEntry> entries = entriesByLeaderId.get(leaderCharId);
        if (entries == null) {
            return null;
        }
        for (BotEntry entry : entries) {
            if (AgentBotRuntimeIdentityRuntime.botIs(entry, agentCharId)) {
                return entry;
            }
        }
        return null;
    }

    public static BotEntry findByName(Map<Integer, List<BotEntry>> entriesByLeaderId,
                                      int leaderCharId,
                                      String agentName) {
        List<BotEntry> entries = entriesByLeaderId.get(leaderCharId);
        if (entries == null || agentName == null) {
            return null;
        }
        for (BotEntry entry : entries) {
            if (AgentBotRuntimeIdentityRuntime.botNameEquals(entry, agentName)) {
                return entry;
            }
        }
        return null;
    }

    public static Character activeLeaderByAgentCharacterId(Map<Integer, List<BotEntry>> entriesByLeaderId,
                                                           int agentCharId) {
        for (List<BotEntry> entries : entriesByLeaderId.values()) {
            for (BotEntry entry : entries) {
                if (AgentBotRuntimeIdentityRuntime.botIs(entry, agentCharId)) {
                    return AgentBotRuntimeIdentityRuntime.owner(entry);
                }
            }
        }
        return null;
    }

    public static Character firstAgent(Map<Integer, List<BotEntry>> entriesByLeaderId, int leaderCharId) {
        BotEntry entry = firstEntry(entriesByLeaderId, leaderCharId);
        return entry == null ? null : AgentBotRuntimeIdentityRuntime.bot(entry);
    }

    public static BotEntry firstEntry(Map<Integer, List<BotEntry>> entriesByLeaderId, int leaderCharId) {
        List<BotEntry> entries = entriesByLeaderId.get(leaderCharId);
        return entries != null && !entries.isEmpty() ? entries.get(0) : null;
    }

    public static boolean isFirstEntryForLeader(Map<Integer, List<BotEntry>> entriesByLeaderId, BotEntry entry) {
        if (entry == null || AgentBotRuntimeIdentityRuntime.owner(entry) == null) {
            return false;
        }
        return firstEntry(entriesByLeaderId, AgentBotRuntimeIdentityRuntime.ownerId(entry)) == entry;
    }

    public static List<BotEntry> entriesForLeader(Map<Integer, List<BotEntry>> entriesByLeaderId,
                                                  int leaderCharId) {
        List<BotEntry> entries = entriesByLeaderId.get(leaderCharId);
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        return List.copyOf(entries);
    }
}
