package server.partner;

import client.Character;
import server.agents.capabilities.equipment.AgentAutoEquipThrottle;
import server.agents.capabilities.combat.AgentCombatSkillCacheRuntime;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

public final class PartnerProfileCacheInvalidator {
    public void invalidate(AgentRuntimeEntry entry, Character agentActor) {
        if (entry == null || agentActor == null) {
            return;
        }
        entry.combatSkillCacheState().reset(-1, -1, 0);
        entry.ammoSupplyState().clearWarningState();
        AgentAutoEquipThrottle.clearAgentRuntimeState(agentActor.getId());
        AgentMovementStateRuntime.refreshMovementProfile(entry, agentActor);
        AgentCombatSkillCacheRuntime.rebuildSkillCacheIfNeeded(entry, agentActor);
        entry.markProfileCachesCurrent(
                agentActor.getProfileOwnerCharacterId(), agentActor.getProfileVersion());
    }
}
