package server.agents.capabilities.combat;

import client.Character;
import server.agents.perception.AgentMapPerception;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.Monster;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Owns live monster enumeration; target policies consume only its bounded candidates. */
final class AgentCombatCandidateProvider {
    private AgentCombatCandidateProvider() {
    }

    static List<Monster> local(
            Character agent, Point origin, double maximumDistanceSquared) {
        if (agent == null || agent.getMap() == null || origin == null) {
            return List.of();
        }
        return AgentMapPerception.monsters(agent.getMap()).stream()
                .filter(AgentCombatTargetEligibilityPolicy::isHostileLivingMonster)
                .filter(monster -> {
                    Point aim = AgentCombatAimPointPolicy.aimPoint(agent, monster);
                    return aim != null && aim.distanceSq(origin) <= maximumDistanceSquared;
                })
                .collect(Collectors.toCollection(ArrayList::new));
    }

    static List<Monster> mapWidePreferred(AgentRuntimeEntry entry, Character agent) {
        return mapWide(entry, agent, true);
    }

    static List<Monster> mapWideIncidental(AgentRuntimeEntry entry, Character agent) {
        return mapWide(entry, agent, false);
    }

    static Map<Integer, Monster> byObjectId(Character agent) {
        if (agent == null || agent.getMap() == null) {
            return Map.of();
        }
        return AgentMapPerception.monsters(agent.getMap()).stream()
                .collect(Collectors.toMap(
                        Monster::getObjectId, Function.identity(), (left, right) -> left));
    }

    private static List<Monster> mapWide(
            AgentRuntimeEntry entry, Character agent, boolean preferred) {
        if (entry == null || agent == null || agent.getMap() == null) {
            return List.of();
        }
        return AgentMapPerception.monsters(agent.getMap()).stream()
                .filter(AgentCombatTargetEligibilityPolicy::isHostileLivingMonster)
                .filter(monster -> AgentCombatObjectiveTargetStateRuntime.allows(
                        entry, monster.getId()))
                .filter(monster -> AgentCombatObjectiveTargetStateRuntime.prefers(
                        entry, monster.getId()) == preferred)
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
