package server.agents.capabilities.townlife;

import server.agents.runtime.state.AgentCapabilityStateKey;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Per-Agent ambient binding; population admission and exit remain externally owned. */
public final class AgentTownLifeAmbientState {
    public static final AgentCapabilityStateKey<AgentTownLifeAmbientState> STATE_KEY =
            new AgentCapabilityStateKey<>("town-life-ambient", AgentTownLifeAmbientState.class,
                    AgentTownLifeAmbientState::new);

    public enum CompletionTransition {
        CONTINUE_IN_PLACE,
        RELOCATE_SAME_ACTIVITY,
        SWITCH_ACTIVITY,
        REQUEST_EXIT
    }

    private String deploymentId = "";
    private AgentTownLifeAmbientPolicy policy;
    private AgentTownLifeState.Activity forcedActivity = AgentTownLifeState.Activity.NONE;
    private boolean exitSuggested;
    private CompletionTransition lastTransition = CompletionTransition.SWITCH_ACTIVITY;
    private long completionOrdinal;

    public synchronized void configure(String nextDeploymentId,
                                       AgentTownLifeAmbientPolicy nextPolicy) {
        if (nextDeploymentId == null || nextDeploymentId.isBlank() || nextPolicy == null) {
            throw new IllegalArgumentException("ambient TownLife deployment and policy are required");
        }
        deploymentId = nextDeploymentId.trim();
        policy = nextPolicy;
        forcedActivity = AgentTownLifeState.Activity.NONE;
        exitSuggested = false;
        lastTransition = CompletionTransition.SWITCH_ACTIVITY;
        completionOrdinal = 0L;
    }

    public synchronized void clear() {
        deploymentId = "";
        policy = null;
        forcedActivity = AgentTownLifeState.Activity.NONE;
        exitSuggested = false;
        lastTransition = CompletionTransition.SWITCH_ACTIVITY;
        completionOrdinal = 0L;
    }

    public synchronized boolean active() {
        return policy != null;
    }

    public synchronized String deploymentId() {
        return deploymentId;
    }

    synchronized AgentTownLifeState.Activity choose(
            AgentTownLifeState.Activity defaultActivity,
            AgentTownLifeProfile profile,
            List<AgentTownLifePopulationPort.AgentView> population) {
        if (policy == null) {
            return defaultActivity;
        }
        if (forcedActivity != AgentTownLifeState.Activity.NONE) {
            AgentTownLifeState.Activity selected = forcedActivity;
            forcedActivity = AgentTownLifeState.Activity.NONE;
            return selected;
        }
        EnumMap<AgentTownLifeState.Activity, Integer> counts =
                new EnumMap<>(AgentTownLifeState.Activity.class);
        for (AgentTownLifePopulationPort.AgentView view : population) {
            counts.merge(view.activity(), 1, Integer::sum);
        }
        int total = Math.max(1, population.size());
        AgentTownLifeState.Activity deficitChoice = policy.activities().entrySet().stream()
                .filter(entry -> supported(profile, entry.getKey()))
                .filter(entry -> counts.getOrDefault(entry.getKey(), 0) < entry.getValue().hardMax())
                .max(Comparator.<Map.Entry<AgentTownLifeState.Activity,
                                AgentTownLifeAmbientPolicy.ActivityRule>>comparingInt(entry -> {
                            int target = Math.max(1,
                                    (total * entry.getValue().targetPercent() + 99) / 100);
                            return target - counts.getOrDefault(entry.getKey(), 0);
                        })
                        .thenComparingInt(entry -> -entry.getKey().ordinal()))
                .filter(entry -> {
                    int target = Math.max(1,
                            (total * entry.getValue().targetPercent() + 99) / 100);
                    return counts.getOrDefault(entry.getKey(), 0) < target;
                })
                .map(Map.Entry::getKey)
                .orElse(null);
        if (deficitChoice != null) {
            return deficitChoice;
        }
        AgentTownLifeAmbientPolicy.ActivityRule defaultRule = policy.activities().get(defaultActivity);
        if (defaultRule != null && supported(profile, defaultActivity)
                && counts.getOrDefault(defaultActivity, 0) < defaultRule.hardMax()) {
            return defaultActivity;
        }
        return policy.activities().entrySet().stream()
                .filter(entry -> supported(profile, entry.getKey()))
                .filter(entry -> counts.getOrDefault(entry.getKey(), 0) < entry.getValue().hardMax())
                .min(Comparator.comparingDouble(entry ->
                        (double) counts.getOrDefault(entry.getKey(), 0)
                                / entry.getValue().hardMax()))
                .map(Map.Entry::getKey)
                .orElse(defaultActivity);
    }

    synchronized long dwellDuration(int agentId, int sequence,
                                    AgentTownLifeState.Activity activity) {
        if (policy == null) {
            return 0L;
        }
        AgentTownLifeAmbientPolicy.ActivityRule rule = policy.activities().get(activity);
        if (rule == null) {
            return 0L;
        }
        long range = rule.maximumDwellMs() - rule.minimumDwellMs() + 1L;
        return rule.minimumDwellMs()
                + Math.floorMod(mix(agentId, sequence, activity.ordinal() + 401), range);
    }

    synchronized CompletionTransition completed(
            int agentId, int sequence, AgentTownLifeState.Activity activity) {
        if (policy == null) {
            return CompletionTransition.SWITCH_ACTIVITY;
        }
        AgentTownLifeAmbientPolicy.TransitionWeights weights = policy.transitions();
        int roll = Math.floorMod((int) mix(agentId, sequence + completionOrdinal++,
                        activity.ordinal() + 719),
                weights.total());
        CompletionTransition transition;
        if (roll < weights.continueInPlace()) {
            transition = CompletionTransition.CONTINUE_IN_PLACE;
        } else if ((roll -= weights.continueInPlace()) < weights.relocateSameActivity()) {
            transition = CompletionTransition.RELOCATE_SAME_ACTIVITY;
            forcedActivity = activity;
        } else if ((roll -= weights.relocateSameActivity()) < weights.switchActivity()) {
            transition = CompletionTransition.SWITCH_ACTIVITY;
        } else {
            transition = CompletionTransition.REQUEST_EXIT;
            exitSuggested = true;
        }
        lastTransition = transition;
        return transition;
    }

    public synchronized boolean consumeExitSuggestion() {
        boolean result = exitSuggested;
        exitSuggested = false;
        return result;
    }

    public synchronized CompletionTransition lastTransition() {
        return lastTransition;
    }

    public synchronized int preferredChairItemId(int identitySeed) {
        if (policy == null || policy.chairItemIds().isEmpty()) {
            return 0;
        }
        return policy.chairItemIds().get(Math.floorMod(identitySeed, policy.chairItemIds().size()));
    }

    private static boolean supported(AgentTownLifeProfile profile,
                                     AgentTownLifeState.Activity activity) {
        return activity != AgentTownLifeState.Activity.NONE && !profile.venuesFor(activity).isEmpty();
    }

    private static long mix(long first, long second, long salt) {
        long value = first * 0x9E3779B97F4A7C15L
                + second * 0xBF58476D1CE4E5B9L + salt * 0x94D049BB133111EBL;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
