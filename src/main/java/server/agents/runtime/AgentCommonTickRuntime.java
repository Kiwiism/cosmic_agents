package server.agents.runtime;

import client.Character;
import server.agents.capabilities.build.AgentBuildService;
import server.agents.capabilities.combat.AgentBuffService;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.capabilities.inventory.AgentInventoryTickRuntime;
import server.agents.capabilities.partyquest.AgentPartyQuestHooks;
import server.agents.capabilities.supplies.AgentPotionService;
import server.agents.integration.AgentBotCombatActionLockRuntime;
import server.agents.integration.AgentBotCombatBuffRuntime;
import server.agents.integration.AgentBotCombatDamageRuntime;
import server.agents.integration.AgentBotCombatDeathRuntime;
import server.agents.integration.AgentBotCombatHealRuntime;
import server.agents.integration.AgentBotCombatSkillCacheRuntime;
import server.agents.integration.AgentBotDeathStateRuntime;
import server.agents.integration.AgentBotManagerStatusRuntime;
import server.bots.BotEntry;
import server.bots.BotMovementManager;

import java.util.function.Consumer;

public final class AgentCommonTickRuntime {
    private AgentCommonTickRuntime() {
    }

    public static boolean runCommonTickSystems(BotEntry entry,
                                               Character agent,
                                               Character leader,
                                               boolean runAiTick,
                                               Consumer<BotEntry> tickScriptTasks) {
        return AgentCommonTickService.runCommonTickSystems(
                entry,
                agent,
                leader,
                runAiTick,
                hooks(tickScriptTasks));
    }

    private static AgentCommonTickService.CommonTickHooks hooks(Consumer<BotEntry> tickScriptTasks) {
        return new AgentCommonTickService.CommonTickHooks(
                (entry, agent) -> AgentBotCombatDamageRuntime.tickMobDamage(
                        entry, agent, AgentCombatConfig.cfg, BotMovementManager::tickDown),
                (entry, agent) -> AgentBotDeathStateRuntime.isDead(entry),
                (entry, agent) -> AgentBotCombatDeathRuntime.enterDeadState(
                        entry, agent, false, AgentCombatConfig.cfg),
                AgentMonsterControlService::releaseControlledMonsters,
                AgentInventoryTickRuntime::tickPassiveLoot,
                AgentPotionService::tickPotionCheck,
                AgentPotionService::tickPassiveRecovery,
                AgentBuildService::checkLevelUp,
                (entry, agent, leader) -> AgentBotManagerStatusRuntime.tickAfkCheck(entry, leader),
                AgentInventoryTickRuntime::tickTrade,
                AgentInventoryTickRuntime::tickManualTrade,
                AgentPartyQuestHooks::tick,
                tickScriptTasks,
                AgentPartyQuestHooks::isNpcLocked,
                AgentBotCombatActionLockRuntime::tickActionLock,
                AgentBotCombatSkillCacheRuntime::rebuildSkillCacheIfNeeded,
                (entry, agent) -> AgentBotCombatHealRuntime.tickSupportHealing(
                        entry, agent, AgentCombatConfig.cfg),
                (entry, agent) -> AgentBotCombatBuffRuntime.tickBuffs(
                        entry, agent, AgentCombatConfig.cfg),
                AgentBuffService::tick,
                AgentActionLockPhysicsRuntime::tickActionLocked);
    }
}
