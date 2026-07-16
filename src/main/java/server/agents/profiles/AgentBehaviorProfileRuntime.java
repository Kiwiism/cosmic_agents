package server.agents.profiles;

import server.agents.capabilities.objective.MapleIslandObjectiveRandomnessRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public final class AgentBehaviorProfileRuntime {
    private AgentBehaviorProfileRuntime() {
    }

    public static void assignMapleIslandQuester(AgentRuntimeEntry entry) {
        if (entry != null) {
            entry.behaviorProfileState().assign(AgentBehaviorProfileRepository.mapleIslandQuester());
        }
    }

    public static Optional<AgentBehaviorProfile> current(AgentRuntimeEntry entry) {
        return entry == null ? Optional.empty() : Optional.ofNullable(entry.behaviorProfileState().profile());
    }

    public static long sampleNpcInteractionDelayMs(AgentRuntimeEntry entry) {
        AgentBehaviorProfile.DelayRange range = current(entry)
                .map(profile -> profile.presentation().timing().beforeNpcInteractionMs())
                .orElse(null);
        return MapleIslandObjectiveRandomnessRuntime.sampleNpcInteractionDelayMs(entry, range)
                .orElseGet(() -> sample(range));
    }

    public static long sampleBetweenObjectivesDelayMs(AgentRuntimeEntry entry) {
        AgentBehaviorProfile.DelayRange range = current(entry)
                .map(profile -> profile.presentation().timing().betweenObjectivesMs())
                .orElse(null);
        return MapleIslandObjectiveRandomnessRuntime.sampleBetweenObjectivesDelayMs(entry, range)
                .orElseGet(() -> sample(range));
    }

    public static long sample(AgentBehaviorProfile.DelayRange range) {
        if (range == null || range.max() == 0) {
            return 0L;
        }
        return ThreadLocalRandom.current().nextLong(range.min(), (long) range.max() + 1L);
    }
}
