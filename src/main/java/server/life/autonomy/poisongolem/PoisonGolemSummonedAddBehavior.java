package server.life.autonomy.poisongolem;

import client.Character;
import server.life.Monster;
import server.life.autonomy.BossActorBehavior;
import server.life.autonomy.GenericWzMobBehavior;
import server.life.autonomy.ServerMobActionCatalog;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

/** Keeps Poison Golem summons inside the parent's server-owned encounter. */
public final class PoisonGolemSummonedAddBehavior implements BossActorBehavior {
    public static final Set<Integer> MOB_IDS = Set.of(9_300_177, 9_300_178, 9_300_179);
    private final int mobId;
    private final GenericWzMobBehavior delegate;

    public PoisonGolemSummonedAddBehavior(int mobId) {
        if (!MOB_IDS.contains(mobId)) throw new IllegalArgumentException("unsupported Poison Golem summon");
        this.mobId = mobId;
        this.delegate = new GenericWzMobBehavior(mobId);
    }

    @Override public int mobId() { return mobId; }
    @Override public boolean usesServerMobPhysics() { return true; }

    @Override
    public Optional<SelectedAction> select(Monster monster, List<Character> targets,
                                           ServerMobActionCatalog.MonsterActions actions,
                                           RandomGenerator random) {
        return delegate.select(monster, targets, actions, random);
    }
}
