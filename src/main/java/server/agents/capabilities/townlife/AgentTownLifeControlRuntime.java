package server.agents.capabilities.townlife;

import client.Character;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

/** Process-local operational switch. Disabling TownLife cleanly resumes every foreground plan. */
public final class AgentTownLifeControlRuntime {
    private static volatile boolean enabled = true;

    private AgentTownLifeControlRuntime() {
    }

    public static boolean enabled() {
        return enabled;
    }

    public static int setEnabled(boolean nextEnabled, String reason) {
        enabled = nextEnabled;
        if (nextEnabled) {
            return 0;
        }
        int stopped = 0;
        for (AgentRuntimeEntry entry : AgentRuntimeRegistry.activeEntriesSnapshot()) {
            if (!AgentTownLifeRuntime.active(entry)) {
                continue;
            }
            Character agent = AgentRuntimeIdentityRuntime.bot(entry);
            AgentTownLifeLifecycleRuntime.stop(entry, agent,
                    reason == null || reason.isBlank() ? "TownLife disabled" : reason);
            stopped++;
        }
        return stopped;
    }
}
