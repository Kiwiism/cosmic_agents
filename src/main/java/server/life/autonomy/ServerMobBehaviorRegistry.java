package server.life.autonomy;

import server.life.autonomy.alishar.AlisharActorBehavior;
import server.life.autonomy.balrog.EasyBalrogBodyBehavior;
import server.life.autonomy.balrog.EasyBalrogInitialClawBehavior;
import server.life.autonomy.balrog.EasyBalrogReleasedClawBehavior;
import server.life.autonomy.papapixie.PapaPixieActorBehavior;

import java.util.Map;
import java.util.Optional;

/** Static registry of mob templates that have server-owned combat behavior. */
public final class ServerMobBehaviorRegistry {
    private static final Map<Integer, BossActorBehavior> BEHAVIORS = Map.of(
            AlisharActorBehavior.MOB_ID, new AlisharActorBehavior(),
            PapaPixieActorBehavior.MOB_ID, new PapaPixieActorBehavior(),
            EasyBalrogBodyBehavior.MOB_ID, new EasyBalrogBodyBehavior(),
            EasyBalrogReleasedClawBehavior.MOB_ID, new EasyBalrogReleasedClawBehavior(),
            EasyBalrogInitialClawBehavior.MOB_ID, new EasyBalrogInitialClawBehavior());

    private ServerMobBehaviorRegistry() {
    }

    public static Optional<BossActorBehavior> behaviorFor(int mobId) {
        return Optional.ofNullable(BEHAVIORS.get(mobId));
    }

    public static boolean supports(int mobId) {
        return BEHAVIORS.containsKey(mobId);
    }
}
