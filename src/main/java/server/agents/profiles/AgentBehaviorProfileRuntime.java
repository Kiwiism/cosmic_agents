package server.agents.profiles;

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
        return current(entry)
                .map(profile -> sample(profile.presentation().timing().beforeNpcInteractionMs()))
                .orElse(0L);
    }

    public static long sampleBetweenObjectivesDelayMs(AgentRuntimeEntry entry) {
        return current(entry)
                .map(profile -> sample(profile.presentation().timing().betweenObjectivesMs()))
                .orElse(0L);
    }

    public static long sample(AgentBehaviorProfile.DelayRange range) {
        if (range == null || range.max() == 0) {
            return 0L;
        }
        return ThreadLocalRandom.current().nextLong(range.min(), (long) range.max() + 1L);
    }
}
