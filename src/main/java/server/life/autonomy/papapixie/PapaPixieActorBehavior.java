package server.life.autonomy.papapixie;

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

/** WZ-driven single-actor behavior for Papa Pixie (9300039). */
public final class PapaPixieActorBehavior implements BossActorBehavior {
    public static final int MOB_ID = 9_300_039;

    @Override
    public int mobId() {
        return MOB_ID;
    }

    @Override
    public boolean usesServerMobPhysics() {
        return true;
    }

    @Override
    public Optional<SelectedAction> select(
            Monster monster,
            List<Character> targets,
            ServerMobActionCatalog.MonsterActions actions,
            RandomGenerator random) {
        Point origin = monster.getPosition();
        if (origin == null) {
            return Optional.empty();
        }
        List<Character> orderedTargets = targets.stream()
                .filter(target -> target.getPosition() != null)
                .sorted(Comparator.comparingDouble(target ->
                        target.getPosition().distanceSq(origin)))
                .toList();
        List<SelectedAction> eligible = new ArrayList<>();

        for (BossAction.OrdinaryAttack attack : actions.attacks()) {
            for (Character target : orderedTargets) {
                Boolean facingLeft = facingFor(attack, origin, target.getPosition());
                if (facingLeft != null && monster.getMp() >= attack.mpCost()) {
                    eligible.add(new SelectedAction(attack, target, facingLeft));
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
                        orderedTargets.isEmpty() ? null : orderedTargets.getFirst()));
                continue;
            }
            orderedTargets.stream()
                    .filter(target -> withinSkillEnvelope(
                            origin, target.getPosition(), skillAction))
                    .findFirst()
                    .ifPresent(target -> eligible.add(new SelectedAction(skillAction, target)));
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

    private static boolean withinSkillEnvelope(Point origin, Point target,
                                               BossAction.Skill action) {
        Point lt = action.lt();
        Point rb = action.rb();
        return lt != null && rb != null
                && target.x >= origin.x + lt.x && target.x <= origin.x + rb.x
                && target.y >= origin.y + lt.y && target.y <= origin.y + rb.y;
    }
}
