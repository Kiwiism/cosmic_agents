package server.agents.capabilities.partyquest.lobby;

import client.Character;
import constants.id.ItemId;
import net.server.coordinator.world.InviteCoordinator;
import net.server.coordinator.world.InviteCoordinator.InviteResultType;
import net.server.coordinator.world.InviteCoordinator.InviteType;
import net.server.world.Party;
import server.agents.capabilities.movement.AgentChairService;
import server.agents.capabilities.movement.AgentMovementPoseService;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentPacketGatewayRuntime;
import server.agents.integration.AgentPartyGatewayRuntime;
import server.agents.integration.AgentPartySnapshot;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentSchedulerRuntime;

import java.awt.Point;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/** Shared presentation and invitation behavior for durable party-quest lobby sessions. */
public final class AgentPartyQuestLobbyRuntime {
    public enum InviteDecision { NOT_LOBBY_WAITER, DELAY_ACCEPT, REJECT }

    private static final long CHAT_MINIMUM_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyRuntime.CHAT_MINIMUM_MS");
    private static final long CHAT_MAXIMUM_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyRuntime.CHAT_MAXIMUM_MS");
    private static final long AMBIENT_MINIMUM_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyRuntime.AMBIENT_MINIMUM_MS");
    private static final long AMBIENT_MAXIMUM_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyRuntime.AMBIENT_MAXIMUM_MS");
    private static final long INVITE_COOLDOWN_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyRuntime.INVITE_COOLDOWN_MS");
    private static final long INVITE_RESPONSE_MINIMUM_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyRuntime.INVITE_RESPONSE_MINIMUM_MS");
    private static final long INVITE_RESPONSE_MAXIMUM_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyRuntime.INVITE_RESPONSE_MAXIMUM_MS");
    private static final Map<String, Presentation> presentations = new ConcurrentHashMap<>();
    private static ScheduledFuture<?> tickTask;

    private AgentPartyQuestLobbyRuntime() {
    }

    public static synchronized void register(AgentPartyQuestLobbySession lobby, long nowMs) {
        if (lobby == null) throw new IllegalArgumentException("party-quest lobby is required");
        unregister(lobby.lobbyId(), nowMs);
        AgentPartyQuestLobbyRegistry.register(lobby);
        presentations.put(lobby.lobbyId(), new Presentation());
        ensureTicking();
    }

    public static synchronized void unregister(String lobbyId) {
        unregister(lobbyId, System.currentTimeMillis());
    }

    public static synchronized void unregister(String lobbyId, long nowMs) {
        AgentPartyQuestLobbySession lobby = AgentPartyQuestLobbyRegistry.byId(lobbyId);
        if (lobby != null) {
            if (lobby.active()) lobby.close(nowMs);
            AgentPartyQuestLobbyRegistry.remove(lobby);
        }
        presentations.remove(lobbyId);
        if (AgentPartyQuestLobbyRegistry.lobbies().isEmpty() && tickTask != null) {
            tickTask.cancel(false);
            tickTask = null;
        }
    }

    public static void observeChat(Character speaker, String message, long nowMs) {
        if (speaker == null || message == null
                || AgentRuntimeRegistry.findByAgentCharacterId(speaker.getId()) != null) return;
        for (AgentPartyQuestLobbySession lobby : AgentPartyQuestLobbyRegistry.lobbies()) {
            if (!lobby.active() || lobby.paused() || !eligibleHuman(lobby, speaker)) continue;
            AgentPartyQuestLobbyIntent intent = AgentPartyQuestLobbyIntentMatcher.match(
                    lobby.profile(), message);
            if (!lobby.recruiterIds().isEmpty()
                    && intent == AgentPartyQuestLobbyIntent.REQUEST_TO_JOIN) {
                inviteSpeaker(lobby, speaker, nowMs);
            } else if (!lobby.waiterIds().isEmpty()
                    && intent == AgentPartyQuestLobbyIntent.RECRUITING_MEMBERS) {
                announceAvailableWaiter(lobby, speaker, nowMs);
            }
        }
    }

    public static InviteDecision decidePartyInvite(Character agent, Character inviter) {
        if (agent == null) return InviteDecision.NOT_LOBBY_WAITER;
        AgentPartyQuestLobbySession lobby = AgentPartyQuestLobbyRegistry.forMember(agent.getId());
        AgentPartyQuestLobbySession.MemberSnapshot member = lobby == null ? null : lobby.members().stream()
                .filter(candidate -> candidate.characterId() == agent.getId()).findFirst().orElse(null);
        if (lobby == null || !lobby.active() || lobby.paused() || member == null
                || member.type() != AgentPartyQuestLobbySession.MemberType.AGENT
                || member.role() != AgentPartyQuestLobbySession.MemberRole.LOOKING_FOR_PARTY) {
            return InviteDecision.NOT_LOBBY_WAITER;
        }
        if (!eligibleHuman(lobby, inviter) || agent.getMapId() != lobby.profile().mapId()
                || !sameWorldAndChannel(inviter, agent)) return InviteDecision.REJECT;
        AgentPartySnapshot party = AgentPartyGatewayRuntime.party().snapshot(inviter);
        if (party == null || party.members().size() >= lobby.profile().maximumPartySize()) {
            return InviteDecision.REJECT;
        }
        boolean inviterIsLeader = party.members().stream().anyMatch(candidate -> candidate != null
                && candidate.id() == inviter.getId() && candidate.leader());
        return inviterIsLeader ? InviteDecision.DELAY_ACCEPT : InviteDecision.REJECT;
    }

    /** Lets a lobby waiter visibly accept a human leader's invitation after the same
     * natural response delay used when an Agent leader invites the human. */
    public static boolean schedulePartyInviteResponse(
            Character agent, Character inviter, int partyId) {
        AgentPartyQuestLobbySession lobby = agent == null
                ? null : AgentPartyQuestLobbyRegistry.forMember(agent.getId());
        if (lobby == null || decidePartyInvite(agent, inviter) != InviteDecision.DELAY_ACCEPT) {
            return false;
        }
        long minimumMs = lobby.profile().inviteResponseMinimumMs() > 0L
                ? lobby.profile().inviteResponseMinimumMs() : INVITE_RESPONSE_MINIMUM_MS;
        long maximumMs = lobby.profile().inviteResponseMaximumMs() > 0L
                ? lobby.profile().inviteResponseMaximumMs() : INVITE_RESPONSE_MAXIMUM_MS;
        long delayMs = inviteResponseDelayMs(
                lobby.seed(), inviter.getId(), agent.getId(), minimumMs, maximumMs);
        AgentSchedulerRuntime.schedule(() -> acceptPartyInvite(
                lobby.lobbyId(), agent.getId(), inviter.getId(), partyId), delayMs);
        return true;
    }

    private static void acceptPartyInvite(
            String lobbyId, int agentId, int inviterId, int partyId) {
        AgentPartyQuestLobbySession lobby = AgentPartyQuestLobbyRegistry.byId(lobbyId);
        Character agent = character(agentId);
        Character inviter = character(inviterId);
        AgentPartySnapshot party = inviter == null
                ? null : AgentPartyGatewayRuntime.party().snapshot(inviter);
        boolean inviterStillLeads = party != null && party.id() == partyId
                && party.members().stream().anyMatch(member -> member != null
                && member.id() == inviterId && member.leader());
        if (lobby == null || !lobby.active() || lobby.paused()
                || agent == null || inviter == null || agent.getParty() != null
                || !inviterStillLeads
                || decidePartyInvite(agent, inviter) != InviteDecision.DELAY_ACCEPT) {
            return;
        }
        if (InviteCoordinator.answerInvite(
                InviteType.PARTY, agentId, partyId, true).result == InviteResultType.ACCEPTED) {
            Party.joinParty(agent, partyId, false);
        }
    }

    public static String inviteRejectionMessage(Character agent) {
        AgentPartyQuestLobbySession lobby = agent == null
                ? null : AgentPartyQuestLobbyRegistry.forMember(agent.getId());
        if (lobby == null) return "This Agent is not accepting that party invitation.";
        return agent.getName() + " is waiting for its level " + lobby.profile().minimumLevel()
                + '-' + lobby.profile().maximumLevel() + ' ' + lobby.profile().questKey().toUpperCase()
                + " lobby leader on the same map and channel.";
    }

    static void tick(long nowMs) {
        for (AgentPartyQuestLobbySession lobby : AgentPartyQuestLobbyRegistry.lobbies()) {
            if (!lobby.active()) {
                unregister(lobby.lobbyId(), nowMs);
                continue;
            }
            if (lobby.paused()) continue;
            AgentPartyQuestLobbyReconciler.reconcile(lobby, nowMs);
            for (AgentPartyQuestLobbySession.MemberSnapshot member : lobby.members()) {
                if (member.type() == AgentPartyQuestLobbySession.MemberType.AGENT) {
                    tickMember(lobby, member, nowMs);
                }
            }
        }
    }

    private static boolean eligibleHuman(AgentPartyQuestLobbySession lobby, Character candidate) {
        return candidate != null
                && (lobby.candidateScope() == AgentPartyQuestCandidateScope.ANY_ELIGIBLE_HUMAN
                    || candidate.getId() == lobby.ownerCharacterId())
                && candidate.getMapId() == lobby.profile().mapId()
                && candidate.getLevel() >= lobby.profile().minimumLevel()
                && candidate.getLevel() <= lobby.profile().maximumLevel()
                && AgentRuntimeRegistry.findByAgentCharacterId(candidate.getId()) == null;
    }

    private static void inviteSpeaker(
            AgentPartyQuestLobbySession lobby, Character speaker, long nowMs) {
        Presentation presentation = presentations.get(lobby.lobbyId());
        if (!eligibleHuman(lobby, speaker) || presentation == null
                || AgentPartyGatewayRuntime.party().hasParty(speaker)
                || !presentation.claimInvitation(nowMs, Math.max(1_000L, INVITE_COOLDOWN_MS))) return;
        Character recruiter = lobby.recruiterIds().stream()
                .map(AgentPartyQuestLobbyRuntime::character)
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
        if (recruiter == null || recruiter.getMapId() != lobby.profile().mapId()
                || !sameWorldAndChannel(recruiter, speaker)) return;
        long minimumMs = lobby.profile().inviteResponseMinimumMs() > 0L
                ? lobby.profile().inviteResponseMinimumMs() : INVITE_RESPONSE_MINIMUM_MS;
        long maximumMs = lobby.profile().inviteResponseMaximumMs() > 0L
                ? lobby.profile().inviteResponseMaximumMs() : INVITE_RESPONSE_MAXIMUM_MS;
        long delayMs = inviteResponseDelayMs(
                lobby.seed(), recruiter.getId(), speaker.getId(), minimumMs, maximumMs);
        AgentSchedulerRuntime.schedule(() -> sendInvitation(
                lobby.lobbyId(), recruiter.getId(), speaker.getId()), delayMs);
    }

    private static void sendInvitation(String lobbyId, int recruiterId, int speakerId) {
        AgentPartyQuestLobbySession lobby = AgentPartyQuestLobbyRegistry.byId(lobbyId);
        Character recruiter = character(recruiterId);
        Character speaker = character(speakerId);
        if (lobby == null || !lobby.active() || lobby.paused()
                || !lobby.recruiterIds().contains(recruiterId)
                || !eligibleHuman(lobby, speaker)
                || AgentPartyGatewayRuntime.party().hasParty(speaker)
                || recruiter == null || recruiter.getMapId() != lobby.profile().mapId()
                || !sameWorldAndChannel(recruiter, speaker)
                || !AgentPartyGatewayRuntime.party().invitePartyMember(recruiter, speaker)) {
            return;
        }
        say(recruiter, "I'll invite you to " + lobby.profile().questKey().toUpperCase() + ".");
    }

    private static void announceAvailableWaiter(
            AgentPartyQuestLobbySession lobby, Character speaker, long nowMs) {
        Presentation presentation = presentations.get(lobby.lobbyId());
        Character waiter = lobby.waiterIds().stream().map(AgentPartyQuestLobbyRuntime::character)
                .filter(java.util.Objects::nonNull)
                .filter(agent -> !AgentPartyGatewayRuntime.party().hasParty(agent))
                .filter(agent -> sameWorldAndChannel(agent, speaker))
                .findFirst().orElse(null);
        if (waiter == null || presentation == null
                || !presentation.claimResponse(nowMs, Math.max(1_000L, INVITE_COOLDOWN_MS))) return;
        say(waiter, "Invite me for " + lobby.profile().questKey().toUpperCase() + ".");
    }

    private static void tickMember(
            AgentPartyQuestLobbySession lobby,
            AgentPartyQuestLobbySession.MemberSnapshot member,
            long nowMs) {
        Character agent = character(member.characterId());
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(member.characterId());
        if (agent == null || entry == null || agent.getMapId() != lobby.profile().mapId()) return;
        Presentation presentation = presentations.computeIfAbsent(lobby.lobbyId(), ignored -> new Presentation());
        MemberPresentation state = presentation.member(member.characterId(), lobby.seed(), nowMs);
        if (nowMs >= state.nextChatAtMs) {
            if (member.role() == AgentPartyQuestLobbySession.MemberRole.RECRUITING_LEADER) {
                say(agent, recruiterMessage(lobby, agent, state.chatCount));
            } else if (member.role() == AgentPartyQuestLobbySession.MemberRole.LOOKING_FOR_PARTY
                    && !lobby.profile().waiterMessages().isEmpty()) {
                List<String> messages = lobby.profile().waiterMessages();
                say(agent, messages.get(Math.floorMod(
                        lobby.seed() + member.characterId() * 31L + state.chatCount, messages.size())));
            }
            state.chatCount++;
            state.nextChatAtMs = nowMs + delay(lobby.seed(), member.characterId(), state.chatCount,
                    CHAT_MINIMUM_MS, CHAT_MAXIMUM_MS);
        }
        if (nowMs >= state.nextAmbientAtMs) {
            ambient(lobby, entry, agent, state.ambientCount++);
            state.nextAmbientAtMs = nowMs + delay(lobby.seed() ^ 0x5DEECE66DL,
                    member.characterId(), state.ambientCount, AMBIENT_MINIMUM_MS, AMBIENT_MAXIMUM_MS);
        }
    }

    private static String recruiterMessage(
            AgentPartyQuestLobbySession lobby, Character recruiter, int chatCount) {
        AgentPartySnapshot party = AgentPartyGatewayRuntime.party().snapshot(recruiter);
        List<Character> roster = party == null ? List.of(recruiter) : party.members().stream()
                .filter(java.util.Objects::nonNull).map(member -> character(member.id()))
                .filter(java.util.Objects::nonNull).toList();
        int currentSize = party == null ? 1 : party.members().size();
        return AgentPartyQuestLobbyNarration.recruiterMessage(
                lobby.profile(), roster, currentSize,
                lobby.seed() + recruiter.getId() * 31L + chatCount);
    }

    private static void ambient(
            AgentPartyQuestLobbySession lobby, AgentRuntimeEntry entry,
            Character agent, int actionIndex) {
        int action = Math.floorMod(lobby.seed() + agent.getId() * 17L + actionIndex * 13L, 5);
        if (action <= 1) {
            if (agent.getChair() >= 0) AgentChairService.stand(entry, agent);
            Point npc = AgentPrimitiveCapabilityGatewayRuntime.gateway()
                    .npcPosition(agent, lobby.profile().entryNpcId());
            if (npc == null) return;
            int offset;
            if (action == 0 && agent.getPosition() != null) {
                int direction = Math.floorMod(lobby.seed() + actionIndex + agent.getId(), 2) == 0 ? -1 : 1;
                offset = agent.getPosition().x - npc.x + direction
                        * (8 + Math.floorMod(lobby.seed() + agent.getId() + actionIndex, 17));
            } else {
                int width = lobby.profile().maximumXOffset() - lobby.profile().minimumXOffset() + 1;
                offset = lobby.profile().minimumXOffset() + Math.floorMod(
                        lobby.seed() + agent.getId() * 43L + actionIndex * 71L, width);
            }
            offset = Math.max(lobby.profile().minimumXOffset(),
                    Math.min(lobby.profile().maximumXOffset(), offset));
            Point target = AgentPrimitiveCapabilityGatewayRuntime.gateway().groundPoint(
                    agent.getMap(), new Point(npc.x + offset, npc.y));
            if (target != null) AgentPrimitiveCapabilityGatewayRuntime.gateway().navigate(entry, target, false);
        } else if (action == 2
                && AgentPrimitiveCapabilityGatewayRuntime.gateway().itemCount(agent, ItemId.RELAXER) > 0) {
            if (agent.getChair() < 0) AgentPrimitiveCapabilityGatewayRuntime.gateway().sitChair(agent, ItemId.RELAXER);
        } else if (action == 3) {
            if (agent.getChair() >= 0) AgentChairService.stand(entry, agent);
            AgentMovementPoseService.idleOnGround(entry, agent);
        } else if (agent.getChair() < 0) {
            AgentMovementPoseService.idleOnGround(entry, agent);
        }
    }

    private static synchronized void ensureTicking() {
        if (tickTask == null || tickTask.isDone()) {
            tickTask = AgentSchedulerRuntime.schedule(AgentPartyQuestLobbyRuntime::tickSafely, 1_000L);
        }
    }

    private static void tickSafely() {
        try {
            tick(System.currentTimeMillis());
        } finally {
            synchronized (AgentPartyQuestLobbyRuntime.class) {
                if (AgentPartyQuestLobbyRegistry.lobbies().isEmpty()) tickTask = null;
                else tickTask = AgentSchedulerRuntime.schedule(
                        AgentPartyQuestLobbyRuntime::tickSafely, 1_000L);
            }
        }
    }

    private static long delay(long seed, int memberId, int count, long minimum, long maximum) {
        long min = Math.max(1_000L, Math.min(minimum, maximum));
        long max = Math.max(min, Math.max(minimum, maximum));
        return min + Math.floorMod(seed + memberId * 101L + count * 307L, max - min + 1L);
    }

    static long inviteResponseDelayMs(
            long seed, int recruiterId, int speakerId, long minimumMs, long maximumMs) {
        long minimum = Math.max(0L, Math.min(minimumMs, maximumMs));
        long maximum = Math.max(minimum, Math.max(minimumMs, maximumMs));
        if (minimum == maximum) return minimum;
        return minimum + Math.floorMod(seed + recruiterId * 101L + speakerId * 307L,
                maximum - minimum + 1L);
    }

    private static void say(Character speaker, String message) {
        AgentPacketGatewayRuntime.packets().broadcastChatText(speaker, message, false, 0);
    }

    private static boolean sameWorldAndChannel(Character first, Character second) {
        return first != null && second != null
                && AgentClientGatewayRuntime.clients().world(first)
                    == AgentClientGatewayRuntime.clients().world(second)
                && AgentClientGatewayRuntime.clients().channel(first)
                    == AgentClientGatewayRuntime.clients().channel(second);
    }

    static Character character(int characterId) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(characterId);
        Character agent = entry == null ? null : AgentRuntimeIdentityRuntime.bot(entry);
        return agent != null ? agent
                : AgentCharacterGatewayRuntime.characters().findOnlineCharacterById(characterId);
    }

    private static final class Presentation {
        private final Map<Integer, MemberPresentation> members = new LinkedHashMap<>();
        private long lastInvitationAtMs = Long.MIN_VALUE;
        private long lastResponseAtMs = Long.MIN_VALUE;

        private synchronized MemberPresentation member(int id, long seed, long nowMs) {
            return members.computeIfAbsent(id, ignored -> new MemberPresentation(
                    nowMs + delay(seed, id, 0, 3_000L, 9_000L),
                    nowMs + delay(seed ^ 0xBL, id, 0, 2_000L, 7_000L)));
        }

        private synchronized boolean claimInvitation(long nowMs, long cooldownMs) {
            if (lastInvitationAtMs != Long.MIN_VALUE && nowMs - lastInvitationAtMs < cooldownMs) return false;
            lastInvitationAtMs = nowMs;
            return true;
        }

        private synchronized boolean claimResponse(long nowMs, long cooldownMs) {
            if (lastResponseAtMs != Long.MIN_VALUE && nowMs - lastResponseAtMs < cooldownMs) return false;
            lastResponseAtMs = nowMs;
            return true;
        }
    }

    private static final class MemberPresentation {
        private long nextChatAtMs;
        private long nextAmbientAtMs;
        private int chatCount;
        private int ambientCount;

        private MemberPresentation(long nextChatAtMs, long nextAmbientAtMs) {
            this.nextChatAtMs = nextChatAtMs;
            this.nextAmbientAtMs = nextAmbientAtMs;
        }
    }
}
