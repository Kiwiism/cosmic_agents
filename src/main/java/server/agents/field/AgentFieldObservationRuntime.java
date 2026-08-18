package server.agents.field;

import client.Character;
import constants.game.ExpTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.capabilities.movement.AgentMovementBroadcastService;
import server.agents.capabilities.movement.AgentMovementCommandRuntime;
import server.agents.capabilities.movement.AgentMovementProfile;
import server.agents.capabilities.movement.AgentMovementStateResetService;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.party.AgentPartyLifecycleService;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.integration.AgentPartyGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.plans.mapleisland.cohort.MapleIslandCohortPoolSnapshot;
import server.agents.plans.mapleisland.cohort.MapleIslandCohortRuntime;
import server.agents.runtime.AgentInteractionRuntime;
import server.agents.runtime.AgentLifecycleService;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.field.AgentFieldSafeSpotPolicy;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentSchedulerRuntime;
import server.maps.MapleMap;

import java.awt.Point;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/** Test-only multi-map deployment harness; normal field admission remains capped at six. */
public final class AgentFieldObservationRuntime {
    private static final Logger log = LoggerFactory.getLogger(AgentFieldObservationRuntime.class);
    private static final long LAUNCH_STAGGER_MS = config.AgentTuning.longValue(
            "server.agents.field.AgentFieldObservationRuntime.LAUNCH_STAGGER_MS");
    private static final Map<ShardKey, Run> runs = new ConcurrentHashMap<>();

    private AgentFieldObservationRuntime() {
    }

    public static StartResult start(
            Character operator,
            String selectedGroup,
            Integer selectedMapId,
            long seed,
            long nowMs) {
        if (operator == null || !AgentClientGatewayRuntime.clients().hasClient(operator)) {
            return new StartResult(false, "A live GM operator is required.", "", 0, 0);
        }
        ShardKey key = ShardKey.of(operator);
        Run current = runs.get(key);
        if (current != null && current.active) {
            return new StartResult(false, "An observation deployment is already active in this world/channel.",
                    current.sessionId, current.maps.size(), current.totalAgents);
        }
        AgentFieldObservationCatalogRepository repository =
                AgentFieldObservationCatalogRepository.defaultRepository();
        List<AgentFieldObservationCatalog.MapPreset> presets = selectedMapId != null
                ? repository.find(selectedMapId).map(List::of).orElse(List.of())
                : selectedGroup == null || "all".equals(selectedGroup)
                        ? repository.maps()
                        : repository.maps(selectedGroup);
        if (presets.isEmpty()) {
            String selector = selectedMapId == null ? selectedGroup : "map " + selectedMapId;
            return new StartResult(false, "Observation selector " + selector
                    + " is not in the level 15-25 manifest.",
                    "", 0, 0);
        }
        int total = presets.stream().mapToInt(AgentFieldObservationCatalog.MapPreset::maximumAgents).sum();
        String sessionId = "field-observe-" + operator.getId() + '-'
                + UUID.randomUUID().toString().substring(0, 8);
        try {
            List<MapleIslandCohortPoolSnapshot.Agent> pooled = MapleIslandCohortRuntime.instance()
                    .acquireForExternalHarness(total, sessionId, operator.getId(), operator.getWorld(),
                            AgentClientGatewayRuntime.clients().channel(operator));
            Run run = new Run(sessionId, operator, seed, nowMs,
                    repository.catalog().rotationWindowMs(), repository.catalog().supplyDurationMs());
            int offset = 0;
            for (AgentFieldObservationCatalog.MapPreset preset : presets) {
                List<MapleIslandCohortPoolSnapshot.Agent> assignment = List.copyOf(
                        pooled.subList(offset, offset + preset.maximumAgents()));
                offset += preset.maximumAgents();
                run.maps.put(preset.mapId(), new MapRun(preset, assignment));
            }
            run.totalAgents = total;
            runs.put(key, run);
            int ordinal = 0;
            for (MapRun mapRun : run.maps.values()) {
                for (MapleIslandCohortPoolSnapshot.Agent pooledAgent : mapRun.pooled) {
                    int launchOrdinal = ordinal++;
                    run.tasks.add(AgentSchedulerRuntime.schedule(
                            () -> launch(run, mapRun, pooledAgent, launchOrdinal),
                            LAUNCH_STAGGER_MS * launchOrdinal));
                }
            }
            return new StartResult(true, "Observation deployment is provisioning from the reusable Agent pool.",
                    sessionId, presets.size(), total);
        } catch (Exception failure) {
            try {
                MapleIslandCohortRuntime.instance().releaseExternalHarness(sessionId);
            } catch (IOException releaseFailure) {
                failure.addSuppressed(releaseFailure);
            }
            return new StartResult(false, "Could not lease the observation roster: " + failure.getMessage(),
                    "", 0, 0);
        }
    }

    public static Status status(Character operator) {
        Run run = operator == null ? null : runs.get(ShardKey.of(operator));
        return run == null ? null : run.status();
    }

    public static MapStatus statusForMapId(int mapId) {
        return runs.values().stream()
                .filter(run -> run.maps.containsKey(mapId))
                .max(Comparator.comparingLong(run -> run.startedAtMs))
                .map(run -> run.maps.get(mapId).status())
                .orElse(null);
    }

    public static boolean rotateNow(Character operator) {
        Run run = operator == null ? null : runs.get(ShardKey.of(operator));
        if (run == null || !run.active) {
            return false;
        }
        run.maps.values().stream().filter(MapRun::ready).forEach(map -> rotate(run, map));
        return true;
    }

    public static StopResult stop(Character operator, long nowMs) {
        if (operator == null) {
            return new StopResult(false, "A live operator is required.", 0);
        }
        Run run = runs.get(ShardKey.of(operator));
        if (run == null || !run.active) {
            return new StopResult(false, "No observation deployment is active in this world/channel.", 0);
        }
        int stopped = 0;
        synchronized (run) {
            run.active = false;
            run.tasks.forEach(task -> task.cancel(false));
            for (MapRun mapRun : run.maps.values()) {
                Character anchor = mapRun.anchor();
                if (mapRun.ready() && mapRun.phaseStartedAtMs > 0L) {
                    captureSample(mapRun, nowMs);
                }
                if (anchor != null) {
                    AgentFieldRuntime.stop(anchor, nowMs);
                }
                for (AgentRuntimeEntry entry : mapRun.roster) {
                    Character agent = AgentRuntimeIdentityRuntime.bot(entry);
                    if (agent != null) {
                        AgentMovementCommandRuntime.stop(entry);
                        AgentPartyLifecycleService.leaveAgentParty(agent);
                        MapleIslandCohortRuntime.instance().stopExternalHarnessAgent(agent.getId());
                        stopped++;
                    }
                }
            }
        }
        try {
            MapleIslandCohortRuntime.instance().releaseExternalHarness(run.sessionId);
        } catch (IOException failure) {
            return new StopResult(true, "Stopped Agents, but the durable pool lease release failed: "
                    + failure.getMessage(), stopped);
        }
        return new StopResult(true, "Stopped the observation deployment and released its pool lease.", stopped);
    }

    private static void launch(Run run, MapRun mapRun,
                               MapleIslandCohortPoolSnapshot.Agent pooled, int ordinal) {
        launch(run, mapRun, pooled, ordinal, 0);
    }

    private static void launch(Run run, MapRun mapRun,
                               MapleIslandCohortPoolSnapshot.Agent pooled, int ordinal,
                               int graphWarmupAttempt) {
        if (!run.active) {
            return;
        }
        Character agent = null;
        try {
            MapleMap map = AgentMapGatewayRuntime.map().resolveMap(
                    run.world, run.channel, mapRun.preset.mapId());
            AgentNavigationGraph baseGraph = AgentNavigationGraphService.peekGraph(
                    map, AgentMovementProfile.base());
            if (baseGraph == null) {
                AgentNavigationGraphService.warmGraphAsync(map, AgentMovementProfile.base());
                if (graphWarmupAttempt < AgentFieldPolicyConfig.maximumGraphWarmupAttempts()) {
                    run.tasks.add(AgentSchedulerRuntime.schedule(
                            () -> launch(run, mapRun, pooled, ordinal, graphWarmupAttempt + 1),
                            AgentFieldPolicyConfig.graphWarmupRetryMs()));
                    return;
                }
                throw new IllegalStateException("navigation graph did not warm before field entry");
            }
            Point staging = AgentFieldSafeSpotPolicy.staging(map, baseGraph, ordinal);
            Point entryPoint = AgentFieldSafeSpotPolicy.nearestEntry(map, baseGraph, staging);
            if (staging == null || entryPoint == null) {
                throw new IllegalStateException(
                        "map has no graph-connected safe spot and player-spawn portal");
            }
            AgentLifecycleService.AgentSpawnResult spawned = AgentInteractionRuntime
                    .spawnStationaryAgentForLeaderAt(run.operator, pooled.name(), map, entryPoint);
            if (!spawned.success()) {
                throw new IllegalStateException(spawned.errorMessage());
            }
            agent = spawned.agent();
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(agent.getId());
            if (entry == null) {
                throw new IllegalStateException("spawned Agent runtime is unavailable");
            }
            AgentPartyLifecycleService.leaveAgentParty(agent);
            long agentSeed = mix(run.seed, mapRun.preset.mapId(), ordinal, agent.getId());
            AgentFieldObservationFixtureService.Prepared prepared =
                    AgentFieldObservationFixtureService.prepare(entry, mapRun.preset.level(),
                            mapRun.preset.allowedMobIds(), agentSeed, System.currentTimeMillis());
            AgentNavigationGraphService.warmGraphAsync(
                    entry, map, AgentMovementStateRuntime.movementProfileOrCharacter(entry, agent));
            entry.capabilityStates().require(AgentFieldObservationState.STATE_KEY)
                    .narrationLevel(AgentFieldObservationState.NarrationLevel.OFF);
            AgentMapGatewayRuntime.map().changeMap(agent, map, entryPoint);
            AgentMovementStateResetService.resetEntryState(entry);
            AgentMovementBroadcastService.broadcastMovement(entry);
            AgentMovementCommandRuntime.stop(entry);
            MapleIslandCohortRuntime.instance().markExternalHarnessAgentActive(
                    agent.getId(), run.sessionId, System.currentTimeMillis());
            synchronized (run) {
                if (!run.active) {
                    MapleIslandCohortRuntime.instance().stopExternalHarnessAgent(agent.getId());
                    return;
                }
                synchronized (mapRun) {
                    mapRun.roster.add(entry);
                    mapRun.prepared.put(agent.getId(), prepared);
                    mapRun.idleStaging.put(agent.getId(), new Point(staging));
                    mapRun.completedLaunches++;
                }
            }
        } catch (Exception | Error failure) {
            log.warn("Observation launch failed session={} map={} agent={}",
                    run.sessionId, mapRun.preset.mapId(), pooled.name(), failure);
            synchronized (mapRun) {
                mapRun.completedLaunches++;
                mapRun.failures.add(pooled.name() + ": " + failure.getMessage());
            }
            if (agent != null) {
                MapleIslandCohortRuntime.instance().stopExternalHarnessAgent(agent.getId());
            }
            try {
                MapleIslandCohortRuntime.instance().markExternalHarnessAgentBroken(
                        pooled.characterId(), run.sessionId, failure.getMessage());
            } catch (IOException markFailure) {
                log.warn("Could not mark observation pool Agent broken {}", pooled.name(), markFailure);
            }
        }
        synchronized (mapRun) {
            if (mapRun.completedLaunches == mapRun.pooled.size() && !mapRun.initialized) {
                mapRun.initialized = true;
                if (!mapRun.roster.isEmpty()) {
                    initializeMap(run, mapRun);
                }
            }
        }
    }

    private static void initializeMap(Run run, MapRun mapRun) {
        mapRun.roster.sort(Comparator.comparing(AgentRuntimeIdentityRuntime::botName));
        createStableParties(mapRun);
        applyPhase(run, mapRun, 0);
        captureSample(mapRun, mapRun.phaseStartedAtMs);
        mapRun.rotationTask = AgentSchedulerRuntime.schedule(
                () -> rotate(run, mapRun), phaseWindowMs(run, mapRun));
        run.tasks.add(mapRun.rotationTask);
    }

    private static void rotate(Run run, MapRun mapRun) {
        synchronized (mapRun) {
            if (!run.active || !mapRun.ready()) {
                return;
            }
            captureSample(mapRun, System.currentTimeMillis());
            int next = (mapRun.phase + 1) % mapRun.preset.activeCounts().size();
            applyPhase(run, mapRun, next);
            captureSample(mapRun, mapRun.phaseStartedAtMs);
            mapRun.rotationTask = AgentSchedulerRuntime.schedule(
                    () -> rotate(run, mapRun), phaseWindowMs(run, mapRun));
            run.tasks.add(mapRun.rotationTask);
        }
    }

    private static void applyPhase(Run run, MapRun mapRun, int phase) {
        mapRun.phase = phase;
        Character anchor = mapRun.anchor();
        if (anchor == null) {
            return;
        }
        AgentFieldRuntime.stop(anchor, System.currentTimeMillis());
        mapRun.roster.forEach(AgentMovementCommandRuntime::stop);
        int activeCount = Math.min(mapRun.preset.activeCounts().get(phase), mapRun.roster.size());
        mapRun.phaseActiveCount = activeCount;
        List<AgentRuntimeEntry> shuffled = new ArrayList<>(mapRun.roster);
        shuffle(shuffled, mix(run.seed, mapRun.preset.mapId(), phase, mapRun.samples.size()));
        Set<AgentRuntimeEntry> active = new LinkedHashSet<>(shuffled.subList(0, activeCount));
        stageInactive(mapRun, active);
        if (active.isEmpty()) {
            return;
        }
        Character fieldAnchor = AgentRuntimeIdentityRuntime.bot(active.iterator().next());
        AgentFieldRuntime.StartResult result = AgentFieldRuntime.startObservation(
                fieldAnchor, List.copyOf(active), mapRun.preset.allowedMobIds(), System.currentTimeMillis());
        if (!result.success()) {
            mapRun.failures.add("phase " + phase + ": " + result.message());
            return;
        }
        active.forEach(AgentMovementCommandRuntime::grind);
        mapRun.activeAgentIds = active.stream().map(AgentRuntimeIdentityRuntime::botId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        mapRun.phaseStartedAtMs = System.currentTimeMillis();
    }

    private static void stageInactive(MapRun mapRun, Set<AgentRuntimeEntry> active) {
        int index = 0;
        for (AgentRuntimeEntry entry : mapRun.roster) {
            if (active.contains(entry)) {
                continue;
            }
            Character agent = AgentRuntimeIdentityRuntime.bot(entry);
            if (agent == null || agent.getMap() == null) {
                continue;
            }
            Point staging = AgentFieldSafeSpotPolicy.staging(entry, agent, index++);
            if (staging == null) {
                staging = mapRun.idleStaging.get(agent.getId());
            }
            if (staging == null) {
                continue;
            }
            AgentMovementCommandRuntime.moveTo(entry, staging, true);
        }
    }

    private static void createStableParties(MapRun mapRun) {
        int offset = 0;
        for (int size : mapRun.preset.partySizes()) {
            if (offset >= mapRun.roster.size()) {
                break;
            }
            int end = Math.min(mapRun.roster.size(), offset + size);
            Character leader = AgentRuntimeIdentityRuntime.bot(mapRun.roster.get(offset));
            if (leader != null && AgentPartyGatewayRuntime.party().createAgentParty(leader)) {
                for (int i = offset + 1; i < end; i++) {
                    AgentPartyLifecycleService.joinAgentToLeaderParty(
                            leader, AgentRuntimeIdentityRuntime.bot(mapRun.roster.get(i)));
                }
            }
            offset = end;
        }
    }

    private static void captureSample(MapRun mapRun, long nowMs) {
        Character anchor = mapRun.anchor();
        AgentFieldSnapshot field = anchor == null ? null : AgentFieldRuntime.snapshot(anchor, nowMs);
        LinkedHashMap<Integer, AgentSample> agents = new LinkedHashMap<>();
        for (AgentRuntimeEntry entry : mapRun.roster) {
            Character agent = AgentRuntimeIdentityRuntime.bot(entry);
            if (agent == null) {
                continue;
            }
            AgentFieldSnapshot.Participant participant = field == null ? null : field.participants().stream()
                    .filter(candidate -> candidate.agentId() == agent.getId())
                    .findFirst().orElse(null);
            long kills = participant == null ? 0L : participant.kills();
            agents.put(agent.getId(), new AgentSample(agent.getId(), agent.getName(), agent.getJob().getId(), agent.getLevel(),
                    mapRun.activeAgentIds.contains(agent.getId()), kills, agent.getExp(), totalExperience(agent),
                    participant == null ? 0L : participant.attacks(),
                    participant == null ? 0L : participant.hitLines(),
                    participant == null ? 0L : participant.missLines(),
                    participant == null ? 0L : participant.damage(),
                    participant == null ? 0L : participant.assignmentChanges(),
                    participant == null ? 0L : participant.routeFailures(),
                    participant == null ? 0L : participant.stuckDetections(),
                    participant == null ? Map.of() : participant.postureTimeMs(),
                    participant == null ? "IDLE" : participant.combatPosture(),
                    participant == null ? "IDLE" : participant.lifecycle()));
        }
        List<StationSample> stations = field == null ? List.of() : field.participants().stream()
                .map(participant -> new StationSample(participant.agentId(), participant.name(),
                        participant.partyId(), participant.cellIds(), participant.regionIds(),
                        participant.anchorX(), participant.anchorY(),
                        participant.positionX(), participant.positionY(), participant.role().name(),
                        participant.combatPosture(), participant.targetMobId(),
                        participant.targetX(), participant.targetY()))
                .toList();
        int conflicts = platformConflicts(field, stations);
        int offAnchor = (int) stations.stream().filter(station -> {
            long dx = (long) station.positionX() - station.anchorX();
            long dy = (long) station.positionY() - station.anchorY();
            return dx * dx + dy * dy > 600L * 600L;
        }).count();
        int emptyAssignments = field == null ? 0 : emptyAssignments(field, stations);
        mapRun.samples.add(new WindowSample(mapRun.phase, mapRun.phaseStartedAtMs, nowMs,
                mapRun.activeAgentIds.size(), field == null ? 0 : field.liveMobs(),
                emptyAssignments, conflicts, offAnchor,
                List.copyOf(agents.values()), stations));
    }

    private static int platformConflicts(
            AgentFieldSnapshot field, List<StationSample> stations) {
        if (field == null) {
            return 0;
        }
        Map<String, Integer> users = new LinkedHashMap<>();
        stations.forEach(station -> station.cellIds().forEach(
                cellId -> users.merge(cellId, 1, Integer::sum)));
        Map<String, Integer> capacities = field.cells().stream().collect(
                java.util.stream.Collectors.toMap(
                        AgentFieldSnapshot.Cell::cellId, AgentFieldSnapshot.Cell::capacity));
        int overCapacity = users.entrySet().stream().mapToInt(entry -> Math.max(
                0, entry.getValue() - capacities.getOrDefault(entry.getKey(), 1))).sum();
        Map<String, Integer> stationUsers = new LinkedHashMap<>();
        stations.forEach(station -> station.cellIds().forEach(cellId -> stationUsers.merge(
                cellId + ':' + station.anchorX() + ':' + station.anchorY(), 1, Integer::sum)));
        int duplicateStations = stationUsers.values().stream()
                .mapToInt(count -> Math.max(0, count - 1)).sum();
        return Math.max(overCapacity, duplicateStations);
    }

    private static int emptyAssignments(AgentFieldSnapshot field, List<StationSample> stations) {
        Map<String, AgentFieldSnapshot.Cell> cells = field.cells().stream()
                .collect(java.util.stream.Collectors.toMap(
                        AgentFieldSnapshot.Cell::cellId, cell -> cell));
        return (int) stations.stream().filter(station -> !station.cellIds().isEmpty()
                && station.cellIds().stream().map(cells::get)
                .filter(java.util.Objects::nonNull)
                .allMatch(cell -> cell.mobCounts().values().stream()
                        .mapToInt(Integer::intValue).sum() == 0)).count();
    }

    static List<CapacityWindow> capacityWindows(List<WindowSample> samples) {
        ArrayList<CapacityWindow> windows = new ArrayList<>();
        for (int index = 1; index < samples.size(); index++) {
            WindowSample start = samples.get(index - 1);
            WindowSample end = samples.get(index);
            if (start.phase() != end.phase() || start.startedAtMs() != end.startedAtMs()
                    || end.endedAtMs() <= start.endedAtMs()) {
                continue;
            }
            long durationMs = end.endedAtMs() - start.endedAtMs();
            Map<Integer, AgentSample> startingAgents = start.agents().stream()
                    .collect(java.util.stream.Collectors.toMap(AgentSample::agentId, agent -> agent));
            long kills = 0L;
            long experience = 0L;
            long attacks = 0L;
            long assignments = 0L;
            long routeFailures = 0L;
            long stuckDetections = 0L;
            long nonCombatMs = 0L;
            for (AgentSample agent : end.agents()) {
                if (!agent.active()) {
                    continue;
                }
                AgentSample before = startingAgents.get(agent.agentId());
                if (before == null) {
                    continue;
                }
                kills += delta(agent.kills(), before.kills());
                experience += delta(agent.totalExp(), before.totalExp());
                attacks += delta(agent.attacks(), before.attacks());
                assignments += delta(agent.assignmentChanges(), before.assignmentChanges());
                routeFailures += delta(agent.routeFailures(), before.routeFailures());
                stuckDetections += delta(agent.stuckDetections(), before.stuckDetections());
                nonCombatMs += postureDelta(agent, before, "SEARCHING")
                        + postureDelta(agent, before, "IDLE");
            }
            long activeTimeMs = durationMs * Math.max(1, end.activeAgents());
            long nonCombatBasisPoints = Math.min(10_000L,
                    nonCombatMs * 10_000L / Math.max(1L, activeTimeMs));
            long killsPerAgentMinuteBasisPoints = kills * 60_000L * 10_000L
                    / Math.max(1L, activeTimeMs);
            windows.add(new CapacityWindow(
                    end.phase(), end.activeAgents(), durationMs, kills, experience,
                    attacks, assignments, routeFailures, stuckDetections,
                    nonCombatBasisPoints, killsPerAgentMinuteBasisPoints,
                    end.liveMobs(), end.emptyAssignedPlatforms(), end.platformConflicts(),
                    end.offAnchorAgents()));
        }
        return List.copyOf(windows);
    }

    private static long postureDelta(AgentSample after, AgentSample before, String posture) {
        return delta(after.postureTimeMs().getOrDefault(posture, 0L),
                before.postureTimeMs().getOrDefault(posture, 0L));
    }

    private static long delta(long after, long before) {
        return Math.max(0L, after - before);
    }

    static long phaseWindowMs(long baseWindowMs, int activeCount) {
        return Math.max(1L, baseWindowMs)
                + Math.max(0, activeCount - 1) * 60_000L;
    }

    private static long phaseWindowMs(Run run, MapRun mapRun) {
        return phaseWindowMs(run.rotationWindowMs, mapRun.phaseActiveCount);
    }

    private static void shuffle(List<AgentRuntimeEntry> entries, long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        for (int i = entries.size() - 1; i > 0; i--) {
            java.util.Collections.swap(entries, i, random.nextInt(i + 1));
        }
    }

    private static long mix(long seed, long... values) {
        long mixed = seed;
        for (long value : values) {
            mixed ^= value + 0x9E3779B97F4A7C15L + (mixed << 6) + (mixed >>> 2);
        }
        return mixed;
    }

    private static long totalExperience(Character agent) {
        long total = agent.getExp();
        for (int level = 1; level < agent.getLevel(); level++) {
            total += ExpTable.getExpNeededForLevel(level);
        }
        return total;
    }

    public record StartResult(boolean success, String message, String sessionId, int maps, int agents) {
    }

    public record StopResult(boolean success, String message, int stoppedAgents) {
    }

    public record AgentSample(int agentId, String name, int jobId, int level, boolean active, long kills,
                              int exp, long totalExp, long attacks, long hitLines,
                              long missLines, long damage, long assignmentChanges,
                              long routeFailures, long stuckDetections,
                              Map<String, Long> postureTimeMs, String combatPosture,
                              String lifecycle) {
        public AgentSample {
            postureTimeMs = Map.copyOf(postureTimeMs);
        }
    }

    public record StationSample(int agentId, String name, int partyId, List<String> cellIds,
                                List<Integer> regionIds, int anchorX, int anchorY,
                                int positionX, int positionY, String role,
                                String combatPosture, int targetMobId, int targetX, int targetY) {
        public StationSample {
            cellIds = List.copyOf(cellIds);
            regionIds = List.copyOf(regionIds);
        }
    }

    public record WindowSample(int phase, long startedAtMs, long endedAtMs, int activeAgents,
                               int liveMobs, int emptyAssignedPlatforms,
                               int platformConflicts, int offAnchorAgents,
                               List<AgentSample> agents, List<StationSample> stations) {
        public WindowSample {
            agents = List.copyOf(agents);
            stations = List.copyOf(stations);
        }
    }

    public record CapacityWindow(
            int phase,
            int activeAgents,
            long durationMs,
            long kills,
            long experience,
            long attacks,
            long assignmentChanges,
            long routeFailures,
            long stuckDetections,
            long nonCombatBasisPoints,
            long killsPerAgentMinuteBasisPoints,
            int liveMobs,
            int emptyAssignedPlatforms,
            int platformConflicts,
            int offAnchorAgents) {
    }

    public record MapStatus(int mapId, String mapName, int level, int readyAgents, int expectedAgents,
                             int phase, int activeAgents, List<Integer> activeCounts,
                             List<String> failures, List<PreparedAgent> preparedAgents,
                             List<WindowSample> samples, List<CapacityWindow> capacityWindows) {
        public MapStatus {
            activeCounts = List.copyOf(activeCounts);
            failures = List.copyOf(failures);
            preparedAgents = List.copyOf(preparedAgents);
            samples = List.copyOf(samples);
            capacityWindows = List.copyOf(capacityWindows);
        }
    }

    public record PreparedAgent(String name, int level, String bundleId, String apProfileId,
                                String spProfileId, List<Integer> equipmentItemIds,
                                int projectileItemId) {
        public PreparedAgent {
            equipmentItemIds = List.copyOf(equipmentItemIds);
        }
    }

    public record Status(String sessionId, long seed, long startedAtMs, boolean active,
                         int totalAgents, long supplyDurationMs, List<MapStatus> maps) {
        public Status {
            maps = List.copyOf(maps);
        }
    }

    private record ShardKey(int world, int channel) {
        static ShardKey of(Character character) {
            return new ShardKey(AgentClientGatewayRuntime.clients().world(character),
                    AgentClientGatewayRuntime.clients().channel(character));
        }
    }

    private static final class Run {
        private final String sessionId;
        private final Character operator;
        private final int world;
        private final int channel;
        private final long seed;
        private final long startedAtMs;
        private final long rotationWindowMs;
        private final long supplyDurationMs;
        private final Map<Integer, MapRun> maps = new LinkedHashMap<>();
        private final List<ScheduledFuture<?>> tasks = new java.util.concurrent.CopyOnWriteArrayList<>();
        private volatile boolean active = true;
        private volatile int totalAgents;

        private Run(String sessionId, Character operator, long seed, long startedAtMs,
                    long rotationWindowMs, long supplyDurationMs) {
            this.sessionId = sessionId;
            this.operator = operator;
            this.world = AgentClientGatewayRuntime.clients().world(operator);
            this.channel = AgentClientGatewayRuntime.clients().channel(operator);
            this.seed = seed;
            this.startedAtMs = startedAtMs;
            this.rotationWindowMs = rotationWindowMs;
            this.supplyDurationMs = supplyDurationMs;
        }

        private Status status() {
            return new Status(sessionId, seed, startedAtMs, active, totalAgents, supplyDurationMs,
                    maps.values().stream().map(MapRun::status).toList());
        }
    }

    private static final class MapRun {
        private final AgentFieldObservationCatalog.MapPreset preset;
        private final List<MapleIslandCohortPoolSnapshot.Agent> pooled;
        private final List<AgentRuntimeEntry> roster = new ArrayList<>();
        private final Map<Integer, AgentFieldObservationFixtureService.Prepared> prepared = new LinkedHashMap<>();
        private final Map<Integer, Point> idleStaging = new LinkedHashMap<>();
        private final List<String> failures = new ArrayList<>();
        private final List<WindowSample> samples = new ArrayList<>();
        private int completedLaunches;
        private boolean initialized;
        private int phase;
        private long phaseStartedAtMs;
        private Set<Integer> activeAgentIds = Set.of();
        private ScheduledFuture<?> rotationTask;
        private int phaseActiveCount;

        private MapRun(AgentFieldObservationCatalog.MapPreset preset,
                       List<MapleIslandCohortPoolSnapshot.Agent> pooled) {
            this.preset = preset;
            this.pooled = pooled;
        }

        private boolean ready() {
            return initialized && !roster.isEmpty();
        }

        private Character anchor() {
            return roster.stream().map(AgentRuntimeIdentityRuntime::bot)
                    .filter(java.util.Objects::nonNull).findFirst().orElse(null);
        }

        private synchronized MapStatus status() {
            return new MapStatus(preset.mapId(), preset.mapName(), preset.level(), roster.size(), pooled.size(),
                    phase, activeAgentIds.size(), preset.activeCounts(), failures,
                    prepared.values().stream().map(value -> new PreparedAgent(
                            value.name(), value.level(), value.bundleId(), value.apProfileId(), value.spProfileId(),
                            value.equipmentItemIds(), value.projectileItemId())).toList(), samples,
                    capacityWindows(samples));
        }
    }
}
