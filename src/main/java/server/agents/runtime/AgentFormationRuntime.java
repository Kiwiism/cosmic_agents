package server.agents.runtime;

import server.agents.capabilities.movement.AgentMovementPhysicsConfig;

import client.Character;
import server.bots.BotEntry;

import java.util.List;

public final class AgentFormationRuntime {
    private AgentFormationRuntime() {
    }

    public static AgentFormationService.FormationState defaultFormationState() {
        return AgentFormationService.defaultStagger(
                AgentRuntimeConfig.cfg.FOLLOW_STAGGER,
                AgentMovementPhysicsConfig.configuredFollowYCap());
    }

    public static AgentFormationService.FormationState formationStateFor(BotEntry entry) {
        return AgentFormationService.stateForEntry(
                entry,
                AgentFormationService.formationsByLeaderId(),
                defaultFormationState());
    }

    public static void setFormationState(Character leader,
                                         AgentFormationService.FormationType type,
                                         int px,
                                         int snapRange,
                                         List<BotEntry> entries) {
        if (leader == null) {
            return;
        }

        AgentFormationService.FormationState formation =
                new AgentFormationService.FormationState(type, px, snapRange);
        AgentFormationService.formationsByLeaderId().put(leader.getId(), formation);
        if (entries != null) {
            AgentFormationService.applyOffsets(entries, formation);
        }
    }
}
