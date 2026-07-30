package server.agents.capabilities.looting;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeConfig;

/**
 * Gives recent, reachable drops a bounded collection window before a hunt
 * objective hands control back to travel.
 */
public final class AgentPreExitLootRuntime {
    private AgentPreExitLootRuntime() {
    }

    public static boolean drain(AgentRuntimeEntry entry, Character agent, long nowMs) {
        if (entry == null || agent == null) {
            return false;
        }
        AgentPostKillLootState postKill =
                entry.capabilityStates().require(AgentPostKillLootState.STATE_KEY);
        boolean pending = AgentGrindLootStateRuntime.hasGrindLootTarget(entry)
                || postKill.snapshot(nowMs).hasKills();
        AgentPreExitLootState state =
                entry.capabilityStates().require(AgentPreExitLootState.STATE_KEY);
        if (!pending) {
            state.clear();
            return false;
        }
        state.begin(nowMs);
        if (!state.active(nowMs)) {
            clear(entry);
            return false;
        }
        AgentGrindLootTargetService.refreshPreExitLootTarget(
                entry,
                agent,
                true,
                AgentRuntimeConfig.cfg.LOOT_RADIUS,
                AgentLootCollectionPolicyConfig.preExitLootRadius());
        return true;
    }

    public static boolean active(AgentRuntimeEntry entry, long nowMs) {
        return entry != null && entry.capabilityStates()
                .find(AgentPreExitLootState.STATE_KEY)
                .map(state -> state.active(nowMs))
                .orElse(false);
    }

    public static void clear(AgentRuntimeEntry entry) {
        if (entry == null) {
            return;
        }
        entry.capabilityStates().remove(AgentPreExitLootState.STATE_KEY)
                .ifPresent(AgentPreExitLootState::clear);
        entry.capabilityStates().remove(AgentPostKillLootState.STATE_KEY)
                .ifPresent(AgentPostKillLootState::clear);
        AgentGrindLootStateRuntime.clearGrindLootTarget(entry);
    }
}
