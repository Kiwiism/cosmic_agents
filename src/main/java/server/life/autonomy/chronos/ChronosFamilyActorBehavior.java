package server.life.autonomy.chronos;

import client.Character;
import server.life.Monster;
import server.life.autonomy.BossAction;
import server.life.autonomy.BossActionGeometry;
import server.life.autonomy.BossActorBehavior;
import server.life.autonomy.ServerMobActionCatalog;

import java.awt.Point;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

/** Server-side WZ attack selection for the Chronos family after Agent aggro takeover. */
public final class ChronosFamilyActorBehavior implements BossActorBehavior {
    private static final Set<Integer> MOB_IDS = Set.of(
            3_230_306, 4_230_114, 4_230_115,
            9_300_015, 9_300_016, 9_300_017);

    private final int mobId;

    private ChronosFamilyActorBehavior(int mobId) {
        this.mobId = mobId;
    }

    public static Optional<ChronosFamilyActorBehavior> behaviorFor(int mobId) {
        return MOB_IDS.contains(mobId)
                ? Optional.of(new ChronosFamilyActorBehavior(mobId))
                : Optional.empty();
    }

    @Override
    public int mobId() {
        return mobId;
    }

    @Override
    public boolean usesPrimaryAggroTargetOnly() {
        return true;
    }

    @Override
    public Optional<SelectedAction> select(
            Monster monster,
            List<Character> targets,
            ServerMobActionCatalog.MonsterActions actions,
            RandomGenerator random) {
        if (targets.isEmpty() || monster.getPosition() == null
                || targets.getFirst().getPosition() == null) {
            return Optional.empty();
        }
        Character target = targets.getFirst();
        Point origin = monster.getPosition();
        Point destination = target.getPosition();
        for (BossAction.OrdinaryAttack attack : actions.attacks()) {
            if (monster.getMp() < attack.mpCost()) continue;
            boolean facingLeft = destination.x < origin.x;
            if (BossActionGeometry.contains(attack, origin, destination, facingLeft)) {
                return Optional.of(new SelectedAction(attack, target, facingLeft));
            }
        }
        return Optional.empty();
    }
}
