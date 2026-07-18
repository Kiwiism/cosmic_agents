package net.server.services.task.channel;

import client.BotClient;
import client.Character;
import net.packet.Packet;
import net.server.services.BaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.TimerManager;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.capabilities.mobcontrol.AgentMobReactionMode;
import server.life.Monster;
import server.life.simulation.MobControlAuthority;
import server.life.simulation.MobMotionState;
import server.life.simulation.MobPhysicsProfile;
import server.life.simulation.MobPhysicsProfileFactory;
import server.life.simulation.MobPhysicsTuningSnapshot;
import server.life.simulation.MobSimulationRegistry;
import server.life.simulation.MobSimulationSession;
import server.maps.MapleMap;
import server.monitoring.MapBroadcastDiagnostics;
import server.monitoring.SlowOperationLogger;
import server.movement.AbsoluteLifeMovement;
import server.movement.LifeMovementFragment;
import server.physics.PhysicsBody;
import server.physics.PhysicsTerrain;
import tools.PacketCreator;

import java.awt.Point;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/** One periodic authoritative monster-physics loop for a channel. */
public final class MobPhysicsService extends BaseService {
    private static final Logger log = LoggerFactory.getLogger(MobPhysicsService.class);
    private static final int OUTER_TICK_MS = 50;
    private static final int CLIENT_Y_OFFSET = 2;
    private static final int BROADCAST_TIMING_SAMPLE_MASK = 127;
    private static final long SLOW_BROADCAST_THRESHOLD_MS = 100L;
    private static final Set<MobPhysicsService> INSTANCES = ConcurrentHashMap.newKeySet();

    public enum ReleaseReason {
        MODE_CHANGE, OBSERVER_LOSS, AGENT_DEPARTURE, AGENT_DEATH,
        MONSTER_GONE, MAP_CHANGE, INVALID_STATE, MISSING_TERRAIN,
        BROADCAST_FAILURE, CLIENT_MAP_TRANSITION, AGGRO_TIMEOUT, SERVICE_SHUTDOWN
    }

    private final MobSimulationRegistry registry = new MobSimulationRegistry();
    private final LongAdder acquisitions = new LongAdder();
    private final LongAdder impacts = new LongAdder();
    private final LongAdder substeps = new LongAdder();
    private final LongAdder publications = new LongAdder();
    private final LongAdder cappedCatchUps = new LongAdder();
    private final LongAdder invalidRecoveries = new LongAdder();
    private final LongAdder missingFootholds = new LongAdder();
    private final LongAdder handoffs = new LongAdder();
    private final LongAdder tickCount = new LongAdder();
    private final LongAdder totalTickNanos = new LongAdder();
    private final AtomicLong maximumTickNanos = new AtomicLong();
    private final AtomicLong lastDiagnosticNanos = new AtomicLong();
    private final Map<ReleaseReason, LongAdder> releases = new EnumMap<>(ReleaseReason.class);
    private final Object tickPassLock = new Object();
    private final Map<MapleMap, ReleaseReason> tickMapInvalidReasons = new IdentityHashMap<>();
    private final TickStats tickStats = new TickStats();
    private final boolean schedulingEnabled;
    private long broadcastSequence;
    private volatile ScheduledFuture<?> task;

    public MobPhysicsService() {
        this(true);
    }

    MobPhysicsService(boolean schedule) {
        for (ReleaseReason reason : ReleaseReason.values()) {
            releases.put(reason, new LongAdder());
        }
        INSTANCES.add(this);
        schedulingEnabled = schedule;
    }

    public boolean acceptedHit(Character attacker, Monster monster, int damage, long delayMs) {
        if (!eligible(attacker, monster, damage)) {
            return false;
        }
        MapleMap map = monster.getMap();
        PhysicsTerrain terrain = map.getPhysicsTerrain();
        if (terrain == null) {
            missingFootholds.increment();
            return false;
        }
        Point attackerPosition = attacker.getPosition();
        Point monsterPosition = monster.getPosition();
        if (attackerPosition == null || monsterPosition == null) {
            return false;
        }
        int direction = Integer.compare(monsterPosition.x, attackerPosition.x);
        if (direction == 0) {
            direction = attacker.isFacingLeft() ? -1 : 1;
        }
        long now = System.nanoTime();
        monster.lockMonster();
        try {
            if (!eligible(attacker, monster, damage) || monster.getMap() != map
                    || map.getMonsterByOid(monster.getObjectId()) != monster
                    || !monster.aggroAcquireAgentPhysics(attacker)) {
                return false;
            }
            MobSimulationSession session = registry.get(monster);
            if (session == null) {
                MobPhysicsProfile profile = MobPhysicsProfileFactory.from(monster.getStats());
                session = new MobSimulationSession(map, monster, attacker, profile, terrain, now);
                MobSimulationSession raced = registry.putIfAbsent(monster, session);
                if (raced != null) {
                    session = raced;
                } else {
                    acquisitions.increment();
                }
            }
            session.acceptHit(attacker, damage, delayMs, direction, now);
            impacts.increment();
            ensureScheduled();
            return true;
        } catch (RuntimeException failure) {
            MobSimulationSession failed = registry.get(monster);
            if (failed != null) {
                release(failed, ReleaseReason.INVALID_STATE);
                if (monster.getControlAuthority() == MobControlAuthority.AGENT_PHYSICS) {
                    monster.aggroReleaseAgentPhysics(null, true);
                }
            } else {
                monster.aggroReleaseAgentPhysics(attacker, true);
            }
            log.warn("Could not acquire Agent physics for mob {}", monster.getObjectId(), failure);
            return false;
        } finally {
            monster.unlockMonster();
        }
    }

    private synchronized void ensureScheduled() {
        if (schedulingEnabled && task == null && registry.size() > 0) {
            task = TimerManager.getInstance().register(
                    this::tickSafely, OUTER_TICK_MS, OUTER_TICK_MS);
        }
    }

    private synchronized void stopIfIdle() {
        if (registry.size() == 0 && task != null) {
            task.cancel(false);
            task = null;
        }
    }

    private static boolean eligible(Character attacker, Monster monster, int damage) {
        if (AgentCombatConfig.cfg.AGENT_MOB_REACTION_MODE != AgentMobReactionMode.PHYSICS
                || attacker == null || monster == null || damage <= 0
                || !(attacker.getClient() instanceof BotClient) || !attacker.isAlive()
                || !monster.isAlive()) {
            return false;
        }
        MapleMap map = monster.getMap();
        return map != null && attacker.getMap() == map && hasPhysicsAudience(map)
                && !map.hasTransitioningPlayerObserver()
                && map.isMobPhysicsObserverWarmupComplete();
    }

    private void tickSafely() {
        long started = System.nanoTime();
        try {
            if (AgentCombatConfig.cfg.AGENT_MOB_REACTION_MODE != AgentMobReactionMode.PHYSICS) {
                releaseAll(ReleaseReason.MODE_CHANGE);
                return;
            }
            tickAt(System.nanoTime());
        } finally {
            long elapsed = System.nanoTime() - started;
            tickCount.increment();
            totalTickNanos.add(elapsed);
            maximumTickNanos.accumulateAndGet(elapsed, Math::max);
            if (AgentCombatConfig.cfg.MOB_PHYSICS_DIAGNOSTIC_LOGGING) {
                long now = System.nanoTime();
                long last = lastDiagnosticNanos.get();
                if (now - last >= 30_000_000_000L
                        && lastDiagnosticNanos.compareAndSet(last, now)) {
                    log.info(globalStatus());
                }
            }
        }
    }

    private void tickAt(long now) {
        synchronized (tickPassLock) {
            MobPhysicsTuningSnapshot tuning = MobPhysicsTuningSnapshot.capture();
            tickMapInvalidReasons.clear();
            tickStats.reset();
            for (MobSimulationSession session : registry.liveSessions()) {
                try {
                    MapleMap map = session.map();
                    ReleaseReason mapInvalidReason;
                    if (tickMapInvalidReasons.containsKey(map)) {
                        mapInvalidReason = tickMapInvalidReasons.get(map);
                    } else {
                        mapInvalidReason = invalidMapReason(map);
                        tickMapInvalidReasons.put(map, mapInvalidReason);
                    }
                    tickSession(session, now, mapInvalidReason, tuning, tickStats);
                } catch (RuntimeException | LinkageError failure) {
                    log.warn("Releasing failed Agent mob-physics session for mob {}",
                            session.monster().getObjectId(), failure);
                    release(session, ReleaseReason.INVALID_STATE);
                }
            }
            flushTickStats(tickStats);
        }
    }

    void tickForTest(long nowNanos) { tickAt(nowNanos); }
    MobSimulationSession sessionForTest(Monster monster) { return registry.get(monster); }
    int activeSessionCountForTest() { return registry.size(); }

    private void tickSession(MobSimulationSession session, long now,
                             ReleaseReason mapInvalidReason,
                             MobPhysicsTuningSnapshot tuning,
                             TickStats stats) {
        long generation = session.generation();
        ReleaseReason invalid = invalidReason(session, mapInvalidReason, now,
                tuning.aggroTimeoutNanos());
        if (invalid != null) {
            releaseIfGeneration(session, invalid, generation);
            return;
        }
        MobSimulationSession.AdvanceResult advanced = session.advance(now, tuning);
        stats.substeps += advanced.substeps();
        if (advanced.catchUpCapped()) stats.cappedCatchUps++;
        stats.invalidRecoveries += advanced.invalidRecoveries();

        Monster monster = session.monster();
        PhysicsBody body = session.body();
        if (!(Double.isFinite(body.x()) && Double.isFinite(body.y())
                && Double.isFinite(body.velocityX()) && Double.isFinite(body.velocityY()))) {
            release(session, ReleaseReason.INVALID_STATE);
            return;
        }
        int currentX = (int) Math.round(body.x());
        int currentY = (int) Math.round(body.y());
        int stance = stance(session);
        monster.lockMonster();
        try {
            if (monster.getControlAuthority() != MobControlAuthority.AGENT_PHYSICS) {
                release(session, ReleaseReason.MONSTER_GONE);
                return;
            }
            if (monster.getFh() != body.footholdId()) {
                monster.setFh(body.footholdId());
            }
            if (monster.getStance() != stance) {
                monster.setStance(stance);
            }
            Point previous = monster.getPosition();
            boolean positionChanged = previous == null
                    || previous.x != currentX || previous.y != currentY;
            boolean publicationDue = session.publicationDue(
                    now, tuning.publicationIntervalNanos());
            Point current = positionChanged || publicationDue
                    ? new Point(currentX, currentY) : null;
            if (positionChanged) {
                session.map().moveMonsterFromServerPhysics(monster, current);
            }
            if (publicationDue) {
                publish(session, current, stance, now, tuning, stats);
            }
        } finally {
            monster.unlockMonster();
        }
    }

    private void publish(MobSimulationSession session, Point current, int stance, long now,
                         MobPhysicsTuningSnapshot tuning, TickStats stats) {
        Point start = session.markPublished(now, current);
        PhysicsBody body = session.body();
        Point clientEnd = new Point(current.x, current.y + CLIENT_Y_OFFSET);
        int duration = Math.min(Short.MAX_VALUE, tuning.publicationIntervalMs());
        AbsoluteLifeMovement movement = new AbsoluteLifeMovement(0, clientEnd, duration, stance);
        movement.setPixelsPerSecond(new Point(
                (int) Math.round(body.velocityX() * 125.0),
                (int) Math.round(body.velocityY() * 125.0)));
        movement.setFh(body.footholdId());
        int rawActivity = session.rawActivityForPublication(stance, now);
        Packet packet = PacketCreator.moveMonster(session.monster().getObjectId(), rawActivity, start,
                List.<LifeMovementFragment>of(movement));
        try {
            boolean timed = (broadcastSequence++ & BROADCAST_TIMING_SAMPLE_MASK) == 0;
            long started = timed ? System.nanoTime() : 0L;
            int recipients = session.map().broadcastMobPhysicsMessage(packet, current);
            long elapsed = timed ? System.nanoTime() - started : 0L;
            stats.recordBroadcast(session.map().getId(), recipients, timed, elapsed);
            if (timed && SlowOperationLogger.isSlow(elapsed, SLOW_BROADCAST_THRESHOLD_MS)) {
                SlowOperationLogger.warnIfSlowElapsed(
                        "mob-physics-broadcast map=" + session.map().getId()
                                + " recipients=" + recipients,
                        elapsed, SLOW_BROADCAST_THRESHOLD_MS);
            }
        } catch (RuntimeException failure) {
            release(session, ReleaseReason.BROADCAST_FAILURE);
        }
    }

    static int stance(MobSimulationSession session) {
        PhysicsBody body = session.body();
        boolean facingLeft;
        if (session.motion() == MobMotionState.KNOCKBACK) {
            facingLeft = session.impactFacingLeft();
            return facingLeft ? 1 : 0;
        }
        if (session.motion() == MobMotionState.FLINCH) {
            facingLeft = session.impactFacingLeft();
            return facingLeft ? 1 : 0;
        }
        facingLeft = Math.abs(body.velocityX()) >= 0.1
                ? body.velocityX() < 0.0
                : session.targetX() < body.x();
        if (!body.grounded() && !session.profile().flying()) {
            return facingLeft ? 3 : 2;
        }
        boolean moving = session.motion() == MobMotionState.CHASE
                || Math.abs(body.velocityX()) >= 0.1
                || session.profile().flying() && Math.abs(body.velocityY()) >= 0.1;
        return moving ? (facingLeft ? 1 : 0) : (facingLeft ? 5 : 4);
    }

    private static ReleaseReason invalidMapReason(MapleMap map) {
        if (map.hasTransitioningPlayerObserver()) return ReleaseReason.CLIENT_MAP_TRANSITION;
        if (!map.isMobPhysicsObserverWarmupComplete()) return ReleaseReason.CLIENT_MAP_TRANSITION;
        if (!hasPhysicsAudience(map)) return ReleaseReason.OBSERVER_LOSS;
        return null;
    }

    /**
     * The stress switch deliberately substitutes only the demand signal. Real-client
     * transition and warm-up checks remain mandatory, and no fake packet recipient is
     * added to the map.
     */
    private static boolean hasPhysicsAudience(MapleMap map) {
        return map.isObservedByPlayer()
                || AgentCombatConfig.cfg.MOB_PHYSICS_VIRTUAL_OBSERVER_STRESS;
    }

    private static ReleaseReason invalidReason(MobSimulationSession session,
                                               ReleaseReason mapInvalidReason,
                                               long nowNanos,
                                               long aggroTimeoutNanos) {
        Monster monster = session.monster();
        Character agent = session.agent();
        MapleMap map = session.map();
        if (mapInvalidReason != null) return mapInvalidReason;
        if (!monster.isAlive() || map.getMonsterByOid(monster.getObjectId()) != monster)
            return ReleaseReason.MONSTER_GONE;
        if (monster.getMap() != map) return ReleaseReason.MAP_CHANGE;
        if (!agent.isAlive()) return ReleaseReason.AGENT_DEATH;
        if (agent.getMap() != map) return ReleaseReason.AGENT_DEPARTURE;
        if (session.agentHitLeaseExpiredNanos(nowNanos, aggroTimeoutNanos)) {
            return ReleaseReason.AGGRO_TIMEOUT;
        }
        return null;
    }

    private void flushTickStats(TickStats stats) {
        if (stats.substeps != 0) substeps.add(stats.substeps);
        if (stats.publications != 0) publications.add(stats.publications);
        if (stats.cappedCatchUps != 0) cappedCatchUps.add(stats.cappedCatchUps);
        if (stats.invalidRecoveries != 0) invalidRecoveries.add(stats.invalidRecoveries);
        if (stats.publications != 0) {
            MapBroadcastDiagnostics.recordBatch(stats.broadcastBatch());
        }
    }

    private static final class TickStats {
        private long substeps;
        private long publications;
        private long cappedCatchUps;
        private long invalidRecoveries;
        private long recipients;
        private long timedBroadcasts;
        private long slowBroadcasts;
        private long totalDurationNanos;
        private long maxDurationNanos;
        private int maxMapId;
        private int maxRecipients;
        private long lastDurationNanos;
        private int lastMapId;
        private int lastRecipients;

        private void reset() {
            substeps = publications = cappedCatchUps = invalidRecoveries = 0L;
            recipients = timedBroadcasts = slowBroadcasts = totalDurationNanos = 0L;
            maxDurationNanos = lastDurationNanos = 0L;
            maxMapId = maxRecipients = lastMapId = lastRecipients = 0;
        }

        private void recordBroadcast(int mapId, int recipientCount,
                                     boolean timed, long elapsedNanos) {
            publications++;
            recipients += recipientCount;
            if (!timed) return;
            timedBroadcasts++;
            totalDurationNanos += elapsedNanos;
            if (SlowOperationLogger.isSlow(elapsedNanos, SLOW_BROADCAST_THRESHOLD_MS)) {
                slowBroadcasts++;
            }
            lastDurationNanos = elapsedNanos;
            lastMapId = mapId;
            lastRecipients = recipientCount;
            if (elapsedNanos > maxDurationNanos) {
                maxDurationNanos = elapsedNanos;
                maxMapId = mapId;
                maxRecipients = recipientCount;
            }
        }

        private MapBroadcastDiagnostics.Batch broadcastBatch() {
            return new MapBroadcastDiagnostics.Batch(
                    publications, recipients, publications, timedBroadcasts,
                    slowBroadcasts, totalDurationNanos, maxDurationNanos,
                    maxMapId, maxRecipients, lastDurationNanos, lastMapId, lastRecipients);
        }
    }

    private void release(MobSimulationSession session, ReleaseReason reason) {
        release(session, reason, null);
    }

    void releaseIfGeneration(MobSimulationSession session, ReleaseReason reason,
                             long expectedGeneration) {
        release(session, reason, expectedGeneration);
    }

    private void release(MobSimulationSession session, ReleaseReason reason,
                         Long expectedGeneration) {
        Monster monster = session.monster();
        monster.lockMonster();
        try {
            if (expectedGeneration != null && !session.hasGeneration(expectedGeneration)) {
                return;
            }
            if (!registry.remove(monster, session)) {
                return;
            }
            if (reason == ReleaseReason.CLIENT_MAP_TRANSITION
                    || reason == ReleaseReason.OBSERVER_LOSS
                    || reason == ReleaseReason.AGGRO_TIMEOUT) {
                // Spawn/control packets should describe a stable pose. A physics session
                // can otherwise leave a recently hit mob in its transient walk/flinch
                // stance after the last observer leaves or while publication is paused.
                monster.setStance(stableTransitionStance(session));
            }
            releaseRemovedSession(session, reason);
        } finally {
            monster.unlockMonster();
        }
        stopIfIdle();
    }

    static int stableTransitionStance(MobSimulationSession session) {
        boolean facingLeft = Math.floorMod(session.monster().getStance(), 2) == 1;
        if (session.profile().flying()) {
            return facingLeft ? 3 : 2;
        }
        return facingLeft ? 5 : 4;
    }

    private void releaseRemovedSession(MobSimulationSession session, ReleaseReason reason) {
        releases.get(reason).increment();
        boolean selectRealController = reason != ReleaseReason.MONSTER_GONE
                && reason != ReleaseReason.MAP_CHANGE
                && reason != ReleaseReason.OBSERVER_LOSS
                && reason != ReleaseReason.CLIENT_MAP_TRANSITION
                && reason != ReleaseReason.SERVICE_SHUTDOWN;
        try {
            if (session.monster().aggroReleaseAgentPhysics(
                    session.agent(), selectRealController,
                    reason != ReleaseReason.AGGRO_TIMEOUT)
                    && session.monster().getControlAuthority() == MobControlAuthority.CLIENT) {
                handoffs.increment();
            }
        } catch (RuntimeException | LinkageError failure) {
            log.warn("Agent mob-physics release failed after session removal: mob={} reason={}",
                    session.monster().getObjectId(), reason, failure);
            try {
                session.monster().aggroReleaseAgentPhysics(null, false);
            } catch (RuntimeException | LinkageError fallbackFailure) {
                log.error("Could not clear Agent physics authority for mob {}",
                        session.monster().getObjectId(), fallbackFailure);
            }
        }
    }

    public void releaseAll(ReleaseReason reason) {
        for (MobSimulationSession session : registry.snapshot()) {
            release(session, reason);
        }
    }

    public void releaseMap(MapleMap map, ReleaseReason reason) {
        for (MobSimulationSession session : registry.snapshot()) {
            if (session.map() == map) release(session, reason);
        }
    }

    public void releaseAgent(Character agent, ReleaseReason reason) {
        for (MobSimulationSession session : registry.snapshot()) {
            if (session.agent() == agent) release(session, reason);
        }
    }

    public static void releaseAllInstances(ReleaseReason reason) {
        for (MobPhysicsService service : Set.copyOf(INSTANCES)) service.releaseAll(reason);
    }

    public static void releaseMapInstances(MapleMap map, ReleaseReason reason) {
        for (MobPhysicsService service : Set.copyOf(INSTANCES)) service.releaseMap(map, reason);
    }

    public static void releaseDepartedAgents(MapleMap previousMap) {
        for (MobPhysicsService service : Set.copyOf(INSTANCES)) {
            for (MobSimulationSession session : service.registry.snapshot()) {
                if (session.map() == previousMap && session.agent().getMap() != previousMap) {
                    service.release(session, ReleaseReason.AGENT_DEPARTURE);
                }
            }
        }
    }

    public static void releaseMonsterInstances(Monster monster, ReleaseReason reason) {
        for (MobPhysicsService service : Set.copyOf(INSTANCES)) {
            MobSimulationSession session = service.registry.get(monster);
            if (session != null) service.release(session, reason);
        }
    }

    public static String globalStatus() {
        long active = 0, acquired = 0, impactCount = 0, steps = 0, sent = 0, capped = 0;
        long recovered = 0, missing = 0, handoffCount = 0, ticks = 0, total = 0, max = 0;
        long virtualSessions = 0;
        Set<MapleMap> activeMaps = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<MapleMap> virtualMaps = Collections.newSetFromMap(new IdentityHashMap<>());
        EnumMap<ReleaseReason, Long> released = new EnumMap<>(ReleaseReason.class);
        for (ReleaseReason reason : ReleaseReason.values()) released.put(reason, 0L);
        for (MobPhysicsService service : Set.copyOf(INSTANCES)) {
            active += service.registry.size(); acquired += service.acquisitions.sum();
            for (MobSimulationSession session : service.registry.snapshot()) {
                MapleMap map = session.map();
                activeMaps.add(map);
                if (!map.isObservedByPlayer()) {
                    virtualSessions++;
                    virtualMaps.add(map);
                }
            }
            impactCount += service.impacts.sum();
            steps += service.substeps.sum(); sent += service.publications.sum();
            capped += service.cappedCatchUps.sum(); ticks += service.tickCount.sum();
            recovered += service.invalidRecoveries.sum(); missing += service.missingFootholds.sum();
            handoffCount += service.handoffs.sum();
            for (ReleaseReason reason : ReleaseReason.values()) {
                released.merge(reason, service.releases.get(reason).sum(), Long::sum);
            }
            total += service.totalTickNanos.sum(); max = Math.max(max, service.maximumTickNanos.get());
        }
        double averageMs = ticks == 0 ? 0.0 : total / 1_000_000.0 / ticks;
        return "mob physics: active=" + active + " maps=" + activeMaps.size()
                + " virtualStress=" + AgentCombatConfig.cfg.MOB_PHYSICS_VIRTUAL_OBSERVER_STRESS
                + " virtualSessions=" + virtualSessions + " virtualMaps=" + virtualMaps.size()
                + " acquisitions=" + acquired + " impacts=" + impactCount
                + " substeps=" + steps + " publications=" + sent + " capped=" + capped
                + " recoveries=" + recovered + " missingFootholds=" + missing
                + " handoffs=" + handoffCount + " releases=" + released + " avgTickMs="
                + String.format(java.util.Locale.ROOT, "%.3f", averageMs)
                + " maxTickMs=" + String.format(java.util.Locale.ROOT, "%.3f", max / 1_000_000.0);
    }

    @Override
    public void dispose() {
        ScheduledFuture<?> scheduled = task;
        if (scheduled != null) scheduled.cancel(false);
        task = null;
        releaseAll(ReleaseReason.SERVICE_SHUTDOWN);
        INSTANCES.remove(this);
    }
}
