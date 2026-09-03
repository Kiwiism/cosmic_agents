package server.agents.capabilities.partyquest;

import client.Character;
import server.agents.capabilities.partyquest.epq.AgentEpqRuntime;
import server.agents.capabilities.partyquest.epq.AgentEpqLobbyAdmissionRuntime;
import server.agents.capabilities.partyquest.epq.AgentEpqLobbyProfile;
import server.agents.capabilities.partyquest.epq.AgentEpqSession;
import server.agents.capabilities.partyquest.epq.AgentEpqSessionRegistry;
import server.agents.capabilities.partyquest.hpq.AgentHpqLobbyAdmissionRuntime;
import server.agents.capabilities.partyquest.hpq.AgentHpqLobbyProfile;
import server.agents.capabilities.partyquest.hpq.AgentHpqRuntime;
import server.agents.capabilities.partyquest.hpq.AgentHpqSession;
import server.agents.capabilities.partyquest.hpq.AgentHpqSessionRegistry;
import server.agents.capabilities.partyquest.kpq.AgentKpqLobbyAdmissionRuntime;
import server.agents.capabilities.partyquest.kpq.AgentKpqLobbyProfile;
import server.agents.capabilities.partyquest.kpq.AgentKpqRuntime;
import server.agents.capabilities.partyquest.kpq.AgentKpqSession;
import server.agents.capabilities.partyquest.kpq.AgentKpqSessionRegistry;
import server.agents.capabilities.partyquest.lpq.AgentLpqRuntime;
import server.agents.capabilities.partyquest.lpq.AgentLpqLobbyAdmissionRuntime;
import server.agents.capabilities.partyquest.lpq.AgentLpqLobbyProfile;
import server.agents.capabilities.partyquest.lpq.AgentLpqSession;
import server.agents.capabilities.partyquest.lpq.AgentLpqSessionRegistry;
import server.agents.capabilities.partyquest.lmpq.AgentLmpqLobbyAdmissionRuntime;
import server.agents.capabilities.partyquest.lmpq.AgentLmpqLobbyProfile;
import server.agents.capabilities.partyquest.lmpq.AgentLmpqRuntime;
import server.agents.capabilities.partyquest.lmpq.AgentLmpqSession;
import server.agents.capabilities.partyquest.lmpq.AgentLmpqSessionRegistry;
import server.agents.capabilities.partyquest.opq.AgentOpqRuntime;
import server.agents.capabilities.partyquest.opq.AgentOpqLobbyAdmissionRuntime;
import server.agents.capabilities.partyquest.opq.AgentOpqLobbyProfile;
import server.agents.capabilities.partyquest.opq.AgentOpqSession;
import server.agents.capabilities.partyquest.opq.AgentOpqSessionRegistry;
import server.agents.capabilities.partyquest.ppq.AgentPpqRuntime;
import server.agents.capabilities.partyquest.ppq.AgentPpqLobbyAdmissionRuntime;
import server.agents.capabilities.partyquest.ppq.AgentPpqLobbyProfile;
import server.agents.capabilities.partyquest.ppq.AgentPpqSession;
import server.agents.capabilities.partyquest.ppq.AgentPpqSessionRegistry;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestQueueRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.session.AgentActivityAdmissionResult;

import java.util.List;

/** Shared router over isolated PQ systems. It owns no stage rules or tuning. */
public final class AgentPartyQuestRuntime {
    private static final List<AgentPartyQuestSystem> SYSTEMS = List.of(
            new HpqSystem(), new KpqSystem(), new LpqSystem(), new LmpqSystem(),
            new OpqSystem(), new EpqSystem(), new PpqSystem());

    private AgentPartyQuestRuntime() {
    }

    public static AgentPartyQuestSystem requireSystem(String questKey) {
        String key = AgentPartyQuestDefinition.normalize(questKey);
        return SYSTEMS.stream().filter(system -> system.definition().questKey().equals(key))
                .findFirst().orElseThrow(() ->
                        new IllegalArgumentException("party-quest system is not implemented: " + key));
    }

    public static boolean active(int characterId) {
        return AgentPartyQuestQueueRuntime.active(characterId)
                || AgentPartyQuestEngagementRegistry.active(characterId)
                || SYSTEMS.stream().anyMatch(system -> system.sessionActive(characterId));
    }

    public static boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
        if (AgentPartyQuestQueueRuntime.active(agent.getId())) {
            return AgentPartyQuestQueueRuntime.tick(agent.getId(), nowMs);
        }
        AgentPartyQuestSystem system = systemFor(agent.getId());
        if (system != null) return system.tick(entry, agent, nowMs);
        return AgentPartyQuestLifecycleRuntime.tick(agent.getId(), nowMs);
    }

    public static boolean requestStop(int characterId, String reason, long nowMs) {
        if (AgentPartyQuestQueueRuntime.active(characterId)) {
            return AgentPartyQuestQueueRuntime.requestStop(characterId, reason, nowMs);
        }
        AgentPartyQuestSystem system = systemFor(characterId);
        return system == null
                ? AgentPartyQuestLifecycleRuntime.requestStop(characterId, reason, nowMs)
                : system.requestStop(characterId, reason, nowMs);
    }

    public static void forceStop(int characterId, String reason, long nowMs) {
        if (AgentPartyQuestQueueRuntime.active(characterId)) {
            AgentPartyQuestQueueRuntime.forceStop(characterId, reason, nowMs);
            return;
        }
        AgentPartyQuestSystem system = systemFor(characterId);
        if (system == null) AgentPartyQuestLifecycleRuntime.forceStop(characterId, reason, nowMs);
        else system.forceStop(characterId, reason, nowMs);
    }

    public static void runtimeRemoved(int characterId, long nowMs) {
        if (AgentPartyQuestQueueRuntime.active(characterId)) {
            AgentPartyQuestQueueRuntime.forceStop(
                    characterId, "Agent runtime was removed", nowMs);
            return;
        }
        AgentPartyQuestSystem system = systemFor(characterId);
        if (system == null) {
            AgentPartyQuestLifecycleRuntime.runtimeRemoved(
                    characterId, "Agent runtime was removed", nowMs);
        } else {
            system.runtimeRemoved(characterId, nowMs);
        }
    }

    public static AgentPartyQuestSessionView sessionView(int characterId) {
        AgentPartyQuestSessionView queued = AgentPartyQuestQueueRuntime.sessionView(characterId);
        if (queued != null && AgentPartyQuestQueueRuntime.active(characterId)) return queued;
        AgentPartyQuestSystem system = systemFor(characterId);
        return system == null ? queued : system.sessionView(characterId);
    }

    public static boolean pause(int characterId) {
        if (AgentPartyQuestQueueRuntime.active(characterId)) {
            return AgentPartyQuestQueueRuntime.pause(characterId);
        }
        AgentPartyQuestSystem system = systemFor(characterId);
        return system != null && system.pause(characterId);
    }

    public static boolean resumeExact(int characterId, String sessionId, long nowMs) {
        if (AgentPartyQuestQueueRuntime.active(characterId)) {
            return AgentPartyQuestQueueRuntime.resumeExact(characterId, sessionId, nowMs);
        }
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
            return AgentPartyQuestQueueRuntime.requestEntry(
                    AgentHpqLobbyProfile.profile(), entry, agent, scenarioId,
                    partySize, maximumRuns, nowMs, AgentHpqLobbyAdmissionRuntime::requestEntry);
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
            return AgentPartyQuestQueueRuntime.requestEntry(
                    AgentKpqLobbyProfile.profile(), entry, agent, scenarioId,
                    partySize, maximumRuns, nowMs, AgentKpqLobbyAdmissionRuntime::requestEntry);
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
            return AgentPartyQuestQueueRuntime.requestEntry(
                    AgentLpqLobbyProfile.profile(), entry, agent, scenarioId,
                    partySize, maximumRuns, nowMs, AgentLpqLobbyAdmissionRuntime::requestEntry);
        }
        @Override public String entryBlocker(
                Character agent, String scenarioId, int partySize, int maximumRuns) {
            return AgentLpqLobbyAdmissionRuntime.blocker(
                    agent, scenarioId, partySize, maximumRuns);
        }
    }

    private static final class LmpqSystem implements AgentPartyQuestSystem {
        @Override public AgentPartyQuestDefinition definition() { return AgentPartyQuestCatalog.require("lmpq"); }
        @Override public boolean sessionActive(int characterId) { return AgentLmpqSessionRegistry.active(characterId); }
        @Override public boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
            return AgentLmpqRuntime.tick(entry, agent, nowMs);
        }
        @Override public boolean requestStop(int characterId, String reason, long nowMs) {
            return AgentLmpqRuntime.requestStop(characterId, reason, nowMs);
        }
        @Override public void forceStop(int characterId, String reason, long nowMs) {
            AgentLmpqRuntime.forceStop(characterId, reason, nowMs);
        }
        @Override public void runtimeRemoved(int characterId, long nowMs) {
            AgentLmpqRuntime.runtimeRemoved(characterId, nowMs);
        }
        @Override public AgentPartyQuestSessionView sessionView(int characterId) {
            AgentLmpqSession session = AgentLmpqSessionRegistry.forMember(characterId);
            if (session == null) return null;
            AgentPartyQuestSessionView.Phase phase = switch (session.phase()) {
                case EXITING -> AgentPartyQuestSessionView.Phase.DRAINING;
                case COMPLETED -> AgentPartyQuestSessionView.Phase.COMPLETED;
                case FAILED -> AgentPartyQuestSessionView.Phase.FAILED;
                default -> session.paused() ? AgentPartyQuestSessionView.Phase.SUSPENDED
                        : AgentPartyQuestSessionView.Phase.ACTIVE;
            };
            return new AgentPartyQuestSessionView("lmpq", session.sessionId(), phase,
                    session.executionAgentId(), session.memberCount(), session.mode().name(),
                    session.failure(), session.startedAtMs(), session.lastProgressAtMs());
        }
        @Override public boolean pause(int characterId) {
            AgentLmpqSession session = AgentLmpqSessionRegistry.forMember(characterId);
            if (session == null || session.paused()) return false;
            session.setPaused(true);
            return true;
        }
        @Override public boolean resumeExact(int characterId, String sessionId, long nowMs) {
            AgentLmpqSession session = AgentLmpqSessionRegistry.forMember(characterId);
            if (session == null || !session.sessionId().equals(sessionId) || !session.paused()) return false;
            session.setPaused(false);
            session.markProgress(nowMs);
            return true;
        }
        @Override public AgentActivityAdmissionResult requestEntry(
                AgentRuntimeEntry entry, Character agent, String scenarioId,
                int partySize, int maximumRuns, long nowMs) {
            return AgentPartyQuestQueueRuntime.requestEntry(
                    AgentLmpqLobbyProfile.profile(), entry, agent, scenarioId,
                    partySize, maximumRuns, nowMs, AgentLmpqLobbyAdmissionRuntime::requestEntry);
        }
        @Override public String entryBlocker(
                Character agent, String scenarioId, int partySize, int maximumRuns) {
            return AgentLmpqLobbyAdmissionRuntime.blocker(agent, scenarioId, partySize, maximumRuns);
        }
    }

    private static final class OpqSystem implements AgentPartyQuestSystem {
        @Override public AgentPartyQuestDefinition definition() { return AgentPartyQuestCatalog.require("opq"); }
        @Override public boolean sessionActive(int characterId) { return AgentOpqSessionRegistry.active(characterId); }
        @Override public boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
            return AgentOpqRuntime.tick(entry, agent, nowMs);
        }
        @Override public boolean requestStop(int characterId, String reason, long nowMs) {
            return AgentOpqRuntime.requestStop(characterId, reason, nowMs);
        }
        @Override public void forceStop(int characterId, String reason, long nowMs) {
            AgentOpqRuntime.forceStop(characterId, reason, nowMs);
        }
        @Override public void runtimeRemoved(int characterId, long nowMs) {
            AgentOpqRuntime.runtimeRemoved(characterId, nowMs);
        }
        @Override public AgentPartyQuestSessionView sessionView(int characterId) {
            AgentOpqSession session = AgentOpqSessionRegistry.forMember(characterId);
            if (session == null) return null;
            AgentPartyQuestSessionView.Phase phase = switch (session.phase()) {
                case EXITING -> AgentPartyQuestSessionView.Phase.DRAINING;
                case COMPLETED -> AgentPartyQuestSessionView.Phase.COMPLETED;
                case FAILED -> AgentPartyQuestSessionView.Phase.FAILED;
                default -> session.paused() ? AgentPartyQuestSessionView.Phase.SUSPENDED
                        : AgentPartyQuestSessionView.Phase.ACTIVE;
            };
            return new AgentPartyQuestSessionView("opq", session.sessionId(), phase,
                    session.executionAgentId(), session.memberCount(), session.mode().name(),
                    session.failure(), session.startedAtMs(), session.lastProgressAtMs());
        }
        @Override public boolean pause(int characterId) {
            AgentOpqSession session = AgentOpqSessionRegistry.forMember(characterId);
            if (session == null || session.paused()) return false;
            session.setPaused(true); return true;
        }
        @Override public boolean resumeExact(int characterId, String sessionId, long nowMs) {
            AgentOpqSession session = AgentOpqSessionRegistry.forMember(characterId);
            if (session == null || !session.sessionId().equals(sessionId) || !session.paused()) return false;
            session.setPaused(false); session.markProgress(nowMs); return true;
        }
        @Override public AgentActivityAdmissionResult requestEntry(
                AgentRuntimeEntry entry, Character agent, String scenarioId,
                int partySize, int maximumRuns, long nowMs) {
            return AgentPartyQuestQueueRuntime.requestEntry(
                    AgentOpqLobbyProfile.profile(), entry, agent, scenarioId,
                    partySize, maximumRuns, nowMs, AgentOpqLobbyAdmissionRuntime::requestEntry);
        }
        @Override public String entryBlocker(
                Character agent, String scenarioId, int partySize, int maximumRuns) {
            return AgentOpqLobbyAdmissionRuntime.blocker(
                    agent, scenarioId, partySize, maximumRuns);
        }
    }

    private static final class EpqSystem implements AgentPartyQuestSystem {
        @Override public AgentPartyQuestDefinition definition() { return AgentPartyQuestCatalog.require("epq"); }
        @Override public boolean sessionActive(int characterId) { return AgentEpqSessionRegistry.active(characterId); }
        @Override public boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
            return AgentEpqRuntime.tick(entry, agent, nowMs);
        }
        @Override public boolean requestStop(int characterId, String reason, long nowMs) {
            return AgentEpqRuntime.requestStop(characterId, reason, nowMs);
        }
        @Override public void forceStop(int characterId, String reason, long nowMs) {
            AgentEpqRuntime.forceStop(characterId, reason, nowMs);
        }
        @Override public void runtimeRemoved(int characterId, long nowMs) {
            AgentEpqRuntime.runtimeRemoved(characterId, nowMs);
        }
        @Override public AgentPartyQuestSessionView sessionView(int characterId) {
            AgentEpqSession session = AgentEpqSessionRegistry.forMember(characterId);
            if (session == null) return null;
            AgentPartyQuestSessionView.Phase phase = switch (session.phase()) {
                case EXITING -> AgentPartyQuestSessionView.Phase.DRAINING;
                case COMPLETED -> AgentPartyQuestSessionView.Phase.COMPLETED;
                case FAILED -> AgentPartyQuestSessionView.Phase.FAILED;
                default -> session.paused() ? AgentPartyQuestSessionView.Phase.SUSPENDED
                        : AgentPartyQuestSessionView.Phase.ACTIVE;
            };
            return new AgentPartyQuestSessionView("epq", session.sessionId(), phase,
                    session.executionAgentId(), session.memberCount(), session.mode().name(),
                    session.failure(), session.startedAtMs(), session.lastProgressAtMs());
        }
        @Override public boolean pause(int characterId) {
            AgentEpqSession session = AgentEpqSessionRegistry.forMember(characterId);
            if (session == null || session.paused()) return false;
            session.setPaused(true);
            return true;
        }
        @Override public boolean resumeExact(int characterId, String sessionId, long nowMs) {
            AgentEpqSession session = AgentEpqSessionRegistry.forMember(characterId);
            if (session == null || !session.sessionId().equals(sessionId) || !session.paused()) return false;
            session.setPaused(false);
            session.markProgress(nowMs);
            return true;
        }
        @Override public AgentActivityAdmissionResult requestEntry(
                AgentRuntimeEntry entry, Character agent, String scenarioId,
                int partySize, int maximumRuns, long nowMs) {
            return AgentPartyQuestQueueRuntime.requestEntry(
                    AgentEpqLobbyProfile.profile(), entry, agent, scenarioId,
                    partySize, maximumRuns, nowMs, AgentEpqLobbyAdmissionRuntime::requestEntry);
        }
        @Override public String entryBlocker(
                Character agent, String scenarioId, int partySize, int maximumRuns) {
            return AgentEpqLobbyAdmissionRuntime.blocker(
                    agent, scenarioId, partySize, maximumRuns);
        }
    }

    private static final class PpqSystem implements AgentPartyQuestSystem {
        @Override public AgentPartyQuestDefinition definition() { return AgentPartyQuestCatalog.require("ppq"); }
        @Override public boolean sessionActive(int characterId) { return AgentPpqSessionRegistry.active(characterId); }
        @Override public boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
            return AgentPpqRuntime.tick(entry, agent, nowMs);
        }
        @Override public boolean requestStop(int characterId, String reason, long nowMs) {
            return AgentPpqRuntime.requestStop(characterId, reason, nowMs);
        }
        @Override public void forceStop(int characterId, String reason, long nowMs) {
            AgentPpqRuntime.forceStop(characterId, reason, nowMs);
        }
        @Override public void runtimeRemoved(int characterId, long nowMs) {
            AgentPpqRuntime.runtimeRemoved(characterId, nowMs);
        }
        @Override public AgentPartyQuestSessionView sessionView(int characterId) {
            AgentPpqSession session = AgentPpqSessionRegistry.forMember(characterId);
            if (session == null) return null;
            AgentPartyQuestSessionView.Phase phase = switch (session.phase()) {
                case EXITING -> AgentPartyQuestSessionView.Phase.DRAINING;
                case COMPLETED -> AgentPartyQuestSessionView.Phase.COMPLETED;
                case FAILED -> AgentPartyQuestSessionView.Phase.FAILED;
                default -> session.paused() ? AgentPartyQuestSessionView.Phase.SUSPENDED
                        : AgentPartyQuestSessionView.Phase.ACTIVE;
            };
            return new AgentPartyQuestSessionView("ppq", session.sessionId(), phase,
                    session.executionAgentId(), session.memberCount(), session.mode().name(),
                    session.failure(), session.startedAtMs(), session.lastProgressAtMs());
        }
        @Override public boolean pause(int characterId) {
            AgentPpqSession session = AgentPpqSessionRegistry.forMember(characterId);
            if (session == null || session.paused()) return false;
            session.setPaused(true); return true;
        }
        @Override public boolean resumeExact(int characterId, String sessionId, long nowMs) {
            AgentPpqSession session = AgentPpqSessionRegistry.forMember(characterId);
            if (session == null || !session.sessionId().equals(sessionId) || !session.paused()) return false;
            session.setPaused(false); session.markProgress(nowMs); return true;
        }
        @Override public AgentActivityAdmissionResult requestEntry(
                AgentRuntimeEntry entry, Character agent, String scenarioId,
                int partySize, int maximumRuns, long nowMs) {
            return AgentPartyQuestQueueRuntime.requestEntry(
                    AgentPpqLobbyProfile.profile(), entry, agent, scenarioId,
                    partySize, maximumRuns, nowMs, AgentPpqLobbyAdmissionRuntime::requestEntry);
        }
        @Override public String entryBlocker(
                Character agent, String scenarioId, int partySize, int maximumRuns) {
            return AgentPpqLobbyAdmissionRuntime.blocker(
                    agent, scenarioId, partySize, maximumRuns);
        }
    }
}
