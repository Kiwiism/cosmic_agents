package server.agents.capabilities.combat;

import server.agents.runtime.AgentRuntimeEntry;

import java.util.Set;
import java.util.List;
import java.util.Collection;

public final class AgentCombatObjectiveTargetStateRuntime {
    private AgentCombatObjectiveTargetStateRuntime() {
    }

    public static void setAllowedMobIds(AgentRuntimeEntry entry, Set<Integer> mobIds) {
        if (entry.combatObjectiveTargetState().setAllowedMobIds(mobIds)) {
            AgentGrindTargetStateRuntime.clear(entry);
        }
    }

    public static void clear(AgentRuntimeEntry entry) {
        entry.combatObjectiveTargetState().clear();
        AgentGrindTargetStateRuntime.clear(entry);
    }

    public static boolean allows(AgentRuntimeEntry entry, int mobId) {
        return entry == null || entry.combatObjectiveTargetState().allows(mobId);
    }

    public static List<server.life.Monster> allowedMonsters(
            AgentRuntimeEntry entry,
            Collection<server.life.Monster> monsters) {
        if (entry == null || !entry.combatObjectiveTargetState().restricted()) {
            return List.copyOf(monsters);
        }
        return monsters.stream().filter(monster -> allows(entry, monster.getId())).toList();
    }

    public static AgentAttackPlan restrictAttackPlan(AgentRuntimeEntry entry, AgentAttackPlan plan) {
        if (plan == null || entry == null || !entry.combatObjectiveTargetState().restricted()) {
            return plan;
        }
        List<server.life.Monster> targets = allowedMonsters(entry, plan.targets);
        if (targets.isEmpty()) {
            return null;
        }
        return new AgentAttackPlan(plan.skillId, plan.skillLevel, plan.numDamage, plan.hitBox, targets,
                plan.route, plan.display, plan.direction, plan.rangedDirection, plan.stance,
                plan.speed, plan.hitDelayMs, plan.cooldownMs, plan.damageWeaponType);
    }
}
