package server.agents.integration.cosmic;

import client.Character;
import config.YamlConfig;
import net.packet.Packet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.capabilities.combat.AgentMobReactionMetrics;
import server.agents.capabilities.combat.MonsterAggroTargetService;
import server.integration.AgentPresence;
import server.life.Monster;
import server.maps.Foothold;
import server.maps.FootholdTree;
import server.maps.MapleMap;
import server.movement.AbsoluteLifeMovement;
import server.movement.LifeMovementFragment;
import tools.PacketCreator;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Narrow protocol fallback for the one case v83 cannot encode: a real client
 * simulating a monster whose logical target is a headless Agent.
 */
final class CosmicMonsterPursuitRuntime {
    private static final Logger log = LoggerFactory.getLogger(CosmicMonsterPursuitRuntime.class);
    static final long TICK_MS = 120L;
    static final long IMPACT_SETTLE_MS = 300L;
    private static final int TARGET_REACHED_X = 38;
    private static final int TARGET_REACHED_Y = 48;
    private static final int MAX_GROUND_Y_DELTA = 28;

    private static final Object lifecycleLock = new Object();
    private static final AtomicLong lastFailureLog = new AtomicLong();
    private static ScheduledFuture<?> task;
    private static LoopScheduler loopScheduler = (action, periodMs) ->
            CosmicSchedulerGateway.INSTANCE.register(action, periodMs);

    private CosmicMonsterPursuitRuntime() {
    }

    static void ensureRunning() {
        synchronized (lifecycleLock) {
            if (task == null || task.isCancelled() || task.isDone()) {
                task = loopScheduler.register(CosmicMonsterPursuitRuntime::tickSafely, TICK_MS);
            }
        }
    }

    static void tickNowForTest(long now) {
        tick(now);
    }

    static void installLoopSchedulerForTest(LoopScheduler scheduler) {
        synchronized (lifecycleLock) {
            stopScheduler();
            loopScheduler = scheduler;
        }
    }

    static void resetForTest() {
        synchronized (lifecycleLock) {
            stopScheduler();
            loopScheduler = (action, periodMs) ->
                    CosmicSchedulerGateway.INSTANCE.register(action, periodMs);
        }
    }

    private static void tickSafely() {
        try {
            tick(System.currentTimeMillis());
        } catch (RuntimeException | LinkageError failure) {
            long now = System.currentTimeMillis();
            long previous = lastFailureLog.get();
            if (now - previous >= 5_000L && lastFailureLog.compareAndSet(previous, now)) {
                log.warn("Agent mob pursuit tick failed; later ticks will continue", failure);
            }
        }
    }

    private static void tick(long now) {
        long timeoutMs = targetTimeoutMs();
        List<MonsterAggroTargetService.PursuitTarget> targets =
                MonsterAggroTargetService.activeTargets(now, timeoutMs);
        restoreExpiredMonsters();

        for (MonsterAggroTargetService.PursuitTarget state : targets) {
            Monster monster = state.monster();
            MapleMap map = monster.getMap();
            if (!featuresEnabled() || !MonsterSimulationControllerResolver.hasObserver(map)) {
                stopProxy(monster);
                MonsterAggroTargetService.clear(monster);
                AgentMobReactionMetrics.pursuitObserverLoss();
                continue;
            }

            Character target = state.target();
            if (!state.agentTarget()) {
                ensurePlayerController(monster, target);
                MonsterAggroTargetService.markReachable(monster, "native-player-controller");
                continue;
            }
            if (!AgentPresence.isAgent(target)) {
                MonsterAggroTargetService.clear(monster);
                restoreNativeController(monster);
                continue;
            }
            if (now < state.pursuitStartAt()) {
                continue;
            }

            // The real client supplied the native impact/knockback first. Remove its
            // control before proxy movement so two movement authorities cannot race.
            stopProxy(monster);
            PursuitStep step = calculateStep(monster, target);
            if (step == null) {
                if (MonsterAggroTargetService.markUnreachable(
                        monster, now, "unreachable-map-geometry")) {
                    AgentMobReactionMetrics.pursuitUnreachable();
                }
                continue;
            }
            if (!step.moved()) {
                MonsterAggroTargetService.markReachable(monster, "target-in-range");
                continue;
            }
            applyStep(monster, step);
            MonsterAggroTargetService.markReachable(monster, step.flying()
                    ? "server-proxy-fly" : "server-proxy-ground");
            AgentMobReactionMetrics.pursuitMove();
        }

        if (!MonsterAggroTargetService.hasTargets()) {
            stopScheduler();
        }
    }

    private static PursuitStep calculateStep(Monster monster, Character target) {
        if (!monster.isMobile() || monster.getStats().getFixedStance() != 0) {
            return null;
        }
        Point from = new Point(monster.getPosition());
        Point targetPos = target.getPosition();
        int dx = targetPos.x - from.x;
        int dy = targetPos.y - from.y;
        if (Math.abs(dx) <= TARGET_REACHED_X && Math.abs(dy) <= TARGET_REACHED_Y) {
            return new PursuitStep(from, from, monster.getStance(), 0, false,
                    monster.getStats().isFlying());
        }

        int pixelsPerSecond = Math.max(20, Math.min(180,
                100 + monster.getStats().getSpeed()));
        int maxStep = Math.max(2, (int) Math.ceil(pixelsPerSecond * TICK_MS / 1000.0));
        if (monster.getStats().isFlying()) {
            double distance = Math.max(1.0, Math.hypot(dx, dy));
            Point destination = new Point(
                    from.x + (int) Math.round(dx / distance * Math.min(maxStep, distance)),
                    from.y + (int) Math.round(dy / distance * Math.min(maxStep, distance)));
            destination = constrainToMap(monster.getMap(), destination);
            int stance = destination.x < from.x ? 3 : 2;
            return new PursuitStep(from, destination, stance, 0, true, true);
        }

        int direction = Integer.signum(dx);
        if (direction == 0) {
            return null;
        }
        int nextX = from.x + direction * Math.min(maxStep, Math.abs(dx));
        Point constrained = constrainToMap(monster.getMap(), new Point(nextX, from.y));
        if (crossesWall(monster.getMap().getFootholds(), from, constrained)) {
            return null;
        }
        Foothold foothold = monster.getMap().getFootholds() == null ? null
                : monster.getMap().getFootholds().findBelow(new Point(constrained.x, from.y - 16));
        if (foothold == null || foothold.isWall()) {
            return null;
        }
        int footingY = footingAt(foothold, constrained.x) - 1;
        if (Math.abs(footingY - from.y) > MAX_GROUND_Y_DELTA) {
            return null;
        }
        Point destination = new Point(constrained.x, footingY);
        int stance = direction < 0 ? 3 : 2;
        return new PursuitStep(from, destination, stance, foothold.getId(), true, false);
    }

    private static void applyStep(Monster monster, PursuitStep step) {
        int velocityX = (int) Math.round(
                (step.to().x - step.from().x) * 1000.0 / TICK_MS);
        int velocityY = (int) Math.round(
                (step.to().y - step.from().y) * 1000.0 / TICK_MS);
        AbsoluteLifeMovement movement = new AbsoluteLifeMovement(
                step.flying() ? 17 : 0,
                new Point(step.to().x, step.to().y + 2),
                (int) TICK_MS,
                step.stance());
        movement.setPixelsPerSecond(new Point(velocityX, velocityY));
        movement.setFh(step.footholdId());
        Packet packet = PacketCreator.moveMonster(monster.getObjectId(), step.from(),
                List.<LifeMovementFragment>of(movement));

        monster.setStance(step.stance());
        monster.getMap().broadcastMessage(packet, step.from());
        monster.getMap().moveMonster(monster, step.to());
    }

    private static Point constrainToMap(MapleMap map, Point point) {
        Rectangle bounds = map.getMapArea();
        if (bounds.width <= 0 || bounds.height <= 0) {
            return point;
        }
        int x = Math.max(bounds.x + 1, Math.min(bounds.x + bounds.width - 1, point.x));
        int y = Math.max(bounds.y + 1, Math.min(bounds.y + bounds.height - 1, point.y));
        return new Point(x, y);
    }

    private static boolean crossesWall(FootholdTree tree, Point from, Point to) {
        if (tree == null || from.x == to.x) {
            return false;
        }
        int minX = Math.min(from.x, to.x);
        int maxX = Math.max(from.x, to.x);
        for (Foothold foothold : tree.getAllFootholds()) {
            if (!foothold.isWall() || foothold.getX1() < minX || foothold.getX1() > maxX) {
                continue;
            }
            int minY = Math.min(foothold.getY1(), foothold.getY2());
            int maxY = Math.max(foothold.getY1(), foothold.getY2());
            if (from.y >= minY && from.y <= maxY) {
                return true;
            }
        }
        return false;
    }

    private static int footingAt(Foothold foothold, int x) {
        if (foothold.getX1() == foothold.getX2() || foothold.getY1() == foothold.getY2()) {
            return foothold.getY1();
        }
        double ratio = (double) (x - foothold.getX1())
                / (double) (foothold.getX2() - foothold.getX1());
        return (int) Math.round(foothold.getY1()
                + ratio * (foothold.getY2() - foothold.getY1()));
    }

    private static void ensurePlayerController(Monster monster, Character player) {
        if (!MonsterSimulationControllerResolver.isEligible(player, monster.getMap())) {
            return;
        }
        if (monster.getController() == player) {
            monster.aggroAutoAggroUpdate(player);
        } else {
            monster.aggroSwitchController(player, true);
        }
        MonsterAggroTargetService.recordController(monster, player);
    }

    private static void stopProxy(Monster monster) {
        if (monster.getController() != null) {
            monster.aggroRemoveController();
            MonsterAggroTargetService.recordController(monster, null);
        }
    }

    private static void restoreExpiredMonsters() {
        for (Monster monster : MonsterAggroTargetService.drainExpiredTargets()) {
            restoreNativeController(monster);
        }
    }

    private static void restoreNativeController(Monster monster) {
        if (monster != null && monster.isAlive() && monster.getMap() != null
                && MonsterSimulationControllerResolver.hasObserver(monster.getMap())) {
            monster.aggroUpdateController();
        }
    }

    private static void stopScheduler() {
        synchronized (lifecycleLock) {
            if (task != null) {
                task.cancel(false);
                task = null;
            }
        }
    }

    private static boolean featuresEnabled() {
        return YamlConfig.config.agents != null && YamlConfig.config.agents.combat != null
                && YamlConfig.config.agents.combat.lastHitAggro != null
                && YamlConfig.config.agents.combat.lastHitAggro.enabled;
    }

    private static long targetTimeoutMs() {
        return YamlConfig.config.agents == null || YamlConfig.config.agents.combat == null
                || YamlConfig.config.agents.combat.lastHitAggro == null
                ? 10_000L : YamlConfig.config.agents.combat.lastHitAggro.targetTimeoutMs;
    }

    record PursuitStep(Point from, Point to, int stance, int footholdId,
                       boolean moved, boolean flying) {
    }

    @FunctionalInterface
    interface LoopScheduler {
        ScheduledFuture<?> register(Runnable action, long periodMs);
    }
}
