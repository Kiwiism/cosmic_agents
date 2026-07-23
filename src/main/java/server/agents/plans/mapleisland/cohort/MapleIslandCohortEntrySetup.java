package server.agents.plans.mapleisland.cohort;

import config.YamlConfig;
import server.agents.capabilities.presentation.AgentPersonalityPresentationRuntime;
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
        boolean presentationEnabled = config.AgentYamlConfig.config.agent.AGENT_PERSONALITY_PRESENTATION_ENABLED
                && context.realismMode() == MapleIslandCohortRealismMode.FULL;
        AgentPersonalityPresentationRuntime.configure(
                entry, presentationEnabled, System.currentTimeMillis());
        AgentBehaviorRuntime.configure(entry,
                config.AgentYamlConfig.config.agent.AGENT_COMBAT_BEHAVIOR_ENABLED
                        && context.realismMode() == MapleIslandCohortRealismMode.FULL);
        return MapleIslandCohortRealismService.configure(
                entry, context.realismMode(), context.runSeed(), context.ordinal());
    }
}
