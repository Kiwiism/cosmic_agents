package server.agents.capabilities.combat;

import client.Character;
import client.Skill;
import client.SkillFactory;
import constants.skills.Archer;
import constants.skills.NightWalker;
import constants.skills.Rogue;
import constants.skills.WindArcher;
import server.StatEffect;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import java.util.Map;

public final class AgentProjectileHitbox {
    // Journey client CharStats::get_range() returns Rectangle(-projectilerange, -5, -50, 50).
    public static final int CLIENT_PROJECTILE_BASE_RANGE = 400;
    private static final int CLIENT_PROJECTILE_NEAR_INSET = 5;
    private static final int CLIENT_PROJECTILE_TOP = 50;
    private static final int CLIENT_PROJECTILE_BOTTOM = 50;

    private static final List<Integer> PASSIVE_PROJECTILE_RANGE_SKILL_IDS = List.of(
            Archer.EYE_OF_AMAZON,
            Rogue.KEEN_EYES,
            WindArcher.EYE_OF_AMAZON,
            NightWalker.KEEN_EYES
    );

    private AgentProjectileHitbox() {
    }

    public static Rectangle clientProjectileHitBox(Character agent, boolean facingLeft, float horizontalScale) {
        return clientProjectileHitBox(agent, facingLeft, horizontalScale,
                CLIENT_PROJECTILE_TOP, CLIENT_PROJECTILE_BOTTOM);
    }

    // Vertical extents are signed offsets from the character feet (origin.y):
    //   yAboveOrigin >  0 -> box top is yAboveOrigin px above feet.
    //   yBelowOrigin >  0 -> box bottom is yBelowOrigin px below feet.
    //   yBelowOrigin <  0 -> box bottom is |yBelowOrigin| px above feet.
    public static Rectangle clientProjectileHitBox(Character agent, boolean facingLeft, float horizontalScale,
                                                   int yAboveOrigin, int yBelowOrigin) {
        if (agent == null || agent.getPosition() == null) {
            return null;
        }

        Point origin = agent.getPosition();
        int projectileRange = CLIENT_PROJECTILE_BASE_RANGE + passiveProjectileRangeBonus(agent);
        int farEdge = Math.max(CLIENT_PROJECTILE_NEAR_INSET, Math.round(projectileRange * Math.max(0f, horizontalScale)));
        int left = facingLeft ? origin.x - farEdge : origin.x + CLIENT_PROJECTILE_NEAR_INSET;
        int right = facingLeft ? origin.x - CLIENT_PROJECTILE_NEAR_INSET : origin.x + farEdge;
        int top = origin.y - yAboveOrigin;
        int height = Math.max(1, yAboveOrigin + yBelowOrigin);
        return new Rectangle(left, top, right - left, height);
    }

    public static float projectileRangeScale(StatEffect effect) {
        return effect != null && effect.getRange() > 0 ? effect.getRange() / 100.0f : 1.0f;
    }

    public static int passiveProjectileRangeBonus(Character agent) {
        if (agent == null) {
            return 0;
        }

        int bonus = 0;
        for (int skillId : PASSIVE_PROJECTILE_RANGE_SKILL_IDS) {
            Skill skill = resolveLearnedSkill(agent, skillId);
            if (skill == null) {
                continue;
            }

            int level = agent.getSkillLevel(skill);
            if (level <= 0) {
                continue;
            }

            bonus += Math.max(0, skill.getEffect(level).getRange());
        }
        return bonus;
    }

    private static Skill resolveLearnedSkill(Character agent, int skillId) {
        Map<Skill, Character.SkillEntry> skills = agent.getSkills();
        if (skills != null) {
            for (Skill learned : skills.keySet()) {
                if (learned != null && learned.getId() == skillId) {
                    return learned;
                }
            }
        }

        return SkillFactory.getSkill(skillId);
    }
}
