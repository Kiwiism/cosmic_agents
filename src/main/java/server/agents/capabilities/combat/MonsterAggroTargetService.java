package server.agents.capabilities.combat;

import client.Character;
import server.life.Monster;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class MonsterAggroTargetService {
    private static final Map<Monster, TargetState> targets =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Monster, PreparedReactionState> preparedReactions =
            Collections.synchronizedMap(new WeakHashMap<>());

    private MonsterAggroTargetService() {
    }

    public static void record(Monster monster, Character attacker, Character simulationController,
                              int damage, int threshold, String reaction, long now) {
        if (monster == null || attacker == null || damage <= 0) {
            return;
        }
        targets.put(monster, new TargetState(new WeakReference<>(attacker),
                new WeakReference<>(simulationController), now, damage, threshold, reaction));
        AgentMobReactionMetrics.targetChange();
    }

    public static void prepareReaction(Monster monster, Character attacker, int damage,
                                       int threshold, String reaction) {
        if (monster == null || attacker == null || damage <= 0) {
            return;
        }
        preparedReactions.put(monster, new PreparedReactionState(
                new WeakReference<>(attacker), damage, threshold, reaction));
    }

    public static PreparedReaction consumePreparedReaction(Monster monster, Character attacker) {
        synchronized (preparedReactions) {
            PreparedReactionState state = preparedReactions.get(monster);
            if (state == null || state.attacker.get() != attacker) {
                return null;
            }
            preparedReactions.remove(monster);
            return new PreparedReaction(state.damage, state.threshold, state.reaction);
        }
    }

    public static Snapshot inspect(Monster monster, long now, long timeoutMs) {
        synchronized (targets) {
            TargetState state = targets.get(monster);
            if (state == null) {
                return Snapshot.empty();
            }
            Character target = state.target.get();
            Character controller = state.controller.get();
            if (!isValidTarget(monster, target, now - state.changedAt, timeoutMs)) {
                targets.remove(monster);
                AgentMobReactionMetrics.staleTarget();
                return Snapshot.empty();
            }
            return new Snapshot(target.getId(), target.getName(),
                    controller == null ? 0 : controller.getId(),
                    controller == null ? "none" : controller.getName(),
                    state.changedAt, state.damage, state.threshold, state.reaction);
        }
    }

    public static void clear(Monster monster) {
        targets.remove(monster);
        preparedReactions.remove(monster);
    }

    private static boolean isValidTarget(Monster monster, Character target, long ageMs, long timeoutMs) {
        return target != null && target.isAlive() && target.isLoggedinWorld()
                && !target.isChangingMaps() && target.getMap() == monster.getMap()
                && (timeoutMs <= 0 || ageMs <= timeoutMs);
    }

    private record TargetState(WeakReference<Character> target,
                               WeakReference<Character> controller,
                               long changedAt, int damage, int threshold, String reaction) {
    }

    private record PreparedReactionState(WeakReference<Character> attacker,
                                         int damage, int threshold, String reaction) {
    }

    public record PreparedReaction(int damage, int threshold, String reaction) {
    }

    public record Snapshot(int targetId, String targetName, int controllerId,
                           String controllerName, long changedAt, int damage,
                           int threshold, String reaction) {
        static Snapshot empty() {
            return new Snapshot(0, "none", 0, "none", 0, 0, 0, "none");
        }

        public boolean hasTarget() {
            return targetId != 0;
        }
    }
}
