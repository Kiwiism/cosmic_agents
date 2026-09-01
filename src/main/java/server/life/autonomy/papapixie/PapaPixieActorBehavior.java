package server.life.autonomy.papapixie;

import client.Character;
import server.life.Monster;
import server.life.autonomy.BossActorBehavior;
import server.life.autonomy.GenericWzMobBehavior;
import server.life.autonomy.ServerMobActionCatalog;

import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

/** WZ-driven single-actor behavior for Papa Pixie (9300039). */
public final class PapaPixieActorBehavior implements BossActorBehavior {
    public static final int MOB_ID = 9_300_039;

    private final GenericWzMobBehavior delegate = new GenericWzMobBehavior(MOB_ID);

    @Override
    public int mobId() {
        return MOB_ID;
    }

    @Override
    public Optional<SelectedAction> select(
            Monster monster,
            List<Character> targets,
            ServerMobActionCatalog.MonsterActions actions,
            RandomGenerator random) {
        return delegate.select(monster, targets, actions, random);
    }
}
