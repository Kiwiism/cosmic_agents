package server.agents.capabilities.dialogue.conversation;

import client.Character;
import config.YamlConfig;
import server.agents.capabilities.dialogue.semantic.AgentDialogueMetrics;
import server.agents.capabilities.dialogue.semantic.AgentDialogueRuntimeSnapshot;
import server.agents.capabilities.dialogue.semantic.AgentDialogueTopicRegistry;
import server.agents.capabilities.dialogue.semantic.AgentSemanticDialogueAct;
import server.agents.capabilities.dialogue.semantic.AgentSemanticDialogueRuntime;
import server.agents.coordination.AgentConversationCoordinationMessage;
import server.agents.coordination.AgentCoordinationEnvelope;
import server.agents.coordination.AgentCoordinationPublishResult;
import server.agents.coordination.AgentCoordinationRuntime;
import server.agents.coordination.AgentCoordinationScope;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded, non-blocking pair sessions layered beside active Agent capabilities. */
public final class AgentConversationRuntime {
    private static final long DIRECT_ROUTE_BASE = 1_000_000_000L;
    private static final int MAX_DIRECT_MESSAGES_PER_TICK = 8;
    private static final AtomicLong nextConversationId = new AtomicLong();
    private static final AgentStoryletConversationModel model = new AgentStoryletConversationModel();
    private static final Map<Long, Session> sessions = new HashMap<>();
    private static final Map<Integer, Long> sessionByAgentId = new HashMap<>();
    private static final Map<Integer, Long> nextEligibleAtMs = new HashMap<>();
    private static final Map<Integer, Long> nextTickAtMs = new HashMap<>();
    private static long nextCleanupAtMs;
    private static long nextMatchmakingAtMs;

    private AgentConversationRuntime() {
    }

    public static synchronized void tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
        try {
            tickInternal(entry, agent, nowMs);
        } catch (RuntimeException ignored) {
            AgentDialogueMetrics.recordFailure();
            if (agent != null) {
                Long conversationId = sessionByAgentId.get(agent.getId());
                if (conversationId != null) {
                    end(sessions.get(conversationId), nowMs, false);
                }
            }
        }
    }

    private static void tickInternal(AgentRuntimeEntry entry, Character agent, long nowMs) {
        if (entry == null || agent == null || agent.getMap() == null) {
            return;
        }
        if (nowMs < nextTickAtMs.getOrDefault(agent.getId(), 0L)) {
            return;
        }
        nextTickAtMs.put(agent.getId(), saturatedAdd(nowMs, tickInterval()));
        drainDirectMessages(agent.getId(), nowMs);
        cleanupExpired(nowMs);
        if (!enabled() || agent.getHp() <= 0) {
            return;
        }
        Long conversationId = sessionByAgentId.get(agent.getId());
        if (conversationId != null) {
            advance(entry, agent, sessions.get(conversationId), nowMs);
            return;
        }
        Long eligibleAtMs = nextEligibleAtMs.get(agent.getId());
        if (eligibleAtMs == null) {
            nextEligibleAtMs.put(agent.getId(), saturatedAdd(nowMs,
                    2_000L + deterministicJitter(agent.getId(), 3_001)));
            return;
        }
        if (nowMs < eligibleAtMs) {
            return;
        }
        runMatchmaking(nowMs);
    }

    public static synchronized void leave(AgentRuntimeEntry entry) {
        if (entry == null || entry.bot() == null) {
            return;
        }
        Long conversationId = sessionByAgentId.get(entry.bot().getId());
        if (conversationId != null) {
            Session departing = sessions.get(conversationId);
            int otherAgentId = departing == null ? 0 : departing.other(entry.bot().getId());
            end(departing, System.currentTimeMillis(), false);
            if (otherAgentId > 0) {
                AgentCoordinationRuntime.discardRoute(
                        AgentCoordinationScope.AGENT, directRoute(otherAgentId));
            }
        }
        nextEligibleAtMs.remove(entry.bot().getId());
        nextTickAtMs.remove(entry.bot().getId());
        AgentCoordinationRuntime.discardRoute(
                AgentCoordinationScope.AGENT, directRoute(entry.bot().getId()));
    }

    public static synchronized List<AgentConversationSessionView> sessionsSnapshot() {
        return sessions.values().stream()
                .sorted(Comparator.comparingLong(session -> session.conversationId))
                .map(Session::view)
                .toList();
    }

    public static synchronized AgentDialogueRuntimeSnapshot snapshot() {
        return AgentDialogueMetrics.snapshot(sessions.size());
    }

    static synchronized void resetForTests() {
        sessions.clear();
        sessionByAgentId.clear();
        nextEligibleAtMs.clear();
        nextTickAtMs.clear();
        nextConversationId.set(0L);
        nextCleanupAtMs = 0L;
        nextMatchmakingAtMs = 0L;
    }

    private static void runMatchmaking(long nowMs) {
        if (nowMs < nextMatchmakingAtMs) {
            return;
        }
        nextMatchmakingAtMs = saturatedAdd(nowMs, 1_000L);
        Map<MapKey, List<AgentRuntimeEntry>> candidatesByMap = new HashMap<>();
        for (AgentRuntimeEntry candidate : AgentRuntimeRegistry.activeEntriesSnapshot()) {
            Character agent = candidate.bot();
            Long eligibleAtMs = agent == null ? null : nextEligibleAtMs.get(agent.getId());
            if (agent == null || agent.getMap() == null || agent.getHp() <= 0
                    || sessionByAgentId.containsKey(agent.getId())
                    || eligibleAtMs == null || nowMs < eligibleAtMs || !canSimulate(agent)) {
                continue;
            }
            candidatesByMap.computeIfAbsent(mapKey(agent), ignored -> new ArrayList<>()).add(candidate);
        }
        long window = nowMs / attemptInterval();
        for (Map.Entry<MapKey, List<AgentRuntimeEntry>> mapEntry : candidatesByMap.entrySet()) {
            List<AgentRuntimeEntry> candidates = mapEntry.getValue();
            candidates.sort(Comparator.comparingLong(candidate -> deterministicScore(
                    candidate.bot().getId(), mapEntry.getKey().mapId(), window)));
            int availableSessions = Math.max(0,
                    maxSessionsPerMap() - activeSessionsIn(mapEntry.getKey()));
            for (AgentRuntimeEntry candidate : candidates) {
                int agentId = candidate.bot().getId();
                nextEligibleAtMs.put(agentId, saturatedAdd(nowMs,
                        attemptInterval() + deterministicJitter(agentId, 2_000)));
            }
            for (int index = 0; index + 1 < candidates.size() && availableSessions > 0; index += 2) {
                if (start(candidates.get(index), candidates.get(index + 1), nowMs, window)) {
                    availableSessions--;
                }
            }
        }
    }

    private static boolean start(AgentRuntimeEntry first,
                                 AgentRuntimeEntry second,
                                 long nowMs,
                                 long window) {
        Character firstAgent = first.bot();
        Character secondAgent = second.bot();
        int sociability = (model.sociability(first) + model.sociability(second)) / 2;
        long seed = deterministicScore(firstAgent.getId(), secondAgent.getId(), window);
        if (Math.floorMod(seed, 100L) >= sociability) {
            return false;
        }
        String topicId = model.selectTopic(first, second, nowMs, seed);
        if (topicId == null) {
            return false;
        }
        int maxTurns = Math.max(2, YamlConfig.config.server.AGENT_CONVERSATION_MAX_TURNS);
        long conversationId = nextConversationId.incrementAndGet();
        Session session = new Session(
                conversationId, mapKey(firstAgent), firstAgent.getId(), secondAgent.getId(),
                topicId, nowMs,
                saturatedAdd(nowMs, sessionTimeout()), maxTurns,
                saturatedAdd(nowMs, 350L + deterministicJitter(firstAgent.getId(), 650)),
                firstAgent.getId());
        sessions.put(conversationId, session);
        sessionByAgentId.put(session.firstAgentId, conversationId);
        sessionByAgentId.put(session.secondAgentId, conversationId);
        AgentDialogueMetrics.recordSessionStarted();
        return true;
    }

    private static void advance(AgentRuntimeEntry speakerEntry,
                                Character speaker,
                                Session session,
                                long nowMs) {
        if (session == null || session.nextSpeakerId != speaker.getId()
                || nowMs < session.nextTurnAtMs
                || !session.canSpeak(speaker.getId())) {
            return;
        }
        AgentRuntimeEntry listenerEntry = AgentRuntimeRegistry.findByAgentCharacterId(
                session.other(speaker.getId()));
        Character listener = listenerEntry == null ? null : listenerEntry.bot();
        if (listener == null || !sameMap(speaker, listener)) {
            end(session, nowMs, false);
            return;
        }
        AgentConversationModelContext context = new AgentConversationModelContext(
                speakerEntry, listenerEntry, session.conversationId, session.topicId,
                session.completedTurns, session.maxTurns, nowMs,
                session.conversationId * 31L + session.completedTurns * 17L + speaker.getId());
        AgentSemanticDialogueAct act = model.produce(context);
        AgentSemanticDialogueRuntime.emit(speakerEntry, act);
        publishDirect(session, act, speaker, listener);
        session.completedTurns++;
        if (session.completedTurns >= session.maxTurns) {
            end(session, nowMs, true);
            return;
        }
        session.nextSpeakerId = listener.getId();
        session.nextTurnAtMs = saturatedAdd(nowMs,
                turnInterval() + deterministicJitter(listener.getId() + session.completedTurns, 700));
    }

    private static void publishDirect(Session session,
                                      AgentSemanticDialogueAct act,
                                      Character speaker,
                                      Character listener) {
        AgentConversationCoordinationMessage message = new AgentConversationCoordinationMessage(
                speaker.getId(), listener.getId(),
                AgentRuntimeRegistry.cohortIdForAgentCharacter(speaker.getId()),
                speaker.getMapId(), act.occurredAtMs(), session.conversationId,
                session.completedTurns, act.topicId(), act.actKey(), act.parameters());
        AgentCoordinationPublishResult result = AgentCoordinationRuntime.publishRouted(
                message, AgentCoordinationScope.AGENT, directRoute(listener.getId()), listener.getId(),
                sessionTimeout(), false, "conversation:" + session.conversationId
                        + ":" + session.completedTurns);
        if (result.accepted()) {
            AgentDialogueMetrics.recordCoordinationPublished();
        }
    }

    private static void drainDirectMessages(int agentId, long nowMs) {
        for (AgentCoordinationEnvelope envelope : AgentCoordinationRuntime.drain(
                AgentCoordinationScope.AGENT, directRoute(agentId),
                MAX_DIRECT_MESSAGES_PER_TICK, nowMs)) {
            if (envelope.message() instanceof AgentConversationCoordinationMessage message
                    && message.targetAgentCharacterId() == agentId) {
                Session session = sessions.get(message.conversationId());
                if (session != null) {
                    session.markReceived(agentId, message.turnIndex());
                }
                AgentDialogueMetrics.recordCoordinationDelivered();
            }
        }
        if (!sessionByAgentId.containsKey(agentId)) {
            AgentCoordinationRuntime.releaseRouteIfEmpty(
                    AgentCoordinationScope.AGENT, directRoute(agentId));
        }
    }

    private static void cleanupExpired(long nowMs) {
        if (nowMs < nextCleanupAtMs) {
            return;
        }
        nextCleanupAtMs = saturatedAdd(nowMs, 1_000L);
        List<Session> expired = sessions.values().stream()
                .filter(session -> nowMs >= session.expiresAtMs)
                .toList();
        expired.forEach(session -> end(session, nowMs, false));
        nextEligibleAtMs.entrySet().removeIf(entry ->
                entry.getValue() < nowMs - Math.max(sessionCooldown(), 60_000L)
                        && !AgentRuntimeRegistry.hasActiveAgentCharacterId(entry.getKey()));
        nextTickAtMs.keySet().removeIf(agentId ->
                !AgentRuntimeRegistry.hasActiveAgentCharacterId(agentId));
    }

    private static void end(Session session, long nowMs, boolean completed) {
        if (session == null || sessions.remove(session.conversationId) == null) {
            return;
        }
        sessionByAgentId.remove(session.firstAgentId, session.conversationId);
        sessionByAgentId.remove(session.secondAgentId, session.conversationId);
        long nextAt = saturatedAdd(nowMs, sessionCooldown());
        nextEligibleAtMs.put(session.firstAgentId, nextAt + deterministicJitter(session.firstAgentId, 3_000));
        nextEligibleAtMs.put(session.secondAgentId, nextAt + deterministicJitter(session.secondAgentId, 3_000));
        if (completed) {
            AgentDialogueMetrics.recordSessionCompleted();
        } else {
            AgentDialogueMetrics.recordSessionTimedOut();
        }
    }

    private static int activeSessionsIn(MapKey key) {
        return (int) sessions.values().stream().filter(session -> session.mapKey.equals(key)).count();
    }

    private static boolean canSimulate(Character agent) {
        return YamlConfig.config.server.AGENT_CONVERSATION_SIMULATE_UNOBSERVED
                || AgentMapGatewayRuntime.map().isObservedByPlayer(agent.getMap());
    }

    private static boolean enabled() {
        return AgentDialogueTopicRegistry.systemEnabled()
                && YamlConfig.config.server.AGENT_CONVERSATION_ENABLED;
    }

    private static boolean sameMap(Character first, Character second) {
        return first.getMap() != null && first.getMap() == second.getMap()
                && first.getWorld() == second.getWorld()
                && channel(first) == channel(second);
    }

    private static MapKey mapKey(Character agent) {
        return new MapKey(agent.getWorld(), channel(agent), agent.getMapId());
    }

    private static int channel(Character agent) {
        return agent.getClient() == null ? 0 : agent.getClient().getChannel();
    }

    private static long directRoute(int agentId) {
        return DIRECT_ROUTE_BASE + agentId;
    }

    private static int maxSessionsPerMap() {
        return Math.max(1, YamlConfig.config.server.AGENT_CONVERSATION_MAX_VISIBLE_SESSIONS_PER_MAP);
    }

    private static long attemptInterval() {
        return Math.max(1_000L, YamlConfig.config.server.AGENT_CONVERSATION_ATTEMPT_INTERVAL_MS);
    }

    private static long tickInterval() {
        return Math.max(50L, YamlConfig.config.server.AGENT_CONVERSATION_TICK_INTERVAL_MS);
    }

    private static long sessionCooldown() {
        return Math.max(0L, YamlConfig.config.server.AGENT_CONVERSATION_SESSION_COOLDOWN_MS);
    }

    private static long turnInterval() {
        return Math.max(500L, YamlConfig.config.server.AGENT_CONVERSATION_TURN_INTERVAL_MS);
    }

    private static long sessionTimeout() {
        return Math.max(5_000L, YamlConfig.config.server.AGENT_CONVERSATION_SESSION_TIMEOUT_MS);
    }

    private static long deterministicScore(int first, int second, long window) {
        long value = (((long) first) << 32) ^ Integer.toUnsignedLong(second) ^ window * 0x9e3779b97f4a7c15L;
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdl;
        value ^= value >>> 33;
        return value;
    }

    private static long deterministicJitter(int seed, int boundExclusive) {
        return boundExclusive <= 0 ? 0L : Math.floorMod(deterministicScore(seed, seed * 31, 1L), boundExclusive);
    }

    private static long saturatedAdd(long value, long increment) {
        return Long.MAX_VALUE - value < increment ? Long.MAX_VALUE : value + increment;
    }

    private record MapKey(int world, int channel, int mapId) {
    }

    private static final class Session {
        private final long conversationId;
        private final MapKey mapKey;
        private final int firstAgentId;
        private final int secondAgentId;
        private final String topicId;
        private final long startedAtMs;
        private final long expiresAtMs;
        private final int maxTurns;
        private int completedTurns;
        private long nextTurnAtMs;
        private int nextSpeakerId;
        private int firstReceivedThrough = -1;
        private int secondReceivedThrough = -1;

        private Session(long conversationId,
                        MapKey mapKey,
                        int firstAgentId,
                        int secondAgentId,
                        String topicId,
                        long startedAtMs,
                        long expiresAtMs,
                        int maxTurns,
                        long nextTurnAtMs,
                        int nextSpeakerId) {
            this.conversationId = conversationId;
            this.mapKey = mapKey;
            this.firstAgentId = firstAgentId;
            this.secondAgentId = secondAgentId;
            this.topicId = topicId;
            this.startedAtMs = startedAtMs;
            this.expiresAtMs = expiresAtMs;
            this.maxTurns = maxTurns;
            this.nextTurnAtMs = nextTurnAtMs;
            this.nextSpeakerId = nextSpeakerId;
        }

        private int other(int agentId) {
            return agentId == firstAgentId ? secondAgentId : firstAgentId;
        }

        private boolean canSpeak(int agentId) {
            if (completedTurns == 0) {
                return true;
            }
            return (agentId == firstAgentId ? firstReceivedThrough : secondReceivedThrough)
                    >= completedTurns - 1;
        }

        private void markReceived(int agentId, int turnIndex) {
            if (agentId == firstAgentId) {
                firstReceivedThrough = Math.max(firstReceivedThrough, turnIndex);
            } else if (agentId == secondAgentId) {
                secondReceivedThrough = Math.max(secondReceivedThrough, turnIndex);
            }
        }

        private AgentConversationSessionView view() {
            return new AgentConversationSessionView(
                    conversationId, firstAgentId, secondAgentId, mapKey.mapId,
                    topicId, completedTurns, maxTurns, startedAtMs, expiresAtMs);
        }
    }
}
