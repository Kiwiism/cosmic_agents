package server.life.autonomy.balrog;

import client.Character;
import server.life.MobSkillType;
import server.life.Monster;
import server.life.autonomy.BossAction;
import server.life.autonomy.BossActionGeometry;
import server.life.autonomy.BossActorBehavior;
import server.life.autonomy.ServerMobActionCatalog;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

/** Shared WZ selection policy for Easy Balrog's stationary combat components. */
abstract class EasyBalrogBehavior implements BossActorBehavior {
    @Override
    public final boolean autoStartOnSpawn() {
        return true;
    }

    @Override
    public final Optional<SelectedAction> select(
            Monster monster, List<Character> targets,
            ServerMobActionCatalog.MonsterActions actions, RandomGenerator random) {
        if (monster.isFake() || monster.getPosition() == null) {
            return Optional.empty();
        }
        Point origin = monster.getPosition();
        List<Character> ordered = targets.stream()
                .filter(target -> target.getPosition() != null)
                .sorted(Comparator.comparingDouble(target ->
                        target.getPosition().distanceSq(origin)))
                .toList();
        List<SelectedAction> eligible = new ArrayList<>();
        for (BossAction.OrdinaryAttack attack : actions.attacks()) {
            addAttack(monster, ordered, origin, attack, eligible);
        }

        int hpPercent = (int) Math.ceil(monster.getHp() * 100.0 / monster.getMaxHp());
        for (BossAction.Skill action : actions.skills()) {
            var skill = action.mobSkill();
            if (skill.getHP() < hpPercent || !monster.canUseSkill(skill, false)) {
                continue;
            }
            if (skill.getType() == MobSkillType.PHYSICAL_AND_MAGIC_COUNTER
                    || skill.getType() == MobSkillType.UNDEAD
                    || skill.getType() == MobSkillType.SUMMON) {
                eligible.add(new SelectedAction(action,
                        ordered.isEmpty() ? null : ordered.getFirst()));
            }
        }
        return eligible.isEmpty()
                ? Optional.empty()
                : Optional.of(eligible.get(random.nextInt(eligible.size())));
    }

    private static void addAttack(Monster monster, List<Character> targets, Point origin,
                                  BossAction.OrdinaryAttack attack,
                                  List<SelectedAction> eligible) {
        if (monster.getMp() < attack.mpCost()) {
            return;
        }
        if (attack.hasDistributedRegions()) {
            if (!targets.isEmpty()) {
                eligible.add(new SelectedAction(attack, targets.getFirst(),
                        (monster.getStance() & 1) != 0));
            }
            return;
        }
        for (Character target : targets) {
            if (BossActionGeometry.contains(attack, origin, target.getPosition(), true)) {
                eligible.add(new SelectedAction(attack, target, true));
                return;
            }
            if (BossActionGeometry.contains(attack, origin, target.getPosition(), false)) {
                eligible.add(new SelectedAction(attack, target, false));
                return;
            }
        }
    }
}
