package server.agents.runtime;

import server.agents.capabilities.combat.AgentDeathStateRuntime;
import server.agents.capabilities.movement.AgentActionLockPhysicsService;
import server.agents.capabilities.combat.AgentMonsterControlService;

import server.agents.capabilities.movement.AgentMovementTimers;

import client.Character;
import server.agents.capabilities.build.AgentBuildService;
import server.agents.capabilities.combat.AgentBuffService;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.capabilities.inventory.AgentInventoryTickRuntime;
import server.agents.capabilities.partyquest.AgentPartyQuestHooks;
import server.agents.capabilities.supplies.AgentPotionService;
import server.agents.capabilities.combat.AgentCombatActionLockRuntime;
import server.agents.capabilities.combat.AgentCombatBuffRuntime;
import server.agents.capabilities.combat.AgentCombatDamageRuntime;
import server.agents.capabilities.combat.AgentCombatDeathRuntime;
import server.agents.capabilities.combat.AgentCombatHealRuntime;
import server.agents.capabilities.combat.AgentCombatSkillCacheRuntime;
import server.agents.capabilities.dialogue.AgentChatStatusOrchestrator;
import server.agents.integration.AgentInventoryGatewayRuntime;

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
                (entry, agent) -> AgentPotionService.tickPotionCheck(
                        entry,
                        agent,
                        AgentInventoryGatewayRuntime.inventory()),
                (entry, agent) -> AgentPotionService.tickPassiveRecovery(entry, agent),
                (entry, agent) -> AgentBuildService.checkLevelUp(entry, agent),
                (entry, agent, leader) -> AgentChatStatusOrchestrator.tickAfkCheck(entry, leader),
                (entry, agent) -> AgentInventoryTickRuntime.tickTrade(entry, agent),
                (entry, agent) -> AgentInventoryTickRuntime.tickManualTrade(entry, agent),
                (entry, agent, leader) -> AgentPartyQuestHooks.tick(
                        entry,
                        agent,
                        leader,
                        AgentInventoryGatewayRuntime.inventory()),
                tickScriptTasks,
                AgentPartyQuestHooks::isNpcLocked,
                AgentCombatActionLockRuntime::tickActionLock,
                AgentCombatSkillCacheRuntime::rebuildSkillCacheIfNeeded,
                (entry, agent) -> AgentCombatHealRuntime.tickSupportHealing(
                        entry, agent, AgentCombatConfig.cfg),
                (entry, agent) -> AgentCombatBuffRuntime.tickBuffs(
                        entry, agent, AgentCombatConfig.cfg),
                (entry, agent) -> AgentBuffService.tick(entry, agent, AgentInventoryGatewayRuntime.inventory()),
                AgentActionLockPhysicsService::tickActionLocked);
    }
}
