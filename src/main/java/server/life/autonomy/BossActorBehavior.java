package server.life.autonomy;

import client.Character;
import server.life.Monster;

import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

/** Mob-specific selection policy; execution and scheduling remain generic. */
public interface BossActorBehavior {
    int mobId();

    Optional<SelectedAction> select(
            Monster monster,
            List<Character> targets,
            ServerMobActionCatalog.MonsterActions actions,
            RandomGenerator random);

    default boolean autoStartOnSpawn() {
        return false;
    }

    record SelectedAction(BossAction action, Character primaryTarget,
                          Boolean facingLeftOverride) {
        public SelectedAction(BossAction action, Character primaryTarget) {
            this(action, primaryTarget, null);
        }
    }
}
