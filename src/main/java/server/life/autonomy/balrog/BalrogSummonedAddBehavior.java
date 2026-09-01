package server.life.autonomy.balrog;

import client.Character;
import server.life.Monster;
import server.life.autonomy.BossAction;
import server.life.autonomy.BossActionGeometry;
import server.life.autonomy.BossActorBehavior;
import server.life.autonomy.ServerMobActionCatalog;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

/** Jr./Crimson Balrog chase their Agent aggro owner and cast only from WZ attack range. */
public final class BalrogSummonedAddBehavior implements BossActorBehavior {
    public static final int JR_BALROG_ID = 6_400_008;
    public static final int CRIMSON_BALROG_ID = 6_400_009;
    private static final Set<Integer> MOB_IDS = Set.of(JR_BALROG_ID, CRIMSON_BALROG_ID);

    private final int mobId;

    private BalrogSummonedAddBehavior(int mobId) {
        this.mobId = mobId;
    }

    public static Optional<BalrogSummonedAddBehavior> behaviorFor(int mobId) {
        return MOB_IDS.contains(mobId)
                ? Optional.of(new BalrogSummonedAddBehavior(mobId))
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
        boolean facingLeft = destination.x < origin.x;
        List<SelectedAction> eligible = new ArrayList<>();
        for (BossAction.OrdinaryAttack attack : actions.attacks()) {
            if (monster.getMp() >= attack.mpCost()
                    && BossActionGeometry.contains(attack, origin, destination, facingLeft)) {
                eligible.add(new SelectedAction(attack, target, facingLeft));
            }
        }
        return eligible.isEmpty()
                ? Optional.empty()
                : Optional.of(eligible.get(random.nextInt(eligible.size())));
    }
}
