package server.agents.capabilities.combat;

import client.BotClient;
import client.Character;
import net.packet.Packet;
import server.agents.runtime.AgentSchedulerRuntime;
import server.agents.capabilities.mobcontrol.AgentMobReactionMode;
import server.life.Monster;
import server.maps.Foothold;
import server.maps.FootholdTree;
import server.maps.MapleMap;
import server.movement.AbsoluteLifeMovement;
import server.movement.LifeMovementFragment;
import tools.PacketCreator;
import tools.Pair;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Supplies the mob hit reaction a headless Agent cannot produce through MOVE_LIFE. */
public final class AgentSyntheticMobReactionService {
    private static final int MAX_GROUND_Y_DELTA = config.AgentTuning.intValue("server.agents.capabilities.combat.AgentSyntheticMobReactionService.MAX_GROUND_Y_DELTA");
    private static final int MOVEMENT_SETTLE_MS = config.AgentTuning.intValue("server.agents.capabilities.combat.AgentSyntheticMobReactionService.MOVEMENT_SETTLE_MS");
    private static final int ASSIGNMENT_HOLD_GRACE_MS = config.AgentTuning.intValue("server.agents.capabilities.combat.AgentSyntheticMobReactionService.ASSIGNMENT_HOLD_GRACE_MS");
    private static final int CLIENT_MOB_Y_OFFSET = config.AgentTuning.intValue("server.agents.capabilities.combat.AgentSyntheticMobReactionService.CLIENT_MOB_Y_OFFSET");
    // Header activity HIT is 8/9. Movement stance remains STAND (4/5) so the
    // one-shot flinch does not seed continued client-side hit physics.
    private static final int HIT_FACING_RIGHT = 8;
    private static final int HIT_FACING_LEFT = 9;
    private static final int STAND_FACING_RIGHT = 4;
    private static final int STAND_FACING_LEFT = 5;
    private static final ConcurrentMap<Monster, PendingReaction> PENDING_REACTIONS =
            new ConcurrentHashMap<>();

    private AgentSyntheticMobReactionService() {
    }

    public static void acceptedHit(Character attacker, Monster monster,
                                   int appliedDamage, long reactionDelayMs) {
        acceptedHit(attacker, monster, appliedDamage, reactionDelayMs,
                (action, delayMs) -> AgentSchedulerRuntime.schedule(action, delayMs));
    }

    static void acceptedHit(Character attacker, Monster monster,
                            int appliedDamage, long reactionDelayMs,
                            DelayedActionScheduler scheduler) {
        if (!canReact(attacker, monster, appliedDamage)) {
            return;
        }

        MapleMap map = monster.getMap();
        Point attackerPosition = attacker.getPosition();
        Point monsterPosition = monster.getPosition();
        if (attackerPosition == null || monsterPosition == null) {
            return;
        }
        int direction = acceptedHitDirection(attacker, attackerPosition, monsterPosition);
        ReactionTuning tuning = currentTuning();
        PendingReaction pending = new PendingReaction(
                map, monster, monster.getObjectId(), attacker, direction, tuning, scheduler);

        monster.lockMonster();
        try {
            if (!canReact(attacker, monster, appliedDamage)
                    || map.getMonsterByOid(pending.monsterOid) != monster
                    || PENDING_REACTIONS.putIfAbsent(monster, pending) != null) {
                return;
            }

            boolean scheduled = false;
            boolean controllerRemoved = false;
            try {
                Pair<Character, Boolean> previousController =
                        monster.aggroSuspendControllerForSyntheticReaction(
                                Math.max(0L, reactionDelayMs) + assignmentHoldMs(tuning));
                if (previousController == null) {
                    return;
                }
                controllerRemoved = true;
                pending.hadAggro = Boolean.TRUE.equals(previousController.getRight());
                scheduler.schedule(() -> applyReaction(pending), Math.max(0L, reactionDelayMs));
                scheduled = true;
            } finally {
                if (!scheduled && PENDING_REACTIONS.remove(monster, pending)
                        && controllerRemoved) {
                    monster.aggroReleaseControllerAssignmentHold();
                    monster.aggroUpdateController(pending.hadAggro);
                }
            }
        } finally {
            monster.unlockMonster();
        }
    }

    private static boolean canReact(Character attacker, Monster monster, int appliedDamage) {
        if (AgentCombatConfig.cfg.AGENT_MOB_REACTION_MODE != AgentMobReactionMode.SYNTHETIC
                || attacker == null || monster == null || appliedDamage <= 0
                || !(attacker.getClient() instanceof BotClient)
                || !monster.isAlive() || !monster.isMobile()) {
            return false;
        }
        MapleMap map = monster.getMap();
        return map != null && attacker.getMap() == map && map.isObservedByPlayer()
                && monster.getStats().getFixedStance() == 0;
    }

    private static void applyReaction(PendingReaction pending) {
        boolean movementApplied = false;
        try {
            pending.monster.lockMonster();
            try {
                if (PENDING_REACTIONS.get(pending.monster) == pending
                        && canApplyImpact(pending)) {
                    ImpactStep step = calculateImpact(
                            pending.monster, pending.direction, pending.tuning);
                    if (step == null || !canApplyImpact(pending)) {
                        Pair<Character, Boolean> interimController =
                                pending.monster.aggroRemoveControllerIfHeadless();
                        recordInterimController(pending, interimController);
                    } else {
                        int controllerHoldMs = pending.tuning.controllerHoldMs();
                        Point clientEndpoint = new Point(
                                step.to().x, step.to().y + CLIENT_MOB_Y_OFFSET);
                        AbsoluteLifeMovement movement = new AbsoluteLifeMovement(
                                0, clientEndpoint, pending.tuning.movementDurationMs(),
                                step.movementState());
                        movement.setPixelsPerSecond(new Point(0, 0));
                        movement.setFh(pending.monster.getFh());
                        Packet packet = PacketCreator.moveMonster(
                                pending.monsterOid, step.hitState(), step.from(),
                                List.<LifeMovementFragment>of(movement));

                        Pair<Character, Boolean> interimController =
                                pending.monster.aggroCommitIfHeadless(
                                        assignmentHoldMs(pending.tuning), () -> {
                                    pending.monster.setStance(step.movementState());
                                    pending.map.broadcastMessage(packet, step.from());
                                    pending.map.moveMonster(pending.monster, step.to());
                                });
                        if (recordInterimController(pending, interimController)) {
                            movementApplied = true;
                            pending.controllerHoldMs = controllerHoldMs;
                        }
                    }
                }
            } finally {
                pending.monster.unlockMonster();
            }
        } catch (RuntimeException | LinkageError failure) {
            completeReaction(pending);
            throw failure;
        }

        if (!movementApplied) {
            completeReaction(pending);
            return;
        }
        try {
            pending.scheduler.schedule(() -> completeReaction(pending),
                    pending.controllerHoldMs);
        } catch (RuntimeException | LinkageError failure) {
            completeReaction(pending);
            throw failure;
        }
    }

    private static boolean canApplyImpact(PendingReaction pending) {
        Monster monster = pending.monster;
        return AgentCombatConfig.cfg.AGENT_MOB_REACTION_MODE == AgentMobReactionMode.SYNTHETIC
                && monster.getMap() == pending.map
                && pending.attacker.getMap() == pending.map
                && pending.map.getMonsterByOid(pending.monsterOid) == monster
                && monster.isAlive() && monster.isMobile()
                && monster.getStats().getFixedStance() == 0
                && pending.map.isObservedByPlayer();
    }

    private static int acceptedHitDirection(Character attacker,
                                            Point attackerPosition,
                                            Point monsterPosition) {
        int direction = Integer.compare(monsterPosition.x, attackerPosition.x);
        return direction != 0 ? direction : (attacker.isFacingLeft() ? -1 : 1);
    }

    private static ReactionTuning currentTuning() {
        int distance = Math.max(0, Math.min(Short.MAX_VALUE,
                AgentCombatConfig.cfg.SYNTHETIC_MOB_KNOCKBACK_DISTANCE_X));
        int duration = Math.max(1, Math.min(Short.MAX_VALUE,
                AgentCombatConfig.cfg.SYNTHETIC_MOB_KNOCKBACK_DURATION_MS));
        int configuredHold = Math.max(0,
                AgentCombatConfig.cfg.SYNTHETIC_MOB_CONTROL_HOLD_MS);
        int controllerHold = Math.max(configuredHold, duration + MOVEMENT_SETTLE_MS);
        return new ReactionTuning(distance, duration, controllerHold);
    }

    private static long assignmentHoldMs(ReactionTuning tuning) {
        return (long) tuning.controllerHoldMs() + ASSIGNMENT_HOLD_GRACE_MS;
    }

    private static boolean recordInterimController(
            PendingReaction pending, Pair<Character, Boolean> controller) {
        if (controller == null) {
            return false;
        }
        if (Boolean.TRUE.equals(controller.getRight())) {
            pending.hadAggro = true;
        }
        return true;
    }

    private static void completeReaction(PendingReaction pending) {
        Monster monster = pending.monster;
        monster.lockMonster();
        try {
            if (!PENDING_REACTIONS.remove(monster, pending)) {
                return;
            }
            monster.aggroReleaseControllerAssignmentHold();
            if (monster.getMap() != pending.map
                    || pending.map.getMonsterByOid(pending.monsterOid) != monster
                    || !monster.isAlive() || !pending.map.isObservedByPlayer()) {
                return;
            }

            Character controller = monster.getController();
            if (controller == null) {
                monster.aggroUpdateController(pending.hadAggro);
            } else if (pending.hadAggro) {
                monster.aggroAutoAggroUpdate(controller);
            }
        } finally {
            monster.unlockMonster();
        }
    }

    /** Immediately retires all pending one-shot reactions during a live mode switch. */
    public static void releaseAll() {
        for (PendingReaction pending : List.copyOf(PENDING_REACTIONS.values())) {
            completeReaction(pending);
        }
    }

    private static ImpactStep calculateImpact(Monster monster, int direction,
                                              ReactionTuning tuning) {
        MapleMap map = monster.getMap();
        Point from = monster.getPosition();
        if (map == null || from == null) {
            return null;
        }
        int normalizedDirection = direction < 0 ? -1 : 1;
        int hitState = normalizedDirection > 0 ? HIT_FACING_LEFT : HIT_FACING_RIGHT;
        int movementState = normalizedDirection > 0
                ? STAND_FACING_LEFT : STAND_FACING_RIGHT;
        Point constrained = constrainToMap(map, new Point(
                from.x + normalizedDirection * tuning.knockbackDistanceX(), from.y));
        if (constrained.equals(from)) {
            return new ImpactStep(from, from, hitState, movementState);
        }

        if (monster.getStats().isFlying()) {
            return new ImpactStep(from, constrained, hitState, movementState);
        }

        FootholdTree footholds = map.getFootholds();
        if (footholds == null || crossesWall(footholds, from, constrained)) {
            return new ImpactStep(from, from, hitState, movementState);
        }
        Foothold foothold = footholds.findBelow(new Point(constrained.x, from.y - 16));
        if (foothold == null || foothold.isWall()) {
            return new ImpactStep(from, from, hitState, movementState);
        }
        int footingY = footingAt(foothold, constrained.x) - 1;
        if (Math.abs(footingY - from.y) > MAX_GROUND_Y_DELTA) {
            return new ImpactStep(from, from, hitState, movementState);
        }
        return new ImpactStep(from, new Point(constrained.x, footingY),
                hitState, movementState);
    }

    private static Point constrainToMap(MapleMap map, Point point) {
        Rectangle bounds = map.getMapArea();
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
            return point;
        }
        int x = Math.max(bounds.x + 1,
                Math.min(bounds.x + bounds.width - 1, point.x));
        int y = Math.max(bounds.y + 1,
                Math.min(bounds.y + bounds.height - 1, point.y));
        return new Point(x, y);
    }

    private static boolean crossesWall(FootholdTree tree, Point from, Point to) {
        Point left = from.x <= to.x ? from : to;
        Point right = from.x <= to.x ? to : from;
        return tree.findWall(left, right) != null;
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

    private record ImpactStep(Point from, Point to, int hitState, int movementState) {
    }

    private record ReactionTuning(int knockbackDistanceX,
                                  int movementDurationMs,
                                  int controllerHoldMs) {
    }

    private static final class PendingReaction {
        private final MapleMap map;
        private final Monster monster;
        private final int monsterOid;
        private final Character attacker;
        private final int direction;
        private final ReactionTuning tuning;
        private final DelayedActionScheduler scheduler;
        private volatile boolean hadAggro;
        private volatile int controllerHoldMs;

        private PendingReaction(MapleMap map, Monster monster, int monsterOid,
                                Character attacker, int direction,
                                ReactionTuning tuning,
                                DelayedActionScheduler scheduler) {
            this.map = map;
            this.monster = monster;
            this.monsterOid = monsterOid;
            this.attacker = attacker;
            this.direction = direction;
            this.tuning = tuning;
            this.scheduler = scheduler;
        }
    }

    @FunctionalInterface
    interface DelayedActionScheduler {
        void schedule(Runnable action, long delayMs);
    }
}
