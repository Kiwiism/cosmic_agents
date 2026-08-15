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
    private static final int STATIONED_SOCIALIZE_WEIGHT = tuningInt("STATIONED_SOCIALIZE_WEIGHT");
    private static final int STATIONED_LINGER_WEIGHT = tuningInt("STATIONED_LINGER_WEIGHT");
    private static final int STATIONED_STROLL_WEIGHT = tuningInt("STATIONED_STROLL_WEIGHT");
    private static final int STATIONED_BROWSE_WEIGHT = tuningInt("STATIONED_BROWSE_WEIGHT");
    private static final int STATIONED_SHOW_OFF_WEIGHT = tuningInt("STATIONED_SHOW_OFF_WEIGHT");
    private static final int MOBILE_REST_WEIGHT = tuningInt("MOBILE_REST_WEIGHT");
    private static final int MOBILE_SOCIALIZE_WEIGHT = tuningInt("MOBILE_SOCIALIZE_WEIGHT");
    private static final int MOBILE_LINGER_WEIGHT = tuningInt("MOBILE_LINGER_WEIGHT");
    private static final int MOBILE_STROLL_WEIGHT = tuningInt("MOBILE_STROLL_WEIGHT");
    private static final int MOBILE_BROWSE_WEIGHT = tuningInt("MOBILE_BROWSE_WEIGHT");
    private static final int MOBILE_SHOW_OFF_WEIGHT = tuningInt("MOBILE_SHOW_OFF_WEIGHT");
    private static final int TRAIT_CENTER = tuningInt("TRAIT_CENTER");
    private static final int REST_TRAIT_CENTER = tuningInt("REST_TRAIT_CENTER");
    private static final int REST_TRAIT_DIVISOR = tuningInt("REST_TRAIT_DIVISOR");
    private static final int SOCIAL_TRAIT_DIVISOR = tuningInt("SOCIAL_TRAIT_DIVISOR");
    private static final int LINGER_TRAIT_DIVISOR = tuningInt("LINGER_TRAIT_DIVISOR");
    private static final int STROLL_TRAIT_DIVISOR = tuningInt("STROLL_TRAIT_DIVISOR");
    private static final int BROWSE_TRAIT_DIVISOR = tuningInt("BROWSE_TRAIT_DIVISOR");
    private static final int SHOW_OFF_TRAIT_DIVISOR = tuningInt("SHOW_OFF_TRAIT_DIVISOR");
    private static final int RECENT_ACTIVITY_WEIGHT_DIVISOR =
            tuningInt("RECENT_ACTIVITY_WEIGHT_DIVISOR");
    private static final int MINIMUM_ACTIVITY_WEIGHT = tuningInt("MINIMUM_ACTIVITY_WEIGHT");

    private AgentTownLifeActivityPolicy() {
    }

    static AgentTownLifeState.Activity choose(AgentRuntimeEntry entry,
                                              Character agent,
                                              AgentTownLifeState state) {
        AgentPersonalityState personality = entry.capabilityStates()
                .find(AgentPersonalityState.STATE_KEY).orElse(null);
        AgentPersonalityProfile.Traits traits = personality == null || personality.profile() == null
                ? null : personality.profile().traits();
        AgentTownLifeDecisionContext.PersonalityView personalityView = traits == null
                ? AgentTownLifeDecisionContext.PersonalityView.neutral()
                : new AgentTownLifeDecisionContext.PersonalityView(
                traits.patience(), traits.activity(), traits.curiosity(), traits.sociability(),
                traits.routinePreference(), traits.expressiveness());
        AgentTownLifeProfile profile = AgentTownLifeProfileRepository.defaultRepository()
                .require(state.townMapId());
        return choose(new AgentTownLifeActivityContext(
                agent.getId(), personality == null ? agent.getId() : personality.behaviorSeed(),
                state.initialPlacementComplete(), state.role(), state.sequence(), personalityView,
                traits != null, profile.activityWeights(), state.memory().recentActivitiesSnapshot()));
    }

    static AgentTownLifeState.Activity choose(AgentTownLifeActivityContext context) {
        if (!context.initialPlacementComplete()) {
            return AgentTownLifeState.Activity.STROLL;
        }
        Map<AgentTownLifeState.Activity, Integer> weights = new EnumMap<>(AgentTownLifeState.Activity.class);
        if (context.role() == AgentTownLifeState.Role.STATIONED) {
            weights.put(AgentTownLifeState.Activity.REST, STATIONED_REST_WEIGHT);
            weights.put(AgentTownLifeState.Activity.SOCIALIZE, STATIONED_SOCIALIZE_WEIGHT);
            weights.put(AgentTownLifeState.Activity.LINGER, STATIONED_LINGER_WEIGHT);
            weights.put(AgentTownLifeState.Activity.STROLL, STATIONED_STROLL_WEIGHT);
            weights.put(AgentTownLifeState.Activity.BROWSE, STATIONED_BROWSE_WEIGHT);
            weights.put(AgentTownLifeState.Activity.SHOW_OFF, STATIONED_SHOW_OFF_WEIGHT);
        } else {
            weights.put(AgentTownLifeState.Activity.REST, MOBILE_REST_WEIGHT);
            weights.put(AgentTownLifeState.Activity.SOCIALIZE, MOBILE_SOCIALIZE_WEIGHT);
            weights.put(AgentTownLifeState.Activity.LINGER, MOBILE_LINGER_WEIGHT);
            weights.put(AgentTownLifeState.Activity.STROLL, MOBILE_STROLL_WEIGHT);
            weights.put(AgentTownLifeState.Activity.BROWSE, MOBILE_BROWSE_WEIGHT);
            weights.put(AgentTownLifeState.Activity.SHOW_OFF, MOBILE_SHOW_OFF_WEIGHT);
        }
        weights.replaceAll((activity, weight) -> Math.max(
                MINIMUM_ACTIVITY_WEIGHT,
                weight * context.profileWeights().getOrDefault(activity, 100) / 100));
        long seed = context.agentId();
        if (context.personalityAssigned()) {
            AgentTownLifeDecisionContext.PersonalityView traits = context.personality();
            adjust(weights, AgentTownLifeState.Activity.REST,
                    (traits.patience() + traits.routinePreference() - REST_TRAIT_CENTER)
                            / REST_TRAIT_DIVISOR);
            adjust(weights, AgentTownLifeState.Activity.SOCIALIZE,
                    (traits.sociability() - TRAIT_CENTER) / SOCIAL_TRAIT_DIVISOR);
            adjust(weights, AgentTownLifeState.Activity.LINGER,
                    (traits.curiosity() - TRAIT_CENTER) / LINGER_TRAIT_DIVISOR);
            adjust(weights, AgentTownLifeState.Activity.STROLL,
                    (traits.activity() - TRAIT_CENTER) / STROLL_TRAIT_DIVISOR);
            adjust(weights, AgentTownLifeState.Activity.BROWSE,
                    (traits.curiosity() - TRAIT_CENTER) / BROWSE_TRAIT_DIVISOR);
            adjust(weights, AgentTownLifeState.Activity.SHOW_OFF,
                    (traits.expressiveness() - TRAIT_CENTER)
                            / SHOW_OFF_TRAIT_DIVISOR);
            seed ^= context.behaviorSeed();
        }
        for (AgentTownLifeState.Activity activity : weights.keySet()) {
            if (context.recentActivities().contains(activity)) {
                weights.compute(
                        activity,
                        (ignored, value) -> Math.max(
                                MINIMUM_ACTIVITY_WEIGHT,
                                value / RECENT_ACTIVITY_WEIGHT_DIVISOR));
            }
        }
        int total = weights.values().stream().mapToInt(Integer::intValue).sum();
        int roll = AgentTownLifeRolePolicy.variation(seed, context.sequence(), total, 239);
        for (Map.Entry<AgentTownLifeState.Activity, Integer> candidate : weights.entrySet()) {
            if (roll < candidate.getValue()) {
                return candidate.getKey();
            }
            roll -= candidate.getValue();
        }
        return AgentTownLifeState.Activity.STROLL;
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
