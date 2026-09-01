package server.life.autonomy;

import server.life.autonomy.alishar.AlisharActorBehavior;
import server.life.autonomy.balrog.EasyBalrogBodyBehavior;
import server.life.autonomy.balrog.EasyBalrogInitialClawBehavior;
import server.life.autonomy.balrog.EasyBalrogReleasedClawBehavior;
import server.life.autonomy.papapixie.PapaPixieActorBehavior;
import server.life.autonomy.poisongolem.PoisonGolemActorBehavior;
import server.life.autonomy.poisongolem.PoisonGolemSummonedAddBehavior;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Static registry of mob templates that have server-owned combat behavior. */
public final class ServerMobBehaviorRegistry {
    private static final Map<Integer, BossActorBehavior> BEHAVIORS = behaviors();

    private ServerMobBehaviorRegistry() {
    }

    public static Optional<BossActorBehavior> behaviorFor(int mobId) {
        return Optional.ofNullable(BEHAVIORS.get(mobId));
    }

    public static boolean supports(int mobId) {
        return BEHAVIORS.containsKey(mobId);
    }

    private static Map<Integer, BossActorBehavior> behaviors() {
        Map<Integer, BossActorBehavior> result = new HashMap<>();
        register(result, new AlisharActorBehavior());
        register(result, new PapaPixieActorBehavior());
        register(result, new EasyBalrogBodyBehavior());
        register(result, new EasyBalrogReleasedClawBehavior());
        register(result, new EasyBalrogInitialClawBehavior());
        PoisonGolemActorBehavior.MOB_IDS.forEach(id ->
                register(result, new PoisonGolemActorBehavior(id)));
        PoisonGolemSummonedAddBehavior.MOB_IDS.forEach(id ->
                register(result, new PoisonGolemSummonedAddBehavior(id)));
        return Map.copyOf(result);
    }

    private static void register(Map<Integer, BossActorBehavior> behaviors,
                                 BossActorBehavior behavior) {
        if (behaviors.putIfAbsent(behavior.mobId(), behavior) != null) {
            throw new IllegalStateException("duplicate server mob behavior " + behavior.mobId());
        }
    }
}
