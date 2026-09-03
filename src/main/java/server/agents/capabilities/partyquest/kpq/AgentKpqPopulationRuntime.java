package server.agents.capabilities.partyquest.kpq;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.integration.AgentPartyGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentSchedulerRuntime;
import server.agents.runtime.activity.AgentActivityBootstrap;
import server.agents.capabilities.partyquest.AgentPartyQuestEngagement;
import server.agents.capabilities.partyquest.AgentPartyQuestEngagementRegistry;
import server.agents.capabilities.partyquest.AgentPartyQuestLifecycleRuntime;
import server.agents.capabilities.partyquest.AgentPartyQuestRuntime;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestCandidateScope;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyRuntime;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbySession;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.Set;

/** Self-contained population director for exact, unobserved KPQ gameplay. */
public final class AgentKpqPopulationRuntime {
    private static final Logger log = LoggerFactory.getLogger(AgentKpqPopulationRuntime.class);
    private static final Map<Integer, Long> lastAdmissionByCharacter = new ConcurrentHashMap<>();
    private static ScheduledFuture<?> task;
    private static BooleanSupplier populationEnabled = () -> false;
    private static Supplier<Set<Integer>> managedCharacterIds = Set::of;

    private AgentKpqPopulationRuntime() {
    }

    public static synchronized void start(
            BooleanSupplier enabledSource, Supplier<Set<Integer>> managedIdsSource) {
        if (task != null) return;
        populationEnabled = enabledSource == null ? () -> false : enabledSource;
        managedCharacterIds = managedIdsSource == null ? Set::of : managedIdsSource;
        if (!featureEnabled()) return;
        long sweepMs = Math.max(5_000L, config.AgentTuning.longValue(
                "server.agents.capabilities.partyquest.kpq.AgentKpqPopulationRuntime.SWEEP_MS"));
        task = AgentSchedulerRuntime.register(AgentKpqPopulationRuntime::sweepSafely, sweepMs);
        AgentSchedulerRuntime.schedule(AgentKpqPopulationRuntime::sweepSafely, sweepMs);
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
            log.warn("Autonomous KPQ population sweep failed closed", failure);
        }
    }

    static synchronized int sweep(long nowMs) {
        if (!enabled()) return 0;
        int partySize = Math.max(3, Math.min(4, config.AgentTuning.intValue(
                "server.agents.capabilities.partyquest.kpq.AgentKpqPopulationRuntime.PARTY_SIZE")));
        long cooldownMs = Math.max(0L, config.AgentTuning.longValue(
                "server.agents.capabilities.partyquest.kpq.AgentKpqPopulationRuntime.REENTRY_COOLDOWN_MS"));
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
            if (!AgentKpqLobbyPolicy.backgroundSlotAvailable(
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
        if (agent == null || agent.getLevel() < 21 || agent.getLevel() > 30 || agent.getHp() <= 0
                || AgentKpqRuntime.active(agent.getId())
                || AgentPartyGatewayRuntime.party().hasParty(agent)) {
            return false;
        }
        Long lastAdmission = lastAdmissionByCharacter.get(agent.getId());
        return lastAdmission == null || nowMs - lastAdmission >= cooldownMs;
    }

    private static boolean admitParty(ChannelKey channel, List<Character> members, long nowMs) {
        List<Integer> queued = new ArrayList<>();
        try {
            MapleMap kerning = AgentMapGatewayRuntime.map().resolveMap(
                    channel.world(), channel.channel(), AgentKpqDefinition.RECRUIT_MAP);
            if (kerning == null) throw new IllegalStateException("KPQ recruitment map is unavailable");
            var portal = kerning.getRandomPlayerSpawnpoint();
            Point spawn = portal == null ? new Point(0, 0) : portal.getPosition();
            int index = 0;
            for (Character member : members) {
                AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(member.getId());
                if (entry == null || !AgentActivityBootstrap.admission().prepare(
                        AgentActivityBootstrap.PARTY_QUEST_CONTROLLER_ID, entry, member,
                        "selected for background KPQ", nowMs)) {
                    throw new IllegalStateException(member.getName()
                            + " could not release its current activity");
                }
                Point staggered = new Point(spawn.x + index * 18, spawn.y);
                AgentMapGatewayRuntime.map().changeMapNear(member, kerning, staggered);
                var result = AgentPartyQuestRuntime.requireSystem("kpq").requestEntry(
                        entry, member, "kpq", members.size(), 1, nowMs);
                if (result.status()
                        != server.agents.runtime.activity.session.AgentActivityAdmissionResult.Status.ACCEPTED) {
                    throw new IllegalStateException(member.getName() + " could not queue: " + result.reason());
                }
                queued.add(member.getId());
                index++;
            }
            log.info("Background KPQ queued: world={} channel={} members={}",
                    channel.world(), channel.channel(),
                    members.stream().map(Character::getName).toList());
            return true;
        } catch (RuntimeException failure) {
            queued.forEach(id -> AgentPartyQuestRuntime.forceStop(
                    id, "background KPQ queue rolled back", nowMs));
            log.warn("Background KPQ queue rolled back for members {}",
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
                "server.agents.capabilities.partyquest.kpq.AgentKpqPopulationRuntime.ENABLED");
    }

    private record ChannelKey(int world, int channel) {
    }
}
