package server.life.autonomy;

import client.Character;
import server.life.MobSkillType;
import server.life.Monster;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

/** WZ-driven fallback used only by adds inheriting a server-owned encounter. */
public final class GenericWzMobBehavior implements BossActorBehavior {
    private final int mobId;

    public GenericWzMobBehavior(int mobId) {
        this.mobId = mobId;
    }

    @Override
    public int mobId() {
        return mobId;
    }

    @Override
    public Optional<SelectedAction> select(Monster monster, List<Character> targets,
                                           ServerMobActionCatalog.MonsterActions actions,
                                           RandomGenerator random) {
        Point origin = monster.getPosition();
        if (origin == null) {
            return Optional.empty();
        }
        List<Character> ordered = targets.stream()
                .filter(target -> target.getPosition() != null)
                .sorted(Comparator.comparingDouble(target ->
                        target.getPosition().distanceSq(origin)))
                .toList();
        List<SelectedAction> eligible = new ArrayList<>();
        for (BossAction.OrdinaryAttack attack : actions.attacks()) {
            for (Character target : ordered) {
                Boolean facing = facingFor(attack, origin, target.getPosition());
                if (facing != null && monster.getMp() >= attack.mpCost()) {
                    eligible.add(new SelectedAction(attack, target, facing));
                    break;
                }
            }
        }
        int hpPercent = (int) Math.ceil(monster.getHp() * 100.0 / monster.getMaxHp());
        for (BossAction.Skill skillAction : actions.skills()) {
            var skill = skillAction.mobSkill();
            if (skill.getHP() < hpPercent || !monster.canUseSkill(skill, false)) {
                continue;
            }
            if (skill.getType() == MobSkillType.SUMMON) {
                eligible.add(new SelectedAction(skillAction,
                        ordered.isEmpty() ? null : ordered.getFirst()));
            } else if (!ordered.isEmpty()) {
                eligible.add(new SelectedAction(skillAction, ordered.getFirst()));
            }
        }
        return eligible.isEmpty()
                ? Optional.empty()
                : Optional.of(eligible.get(random.nextInt(eligible.size())));
    }

    private static Boolean facingFor(BossAction.OrdinaryAttack attack, Point origin,
                                     Point target) {
        if (BossActionGeometry.contains(attack, origin, target, true)) {
            return true;
        }
        if (BossActionGeometry.contains(attack, origin, target, false)) {
            return false;
        }
        return null;
    }
}
