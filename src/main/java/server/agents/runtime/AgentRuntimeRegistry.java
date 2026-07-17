package server.agents.runtime;

import client.Character;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentRelationshipRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.scheduler.AgentAdmissionDecision;
import server.agents.runtime.scheduler.AgentLoadSheddingRuntime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiFunction;

/**
 * Agent-owned lookup helpers over live Agent runtime entries.
 */
public final class AgentRuntimeRegistry {
    private static final Object mutationLock = new Object();
    private static final Map<Integer, ActiveSession> activeSessionsByAgentId = new ConcurrentHashMap<>();
    private static final Map<Long, List<AgentRuntimeEntry>> entriesByCohortId = new ConcurrentHashMap<>();
    private static final Map<Integer, List<AgentRuntimeEntry>> entriesByLeaderId = new ConcurrentHashMap<>();

    private record ActiveSession(long cohortId, AgentRuntimeEntry entry, long generation) {
        private boolean matches(AgentRuntimeEntry expectedEntry, long expectedGeneration) {
            return entry == expectedEntry && generation == expectedGeneration;
        }
    }

    private AgentRuntimeRegistry() {
    }

    /** Compatibility access for read-only leader-grouped callers. */
    public static Map<Integer, List<AgentRuntimeEntry>> entriesByLeaderId() {
        return entriesByLeaderId;
    }

    public static Map<Long, List<AgentRuntimeEntry>> entriesByCohortId() {
        return entriesByCohortId;
    }

    /** @deprecated New mutation code must use the explicit registry methods. */
    @Deprecated
    public static List<AgentRuntimeEntry> mutableEntriesForLeader(int leaderCharId) {
        return entriesByLeaderId.computeIfAbsent(leaderCharId, ignored -> new CopyOnWriteArrayList<>());
    }

    public static void registerEntry(int leaderCharId, AgentRuntimeEntry entry) {
        if (entry != null) {
            entry.relationshipState().setCohortId(leaderCharId);
        }
        registerEntry(entry);
    }

    public static void registerEntry(AgentRuntimeEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("Agent runtime entry is required");
        }
        synchronized (mutationLock) {
            long cohortId = AgentRelationshipRuntime.cohortId(entry);
            int agentCharId = AgentRuntimeIdentityRuntime.botId(entry);
            if (cohortId == 0L && agentCharId >= 0) {
                cohortId = agentCharId;
                AgentRelationshipRuntime.setCohortId(entry, cohortId);
                if (AgentRelationshipRuntime.formationId(entry) == 0L) {
                    AgentRelationshipRuntime.setFormationId(entry, cohortId);
                }
            }
            ActiveSession previous = agentCharId < 0 ? null : activeSessionsByAgentId.get(agentCharId);
            AgentAdmissionDecision admission = AgentLoadSheddingRuntime.admissionDecision(
                    previous != null,
                    activeSessionsByAgentId.size());
            if (!admission.allowed()) {
                throw new RejectedExecutionException(admission.message());
            }
            if (previous != null && previous.entry() != entry) {
                removeFromCohortView(previous.cohortId(), previous.entry());
                removeFromLeaderView(Math.toIntExact(previous.cohortId()), previous.entry());
            }
            List<AgentRuntimeEntry> cohortEntries = entriesByCohortId.computeIfAbsent(
                    cohortId,
                    ignored -> new CopyOnWriteArrayList<>());
            if (!cohortEntries.contains(entry)) {
                cohortEntries.add(entry);
            }
            List<AgentRuntimeEntry> entries = entriesByLeaderId.computeIfAbsent(
                    Math.toIntExact(cohortId),
                    ignored -> new CopyOnWriteArrayList<>());
            if (!entries.contains(entry)) {
                entries.add(entry);
            }
            index(cohortId, entry);
        }
    }

    public static boolean unregisterEntry(int leaderCharId, AgentRuntimeEntry entry) {
        return unregisterEntry(entry);
    }

    public static boolean unregisterEntry(AgentRuntimeEntry entry) {
        synchronized (mutationLock) {
            long cohortId = AgentRelationshipRuntime.cohortId(entry);
            List<AgentRuntimeEntry> entries = entriesByCohortId.get(cohortId);
            if (entries == null || !entries.remove(entry)) {
                return false;
            }
            unindex(entry);
            if (entries.isEmpty()) {
                entriesByCohortId.remove(cohortId, entries);
            }
            removeFromLeaderView(Math.toIntExact(cohortId), entry);
            return true;
        }
    }

    public static List<AgentRuntimeEntry> unregisterAgentCharacter(int leaderCharId, int agentCharId) {
        AgentRuntimeEntry removed = unregisterAgentCharacter(agentCharId);
        return removed == null ? List.of() : List.of(removed);
    }

    public static AgentRuntimeEntry unregisterAgentCharacter(int agentCharId) {
        synchronized (mutationLock) {
            ActiveSession session = activeSessionsByAgentId.get(agentCharId);
            if (session == null) {
                return null;
            }
            return unregisterEntry(session.entry()) ? session.entry() : null;
        }
    }

    public static List<AgentRuntimeEntry> unregisterLeader(int leaderCharId) {
        return unregisterCohort(leaderCharId);
    }

    public static List<AgentRuntimeEntry> unregisterCohort(long cohortId) {
        synchronized (mutationLock) {
            List<AgentRuntimeEntry> entries = entriesByCohortId.remove(cohortId);
            if (entries == null) {
                return List.of();
            }
            entriesByLeaderId.remove(Math.toIntExact(cohortId));
            entries.forEach(AgentRuntimeRegistry::unindex);
            return List.copyOf(entries);
        }
    }

    public static int leaderIdForAgentCharacter(int agentCharId) {
        ActiveSession session = activeSessionsByAgentId.get(agentCharId);
        return session == null ? -1 : Math.toIntExact(session.cohortId());
    }

    public static long cohortIdForAgentCharacter(int agentCharId) {
        ActiveSession session = activeSessionsByAgentId.get(agentCharId);
        return session == null ? 0L : session.cohortId();
    }

    public static AgentRuntimeEntry findByCharacterInstance(Character agent) {
        if (agent == null) {
            return null;
        }
        AgentRuntimeEntry entry = findByAgentCharacterId(agent.getId());
        return AgentRuntimeIdentityRuntime.bot(entry) == agent ? entry : null;
    }

    public static void clear() {
        synchronized (mutationLock) {
            entriesByLeaderId.clear();
            entriesByCohortId.clear();
            activeSessionsByAgentId.clear();
        }
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

    public static AgentRuntimeEntry findByAgentCharacterId(int agentCharId) {
        ActiveSession session = activeSessionsByAgentId.get(agentCharId);
        return session == null ? null : session.entry();
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
                    return AgentRelationshipRuntime.interactionTarget(entry);
                }
            }
        }
        return null;
    }

    public static Character activeLeaderByAgentCharacterId(int agentCharId) {
        return AgentRelationshipRuntime.interactionTarget(findByAgentCharacterId(agentCharId));
    }

    public static boolean hasActiveAgentCharacterId(int agentCharId) {
        return activeSessionsByAgentId.containsKey(agentCharId);
    }

    public static boolean isActiveSession(AgentRuntimeEntry expectedEntry, long expectedGeneration) {
        if (expectedEntry == null || expectedEntry.sessionGeneration() != expectedGeneration) {
            return false;
        }
        ActiveSession session = activeSessionsByAgentId.get(AgentRuntimeIdentityRuntime.botId(expectedEntry));
        return session != null && session.matches(expectedEntry, expectedGeneration);
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
        if (entry == null) {
            return false;
        }
        for (List<AgentRuntimeEntry> entries : entriesByLeaderId.values()) {
            if (entries.contains(entry)) {
                return !entries.isEmpty() && entries.getFirst() == entry;
            }
        }
        return false;
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

    public static List<AgentRuntimeEntry> entriesForCohort(long cohortId) {
        List<AgentRuntimeEntry> entries = entriesByCohortId.get(cohortId);
        return entries == null || entries.isEmpty() ? List.of() : List.copyOf(entries);
    }

    public static AgentRuntimeEntry findByNameInCohort(long cohortId, String agentName) {
        if (agentName == null) {
            return null;
        }
        for (AgentRuntimeEntry entry : entriesForCohort(cohortId)) {
            if (AgentRuntimeIdentityRuntime.botNameEquals(entry, agentName)) {
                return entry;
            }
        }
        return null;
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

    public static int activeAgentCount() {
        return activeSessionsByAgentId.size();
    }

    public static List<AgentRuntimeEntry> activeEntriesSnapshot() {
        return activeSessionsByAgentId.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getValue().entry())
                .toList();
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

    private static void index(long cohortId, AgentRuntimeEntry entry) {
        int agentCharId = AgentRuntimeIdentityRuntime.botId(entry);
        if (agentCharId >= 0) {
            activeSessionsByAgentId.put(
                    agentCharId,
                    new ActiveSession(cohortId, entry, entry.sessionGeneration()));
        }
    }

    private static void unindex(AgentRuntimeEntry entry) {
        int agentCharId = AgentRuntimeIdentityRuntime.botId(entry);
        if (agentCharId >= 0) {
            ActiveSession session = activeSessionsByAgentId.get(agentCharId);
            if (session != null && session.matches(entry, entry.sessionGeneration())) {
                activeSessionsByAgentId.remove(agentCharId, session);
            }
        }
    }

    private static void removeFromLeaderView(int leaderCharId, AgentRuntimeEntry entry) {
        List<AgentRuntimeEntry> entries = entriesByLeaderId.get(leaderCharId);
        if (entries == null) {
            return;
        }
        entries.remove(entry);
        if (entries.isEmpty()) {
            entriesByLeaderId.remove(leaderCharId, entries);
        }
    }

    private static void removeFromCohortView(long cohortId, AgentRuntimeEntry entry) {
        List<AgentRuntimeEntry> entries = entriesByCohortId.get(cohortId);
        if (entries == null) {
            return;
        }
        entries.remove(entry);
        if (entries.isEmpty()) {
            entriesByCohortId.remove(cohortId, entries);
        }
    }

}
