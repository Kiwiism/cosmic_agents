package server.agents.capabilities.townlife;

import client.Character;
import server.agents.personality.AgentPersonalityProfile;
import server.agents.personality.AgentPersonalityState;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.EnumMap;
import java.util.Map;

final class AgentTownLifeActivityPolicy {
    private AgentTownLifeActivityPolicy() {
    }

    static AgentTownLifeState.Activity choose(AgentRuntimeEntry entry,
                                              Character agent,
                                              AgentTownLifeState state) {
        Map<AgentTownLifeState.Activity, Integer> weights = new EnumMap<>(AgentTownLifeState.Activity.class);
        if (state.role() == AgentTownLifeState.Role.STATIONED) {
            weights.put(AgentTownLifeState.Activity.REST, 32);
            weights.put(AgentTownLifeState.Activity.SOCIAL, 27);
            weights.put(AgentTownLifeState.Activity.NPC_PAUSE, 22);
            weights.put(AgentTownLifeState.Activity.WANDER, 6);
            weights.put(AgentTownLifeState.Activity.SHOP_VISIT, 5);
            weights.put(AgentTownLifeState.Activity.WEAPON_FLOURISH, 8);
        } else {
            weights.put(AgentTownLifeState.Activity.REST, 12);
            weights.put(AgentTownLifeState.Activity.SOCIAL, 20);
            weights.put(AgentTownLifeState.Activity.NPC_PAUSE, 15);
            weights.put(AgentTownLifeState.Activity.WANDER, 27);
            weights.put(AgentTownLifeState.Activity.SHOP_VISIT, 18);
            weights.put(AgentTownLifeState.Activity.WEAPON_FLOURISH, 8);
        }
        AgentPersonalityState personality = entry.capabilityStates()
                .find(AgentPersonalityState.STATE_KEY).orElse(null);
        long seed = agent.getId();
        if (personality != null && personality.profile() != null) {
            AgentPersonalityProfile.Traits traits = personality.profile().traits();
            adjust(weights, AgentTownLifeState.Activity.REST,
                    (traits.patience() + traits.routinePreference() - 100) / 12);
            adjust(weights, AgentTownLifeState.Activity.SOCIAL, (traits.sociability() - 50) / 6);
            adjust(weights, AgentTownLifeState.Activity.NPC_PAUSE, (traits.curiosity() - 50) / 8);
            adjust(weights, AgentTownLifeState.Activity.WANDER, (traits.activity() - 50) / 6);
            adjust(weights, AgentTownLifeState.Activity.SHOP_VISIT, (traits.curiosity() - 50) / 10);
            adjust(weights, AgentTownLifeState.Activity.WEAPON_FLOURISH,
                    (traits.expressiveness() - 50) / 8);
            seed ^= personality.behaviorSeed();
        }
        for (AgentTownLifeState.Activity activity : weights.keySet()) {
            if (state.memory().recentlyUsed(activity)) {
                weights.compute(activity, (ignored, value) -> Math.max(1, value / 4));
            }
        }
        int total = weights.values().stream().mapToInt(Integer::intValue).sum();
        int roll = AgentTownLifeRolePolicy.variation(seed, state.sequence(), total, 239);
        for (Map.Entry<AgentTownLifeState.Activity, Integer> candidate : weights.entrySet()) {
            if (roll < candidate.getValue()) {
                return candidate.getKey();
            }
            roll -= candidate.getValue();
        }
        return AgentTownLifeState.Activity.WANDER;
    }

    private static void adjust(Map<AgentTownLifeState.Activity, Integer> weights,
                               AgentTownLifeState.Activity activity,
                               int delta) {
        weights.compute(activity, (ignored, value) -> Math.max(1, value + delta));
    }
}
