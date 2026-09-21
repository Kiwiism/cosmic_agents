package server.agents.capabilities.combat;

import client.Character;
import client.Skill;
import server.StatEffect;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentCombatGatewayRuntime;
import server.agents.integration.AgentSkillGatewayRuntime;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Casts summon skills (Puppet, Silver Hawk, Ifrit, Bahamut, Beholder, Octopus, ...). The skill
 * cache has collected summons for a long time but nothing ever cast them; this is the execution
 * half. Selection stays with the caller: the probe casts explicitly, combat can call
 * {@link #tickSummons} to keep one summon up while fighting.
 */
public final class AgentCombatSummonRuntime {
    private AgentCombatSummonRuntime() {
    }

    public enum SummonResult { CAST, ALREADY_ACTIVE, NOT_A_SUMMON, NOT_READY, DISPATCH_FAILED }

    public static SummonResult trySummon(AgentRuntimeEntry entry, Character bot, int skillId) {
        if (entry == null || bot == null || !bot.isAlive() || !AgentClientGatewayRuntime.clients().hasClient(bot)) {
            return SummonResult.NOT_READY;
        }
        Skill skill = AgentSkillGatewayRuntime.skills().getSkill(skillId);
        int level = skill == null ? 0 : bot.getSkillLevel(skill);
        if (level <= 0) {
            return SummonResult.NOT_READY;
        }
        StatEffect effect = skill.getEffect(level);
        if (!AgentCombatSkillClassifier.isSummonSkill(effect)) {
            return SummonResult.NOT_A_SUMMON;
        }
        if (bot.getSummonByKey(skillId) != null) {
            return SummonResult.ALREADY_ACTIVE;
        }
        if (bot.skillIsCooling(skillId) || !effect.canPaySkillCost(bot) || !effect.hasItemCon(bot)
                || AgentCombatBuffRuntime.airborneOrClimbing(entry)) {
            return SummonResult.NOT_READY;
        }
        int timestamp = AgentCombatGatewayRuntime.combat().currentTimestamp();
        return AgentCombatGatewayRuntime.combat().dispatchSummonSpecialMove(bot, skillId, level, timestamp)
                ? SummonResult.CAST
                : SummonResult.DISPATCH_FAILED;
    }

    /** Keeps the first cached summon up; returns true when a cast was issued this tick. */
    public static boolean tickSummons(AgentRuntimeEntry entry, Character bot) {
        for (int skillId : AgentCombatSkillCacheStateRuntime.summonSkillIds(entry)) {
            if (trySummon(entry, bot, skillId) == SummonResult.CAST) {
                return true;
            }
        }
        return false;
    }
}
