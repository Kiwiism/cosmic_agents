package server.agents.capabilities.combat;

import client.Character;
import server.life.Monster;

import java.util.List;

public final class AgentBasicAttackPlanRuntime {
    private AgentBasicAttackPlanRuntime() {
    }

    public static AgentAttackPlan planBasicAttack(Character bot, Monster target) {
        AgentBasicAttackPlanner.BasicAttackSelection selection = AgentBasicAttackPlanner.selectBasicAttack(
                target,
                candidate -> AgentAttackExecutionProvider.buildBasicAttackData(bot, candidate.getPosition()),
                (candidate, hitBox) -> AgentCombatTargetSelector.resolveEffectivePrimary(
                        bot.getPosition(), candidate, hitBox, bot.getMap().getAllMonsters()),
                AgentCombatHitboxIntersection::intersectsMonster,
                candidate -> bot == null ? null : AgentCombatTargetSelector.findReachableOnOppositeFacing(
                        bot.getPosition(),
                        candidate,
                        mirroredPos -> AgentAttackExecutionProvider.buildBasicAttackData(bot, mirroredPos).hitBox(),
                        hitBox -> AgentCombatTargetSelector.resolveEffectivePrimary(
                                bot.getPosition(), candidate, hitBox, bot.getMap().getAllMonsters())));
        if (selection == null) {
            return null;
        }

        AgentAttackExecutionProvider.BasicAttackData basicAttackData = selection.attackData();
        Monster effective = selection.target();
        int numDamage = AgentCombatHitCounter.packetSafeHitCount(bot, basicAttackData.route(), 1);
        return new AgentAttackPlan(0, 0, numDamage, basicAttackData.hitBox(), List.of(effective), basicAttackData.route(),
                basicAttackData.display(), basicAttackData.direction(), basicAttackData.rangedDirection(),
                basicAttackData.stance(), basicAttackData.speed(), basicAttackData.hitDelayMs(),
                basicAttackData.cooldownMs(), AgentCombatWeaponPolicy.damageWeaponTypeForAction(
                0, AgentAttackExecutionProvider.getEquippedWeaponType(bot), basicAttackData.action()));
    }
}
