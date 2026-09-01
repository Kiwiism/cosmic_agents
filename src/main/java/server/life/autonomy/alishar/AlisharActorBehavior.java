package server.life.autonomy.alishar;

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

/** WZ-driven single-actor behavior for Alishar (9300012). */
public final class AlisharActorBehavior implements BossActorBehavior {
    public static final int MOB_ID = 9_300_012;

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
        List<SelectedAction> eligibleAttacks = new ArrayList<>();

        for (BossAction.OrdinaryAttack attack : actions.attacks()) {
            Character target = orderedTargets.stream()
                    .filter(candidate -> inAttackRange(origin, candidate.getPosition(), attack))
                    .findFirst()
                    .orElse(null);
            if (target != null && monster.getMp() >= attack.mpCost()) {
                eligibleAttacks.add(new SelectedAction(attack, target));
            }
        }

        List<SelectedAction> eligibleDebuffs = new ArrayList<>();
        List<SelectedAction> eligibleSummons = new ArrayList<>();
        int hpPercent = (int) Math.ceil(monster.getHp() * 100.0 / monster.getMaxHp());
        for (BossAction.Skill skillAction : actions.skills()) {
            var skill = skillAction.mobSkill();
            if (skill.getHP() < hpPercent || !monster.canUseSkill(skill, false)) {
                continue;
            }
            if (skill.getType() == MobSkillType.SUMMON) {
                eligibleSummons.add(new SelectedAction(skillAction,
                        orderedTargets.isEmpty() ? null : orderedTargets.getFirst()));
                continue;
            }
            Character target = orderedTargets.stream()
                    .filter(candidate -> withinSkillEnvelope(
                            origin, candidate.getPosition(), skillAction))
                    .findFirst()
                    .orElse(null);
            if (target != null) {
                eligibleDebuffs.add(new SelectedAction(skillAction, target));
            }
        }

        List<SelectedAction> eligible = !eligibleDebuffs.isEmpty()
                ? eligibleDebuffs
                : (!eligibleSummons.isEmpty() ? eligibleSummons : eligibleAttacks);
        if (eligible.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(eligible.get(random.nextInt(eligible.size())));
    }

    private static boolean inAttackRange(
            Point origin, Point target, BossAction.OrdinaryAttack attack) {
        boolean facingLeft = target.x < origin.x;
        return BossActionGeometry.contains(attack, origin, target, facingLeft);
    }

    private static boolean withinSkillEnvelope(
            Point origin, Point target, BossAction.Skill action) {
        Point lt = action.lt();
        Point rb = action.rb();
        return lt != null && rb != null
                && target.x >= origin.x + lt.x && target.x <= origin.x + rb.x
                && target.y >= origin.y + lt.y && target.y <= origin.y + rb.y;
    }
}
