package server.agents.plans.mapleisland.cohort;

import server.agents.runtime.AgentRuntimeEntry;

/**
 * Single integration point for cohort-only seeded behavior settings.
 * Objective/navigation policy can attach here without coupling it to provisioning or wave scheduling.
 */
public final class MapleIslandCohortEntrySetup {
    private MapleIslandCohortEntrySetup() {
    }

    public static void apply(AgentRuntimeEntry entry, MapleIslandCohortRunService.AgentContext context) {
        MapleIslandCohortRealismService.configure(
                entry, context.realismMode(), context.runSeed(), context.ordinal());
    }
}
