package server.agents.capabilities.navigation;

import server.agents.capabilities.movement.AgentJumpProbeService;
import server.agents.capabilities.movement.AgentJumpLanding;
import server.agents.capabilities.movement.AgentWalkOffLanding;
import server.agents.capabilities.movement.AgentPostLandingJump;
import server.agents.capabilities.movement.AgentGroundCollisionService;
import server.agents.capabilities.movement.AgentGroundingService;
import server.agents.capabilities.movement.AgentMovementKinematicsService;
import server.agents.capabilities.movement.AgentWallCollisionPolicy;

import server.agents.capabilities.movement.AgentMovementProfile;
import server.agents.capabilities.movement.AgentMovementPhysicsConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.maps.Foothold;
import server.maps.MapleMap;
import server.maps.Portal;
import server.maps.Rope;
import server.agents.runtime.AgentBoundedExecutorFactory;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

public final class AgentNavigationGraphService {
    private static final Logger log = LoggerFactory.getLogger(AgentNavigationGraphService.class);

    private static final int GRAPH_VERSION = 51;
    private static final int ENDPOINT_ANCHOR_SPACING_PX = 10;
    private static final int DOWN_JUMP_PRELAUNCH_WINDOW_PX = 20;
    private static final int SAME_SOLID_NEST_GAP_PX = 8;
    private static final int ROPE_ANCHOR_INTERVAL_PX = 30;
    private static final int JUMP_POST_LANDING_STABILITY_TICKS = 3;
    private static final int MAX_PROFILED_JUMP_REGIONS = 5;
    private static final int FAST_WARMUP_MAX_FOOTHOLDS = 200;
    private static final long DEFAULT_GRAPH_CACHE_MAX_WEIGHT = 1_000_000L;
    private static final Path CACHE_DIR = Path.of("cache", "bot-nav", "v" + GRAPH_VERSION);
    private static final AgentWeightedLruCache<GraphCacheKey, AgentNavigationGraph> GRAPHS =
            new AgentWeightedLruCache<>(configuredGraphCacheMaxWeight(), AgentNavigationGraphService::graphWeight);
    private static final Map<GraphCacheKey, CompletableFuture<AgentNavigationGraph>> PENDING_GRAPHS = new ConcurrentHashMap<>();
    private static final Map<GraphCacheKey, GraphBuildReport> LAST_BUILD_REPORTS = new ConcurrentHashMap<>();
    private static final Map<Integer, Set<Integer>> COLLIDABLE_FROM_BELOW_IDS_BY_MAP_ID = new ConcurrentHashMap<>();
    private static final ThreadLocal<BuildProfileBuilder> ACTIVE_BUILD_PROFILE = new ThreadLocal<>();
    private static final Object WARMUP_LIFECYCLE_LOCK = new Object();
    private static volatile WarmupExecutors warmupExecutors = createWarmupExecutors();
    private static volatile boolean acceptingAsyncWarmups = true;

    private record WarmupExecutors(ExecutorService regular, ExecutorService fast) {
    }

    private record GraphCacheKey(int mapId, int totalSpeedStat, int totalJumpStat) {
        static GraphCacheKey from(int mapId, AgentMovementProfile profile) {
            AgentMovementProfile effective = profile == null ? AgentMovementProfile.base() : profile;
            return new GraphCacheKey(mapId, effective.totalSpeedStat(), effective.totalJumpStat());
        }
    }

    public static final class GraphBuildReport {
        public final long buildAnchorPointsNs;
        public final int mapId;
        public final int totalSpeedStat;
        public final int totalJumpStat;
        public final int footholdCount;
        public final int walkableFootholdCount;
        public final int ropeCount;
        public final int regionCount;
        public final int totalEdgeCount;
        public final int walkEdgeCount;
        public final int jumpEdgeCount;
        public final int dropEdgeCount;
        public final int climbEdgeCount;
        public final int portalEdgeCount;
        public final long collectFootholdsNs;
        public final long buildRegionsNs;
        public final long addRopeRegionsNs;
        public final long buildFeatureXsNs;
        public final long buildWalkEdgesNs;
        public final long buildDropEdgesNs;
        public final long buildJumpEdgesNs;
        public final long buildRopeEntryEdgesNs;
        public final long buildRopeExitEdgesNs;
        public final long buildPortalEdgesNs;
        public final long totalBuildNs;
        public final long jumpSampleCount;
        public final long jumpCacheHitCount;
        public final long jumpCacheMissCount;
        public final long jumpBoundaryRefineProbeCount;
        public final List<JumpRegionProfile> slowestJumpRegions;

        GraphBuildReport(int mapId,
                         int totalSpeedStat,
                         int totalJumpStat,
                         int footholdCount,
                         int walkableFootholdCount,
                         int ropeCount,
                         int regionCount,
                         int totalEdgeCount,
                         int walkEdgeCount,
                         int jumpEdgeCount,
                         int dropEdgeCount,
                         int climbEdgeCount,
                         int portalEdgeCount,
                         long buildAnchorPointsNs,
                         long collectFootholdsNs,
                         long buildRegionsNs,
                         long addRopeRegionsNs,
                         long buildFeatureXsNs,
                         long buildWalkEdgesNs,
                         long buildDropEdgesNs,
                         long buildJumpEdgesNs,
                         long buildRopeEntryEdgesNs,
                         long buildRopeExitEdgesNs,
                         long buildPortalEdgesNs,
                         long totalBuildNs,
                         long jumpSampleCount,
                         long jumpCacheHitCount,
                         long jumpCacheMissCount,
                         long jumpBoundaryRefineProbeCount,
                         List<JumpRegionProfile> slowestJumpRegions) {
            this.buildAnchorPointsNs = buildAnchorPointsNs;
            this.mapId = mapId;
            this.totalSpeedStat = totalSpeedStat;
            this.totalJumpStat = totalJumpStat;
            this.footholdCount = footholdCount;
            this.walkableFootholdCount = walkableFootholdCount;
            this.ropeCount = ropeCount;
            this.regionCount = regionCount;
            this.totalEdgeCount = totalEdgeCount;
            this.walkEdgeCount = walkEdgeCount;
            this.jumpEdgeCount = jumpEdgeCount;
            this.dropEdgeCount = dropEdgeCount;
            this.climbEdgeCount = climbEdgeCount;
            this.portalEdgeCount = portalEdgeCount;
            this.collectFootholdsNs = collectFootholdsNs;
            this.buildRegionsNs = buildRegionsNs;
            this.addRopeRegionsNs = addRopeRegionsNs;
            this.buildFeatureXsNs = buildFeatureXsNs;
            this.buildWalkEdgesNs = buildWalkEdgesNs;
            this.buildDropEdgesNs = buildDropEdgesNs;
            this.buildJumpEdgesNs = buildJumpEdgesNs;
            this.buildRopeEntryEdgesNs = buildRopeEntryEdgesNs;
            this.buildRopeExitEdgesNs = buildRopeExitEdgesNs;
            this.buildPortalEdgesNs = buildPortalEdgesNs;
            this.totalBuildNs = totalBuildNs;
            this.jumpSampleCount = jumpSampleCount;
            this.jumpCacheHitCount = jumpCacheHitCount;
            this.jumpCacheMissCount = jumpCacheMissCount;
            this.jumpBoundaryRefineProbeCount = jumpBoundaryRefineProbeCount;
            this.slowestJumpRegions = new ArrayList<>(slowestJumpRegions);
        }
    }

    public record JumpRegionProfile(int regionId,
                                    int width,
                                    int sampleCount,
                                    int edgeCount,
                                    int cacheHits,
                                    int cacheMisses,
                                    long elapsedNs) {
    }

    private static final class BuildProfileBuilder {
        private long buildAnchorPointsNs;
        private final int mapId;
        private final int totalSpeedStat;
        private final int totalJumpStat;
        private final long buildStartedAtNs = System.nanoTime();
        private int footholdCount;
        private int walkableFootholdCount;
        private int ropeCount;
        private int regionCount;
        private int totalEdgeCount;
        private int walkEdgeCount;
        private int jumpEdgeCount;
        private int dropEdgeCount;
        private int climbEdgeCount;
        private int portalEdgeCount;
        private long collectFootholdsNs;
        private long buildRegionsNs;
        private long addRopeRegionsNs;
        private long buildFeatureXsNs;
        private long buildWalkEdgesNs;
        private long buildDropEdgesNs;
        private long buildJumpEdgesNs;
        private long buildRopeEntryEdgesNs;
        private long buildRopeExitEdgesNs;
        private long buildPortalEdgesNs;
        private long jumpSampleCount;
        private long jumpCacheHitCount;
        private long jumpCacheMissCount;
        private long jumpBoundaryRefineProbeCount;
        private final List<JumpRegionProfile> slowestJumpRegions = new ArrayList<>();

        private BuildProfileBuilder(int mapId, AgentMovementProfile movementProfile) {
            this.mapId = mapId;
            this.totalSpeedStat = movementProfile.totalSpeedStat();
            this.totalJumpStat = movementProfile.totalJumpStat();
        }

        private void recordEdge(AgentNavigationGraph.EdgeType type) {
            totalEdgeCount++;
            switch (type) {
                case WALK -> walkEdgeCount++;
                case JUMP -> jumpEdgeCount++;
                case DROP -> dropEdgeCount++;
                case CLIMB -> climbEdgeCount++;
                case PORTAL -> portalEdgeCount++;
            }
        }

        private void recordJumpSample(boolean cacheHit) {
            jumpSampleCount++;
            if (cacheHit) {
                jumpCacheHitCount++;
            } else {
                jumpCacheMissCount++;
            }
        }

        private void recordJumpBoundaryRefineProbe() {
            jumpBoundaryRefineProbeCount++;
        }

        private void recordJumpRegion(JumpRegionProfile profile) {
            slowestJumpRegions.add(profile);
            slowestJumpRegions.sort(Comparator.comparingLong(JumpRegionProfile::elapsedNs).reversed());
            if (slowestJumpRegions.size() > MAX_PROFILED_JUMP_REGIONS) {
                slowestJumpRegions.removeLast();
            }
        }

        private GraphBuildReport finish() {
            return new GraphBuildReport(
                    mapId,
                    totalSpeedStat,
                    totalJumpStat,
                    footholdCount,
                    walkableFootholdCount,
                    ropeCount,
                    regionCount,
                    totalEdgeCount,
                    walkEdgeCount,
                    jumpEdgeCount,
                    dropEdgeCount,
                    climbEdgeCount,
                    portalEdgeCount,
                    buildAnchorPointsNs,
                    collectFootholdsNs,
                    buildRegionsNs,
                    addRopeRegionsNs,
                    buildFeatureXsNs,
                    buildWalkEdgesNs,
                    buildDropEdgesNs,
                    buildJumpEdgesNs,
                    buildRopeEntryEdgesNs,
                    buildRopeExitEdgesNs,
                    buildPortalEdgesNs,
                    System.nanoTime() - buildStartedAtNs,
                    jumpSampleCount,
                    jumpCacheHitCount,
                    jumpCacheMissCount,
                    jumpBoundaryRefineProbeCount,
                    slowestJumpRegions);
        }
    }

    private record JumpLaunchWindow(int minX, int maxX, Point startPoint, Point endPoint, int landingTimeMs) {
    }

    private static final class JumpBuildStats {
        int sampleCount;
        int edgeCount;
        int cacheHits;
        int cacheMisses;
    }

    private record JumpLandingKey(int x, int y, int launchStepX) {
    }

    private static final class JumpLandingCache {
        private final Map<JumpLandingKey, AgentPostLandingJump> hits = new HashMap<>();
        private final Set<JumpLandingKey> misses = new HashSet<>();
    }

    private record RopeGrabKey(int x, int y, int launchStepX, int ropeX, int ropeTopY, int ropeBottomY) {
    }

    private static final class RopeGrabCache {
        private final Map<RopeGrabKey, Point> hits = new HashMap<>();
        private final Set<RopeGrabKey> misses = new HashSet<>();
    }

    public static AgentNavigationGraph getGraph(MapleMap map) {
        return getGraph(map, AgentMovementProfile.base());
    }

    public static AgentNavigationGraph getGraph(MapleMap map, AgentMovementProfile movementProfile) {
        if (map == null) {
            return null;
        }
        GraphCacheKey key = GraphCacheKey.from(map.getId(), movementProfile);
        AgentNavigationGraph cached = GRAPHS.get(key);
        if (cached != null) {
            return cached;
        }
        return getOrStartGraphLoad(map, movementProfile, key, false).join();
    }

    /** Returns the cached graph without triggering a build. */
    public static AgentNavigationGraph peekGraph(MapleMap map) {
        if (map == null) {
            return null;
        }
        for (Map.Entry<GraphCacheKey, AgentNavigationGraph> entry : GRAPHS.snapshotEntries()) {
            if (entry.getKey().mapId() == map.getId()) {
                return entry.getValue();
            }
        }
        return null;
    }

    /** Returns the cached graph for the requested profile without triggering a build. */
    public static AgentNavigationGraph peekGraph(MapleMap map, AgentMovementProfile movementProfile) {
        if (map == null) {
            return null;
        }
        return GRAPHS.get(GraphCacheKey.from(map.getId(), movementProfile));
    }

    /** Returns the closest cached graph for this map when the exact profile graph is unavailable. */
    public static AgentNavigationGraph peekClosestGraph(MapleMap map, AgentMovementProfile movementProfile) {
        if (map == null) {
            return null;
        }

        GraphCacheKey requested = GraphCacheKey.from(map.getId(), movementProfile);
        AgentNavigationGraph bestGraph = null;
        int bestDistance = Integer.MAX_VALUE;
        GraphCacheKey bestKey = null;
        for (Map.Entry<GraphCacheKey, AgentNavigationGraph> entry : GRAPHS.snapshotEntries()) {
            GraphCacheKey key = entry.getKey();
            if (key.mapId() != requested.mapId()) {
                continue;
            }

            int distance = Math.abs(key.totalSpeedStat() - requested.totalSpeedStat())
                    + Math.abs(key.totalJumpStat() - requested.totalJumpStat());
            if (bestGraph == null || distance < bestDistance) {
                bestGraph = entry.getValue();
                bestKey = key;
                bestDistance = distance;
            }
        }
        return bestKey == null ? null : GRAPHS.get(bestKey);
    }

    /**
     * Best cached graph for the requested profile: the exact-profile graph if cached, otherwise the
     * closest cached profile for this map (by speed/jump distance), or {@code null} if none cached.
     * Single source of truth for "which cached graph should a bot of this profile navigate against",
     * replacing the bare {@link #peekGraph(MapleMap)} arbitrary-first-entry pick at profile-aware
     * call sites and the open-coded exact-then-closest fallback duplicated across callers.
     */
    public static AgentNavigationGraph peekBestGraph(MapleMap map, AgentMovementProfile movementProfile) {
        AgentNavigationGraph exact = peekGraph(map, movementProfile);
        return exact != null ? exact : peekClosestGraph(map, movementProfile);
    }

    public static void warmGraphAsync(MapleMap map, AgentMovementProfile movementProfile) {
        if (map == null || !acceptingAsyncWarmups) {
            return;
        }
        GraphCacheKey key = GraphCacheKey.from(map.getId(), movementProfile);
        if (GRAPHS.containsKey(key)) {
            return;
        }
        getOrStartGraphLoad(map, movementProfile, key, true);
    }

    public static void startAsyncWarmups() {
        synchronized (WARMUP_LIFECYCLE_LOCK) {
            if (warmupExecutors == null
                    || warmupExecutors.regular().isShutdown()
                    || warmupExecutors.fast().isShutdown()) {
                warmupExecutors = createWarmupExecutors();
            }
            acceptingAsyncWarmups = true;
        }
    }

    public static void shutdownAsyncWarmups() {
        WarmupExecutors executors;
        synchronized (WARMUP_LIFECYCLE_LOCK) {
            acceptingAsyncWarmups = false;
            executors = warmupExecutors;
            warmupExecutors = null;
            PENDING_GRAPHS.values().forEach(future -> future.cancel(true));
            PENDING_GRAPHS.clear();
        }
        if (executors == null) {
            return;
        }

        executors.regular().shutdownNow();
        executors.fast().shutdownNow();
        awaitWarmupTermination(executors.regular(), "regular");
        awaitWarmupTermination(executors.fast(), "fast");
    }

    public static AgentNavigationGraph rebuildGraph(MapleMap map) {
        return rebuildGraph(map, AgentMovementProfile.base());
    }

    public static AgentNavigationGraph rebuildGraph(MapleMap map, AgentMovementProfile movementProfile) {
        GraphCacheKey key = GraphCacheKey.from(map.getId(), movementProfile);
        AgentNavigationGraph rebuilt = buildGraph(map, movementProfile);
        cacheGraph(key, rebuilt);
        CompletableFuture<AgentNavigationGraph> pending = PENDING_GRAPHS.remove(key);
        if (pending != null) {
            pending.complete(rebuilt);
        }
        saveGraph(rebuilt);
        return rebuilt;
    }

    private static CompletableFuture<AgentNavigationGraph> getOrStartGraphLoad(MapleMap map,
                                                                             AgentMovementProfile movementProfile,
                                                                             GraphCacheKey key,
                                                                             boolean async) {
        AgentNavigationGraph cached = GRAPHS.get(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        CompletableFuture<AgentNavigationGraph> existing = PENDING_GRAPHS.get(key);
        if (existing != null) {
            return existing;
        }

        CompletableFuture<AgentNavigationGraph> future = new CompletableFuture<>();
        CompletableFuture<AgentNavigationGraph> race = PENDING_GRAPHS.putIfAbsent(key, future);
        if (race != null) {
            return race;
        }

        Runnable task = () -> {
            try {
                throwIfCancelled(async, future);
                AgentNavigationGraph graph = loadOrBuildGraph(map, movementProfile, key);
                throwIfCancelled(async, future);
                cacheGraph(key, graph);
                future.complete(graph);
            } catch (CancellationException cancelled) {
                future.cancel(false);
            } catch (Throwable t) {
                future.completeExceptionally(t);
                log.warn("Failed to warm bot nav graph for map {} speed={} jump={}",
                        key.mapId(), key.totalSpeedStat(), key.totalJumpStat(), t);
            } finally {
                PENDING_GRAPHS.remove(key, future);
            }
        };

        if (async) {
            try {
                selectWarmupExecutor(map).execute(task);
            } catch (RejectedExecutionException e) {
                PENDING_GRAPHS.remove(key, future);
                future.completeExceptionally(e);
                if (acceptingAsyncWarmups) {
                    log.warn("Skipped Agent navigation graph warmup for map {} because the queue is full", key.mapId());
                }
            }
        } else {
            task.run();
        }
        return future;
    }

    private static ExecutorService selectWarmupExecutor(MapleMap map) {
        WarmupExecutors executors = warmupExecutors;
        if (!acceptingAsyncWarmups || executors == null) {
            throw new RejectedExecutionException("Agent navigation graph warmups are stopping");
        }
        return isFastWarmupCandidate(map) ? executors.fast() : executors.regular();
    }

    public static int pendingWarmupCount() {
        return PENDING_GRAPHS.size();
    }

    static boolean asyncWarmupsRunning() {
        WarmupExecutors executors = warmupExecutors;
        return acceptingAsyncWarmups
                && executors != null
                && !executors.regular().isShutdown()
                && !executors.fast().isShutdown();
    }

    private static WarmupExecutors createWarmupExecutors() {
        return new WarmupExecutors(
                AgentBoundedExecutorFactory.fixed(
                        "navigation",
                        "bot-nav-graph-warmup",
                        1,
                        AgentBoundedExecutorFactory.positiveIntegerProperty(
                                "agents.async.navigation.queueCapacity", 64)),
                AgentBoundedExecutorFactory.fixed(
                        "navigation-fast",
                        "bot-nav-graph-warmup-fast",
                        1,
                        AgentBoundedExecutorFactory.positiveIntegerProperty(
                                "agents.async.navigation.fastQueueCapacity", 64)));
    }

    private static void awaitWarmupTermination(ExecutorService executor, String lane) {
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn("Agent navigation {} warmup worker did not stop within 10 seconds", lane);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while stopping Agent navigation {} warmup worker", lane);
        }
    }

    private static void throwIfCancelled(boolean cancellable,
                                         CompletableFuture<AgentNavigationGraph> future) {
        if (cancellable && (future.isCancelled() || Thread.currentThread().isInterrupted())) {
            throw new CancellationException("Agent navigation graph warmup cancelled");
        }
    }

    private static boolean isFastWarmupCandidate(MapleMap map) {
        if (map == null || map.getFootholds() == null) {
            return false;
        }
        return map.getFootholds().getAllFootholds().size() <= FAST_WARMUP_MAX_FOOTHOLDS;
    }

    private static AgentNavigationGraph loadOrBuildGraph(MapleMap map,
                                                       AgentMovementProfile movementProfile,
                                                       GraphCacheKey key) {
        AgentNavigationGraph cached = loadGraph(key);
        if (cached != null) {
            return cached;
        }

        AgentNavigationGraph built = buildGraph(map, movementProfile);
        saveGraph(built);
        return built;
    }

    private static AgentNavigationGraph loadGraph(GraphCacheKey key) {
        Path file = graphFile(key);
        try {
            AgentNavigationGraph graph = AgentNavigationGraphCacheFile.read(
                    file,
                    GRAPH_VERSION,
                    key.mapId(),
                    new AgentMovementProfile(key.totalSpeedStat(), key.totalJumpStat()));
            if (graph == null) {
                return null;
            }
            seedCachedFootholdCollisionIds(graph);
            return graph;
        } catch (IOException | ClassNotFoundException e) {
            log.debug("Failed to load bot nav graph cache for map {} speed={} jump={}",
                    key.mapId(), key.totalSpeedStat(), key.totalJumpStat(), e);
            return null;
        }
    }

    private static void saveGraph(AgentNavigationGraph graph) {
        try {
            AgentNavigationGraphCacheFile.write(
                    graphFile(GraphCacheKey.from(graph.mapId, graph.movementProfile)), graph);
        } catch (IOException e) {
            log.debug("Failed to save bot nav graph cache for map {}", graph.mapId, e);
        }
    }

    private static Path graphFile(GraphCacheKey key) {
        return CACHE_DIR.resolve(key.mapId() + "-s" + key.totalSpeedStat() + "-j" + key.totalJumpStat() + ".bin");
    }

    private static AgentNavigationGraph buildGraph(MapleMap map, AgentMovementProfile movementProfile) {
        throwIfBuildInterrupted();
        movementProfile = movementProfile == null ? AgentMovementProfile.base() : movementProfile;
        BuildProfileBuilder buildProfile = new BuildProfileBuilder(map.getId(), movementProfile);
        ACTIVE_BUILD_PROFILE.set(buildProfile);
        try {
            List<Foothold> footholds = map.getFootholds() == null ? List.of() : map.getFootholds().getAllFootholds();
            Map<Integer, Foothold> footholdsById = new HashMap<>();
            List<Foothold> walkableFootholds = new ArrayList<>();
            long phaseStartedAt = System.nanoTime();
            Set<Integer> collidableFromBelowIds;
            for (Foothold foothold : footholds) {
                footholdsById.put(foothold.getId(), foothold);
                if (!foothold.isWall()) {
                    walkableFootholds.add(foothold);
                }
            }
            collidableFromBelowIds = classifyCollidableFromBelowFootholds(footholdsById);
            buildProfile.collectFootholdsNs = System.nanoTime() - phaseStartedAt;
            buildProfile.footholdCount = footholds.size();
            buildProfile.walkableFootholdCount = walkableFootholds.size();
            buildProfile.ropeCount = map.getRopes().size();
            throwIfBuildInterrupted();
            COLLIDABLE_FROM_BELOW_IDS_BY_MAP_ID.put(map.getId(), new HashSet<>(collidableFromBelowIds));

            List<AgentNavigationGraph.Region> regions = new ArrayList<>();
            Map<Integer, AgentNavigationGraph.Region> regionsById = new HashMap<>();
            Map<Integer, Integer> regionIdByFootholdId = new HashMap<>();
            phaseStartedAt = System.nanoTime();
            buildRegions(walkableFootholds, footholdsById, collidableFromBelowIds, regions, regionsById, regionIdByFootholdId);
            buildProfile.buildRegionsNs = System.nanoTime() - phaseStartedAt;

            phaseStartedAt = System.nanoTime();
            int nextRegionId = regions.stream().mapToInt(r -> r.id).max().orElse(0) + 1;
            for (Rope rope : map.getRopes()) {
                AgentNavigationGraph.Region ropeRegion = new AgentNavigationGraph.Region(
                        nextRegionId++, rope.x(), rope.topY(), rope.bottomY(), rope.isLadder());
                regions.add(ropeRegion);
                regionsById.put(ropeRegion.id, ropeRegion);
            }
            buildProfile.addRopeRegionsNs = System.nanoTime() - phaseStartedAt;
            buildProfile.regionCount = regions.size();

            phaseStartedAt = System.nanoTime();
            Map<Integer, List<Integer>> featureXsByRegionId = buildFeatureXsByRegionId(map, regions, regionIdByFootholdId);
            buildProfile.buildFeatureXsNs = System.nanoTime() - phaseStartedAt;
            phaseStartedAt = System.nanoTime();
            Map<Integer, List<Point>> anchorsByRegionId = buildAnchorsByRegionId(map, regions, featureXsByRegionId, movementProfile);
            buildProfile.buildAnchorPointsNs = System.nanoTime() - phaseStartedAt;
            List<AgentNavigationGraph.Region> groundRegions = new ArrayList<>();
            List<AgentNavigationGraph.Region> ropeRegions = new ArrayList<>();
            for (AgentNavigationGraph.Region region : regions) {
                if (region.isRopeRegion) {
                    ropeRegions.add(region);
                } else {
                    groundRegions.add(region);
                }
            }
            Map<Integer, Rope> ropeByRegionId = buildRopeByRegionId(map, ropeRegions);
            Map<Integer, List<AgentNavigationGraph.Edge>> outgoing = new HashMap<>();
            Set<String> edgeKeys = new HashSet<>();
            JumpLandingCache jumpLandingCache = new JumpLandingCache();
            RopeGrabCache ropeGrabCache = new RopeGrabCache();

            AgentNavigationPhysicsService.setBuildWalkRegionLookup(map, regionsById, regionIdByFootholdId, footholdsById);

            phaseStartedAt = System.nanoTime();
            for (Foothold foothold : walkableFootholds) {
                throwIfBuildInterrupted();
                addWalkEdges(foothold, footholdsById, regionsById, regionIdByFootholdId, outgoing, edgeKeys, movementProfile);
            }
            buildProfile.buildWalkEdgesNs = System.nanoTime() - phaseStartedAt;

            phaseStartedAt = System.nanoTime();
            for (AgentNavigationGraph.Region region : groundRegions) {
                throwIfBuildInterrupted();
                addDropEdges(region, map, regionsById, regionIdByFootholdId,
                        anchorsByRegionId.getOrDefault(region.id, List.of()), outgoing, edgeKeys, movementProfile);
            }
            buildProfile.buildDropEdgesNs = System.nanoTime() - phaseStartedAt;

            phaseStartedAt = System.nanoTime();
            for (AgentNavigationGraph.Region region : groundRegions) {
                throwIfBuildInterrupted();
                addJumpEdges(region, map, regionsById, regionIdByFootholdId,
                        anchorsByRegionId.getOrDefault(region.id, List.of()), outgoing, edgeKeys, jumpLandingCache, movementProfile);
            }
            buildProfile.buildJumpEdgesNs = System.nanoTime() - phaseStartedAt;

            phaseStartedAt = System.nanoTime();
            for (AgentNavigationGraph.Region region : ropeRegions) {
                throwIfBuildInterrupted();
                addRopeEntryEdges(region, groundRegions, ropeByRegionId, map, anchorsByRegionId,
                        outgoing, edgeKeys, ropeGrabCache, movementProfile);
            }
            buildProfile.buildRopeEntryEdgesNs = System.nanoTime() - phaseStartedAt;

            phaseStartedAt = System.nanoTime();
            for (AgentNavigationGraph.Region region : ropeRegions) {
                throwIfBuildInterrupted();
                addRopeExitEdges(region, ropeRegions, ropeByRegionId, map, regionsById, regionIdByFootholdId, outgoing, edgeKeys, movementProfile);
            }
            buildProfile.buildRopeExitEdgesNs = System.nanoTime() - phaseStartedAt;

            phaseStartedAt = System.nanoTime();
            for (Portal portal : map.getPortals()) {
                throwIfBuildInterrupted();
                addPortalEdges(portal, map, regionsById, regionIdByFootholdId, outgoing, edgeKeys);
            }
            buildProfile.buildPortalEdgesNs = System.nanoTime() - phaseStartedAt;

            AgentNavigationGraph graph = new AgentNavigationGraph(
                    map.getId(), GRAPH_VERSION, movementProfile, regions, regionsById, regionIdByFootholdId, outgoing,
                    Set.of(), collidableFromBelowIds);
            GraphBuildReport report = buildProfile.finish();
            LAST_BUILD_REPORTS.put(GraphCacheKey.from(map.getId(), movementProfile), report);
            log.debug("Built bot nav graph map {} speed={} jump={} in {} ms (regions={}, edges={}, drop={} ms, jump={} ms, jumpSamples={}, cacheHits={})",
                    map.getId(),
                    movementProfile.totalSpeedStat(),
                    movementProfile.totalJumpStat(),
                    String.format("%.2f", report.totalBuildNs / 1_000_000.0),
                    report.regionCount,
                    report.totalEdgeCount,
                    String.format("%.2f", report.buildDropEdgesNs / 1_000_000.0),
                    String.format("%.2f", report.buildJumpEdgesNs / 1_000_000.0),
                    report.jumpSampleCount,
                    report.jumpCacheHitCount);
            return graph;
        } finally {
            AgentNavigationPhysicsService.clearBuildWalkRegionLookup();
            ACTIVE_BUILD_PROFILE.remove();
        }
    }

    private static void throwIfBuildInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Agent navigation graph build interrupted");
        }
    }

    private static void cacheGraph(GraphCacheKey key, AgentNavigationGraph graph) {
        List<GraphCacheKey> evicted = GRAPHS.put(key, graph);
        for (GraphCacheKey evictedKey : evicted) {
            LAST_BUILD_REPORTS.remove(evictedKey);
            if (!GRAPHS.anyKeyMatches(candidate -> candidate.mapId() == evictedKey.mapId())) {
                COLLIDABLE_FROM_BELOW_IDS_BY_MAP_ID.remove(evictedKey.mapId());
            }
        }
    }

    private static long graphWeight(AgentNavigationGraph graph) {
        long edgeCount = graph.outgoingByRegionId.values().stream().mapToLong(List::size).sum();
        return Math.max(1L, graph.regions.size() + edgeCount);
    }

    private static long configuredGraphCacheMaxWeight() {
        return Math.max(1L, Long.getLong(
                "agents.navigation.cache.maxWeight", DEFAULT_GRAPH_CACHE_MAX_WEIGHT));
    }

    static int cachedGraphCount() {
        return GRAPHS.size();
    }

    static long cachedGraphWeight() {
        return GRAPHS.currentWeight();
    }

    static long graphCacheEvictionCount() {
        return GRAPHS.evictionCount();
    }

    public static GraphBuildReport getLastBuildReport(int mapId) {
        return getLastBuildReport(mapId, AgentMovementProfile.base());
    }

    public static GraphBuildReport getLastBuildReport(int mapId, AgentMovementProfile movementProfile) {
        return LAST_BUILD_REPORTS.get(GraphCacheKey.from(mapId, movementProfile));
    }

    public static Set<Integer> getCachedCollidableFromBelowIds(int mapId) {
        return COLLIDABLE_FROM_BELOW_IDS_BY_MAP_ID.get(mapId);
    }

    public static Set<Integer> computeCollidableFromBelowIds(MapleMap map) {
        if (map == null || map.getFootholds() == null) {
            return Set.of();
        }
        Set<Integer> cached = COLLIDABLE_FROM_BELOW_IDS_BY_MAP_ID.get(map.getId());
        if (cached != null) {
            return cached;
        }
        Map<Integer, Foothold> footholdsById = new HashMap<>();
        for (Foothold foothold : map.getFootholds().getAllFootholds()) {
            footholdsById.put(foothold.getId(), foothold);
        }
        Set<Integer> computed = classifyCollidableFromBelowFootholds(footholdsById);
        COLLIDABLE_FROM_BELOW_IDS_BY_MAP_ID.put(map.getId(), new HashSet<>(computed));
        return computed;
    }

    private static void seedCachedFootholdCollisionIds(AgentNavigationGraph graph) {
        COLLIDABLE_FROM_BELOW_IDS_BY_MAP_ID.put(graph.mapId, new HashSet<>(graph.collidableFromBelowIds));
    }

    private static Set<Integer> classifyCollidableFromBelowFootholds(Map<Integer, Foothold> footholdsById) {
        List<ClassifiedLoop> loops = classifyClosedLoops(buildClosedLoops(footholdsById));
        if (loops.isEmpty()) {
            return Set.of();
        }

        Set<Integer> result = new HashSet<>();
        for (ClassifiedLoop classifiedLoop : loops) {
            if (!classifiedLoop.solid()) {
                continue;
            }

            ClosedLoop loop = classifiedLoop.loop();
            for (int footholdId : loop.footholdIds()) {
                Foothold foothold = footholdsById.get(footholdId);
                if (foothold == null || foothold.isWall() || foothold.getY1() != foothold.getY2()) {
                    continue;
                }

                double midX = (foothold.getX1() + foothold.getX2()) / 2.0;
                double y = foothold.getY1();
                boolean insideAbove = isPointInLoop(loop, midX, y - 1.0);
                boolean insideBelow = isPointInLoop(loop, midX, y + 1.0);
                if (insideAbove && !insideBelow) {
                    result.add(foothold.getId());
                }
            }
        }
        return result;
    }

    private record ClosedLoop(int[] footholdIds, double[] xs, double[] ys, double minX, double maxX, double minY, double maxY) {}

    private record ClassifiedLoop(ClosedLoop loop, int depth, boolean solid) {}

    private static List<ClosedLoop> buildClosedLoops(Map<Integer, Foothold> footholdsById) {
        List<ClosedLoop> loops = new ArrayList<>();
        Set<Integer> visited = new HashSet<>();
        for (Foothold start : footholdsById.values()) {
            if (visited.contains(start.getId())) {
                continue;
            }

            List<Foothold> chain = new ArrayList<>();
            Set<Integer> seenInChain = new HashSet<>();
            Foothold current = start;
            boolean closed = false;
            while (current != null && seenInChain.add(current.getId()) && chain.size() <= footholdsById.size()) {
                chain.add(current);
                Foothold next = footholdsById.get(current.getNext());
                if (next != null && next.getId() == start.getId()) {
                    closed = true;
                    break;
                }
                current = next;
            }

            if (!closed || chain.size() < 3 || !isEndpointConnectedLoop(chain)) {
                continue;
            }

            visited.addAll(seenInChain);
            loops.add(toClosedLoop(chain));
        }
        return loops;
    }

    private static boolean isEndpointConnectedLoop(List<Foothold> chain) {
        for (int i = 0; i < chain.size(); i++) {
            Foothold current = chain.get(i);
            Foothold next = chain.get((i + 1) % chain.size());
            if (current.getX2() != next.getX1() || current.getY2() != next.getY1()) {
                return false;
            }
        }
        return true;
    }

    private static ClosedLoop toClosedLoop(List<Foothold> chain) {
        int size = chain.size();
        int[] footholdIds = new int[size];
        double[] xs = new double[size];
        double[] ys = new double[size];
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < size; i++) {
            Foothold foothold = chain.get(i);
            footholdIds[i] = foothold.getId();
            xs[i] = foothold.getX1();
            ys[i] = foothold.getY1();
            minX = Math.min(minX, Math.min(foothold.getX1(), foothold.getX2()));
            maxX = Math.max(maxX, Math.max(foothold.getX1(), foothold.getX2()));
            minY = Math.min(minY, Math.min(foothold.getY1(), foothold.getY2()));
            maxY = Math.max(maxY, Math.max(foothold.getY1(), foothold.getY2()));
        }
        return new ClosedLoop(footholdIds, xs, ys, minX, maxX, minY, maxY);
    }

    private static List<ClassifiedLoop> classifyClosedLoops(List<ClosedLoop> loops) {
        List<ClosedLoop> ordered = new ArrayList<>(loops);
        ordered.sort(Comparator.comparingDouble(AgentNavigationGraphService::loopBoundingArea).reversed());

        List<ClassifiedLoop> classified = new ArrayList<>(ordered.size());
        for (ClosedLoop loop : ordered) {
            ClassifiedLoop parent = null;
            double parentArea = Double.POSITIVE_INFINITY;
            for (ClassifiedLoop candidate : classified) {
                if (loopContainsLoop(candidate.loop(), loop)) {
                    double area = loopBoundingArea(candidate.loop());
                    if (area < parentArea) {
                        parent = candidate;
                        parentArea = area;
                    }
                }
            }

            int depth = parent == null ? 0 : parent.depth() + 1;
            boolean solid = parent == null
                    || isThinNestedShell(parent.loop(), loop)
                    ? parent == null || parent.solid()
                    : !parent.solid();
            ClassifiedLoop classifiedLoop = new ClassifiedLoop(loop, depth, solid);
            classified.add(classifiedLoop);
        }
        return classified;
    }

    private static double loopBoundingArea(ClosedLoop loop) {
        return (loop.maxX() - loop.minX()) * (loop.maxY() - loop.minY());
    }

    private static boolean loopContainsLoop(ClosedLoop outer, ClosedLoop inner) {
        if (outer.minX() > inner.minX() || outer.maxX() < inner.maxX()
                || outer.minY() > inner.minY() || outer.maxY() < inner.maxY()) {
            return false;
        }
        return isPointInLoop(outer, inner.xs()[0], inner.ys()[0]);
    }

    private static boolean isThinNestedShell(ClosedLoop outer, ClosedLoop inner) {
        return inner.minX() - outer.minX() <= SAME_SOLID_NEST_GAP_PX
                && outer.maxX() - inner.maxX() <= SAME_SOLID_NEST_GAP_PX
                && inner.minY() - outer.minY() <= SAME_SOLID_NEST_GAP_PX
                && outer.maxY() - inner.maxY() <= SAME_SOLID_NEST_GAP_PX;
    }

    private static boolean isPointInLoop(ClosedLoop loop, double x, double y) {
        boolean inside = false;
        double[] xs = loop.xs();
        double[] ys = loop.ys();
        for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
            boolean intersects = ((ys[i] > y) != (ys[j] > y))
                    && (x < (xs[j] - xs[i]) * (y - ys[i]) / ((ys[j] - ys[i]) + 0.0) + xs[i]);
            if (intersects) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static void buildRegions(List<Foothold> footholds,
                                     Map<Integer, Foothold> footholdsById,
                                     Set<Integer> collidableFromBelowIds,
                                     List<AgentNavigationGraph.Region> regions,
                                     Map<Integer, AgentNavigationGraph.Region> regionsById,
                                     Map<Integer, Integer> regionIdByFootholdId) {
        UnionFind unionFind = new UnionFind();
        for (Foothold foothold : footholds) {
            unionFind.add(foothold.getId());
        }

        for (Foothold foothold : footholds) {
            unionWalkableFootholds(unionFind, foothold, footholdsById.get(foothold.getPrev()));
            unionWalkableFootholds(unionFind, foothold, footholdsById.get(foothold.getNext()));
        }

        Map<Integer, List<Foothold>> groupedFootholds = new HashMap<>();
        for (Foothold foothold : footholds) {
            groupedFootholds.computeIfAbsent(unionFind.find(foothold.getId()), ignored -> new ArrayList<>()).add(foothold);
        }

        List<List<Foothold>> groups = new ArrayList<>(groupedFootholds.values());
        groups.sort(Comparator
                .comparingInt(AgentNavigationGraphService::groupMinY)
                .thenComparingInt(AgentNavigationGraphService::groupMinX));

        int nextRegionId = 1;
        for (List<Foothold> group : groups) {
            group.sort(Comparator
                    .comparingInt(AgentNavigationGraphService::footholdMinX)
                    .thenComparingInt(foothold -> Math.min(foothold.getY1(), foothold.getY2()))
                    .thenComparingInt(Foothold::getId));

            List<AgentNavigationGraph.Segment> segments = new ArrayList<>(group.size());
            for (Foothold foothold : group) {
                segments.add(new AgentNavigationGraph.Segment(
                        foothold,
                        collidableFromBelowIds.contains(foothold.getId())));
            }

            AgentNavigationGraph.Region region = new AgentNavigationGraph.Region(nextRegionId++, segments);
            regions.add(region);
            regionsById.put(region.id, region);
            for (Foothold foothold : group) {
                regionIdByFootholdId.put(foothold.getId(), region.id);
            }
        }
    }

    private static void unionWalkableFootholds(UnionFind unionFind, Foothold first, Foothold second) {
        if (!AgentNavigationPhysicsService.canWalkAcrossFootholds(first, second)) {
            return;
        }
        unionFind.union(first.getId(), second.getId());
    }

    private static void addWalkEdges(Foothold foothold,
                                     Map<Integer, Foothold> footholdsById,
                                     Map<Integer, AgentNavigationGraph.Region> regionsById,
                                     Map<Integer, Integer> regionIdByFootholdId,
                                     Map<Integer, List<AgentNavigationGraph.Edge>> outgoing,
                                     Set<String> edgeKeys,
                                     AgentMovementProfile movementProfile) {
        addWalkEdge(foothold, footholdsById.get(foothold.getPrev()), regionsById, regionIdByFootholdId, outgoing, edgeKeys, movementProfile);
        addWalkEdge(foothold, footholdsById.get(foothold.getNext()), regionsById, regionIdByFootholdId, outgoing, edgeKeys, movementProfile);
    }

    private static void addWalkEdge(Foothold fromFoothold,
                                    Foothold targetFoothold,
                                    Map<Integer, AgentNavigationGraph.Region> regionsById,
                                    Map<Integer, Integer> regionIdByFootholdId,
                                    Map<Integer, List<AgentNavigationGraph.Edge>> outgoing,
                                    Set<String> edgeKeys,
                                    AgentMovementProfile movementProfile) {
        if (fromFoothold == null || targetFoothold == null || targetFoothold.isWall()) {
            return;
        }

        int fromRegionId = regionIdByFootholdId.getOrDefault(fromFoothold.getId(), -1);
        int toRegionId = regionIdByFootholdId.getOrDefault(targetFoothold.getId(), -1);
        if (fromRegionId < 0 || toRegionId < 0 || fromRegionId == toRegionId) {
            return;
        }

        EndpointConnection connection = closestEndpointConnection(fromFoothold, targetFoothold);
        if (connection == null || !isWalkConnection(connection)) {
            return;
        }

        AgentNavigationGraph.Region from = regionsById.get(fromRegionId);
        AgentNavigationGraph.Region to = regionsById.get(toRegionId);
        if (from == null || to == null) {
            return;
        }

        Point start = from.pointAt(connection.from.x);
        Point end = to.pointAt(connection.to.x);
        int cost = estimateWalkCost(start, end, movementProfile);
        addEdge(from.id, to.id, AgentNavigationGraph.EdgeType.WALK, start, end, 0, 0, cost, outgoing, edgeKeys);
        addEdge(to.id, from.id, AgentNavigationGraph.EdgeType.WALK, end, start, 0, 0, cost, outgoing, edgeKeys);
    }

    private static void addDropEdges(AgentNavigationGraph.Region from,
                                     MapleMap map,
                                     Map<Integer, AgentNavigationGraph.Region> regionsById,
                                     Map<Integer, Integer> regionIdByFootholdId,
                                     List<Point> anchors,
                                     Map<Integer, List<AgentNavigationGraph.Edge>> outgoing,
                                     Set<String> edgeKeys,
                                     AgentMovementProfile movementProfile) {
        addDirectionalDropEdge(from, map, regionsById, regionIdByFootholdId, -1, outgoing, edgeKeys, movementProfile);
        addDirectionalDropEdge(from, map, regionsById, regionIdByFootholdId, 1, outgoing, edgeKeys, movementProfile);

        for (Point anchor : anchors) {
            if (dropLaunchStep(from, map, anchor, movementProfile) != 0) {
                continue;
            }

            JumpLaunchWindow launchWindow = expandDownJumpLaunchWindow(
                    from, map, regionIdByFootholdId, anchor.x, movementProfile);
            if (launchWindow == null) {
                continue;
            }

            int toRegionId = findRegionIdBelow(map, regionIdByFootholdId, launchWindow.endPoint());
            AgentNavigationGraph.Region below = regionsById.get(toRegionId);
            if (below == null || below.id == from.id) {
                continue;
            }

            addEdge(from.id, below.id, AgentNavigationGraph.EdgeType.DROP,
                    launchWindow.startPoint(), launchWindow.endPoint(),
                    launchWindow.minX(), launchWindow.maxX(),
                    0, 0, launchWindow.landingTimeMs(), outgoing, edgeKeys);
        }
    }

    private static void addDirectionalDropEdge(AgentNavigationGraph.Region from,
                                               MapleMap map,
                                               Map<Integer, AgentNavigationGraph.Region> regionsById,
                                               Map<Integer, Integer> regionIdByFootholdId,
                                               int direction,
                                               Map<Integer, List<AgentNavigationGraph.Edge>> outgoing,
                                               Set<String> edgeKeys,
                                               AgentMovementProfile movementProfile) {
        if (direction == 0) {
            return;
        }
        Point endpoint = direction < 0 ? from.leftPoint() : from.rightPoint();
        if (from.isForbidFallDownAt(endpoint.x)) {
            return;
        }

        // O(1) runway: place the edge startPoint so the bot has room to accelerate
        // to (near) max walk speed before walking off. No candidate iteration, no
        // runway simulation — just constants from config.
        int runwayPx = AgentMovementKinematicsService.launchRunwayPx(map, movementProfile);
        int launchX;
        if (direction < 0) {
            launchX = Math.min(from.maxX, endpoint.x + runwayPx);
        } else {
            launchX = Math.max(from.minX, endpoint.x - runwayPx);
        }
        // Skip if region too small to fit a meaningful runway — no directional drop here.
        int actualRunway = Math.abs(launchX - endpoint.x);
        if (actualRunway < Math.min(runwayPx, 20)) {
            return;
        }
        Point startPoint = from.pointAt(launchX);
        if (AgentGroundCollisionService.isGroundRunwayBlockedByWall(map, startPoint, endpoint)) {
            return;
        }

        // Ballistic fall from ledge at max walk velocity — single simulation call.
        int stepX = AgentMovementKinematicsService.walkStep(map, movementProfile) * direction;
        AgentWalkOffLanding walkOff = AgentJumpProbeService.simulateWalkOffLanding(
                map, startPoint, direction, movementProfile);
        if (walkOff == null || walkOff.landing() == null || walkOff.landing().foothold() == null) {
            return;
        }
        AgentJumpLanding landing = walkOff.landing();

        int toRegionId = regionIdByFootholdId.getOrDefault(landing.foothold().getId(), -1);
        AgentNavigationGraph.Region below = regionsById.get(toRegionId);
        if (below == null || below.id == from.id) {
            return;
        }
        if (landing.point().y <= walkOff.launchPoint().y + 4) {
            return;
        }

        // Keep the established route weight while authoring the endpoint from the
        // execution-accurate walk-off simulation. Sub-tick acceleration detail must
        // not make A* abandon a previously preferred, valid local drop route.
        int travelMs = AgentJumpProbeService.estimateFallLandingTimeMs(map, endpoint, stepX)
                + estimateHorizontalTravelTimeMs(actualRunway, movementProfile);

        addEdge(from.id, below.id, AgentNavigationGraph.EdgeType.DROP,
                startPoint,
                landing.point(),
                stepX,
                0,
                travelMs,
                outgoing,
                edgeKeys);
    }

    private static void addJumpEdges(AgentNavigationGraph.Region from,
                                     MapleMap map,
                                     Map<Integer, AgentNavigationGraph.Region> regionsById,
                                     Map<Integer, Integer> regionIdByFootholdId,
                                     List<Point> anchors,
                                     Map<Integer, List<AgentNavigationGraph.Edge>> outgoing,
                                     Set<String> edgeKeys,
                                     JumpLandingCache jumpLandingCache,
                                     AgentMovementProfile movementProfile) {
        long startedAt = System.nanoTime();
        int jumpStep = AgentMovementKinematicsService.walkStep(map, movementProfile);
        JumpBuildStats stats = new JumpBuildStats();
        for (Point anchor : anchors) {
            throwIfBuildInterrupted();
            for (int launchStepX : new int[]{-jumpStep, 0, jumpStep}) {
                AgentPostLandingJump simulatedLanding = simulateJumpLandingCached(
                        map, anchor, launchStepX, jumpLandingCache, stats, movementProfile);
                if (simulatedLanding == null || simulatedLanding.lostGround()) {
                    continue;
                }
                AgentJumpLanding landing = simulatedLanding.landing();

                int toRegionId = regionIdByFootholdId.getOrDefault(simulatedLanding.finalFoothold().getId(), -1);
                AgentNavigationGraph.Region to = regionsById.get(toRegionId);
                if (to == null || to.id == from.id) {
                    continue;
                }
                if (Math.abs(landing.point().x - anchor.x) < 6 && Math.abs(landing.point().y - anchor.y) < 6) {
                    continue;
                }

                JumpLaunchWindow launchWindow = expandJumpLaunchWindow(from, map, regionIdByFootholdId,
                        anchor.x, launchStepX, to.id, stats, jumpLandingCache, movementProfile);
                if (launchWindow == null) {
                    continue;
                }
                if (isPhantomSharedGroundLanding(from, launchWindow.endPoint())) {
                    continue;
                }

                addEdge(from.id, to.id, AgentNavigationGraph.EdgeType.JUMP,
                        launchWindow.startPoint(), launchWindow.endPoint(),
                        launchWindow.minX(), launchWindow.maxX(),
                        launchStepX, 0, launchWindow.landingTimeMs(),
                        outgoing, edgeKeys);
                stats.edgeCount++;
            }
        }

        BuildProfileBuilder profile = ACTIVE_BUILD_PROFILE.get();
        if (profile != null) {
            profile.recordJumpRegion(new JumpRegionProfile(
                    from.id,
                    from.width(),
                    stats.sampleCount,
                    stats.edgeCount,
                    stats.cacheHits,
                    stats.cacheMisses,
                    System.nanoTime() - startedAt));
        }
    }

    static boolean isPhantomSharedGroundLanding(AgentNavigationGraph.Region source, Point landing) {
        return source != null
                && landing != null
                && source.surfaceCoversPoint(
                landing.x, landing.y, AgentNavigationGraph.SHARED_GROUND_Y_PX);
    }

    private static AgentPostLandingJump simulateJumpLandingCached(MapleMap map,
                                                                  Point start,
                                                                  int launchStepX,
                                                                  JumpLandingCache jumpLandingCache,
                                                                  JumpBuildStats stats,
                                                                  AgentMovementProfile movementProfile) {
        JumpLandingKey key = new JumpLandingKey(start.x, start.y, launchStepX);
        AgentPostLandingJump cachedLanding = jumpLandingCache.hits.get(key);
        if (cachedLanding != null) {
            recordJumpSample(stats, true);
            return cachedLanding;
        }
        if (jumpLandingCache.misses.contains(key)) {
            recordJumpSample(stats, true);
            return null;
        }

        AgentPostLandingJump landing = AgentJumpProbeService.simulateJumpLandingWithPostLandingTicks(
                map, start, launchStepX, movementProfile, JUMP_POST_LANDING_STABILITY_TICKS);
        if (landing == null) {
            jumpLandingCache.misses.add(key);
        } else {
            jumpLandingCache.hits.put(key, landing);
        }
        recordJumpSample(stats, false);
        return landing;
    }

    private static Point simulateGroundJumpRopeGrabCached(MapleMap map,
                                                          Point start,
                                                          int launchStepX,
                                                          Rope rope,
                                                          RopeGrabCache ropeGrabCache,
                                                          JumpBuildStats stats,
                                                          AgentMovementProfile movementProfile) {
        RopeGrabKey key = new RopeGrabKey(start.x, start.y, launchStepX,
                rope.x(), rope.topY(), rope.bottomY());
        Point cachedGrab = ropeGrabCache.hits.get(key);
        if (cachedGrab != null) {
            recordJumpSample(stats, true);
            return new Point(cachedGrab);
        }
        if (ropeGrabCache.misses.contains(key)) {
            recordJumpSample(stats, true);
            return null;
        }

        Point grab = AgentJumpProbeService.simulateGroundJumpRopeGrab(map, start, launchStepX, rope, movementProfile);
        if (grab == null) {
            ropeGrabCache.misses.add(key);
        } else {
            ropeGrabCache.hits.put(key, new Point(grab));
        }
        recordJumpSample(stats, false);
        return grab;
    }

    private static void recordJumpSample(JumpBuildStats stats, boolean cacheHit) {
        BuildProfileBuilder profile = ACTIVE_BUILD_PROFILE.get();
        if (profile != null) {
            profile.recordJumpSample(cacheHit);
        }
        stats.sampleCount++;
        if (cacheHit) {
            stats.cacheHits++;
        } else {
            stats.cacheMisses++;
        }
    }

    private static Map<Integer, List<Point>> buildAnchorsByRegionId(MapleMap map,
                                                                    List<AgentNavigationGraph.Region> regions,
                                                                    Map<Integer, List<Integer>> featureXsByRegionId,
                                                                    AgentMovementProfile movementProfile) {
        Map<Integer, List<Point>> anchorsByRegionId = new HashMap<>();
        for (AgentNavigationGraph.Region region : regions) {
            if (region.isRopeRegion) {
                continue;
            }
            anchorsByRegionId.put(region.id, anchorPoints(map, region, featureXsByRegionId.getOrDefault(region.id, List.of()), movementProfile));
        }
        return anchorsByRegionId;
    }

    private static JumpLaunchWindow expandJumpLaunchWindow(AgentNavigationGraph.Region from,
                                                           MapleMap map,
                                                           Map<Integer, Integer> regionIdByFootholdId,
                                                           int anchorX,
                                                           int launchStepX,
                                                           int targetRegionId,
                                                           JumpBuildStats stats,
                                                           JumpLandingCache jumpLandingCache,
                                                           AgentMovementProfile movementProfile) {
        if (!isValidJumpLaunchX(from, map, regionIdByFootholdId, anchorX, launchStepX, targetRegionId,
                stats, jumpLandingCache, movementProfile)) {
            return null;
        }

        int minX = findJumpBoundary(from, map, regionIdByFootholdId, anchorX, launchStepX, targetRegionId,
                true, stats, jumpLandingCache, movementProfile);
        int maxX = findJumpBoundary(from, map, regionIdByFootholdId, anchorX, launchStepX, targetRegionId,
                false, stats, jumpLandingCache, movementProfile);

        int representativeX = (minX + maxX) / 2;
        Point representativeStart = from.pointAt(representativeX);
        AgentPostLandingJump representativeSimulation =
                simulateJumpLandingCached(map, representativeStart, launchStepX, jumpLandingCache, stats, movementProfile);
        if (representativeSimulation == null || representativeSimulation.lostGround()) {
            return null;
        }

        int landingRegionId = regionIdByFootholdId.getOrDefault(representativeSimulation.finalFoothold().getId(), -1);
        if (landingRegionId != targetRegionId) {
            return null;
        }

        return new JumpLaunchWindow(minX, maxX, representativeStart, representativeSimulation.landing().point(),
                representativeSimulation.landing().timeMs());
    }

    private static JumpLaunchWindow expandDownJumpLaunchWindow(AgentNavigationGraph.Region from,
                                                               MapleMap map,
                                                               Map<Integer, Integer> regionIdByFootholdId,
                                                               int anchorX,
                                                               AgentMovementProfile movementProfile) {
        AgentJumpLanding anchorLanding = validateDownJumpLaunchX(
                from, map, regionIdByFootholdId, anchorX, movementProfile);
        if (anchorLanding == null) {
            return null;
        }

        int targetRegionId = regionIdByFootholdId.getOrDefault(anchorLanding.foothold().getId(), -1);
        if (targetRegionId < 0) {
            return null;
        }

        int minX = findDownJumpBoundary(from, map, regionIdByFootholdId, anchorX, targetRegionId, true, movementProfile);
        int maxX = findDownJumpBoundary(from, map, regionIdByFootholdId, anchorX, targetRegionId, false, movementProfile);

        int representativeX = (minX + maxX) / 2;
        Point representativeStart = from.pointAt(representativeX);
        AgentJumpLanding representativeLanding = validateDownJumpLaunchX(
                from, map, regionIdByFootholdId, representativeX, movementProfile, targetRegionId);
        if (representativeLanding == null) {
            return null;
        }

        return new JumpLaunchWindow(minX, maxX, representativeStart,
                representativeLanding.point(), representativeLanding.timeMs());
    }

    private static int findJumpBoundary(AgentNavigationGraph.Region from,
                                        MapleMap map,
                                        Map<Integer, Integer> regionIdByFootholdId,
                                        int startX,
                                        int launchStepX,
                                        int targetRegionId,
                                        boolean searchLeft,
                                        JumpBuildStats stats,
                                        JumpLandingCache jumpLandingCache,
                                        AgentMovementProfile movementProfile) {
        int limitX = searchLeft ? from.minX : from.maxX;
        int validX = startX;
        int invalidX = startX;
        int step = 1;

        while (true) {
            int probeX = searchLeft
                    ? Math.max(limitX, startX - step)
                    : Math.min(limitX, startX + step);
            if (probeX == validX) {
                break;
            }

            if (!isValidJumpLaunchX(from, map, regionIdByFootholdId, probeX,
                    launchStepX, targetRegionId, stats, jumpLandingCache, movementProfile)) {
                invalidX = probeX;
                break;
            }

            validX = probeX;
            if (probeX == limitX) {
                return probeX;
            }
            step *= 2;
        }

        while (Math.abs(validX - invalidX) > 1) {
            int probeX = (validX + invalidX) / 2;
            if (isValidJumpLaunchX(from, map, regionIdByFootholdId, probeX,
                    launchStepX, targetRegionId, stats, jumpLandingCache, movementProfile)) {
                validX = probeX;
            } else {
                invalidX = probeX;
            }
        }
        return validX;
    }

    private static int findDownJumpBoundary(AgentNavigationGraph.Region from,
                                            MapleMap map,
                                            Map<Integer, Integer> regionIdByFootholdId,
                                            int startX,
                                            int targetRegionId,
                                            boolean searchLeft,
                                            AgentMovementProfile movementProfile) {
        int limitX = searchLeft
                ? Math.max(from.minX, startX - DOWN_JUMP_PRELAUNCH_WINDOW_PX)
                : Math.min(from.maxX, startX + DOWN_JUMP_PRELAUNCH_WINDOW_PX);
        int validX = startX;
        int invalidX = startX;
        int step = 1;

        while (true) {
            int probeX = searchLeft
                    ? Math.max(limitX, startX - step)
                    : Math.min(limitX, startX + step);
            if (probeX == validX) {
                break;
            }

            if (!isValidDownJumpLaunchX(from, map, regionIdByFootholdId, probeX, movementProfile, targetRegionId)) {
                invalidX = probeX;
                break;
            }

            validX = probeX;
            if (probeX == limitX) {
                return probeX;
            }
            step *= 2;
        }

        while (Math.abs(validX - invalidX) > 1) {
            int probeX = (validX + invalidX) / 2;
            if (isValidDownJumpLaunchX(from, map, regionIdByFootholdId, probeX, movementProfile, targetRegionId)) {
                validX = probeX;
            } else {
                invalidX = probeX;
            }
        }
        return validX;
    }

    private static boolean isValidJumpLaunchX(AgentNavigationGraph.Region from,
                                              MapleMap map,
                                              Map<Integer, Integer> regionIdByFootholdId,
                                              int launchX,
                                              int launchStepX,
                                              int targetRegionId,
                                              JumpBuildStats stats,
                                              JumpLandingCache jumpLandingCache,
                                              AgentMovementProfile movementProfile) {
        if (!isApproachableJumpLaunchX(from, map, launchX)) {
            return false;
        }
        AgentPostLandingJump landing = simulateJumpLandingCached(
                map, from.pointAt(launchX), launchStepX, jumpLandingCache, stats, movementProfile);
        return landing != null
                && !landing.lostGround()
                && regionIdByFootholdId.getOrDefault(landing.finalFoothold().getId(), -1) == targetRegionId;
    }

    private static AgentJumpLanding validateDownJumpLaunchX(AgentNavigationGraph.Region from,
                                                            MapleMap map,
                                                            Map<Integer, Integer> regionIdByFootholdId,
                                                            int launchX,
                                                            AgentMovementProfile movementProfile) {
        return validateDownJumpLaunchX(from, map, regionIdByFootholdId, launchX, movementProfile, Integer.MIN_VALUE);
    }

    private static boolean isValidDownJumpLaunchX(AgentNavigationGraph.Region from,
                                                  MapleMap map,
                                                  Map<Integer, Integer> regionIdByFootholdId,
                                                  int launchX,
                                                  AgentMovementProfile movementProfile,
                                                  int targetRegionId) {
        return validateDownJumpLaunchX(from, map, regionIdByFootholdId, launchX, movementProfile, targetRegionId) != null;
    }

    private static AgentJumpLanding validateDownJumpLaunchX(AgentNavigationGraph.Region from,
                                                            MapleMap map,
                                                            Map<Integer, Integer> regionIdByFootholdId,
                                                            int launchX,
                                                            AgentMovementProfile movementProfile,
                                                            int requiredTargetRegionId) {
        if (from == null || from.isRopeRegion || map == null) {
            return null;
        }
        Point launchPoint = from.pointAt(launchX);
        if (isBlockedWallBoundaryLaunch(from, map, launchPoint)) {
            return null;
        }
        if (!AgentGroundCollisionService.canStartDownJump(map, launchPoint)
                || from.isForbidFallDownAt(launchX)
                || dropLaunchStep(from, map, launchPoint, movementProfile) != 0) {
            return null;
        }

        AgentJumpLanding landing = AgentJumpProbeService.simulateDownJumpLanding(map, launchPoint);
        if (landing == null || landing.point().y <= launchPoint.y + 4) {
            return null;
        }

        int landingRegionId = regionIdByFootholdId.getOrDefault(landing.foothold().getId(), -1);
        if (landingRegionId < 0 || landingRegionId == from.id) {
            return null;
        }
        if (requiredTargetRegionId != Integer.MIN_VALUE && landingRegionId != requiredTargetRegionId) {
            return null;
        }
        return landing;
    }

    private static boolean isApproachableJumpLaunchX(AgentNavigationGraph.Region from, MapleMap map, int launchX) {
        if (from == null || from.isRopeRegion || map == null) {
            return false;
        }
        Point launchPoint = from.pointAt(launchX);
        if (isBlockedWallBoundaryLaunch(from, map, launchPoint)) {
            return false;
        }
        if (launchX > from.minX && canWalkToLaunchX(from, map, launchX - 1, launchX)) {
            return true;
        }
        return launchX < from.maxX && canWalkToLaunchX(from, map, launchX + 1, launchX);
    }

    private static JumpLaunchWindow expandRopeGrabLaunchWindow(AgentNavigationGraph.Region from,
                                                               MapleMap map,
                                                               int anchorX,
                                                               int launchStepX,
                                                               Rope rope,
                                                               JumpBuildStats stats,
                                                               RopeGrabCache ropeGrabCache,
                                                               AgentMovementProfile movementProfile) {
        if (!isValidRopeGrabLaunchX(from, map, anchorX, launchStepX, rope, stats, ropeGrabCache, movementProfile)) {
            return null;
        }

        int minX = findRopeGrabBoundary(from, map, anchorX, launchStepX, rope,
                true, stats, ropeGrabCache, movementProfile);
        int maxX = findRopeGrabBoundary(from, map, anchorX, launchStepX, rope,
                false, stats, ropeGrabCache, movementProfile);

        int representativeX = (minX + maxX) / 2;
        Point representativeStart = from.pointAt(representativeX);
        Point representativeGrab = simulateGroundJumpRopeGrabCached(
                map, representativeStart, launchStepX, rope, ropeGrabCache, stats, movementProfile);
        if (representativeGrab == null) {
            return null;
        }

        int travelMs = AgentJumpProbeService.estimateGroundJumpRopeGrabTimeMs(
                map, representativeStart, launchStepX, rope, movementProfile);
        return new JumpLaunchWindow(minX, maxX, representativeStart, representativeGrab, travelMs);
    }

    private static int findRopeGrabBoundary(AgentNavigationGraph.Region from,
                                            MapleMap map,
                                            int startX,
                                            int launchStepX,
                                            Rope rope,
                                            boolean searchLeft,
                                            JumpBuildStats stats,
                                            RopeGrabCache ropeGrabCache,
                                            AgentMovementProfile movementProfile) {
        int limitX = searchLeft ? from.minX : from.maxX;
        int validX = startX;
        int invalidX = startX;
        int step = 1;

        while (true) {
            int probeX = searchLeft
                    ? Math.max(limitX, startX - step)
                    : Math.min(limitX, startX + step);
            if (probeX == validX) {
                break;
            }

            if (!isValidRopeGrabLaunchX(from, map, probeX, launchStepX, rope, stats, ropeGrabCache, movementProfile)) {
                invalidX = probeX;
                break;
            }

            validX = probeX;
            if (probeX == limitX) {
                return probeX;
            }
            step *= 2;
        }

        while (Math.abs(validX - invalidX) > 1) {
            int probeX = (validX + invalidX) / 2;
            if (isValidRopeGrabLaunchX(from, map, probeX, launchStepX, rope, stats, ropeGrabCache, movementProfile)) {
                validX = probeX;
            } else {
                invalidX = probeX;
            }
        }
        return validX;
    }

    private static boolean isValidRopeGrabLaunchX(AgentNavigationGraph.Region from,
                                                  MapleMap map,
                                                  int launchX,
                                                  int launchStepX,
                                                  Rope rope,
                                                  JumpBuildStats stats,
                                                  RopeGrabCache ropeGrabCache,
                                                  AgentMovementProfile movementProfile) {
        if (!isApproachableJumpLaunchX(from, map, launchX)) {
            return false;
        }
        Point grab = simulateGroundJumpRopeGrabCached(
                map, from.pointAt(launchX), launchStepX, rope, ropeGrabCache, stats, movementProfile);
        return grab != null;
    }

    private static boolean isBlockedWallBoundaryLaunch(AgentNavigationGraph.Region from,
                                                       MapleMap map,
                                                       Point launchPoint) {
        if (map == null || map.getFootholds() == null || launchPoint == null) {
            return false;
        }

        int moverZMass = from == null || from.isRopeRegion || from.segments.isEmpty()
                ? AgentWallCollisionPolicy.UNKNOWN_GROUP
                : zMassForFoothold(map, from.segments.getFirst().footholdId);
        for (Foothold foothold : map.getFootholds().getAllFootholds()) {
            if (!foothold.isWall()
                    || foothold.getX1() != launchPoint.x
                    || !AgentWallCollisionPolicy.collides(map, foothold, moverZMass)) {
                continue;
            }

            int minY = Math.min(foothold.getY1(), foothold.getY2());
            int maxY = Math.max(foothold.getY1(), foothold.getY2());
            if (launchPoint.y > minY && launchPoint.y <= maxY) {
                return true;
            }
        }
        return false;
    }

    private static int zMassForFoothold(MapleMap map, int footholdId) {
        if (map == null || map.getFootholds() == null) {
            return AgentWallCollisionPolicy.UNKNOWN_GROUP;
        }
        for (Foothold foothold : map.getFootholds().getAllFootholds()) {
            if (foothold.getId() == footholdId) {
                return foothold.getZMass();
            }
        }
        return AgentWallCollisionPolicy.UNKNOWN_GROUP;
    }

    private static boolean canWalkToLaunchX(AgentNavigationGraph.Region from, MapleMap map, int fromX, int launchX) {
        Point fromPoint = from.pointAt(fromX);
        Point launchPoint = from.pointAt(launchX);
        if (fromPoint == null || launchPoint == null || fromPoint.equals(launchPoint)) {
            return false;
        }
        return AgentGroundCollisionService.canWalkGroundStep(map, fromPoint, launchPoint.x - fromPoint.x);
    }

    // --- Rope entry edges: ground/rope region → rope region ---

    private static void addRopeEntryEdges(AgentNavigationGraph.Region ropeRegion,
                                          List<AgentNavigationGraph.Region> groundRegions,
                                          Map<Integer, Rope> ropeByRegionId,
                                          MapleMap map,
                                          Map<Integer, List<Point>> anchorsByRegionId,
                                          Map<Integer, List<AgentNavigationGraph.Edge>> outgoing,
                                          Set<String> edgeKeys,
                                          RopeGrabCache ropeGrabCache,
                                          AgentMovementProfile movementProfile) {
        Rope rope = ropeByRegionId.get(ropeRegion.id);
        if (rope == null) {
            return;
        }

        int ropeX = rope.x();
        int walkStep = AgentMovementKinematicsService.walkStep(map, movementProfile);
        JumpBuildStats stats = new JumpBuildStats();
        for (AgentNavigationGraph.Region ground : groundRegions) {
            for (Point anchor : anchorsByRegionId.getOrDefault(ground.id, List.of())) {
                int firstClimbableY = AgentNavigationPhysicsService.firstClimbableY(rope);
                boolean canGrab = Math.abs(anchor.x - ropeX) <= AgentMovementPhysicsConfig.configuredRopeGrabX()
                        && anchor.y >= firstClimbableY && anchor.y <= rope.bottomY();
                boolean canTopGrab = Math.abs(anchor.x - ropeX) <= AgentMovementPhysicsConfig.configuredRopeGrabX()
                        && anchor.y < rope.topY()
                        && rope.topY() - anchor.y <= AgentMovementPhysicsConfig.configuredMaxSnapDrop();
                boolean canJumpGrab = AgentJumpProbeService.canReachRopeFromGround(map, anchor, rope, movementProfile);
                boolean canTopStep = anchor.y <= rope.topY() + AgentMovementPhysicsConfig.configuredJumpYThreshold()
                        && Math.abs(anchor.x - ropeX) <= AgentMovementPhysicsConfig.configuredRopeGrabX();

                if (canGrab) {
                    Point ropePoint = new Point(ropeX, Math.max(firstClimbableY, Math.min(anchor.y, rope.bottomY())));
                    addEdge(ground.id, ropeRegion.id, AgentNavigationGraph.EdgeType.CLIMB,
                            anchor, ropePoint, 0, 0, AgentMovementPhysicsConfig.configuredMovementTickMs(), outgoing, edgeKeys);
                    continue;
                }

                if (canTopGrab) {
                    addEdge(ground.id, ropeRegion.id, AgentNavigationGraph.EdgeType.CLIMB,
                            anchor, new Point(ropeX, firstClimbableY), 0, 0, AgentMovementPhysicsConfig.configuredMovementTickMs(), outgoing, edgeKeys);
                    continue;
                }

                if (canTopStep) {
                    Point ropeGrab = AgentJumpProbeService.simulateDownJumpRopeGrab(map, anchor, rope);
                    if (ropeGrab != null) {
                        int cost = AgentJumpProbeService.estimateDownJumpRopeGrabTimeMs(map, anchor, rope);
                        addEdge(ground.id, ropeRegion.id, AgentNavigationGraph.EdgeType.CLIMB,
                                anchor, ropeGrab, 0, 0, cost, outgoing, edgeKeys);
                    }
                }

                if (canJumpGrab) {
                    for (int jumpStep : new int[]{-walkStep, 0, walkStep}) {
                        JumpLaunchWindow launchWindow = expandRopeGrabLaunchWindow(
                                ground, map, anchor.x, jumpStep, rope, stats, ropeGrabCache, movementProfile);
                        if (launchWindow == null) {
                            continue;
                        }
                        addEdge(ground.id, ropeRegion.id, AgentNavigationGraph.EdgeType.CLIMB,
                                launchWindow.startPoint(), launchWindow.endPoint(),
                                launchWindow.minX(), launchWindow.maxX(),
                                jumpStep, 0, launchWindow.landingTimeMs(), outgoing, edgeKeys);
                    }
                }
            }
        }
    }

    // --- Rope exit edges: rope region → ground/rope region ---

    private static void addRopeExitEdges(AgentNavigationGraph.Region ropeRegion,
                                         List<AgentNavigationGraph.Region> ropeRegions,
                                         Map<Integer, Rope> ropeByRegionId,
                                         MapleMap map,
                                         Map<Integer, AgentNavigationGraph.Region> regionsById,
                                         Map<Integer, Integer> regionIdByFootholdId,
                                         Map<Integer, List<AgentNavigationGraph.Edge>> outgoing,
                                         Set<String> edgeKeys,
                                         AgentMovementProfile movementProfile) {
        Rope rope = ropeByRegionId.get(ropeRegion.id);
        if (rope == null) {
            return;
        }

        int ropeX = rope.x();
        int jumpStep = AgentMovementKinematicsService.walkStep(map, movementProfile);
        int maxRopeJumpDx = AgentMovementKinematicsService.maxRopeGrabSimulationHorizontalTravel(map, movementProfile);

        // Direct step-off at the top of the rope
        addTopStepOffEdge(ropeRegion, rope, map, regionsById, regionIdByFootholdId, outgoing, edgeKeys);

        // Jump-off / step-off to ground at various heights along the rope
        for (int anchorY : ropeAnchorYs(rope)) {
            Point ropePoint = new Point(ropeX, anchorY);
            for (int stepX : new int[]{-jumpStep, 0, jumpStep}) {
                AgentJumpLanding landing = AgentJumpProbeService.simulateRopeJumpLanding(map, ropePoint, stepX, movementProfile);
                if (landing == null) {
                    continue;
                }

                int toRegionId = regionIdByFootholdId.getOrDefault(landing.foothold().getId(), -1);
                AgentNavigationGraph.Region toRegion = regionsById.get(toRegionId);
                if (toRegion == null || toRegion.isRopeRegion) {
                    continue;
                }

                int cost = AgentJumpProbeService.estimateRopeJumpLandingTimeMs(map, ropePoint, stepX, movementProfile);
                addEdge(ropeRegion.id, toRegion.id, AgentNavigationGraph.EdgeType.CLIMB,
                        ropePoint, landing.point(), stepX, 0, cost, outgoing, edgeKeys);
            }
        }

        // Rope-to-rope transfers need a tighter vertical sweep than generic rope exits.
        for (int anchorY : ropeTransferAnchorYs(rope)) {
            Point ropePoint = new Point(ropeX, anchorY);
            for (AgentNavigationGraph.Region otherRope : ropeRegions) {
                if (otherRope.id == ropeRegion.id) {
                    continue;
                }

                Rope targetRope = ropeByRegionId.get(otherRope.id);
                if (targetRope == null) {
                    continue;
                }

                int dx = Math.abs(ropeX - targetRope.x());
                if (dx > maxRopeJumpDx) {
                    continue;
                }

                int launchDir = targetRope.x() > ropeX ? jumpStep : -jumpStep;
                Point ropeGrab = AgentJumpProbeService.simulateRopeJumpGrab(map, ropePoint, launchDir, targetRope, movementProfile);
                if (ropeGrab == null) {
                    continue;
                }

                int cost = AgentJumpProbeService.estimateRopeJumpGrabTimeMs(map, ropePoint, launchDir, targetRope, movementProfile);
                addEdge(ropeRegion.id, otherRope.id, AgentNavigationGraph.EdgeType.CLIMB,
                        ropePoint, ropeGrab, launchDir, 0, cost, outgoing, edgeKeys);
            }
        }
    }

    private static void addTopStepOffEdge(AgentNavigationGraph.Region ropeRegion,
                                          Rope rope,
                                          MapleMap map,
                                          Map<Integer, AgentNavigationGraph.Region> regionsById,
                                          Map<Integer, Integer> regionIdByFootholdId,
                                          Map<Integer, List<AgentNavigationGraph.Edge>> outgoing,
                                          Set<String> edgeKeys) {
        Point probe = new Point(rope.x(), rope.topY() - 3);
        Point landPoint = map.getPointBelow(probe);
        if (landPoint == null || landPoint.y > rope.topY() + AgentMovementKinematicsService.climbStepPerTick() + 2) {
            return;
        }

        Foothold foothold = AgentGroundingService.findGroundFoothold(map, landPoint);
        if (foothold == null) {
            return;
        }

        AgentNavigationGraph.Region ground = regionsById.get(regionIdByFootholdId.getOrDefault(foothold.getId(), -1));
        if (ground == null || ground.isRopeRegion) {
            return;
        }

        Point ropePoint = new Point(rope.x(), rope.topY());
        addEdge(ropeRegion.id, ground.id, AgentNavigationGraph.EdgeType.CLIMB,
                ropePoint, landPoint, 0, 0, AgentMovementPhysicsConfig.configuredMovementTickMs(), outgoing, edgeKeys);
    }
    private static List<Integer> ropeAnchorYs(Rope rope) {
        List<Integer> ys = new ArrayList<>();
        int firstClimbableY = AgentNavigationPhysicsService.firstClimbableY(rope);
        ys.add(firstClimbableY);
        for (int y = rope.topY() + ROPE_ANCHOR_INTERVAL_PX; y < rope.bottomY(); y += ROPE_ANCHOR_INTERVAL_PX) {
            if (y > firstClimbableY) {
                ys.add(y);
            }
        }
        if (ys.getLast() != rope.bottomY()) {
            ys.add(rope.bottomY());
        }
        return ys;
    }

    private static Map<Integer, Rope> buildRopeByRegionId(MapleMap map, List<AgentNavigationGraph.Region> ropeRegions) {
        Map<Integer, Rope> ropeByRegionId = new HashMap<>();
        for (AgentNavigationGraph.Region ropeRegion : ropeRegions) {
            Rope rope = findRopeFromRegion(map, ropeRegion);
            if (rope != null) {
                ropeByRegionId.put(ropeRegion.id, rope);
            }
        }
        return ropeByRegionId;
    }

    private static List<Integer> ropeTransferAnchorYs(Rope rope) {
        List<Integer> ys = new ArrayList<>();
        int step = Math.max(1, AgentMovementKinematicsService.climbStepPerTick());
        int firstClimbableY = AgentNavigationPhysicsService.firstClimbableY(rope);
        for (int y = firstClimbableY; y <= rope.bottomY(); y += step) {
            ys.add(y);
        }
        if (ys.isEmpty() || ys.getLast() != rope.bottomY()) {
            ys.add(rope.bottomY());
        }
        return ys;
    }

    public static Rope findRopeFromRegion(MapleMap map, AgentNavigationGraph.Region ropeRegion) {
        if (ropeRegion == null || !ropeRegion.isRopeRegion) {
            return null;
        }
        for (Rope rope : map.getRopes()) {
            if (rope.x() == ropeRegion.minX && rope.topY() == ropeRegion.minY && rope.bottomY() == ropeRegion.maxY) {
                return rope;
            }
        }
        return null;
    }

    private static void addPortalEdges(Portal portal,
                                       MapleMap map,
                                       Map<Integer, AgentNavigationGraph.Region> regionsById,
                                       Map<Integer, Integer> regionIdByFootholdId,
                                       Map<Integer, List<AgentNavigationGraph.Edge>> outgoing,
                                       Set<String> edgeKeys) {
        if (portal.getTargetMapId() != map.getId()) {
            return;
        }

        Portal targetPortal = map.getPortal(portal.getTarget());
        if (targetPortal == null || targetPortal.getId() == portal.getId()) {
            return;
        }

        // Portal WZ positions can sit a few pixels below the foothold surface they belong to,
        // causing findBelow to skip the correct foothold and land on the next lower platform.
        // Probe MAX_SNAP_DROP above the portal position so findBelow always finds the right foothold.
        int snapUp = AgentMovementPhysicsConfig.configuredMaxSnapDrop();
        AgentNavigationGraph.Region from = findRegionBelow(map, regionsById, regionIdByFootholdId,
                new Point(portal.getPosition().x, portal.getPosition().y - snapUp));
        AgentNavigationGraph.Region to = findRegionBelow(map, regionsById, regionIdByFootholdId,
                new Point(targetPortal.getPosition().x, targetPortal.getPosition().y - snapUp));
        if (from == null || to == null) {
            return;
        }

        Point start = from.pointAt(portal.getPosition().x);
        Point end = to.pointAt(targetPortal.getPosition().x);
        // A single portal is instantaneous, so its base edge cost is 0. The only real time a
        // portal costs is the post-use cooldown the bot pays when it chains straight into another
        // portal; that is modelled path-dependently in AgentNavigationPathService A* (the viaPortal
        // state flag), not as a flat per-edge cost. See PORTAL_USE_COOLDOWN_MS.
        addEdge(from.id, to.id, AgentNavigationGraph.EdgeType.PORTAL, start, end, 0, portal.getId(), 0, outgoing, edgeKeys);
    }

    private static Map<Integer, List<Integer>> buildFeatureXsByRegionId(MapleMap map,
                                                                        List<AgentNavigationGraph.Region> regions,
                                                                        Map<Integer, Integer> regionIdByFootholdId) {
        Map<Integer, Set<Integer>> featureXs = new HashMap<>();

        for (Rope rope : map.getRopes()) {
            addFeatureX(featureXs, findRegionIdBelow(map, regionIdByFootholdId, new Point(rope.x(), rope.bottomY() - 1)), rope.x());
            addFeatureX(featureXs, findRegionIdBelow(map, regionIdByFootholdId, new Point(rope.x(), rope.topY() - 1)), rope.x());
            addFeatureX(featureXs, findRegionIdBelow(map, regionIdByFootholdId,
                    new Point(rope.x(), rope.topY() - AgentMovementPhysicsConfig.configuredJumpYThreshold() * 2)), rope.x());
        }

        for (Portal portal : map.getPortals()) {
            int snapUp = AgentMovementPhysicsConfig.configuredMaxSnapDrop();
            Point portalProbe = new Point(portal.getPosition().x, portal.getPosition().y - snapUp);
            addFeatureX(featureXs, findRegionIdBelow(map, regionIdByFootholdId, portalProbe), portal.getPosition().x);
            if (portal.getTargetMapId() != map.getId()) {
                continue;
            }

            Portal targetPortal = map.getPortal(portal.getTarget());
            if (targetPortal != null) {
                Point targetProbe = new Point(targetPortal.getPosition().x, targetPortal.getPosition().y - snapUp);
                addFeatureX(featureXs, findRegionIdBelow(map, regionIdByFootholdId, targetProbe), targetPortal.getPosition().x);
            }
        }

        for (AgentNavigationGraph.Region region : regions) {
            if (region.isRopeRegion) {
                continue;
            }
            // Left/right endpoints of any platform are X positions where a jump trajectory
            // from the region below qualitatively changes (clears the platform vs. lands on it).
            // Without these as feature anchors, narrow launch windows for "jump-over" or
            // "land-on-intermediate" edges can have zero seeded samples and be missed.
            projectRegionXsToRegionBelow(featureXs, map, regionIdByFootholdId, region.leftPoint());
            projectRegionXsToRegionBelow(featureXs, map, regionIdByFootholdId, region.rightPoint());
            if (region.width() <= 64) {
                projectRegionXsToRegionBelow(featureXs, map, regionIdByFootholdId, region.centerPoint());
            }
        }

        Map<Integer, List<Integer>> featuresByRegionId = new HashMap<>();
        for (Map.Entry<Integer, Set<Integer>> entry : featureXs.entrySet()) {
            List<Integer> xs = new ArrayList<>(entry.getValue());
            xs.sort(Integer::compareTo);
            featuresByRegionId.put(entry.getKey(), xs);
        }
        return featuresByRegionId;
    }

    private static void projectRegionXsToRegionBelow(Map<Integer, Set<Integer>> featureXs,
                                                     MapleMap map,
                                                     Map<Integer, Integer> regionIdByFootholdId,
                                                     Point point) {
        if (point == null) {
            return;
        }
        addFeatureX(featureXs, findRegionIdBelow(map, regionIdByFootholdId, new Point(point.x, point.y + 1)), point.x);
    }

    private static void addFeatureX(Map<Integer, Set<Integer>> featureXs, int regionId, int x) {
        if (regionId < 0) {
            return;
        }
        featureXs.computeIfAbsent(regionId, ignored -> new HashSet<>()).add(x);
    }

    private static int findRegionIdBelow(MapleMap map,
                                         Map<Integer, Integer> regionIdByFootholdId,
                                         Point point) {
        if (map.getFootholds() == null) {
            return -1;
        }

        Foothold foothold = map.getFootholds().findBelow(point);
        if (foothold == null) {
            return -1;
        }

        return regionIdByFootholdId.getOrDefault(foothold.getId(), -1);
    }

    private static AgentNavigationGraph.Region findRegionBelow(MapleMap map,
                                                             Map<Integer, AgentNavigationGraph.Region> regionsById,
                                                             Map<Integer, Integer> regionIdByFootholdId,
                                                             Point point) {
        int regionId = findRegionIdBelow(map, regionIdByFootholdId, point);
        if (regionId < 0) {
            return null;
        }
        return regionsById.get(regionId);
    }

    private static List<Point> anchorPoints(MapleMap map,
                                            AgentNavigationGraph.Region region,
                                            List<Integer> featureXs,
                                            AgentMovementProfile movementProfile) {
        List<Point> points = new ArrayList<>();
        addAnchor(points, region.leftPoint());
        // Near-edge anchors for better jump/drop accuracy at platform boundaries
        int edgeInset = Math.max(8, (int) Math.round(movementProfile.walkVelocityPxs() * AgentMovementPhysicsConfig.configuredMovementTickMs() / 1000.0));
        if (region.width() > edgeInset * 2) {
            addAnchor(points, region.pointAt(region.minX + edgeInset), ENDPOINT_ANCHOR_SPACING_PX);
            addAnchor(points, region.pointAt(region.maxX - edgeInset), ENDPOINT_ANCHOR_SPACING_PX);
        }
        int ticksToApex = Math.max(1, (int) Math.ceil(
                AgentMovementKinematicsService.jumpForcePerTick(movementProfile)
                        / Math.max(0.001f, AgentMovementKinematicsService.gravityPerTick())));
        int jumpInset = Math.max(edgeInset * 3, AgentMovementKinematicsService.walkStep(map, movementProfile) * ticksToApex);
        if (region.width() > jumpInset * 2) {
            addAnchor(points, region.pointAt(region.minX + jumpInset), ENDPOINT_ANCHOR_SPACING_PX);
            addAnchor(points, region.pointAt(region.maxX - jumpInset), ENDPOINT_ANCHOR_SPACING_PX);
        }
        // Interior densification: narrow launch windows for jumps over or onto platforms above
        // can sit in the middle of a wide region with no naturally-derived feature X. Without
        // dense interior sampling, findJumpBoundary has no seed and the edge is missed entirely.
        // Spacing matches walkStep so a launch window as narrow as a single pre-launch tick
        // (≈walkStep px) is guaranteed at least one seed sample. Repeated sims are absorbed by
        // jumpLandingCache.
        int walkStep = AgentMovementKinematicsService.walkStep(map, movementProfile);
        int interiorSpacing = Math.max(walkStep, 1);
        if (region.width() > edgeInset * 2) {
            for (int x = region.minX + edgeInset;
                 x <= region.maxX - edgeInset;
                 x += interiorSpacing) {
                addAnchor(points, region.pointAt(x), 0);
            }
        }
        for (AgentNavigationGraph.Segment segment : region.segments) {
            addAnchor(points, new Point(segment.x1, segment.y1), ENDPOINT_ANCHOR_SPACING_PX);
            addAnchor(points, new Point(segment.x2, segment.y2), ENDPOINT_ANCHOR_SPACING_PX);
        }
        if (region.width() >= Math.max(AgentMovementPhysicsConfig.configuredFollowDist() * 2, 140)) {
            addAnchor(points, region.centerPoint());
        }
        if (region.width() >= Math.max(AgentMovementPhysicsConfig.configuredFollowDist() * 4, 260)) {
            addAnchor(points, region.pointAt(region.minX + region.width() / 3));
            addAnchor(points, region.pointAt(region.maxX - region.width() / 3));
        }
        addAnchor(points, region.rightPoint());
        for (int featureX : featureXs) {
            if (featureX >= region.minX && featureX <= region.maxX) {
                addAnchor(points, region.pointAt(featureX));
            }
        }
        points.sort(Comparator.comparingInt((Point point) -> point.x).thenComparingInt(point -> point.y));
        return points;
    }

    private static void addAnchor(List<Point> points, Point point) {
        addAnchor(points, point, 0);
    }

    private static void addAnchor(List<Point> points, Point point, int minSpacingPx) {
        for (Point existing : points) {
            if (existing.equals(point)) {
                return;
            }
            if (minSpacingPx > 0
                    && Math.abs(existing.x - point.x) <= minSpacingPx
                    && Math.abs(existing.y - point.y) <= 8) {
                return;
            }
        }
        points.add(point);
    }

    private static boolean isWalkConnection(EndpointConnection connection) {
        int dx = Math.abs(connection.to.x - connection.from.x);
        int dy = connection.to.y - connection.from.y;
        return AgentNavigationPhysicsService.isWalkableEndpointStep(dx, dy);
    }

    private static void addEdge(int fromRegionId,
                                int toRegionId,
                                AgentNavigationGraph.EdgeType type,
                                Point startPoint,
                                Point endPoint,
                                int launchMinX,
                                int launchMaxX,
                                int launchStepX,
                                int portalId,
                                int cost,
                                Map<Integer, List<AgentNavigationGraph.Edge>> outgoing,
                                Set<String> edgeKeys) {
        addEdge(fromRegionId, toRegionId, type, startPoint, endPoint, launchMinX, launchMaxX, launchStepX, portalId,
                0, 0, 0, cost, outgoing, edgeKeys);
    }

    private static void addEdge(int fromRegionId,
                                int toRegionId,
                                AgentNavigationGraph.EdgeType type,
                                Point startPoint,
                                Point endPoint,
                                int launchStepX,
                                int portalId,
                                int cost,
                                Map<Integer, List<AgentNavigationGraph.Edge>> outgoing,
                                Set<String> edgeKeys) {
        addEdge(fromRegionId, toRegionId, type, startPoint, endPoint, startPoint.x, startPoint.x, launchStepX, portalId,
                0, 0, 0, cost, outgoing, edgeKeys);
    }

    private static void addEdge(int fromRegionId,
                                int toRegionId,
                                AgentNavigationGraph.EdgeType type,
                                Point startPoint,
                                Point endPoint,
                                int launchMinX,
                                int launchMaxX,
                                int launchStepX,
                                int portalId,
                                int ropeX,
                                int ropeTopY,
                                int ropeBottomY,
                                int cost,
                                Map<Integer, List<AgentNavigationGraph.Edge>> outgoing,
                                Set<String> edgeKeys) {
        String key = fromRegionId + ":" + toRegionId + ":" + type + ":" + startPoint.x + ":" + startPoint.y + ":"
                + endPoint.x + ":" + endPoint.y + ":" + launchStepX + ":" + portalId + ":"
                + ropeX + ":" + ropeTopY + ":" + ropeBottomY + ":" + launchMinX + ":" + launchMaxX;
        if (!edgeKeys.add(key)) {
            return;
        }

        outgoing.computeIfAbsent(fromRegionId, ignored -> new ArrayList<>())
                .add(new AgentNavigationGraph.Edge(fromRegionId, toRegionId, type, startPoint, endPoint,
                        launchMinX, launchMaxX, launchStepX, portalId, ropeX, ropeTopY, ropeBottomY, cost));
        BuildProfileBuilder profile = ACTIVE_BUILD_PROFILE.get();
        if (profile != null) {
            profile.recordEdge(type);
        }
    }

    private static EndpointConnection closestEndpointConnection(Foothold first, Foothold second) {
        Point[] firstEndpoints = new Point[]{
                new Point(first.getX1(), first.getY1()),
                new Point(first.getX2(), first.getY2())
        };
        Point[] secondEndpoints = new Point[]{
                new Point(second.getX1(), second.getY1()),
                new Point(second.getX2(), second.getY2())
        };

        EndpointConnection best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Point from : firstEndpoints) {
            for (Point to : secondEndpoints) {
                int distance = Math.abs(to.x - from.x) + Math.abs(to.y - from.y);
                if (distance < bestDistance) {
                    best = new EndpointConnection(from, to);
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    private static EndpointConnection sharedEndpointConnection(Foothold first, Foothold second) {
        Point[] firstEndpoints = new Point[]{
                new Point(first.getX1(), first.getY1()),
                new Point(first.getX2(), first.getY2())
        };
        Point[] secondEndpoints = new Point[]{
                new Point(second.getX1(), second.getY1()),
                new Point(second.getX2(), second.getY2())
        };

        for (Point from : firstEndpoints) {
            for (Point to : secondEndpoints) {
                if (from.equals(to)) {
                    return new EndpointConnection(from, to);
                }
            }
        }
        return null;
    }

    private static int footholdMinX(Foothold foothold) {
        return Math.min(foothold.getX1(), foothold.getX2());
    }

    private static int groupMinX(List<Foothold> footholds) {
        int minX = Integer.MAX_VALUE;
        for (Foothold foothold : footholds) {
            minX = Math.min(minX, footholdMinX(foothold));
        }
        return minX;
    }

    private static int groupMinY(List<Foothold> footholds) {
        int minY = Integer.MAX_VALUE;
        for (Foothold foothold : footholds) {
            minY = Math.min(minY, Math.min(foothold.getY1(), foothold.getY2()));
        }
        return minY;
    }

    private static int estimateWalkCost(Point start, Point end) {
        return estimateWalkCost(start, end, AgentMovementProfile.base());
    }

    private static int estimateWalkCost(Point start, Point end, AgentMovementProfile movementProfile) {
        return estimateHorizontalTravelTimeMs(Math.abs(end.x - start.x), movementProfile);
    }

    private static int estimateHorizontalTravelTimeMs(int dx, AgentMovementProfile movementProfile) {
        return Math.max(0, (int) Math.round((dx * 1000.0) / Math.max(1.0, movementProfile.walkVelocityPxs())));
    }

    private static int dropLaunchStep(AgentNavigationGraph.Region region, MapleMap map, Point anchor, AgentMovementProfile movementProfile) {
        Point left = region.leftPoint();
        if (Math.abs(anchor.x - left.x) <= ENDPOINT_ANCHOR_SPACING_PX && Math.abs(anchor.y - left.y) <= 12) {
            return -AgentMovementKinematicsService.walkStep(map, movementProfile);
        }

        Point right = region.rightPoint();
        if (Math.abs(anchor.x - right.x) <= ENDPOINT_ANCHOR_SPACING_PX && Math.abs(anchor.y - right.y) <= 12) {
            return AgentMovementKinematicsService.walkStep(map, movementProfile);
        }

        return 0;
    }

    private record EndpointConnection(Point from, Point to) {
    }



    private static final class UnionFind {
        private final Map<Integer, Integer> parent = new HashMap<>();
        private final Map<Integer, Integer> rank = new HashMap<>();

        void add(int value) {
            parent.putIfAbsent(value, value);
            rank.putIfAbsent(value, 0);
        }

        int find(int value) {
            Integer parentValue = parent.get(value);
            if (parentValue == null) {
                add(value);
                return value;
            }
            if (parentValue == value) {
                return value;
            }

            int root = find(parentValue);
            parent.put(value, root);
            return root;
        }

        void union(int first, int second) {
            int firstRoot = find(first);
            int secondRoot = find(second);
            if (firstRoot == secondRoot) {
                return;
            }

            int firstRank = rank.getOrDefault(firstRoot, 0);
            int secondRank = rank.getOrDefault(secondRoot, 0);
            if (firstRank < secondRank) {
                parent.put(firstRoot, secondRoot);
                return;
            }
            if (firstRank > secondRank) {
                parent.put(secondRoot, firstRoot);
                return;
            }

            parent.put(secondRoot, firstRoot);
            rank.put(firstRoot, firstRank + 1);
        }
    }
}
