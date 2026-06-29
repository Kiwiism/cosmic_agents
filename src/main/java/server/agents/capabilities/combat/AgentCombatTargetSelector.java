package server.agents.capabilities.combat;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import server.life.Monster;

public final class AgentCombatTargetSelector {
    private AgentCombatTargetSelector() {
    }

    public static List<Monster> collectTargetsInHitBox(Monster primaryTarget,
                                                       Rectangle hitBox,
                                                       int maxTargets,
                                                       Iterable<Monster> candidates) {
        if (!AgentCombatHitboxIntersection.intersectsMonster(hitBox, primaryTarget)) {
            return List.of();
        }

        List<Monster> targets = new ArrayList<>();
        targets.add(primaryTarget);
        if (maxTargets <= 1) {
            return targets;
        }

        List<Monster> secondaryTargets = new ArrayList<>();
        for (Monster monster : candidates) {
            if (!AgentCombatTargetEligibilityPolicy.isHostileLivingMonster(monster)
                    || monster.getObjectId() == primaryTarget.getObjectId()) {
                continue;
            }
            if (!AgentCombatHitboxIntersection.intersectsMonster(hitBox, monster)) {
                continue;
            }
            secondaryTargets.add(monster);
        }

        secondaryTargets.sort(Comparator.comparingDouble(
                monster -> monster.getPosition().distanceSq(primaryTarget.getPosition())));
        for (Monster monster : secondaryTargets) {
            targets.add(monster);
            if (targets.size() >= maxTargets) {
                break;
            }
        }
        return targets;
    }
}
