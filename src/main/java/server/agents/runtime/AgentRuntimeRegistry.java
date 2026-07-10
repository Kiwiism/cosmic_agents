package server.agents.runtime;

import client.Character;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;

/**
 * Agent-owned lookup helpers over live Agent runtime entries.
 */
public final class AgentRuntimeRegistry {
    private static final Map<Integer, List<AgentRuntimeEntry>> entriesByLeaderId = new ConcurrentHashMap<>();

    private AgentRuntimeRegistry() {
    }

    public static Map<Integer, List<AgentRuntimeEntry>> entriesByLeaderId() {
        return entriesByLeaderId;
    }

    public static List<AgentRuntimeEntry> mutableEntriesForLeader(int leaderCharId) {
        return entriesByLeaderId.computeIfAbsent(leaderCharId, ignored -> new CopyOnWriteArrayList<>());
    }

    public static AgentRuntimeEntry findByCharacterId(Map<Integer, List<AgentRuntimeEntry>> entriesByLeaderId,
                                                      int leaderCharId,
                                                      int agentCharId) {
        List<AgentRuntimeEntry> entries = entriesByLeaderId.get(leaderCharId);
        if (entries == null) {
            return null;
        }
        for (AgentRuntimeEntry entry : entries) {
            if (AgentRuntimeIdentityRuntime.botIs(entry, agentCharId)) {
                return entry;
            }
        }
        return null;
    }

    public static AgentRuntimeEntry findByCharacterId(int leaderCharId, int agentCharId) {
        return findByCharacterId(entriesByLeaderId, leaderCharId, agentCharId);
    }

    public static AgentRuntimeEntry findByName(Map<Integer, List<AgentRuntimeEntry>> entriesByLeaderId,
                                               int leaderCharId,
                                               String agentName) {
        List<AgentRuntimeEntry> entries = entriesByLeaderId.get(leaderCharId);
        if (entries == null || agentName == null) {
            return null;
        }
        for (AgentRuntimeEntry entry : entries) {
            if (AgentRuntimeIdentityRuntime.botNameEquals(entry, agentName)) {
                return entry;
            }
        }
        return null;
    }

    public static AgentRuntimeEntry findByName(int leaderCharId, String agentName) {
        return findByName(entriesByLeaderId, leaderCharId, agentName);
    }

    public static Character activeLeaderByAgentCharacterId(Map<Integer, List<AgentRuntimeEntry>> entriesByLeaderId,
                                                           int agentCharId) {
        for (List<AgentRuntimeEntry> entries : entriesByLeaderId.values()) {
            for (AgentRuntimeEntry entry : entries) {
                if (AgentRuntimeIdentityRuntime.botIs(entry, agentCharId)) {
                    return AgentRuntimeIdentityRuntime.owner(entry);
                }
            }
        }
        return null;
    }

    public static Character activeLeaderByAgentCharacterId(int agentCharId) {
        return activeLeaderByAgentCharacterId(entriesByLeaderId, agentCharId);
    }

    public static boolean hasActiveAgentCharacterId(int agentCharId) {
        for (List<AgentRuntimeEntry> entries : entriesByLeaderId.values()) {
            for (AgentRuntimeEntry entry : entries) {
                if (AgentRuntimeIdentityRuntime.botIs(entry, agentCharId)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static Character findUnclaimedOnlineAgentByName(String agentName, int world) {
        return findUnclaimedOnlineAgentByName(agentName, world,
                (lookupWorld, lookupName) -> AgentCharacterGatewayRuntime.characters()
                        .findWorldCharacterByName(lookupWorld, lookupName));
    }

    static Character findUnclaimedOnlineAgentByName(String agentName,
                                                    int world,
                                                    BiFunction<Integer, String, Character> characterLookup) {
        Character candidate = characterLookup.apply(world, agentName);
        return isUnclaimedBotClientCharacter(candidate) ? candidate : null;
    }

    public static boolean isUnclaimedBotClientCharacter(Character candidate) {
        return candidate != null
                && AgentCharacterGatewayRuntime.characters().isAgentCharacter(candidate)
                && activeLeaderByAgentCharacterId(candidate.getId()) == null;
    }

    public static Character firstAgent(Map<Integer, List<AgentRuntimeEntry>> entriesByLeaderId, int leaderCharId) {
        AgentRuntimeEntry entry = firstEntry(entriesByLeaderId, leaderCharId);
        return entry == null ? null : AgentRuntimeIdentityRuntime.bot(entry);
    }

    public static Character firstAgent(int leaderCharId) {
        return firstAgent(entriesByLeaderId, leaderCharId);
    }

    public static AgentRuntimeEntry firstEntry(Map<Integer, List<AgentRuntimeEntry>> entriesByLeaderId, int leaderCharId) {
        List<AgentRuntimeEntry> entries = entriesByLeaderId.get(leaderCharId);
        return entries != null && !entries.isEmpty() ? entries.get(0) : null;
    }

    public static AgentRuntimeEntry firstEntry(int leaderCharId) {
        return firstEntry(entriesByLeaderId, leaderCharId);
    }

    public static boolean isFirstEntryForLeader(Map<Integer, List<AgentRuntimeEntry>> entriesByLeaderId, AgentRuntimeEntry entry) {
        if (entry == null || AgentRuntimeIdentityRuntime.owner(entry) == null) {
            return false;
        }
        return firstEntry(entriesByLeaderId, AgentRuntimeIdentityRuntime.ownerId(entry)) == entry;
    }

    public static boolean isFirstEntryForLeader(AgentRuntimeEntry entry) {
        return isFirstEntryForLeader(entriesByLeaderId, entry);
    }

    public static List<AgentRuntimeEntry> entriesForLeader(Map<Integer, List<AgentRuntimeEntry>> entriesByLeaderId,
                                                           int leaderCharId) {
        List<AgentRuntimeEntry> entries = entriesByLeaderId.get(leaderCharId);
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        return List.copyOf(entries);
    }

    public static List<AgentRuntimeEntry> entriesForLeader(int leaderCharId) {
        return entriesForLeader(entriesByLeaderId, leaderCharId);
    }

    public static List<AgentRuntimeEntry> agentEntriesForLeader(Map<Integer, List<AgentRuntimeEntry>> entriesByLeaderId,
                                                                int leaderCharId) {
        List<AgentRuntimeEntry> entries = entriesByLeaderId.get(leaderCharId);
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        return List.copyOf(new ArrayList<>(entries));
    }

    public static List<AgentRuntimeEntry> agentEntriesForLeader(int leaderCharId) {
        return agentEntriesForLeader(entriesByLeaderId, leaderCharId);
    }

    public static int activeAgentCountForLeader(int leaderCharId) {
        List<AgentRuntimeEntry> entries = entriesByLeaderId.get(leaderCharId);
        return entries == null ? 0 : entries.size();
    }

    public static List<Character> activeAgentCharactersForLeader(int leaderCharId) {
        List<AgentRuntimeEntry> entries = entriesByLeaderId.get(leaderCharId);
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }

        return entries.stream()
                .map(AgentRuntimeIdentityRuntime::bot)
                .filter(agent -> agent != null)
                .toList();
    }
}
