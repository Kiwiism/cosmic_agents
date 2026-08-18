package server.agents.capabilities.combat;

import server.agents.runtime.AgentRuntimeEntry;

import java.util.Set;
import java.util.List;
import java.util.Collection;

public final class AgentCombatObjectiveTargetStateRuntime {
    private AgentCombatObjectiveTargetStateRuntime() {
    }

    public static void setAllowedMobIds(AgentRuntimeEntry entry, Set<Integer> mobIds) {
        if (entry.capabilityStates().require(AgentCombatObjectiveTargetState.STATE_KEY).setAllowedMobIds(mobIds)) {
            clearDisallowedTarget(entry);
        }
        AgentCombatDirectiveRuntime.assignAllowed(entry, mobIds);
    }

    public static void setTargetPreferences(AgentRuntimeEntry entry,
                                            Set<Integer> preferredMobIds,
                                            Set<Integer> fallbackMobIds) {
        if (entry.capabilityStates().require(AgentCombatObjectiveTargetState.STATE_KEY).setTargetPreferences(preferredMobIds, fallbackMobIds)) {
            clearDisallowedTarget(entry);
        }
        AgentCombatDirectiveRuntime.assignPreferences(entry, preferredMobIds, fallbackMobIds);
    }

    public static void clear(AgentRuntimeEntry entry) {
        entry.capabilityStates().require(AgentCombatObjectiveTargetState.STATE_KEY).clear();
        AgentCombatDirectiveRuntime.clear(entry);
        AgentGrindTargetStateRuntime.clear(entry);
    }

    public static boolean allows(AgentRuntimeEntry entry, int mobId) {
        return entry == null || entry.capabilityStates().require(AgentCombatObjectiveTargetState.STATE_KEY).allows(mobId);
    }

    public static boolean prefers(AgentRuntimeEntry entry, int mobId) {
        return entry == null || entry.capabilityStates().require(AgentCombatObjectiveTargetState.STATE_KEY).prefers(mobId);
    }

    public static boolean hasPreferredTargets(AgentRuntimeEntry entry) {
        return entry != null && entry.capabilityStates().require(AgentCombatObjectiveTargetState.STATE_KEY).hasPreferredTargets();
    }

    public static List<server.life.Monster> allowedMonsters(
            AgentRuntimeEntry entry,
            Collection<server.life.Monster> monsters) {
        if (entry == null || !entry.capabilityStates().require(AgentCombatObjectiveTargetState.STATE_KEY).restricted()) {
            return List.copyOf(monsters);
        }
        return monsters.stream().filter(monster -> allows(entry, monster.getId())).toList();
    }

    public static AgentAttackPlan restrictAttackPlan(AgentRuntimeEntry entry, AgentAttackPlan plan) {
        if (plan == null || entry == null || !entry.capabilityStates().require(AgentCombatObjectiveTargetState.STATE_KEY).restricted()) {
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

    private static void clearDisallowedTarget(AgentRuntimeEntry entry) {
        server.life.Monster target = AgentGrindTargetStateRuntime.target(entry);
        if (target != null && !allows(entry, target.getId())) {
            AgentGrindTargetStateRuntime.clear(entry);
        }
    }
}
