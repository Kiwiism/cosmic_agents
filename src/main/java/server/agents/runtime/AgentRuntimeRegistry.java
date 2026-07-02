package server.agents.runtime;

import client.BotClient;
import client.Character;
import net.server.Server;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.bots.BotEntry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Agent-owned lookup helpers over the temporary BotEntry-backed runtime map.
 * Runtime storage and lookup behavior live here while BotEntry is still the
 * backing session object.
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

    public static BotEntry findByCharacterId(int leaderCharId, int agentCharId) {
        return findByCharacterId(entriesByLeaderId, leaderCharId, agentCharId);
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

    public static BotEntry findByName(int leaderCharId, String agentName) {
        return findByName(entriesByLeaderId, leaderCharId, agentName);
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

    public static Character activeLeaderByAgentCharacterId(int agentCharId) {
        return activeLeaderByAgentCharacterId(entriesByLeaderId, agentCharId);
    }

    public static Character findUnclaimedOnlineAgentByName(String agentName, int world) {
        for (var channel : Server.getInstance().getWorld(world).getChannels()) {
            Character candidate = channel.getPlayerStorage().getCharacterByName(agentName);
            if (isUnclaimedBotClientCharacter(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    public static boolean isUnclaimedBotClientCharacter(Character candidate) {
        return candidate != null
                && candidate.getClient() instanceof BotClient
                && activeLeaderByAgentCharacterId(candidate.getId()) == null;
    }

    public static Character firstAgent(Map<Integer, List<BotEntry>> entriesByLeaderId, int leaderCharId) {
        BotEntry entry = firstEntry(entriesByLeaderId, leaderCharId);
        return entry == null ? null : AgentBotRuntimeIdentityRuntime.bot(entry);
    }

    public static Character firstAgent(int leaderCharId) {
        return firstAgent(entriesByLeaderId, leaderCharId);
    }

    public static BotEntry firstEntry(Map<Integer, List<BotEntry>> entriesByLeaderId, int leaderCharId) {
        List<BotEntry> entries = entriesByLeaderId.get(leaderCharId);
        return entries != null && !entries.isEmpty() ? entries.get(0) : null;
    }

    public static BotEntry firstEntry(int leaderCharId) {
        return firstEntry(entriesByLeaderId, leaderCharId);
    }

    public static boolean isFirstEntryForLeader(Map<Integer, List<BotEntry>> entriesByLeaderId, BotEntry entry) {
        if (entry == null || AgentBotRuntimeIdentityRuntime.owner(entry) == null) {
            return false;
        }
        return firstEntry(entriesByLeaderId, AgentBotRuntimeIdentityRuntime.ownerId(entry)) == entry;
    }

    public static boolean isFirstEntryForLeader(BotEntry entry) {
        return isFirstEntryForLeader(entriesByLeaderId, entry);
    }

    public static List<BotEntry> entriesForLeader(Map<Integer, List<BotEntry>> entriesByLeaderId,
                                                  int leaderCharId) {
        List<BotEntry> entries = entriesByLeaderId.get(leaderCharId);
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        return List.copyOf(entries);
    }

    public static List<BotEntry> entriesForLeader(int leaderCharId) {
        return entriesForLeader(entriesByLeaderId, leaderCharId);
    }

    public static int activeAgentCountForLeader(int leaderCharId) {
        List<BotEntry> entries = entriesByLeaderId.get(leaderCharId);
        return entries == null ? 0 : entries.size();
    }

    public static List<Character> activeAgentCharactersForLeader(int leaderCharId) {
        List<BotEntry> entries = entriesByLeaderId.get(leaderCharId);
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }

        return entries.stream()
                .map(AgentBotRuntimeIdentityRuntime::bot)
                .filter(agent -> agent != null)
                .toList();
    }
}
