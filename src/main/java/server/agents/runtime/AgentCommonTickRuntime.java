package server.agents.runtime;

import server.agents.capabilities.movement.AgentMovementTimers;

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
                        asBotEntry(entry), agent, AgentCombatConfig.cfg, AgentMovementTimers::tickDown),
                (entry, agent) -> AgentBotDeathStateRuntime.isDead(asBotEntry(entry)),
                (entry, agent) -> AgentBotCombatDeathRuntime.enterDeadState(
                        asBotEntry(entry), agent, false, AgentCombatConfig.cfg),
                AgentMonsterControlService::releaseControlledMonsters,
                (entry, agent) -> AgentInventoryTickRuntime.tickPassiveLoot(asBotEntry(entry), agent),
                (entry, agent) -> AgentPotionService.tickPotionCheck(asBotEntry(entry), agent),
                (entry, agent) -> AgentPotionService.tickPassiveRecovery(asBotEntry(entry), agent),
                (entry, agent) -> AgentBuildService.checkLevelUp(asBotEntry(entry), agent),
                (entry, agent, leader) -> AgentBotManagerStatusRuntime.tickAfkCheck(entry, leader),
                (entry, agent) -> AgentInventoryTickRuntime.tickTrade(asBotEntry(entry), agent),
                (entry, agent) -> AgentInventoryTickRuntime.tickManualTrade(asBotEntry(entry), agent),
                (entry, agent, leader) -> AgentPartyQuestHooks.tick(asBotEntry(entry), agent, leader),
                entry -> tickScriptTasks.accept(asBotEntry(entry)),
                AgentPartyQuestHooks::isNpcLocked,
                entry -> AgentBotCombatActionLockRuntime.tickActionLock(asBotEntry(entry)),
                (entry, agent) -> AgentBotCombatSkillCacheRuntime.rebuildSkillCacheIfNeeded(asBotEntry(entry), agent),
                (entry, agent) -> AgentBotCombatHealRuntime.tickSupportHealing(
                        asBotEntry(entry), agent, AgentCombatConfig.cfg),
                (entry, agent) -> AgentBotCombatBuffRuntime.tickBuffs(
                        asBotEntry(entry), agent, AgentCombatConfig.cfg),
                (entry, agent) -> AgentBuffService.tick(asBotEntry(entry), agent),
                AgentActionLockPhysicsRuntime::tickActionLocked);
    }

    private static BotEntry asBotEntry(AgentRuntimeEntry entry) {
        return (BotEntry) entry;
    }
}
