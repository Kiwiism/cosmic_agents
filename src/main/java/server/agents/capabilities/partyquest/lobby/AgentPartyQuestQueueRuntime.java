package server.agents.capabilities.partyquest.lobby;

import client.Character;
import net.server.coordinator.world.InviteCoordinator;
import net.server.coordinator.world.InviteCoordinator.InviteResultType;
import net.server.coordinator.world.InviteCoordinator.InviteType;
import net.server.world.Party;
import server.agents.capabilities.partyquest.AgentPartyQuestLifecycleRuntime;
import server.agents.capabilities.partyquest.AgentPartyQuestSessionView;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentPacketGatewayRuntime;
import server.agents.integration.AgentPartyGatewayRuntime;
import server.agents.integration.AgentPartySnapshot;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentSchedulerRuntime;
import server.agents.runtime.activity.session.AgentActivityAdmissionResult;
import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.activity.session.AgentActivityPhase;
import server.agents.runtime.activity.session.AgentActivitySessionSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared unpartied queue in front of every party-quest aggregate.
 *
 * <p>A queue ticket owns the Agent's foreground activity without creating a MapleStory party.
 * A human leader may invite the waiter, or a complete compatible Agent roster is committed
 * atomically after the human-priority grace period. The PQ-specific admission service remains
 * authoritative for the actual party and event.</p>
 */
public final class AgentPartyQuestQueueRuntime {
    private static final long AGENT_FORMATION_GRACE_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.lobby.AgentPartyQuestQueueRuntime.AGENT_FORMATION_GRACE_MS");
    private static final long QUEUE_TIMEOUT_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.lobby.AgentPartyQuestQueueRuntime.QUEUE_TIMEOUT_MS");
    private static final long CHAT_MINIMUM_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.lobby.AgentPartyQuestQueueRuntime.CHAT_MINIMUM_MS");
    private static final long CHAT_MAXIMUM_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.lobby.AgentPartyQuestQueueRuntime.CHAT_MAXIMUM_MS");
    private static final Map<Integer, Ticket> TICKETS = new ConcurrentHashMap<>();
    private static final Map<Integer, Terminal> TERMINALS = new ConcurrentHashMap<>();

    private AgentPartyQuestQueueRuntime() { }

    @FunctionalInterface
    public interface DirectAdmission {
        AgentActivityAdmissionResult request(
                AgentRuntimeEntry entry, Character agent, String scenarioId,
                int partySize, int maximumRuns, long nowMs);
    }

    public static synchronized AgentActivityAdmissionResult requestEntry(
            AgentPartyQuestLobbyProfile profile,
            AgentRuntimeEntry entry,
            Character agent,
            String scenarioId,
            int partySize,
            int maximumRuns,
            long nowMs,
            DirectAdmission directAdmission) {
        if (profile == null || entry == null || agent == null || directAdmission == null) {
            return AgentActivityAdmissionResult.rejected("complete party-quest queue admission is required");
        }
        if (!profile.questKey().equalsIgnoreCase(scenarioId)) {
            return AgentActivityAdmissionResult.rejected("party-quest queue profile does not match the request");
        }
        if (agent.getMapId() != profile.mapId()) {
            return AgentActivityAdmissionResult.deferred(
                    "traveling to the " + profile.questKey().toUpperCase() + " lobby", nowMs + 500L);
        }
        if (agent.getLevel() < profile.minimumLevel() || agent.getLevel() > profile.maximumLevel()) {
            return AgentActivityAdmissionResult.rejected(profile.questKey().toUpperCase()
                    + " requires level " + profile.minimumLevel() + '-' + profile.maximumLevel());
        }
        if (partySize < 1 || partySize > profile.maximumPartySize() || maximumRuns < 1) {
            return AgentActivityAdmissionResult.rejected("invalid party-quest queue size or run budget");
        }
        Ticket retained = TICKETS.get(agent.getId());
        if (retained != null) {
            return retained.matches(profile.questKey(), partySize)
                    ? accepted(retained)
                    : AgentActivityAdmissionResult.rejected("Agent is already queued for another party quest");
        }
        TERMINALS.remove(agent.getId());
        Ticket ticket = new Ticket(
                "pqq-" + UUID.randomUUID(), profile, entry, agent.getId(), scenarioId,
                partySize, maximumRuns, nowMs, directAdmission,
                nowMs + chatDelay(nowMs ^ agent.getId()));
        TICKETS.put(agent.getId(), ticket);
        process(ticket.pool(), nowMs);
        Ticket queued = TICKETS.get(agent.getId());
        return queued == null ? admittedSnapshot(agent.getId(), nowMs) : accepted(queued);
    }

    public static boolean active(int characterId) {
        return TICKETS.containsKey(characterId);
    }

    public static synchronized boolean tick(int characterId, long nowMs) {
        Ticket ticket = TICKETS.get(characterId);
        if (ticket == null) return false;
        if (ticket.paused) return true;
        Character agent = character(characterId);
        if (agent == null) return true;
        if (nowMs - ticket.enqueuedAtMs >= Math.max(30_000L, QUEUE_TIMEOUT_MS)) {
            finish(ticket, AgentPartyQuestSessionView.Phase.FAILED,
                    "party-quest queue wait expired", nowMs);
            return false;
        }
        if (agent.getMapId() != ticket.profile.mapId()) {
            finish(ticket, AgentPartyQuestSessionView.Phase.FAILED,
                    "Agent left the party-quest lobby", nowMs);
            return false;
        }
        if (nowMs >= ticket.nextChatAtMs) {
            List<String> messages = ticket.profile.waiterMessages();
            if (!messages.isEmpty()) {
                int index = Math.floorMod(ticket.agentId + ticket.chatCount, messages.size());
                AgentPacketGatewayRuntime.packets().broadcastChatText(
                        agent, messages.get(index), false, 0);
            }
            ticket.chatCount++;
            ticket.nextChatAtMs = nowMs + chatDelay(ticket.enqueuedAtMs
                    + ticket.agentId * 31L + ticket.chatCount * 101L);
        }
        if (AgentPartyGatewayRuntime.party().hasParty(agent)) {
            admitHumanParty(ticket, nowMs);
        } else {
            process(ticket.pool(), nowMs);
        }
        return TICKETS.containsKey(characterId);
    }

    public static synchronized boolean requestStop(int characterId, String reason, long nowMs) {
        Ticket ticket = TICKETS.get(characterId);
        if (ticket == null) return true;
        Character agent = character(characterId);
        if (agent != null && AgentPartyGatewayRuntime.party().hasParty(agent)) {
            AgentPartyGatewayRuntime.party().leaveCurrentParty(agent);
        }
        finish(ticket, AgentPartyQuestSessionView.Phase.COMPLETED,
                reason == null || reason.isBlank() ? "left party-quest queue" : reason, nowMs);
        return true;
    }

    public static synchronized void forceStop(int characterId, String reason, long nowMs) {
        requestStop(characterId, reason, nowMs);
    }

    public static synchronized boolean pause(int characterId) {
        Ticket ticket = TICKETS.get(characterId);
        if (ticket == null || ticket.paused) return false;
        ticket.paused = true;
        return true;
    }

    public static synchronized boolean resumeExact(int characterId, String sessionId, long nowMs) {
        Ticket ticket = TICKETS.get(characterId);
        if (ticket == null || !ticket.ticketId.equals(sessionId) || !ticket.paused) return false;
        ticket.paused = false;
        ticket.lastProgressAtMs = nowMs;
        return true;
    }

    public static AgentPartyQuestSessionView sessionView(int characterId) {
        Ticket ticket = TICKETS.get(characterId);
        if (ticket != null) {
            return new AgentPartyQuestSessionView(
                    ticket.profile.questKey(), ticket.ticketId,
                    ticket.paused ? AgentPartyQuestSessionView.Phase.SUSPENDED
                            : AgentPartyQuestSessionView.Phase.ACTIVE,
                    ticket.agentId, 1, "QUEUE", ticket.lastReason,
                    ticket.enqueuedAtMs, Math.max(ticket.enqueuedAtMs, ticket.lastProgressAtMs));
        }
        Terminal terminal = TERMINALS.get(characterId);
        if (terminal == null) return null;
        return new AgentPartyQuestSessionView(
                terminal.questKey, terminal.sessionId, terminal.phase,
                characterId, 1, "QUEUE", terminal.reason,
                terminal.startedAtMs, terminal.finishedAtMs);
    }

    public static synchronized boolean canAcceptInvite(Character agent, Character inviter) {
        Ticket ticket = agent == null ? null : TICKETS.get(agent.getId());
        if (ticket == null || ticket.paused || inviter == null
                || agent.getParty() != null || !eligibleHuman(ticket, inviter)) return false;
        AgentPartySnapshot party = AgentPartyGatewayRuntime.party().snapshot(inviter);
        if (party == null || party.members().size() >= ticket.partySize) return false;
        return party.members().stream().anyMatch(member -> member != null
                && member.id() == inviter.getId() && member.leader());
    }

    public static synchronized boolean scheduleInviteResponse(
            Character agent, Character inviter, int partyId) {
        if (!canAcceptInvite(agent, inviter)) return false;
        Ticket ticket = TICKETS.get(agent.getId());
        long delayMs = 900L + Math.floorMod(
                ticket.enqueuedAtMs + agent.getId() * 101L + inviter.getId() * 307L, 901L);
        AgentSchedulerRuntime.schedule(
                () -> acceptInvite(ticket.ticketId, agent.getId(), inviter.getId(), partyId), delayMs);
        return true;
    }

    public static synchronized String inviteRejectionMessage(Character agent) {
        Ticket ticket = agent == null ? null : TICKETS.get(agent.getId());
        if (ticket == null) return "This Agent is not waiting for a party quest.";
        return agent.getName() + " is queued for level " + ticket.profile.minimumLevel()
                + '-' + ticket.profile.maximumLevel() + ' '
                + ticket.profile.questKey().toUpperCase() + ".";
    }

    public static synchronized void observeChat(Character speaker, String message, long nowMs) {
        if (speaker == null || message == null
                || AgentRuntimeRegistry.findByAgentCharacterId(speaker.getId()) != null) return;
        for (Ticket ticket : TICKETS.values().stream()
                .sorted(Comparator.comparingLong(value -> value.enqueuedAtMs)).toList()) {
            Character waiter = character(ticket.agentId);
            if (waiter == null || waiter.getParty() != null || ticket.paused
                    || !eligibleHuman(ticket, speaker)) continue;
            AgentPartyQuestLobbyIntent intent = AgentPartyQuestLobbyIntentMatcher.match(
                    ticket.profile, message);
            if (intent == AgentPartyQuestLobbyIntent.RECRUITING_MEMBERS) {
                AgentPacketGatewayRuntime.packets().broadcastChatText(
                        waiter, "Invite me for " + ticket.profile.questKey().toUpperCase() + ".",
                        false, 0);
                ticket.lastProgressAtMs = nowMs;
                return;
            }
        }
    }

    private static void acceptInvite(String ticketId, int agentId, int inviterId, int partyId) {
        synchronized (AgentPartyQuestQueueRuntime.class) {
            Ticket ticket = TICKETS.get(agentId);
            Character agent = character(agentId);
            Character inviter = character(inviterId);
            AgentPartySnapshot party = inviter == null
                    ? null : AgentPartyGatewayRuntime.party().snapshot(inviter);
            boolean leader = party != null && party.id() == partyId
                    && party.members().stream().anyMatch(member -> member != null
                    && member.id() == inviterId && member.leader());
            if (ticket == null || !ticket.ticketId.equals(ticketId) || agent == null
                    || inviter == null || agent.getParty() != null || !leader
                    || !canAcceptInvite(agent, inviter)) return;
            if (InviteCoordinator.answerInvite(
                    InviteType.PARTY, agentId, partyId, true).result == InviteResultType.ACCEPTED) {
                Party.joinParty(agent, partyId, false);
                ticket.lastProgressAtMs = System.currentTimeMillis();
            }
        }
    }

    private static void admitHumanParty(Ticket ticket, long nowMs) {
        Character agent = character(ticket.agentId);
        AgentPartySnapshot party = agent == null ? null : AgentPartyGatewayRuntime.party().snapshot(agent);
        if (party == null || party.members().size() > ticket.partySize) return;
        AgentActivityAdmissionResult result = ticket.directAdmission.request(
                ticket.entry, agent, ticket.scenarioId, ticket.partySize, ticket.maximumRuns, nowMs);
        ticket.lastReason = result.reason();
        ticket.lastProgressAtMs = nowMs;
        if (result.status() == AgentActivityAdmissionResult.Status.ACCEPTED) {
            TICKETS.remove(ticket.agentId, ticket);
            TERMINALS.remove(ticket.agentId);
        }
    }

    private static void process(PoolKey pool, long nowMs) {
        List<Ticket> candidates = TICKETS.values().stream()
                .filter(ticket -> !ticket.paused && ticket.pool().equals(pool))
                .filter(ticket -> {
                    Character agent = character(ticket.agentId);
                    return agent != null && agent.getParty() == null
                            && agent.getMapId() == ticket.profile.mapId();
                })
                .sorted(Comparator.comparingLong((Ticket value) -> value.enqueuedAtMs)
                        .thenComparingInt(value -> value.agentId)).toList();
        if (candidates.size() < pool.partySize) return;
        List<Ticket> roster = selectRoster(candidates, pool.partySize);
        if (roster.size() != pool.partySize) return;
        long readySince = roster.stream().mapToLong(ticket -> ticket.enqueuedAtMs).max().orElse(nowMs);
        if (nowMs - readySince < Math.max(0L, AGENT_FORMATION_GRACE_MS)) return;

        List<Ticket> admitted = new ArrayList<>();
        for (Ticket ticket : roster) {
            Character agent = character(ticket.agentId);
            if (agent == null) break;
            AgentActivityAdmissionResult result = ticket.directAdmission.request(
                    ticket.entry, agent, ticket.scenarioId, ticket.partySize,
                    ticket.maximumRuns, nowMs);
            ticket.lastReason = result.reason();
            ticket.lastProgressAtMs = nowMs;
            if (result.status() != AgentActivityAdmissionResult.Status.ACCEPTED) break;
            admitted.add(ticket);
        }
        if (admitted.size() == roster.size()) {
            admitted.forEach(ticket -> {
                TICKETS.remove(ticket.agentId, ticket);
                TERMINALS.remove(ticket.agentId);
            });
            return;
        }
        for (Ticket ticket : admitted) {
            AgentPartyQuestLifecycleRuntime.requestStop(
                    ticket.agentId, "party-quest queue formation rolled back", nowMs);
            Character agent = character(ticket.agentId);
            if (agent != null && AgentPartyGatewayRuntime.party().hasParty(agent)) {
                AgentPartyGatewayRuntime.party().leaveCurrentParty(agent);
            }
        }
    }

    private static List<Ticket> selectRoster(List<Ticket> candidates, int partySize) {
        LinkedHashSet<Ticket> selected = new LinkedHashSet<>();
        AgentPartyQuestLobbyProfile profile = candidates.getFirst().profile;
        for (AgentPartyQuestLobbyProfile.MemberRequirement requirement : profile.memberRequirements()) {
            long present = selected.stream().filter(ticket -> requirement.matches(character(ticket.agentId))).count();
            for (Ticket candidate : candidates) {
                if (present >= requirement.minimumCount()) break;
                if (selected.contains(candidate) || !requirement.matches(character(candidate.agentId))) continue;
                selected.add(candidate);
                present++;
            }
            if (present < requirement.minimumCount()) return List.of();
        }
        for (Ticket candidate : candidates) {
            if (selected.size() >= partySize) break;
            selected.add(candidate);
        }
        return selected.size() == partySize ? List.copyOf(selected) : List.of();
    }

    private static AgentActivityAdmissionResult admittedSnapshot(int characterId, long nowMs) {
        server.agents.capabilities.partyquest.AgentPartyQuestEngagement engagement =
                server.agents.capabilities.partyquest.AgentPartyQuestEngagementRegistry.forMember(characterId);
        if (engagement == null) {
            return AgentActivityAdmissionResult.deferred("party formation is settling", nowMs + 250L);
        }
        return AgentActivityAdmissionResult.accepted(new AgentActivitySessionSnapshot(
                AgentActivityKind.PARTY_QUEST, AgentActivityPhase.ACTIVE,
                engagement.engagementId(), engagement.engagementId(),
                "pq-queue", Integer.toString(characterId), engagement.startedAtMs(), ""));
    }

    private static AgentActivityAdmissionResult accepted(Ticket ticket) {
        return AgentActivityAdmissionResult.accepted(new AgentActivitySessionSnapshot(
                AgentActivityKind.PARTY_QUEST,
                ticket.paused ? AgentActivityPhase.SUSPENDED : AgentActivityPhase.ACTIVE,
                ticket.ticketId, ticket.ticketId, "pq-queue",
                Integer.toString(ticket.agentId), ticket.enqueuedAtMs, ticket.lastReason));
    }

    private static void finish(
            Ticket ticket, AgentPartyQuestSessionView.Phase phase, String reason, long nowMs) {
        if (!TICKETS.remove(ticket.agentId, ticket)) return;
        TERMINALS.put(ticket.agentId, new Terminal(
                ticket.profile.questKey(), ticket.ticketId, phase, reason,
                ticket.enqueuedAtMs, Math.max(ticket.enqueuedAtMs, nowMs)));
    }

    private static boolean eligibleHuman(Ticket ticket, Character candidate) {
        return candidate != null && candidate.getMapId() == ticket.profile.mapId()
                && candidate.getLevel() >= ticket.profile.minimumLevel()
                && candidate.getLevel() <= ticket.profile.maximumLevel()
                && AgentRuntimeRegistry.findByAgentCharacterId(candidate.getId()) == null
                && sameWorldAndChannel(character(ticket.agentId), candidate);
    }

    private static boolean sameWorldAndChannel(Character first, Character second) {
        return first != null && second != null
                && AgentClientGatewayRuntime.clients().world(first)
                == AgentClientGatewayRuntime.clients().world(second)
                && AgentClientGatewayRuntime.clients().channel(first)
                == AgentClientGatewayRuntime.clients().channel(second);
    }

    private static Character character(int characterId) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(characterId);
        Character agent = entry == null ? null : AgentRuntimeIdentityRuntime.bot(entry);
        return agent != null ? agent
                : server.agents.integration.AgentCharacterGatewayRuntime.characters()
                .findOnlineCharacterById(characterId);
    }

    private static long chatDelay(long seed) {
        long min = Math.max(1_000L, Math.min(CHAT_MINIMUM_MS, CHAT_MAXIMUM_MS));
        long max = Math.max(min, Math.max(CHAT_MINIMUM_MS, CHAT_MAXIMUM_MS));
        return min + Math.floorMod(seed, max - min + 1L);
    }

    private record PoolKey(String questKey, int world, int channel, int partySize) { }

    private static final class Ticket {
        private final String ticketId;
        private final AgentPartyQuestLobbyProfile profile;
        private final AgentRuntimeEntry entry;
        private final int agentId;
        private final String scenarioId;
        private final int partySize;
        private final int maximumRuns;
        private final long enqueuedAtMs;
        private final DirectAdmission directAdmission;
        private long lastProgressAtMs;
        private long nextChatAtMs;
        private int chatCount;
        private boolean paused;
        private String lastReason = "waiting unpartied for a compatible lobby roster";

        private Ticket(
                String ticketId, AgentPartyQuestLobbyProfile profile, AgentRuntimeEntry entry,
                int agentId, String scenarioId, int partySize, int maximumRuns,
                long enqueuedAtMs, DirectAdmission directAdmission, long nextChatAtMs) {
            this.ticketId = ticketId;
            this.profile = profile;
            this.entry = entry;
            this.agentId = agentId;
            this.scenarioId = scenarioId;
            this.partySize = partySize;
            this.maximumRuns = maximumRuns;
            this.enqueuedAtMs = enqueuedAtMs;
            this.directAdmission = directAdmission;
            this.lastProgressAtMs = enqueuedAtMs;
            this.nextChatAtMs = nextChatAtMs;
        }

        private boolean matches(String questKey, int expectedPartySize) {
            return profile.questKey().equalsIgnoreCase(questKey) && partySize == expectedPartySize;
        }

        private PoolKey pool() {
            Character agent = character(agentId);
            int world = agent == null ? 0 : AgentClientGatewayRuntime.clients().world(agent);
            int channel = agent == null ? 0 : AgentClientGatewayRuntime.clients().channel(agent);
            return new PoolKey(profile.questKey(), world, channel, partySize);
        }
    }

    private record Terminal(
            String questKey, String sessionId, AgentPartyQuestSessionView.Phase phase,
            String reason, long startedAtMs, long finishedAtMs) { }
}
