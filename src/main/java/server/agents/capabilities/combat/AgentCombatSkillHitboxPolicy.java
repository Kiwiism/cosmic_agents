package server.agents.capabilities.combat;

import client.Character;
import constants.skills.Crossbowman;
import constants.skills.Hermit;
import constants.skills.Hunter;
import constants.skills.NightWalker;
import server.StatEffect;
import server.life.Monster;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.Map;
import java.util.Set;

public final class AgentCombatSkillHitboxPolicy {
    // Pierce-line projectiles do NOT use the generic +/-50 px vertical band: the actual
    // client projectile sprite is much thinner (Iron Arrow) or much taller (Avenger).
    private record ProjectileVerticalReach(int yAbove, int yBelow) { }

    private static final Map<Integer, ProjectileVerticalReach> PIERCE_LINE_PROJECTILE_REACH = Map.of(
            Crossbowman.IRON_ARROW, new ProjectileVerticalReach(32, -28),
            Hermit.AVENGER, new ProjectileVerticalReach(60, 0),
            NightWalker.AVENGER, new ProjectileVerticalReach(60, 0)
    );

    private static final Set<Integer> STRIKE_POINT_ANCHORED_AOE_SKILL_IDS = Set.of(
            Hunter.ARROW_BOMB
    );

    private AgentCombatSkillHitboxPolicy() {
    }

    public static Rectangle calculateSkillHitBox(StatEffect effect, Character agent, Monster primaryTarget,
                                                 AgentAttackRoute route, int skillId, String action) {
        boolean facingLeft = primaryTarget.getPosition().x < agent.getPosition().x;
        if (effect.hasBoundingBox()) {
            Point anchor = isStrikePointAnchoredAoeSkill(skillId)
                    ? primaryTarget.getPosition()
                    : agent.getPosition();
            return effect.calculateBoundingBox(anchor, facingLeft);
        }

        return fallbackSkillHitBox(effect, agent, facingLeft, route, skillId, action);
    }

    public static boolean isStrikePointAnchoredAoeSkill(int skillId) {
        return STRIKE_POINT_ANCHORED_AOE_SKILL_IDS.contains(skillId);
    }

    public static Rectangle fallbackCloseRangeSkillHitBox(StatEffect effect, Character agent, String action,
                                                          boolean facingLeft) {
        if (effect == null || agent == null) {
            return null;
        }

        Rectangle weaponBox = AgentAttackExecutionProvider.closeRangeWeaponActionHitBox(agent, action, facingLeft);
        if (weaponBox != null) {
            return weaponBox;
        }

        Point origin = agent.getPosition();
        int horizontalRange = Math.max(AgentCombatConfig.cfg.ATTACK_RANGE_X, effect.getRange());
        int top = origin.y - AgentCombatConfig.cfg.ATTACK_RANGE_Y;
        int height = AgentCombatConfig.cfg.ATTACK_RANGE_Y + AgentCombatConfig.cfg.ATTACK_DOWN_MAX;
        int left = facingLeft ? origin.x - horizontalRange : origin.x;
        return new Rectangle(left, top, horizontalRange, height);
    }

    public static Rectangle fallbackSkillHitBox(StatEffect effect, Character agent, boolean facingLeft,
                                                AgentAttackRoute route, int skillId, String action) {
        if (route == AgentAttackRoute.CLOSE) {
            return fallbackCloseRangeSkillHitBox(effect, agent, action, facingLeft);
        }
        if (effect == null || agent == null) {
            return null;
        }

        ProjectileVerticalReach reach = PIERCE_LINE_PROJECTILE_REACH.get(skillId);
        if (reach != null) {
            return AgentProjectileHitbox.clientProjectileHitBox(agent, facingLeft,
                    AgentProjectileHitbox.projectileRangeScale(effect), reach.yAbove(), reach.yBelow());
        }
        return AgentProjectileHitbox.clientProjectileHitBox(agent, facingLeft,
                AgentProjectileHitbox.projectileRangeScale(effect));
    }
}
