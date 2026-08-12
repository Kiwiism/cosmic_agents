package server.agents.plans.mapleisland.cohort;

import config.YamlConfig;
import server.agents.capabilities.presentation.AgentPersonalityPresentationRuntime;
import server.agents.capabilities.presentation.AgentPresentationProfile;
import server.agents.behavior.AgentBehaviorFeatureProfile;
import server.agents.behavior.AgentBehaviorRuntime;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Single integration point for cohort-only seeded behavior settings.
 * Objective/navigation policy can attach here without coupling it to provisioning or wave scheduling.
 */
public final class MapleIslandCohortEntrySetup {
    private MapleIslandCohortEntrySetup() {
    }

    public static long apply(AgentRuntimeEntry entry, MapleIslandCohortRunService.AgentContext context) {
        boolean presentationEnabled = AgentPresentationProfile.current().enabled()
                && context.realismMode() == MapleIslandCohortRealismMode.FULL;
        AgentPersonalityPresentationRuntime.configure(
                entry, presentationEnabled, System.currentTimeMillis());
        AgentBehaviorRuntime.configure(entry,
                AgentBehaviorFeatureProfile.current().enabled()
                        && context.realismMode() == MapleIslandCohortRealismMode.FULL);
        return MapleIslandCohortRealismService.configure(
                entry, context.realismMode(), context.runSeed(), context.ordinal());
    }
}
