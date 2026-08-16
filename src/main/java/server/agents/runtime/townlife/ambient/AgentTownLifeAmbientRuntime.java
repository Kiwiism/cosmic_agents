package server.agents.runtime.townlife.ambient;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.capabilities.movement.AgentMovementBroadcastService;
import server.agents.capabilities.movement.AgentMovementCommandRuntime;
import server.agents.capabilities.movement.AgentMovementStateResetService;
import server.agents.capabilities.party.AgentPartyLifecycleService;
import server.agents.capabilities.townlife.AgentTownLifeAdmissionMode;
import server.agents.capabilities.townlife.AgentTownLifeAmbientState;
import server.agents.capabilities.townlife.AgentTownLifeEntryRequest;
import server.agents.capabilities.townlife.AgentTownLifeExitRequest;
import server.agents.capabilities.townlife.AgentTownLifeProfile;
import server.agents.capabilities.townlife.AgentTownLifeProfileRepository;
import server.agents.capabilities.townlife.AgentTownLifeRuntime;
import server.agents.capabilities.townlife.AgentTownLifeSessionResult;
import server.agents.capabilities.townlife.AgentTownLifeState;
import server.agents.capabilities.townlife.AgentTownLifeVisitRequest;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentInventoryGatewayRuntime;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.plans.mapleisland.cohort.MapleIslandCohortPoolSnapshot;
import server.agents.plans.mapleisland.cohort.MapleIslandCohortRuntime;
import server.agents.runtime.AgentInteractionRuntime;
import server.agents.runtime.AgentLifecycleService;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentSchedulerRuntime;
import server.agents.runtime.townlife.AgentTownLifeVisitLeaseRequest;
import server.agents.runtime.townlife.AgentTownLifeVisitLeaseRuntime;
import server.agents.runtime.townlife.AgentTownLifeTestObservationState;
import server.maps.MapleMap;
import server.maps.Portal;

import java.awt.Point;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/** External observation owner for ambient TownLife population; it never owns an activity tick. */
public final class AgentTownLifeAmbientRuntime {
    private static final Logger log = LoggerFactory.getLogger(AgentTownLifeAmbientRuntime.class);
    private static final long LAUNCH_STAGGER_MS = config.AgentTuning.longValue(
            "server.agents.runtime.townlife.ambient.AgentTownLifeAmbientRuntime.LAUNCH_STAGGER_MS");
    private static final Map<ShardKey, Run> runs = new ConcurrentHashMap<>();

    private AgentTownLifeAmbientRuntime() {
    }

    public static StartResult start(Character operator, Integer requestedPoolSize,
                                    Integer requestedActivePercent,
                                    AgentTownLifeAmbientManifest.StandbyMode requestedStandby,
                                    long nowMs) {
        if (operator == null || !AgentClientGatewayRuntime.clients().hasClient(operator)) {
            return new StartResult(false, "A live GM operator is required.", "", 0, 0);
        }
        ShardKey key = ShardKey.of(operator);
        Run current = runs.get(key);
        if (current != null && !current.terminated) {
            return new StartResult(false, "Ambient TownLife is already active in this world/channel.",
                    current.deploymentId, current.members.size(), current.activeCount());
        }
        AgentTownLifeAmbientManifest manifest =
                AgentTownLifeAmbientManifestRepository.defaultManifest();
        int poolSize = requestedPoolSize == null ? manifest.defaultPoolSize() : requestedPoolSize;
        int activePercent = requestedActivePercent == null
                ? manifest.targetActivePercent() : requestedActivePercent;
        if (poolSize < 1 || poolSize > 100 || activePercent < 0 || activePercent > 100) {
            throw new IllegalArgumentException("pool-size must be 1-100 and active-percent 0-100");
        }
        AgentTownLifeAmbientManifest.StandbyMode standby = requestedStandby == null
                ? manifest.standbyMode() : requestedStandby;
        String deploymentId = "townlife-ambient-" + operator.getId() + '-'
                + UUID.randomUUID().toString().substring(0, 8);
        try {
            List<MapleIslandCohortPoolSnapshot.Agent> pooled = MapleIslandCohortRuntime.instance()
                    .acquireForExternalHarness(poolSize, deploymentId, operator.getId(),
                            operator.getWorld(), AgentClientGatewayRuntime.clients().channel(operator));
            Run run = new Run(deploymentId, operator, manifest, activePercent, standby);
            Map<Integer, Integer> assignments = AgentTownLifeAmbientAllocator.allocateRoster(
                    poolSize, manifest.towns());
            int offset = 0;
            for (AgentTownLifeAmbientManifest.Town town : manifest.towns()) {
                int count = assignments.getOrDefault(town.mapId(), 0);
                for (int index = 0; index < count && offset < pooled.size(); index++) {
                    run.members.add(new Member(pooled.get(offset++), town, index));
                }
            }
            runs.put(key, run);
            rebalance(run, nowMs);
            run.periodic = AgentSchedulerRuntime.register(
                    () -> rebalance(run, System.currentTimeMillis()), manifest.rebalanceEveryMs());
            return new StartResult(true, "Ambient TownLife leased its reusable roster.",
                    deploymentId, run.members.size(), targetActive(run));
        } catch (Exception failure) {
            try {
                MapleIslandCohortRuntime.instance().releaseExternalHarness(deploymentId);
            } catch (IOException releaseFailure) {
                failure.addSuppressed(releaseFailure);
            }
            return new StartResult(false, "Could not start ambient TownLife: "
                    + failure.getMessage(), deploymentId, 0, 0);
        }
    }

    public static StopResult stop(Character operator, long nowMs) {
        Run run = operator == null ? null : runs.get(ShardKey.of(operator));
        if (run == null || run.terminated) {
            return new StopResult(false, "No ambient TownLife deployment is active.", 0);
        }
        synchronized (run) {
            run.stopping = true;
        }
        rebalance(run, nowMs);
        return new StopResult(true,
                "Ambient TownLife is draining current activities before releasing the pool.",
                run.activeCount());
    }

    public static boolean rebalanceNow(Character operator, long nowMs) {
        Run run = operator == null ? null : runs.get(ShardKey.of(operator));
        if (run == null || run.terminated) {
            return false;
        }
        rebalance(run, nowMs);
        return true;
    }

    public static boolean setActivePercent(Character operator, int percent, long nowMs) {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("active-percent must be between 0 and 100");
        }
        Run run = operator == null ? null : runs.get(ShardKey.of(operator));
        if (run == null || run.terminated || run.stopping) {
            return false;
        }
        synchronized (run) {
            run.activePercent = percent;
        }
        rebalance(run, nowMs);
        return true;
    }

    public static Status status(Character operator) {
        Run run = operator == null ? null : runs.get(ShardKey.of(operator));
        return run == null ? null : run.status();
    }

    public static MemberStatus inspect(Character operator, String name) {
        Run run = operator == null ? null : runs.get(ShardKey.of(operator));
        if (run == null || name == null) {
            return null;
        }
        synchronized (run) {
            return run.members.stream()
                    .filter(member -> member.pooled.name().equalsIgnoreCase(name))
                    .findFirst().map(Member::status).orElse(null);
        }
    }

    private static void rebalance(Run run, long nowMs) {
        synchronized (run) {
            if (run.terminated) {
                return;
            }
            reconcile(run, nowMs);
            if (run.stopping) {
                requestDrains(run, nowMs);
                cleanupInactive(run);
                if (run.members.stream().noneMatch(member -> member.active || member.launching)) {
                    terminate(run);
                }
                return;
            }
            Map<Integer, Integer> targets = AgentTownLifeAmbientAllocator.allocate(
                    targetActive(run), run.manifest.towns());
            for (AgentTownLifeAmbientManifest.Town town : run.manifest.towns()) {
                List<Member> members = run.members.stream()
                        .filter(member -> member.town.mapId() == town.mapId())
                        .sorted(Comparator.comparingInt(member -> member.ordinal))
                        .toList();
                int target = targets.getOrDefault(town.mapId(), 0);
                long current = members.stream().filter(member -> member.active || member.launching).count();
                if (current < target) {
                    members.stream()
                            .filter(member -> !member.active && !member.launching
                                    && nowMs >= member.eligibleAtMs)
                            .limit(target - current)
                            .forEach(member -> scheduleLaunch(run, member, true));
                } else if (current > target) {
                    members.stream().filter(member -> member.active && !member.draining)
                            .sorted(Comparator.comparingLong(member -> member.activatedAtMs))
                            .limit(current - target)
                            .forEach(member -> requestExit(run, member, nowMs,
                                    "ambient population rebalance"));
                }
            }
            if (run.standbyMode == AgentTownLifeAmbientManifest.StandbyMode.VISIBLE) {
                run.members.stream().filter(member -> !member.active && !member.launching
                                && member.liveCharacterId == 0 && nowMs >= member.eligibleAtMs)
                        .forEach(member -> scheduleLaunch(run, member, false));
            }
        }
    }

    private static void reconcile(Run run, long nowMs) {
        for (Member member : run.members) {
            if (member.liveCharacterId == 0) {
                continue;
            }
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(
                    member.liveCharacterId);
            Character agent = entry == null ? null : AgentRuntimeIdentityRuntime.bot(entry);
            if (entry == null || agent == null) {
                member.liveCharacterId = 0;
                member.active = false;
                member.draining = false;
                member.launching = false;
                continue;
            }
            AgentTownLifeAmbientState ambient = entry.capabilityStates()
                    .find(AgentTownLifeAmbientState.STATE_KEY).orElse(null);
            if (member.active && ambient != null && ambient.consumeExitSuggestion()) {
                requestExit(run, member, nowMs, "ambient activity requested a rotation");
            }
            if (member.active && !AgentTownLifeRuntime.active(entry)) {
                AgentTownLifeVisitLeaseRuntime.clear(entry, agent);
                entry.capabilityStates().require(AgentTownLifeAmbientState.STATE_KEY).clear();
                member.active = false;
                member.draining = false;
                member.eligibleAtMs = nowMs + member.townProfile().admission().revisitCooldownMs();
                if (run.standbyMode == AgentTownLifeAmbientManifest.StandbyMode.UNMATERIALIZED) {
                    MapleIslandCohortRuntime.instance().stopExternalHarnessAgent(agent.getId());
                    member.liveCharacterId = 0;
                } else {
                    stageAtArrival(entry, agent, member.townProfile(), member.ordinal);
                }
            }
        }
    }

    private static void requestDrains(Run run, long nowMs) {
        for (Member member : run.members) {
            if (member.active && !member.draining) {
                requestExit(run, member, nowMs, "ambient TownLife deployment stopped");
            }
        }
    }

    private static void requestExit(Run run, Member member, long nowMs, String reason) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(member.liveCharacterId);
        Character agent = entry == null ? null : AgentRuntimeIdentityRuntime.bot(entry);
        AgentTownLifeState state = entry == null ? null : entry.capabilityStates()
                .find(AgentTownLifeState.STATE_KEY).orElse(null);
        if (agent == null || state == null || !state.enabled()) {
            member.active = false;
            member.draining = false;
            return;
        }
        var handle = state.sessionHandle(agent.getId());
        if (handle != null) {
            AgentTownLifeRuntime.requestExit(entry, agent, AgentTownLifeExitRequest.graceful(
                    handle, reason, nowMs, nowMs + run.manifest.gracefulExitMs()));
            member.draining = true;
        }
    }

    private static void cleanupInactive(Run run) {
        for (Member member : run.members) {
            if (member.active || member.launching || member.liveCharacterId == 0) {
                continue;
            }
            MapleIslandCohortRuntime.instance().stopExternalHarnessAgent(member.liveCharacterId);
            member.liveCharacterId = 0;
        }
    }

    private static void scheduleLaunch(Run run, Member member, boolean activate) {
        member.launching = true;
        long delay = LAUNCH_STAGGER_MS * Math.floorMod(run.launchOrdinal++, 10);
        if (activate && member.liveCharacterId > 0) {
            AgentSchedulerRuntime.schedule(() -> activateExisting(run, member), delay);
        } else {
            AgentSchedulerRuntime.schedule(() -> launch(run, member, activate), delay);
        }
    }

    private static void activateExisting(Run run, Member member) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(member.liveCharacterId);
        Character agent = entry == null ? null : AgentRuntimeIdentityRuntime.bot(entry);
        synchronized (run) {
            member.launching = false;
            if (run.terminated || run.stopping || entry == null || agent == null) {
                return;
            }
        }
        stageAtArrival(entry, agent, member.townProfile(), member.ordinal);
        activate(run, member, entry, agent, System.currentTimeMillis());
    }

    private static void launch(Run run, Member member, boolean activate) {
        Character agent = null;
        try {
            synchronized (run) {
                if (run.terminated || run.stopping || (!activate
                        && run.standbyMode != AgentTownLifeAmbientManifest.StandbyMode.VISIBLE)) {
                    member.launching = false;
                    return;
                }
            }
            MapleMap map = AgentMapGatewayRuntime.map().resolveMap(
                    run.world, run.channel, member.town.mapId());
            Point point = arrivalPoint(map, member.townProfile(), member.ordinal);
            AgentLifecycleService.AgentSpawnResult spawned = AgentInteractionRuntime
                    .spawnStationaryAgentForLeaderAt(run.operator, member.pooled.name(), map, point);
            if (!spawned.success()) {
                throw new IllegalStateException(spawned.errorMessage());
            }
            agent = spawned.agent();
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(agent.getId());
            if (entry == null) {
                throw new IllegalStateException("spawned ambient TownLife Agent runtime is unavailable");
            }
            AgentPartyLifecycleService.leaveAgentParty(agent);
            AgentMapGatewayRuntime.map().changeMap(agent, map, point);
            AgentMovementStateResetService.resetEntryState(entry);
            AgentMovementBroadcastService.broadcastMovement(entry);
            AgentMovementCommandRuntime.stop(entry);
            provisionChair(agent, run.manifest.chairItemIds(), member.ordinal);
            MapleIslandCohortRuntime.instance().markExternalHarnessAgentActive(
                    agent.getId(), run.deploymentId, System.currentTimeMillis());
            synchronized (run) {
                member.liveCharacterId = agent.getId();
                member.launching = false;
                member.failure = "";
            }
            if (activate) {
                activate(run, member, entry, agent, System.currentTimeMillis());
            }
        } catch (Exception | Error failure) {
            log.warn("Ambient TownLife launch failed deployment={} town={} agent={}",
                    run.deploymentId, member.town.profileId(), member.pooled.name(), failure);
            synchronized (run) {
                member.launching = false;
                member.active = false;
                member.failure = failure.getMessage() == null
                        ? failure.getClass().getSimpleName() : failure.getMessage();
                member.eligibleAtMs = System.currentTimeMillis() + 30000L;
            }
            if (agent != null) {
                MapleIslandCohortRuntime.instance().stopExternalHarnessAgent(agent.getId());
                member.liveCharacterId = 0;
            }
        }
    }

    private static void activate(Run run, Member member, AgentRuntimeEntry entry,
                                 Character agent, long nowMs) {
        entry.capabilityStates().require(AgentTownLifeAmbientState.STATE_KEY).configure(
                run.deploymentId, member.town.behavior(
                        run.manifest.transitions(), run.manifest.chairItemIds()));
        AgentTownLifeProfile profile = member.townProfile();
        long range = profile.admission().maxVisitMs() - profile.admission().minVisitMs() + 1L;
        long duration = profile.admission().minVisitMs()
                + Math.floorMod((long) agent.getId() * 31L + member.visits * 997L, range);
        String requestId = run.deploymentId + ':' + agent.getId() + ':' + member.visits;
        AgentTownLifeVisitRequest visit = new AgentTownLifeVisitRequest(
                profile.mapId(), AgentTownLifeVisitRequest.Purpose.SYSTEM,
                "ambient town population", 0L);
        AgentTownLifeVisitLeaseRequest lease = new AgentTownLifeVisitLeaseRequest(
                AgentTownLifeEntryRequest.external(requestId, run.deploymentId, visit),
                AgentTownLifeAdmissionMode.AMBIENT, nowMs + duration,
                run.manifest.gracefulExitMs(), "ambient visit lease elapsed");
        AgentTownLifeSessionResult result = AgentTownLifeVisitLeaseRuntime.start(
                entry, agent, lease, nowMs, agent.getId());
        synchronized (run) {
            member.launching = false;
            member.active = result.started();
            member.draining = false;
            member.activatedAtMs = result.started() ? nowMs : 0L;
            member.visits += result.started() ? 1 : 0;
            if (!result.started()) {
                member.failure = result.status() + (result.reason().isBlank()
                        ? "" : ": " + result.reason());
                member.eligibleAtMs = nowMs + 5000L;
                entry.capabilityStates().require(AgentTownLifeAmbientState.STATE_KEY).clear();
            } else {
                entry.capabilityStates().require(AgentTownLifeTestObservationState.STATE_KEY)
                        .enable(requestId, true);
            }
        }
    }

    private static void provisionChair(Character agent, List<Integer> chairIds, int ordinal) {
        if (agent == null || chairIds.isEmpty()) {
            return;
        }
        boolean hasChair = chairIds.stream().anyMatch(itemId ->
                agent.getInventory(client.inventory.InventoryType.SETUP).countById(itemId) > 0);
        if (!hasChair) {
            int selected = chairIds.get(Math.floorMod(ordinal, chairIds.size()));
            AgentInventoryGatewayRuntime.inventory().addItem(agent, selected, (short) 1);
        }
    }

    private static void stageAtArrival(AgentRuntimeEntry entry, Character agent,
                                       AgentTownLifeProfile profile, int ordinal) {
        Point point = arrivalPoint(agent.getMap(), profile, ordinal);
        AgentMapGatewayRuntime.map().changeMap(agent, agent.getMap(), point);
        AgentMovementStateResetService.resetEntryState(entry);
        AgentMovementBroadcastService.broadcastMovement(entry);
        AgentMovementCommandRuntime.stop(entry);
    }

    private static Point arrivalPoint(MapleMap map, AgentTownLifeProfile profile, int ordinal) {
        Portal portal = map.getPortal(profile.arrivalPortal(ordinal));
        if (portal == null) {
            portal = map.getPortal(0);
        }
        Point point = portal == null
                ? new Point(map.getRandomPlayerSpawnpoint().getPosition())
                : new Point(portal.getPosition());
        point.translate((Math.floorMod(ordinal, 5) - 2) * 24, 0);
        Point below = AgentMapGatewayRuntime.map().pointBelow(map, point);
        return below == null ? point : below;
    }

    private static int targetActive(Run run) {
        int capacity = run.manifest.towns().stream()
                .mapToInt(AgentTownLifeAmbientManifest.Town::maxActive).sum();
        return Math.min(capacity, Math.min(run.members.size(),
                (run.members.size() * run.activePercent + 50) / 100));
    }

    private static void terminate(Run run) {
        if (run.terminated) {
            return;
        }
        run.terminated = true;
        if (run.periodic != null) {
            run.periodic.cancel(false);
        }
        cleanupInactive(run);
        try {
            MapleIslandCohortRuntime.instance().releaseExternalHarness(run.deploymentId);
        } catch (IOException failure) {
            log.warn("Could not release ambient TownLife pool lease {}", run.deploymentId, failure);
            run.releaseFailure = failure.getMessage();
        }
    }

    public record StartResult(boolean success, String message, String deploymentId,
                              int poolSize, int targetActive) { }

    public record StopResult(boolean success, String message, int draining) { }

    public record TownStatus(String profileId, int mapId, int assigned, int active,
                             int draining, int visibleStandby, int failures) { }

    public record Status(String deploymentId, boolean stopping, boolean terminated,
                         int poolSize, int activePercent, int targetActive,
                         int active, int draining,
                         AgentTownLifeAmbientManifest.StandbyMode standbyMode,
                         List<TownStatus> towns, String releaseFailure) { }

    public record MemberStatus(String name, String profileId, int mapId, boolean live,
                               boolean active, boolean draining, int visits,
                               long activatedAtMs, long eligibleAtMs, String activity,
                               String stage, String lastTransition, String failure) { }

    private record ShardKey(int world, int channel) {
        static ShardKey of(Character operator) {
            return new ShardKey(operator.getWorld(),
                    AgentClientGatewayRuntime.clients().channel(operator));
        }
    }

    private static final class Run {
        private final String deploymentId;
        private final Character operator;
        private final int world;
        private final int channel;
        private final AgentTownLifeAmbientManifest manifest;
        private final AgentTownLifeAmbientManifest.StandbyMode standbyMode;
        private final List<Member> members = new ArrayList<>();
        private int activePercent;
        private int launchOrdinal;
        private boolean stopping;
        private boolean terminated;
        private ScheduledFuture<?> periodic;
        private String releaseFailure = "";

        private Run(String deploymentId, Character operator,
                    AgentTownLifeAmbientManifest manifest, int activePercent,
                    AgentTownLifeAmbientManifest.StandbyMode standbyMode) {
            this.deploymentId = deploymentId;
            this.operator = operator;
            this.world = operator.getWorld();
            this.channel = AgentClientGatewayRuntime.clients().channel(operator);
            this.manifest = manifest;
            this.activePercent = activePercent;
            this.standbyMode = standbyMode;
        }

        private int activeCount() {
            return (int) members.stream().filter(member -> member.active).count();
        }

        private Status status() {
            synchronized (this) {
                List<TownStatus> townStatuses = manifest.towns().stream().map(town -> {
                    List<Member> local = members.stream()
                            .filter(member -> member.town.mapId() == town.mapId()).toList();
                    return new TownStatus(town.profileId(), town.mapId(), local.size(),
                            (int) local.stream().filter(member -> member.active).count(),
                            (int) local.stream().filter(member -> member.draining).count(),
                            (int) local.stream().filter(member -> member.liveCharacterId > 0
                                    && !member.active).count(),
                            (int) local.stream().filter(member -> !member.failure.isBlank()).count());
                }).toList();
                return new Status(deploymentId, stopping, terminated, members.size(), activePercent,
                        targetActive(this), activeCount(),
                        (int) members.stream().filter(member -> member.draining).count(),
                        standbyMode, townStatuses, releaseFailure);
            }
        }
    }

    private static final class Member {
        private final MapleIslandCohortPoolSnapshot.Agent pooled;
        private final AgentTownLifeAmbientManifest.Town town;
        private final int ordinal;
        private int liveCharacterId;
        private boolean launching;
        private boolean active;
        private boolean draining;
        private int visits;
        private long activatedAtMs;
        private long eligibleAtMs;
        private String failure = "";

        private Member(MapleIslandCohortPoolSnapshot.Agent pooled,
                       AgentTownLifeAmbientManifest.Town town, int ordinal) {
            this.pooled = pooled;
            this.town = town;
            this.ordinal = ordinal;
        }

        private AgentTownLifeProfile townProfile() {
            return AgentTownLifeProfileRepository.defaultRepository().require(town.mapId());
        }

        private MemberStatus status() {
            AgentRuntimeEntry entry = liveCharacterId <= 0 ? null
                    : AgentRuntimeRegistry.findByAgentCharacterId(liveCharacterId);
            AgentTownLifeState state = entry == null ? null : entry.capabilityStates()
                    .find(AgentTownLifeState.STATE_KEY).orElse(null);
            AgentTownLifeAmbientState ambient = entry == null ? null : entry.capabilityStates()
                    .find(AgentTownLifeAmbientState.STATE_KEY).orElse(null);
            return new MemberStatus(pooled.name(), town.profileId(), town.mapId(),
                    liveCharacterId > 0, active, draining, visits, activatedAtMs, eligibleAtMs,
                    state == null ? "NONE" : state.activity().name(),
                    state == null ? "DISABLED" : state.stage().name(),
                    ambient == null ? "NONE" : ambient.lastTransition().name(), failure);
        }
    }
}
