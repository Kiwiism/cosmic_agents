package server.agents.capabilities.combat;

import client.Character;
import server.life.Monster;
import server.maps.MapleMap;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Transport-neutral latest-attacker state. Monster identity, not OID alone, is
 * retained so a delayed reaction cannot affect a replacement spawn.
 */
public final class MonsterAggroTargetService {
    private static final Map<Monster, TargetState> targets =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Monster, PreparedReactionState> preparedReactions =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final ConcurrentLinkedQueue<WeakReference<Monster>> expiredTargets =
            new ConcurrentLinkedQueue<>();

    private MonsterAggroTargetService() {
    }

    public static void record(Monster monster, Character attacker, Character simulationController,
                              boolean agentTarget, int damage, int threshold, String reaction,
                              long now, long reactionDelayMs) {
        if (monster == null || attacker == null || damage <= 0 || monster.getMap() == null) {
            return;
        }
        removeExpiredReference(monster);
        targets.put(monster, new TargetState(
                new WeakReference<>(monster.getMap()),
                new WeakReference<>(attacker),
                new WeakReference<>(simulationController),
                agentTarget,
                now,
                damage,
                threshold,
                reaction,
                now + Math.max(0L, reactionDelayMs),
                0L,
                "pending"));
        AgentMobReactionMetrics.targetChange();
    }

    static void record(Monster monster, Character attacker, Character simulationController,
                       int damage, int threshold, String reaction, long now) {
        record(monster, attacker, simulationController, false, damage, threshold,
                reaction, now, 0L);
    }

    public static void prepareReaction(Monster monster, Character attacker, int damage,
                                       int threshold, String reaction, long hitDelayMs) {
        if (monster == null || attacker == null || damage <= 0 || monster.getMap() == null) {
            return;
        }
        preparedReactions.put(monster, new PreparedReactionState(
                new WeakReference<>(monster.getMap()),
                new WeakReference<>(attacker), damage, threshold, reaction,
                Math.max(0L, hitDelayMs)));
    }

    static void prepareReaction(Monster monster, Character attacker, int damage,
                                int threshold, String reaction) {
        prepareReaction(monster, attacker, damage, threshold, reaction, 0L);
    }

    public static PreparedReaction consumePreparedReaction(Monster monster, Character attacker) {
        synchronized (preparedReactions) {
            PreparedReactionState state = preparedReactions.get(monster);
            if (state == null || state.attacker.get() != attacker
                    || state.map.get() != monster.getMap()
                    || !isCurrentSpawn(monster, state.map.get())) {
                if (state != null) {
                    preparedReactions.remove(monster);
                    AgentMobReactionMetrics.staleReaction();
                }
                return null;
            }
            preparedReactions.remove(monster);
            return new PreparedReaction(state.damage, state.threshold, state.reaction,
                    state.hitDelayMs);
        }
    }

    public static List<PursuitTarget> activeTargets(long now, long unreachableTimeoutMs) {
        List<PursuitTarget> result = new ArrayList<>();
        synchronized (targets) {
            var iterator = targets.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                Monster monster = entry.getKey();
                TargetState state = entry.getValue();
                Character target = state.target.get();
                MapleMap map = state.map.get();
                if (!isValidTarget(monster, target, map)
                        || unreachableExpired(state, now, unreachableTimeoutMs)) {
                    iterator.remove();
                    expiredTargets.offer(new WeakReference<>(monster));
                    AgentMobReactionMetrics.staleTarget();
                    continue;
                }
                result.add(new PursuitTarget(monster, target, state.controller.get(),
                        state.agentTarget, state.changedAt, state.damage, state.threshold,
                        state.reaction, state.pursuitStartAt, state.unreachableSince,
                        state.latestMovement));
            }
        }
        return result;
    }

    public static Snapshot inspect(Monster monster, long now, long unreachableTimeoutMs) {
        synchronized (targets) {
            TargetState state = targets.get(monster);
            if (state == null) {
                return Snapshot.empty();
            }
            Character target = state.target.get();
            Character controller = state.controller.get();
            MapleMap map = state.map.get();
            if (!isValidTarget(monster, target, map)
                    || unreachableExpired(state, now, unreachableTimeoutMs)) {
                targets.remove(monster);
                expiredTargets.offer(new WeakReference<>(monster));
                AgentMobReactionMetrics.staleTarget();
                return Snapshot.empty();
            }
            return new Snapshot(target.getId(), target.getName(), state.agentTarget,
                    controller == null ? 0 : controller.getId(),
                    controller == null ? "none" : controller.getName(),
                    state.changedAt, state.damage, state.threshold, state.reaction,
                    state.pursuitStartAt, state.unreachableSince, state.latestMovement);
        }
    }

    public static void markReachable(Monster monster, String movement) {
        synchronized (targets) {
            TargetState state = targets.get(monster);
            if (state != null) {
                state.unreachableSince = 0L;
                state.latestMovement = movement;
            }
        }
    }

    public static boolean markUnreachable(Monster monster, long now, String reason) {
        synchronized (targets) {
            TargetState state = targets.get(monster);
            if (state != null) {
                boolean newlyUnreachable = state.unreachableSince == 0L;
                if (state.unreachableSince == 0L) {
                    state.unreachableSince = now;
                }
                state.latestMovement = reason;
                return newlyUnreachable;
            }
        }
        return false;
    }

    public static void recordController(Monster monster, Character controller) {
        synchronized (targets) {
            TargetState state = targets.get(monster);
            if (state != null) {
                state.controller = new WeakReference<>(controller);
            }
        }
    }

    public static void recordControllerMovement(Monster monster, int movementCommand,
                                                long now) {
        synchronized (targets) {
            TargetState state = targets.get(monster);
            if (state == null) {
                return;
            }
            state.latestMovement = "client-command-" + movementCommand;
            if (movementCommand == 2 && state.reaction.contains("knockback")
                    && now - state.changedAt <= 1_500L) {
                AgentMobReactionMetrics.knockbackApplied();
            }
        }
    }

    public static List<Monster> drainExpiredTargets() {
        List<Monster> expired = new ArrayList<>();
        WeakReference<Monster> reference;
        while ((reference = expiredTargets.poll()) != null) {
            Monster monster = reference.get();
            if (monster != null) {
                expired.add(monster);
            }
        }
        return expired;
    }

    public static boolean hasTargets() {
        return !targets.isEmpty();
    }

    public static boolean usesServerPursuit(Monster monster, long now) {
        synchronized (targets) {
            TargetState state = targets.get(monster);
            if (state == null || !state.agentTarget || now < state.pursuitStartAt) {
                return false;
            }
            return isValidTarget(monster, state.target.get(), state.map.get());
        }
    }

    public static void clear(Monster monster) {
        targets.remove(monster);
        preparedReactions.remove(monster);
        removeExpiredReference(monster);
    }

    private static void removeExpiredReference(Monster monster) {
        expiredTargets.removeIf(reference -> {
            Monster queued = reference.get();
            return queued == null || queued == monster;
        });
    }

    private static boolean isCurrentSpawn(Monster monster, MapleMap map) {
        return monster != null && map != null
                && map.getMonsterByOid(monster.getObjectId()) == monster;
    }

    private static boolean isValidTarget(Monster monster, Character target, MapleMap map) {
        return target != null && map != null && monster != null && monster.isAlive()
                && monster.getMap() == map && isCurrentSpawn(monster, map)
                && target.isAlive() && target.isLoggedinWorld()
                && !target.isChangingMaps() && target.getMap() == map;
    }

    private static boolean unreachableExpired(TargetState state, long now, long timeoutMs) {
        return timeoutMs > 0 && state.unreachableSince > 0
                && now - state.unreachableSince > timeoutMs;
    }

    private static final class TargetState {
        private final WeakReference<MapleMap> map;
        private final WeakReference<Character> target;
        private WeakReference<Character> controller;
        private final boolean agentTarget;
        private final long changedAt;
        private final int damage;
        private final int threshold;
        private final String reaction;
        private final long pursuitStartAt;
        private long unreachableSince;
        private String latestMovement;

        private TargetState(WeakReference<MapleMap> map, WeakReference<Character> target,
                            WeakReference<Character> controller, boolean agentTarget,
                            long changedAt, int damage, int threshold, String reaction,
                            long pursuitStartAt, long unreachableSince, String latestMovement) {
            this.map = map;
            this.target = target;
            this.controller = controller;
            this.agentTarget = agentTarget;
            this.changedAt = changedAt;
            this.damage = damage;
            this.threshold = threshold;
            this.reaction = reaction;
            this.pursuitStartAt = pursuitStartAt;
            this.unreachableSince = unreachableSince;
            this.latestMovement = latestMovement;
        }
    }

    private record PreparedReactionState(WeakReference<MapleMap> map,
                                         WeakReference<Character> attacker,
                                         int damage, int threshold, String reaction,
                                         long hitDelayMs) {
    }

    public record PreparedReaction(int damage, int threshold, String reaction,
                                   long hitDelayMs) {
    }

    public record PursuitTarget(Monster monster, Character target, Character controller,
                                boolean agentTarget, long changedAt, int damage,
                                int threshold, String reaction, long pursuitStartAt,
                                long unreachableSince, String latestMovement) {
    }

    public record Snapshot(int targetId, String targetName, boolean agentTarget,
                           int controllerId, String controllerName, long changedAt,
                           int damage, int threshold, String reaction,
                           long pursuitStartAt, long unreachableSince,
                           String latestMovement) {
        static Snapshot empty() {
            return new Snapshot(0, "none", false, 0, "none", 0, 0, 0,
                    "none", 0, 0, "none");
        }

        public boolean hasTarget() {
            return targetId != 0;
        }
    }
}
