package server.life.autonomy.poisongolem;

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
import java.util.Set;
import java.util.random.RandomGenerator;

/** WZ-driven server actor shared by all three Ellin Poison Golem forms. */
public final class PoisonGolemActorBehavior implements BossActorBehavior {
    public static final Set<Integer> MOB_IDS = Set.of(9_300_180, 9_300_181, 9_300_182);
    private final int mobId;

    public PoisonGolemActorBehavior(int mobId) {
        if (!MOB_IDS.contains(mobId)) throw new IllegalArgumentException("unsupported Poison Golem form");
        this.mobId = mobId;
    }

    @Override public int mobId() { return mobId; }
    @Override public boolean autoStartOnSpawn() { return true; }
    @Override public boolean forceServerAuthority() { return true; }
    @Override public boolean usesServerMobPhysics() { return true; }

    @Override
    public Optional<SelectedAction> select(Monster monster, List<Character> targets,
                                           ServerMobActionCatalog.MonsterActions actions,
                                           RandomGenerator random) {
        Point origin = monster.getPosition();
        if (origin == null) return Optional.empty();
        List<Character> ordered = targets.stream()
                .filter(target -> target != null && target.getPosition() != null)
                .sorted(Comparator.comparingDouble(target ->
                        target.getPosition().distanceSq(origin))).toList();

        List<SelectedAction> heals = new ArrayList<>();
        List<SelectedAction> summons = new ArrayList<>();
        List<SelectedAction> controls = new ArrayList<>();
        List<SelectedAction> buffs = new ArrayList<>();
        int hpPercent = (int) Math.ceil(monster.getHp() * 100.0d / monster.getMaxHp());
        for (BossAction.Skill action : actions.skills()) {
            var skill = action.mobSkill();
            if (skill.getHP() < hpPercent || !monster.canUseSkill(skill, false)) continue;
            SelectedAction selected = new SelectedAction(action,
                    ordered.isEmpty() ? null : ordered.getFirst());
            switch (skill.getType()) {
                case HEAL_M -> heals.add(selected);
                case SUMMON -> summons.add(selected);
                case SEAL, DARKNESS, WEAKNESS, AREA_POISON, REVERSE_INPUT -> {
                    Character target = ordered.stream()
                            .filter(candidate -> withinSkillEnvelope(origin, candidate.getPosition(), action))
                            .findFirst().orElse(null);
                    if (target != null) controls.add(new SelectedAction(action, target));
                }
                default -> buffs.add(selected);
            }
        }
        List<SelectedAction> attacks = new ArrayList<>();
        for (BossAction.OrdinaryAttack attack : actions.attacks()) {
            for (Character target : ordered) {
                Boolean facing = facingFor(attack, origin, target.getPosition());
                if (facing != null && monster.getMp() >= attack.mpCost()) {
                    attacks.add(new SelectedAction(attack, target, facing));
                    break;
                }
            }
        }
        for (List<SelectedAction> priority : List.of(heals, summons, controls, buffs, attacks)) {
            if (!priority.isEmpty()) return Optional.of(priority.get(random.nextInt(priority.size())));
        }
        return Optional.empty();
    }

    private static Boolean facingFor(BossAction.OrdinaryAttack attack, Point origin, Point target) {
        if (BossActionGeometry.contains(attack, origin, target, true)) return true;
        if (BossActionGeometry.contains(attack, origin, target, false)) return false;
        return null;
    }

    private static boolean withinSkillEnvelope(Point origin, Point target, BossAction.Skill action) {
        Point lt = action.lt();
        Point rb = action.rb();
        return lt == null || rb == null || target.x >= origin.x + lt.x
                && target.x <= origin.x + rb.x && target.y >= origin.y + lt.y
                && target.y <= origin.y + rb.y;
    }
}
