package server.agents.capabilities.partyquest.hpq;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.capabilities.partyquest.AgentPartyQuestEngagement;
import server.agents.capabilities.partyquest.AgentPartyQuestEngagementRegistry;
import server.agents.capabilities.partyquest.AgentPartyQuestLifecycleRuntime;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestCandidateScope;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyRuntime;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbySession;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.integration.AgentPartyGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentSchedulerRuntime;
import server.agents.runtime.activity.AgentActivityBootstrap;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Self-contained population director for exact, unobserved HPQ gameplay. */
public final class AgentHpqPopulationRuntime {
    private static final Logger log = LoggerFactory.getLogger(AgentHpqPopulationRuntime.class);
    private static final Map<Integer, Long> lastAdmissionByCharacter = new ConcurrentHashMap<>();
    private static ScheduledFuture<?> task;
    private static BooleanSupplier populationEnabled = () -> false;
    private static Supplier<Set<Integer>> managedCharacterIds = Set::of;

    private AgentHpqPopulationRuntime() {
    }

    public static synchronized void start(
            BooleanSupplier enabledSource, Supplier<Set<Integer>> managedIdsSource) {
        if (task != null) return;
        populationEnabled = enabledSource == null ? () -> false : enabledSource;
        managedCharacterIds = managedIdsSource == null ? Set::of : managedIdsSource;
        if (!featureEnabled()) return;
        long sweepMs = Math.max(5_000L, config.AgentTuning.longValue(
                "server.agents.capabilities.partyquest.hpq.AgentHpqPopulationRuntime.SWEEP_MS"));
        task = AgentSchedulerRuntime.register(AgentHpqPopulationRuntime::sweepSafely, sweepMs);
        AgentSchedulerRuntime.schedule(AgentHpqPopulationRuntime::sweepSafely, sweepMs);
    }

    public static synchronized void stop() {
        ScheduledFuture<?> running = task;
        task = null;
        if (running != null) running.cancel(false);
        lastAdmissionByCharacter.clear();
        populationEnabled = () -> false;
        managedCharacterIds = Set::of;
    }

    static void sweepSafely() {
        try {
            sweep(System.currentTimeMillis());
        } catch (RuntimeException failure) {
            log.warn("Autonomous HPQ population sweep failed closed", failure);
        }
    }

    static synchronized int sweep(long nowMs) {
        if (!enabled()) return 0;
        int partySize = Math.max(3, Math.min(6, config.AgentTuning.intValue(
                "server.agents.capabilities.partyquest.hpq.AgentHpqPopulationRuntime.PARTY_SIZE")));
        long cooldownMs = Math.max(0L, config.AgentTuning.longValue(
                "server.agents.capabilities.partyquest.hpq.AgentHpqPopulationRuntime.REENTRY_COOLDOWN_MS"));
        Map<ChannelKey, List<Character>> eligible = new LinkedHashMap<>();
        Set<Integer> managedIds = Set.copyOf(managedCharacterIds.get());
        for (AgentRuntimeEntry entry : AgentRuntimeRegistry.activeEntriesSnapshot()) {
            Character agent = AgentRuntimeIdentityRuntime.bot(entry);
            if (agent == null || !managedIds.contains(agent.getId())
                    || !eligible(agent, nowMs, cooldownMs)) continue;
            ChannelKey key = new ChannelKey(
                    AgentClientGatewayRuntime.clients().world(agent),
                    AgentClientGatewayRuntime.clients().channel(agent));
            eligible.computeIfAbsent(key, ignored -> new ArrayList<>()).add(agent);
        }
        int admitted = 0;
        for (Map.Entry<ChannelKey, List<Character>> channel : eligible.entrySet()) {
            if (!AgentHpqLobbyPolicy.backgroundSlotAvailable(
                    channel.getKey().world(), channel.getKey().channel())) continue;
            List<Character> candidates = channel.getValue().stream()
                    .sorted(Comparator.comparingInt(Character::getId)).limit(partySize).toList();
            if (candidates.size() < partySize) continue;
            if (admitParty(channel.getKey(), candidates, nowMs)) {
                candidates.forEach(agent -> lastAdmissionByCharacter.put(agent.getId(), nowMs));
                admitted++;
            }
        }
        return admitted;
    }

    private static boolean eligible(Character agent, long nowMs, long cooldownMs) {
        if (agent.getLevel() < 10 || agent.getLevel() > 255 || agent.getHp() <= 0
                || AgentHpqSessionRegistry.active(agent.getId())
                || AgentPartyGatewayRuntime.party().hasParty(agent)) {
            return false;
        }
        Long lastAdmission = lastAdmissionByCharacter.get(agent.getId());
        return lastAdmission == null || nowMs - lastAdmission >= cooldownMs;
    }

    private static boolean admitParty(ChannelKey channel, List<Character> members, long nowMs) {
        Character leader = members.getFirst();
        long seed = nowMs ^ leader.getId();
        AgentPartyQuestEngagement engagement = new AgentPartyQuestEngagement(
                "hpq", AgentPartyQuestEngagement.Mode.BACKGROUND_POPULATION,
                seed, leader.getId(), members.size(), nowMs);
        AgentPartyQuestLobbySession lobby = null;
        try {
            AgentPartyQuestEngagementRegistry.register(engagement);
            for (Character member : members) {
                AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(member.getId());
                if (entry == null || !AgentActivityBootstrap.admission().prepare(
                        AgentActivityBootstrap.PARTY_QUEST_CONTROLLER_ID, entry, member,
                        "selected for background HPQ", nowMs)) {
                    throw new IllegalStateException(member.getName()
                            + " could not release its current activity");
                }
                AgentPartyQuestEngagementRegistry.addAndIndexMember(
                        engagement, member.getId(),
                        AgentPartyQuestEngagement.MemberType.AGENT, nowMs);
            }
            if (!AgentPartyGatewayRuntime.party().createAgentParty(leader)) {
                throw new IllegalStateException("could not create background HPQ party");
            }
            var party = AgentPartyGatewayRuntime.party().snapshot(leader);
            if (party == null) throw new IllegalStateException("created HPQ party has no snapshot");
            for (Character member : members.subList(1, members.size())) {
                if (!AgentPartyGatewayRuntime.party().joinAgentParty(member, party.id())) {
                    throw new IllegalStateException(member.getName()
                            + " could not join background HPQ party");
                }
                AgentPartyGatewayRuntime.party().publishAgentOnline(member, party.id());
            }
            MapleMap henesys = AgentMapGatewayRuntime.map().resolveMap(
                    channel.world(), channel.channel(), AgentHpqDefinition.RECRUIT_MAP);
            if (henesys == null) throw new IllegalStateException("HPQ recruit map is unavailable");
            var portal = henesys.getRandomPlayerSpawnpoint();
            Point spawn = portal == null ? new Point(0, 0) : portal.getPosition();
            for (int index = 0; index < members.size(); index++) {
                AgentMapGatewayRuntime.map().changeMapNear(
                        members.get(index), henesys, new Point(spawn.x + index * 18, spawn.y));
            }
            lobby = new AgentPartyQuestLobbySession(
                    engagement.engagementId(), AgentHpqLobbyProfile.profile(), seed,
                    leader.getId(), members.size(), AgentPartyQuestCandidateScope.OWNER_ONLY, nowMs);
            for (Character member : members) {
                lobby.addMember(member.getId(), AgentPartyQuestLobbySession.MemberType.AGENT,
                        member == leader
                                ? AgentPartyQuestLobbySession.MemberRole.RECRUITING_LEADER
                                : AgentPartyQuestLobbySession.MemberRole.JOINED_MEMBER, nowMs);
            }
            lobby.setCoordinatorAgentId(leader.getId());
            lobby.reconcileParty(party.id(), leader.getId(),
                    members.stream().map(Character::getId)
                            .collect(java.util.stream.Collectors.toSet()), nowMs);
            lobby.markReady(nowMs);
            engagement.beginLobby(lobby.lobbyId(), nowMs);
            engagement.lobbyReady(nowMs);
            AgentPartyQuestLobbyRuntime.register(lobby, nowMs);
            AgentHpqAdmissionService.AdmissionResult result = AgentHpqAdmissionService.admitFromLobby(
                    engagement, lobby, leader, leader, members, seed, nowMs,
                    AgentHpqSession.Mode.BACKGROUND_POPULATION);
            if (!result.success()) throw new IllegalStateException(result.message());
            log.info("Background HPQ admitted: session={} world={} channel={} members={}",
                    result.session().sessionId(), channel.world(), channel.channel(),
                    members.stream().map(Character::getName).toList());
            return true;
        } catch (RuntimeException failure) {
            if (lobby != null) AgentPartyQuestLobbyRuntime.unregister(lobby.lobbyId(), nowMs);
            if (AgentPartyQuestEngagementRegistry.byId(engagement.engagementId()) == engagement) {
                engagement.beginRecovery(failure.getMessage(), nowMs);
                AgentPartyQuestLifecycleRuntime.recover(engagement, nowMs);
            }
            cleanupParty(members, leader);
            log.warn("Background HPQ admission rolled back for members {}",
                    members.stream().map(Character::getName).toList(), failure);
            return false;
        }
    }

    private static void cleanupParty(List<Character> members, Character leader) {
        members.stream().filter(member -> member != leader)
                .filter(AgentPartyGatewayRuntime.party()::hasParty)
                .forEach(AgentPartyGatewayRuntime.party()::leaveCurrentParty);
        if (leader != null && AgentPartyGatewayRuntime.party().hasParty(leader)) {
            AgentPartyGatewayRuntime.party().leaveCurrentParty(leader);
        }
    }

    private static boolean enabled() {
        return populationEnabled.getAsBoolean() && featureEnabled();
    }

    private static boolean featureEnabled() {
        return config.AgentTuning.booleanValue(
                "server.agents.capabilities.partyquest.hpq.AgentHpqPopulationRuntime.ENABLED");
    }

    private record ChannelKey(int world, int channel) {
    }
}
