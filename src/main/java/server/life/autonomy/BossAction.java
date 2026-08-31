package server.life.autonomy;

import server.life.MobSkill;

import java.awt.Point;

/** Immutable WZ-backed action description used by server-owned monster actors. */
public sealed interface BossAction permits BossAction.OrdinaryAttack, BossAction.Skill {
    int actionNumber();

    int animationTimeMs();

    record OrdinaryAttack(
            int attackIndex,
            int actionNumber,
            int mpCost,
            int impactDelayMs,
            int animationTimeMs,
            boolean magic,
            Point lt,
            Point rb,
            int areaStart,
            int areaCount,
            int selectedAreaCount,
            boolean deadly,
            int physicalAttack,
            int magicAttack,
            int diseaseSkill,
            int diseaseLevel,
            boolean tremble
    ) implements BossAction {
        public boolean hasDistributedRegions() {
            return areaCount > 0 && selectedAreaCount > 0 && selectedAreaCount < areaCount;
        }
    }

    record Skill(
            MobSkill mobSkill,
            int actionNumber,
            int effectDelayMs,
            int animationTimeMs,
            Point lt,
            Point rb
    ) implements BossAction {
    }
}
