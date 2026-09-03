package server.life.autonomy.lordpirate;

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

/** WZ-driven server-owned behavior shared by every Lord Pirate form. */
public final class LordPirateActorBehavior implements BossActorBehavior {
    public static final Set<Integer> MOB_IDS = Set.of(9_300_105, 9_300_106, 9_300_107, 9_300_119);
    private final int mobId;

    public LordPirateActorBehavior(int mobId) {
        if (!MOB_IDS.contains(mobId)) throw new IllegalArgumentException("unsupported Lord Pirate " + mobId);
        this.mobId = mobId;
    }

    @Override public int mobId() { return mobId; }
    @Override public boolean usesServerMobPhysics() { return true; }
    @Override public boolean autoStartOnSpawn() { return true; }

    @Override
    public Optional<SelectedAction> select(Monster monster, List<Character> targets,
                                            ServerMobActionCatalog.MonsterActions actions,
                                            RandomGenerator random) {
        Point origin = monster.getPosition();
        if (origin == null) return Optional.empty();
        List<Character> ordered = targets.stream().filter(target -> target.getPosition() != null)
                .sorted(Comparator.comparingDouble(target -> target.getPosition().distanceSq(origin))).toList();
        int hpPercent = (int) Math.ceil(monster.getHp() * 100.0 / monster.getMaxHp());
        List<SelectedAction> summons = new ArrayList<>();
        List<SelectedAction> buffs = new ArrayList<>();
        for (BossAction.Skill action : actions.skills()) {
            var skill = action.mobSkill();
            if (skill.getHP() < hpPercent || !monster.canUseSkill(skill, false)) continue;
            SelectedAction selected = new SelectedAction(action, ordered.isEmpty() ? null : ordered.getFirst());
            if (skill.getType() == MobSkillType.SUMMON) summons.add(selected);
            else if (skill.getType() == MobSkillType.DEFENSE_UP
                    || skill.getType() == MobSkillType.MAGIC_DEFENSE_UP
                    || skill.getType() == MobSkillType.PHYSICAL_IMMUNE
                    || skill.getType() == MobSkillType.MAGIC_IMMUNE) buffs.add(selected);
        }
        List<SelectedAction> attacks = new ArrayList<>();
        for (BossAction.OrdinaryAttack attack : actions.attacks()) {
            Character target = ordered.stream().filter(candidate -> {
                boolean facingLeft = candidate.getPosition().x < origin.x;
                return BossActionGeometry.contains(attack, origin, candidate.getPosition(), facingLeft);
            }).findFirst().orElse(null);
            if (target != null && monster.getMp() >= attack.mpCost()) attacks.add(new SelectedAction(attack, target));
        }
        List<SelectedAction> eligible = !summons.isEmpty() ? summons : (!buffs.isEmpty() ? buffs : attacks);
        return eligible.isEmpty() ? Optional.empty()
                : Optional.of(eligible.get(random.nextInt(eligible.size())));
    }
}
