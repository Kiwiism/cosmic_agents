package server.agents.capabilities.combat;

import client.Character;
import client.Skill;
import server.StatEffect;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentCombatGatewayRuntime;
import server.agents.integration.AgentSkillGatewayRuntime;
import server.agents.perception.AgentMapPerception;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.Monster;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

/**
 * Casts mob debuffs (Slow, Seal, Doom, Ninja Ambush, the Crashes, Threaten, Dispel) and Monster
 * Magnet. Cosmic applies these on a special move: {@code StatEffect.applyMonsterBuff} reads the
 * skill box relative to the caster's facing and applies the status to the mobs inside it. Selection
 * is the caller's; the automatic combat loop does not pick debuffs yet.
 */
public final class AgentCombatDebuffRuntime {
    private static final int MAGNET_RANGE_X = config.AgentTuning.intValue("server.agents.capabilities.combat.AgentCombatDebuffRuntime.MAGNET_RANGE_X");
    private static final int MAGNET_RANGE_Y = config.AgentTuning.intValue("server.agents.capabilities.combat.AgentCombatDebuffRuntime.MAGNET_RANGE_Y");

    private AgentCombatDebuffRuntime() {
    }

    public enum DebuffResult { CAST, NOT_A_DEBUFF, NOT_READY, NO_TARGETS, DISPATCH_FAILED }

    public static DebuffResult tryDebuff(AgentRuntimeEntry entry, Character bot, int skillId) {
        if (entry == null || bot == null || !bot.isAlive() || !AgentClientGatewayRuntime.clients().hasClient(bot)) {
            return DebuffResult.NOT_READY;
        }
        if (!AgentCombatSkillClassifier.isMobDebuffSkill(skillId) && !AgentCombatSkillClassifier.isUtilitySpecialMove(skillId)) {
            return DebuffResult.NOT_A_DEBUFF;
        }
        Skill skill = AgentSkillGatewayRuntime.skills().getSkill(skillId);
        int level = skill == null ? 0 : bot.getSkillLevel(skill);
        if (level <= 0) {
            return DebuffResult.NOT_READY;
        }
        StatEffect effect = skill.getEffect(level);
        if (bot.skillIsCooling(skillId) || !effect.canPaySkillCost(bot) || !effect.hasItemCon(bot)
                || AgentCombatBuffRuntime.airborneOrClimbing(entry)) {
            return DebuffResult.NOT_READY;
        }
        int timestamp = AgentCombatGatewayRuntime.combat().currentTimestamp();
        if (AgentCombatSkillClassifier.isMonsterMagnet(skillId)) {
            List<Integer> oids = magnetTargets(bot, effect.getMobCount());
            if (oids.isEmpty()) {
                return DebuffResult.NO_TARGETS;
            }
            return AgentCombatGatewayRuntime.combat().dispatchMonsterMagnet(bot, skillId, level, timestamp, oids)
                    ? DebuffResult.CAST : DebuffResult.DISPATCH_FAILED;
        }
        return AgentCombatGatewayRuntime.combat().dispatchSummonSpecialMove(bot, skillId, level, timestamp)
                ? DebuffResult.CAST : DebuffResult.DISPATCH_FAILED;
    }

    private static List<Integer> magnetTargets(Character bot, int mobCount) {
        List<Integer> oids = new ArrayList<>();
        Point origin = bot.getPosition();
        if (origin == null || bot.getMap() == null) {
            return oids;
        }
        for (Monster monster : AgentMapPerception.monsters(bot.getMap())) {
            if (!AgentCombatTargetEligibilityPolicy.isHostileLivingMonster(monster) || monster.isBoss()
                    || monster.getPosition() == null) {
                continue;
            }
            if (Math.abs(monster.getPosition().x - origin.x) <= MAGNET_RANGE_X
                    && Math.abs(monster.getPosition().y - origin.y) <= MAGNET_RANGE_Y) {
                oids.add(monster.getObjectId());
                if (oids.size() >= Math.max(1, mobCount)) {
                    break;
                }
            }
        }
        return oids;
    }
}
