package server.agents.capabilities.townlife;

import client.Character;
import server.agents.personality.AgentPersonalityProfile;
import server.agents.personality.AgentPersonalityState;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.EnumMap;
import java.util.Map;

final class AgentTownLifeActivityPolicy {
    private static final String TUNING_PREFIX =
            "server.agents.capabilities.townlife.AgentTownLifeActivityPolicy.";
    private static final int STATIONED_REST_WEIGHT = tuningInt("STATIONED_REST_WEIGHT");
    private static final int STATIONED_SOCIAL_WEIGHT = tuningInt("STATIONED_SOCIAL_WEIGHT");
    private static final int STATIONED_NPC_PAUSE_WEIGHT = tuningInt("STATIONED_NPC_PAUSE_WEIGHT");
    private static final int STATIONED_ROAM_WEIGHT = tuningInt("STATIONED_ROAM_WEIGHT");
    private static final int STATIONED_SHOP_VISIT_WEIGHT = tuningInt("STATIONED_SHOP_VISIT_WEIGHT");
    private static final int STATIONED_WEAPON_FLOURISH_WEIGHT =
            tuningInt("STATIONED_WEAPON_FLOURISH_WEIGHT");
    private static final int MOBILE_REST_WEIGHT = tuningInt("MOBILE_REST_WEIGHT");
    private static final int MOBILE_SOCIAL_WEIGHT = tuningInt("MOBILE_SOCIAL_WEIGHT");
    private static final int MOBILE_NPC_PAUSE_WEIGHT = tuningInt("MOBILE_NPC_PAUSE_WEIGHT");
    private static final int MOBILE_ROAM_WEIGHT = tuningInt("MOBILE_ROAM_WEIGHT");
    private static final int MOBILE_SHOP_VISIT_WEIGHT = tuningInt("MOBILE_SHOP_VISIT_WEIGHT");
    private static final int MOBILE_WEAPON_FLOURISH_WEIGHT =
            tuningInt("MOBILE_WEAPON_FLOURISH_WEIGHT");
    private static final int TRAIT_CENTER = tuningInt("TRAIT_CENTER");
    private static final int REST_TRAIT_CENTER = tuningInt("REST_TRAIT_CENTER");
    private static final int REST_TRAIT_DIVISOR = tuningInt("REST_TRAIT_DIVISOR");
    private static final int SOCIAL_TRAIT_DIVISOR = tuningInt("SOCIAL_TRAIT_DIVISOR");
    private static final int NPC_PAUSE_TRAIT_DIVISOR = tuningInt("NPC_PAUSE_TRAIT_DIVISOR");
    private static final int ROAM_TRAIT_DIVISOR = tuningInt("ROAM_TRAIT_DIVISOR");
    private static final int SHOP_VISIT_TRAIT_DIVISOR = tuningInt("SHOP_VISIT_TRAIT_DIVISOR");
    private static final int WEAPON_FLOURISH_TRAIT_DIVISOR =
            tuningInt("WEAPON_FLOURISH_TRAIT_DIVISOR");
    private static final int RECENT_ACTIVITY_WEIGHT_DIVISOR =
            tuningInt("RECENT_ACTIVITY_WEIGHT_DIVISOR");
    private static final int MINIMUM_ACTIVITY_WEIGHT = tuningInt("MINIMUM_ACTIVITY_WEIGHT");

    private AgentTownLifeActivityPolicy() {
    }

    static AgentTownLifeState.Activity choose(AgentRuntimeEntry entry,
                                              Character agent,
                                              AgentTownLifeState state) {
        if (!state.initialPlacementComplete()) {
            return AgentTownLifeState.Activity.ROAM;
        }
        Map<AgentTownLifeState.Activity, Integer> weights = new EnumMap<>(AgentTownLifeState.Activity.class);
        if (state.role() == AgentTownLifeState.Role.STATIONED) {
            weights.put(AgentTownLifeState.Activity.REST, STATIONED_REST_WEIGHT);
            weights.put(AgentTownLifeState.Activity.SOCIAL, STATIONED_SOCIAL_WEIGHT);
            weights.put(AgentTownLifeState.Activity.NPC_PAUSE, STATIONED_NPC_PAUSE_WEIGHT);
            weights.put(AgentTownLifeState.Activity.ROAM, STATIONED_ROAM_WEIGHT);
            weights.put(AgentTownLifeState.Activity.SHOP_VISIT, STATIONED_SHOP_VISIT_WEIGHT);
            weights.put(
                    AgentTownLifeState.Activity.WEAPON_FLOURISH,
                    STATIONED_WEAPON_FLOURISH_WEIGHT);
        } else {
            weights.put(AgentTownLifeState.Activity.REST, MOBILE_REST_WEIGHT);
            weights.put(AgentTownLifeState.Activity.SOCIAL, MOBILE_SOCIAL_WEIGHT);
            weights.put(AgentTownLifeState.Activity.NPC_PAUSE, MOBILE_NPC_PAUSE_WEIGHT);
            weights.put(AgentTownLifeState.Activity.ROAM, MOBILE_ROAM_WEIGHT);
            weights.put(AgentTownLifeState.Activity.SHOP_VISIT, MOBILE_SHOP_VISIT_WEIGHT);
            weights.put(
                    AgentTownLifeState.Activity.WEAPON_FLOURISH,
                    MOBILE_WEAPON_FLOURISH_WEIGHT);
        }
        AgentPersonalityState personality = entry.capabilityStates()
                .find(AgentPersonalityState.STATE_KEY).orElse(null);
        long seed = agent.getId();
        if (personality != null && personality.profile() != null) {
            AgentPersonalityProfile.Traits traits = personality.profile().traits();
            adjust(weights, AgentTownLifeState.Activity.REST,
                    (traits.patience() + traits.routinePreference() - REST_TRAIT_CENTER)
                            / REST_TRAIT_DIVISOR);
            adjust(weights, AgentTownLifeState.Activity.SOCIAL,
                    (traits.sociability() - TRAIT_CENTER) / SOCIAL_TRAIT_DIVISOR);
            adjust(weights, AgentTownLifeState.Activity.NPC_PAUSE,
                    (traits.curiosity() - TRAIT_CENTER) / NPC_PAUSE_TRAIT_DIVISOR);
            adjust(weights, AgentTownLifeState.Activity.ROAM,
                    (traits.activity() - TRAIT_CENTER) / ROAM_TRAIT_DIVISOR);
            adjust(weights, AgentTownLifeState.Activity.SHOP_VISIT,
                    (traits.curiosity() - TRAIT_CENTER) / SHOP_VISIT_TRAIT_DIVISOR);
            adjust(weights, AgentTownLifeState.Activity.WEAPON_FLOURISH,
                    (traits.expressiveness() - TRAIT_CENTER)
                            / WEAPON_FLOURISH_TRAIT_DIVISOR);
            seed ^= personality.behaviorSeed();
        }
        for (AgentTownLifeState.Activity activity : weights.keySet()) {
            if (state.memory().recentlyUsed(activity)) {
                weights.compute(
                        activity,
                        (ignored, value) -> Math.max(
                                MINIMUM_ACTIVITY_WEIGHT,
                                value / RECENT_ACTIVITY_WEIGHT_DIVISOR));
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
        return AgentTownLifeState.Activity.ROAM;
    }

    private static void adjust(Map<AgentTownLifeState.Activity, Integer> weights,
                               AgentTownLifeState.Activity activity,
                               int delta) {
        weights.compute(
                activity,
                (ignored, value) -> Math.max(MINIMUM_ACTIVITY_WEIGHT, value + delta));
    }

    private static int tuningInt(String name) {
        return config.AgentTuning.intValue(TUNING_PREFIX + name);
    }
}
