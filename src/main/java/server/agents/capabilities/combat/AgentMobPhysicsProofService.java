package server.agents.capabilities.combat;

import client.BotClient;
import client.Character;
import net.packet.Packet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.runtime.AgentSchedulerRuntime;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Transport-first proof of concept for Agent-owned monster movement.
 *
 * <p>This deliberately is not the Journey physics port. It proves that a BotClient can hold
 * logical controller identity while the server publishes authoritative knockback and simple
 * grounded aggro movement through ordinary MOVE_MONSTER packets.</p>
 */
public final class AgentMobPhysicsProofService {
    private static final Logger log = LoggerFactory.getLogger(AgentMobPhysicsProofService.class);
    private static final int SERVICE_TICK_MS = 50;
    private static final int CLIENT_MOB_Y_OFFSET = 2;
    private static final int MAX_GROUND_Y_DELTA = 28;
    private static final int MOVE_FACING_RIGHT = 2;
    private static final int MOVE_FACING_LEFT = 3;
    private static final int STAND_FACING_RIGHT = 4;
    private static final int STAND_FACING_LEFT = 5;
    private static final ConcurrentMap<Monster, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final AtomicBoolean TICKER_STARTED = new AtomicBoolean();

    private AgentMobPhysicsProofService() {
    }

    public static void acceptedHit(Character attacker, Monster monster,
                                   int appliedDamage, long reactionDelayMs) {
        acceptedHit(attacker, monster, appliedDamage, reactionDelayMs,
                System.currentTimeMillis(), true);
    }

    static void acceptedHit(Character attacker, Monster monster, int appliedDamage,
                            long reactionDelayMs, long nowMs, boolean startTicker) {
        if (!canAcquire(attacker, monster, appliedDamage)) {
            return;
        }

        Point attackerPosition = attacker.getPosition();
        Point monsterPosition = monster.getPosition();
        if (attackerPosition == null || monsterPosition == null) {
            return;
        }
        int direction = directionAway(attacker, attackerPosition, monsterPosition);

        synchronized (monster) {
            Session current = SESSIONS.get(monster);
            if (current == null) {
                if (!monster.aggroAcquireAgentPhysicsController(attacker)) {
                    return;
                }
                current = new Session(monster, monster.getMap(), monster.getObjectId(), attacker);
                SESSIONS.put(monster, current);
            } else if (current.attacker != attacker) {
                if (!monster.aggroAcquireAgentPhysicsController(attacker)) {
                    return;
                }
                current.attacker = attacker;
            }
            current.impactDirection = direction;
            current.impactAtMs = nowMs + Math.max(0L, reactionDelayMs);
            current.impactPending = true;
            current.moving = false;
        }

        if (startTicker && TICKER_STARTED.compareAndSet(false, true)) {
            AgentSchedulerRuntime.register(AgentMobPhysicsProofService::tick, SERVICE_TICK_MS);
        }
    }

    public static void releaseMap(MapleMap map) {
        if (map == null) {
            return;
        }
        SESSIONS.values().stream()
                .filter(session -> session.map == map)
                .toList()
                .forEach(AgentMobPhysicsProofService::release);
    }

    public static void releaseInvalidSessions(MapleMap map) {
        if (map == null) {
            return;
        }
        SESSIONS.values().stream()
                .filter(session -> session.map == map && !isSessionValid(session))
                .toList()
                .forEach(AgentMobPhysicsProofService::release);
    }

    private static void tick() {
        tick(System.currentTimeMillis());
    }

    static void tick(long nowMs) {
        for (Session session : SESSIONS.values()) {
            try {
                tickSession(session, nowMs);
            } catch (RuntimeException | LinkageError failure) {
                log.warn("Agent mob physics proof released OID {} after a tick failure",
                        session.monsterOid, failure);
                release(session);
            }
        }
    }

    private static void tickSession(Session session, long nowMs) {
        if (!isSessionValid(session)) {
            release(session);
            return;
        }

        synchronized (session.monster) {
            if (SESSIONS.get(session.monster) != session || !isSessionValid(session)) {
                return;
            }
            if (session.impactPending && nowMs >= session.impactAtMs) {
                session.impactPending = false;
                int duration = positive(AgentCombatConfig.cfg.MOB_PHYSICS_POC_KNOCKBACK_DURATION_MS);
                int distance = nonNegative(AgentCombatConfig.cfg.MOB_PHYSICS_POC_KNOCKBACK_DISTANCE_X);
                Point from = session.monster.getPosition();
                Point to = groundedEndpoint(session.monster,
                        from.x + session.impactDirection * distance);
                int stance = session.impactDirection > 0
                        ? STAND_FACING_LEFT : STAND_FACING_RIGHT;
                publish(session, from, to, stance, duration);
                session.knockbackUntilMs = nowMs + duration;
                session.nextPublishAtMs = session.knockbackUntilMs;
                return;
            }
            if (session.impactPending || nowMs < session.knockbackUntilMs
                    || nowMs < session.nextPublishAtMs) {
                return;
            }
            publishAggroStep(session, nowMs);
        }
    }

    private static void publishAggroStep(Session session, long nowMs) {
        int interval = Math.max(SERVICE_TICK_MS,
                positive(AgentCombatConfig.cfg.MOB_PHYSICS_POC_PUBLISH_INTERVAL_MS));
        session.nextPublishAtMs = nowMs + interval;

        Point from = session.monster.getPosition();
        Point target = session.attacker.getPosition();
        if (from == null || target == null) {
            return;
        }
        int dx = target.x - from.x;
        int distance = Math.abs(dx);
        int stop = nonNegative(AgentCombatConfig.cfg.MOB_PHYSICS_POC_STOP_DISTANCE_X);
        int resume = Math.max(stop,
                nonNegative(AgentCombatConfig.cfg.MOB_PHYSICS_POC_RESUME_DISTANCE_X));
        if (session.moving ? distance <= stop : distance < resume) {
            if (session.moving) {
                int stance = dx < 0 ? STAND_FACING_LEFT : STAND_FACING_RIGHT;
                publish(session, from, from, stance, 1);
                session.moving = false;
            }
            return;
        }

        int direction = dx < 0 ? -1 : 1;
        int step = Math.min(nonNegative(AgentCombatConfig.cfg.MOB_PHYSICS_POC_AGGRO_STEP_X),
                Math.max(0, distance - stop));
        if (step == 0) {
            return;
        }
        Point to = groundedEndpoint(session.monster, from.x + direction * step);
        if (to.equals(from)) {
            session.moving = false;
            return;
        }
        int stance = direction < 0 ? MOVE_FACING_LEFT : MOVE_FACING_RIGHT;
        publish(session, from, to, stance, interval);
        session.moving = true;
    }

    private static void publish(Session session, Point from, Point to,
                                int stance, int durationMs) {
        Point clientEndpoint = new Point(to.x, to.y + CLIENT_MOB_Y_OFFSET);
        AbsoluteLifeMovement movement = new AbsoluteLifeMovement(
                0, clientEndpoint, Math.min(Short.MAX_VALUE, positive(durationMs)), stance);
        movement.setPixelsPerSecond(new Point(0, 0));
        movement.setFh(session.monster.getFh());
        Packet packet = PacketCreator.moveMonster(session.monsterOid, -1, from,
                List.<LifeMovementFragment>of(movement));
        session.monster.setStance(stance);
        session.map.broadcastMessage(packet, from);
        session.map.moveMonster(session.monster, to);
    }

    private static Point groundedEndpoint(Monster monster, int requestedX) {
        MapleMap map = monster.getMap();
        Point from = monster.getPosition();
        if (map == null || from == null) {
            return from;
        }
        Rectangle bounds = map.getMapArea();
        int x = requestedX;
        if (bounds != null && bounds.width > 0) {
            x = Math.max(bounds.x + 1, Math.min(bounds.x + bounds.width - 1, x));
        }
        FootholdTree footholds = map.getFootholds();
        if (footholds == null || crossesWall(footholds, from, x)) {
            return from;
        }
        Foothold foothold = footholds.findBelow(new Point(x, from.y - 16));
        if (foothold == null || foothold.isWall()) {
            return from;
        }
        int y = preciseFootingAt(foothold, x) - 1;
        if (Math.abs(y - from.y) > MAX_GROUND_Y_DELTA) {
            return from;
        }
        return new Point(x, y);
    }

    private static boolean crossesWall(FootholdTree footholds, Point from, int toX) {
        if (from.x == toX) {
            return false;
        }
        Point left = new Point(Math.min(from.x, toX), from.y);
        Point right = new Point(Math.max(from.x, toX), from.y);
        return footholds.findWall(left, right) != null;
    }

    private static int preciseFootingAt(Foothold foothold, int x) {
        if (foothold.getX1() == foothold.getX2()) {
            return foothold.getY1();
        }
        double ratio = (x - foothold.getX1())
                / (double) (foothold.getX2() - foothold.getX1());
        return (int) Math.round(foothold.getY1()
                + ratio * (foothold.getY2() - foothold.getY1()));
    }

    private static boolean canAcquire(Character attacker, Monster monster, int damage) {
        if (!AgentCombatConfig.cfg.MOB_PHYSICS_POC_ENABLED
                || attacker == null || monster == null || damage <= 0
                || !(attacker.getClient() instanceof BotClient)
                || !attacker.isAlive() || !monster.isAlive() || !monster.isMobile()) {
            return false;
        }
        MapleMap map = monster.getMap();
        return map != null && attacker.getMap() == map && map.isObservedByPlayer()
                && monster.getStats().getFixedStance() == 0
                && !monster.getStats().isFlying();
    }

    private static boolean isSessionValid(Session session) {
        return AgentCombatConfig.cfg.MOB_PHYSICS_POC_ENABLED
                && session.monster.getMap() == session.map
                && session.attacker.getMap() == session.map
                && session.map.getMonsterByOid(session.monsterOid) == session.monster
                && session.monster.getController() == session.attacker
                && session.attacker.isAlive() && session.monster.isAlive()
                && session.map.isObservedByPlayer();
    }

    private static void release(Session session) {
        if (session == null) {
            return;
        }
        synchronized (session.monster) {
            if (SESSIONS.remove(session.monster, session)) {
                session.monster.aggroReleaseAgentPhysicsController(session.attacker);
            }
        }
    }

    private static int directionAway(Character attacker, Point attackerPosition,
                                     Point monsterPosition) {
        int direction = Integer.compare(monsterPosition.x, attackerPosition.x);
        return direction != 0 ? direction : (attacker.isFacingLeft() ? -1 : 1);
    }

    private static int positive(int value) {
        return Math.max(1, value);
    }

    private static int nonNegative(int value) {
        return Math.max(0, value);
    }

    static void clearForTest() {
        SESSIONS.clear();
    }

    private static final class Session {
        private final Monster monster;
        private final MapleMap map;
        private final int monsterOid;
        private volatile Character attacker;
        private int impactDirection;
        private long impactAtMs;
        private long knockbackUntilMs;
        private long nextPublishAtMs;
        private boolean impactPending;
        private boolean moving;

        private Session(Monster monster, MapleMap map, int monsterOid, Character attacker) {
            this.monster = monster;
            this.map = map;
            this.monsterOid = monsterOid;
            this.attacker = attacker;
        }
    }
}
