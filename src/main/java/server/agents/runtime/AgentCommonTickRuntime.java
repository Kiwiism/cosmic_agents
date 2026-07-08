package server.agents.runtime;

import server.agents.capabilities.movement.AgentMovementTimers;

import client.Character;
import server.agents.capabilities.build.AgentBuildService;
import server.agents.capabilities.combat.AgentBuffService;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.capabilities.inventory.AgentInventoryTickRuntime;
import server.agents.capabilities.partyquest.AgentPartyQuestHooks;
import server.agents.capabilities.supplies.AgentPotionService;
import server.agents.integration.AgentCombatActionLockRuntime;
import server.agents.integration.AgentCombatBuffRuntime;
import server.agents.integration.AgentCombatDamageRuntime;
import server.agents.integration.AgentCombatDeathRuntime;
import server.agents.integration.AgentCombatHealRuntime;
import server.agents.integration.AgentCombatSkillCacheRuntime;
import server.agents.integration.AgentDeathStateRuntime;
import server.agents.integration.AgentManagerStatusRuntime;

import java.util.function.Consumer;

public final class AgentCommonTickRuntime {
    private AgentCommonTickRuntime() {
    }

    public static boolean runCommonTickSystems(AgentRuntimeEntry entry,
                                               Character agent,
                                               Character leader,
                                               boolean runAiTick,
                                               Consumer<AgentRuntimeEntry> tickScriptTasks) {
        return AgentCommonTickService.runCommonTickSystems(
                entry,
                agent,
                leader,
                runAiTick,
                hooks(tickScriptTasks));
    }

    private static AgentCommonTickService.CommonTickHooks hooks(Consumer<AgentRuntimeEntry> tickScriptTasks) {
        return new AgentCommonTickService.CommonTickHooks(
                (entry, agent) -> AgentCombatDamageRuntime.tickMobDamage(
                        entry, agent, AgentCombatConfig.cfg, AgentMovementTimers::tickDown),
                (entry, agent) -> AgentDeathStateRuntime.isDead(entry),
                (entry, agent) -> AgentCombatDeathRuntime.enterDeadState(
                        entry, agent, false, AgentCombatConfig.cfg),
                AgentMonsterControlService::releaseControlledMonsters,
                (entry, agent) -> AgentInventoryTickRuntime.tickPassiveLoot(entry, agent),
                (entry, agent) -> AgentPotionService.tickPotionCheck(entry, agent),
                (entry, agent) -> AgentPotionService.tickPassiveRecovery(entry, agent),
                (entry, agent) -> AgentBuildService.checkLevelUp(entry, agent),
                (entry, agent, leader) -> AgentManagerStatusRuntime.tickAfkCheck(entry, leader),
                (entry, agent) -> AgentInventoryTickRuntime.tickTrade(entry, agent),
                (entry, agent) -> AgentInventoryTickRuntime.tickManualTrade(entry, agent),
                AgentPartyQuestHooks::tick,
                tickScriptTasks,
                AgentPartyQuestHooks::isNpcLocked,
                AgentCombatActionLockRuntime::tickActionLock,
                AgentCombatSkillCacheRuntime::rebuildSkillCacheIfNeeded,
                (entry, agent) -> AgentCombatHealRuntime.tickSupportHealing(
                        entry, agent, AgentCombatConfig.cfg),
                (entry, agent) -> AgentCombatBuffRuntime.tickBuffs(
                        entry, agent, AgentCombatConfig.cfg),
                AgentBuffService::tick,
                AgentActionLockPhysicsRuntime::tickActionLocked);
    }
}
