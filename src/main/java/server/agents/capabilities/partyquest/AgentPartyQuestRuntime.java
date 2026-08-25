package server.agents.capabilities.partyquest;

import client.Character;
import server.agents.capabilities.partyquest.hpq.AgentHpqLobbyAdmissionRuntime;
import server.agents.capabilities.partyquest.hpq.AgentHpqRuntime;
import server.agents.capabilities.partyquest.hpq.AgentHpqSession;
import server.agents.capabilities.partyquest.hpq.AgentHpqSessionRegistry;
import server.agents.capabilities.partyquest.kpq.AgentKpqLobbyAdmissionRuntime;
import server.agents.capabilities.partyquest.kpq.AgentKpqRuntime;
import server.agents.capabilities.partyquest.kpq.AgentKpqSession;
import server.agents.capabilities.partyquest.kpq.AgentKpqSessionRegistry;
import server.agents.capabilities.partyquest.lpq.AgentLpqRuntime;
import server.agents.capabilities.partyquest.lpq.AgentLpqSession;
import server.agents.capabilities.partyquest.lpq.AgentLpqSessionRegistry;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.session.AgentActivityAdmissionResult;

import java.util.List;

/** Shared router over isolated PQ systems. It owns no stage rules or tuning. */
public final class AgentPartyQuestRuntime {
    private static final List<AgentPartyQuestSystem> SYSTEMS = List.of(
            new HpqSystem(), new KpqSystem(), new LpqSystem());

    private AgentPartyQuestRuntime() {
    }

    public static AgentPartyQuestSystem requireSystem(String questKey) {
        String key = AgentPartyQuestDefinition.normalize(questKey);
        return SYSTEMS.stream().filter(system -> system.definition().questKey().equals(key))
                .findFirst().orElseThrow(() ->
                        new IllegalArgumentException("party-quest system is not implemented: " + key));
    }

    public static boolean active(int characterId) {
        return AgentPartyQuestEngagementRegistry.active(characterId)
                || SYSTEMS.stream().anyMatch(system -> system.sessionActive(characterId));
    }

    public static boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
        AgentPartyQuestSystem system = systemFor(agent.getId());
        if (system != null) return system.tick(entry, agent, nowMs);
        return AgentPartyQuestLifecycleRuntime.tick(agent.getId(), nowMs);
    }

    public static boolean requestStop(int characterId, String reason, long nowMs) {
        AgentPartyQuestSystem system = systemFor(characterId);
        return system == null
                ? AgentPartyQuestLifecycleRuntime.requestStop(characterId, reason, nowMs)
                : system.requestStop(characterId, reason, nowMs);
    }

    public static void forceStop(int characterId, String reason, long nowMs) {
        AgentPartyQuestSystem system = systemFor(characterId);
        if (system == null) AgentPartyQuestLifecycleRuntime.forceStop(characterId, reason, nowMs);
        else system.forceStop(characterId, reason, nowMs);
    }

    public static void runtimeRemoved(int characterId, long nowMs) {
        AgentPartyQuestSystem system = systemFor(characterId);
        if (system == null) {
            AgentPartyQuestLifecycleRuntime.runtimeRemoved(
                    characterId, "Agent runtime was removed", nowMs);
        } else {
            system.runtimeRemoved(characterId, nowMs);
        }
    }

    public static AgentPartyQuestSessionView sessionView(int characterId) {
        AgentPartyQuestSystem system = systemFor(characterId);
        return system == null ? null : system.sessionView(characterId);
    }

    public static boolean pause(int characterId) {
        AgentPartyQuestSystem system = systemFor(characterId);
        return system != null && system.pause(characterId);
    }

    public static boolean resumeExact(int characterId, String sessionId, long nowMs) {
        AgentPartyQuestSystem system = systemFor(characterId);
        return system != null && system.resumeExact(characterId, sessionId, nowMs);
    }

    private static AgentPartyQuestSystem systemFor(int characterId) {
        AgentPartyQuestEngagement engagement = AgentPartyQuestEngagementRegistry.forMember(characterId);
        if (engagement != null) {
            return SYSTEMS.stream()
                    .filter(system -> system.definition().questKey().equals(engagement.questKey()))
                    .findFirst().orElse(null);
        }
        return SYSTEMS.stream().filter(system -> system.sessionActive(characterId))
                .findFirst().orElse(null);
    }

    private static final class HpqSystem implements AgentPartyQuestSystem {
        @Override public AgentPartyQuestDefinition definition() {
            return AgentPartyQuestCatalog.require("hpq");
        }

        @Override public boolean sessionActive(int characterId) {
            return AgentHpqSessionRegistry.active(characterId);
        }

        @Override public boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
            return AgentHpqRuntime.tick(entry, agent, nowMs);
        }

        @Override public boolean requestStop(int characterId, String reason, long nowMs) {
            return AgentHpqRuntime.requestStop(characterId, reason, nowMs);
        }

        @Override public void forceStop(int characterId, String reason, long nowMs) {
            AgentHpqRuntime.forceStop(characterId, reason, nowMs);
        }

        @Override public void runtimeRemoved(int characterId, long nowMs) {
            AgentHpqRuntime.runtimeRemoved(characterId, nowMs);
        }

        @Override public AgentPartyQuestSessionView sessionView(int characterId) {
            AgentHpqSession session = AgentHpqSessionRegistry.forMember(characterId);
            if (session == null) return null;
            AgentPartyQuestSessionView.Phase phase = switch (session.phase()) {
                case EXITING -> AgentPartyQuestSessionView.Phase.DRAINING;
                case COMPLETED -> AgentPartyQuestSessionView.Phase.COMPLETED;
                case FAILED -> AgentPartyQuestSessionView.Phase.FAILED;
                default -> session.paused() ? AgentPartyQuestSessionView.Phase.SUSPENDED
                        : AgentPartyQuestSessionView.Phase.ACTIVE;
            };
            return new AgentPartyQuestSessionView(
                    "hpq", session.sessionId(), phase, session.executionAgentId(),
                    session.memberCount(), session.mode().name(), session.failure(),
                    session.startedAtMs(), session.lastProgressAtMs());
        }

        @Override public boolean pause(int characterId) {
            AgentHpqSession session = AgentHpqSessionRegistry.forMember(characterId);
            if (session == null || session.paused()) return false;
            session.setPaused(true);
            return true;
        }

        @Override public boolean resumeExact(int characterId, String sessionId, long nowMs) {
            AgentHpqSession session = AgentHpqSessionRegistry.forMember(characterId);
            if (session == null || !session.sessionId().equals(sessionId) || !session.paused()) return false;
            session.setPaused(false);
            session.markProgress(nowMs);
            return true;
        }

        @Override public AgentActivityAdmissionResult requestEntry(
                AgentRuntimeEntry entry, Character agent, String scenarioId,
                int partySize, int maximumRuns, long nowMs) {
            return AgentHpqLobbyAdmissionRuntime.requestEntry(
                    entry, agent, scenarioId, partySize, maximumRuns, nowMs);
        }

        @Override public String entryBlocker(
                Character agent, String scenarioId, int partySize, int maximumRuns) {
            return AgentHpqLobbyAdmissionRuntime.blocker(
                    agent, scenarioId, partySize, maximumRuns);
        }
    }

    private static final class KpqSystem implements AgentPartyQuestSystem {
        @Override
        public AgentPartyQuestDefinition definition() {
            return AgentPartyQuestCatalog.require("kpq");
        }

        @Override public boolean sessionActive(int characterId) {
            return AgentKpqSessionRegistry.active(characterId);
        }

        @Override public boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
            return AgentKpqRuntime.tick(entry, agent, nowMs);
        }

        @Override public boolean requestStop(int characterId, String reason, long nowMs) {
            return AgentKpqRuntime.requestStop(characterId, reason, nowMs);
        }

        @Override public void forceStop(int characterId, String reason, long nowMs) {
            AgentKpqRuntime.forceStop(characterId, reason, nowMs);
        }

        @Override public void runtimeRemoved(int characterId, long nowMs) {
            AgentKpqRuntime.runtimeRemoved(characterId, nowMs);
        }

        @Override public AgentPartyQuestSessionView sessionView(int characterId) {
            AgentKpqSession session = AgentKpqSessionRegistry.forMember(characterId);
            if (session == null) return null;
            AgentPartyQuestSessionView.Phase phase = switch (session.phase()) {
                case EXITING -> AgentPartyQuestSessionView.Phase.DRAINING;
                case COMPLETED -> AgentPartyQuestSessionView.Phase.COMPLETED;
                case FAILED -> AgentPartyQuestSessionView.Phase.FAILED;
                default -> session.paused() ? AgentPartyQuestSessionView.Phase.SUSPENDED
                        : AgentPartyQuestSessionView.Phase.ACTIVE;
            };
            int caller = session.formationCallerId() > 0
                    ? session.formationCallerId() : session.operatorId();
            return new AgentPartyQuestSessionView(
                    "kpq", session.sessionId(), phase, caller, session.memberCount(),
                    session.mode().name(), session.failure(), session.startedAtMs(),
                    Math.max(session.startedAtMs(), session.lastProgressAtMs()));
        }

        @Override public boolean pause(int characterId) {
            AgentKpqSession session = AgentKpqSessionRegistry.forMember(characterId);
            if (session == null || session.paused()) return false;
            session.setPaused(true);
            return true;
        }

        @Override public boolean resumeExact(int characterId, String sessionId, long nowMs) {
            AgentKpqSession session = AgentKpqSessionRegistry.forMember(characterId);
            if (session == null || !session.sessionId().equals(sessionId) || !session.paused()) {
                return false;
            }
            session.setPaused(false);
            session.markProgress(nowMs);
            return true;
        }

        @Override public AgentActivityAdmissionResult requestEntry(
                AgentRuntimeEntry entry, Character agent, String scenarioId,
                int partySize, int maximumRuns, long nowMs) {
            return AgentKpqLobbyAdmissionRuntime.requestEntry(
                    entry, agent, scenarioId, partySize, maximumRuns, nowMs);
        }

        @Override public String entryBlocker(
                Character agent, String scenarioId, int partySize, int maximumRuns) {
            return AgentKpqLobbyAdmissionRuntime.blocker(
                    agent, scenarioId, partySize, maximumRuns);
        }
    }

    private static final class LpqSystem implements AgentPartyQuestSystem {
        @Override public AgentPartyQuestDefinition definition() { return AgentPartyQuestCatalog.require("lpq"); }
        @Override public boolean sessionActive(int characterId) { return AgentLpqSessionRegistry.active(characterId); }
        @Override public boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
            return AgentLpqRuntime.tick(entry, agent, nowMs);
        }
        @Override public boolean requestStop(int characterId, String reason, long nowMs) {
            return AgentLpqRuntime.requestStop(characterId, reason, nowMs);
        }
        @Override public void forceStop(int characterId, String reason, long nowMs) {
            AgentLpqRuntime.forceStop(characterId, reason, nowMs);
        }
        @Override public void runtimeRemoved(int characterId, long nowMs) {
            AgentLpqRuntime.runtimeRemoved(characterId, nowMs);
        }
        @Override public AgentPartyQuestSessionView sessionView(int characterId) {
            AgentLpqSession session = AgentLpqSessionRegistry.forMember(characterId);
            if (session == null) return null;
            AgentPartyQuestSessionView.Phase phase = switch (session.phase()) {
                case EXITING -> AgentPartyQuestSessionView.Phase.DRAINING;
                case COMPLETED -> AgentPartyQuestSessionView.Phase.COMPLETED;
                case FAILED -> AgentPartyQuestSessionView.Phase.FAILED;
                default -> session.paused() ? AgentPartyQuestSessionView.Phase.SUSPENDED
                        : AgentPartyQuestSessionView.Phase.ACTIVE;
            };
            return new AgentPartyQuestSessionView("lpq", session.sessionId(), phase,
                    session.executionAgentId(), session.memberCount(), session.mode().name(),
                    session.failure(), session.startedAtMs(), session.lastProgressAtMs());
        }
        @Override public boolean pause(int characterId) {
            AgentLpqSession session = AgentLpqSessionRegistry.forMember(characterId);
            if (session == null || session.paused()) return false;
            session.setPaused(true);
            return true;
        }
        @Override public boolean resumeExact(int characterId, String sessionId, long nowMs) {
            AgentLpqSession session = AgentLpqSessionRegistry.forMember(characterId);
            if (session == null || !session.sessionId().equals(sessionId) || !session.paused()) return false;
            session.setPaused(false);
            session.markProgress(nowMs);
            return true;
        }
        @Override public AgentActivityAdmissionResult requestEntry(
                AgentRuntimeEntry entry, Character agent, String scenarioId,
                int partySize, int maximumRuns, long nowMs) {
            return AgentActivityAdmissionResult.rejected(
                    "LPQ background population remains disabled until live observation gates pass");
        }
        @Override public String entryBlocker(
                Character agent, String scenarioId, int partySize, int maximumRuns) {
            return "LPQ background population remains disabled until live observation gates pass";
        }
    }
}
