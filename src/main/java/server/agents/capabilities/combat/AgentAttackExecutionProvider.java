package server.agents.capabilities.combat;

import client.BuffStat;
import client.Character;
import client.Skill;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.WeaponType;
import constants.skills.Crossbowman;
import constants.skills.Hunter;
import net.server.channel.handlers.AbstractDealDamageHandler;
import net.server.channel.handlers.CloseRangeDamageHandler;
import net.server.channel.handlers.MagicDamageHandler;
import net.server.channel.handlers.RangedAttackHandler;
import server.agents.capabilities.combat.data.AgentAttackDataProvider;
import server.agents.capabilities.combat.data.AgentAttackTiming;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class AgentAttackExecutionProvider {
    // This server's close-range packet path uses:
    // byte 2 = body action id from Character/00002000.img
    // byte 3 = facing mask (0 / 0x80)
    public record CloseRangePacketFields(int display, int bodyActionId, int facingMask) {
    }

    public record SkillAttackTiming(int hitDelayMs, int cooldownMs) {
    }

    public record BasicAttackData(Rectangle hitBox, int display, int direction, int rangedDirection, String action,
                           int stance, int speed, int hitDelayMs, int cooldownMs, AgentAttackRoute route) {
    }

    private AgentAttackExecutionProvider() {
    }

    public static BasicAttackData buildBasicAttackData(Character bot, Point targetPosition) {
        Item weapon = bot.getInventory(InventoryType.EQUIPPED).getItem((short) -11);
        if (weapon == null) {
            return fallbackBasicAttackData(targetPosition.x < bot.getPosition().x, 4, null, bot, targetPosition);
        }

        AgentAttackDataProvider.NormalAttackProfile attackProfile =
                AgentAttackDataProvider.getInstance().getNormalAttackProfile(weapon.getItemId());
        if (attackProfile == null) {
            return fallbackBasicAttackData(targetPosition.x < bot.getPosition().x, 4, null, bot, targetPosition);
        }

        WeaponType weaponType = server.ItemInformationProvider.getInstance().getWeaponType(weapon.getItemId());
        boolean facingLeft = targetPosition.x < bot.getPosition().x;
        return buildBasicAttackDataFromProfile(attackProfile, weaponType, facingLeft, bot, targetPosition);
    }

    private static BasicAttackData buildBasicAttackDataFromProfile(AgentAttackDataProvider.NormalAttackProfile profile,
                                                                   WeaponType weaponType, boolean facingLeft,
                                                                   Character bot, Point targetPosition) {
        int baseDisplay = profile.getAttack();
        AgentAttackDataProvider provider = AgentAttackDataProvider.getInstance();
        boolean useDegenerateCloseRange = shouldDegenerateRangedAttack(weaponType,
                bot != null ? bot.getPosition() : null, targetPosition)
                || shouldDegenerateForNoAmmo(weaponType, bot);
        AgentAttackDataProvider.AttackAnimationSpec attackSpec =
                provider.getBasicAttackSpec(baseDisplay, weaponType, useDegenerateCloseRange);
        if (baseDisplay <= 0) {
            return fallbackBasicAttackData(facingLeft, profile.getAttackSpeed(), weaponType, bot, targetPosition);
        }

        String fallbackAction = attackSpec.primaryAction();
        List<String> candidateActions = resolveAttackActions(attackSpec, profile.getSourceActions());
        String action = sampleAttackAction(candidateActions, fallbackAction);
        AgentAttackRoute route = useDegenerateCloseRange
                ? AgentAttackRoute.CLOSE
                : determineBasicWeaponRoute(weaponType);
        boolean closeRangeRoute = route == AgentAttackRoute.CLOSE;
        CloseRangePacketFields closeRangePacketFields = mimicCloseRangePacketFields(action, fallbackAction, facingLeft);
        int display = closeRangeRoute ? closeRangePacketFields.display() : 0;
        int direction = closeRangeRoute
                ? closeRangePacketFields.bodyActionId()
                : bodyActionId(action, fallbackAction, weaponType);
        int effectiveAttackSpeed = resolveEffectiveAttackSpeed(profile.getAttackSpeed(), bot);

        int rawAnimationDelayMs = provider.getBodyStanceDurationMs(action);
        if (rawAnimationDelayMs <= 0) {
            rawAnimationDelayMs = 600;
        }
        int rawHitDelayMs = provider.getBodyStanceDelayBeforeFrameMs(action, profile.getAfterimageFirstFrame(action));

        int cooldownMs = toCooldownMs(adjustAttackDelayMillis(rawAnimationDelayMs, effectiveAttackSpeed));
        int hitDelayMs = adjustAttackDelayMillis(rawHitDelayMs, effectiveAttackSpeed);
        int stance = attackPacketStance(facingLeft);
        // Ranged route must use clientProjectileHitBox so the bot's reach scales with
        // CLIENT_PROJECTILE_BASE_RANGE + Keen-Eyes bonus. The weapon's afterimage WZ data
        // (e.g. claws share swordOL with melee, which has lt/rb vectors) reports a near-body
        // swing rect that would cap basic claw/bow/etc. reach at ~80 px — so a degenerate
        // close-range hit (out-of-ammo bow/claw meleeing) stays on the flat fallback rect.
        // A true melee weapon uses its sampled action's afterimage swing box: real per-weapon,
        // per-action reach (spear stab long+low vs overhead swing tall+short).
        Rectangle weaponActionHitBox = closeRangeRoute && !useDegenerateCloseRange
                ? profile.calculateActionBoundingBox(action, bot.getPosition(), facingLeft)
                : null;
        Rectangle hitBox = closeRangeRoute
                ? (weaponActionHitBox != null ? weaponActionHitBox : closeRangeBasicHitBox(bot.getPosition(), facingLeft))
                : rangedBasicHitBox(route, bot, facingLeft);

        return new BasicAttackData(hitBox, display, direction, direction, action, stance, effectiveAttackSpeed,
                hitDelayMs, cooldownMs, route);
    }

    private static BasicAttackData fallbackBasicAttackData(boolean facingLeft, int baseAttackSpeed,
                                                           WeaponType weaponType, Character bot, Point targetPosition) {
        AgentAttackDataProvider provider = AgentAttackDataProvider.getInstance();
        boolean useDegenerateCloseRange = shouldDegenerateRangedAttack(weaponType,
                bot != null ? bot.getPosition() : null, targetPosition)
                || shouldDegenerateForNoAmmo(weaponType, bot);
        AgentAttackDataProvider.AttackAnimationSpec attackSpec = provider.getBasicAttackSpec(weaponType, useDegenerateCloseRange);
        String action = sampleAttackAction(attackSpec.actions(), attackSpec.primaryAction());
        AgentAttackRoute route = useDegenerateCloseRange
                ? AgentAttackRoute.CLOSE
                : determineBasicWeaponRoute(weaponType);
        boolean closeRangeRoute = route == AgentAttackRoute.CLOSE;
        CloseRangePacketFields closeRangePacketFields =
                mimicCloseRangePacketFields(action, attackSpec.primaryAction(), facingLeft);
        int display = closeRangeRoute ? closeRangePacketFields.display() : 0;
        int direction = closeRangeRoute
                ? closeRangePacketFields.bodyActionId()
                : bodyActionId(action, attackSpec.primaryAction(), weaponType);
        int effectiveAttackSpeed = resolveEffectiveAttackSpeed(baseAttackSpeed, bot);
        int rawAnimationDelayMs = provider.getBodyStanceDurationMs(action);
        if (rawAnimationDelayMs <= 0) {
            rawAnimationDelayMs = 600;
        }
        int adjustedAnimationDelayMs = adjustAttackDelayMillis(rawAnimationDelayMs, effectiveAttackSpeed);
        Rectangle hitBox = closeRangeRoute && bot != null
                ? closeRangeBasicHitBox(bot.getPosition(), facingLeft)
                : bot != null ? rangedBasicHitBox(route, bot, facingLeft) : null;

        return new BasicAttackData(hitBox, display, direction, direction, action,
                attackPacketStance(facingLeft),
                effectiveAttackSpeed, defaultHitDelayMs(adjustedAnimationDelayMs), toCooldownMs(adjustedAnimationDelayMs),
                route);
    }

    // This is not the same table as the client stance enum. It is the body action ordering
    // from Character/00002000.img and matches the server's packet byte 2 semantics.
    public static int bodyActionId(String actionName, String fallbackAction) {
        return bodyActionId(actionName, fallbackAction, null);
    }

    public static int bodyActionId(String actionName, String fallbackAction, WeaponType weaponType) {
        AgentAttackDataProvider provider = AgentAttackDataProvider.getInstance();
        int actionId = provider.getBodyActionId(actionName, weaponType);
        if (actionId >= 0) {
            return actionId;
        }

        if (fallbackAction != null && !fallbackAction.equals(actionName)) {
            int fallbackActionId = provider.getBodyActionId(fallbackAction, weaponType);
            if (fallbackActionId >= 0) {
                return fallbackActionId;
            }
        }

        return 0;
    }

    public static CloseRangePacketFields mimicCloseRangePacketFields(String actionName, String fallbackAction, boolean facingLeft) {
        return new CloseRangePacketFields(0,
                bodyActionId(actionName, fallbackAction),
                facingLeft ? 0x80 : 0x00);
    }

    // Packet byte 3 on every attack route (close-range, ranged, magic) is purely the
    // facing-direction bit. Per logs/monitored-packets-assasin-* the v83 client renders
    // nothing when this byte carries a non-zero action id on a ranged star throw, even
    // though the server still applies damage. Single source of truth for all attack-plan
    // builders so basic and skill packets stay in lockstep.
    public static int attackPacketStance(boolean facingLeft) {
        return facingLeft ? 0x80 : 0x00;
    }

    public static int facingDirFromAttackPacketStance(int attackPacketStance) {
        return (attackPacketStance & 0x80) != 0 ? -1 : 1;
    }

    public static List<String> resolveAttackActions(AgentAttackDataProvider.AttackAnimationSpec attackSpec, List<String> sourceActions) {
        if (attackSpec == null || attackSpec.actions().isEmpty()) {
            return List.of("swingO1");
        }

        if (sourceActions == null || sourceActions.isEmpty()) {
            return attackSpec.actions();
        }

        List<String> resolvedActions = new ArrayList<>();
        for (String attackAction : attackSpec.actions()) {
            if (sourceActions.contains(attackAction)) {
                resolvedActions.add(attackAction);
            }
        }

        return resolvedActions.isEmpty() ? attackSpec.actions() : List.copyOf(resolvedActions);
    }

    public static String resolveSkillAttackAction(Character bot, Skill skill, int skillLevel, WeaponType weaponType) {
        if (skill != null) {
            boolean twoHanded = isTwoHandedWeapon(bot);
            String skillAction = skill.resolveAnimationAction(skillLevel, twoHanded);
            if (skillAction != null) {
                return skillAction;
            }
        }
        // Bow/crossbow skills that broadcast as melee (Power Knockback) must sample from
        // the degenerate (close-range swing) action set, not the bow's "shoot1/2" ranged
        // actions. A real player firing PK animates a melee swing — sending a ranged
        // shoot action id in a 0xBA close-range broadcast can crash watching v83 clients.
        if (skill != null && FORCED_CLOSE_RANGE_SKILL_IDS.contains(skill.getId())) {
            return sampleDegenerateCloseRangeAction(bot, weaponType);
        }
        return sampleWeaponAttackAction(bot, weaponType);
    }

    private static String sampleDegenerateCloseRangeAction(Character bot, WeaponType weaponType) {
        AgentAttackDataProvider provider = AgentAttackDataProvider.getInstance();
        Item weapon = bot != null ? bot.getInventory(InventoryType.EQUIPPED).getItem((short) -11) : null;
        if (weapon != null) {
            AgentAttackDataProvider.NormalAttackProfile attackProfile =
                    provider.getNormalAttackProfile(weapon.getItemId());
            if (attackProfile != null) {
                AgentAttackDataProvider.AttackAnimationSpec attackSpec =
                        provider.getBasicAttackSpec(attackProfile.getAttack(), weaponType, true);
                return sampleAttackAction(resolveAttackActions(attackSpec, attackProfile.getSourceActions()),
                        attackSpec.primaryAction());
            }
        }
        AgentAttackDataProvider.AttackAnimationSpec attackSpec = provider.getBasicAttackSpec(weaponType, true);
        return sampleAttackAction(attackSpec.actions(), attackSpec.primaryAction());
    }

    public static void applyAttackRoute(AgentAttackRoute route, AbstractDealDamageHandler.AttackInfo attack, Character bot) {
        switch (route) {
            case RANGED -> RangedAttackHandler.applyRangedAttackEffects(attack, bot, bot.getClient());
            case MAGIC -> MagicDamageHandler.applyMagicAttackEffects(attack, bot, bot.getClient());
            default -> CloseRangeDamageHandler.applyCloseRangeEffects(attack, bot, bot.getClient());
        }
    }

    public static AgentAttackRoute determineBasicAttackRoute(Character bot) {
        return determineBasicWeaponRoute(getEquippedWeaponType(bot));
    }

    public static boolean canUseRangedAttackRoute(AgentAttackRoute route, WeaponType weaponType, Point botPos, Point targetPos) {
        return route != AgentAttackRoute.RANGED || !shouldDegenerateRangedAttack(weaponType, botPos, targetPos);
    }

    // Skills the bow/crossbow classes cast as a melee swing on the client (opcode 0x2C,
    // broadcast via CLOSE_RANGE_ATTACK 0xBA) even though the equipped weapon is ranged.
    // Power Knockback fires no projectile and uses skill.range as the close-range reach
    // (e.g. 130 px at level 20). Treat them as CLOSE so reach gating and packet route match
    // the v83 client.
    private static final Set<Integer> FORCED_CLOSE_RANGE_SKILL_IDS = Set.of(
            Hunter.POWER_KNOCKBACK,
            Crossbowman.POWER_KNOCKBACK
    );

    public static AgentAttackRoute determineSkillRoute(Character bot, int skillId) {
        if (FORCED_CLOSE_RANGE_SKILL_IDS.contains(skillId)) {
            return AgentAttackRoute.CLOSE;
        }
        if (isRangedSkill(skillId)) {
            return AgentAttackRoute.RANGED;
        }

        WeaponType weaponType = getEquippedWeaponType(bot);
        if (weaponType == WeaponType.WAND || weaponType == WeaponType.STAFF) {
            return isMagicAttackSkill(skillId)
                    ? AgentAttackRoute.MAGIC
                    : AgentAttackRoute.CLOSE;
        }

        return determineWeaponRoute(weaponType);
    }

    private static boolean isMagicAttackSkill(int skillId) {
        int job = skillId / 10000;
        int family = job / 100;
        return family == 2 || family == 12 || family == 22;
    }

    public static AgentAttackRoute determineBasicWeaponRoute(WeaponType weaponType) {
        if (weaponType == null) {
            return AgentAttackRoute.CLOSE;
        }

        return switch (weaponType) {
            case BOW, CROSSBOW, CLAW, GUN -> AgentAttackRoute.RANGED;
            default -> AgentAttackRoute.CLOSE;
        };
    }

    public static WeaponType getEquippedWeaponType(Character bot) {
        Item weapon = bot.getInventory(InventoryType.EQUIPPED).getItem((short) -11);
        if (weapon == null) {
            return null;
        }

        return server.ItemInformationProvider.getInstance().getWeaponType(weapon.getItemId());
    }

    public static boolean shouldDegenerateRangedAttack(WeaponType weaponType, Point botPos, Point targetPos) {
        if (!isDegenerateCapableRangedWeapon(weaponType) || botPos == null || targetPos == null) {
            return false;
        }

        int dx = Math.abs(targetPos.x - botPos.x);
        int dy = Math.abs(targetPos.y - botPos.y);
        return dx <= AgentCombatConfig.cfg.RANGED_DEGENERATE_RANGE_X
                && dy <= AgentCombatConfig.cfg.RANGED_DEGENERATE_RANGE_Y;
    }

    public static boolean shouldRetreatFromNearbyTarget(WeaponType weaponType, Point botPos, Point targetPos) {
        if (!isDegenerateCapableRangedWeapon(weaponType) || botPos == null || targetPos == null) {
            return false;
        }

        int dx = Math.abs(targetPos.x - botPos.x);
        int dy = Math.abs(targetPos.y - botPos.y);
        return dx <= AgentCombatConfig.cfg.RANGED_RETREAT_THRESHOLD_X
                && dy <= AgentCombatConfig.cfg.RANGED_DEGENERATE_RANGE_Y;
    }

    public static boolean isAnyMobNearerThanTarget(Character bot, Point botPos, Point targetPos) {
        return findCloserThreatMob(bot, botPos, targetPos) != null;
    }

    /**
     * Returns the closest live mob breaching the retreat band that is nearer to the bot
     * than the active target, or null if none. Same gates as {@link #isAnyMobNearerThanTarget}
     * (bow/crossbow/claw/gun only). Used by grind mode to swap onto a crowding threat
     * instead of fleeing the original target while shooting in the wrong direction.
     */
    public static server.life.Monster findCloserThreatMob(Character bot, Point botPos, Point targetPos) {
        if (bot == null || botPos == null || targetPos == null) {
            return null;
        }
        WeaponType wt = getEquippedWeaponType(bot);
        if (!isDegenerateCapableRangedWeapon(wt)) {
            return null;
        }
        int threshX = AgentCombatConfig.cfg.RANGED_RETREAT_THRESHOLD_X;
        int threshY = AgentCombatConfig.cfg.RANGED_DEGENERATE_RANGE_Y;
        double targetDistSq = targetPos.distanceSq(botPos);
        server.life.Monster closest = null;
        double closestDistSq = targetDistSq;
        for (server.life.Monster m : bot.getMap().getAllMonsters()) {
            if (!m.isAlive()) continue;
            Point mp = m.getPosition();
            double mDistSq = mp.distanceSq(botPos);
            if (mDistSq >= closestDistSq) continue;
            int dx = Math.abs(mp.x - botPos.x);
            int dy = Math.abs(mp.y - botPos.y);
            if (dx <= threshX && dy <= threshY) {
                closest = m;
                closestDistSq = mDistSq;
            }
        }
        return closest;
    }

    public static Point retreatTargetPosition(Point botPos, Point targetPos) {
        return retreatTargetPosition(null, botPos, targetPos);
    }

    /**
     * Cluster-aware retreat: picks a direction toward the more open side, then sweeps
     * candidate distances within that side and lands at the X with the largest gap to
     * any nearby mob. Falls back to the fixed-distance step when {@code bot} is null
     * or no candidate sweep is possible.
     */
    public static Point retreatTargetPosition(Character bot, Point botPos, Point targetPos) {
        int direction = pickRetreatDirection(bot, botPos, targetPos);
        int defaultStep = AgentCombatConfig.cfg.RANGED_RETREAT_DISTANCE_X;
        if (bot == null || bot.getMap() == null) {
            return new Point(botPos.x + direction * defaultStep, botPos.y);
        }

        int minStep = AgentCombatConfig.cfg.RANGED_DEGENERATE_RANGE_X + 20;
        int maxStep = defaultStep * 2;
        int yBand = AgentCombatConfig.cfg.RANGED_DEGENERATE_RANGE_Y * 2;
        int bestX = botPos.x + direction * defaultStep;
        long bestScore = Long.MIN_VALUE;
        for (int step = minStep; step <= maxStep; step += 30) {
            int candX = botPos.x + direction * step;
            long minMobDistSq = Long.MAX_VALUE;
            for (server.life.Monster m : bot.getMap().getAllMonsters()) {
                if (!m.isAlive()) continue;
                Point mp = m.getPosition();
                int dy = Math.abs(mp.y - botPos.y);
                if (dy > yBand) continue;
                long dx = mp.x - candX;
                long sq = dx * dx + (long) dy * dy;
                if (sq < minMobDistSq) minMobDistSq = sq;
            }
            // Maximize gap to nearest mob; tie-break toward closer to active target so DPS lands.
            int dxToTarget = Math.abs(targetPos.x - candX);
            long score = minMobDistSq - dxToTarget * 10L;
            if (score > bestScore) {
                bestScore = score;
                bestX = candX;
            }
        }
        return new Point(bestX, botPos.y);
    }

    public static int pickRetreatDirection(Character bot, Point botPos, Point targetPos) {
        int defaultDir = targetPos.x >= botPos.x ? -1 : 1;
        if (bot == null || bot.getMap() == null) {
            return defaultDir;
        }
        FlankScan scan = scanFlankingMobs(bot, botPos);
        if (scan.leftNearestSq == scan.rightNearestSq) {
            return defaultDir;
        }
        return scan.leftNearestSq > scan.rightNearestSq ? -1 : 1;
    }

    /**
     * True when live mobs breach the tight retreat band ({@code dx <= RETREAT_THRESHOLD_X},
     * {@code dy <= DEGENERATE_RANGE_Y}) on BOTH horizontal sides — the bot is pincered and
     * a one-step local retreat just walks it into the other wall. Bow/crossbow/claw/gun only.
     */
    public static boolean isSurrounded(Character bot, Point botPos) {
        if (!isDegenerateCapableRangedWeapon(getEquippedWeaponType(bot)) || botPos == null) {
            return false;
        }
        FlankScan scan = scanFlankingMobs(bot, botPos);
        return scan.leftInBand && scan.rightInBand;
    }

    /** One pass over nearby live mobs: nearest distance per side (wide scan, for retreat
     *  direction) plus whether the tight retreat band is breached per side (for surround
     *  detection). Mobs exactly on the bot's x are ignored, as the direction scan always has. */
    private static FlankScan scanFlankingMobs(Character bot, Point botPos) {
        FlankScan scan = new FlankScan();
        if (bot == null || bot.getMap() == null || botPos == null) {
            return scan;
        }
        int scanWidth = AgentCombatConfig.cfg.ATTACK_RANGE_X * 4;
        int scanHeight = AgentCombatConfig.cfg.RANGED_DEGENERATE_RANGE_Y * 2;
        int bandX = AgentCombatConfig.cfg.RANGED_RETREAT_THRESHOLD_X;
        int bandY = AgentCombatConfig.cfg.RANGED_DEGENERATE_RANGE_Y;
        for (server.life.Monster m : bot.getMap().getAllMonsters()) {
            if (!m.isAlive()) continue;
            Point mp = m.getPosition();
            int dy = Math.abs(mp.y - botPos.y);
            if (dy > scanHeight) continue;
            int dx = mp.x - botPos.x;
            int dxAbs = Math.abs(dx);
            if (dxAbs > scanWidth) continue;
            long distSq = (long) dx * dx + (long) dy * dy;
            boolean inBand = dxAbs <= bandX && dy <= bandY;
            if (dx < 0) {
                if (distSq < scan.leftNearestSq) scan.leftNearestSq = distSq;
                if (inBand) scan.leftInBand = true;
            } else if (dx > 0) {
                if (distSq < scan.rightNearestSq) scan.rightNearestSq = distSq;
                if (inBand) scan.rightInBand = true;
            }
        }
        return scan;
    }

    private static final class FlankScan {
        long leftNearestSq = Long.MAX_VALUE;
        long rightNearestSq = Long.MAX_VALUE;
        boolean leftInBand = false;
        boolean rightInBand = false;
    }

    public static SkillAttackTiming resolveSkillAttackTiming(Skill skill, String action, Character bot,
                                                      BasicAttackData fallbackAttackData) {
        int fallbackHitDelayMs = fallbackAttackData != null ? fallbackAttackData.hitDelayMs() : defaultHitDelayMs(600);
        int fallbackCooldownMs = fallbackAttackData != null ? fallbackAttackData.cooldownMs() : toCooldownMs(600);
        return resolveSkillAttackTiming(action, resolveWeaponAttackProfile(bot), resolveSkillAttackDelayMillis(skill),
                resolveWeaponAttackSpeed(bot),
                fallbackHitDelayMs, fallbackCooldownMs);
    }

    public static SkillAttackTiming resolveSkillAttackTiming(String action,
                                                      AgentAttackDataProvider.NormalAttackProfile weaponAttackProfile,
                                                      int rawSkillDelayMs,
                                                      int effectiveWeaponAttackSpeed,
                                                      int fallbackHitDelayMs, int fallbackCooldownMs) {
        AgentAttackDataProvider provider = AgentAttackDataProvider.getInstance();
        int rawActionCooldownMs = provider.getBodyActionDurationMs(action);
        if (rawActionCooldownMs > 0) {
            int rawActionHitDelayMs = provider.getBodyActionAttackDelayMs(action, 0);
            int adjustedActionCooldownMs = adjustAttackDelayMillis(rawActionCooldownMs, effectiveWeaponAttackSpeed);
            int adjustedActionHitDelayMs = rawActionHitDelayMs >= 0
                    ? adjustAttackDelayMillis(rawActionHitDelayMs, effectiveWeaponAttackSpeed)
                    : defaultHitDelayMs(adjustedActionCooldownMs);
            return new SkillAttackTiming(adjustedActionHitDelayMs,
                    Math.max(toCooldownMs(adjustedActionCooldownMs), fallbackCooldownMs));
        }

        int rawStanceCooldownMs = provider.getBodyStanceDurationMs(action);
        if (rawStanceCooldownMs > 0) {
            int firstFrame = weaponAttackProfile != null ? weaponAttackProfile.getAfterimageFirstFrame(action) : 0;
            int rawStanceHitDelayMs = provider.getBodyStanceDelayBeforeFrameMs(action, firstFrame);
            int adjustedStanceCooldownMs = adjustAttackDelayMillis(rawStanceCooldownMs, effectiveWeaponAttackSpeed);
            int adjustedStanceHitDelayMs = rawStanceHitDelayMs > 0
                    ? adjustAttackDelayMillis(rawStanceHitDelayMs, effectiveWeaponAttackSpeed)
                    : fallbackHitDelayMs;
            return new SkillAttackTiming(adjustedStanceHitDelayMs,
                    Math.max(toCooldownMs(adjustedStanceCooldownMs), fallbackCooldownMs));
        }

        return resolveSkillAttackTiming(rawSkillDelayMs, effectiveWeaponAttackSpeed,
                fallbackHitDelayMs, fallbackCooldownMs);
    }

    public static SkillAttackTiming resolveSkillAttackTiming(int rawSkillDelayMs,
                                                      int effectiveWeaponAttackSpeed,
                                                      int fallbackHitDelayMs, int fallbackCooldownMs) {
        if (rawSkillDelayMs <= 0) {
            return new SkillAttackTiming(fallbackHitDelayMs, fallbackCooldownMs);
        }

        int adjustedSkillDelayMs = adjustAttackDelayMillis(rawSkillDelayMs, effectiveWeaponAttackSpeed);
        return new SkillAttackTiming(defaultHitDelayMs(adjustedSkillDelayMs),
                Math.max(toCooldownMs(adjustedSkillDelayMs), fallbackCooldownMs));
    }

    private static String sampleAttackAction(List<String> candidateActions, String fallbackAction) {
        if (candidateActions == null || candidateActions.isEmpty()) {
            return fallbackAction;
        }

        int variantOffset = ThreadLocalRandom.current().nextInt(candidateActions.size());
        return candidateActions.get(variantOffset);
    }

    private static AgentAttackDataProvider.AttackAnimationSpec resolveWeaponAttackSpec(Character bot, WeaponType weaponType) {
        AgentAttackDataProvider provider = AgentAttackDataProvider.getInstance();
        Item weapon = bot != null ? bot.getInventory(InventoryType.EQUIPPED).getItem((short) -11) : null;
        if (weapon != null) {
            AgentAttackDataProvider.NormalAttackProfile attackProfile =
                    provider.getNormalAttackProfile(weapon.getItemId());
            if (attackProfile != null && attackProfile.getAttack() > 0) {
                return provider.getBasicAttackSpec(attackProfile.getAttack(), weaponType);
            }
        }
        return provider.getBasicAttackSpec(weaponType);
    }

    private static String sampleWeaponAttackAction(Character bot, WeaponType weaponType) {
        AgentAttackDataProvider provider = AgentAttackDataProvider.getInstance();
        Item weapon = bot != null ? bot.getInventory(InventoryType.EQUIPPED).getItem((short) -11) : null;
        if (weapon != null) {
            AgentAttackDataProvider.NormalAttackProfile attackProfile =
                    provider.getNormalAttackProfile(weapon.getItemId());
            if (attackProfile != null) {
                AgentAttackDataProvider.AttackAnimationSpec attackSpec = provider.getBasicAttackSpec(attackProfile.getAttack(), weaponType);
                return sampleAttackAction(resolveAttackActions(attackSpec, attackProfile.getSourceActions()),
                        attackSpec.primaryAction());
            }
        }

        AgentAttackDataProvider.AttackAnimationSpec attackSpec = provider.getBasicAttackSpec(weaponType);
        return sampleAttackAction(attackSpec.actions(), attackSpec.primaryAction());
    }

    private static AgentAttackRoute determineWeaponRoute(WeaponType weaponType) {
        if (weaponType == null) {
            return AgentAttackRoute.CLOSE;
        }

        return switch (weaponType) {
            case BOW, CROSSBOW, CLAW, GUN -> AgentAttackRoute.RANGED;
            case WAND, STAFF -> AgentAttackRoute.MAGIC;
            default -> AgentAttackRoute.CLOSE;
        };
    }

    private static boolean isTwoHandedWeapon(Character bot) {
        Item weapon = bot != null ? bot.getInventory(InventoryType.EQUIPPED).getItem((short) -11) : null;
        if (weapon == null) {
            return false;
        }

        return server.ItemInformationProvider.getInstance().isTwoHanded(weapon.getItemId());
    }

    private static boolean isRangedSkill(int skillId) {
        return switch (skillId) {
            case constants.skills.Buccaneer.ENERGY_ORB,
                    constants.skills.ThunderBreaker.SPARK,
                    constants.skills.ThunderBreaker.SHARK_WAVE,
                    constants.skills.Shadower.TAUNT,
                    constants.skills.NightLord.TAUNT,
                    constants.skills.Aran.COMBO_SMASH,
                    constants.skills.Aran.COMBO_FENRIR,
                    constants.skills.Aran.COMBO_TEMPEST -> true;
            default -> false;
        };
    }

    private static boolean isDegenerateCapableRangedWeapon(WeaponType weaponType) {
        return weaponType == WeaponType.BOW
                || weaponType == WeaponType.CROSSBOW
                || weaponType == WeaponType.CLAW
                || weaponType == WeaponType.GUN;
    }

    private static boolean shouldDegenerateForNoAmmo(WeaponType weaponType, Character bot) {
        if (bot == null || !isDegenerateCapableRangedWeapon(weaponType)) {
            return false;
        }
        return AgentCombatAmmoCounter.countAmmo(bot, weaponType) <= 0;
    }

    private static Rectangle closeRangeBasicHitBox(Point origin, boolean facingLeft) {
        int horizontalRange = AgentCombatConfig.cfg.ATTACK_RANGE_X;
        int top = origin.y - AgentCombatConfig.cfg.ATTACK_RANGE_Y;
        int height = AgentCombatConfig.cfg.ATTACK_RANGE_Y + AgentCombatConfig.cfg.ATTACK_DOWN_MAX;
        int left = facingLeft ? origin.x - horizontalRange : origin.x;
        return new Rectangle(left, top, horizontalRange, height);
    }

    // Real melee reach comes from the equipped weapon's afterimage swing box (Character.wz),
    // which is per-weapon (spear/polearm ~150 px, sword ~120, dagger/knuckle ~64) and per-action
    // (stab vs overhead swing differ). Returns null when the weapon has no melee afterimage bounds
    // (ranged weapons), so callers can keep their flat-rect / skill-range fallback.
    public static Rectangle closeRangeWeaponActionHitBox(Character bot, String action, boolean facingLeft) {
        if (bot == null || bot.getPosition() == null) {
            return null;
        }
        AgentAttackDataProvider.NormalAttackProfile profile = equippedNormalAttackProfile(bot);
        if (profile == null) {
            return null;
        }
        return profile.calculateActionBoundingBox(action, bot.getPosition(), facingLeft);
    }

    private static AgentAttackDataProvider.NormalAttackProfile equippedNormalAttackProfile(Character bot) {
        Inventory equipped = bot.getInventory(InventoryType.EQUIPPED);
        Item weapon = equipped != null ? equipped.getItem((short) -11) : null;
        if (weapon == null) {
            return null;
        }
        return AgentAttackDataProvider.getInstance().getNormalAttackProfile(weapon.getItemId());
    }

    private static Rectangle rangedBasicHitBox(AgentAttackRoute route, Character bot, boolean facingLeft) {
        if (bot == null || bot.getPosition() == null) {
            return null;
        }

        return AgentProjectileHitbox.clientProjectileHitBox(bot, facingLeft, 1.0f);
    }

    private static int resolveSkillAttackDelayMillis(Skill skill) {
        if (skill == null) {
            return 0;
        }
        return Math.max(0, skill.getAnimationTime());
    }

    private static int toCooldownMs(int attackDelayMillis) {
        // Attack cooldown is assigned after BotCombatManager.tickActionLock() has already
        // consumed this server tick. Do not subtract TICK_MS here, or the next AI pass can
        // start a new attack on the same tick the previous animation expires, skipping the
        // short stand/recovery frame a real client shows between attacks.
        return Math.max(0, attackDelayMillis);
    }

    private static int defaultHitDelayMs(int animationDelayMs) {
        if (animationDelayMs <= 0) {
            return 305;
        }
        return Math.max(0, animationDelayMs / 2);
    }

    private static int resolveWeaponAttackSpeed(Character bot) {
        return resolveEffectiveAttackSpeed(resolveBaseWeaponAttackSpeed(bot), bot);
    }

    private static int resolveBaseWeaponAttackSpeed(Character bot) {
        AgentAttackDataProvider.NormalAttackProfile attackProfile = resolveWeaponAttackProfile(bot);
        if (attackProfile == null) {
            return 4;
        }

        return attackProfile.getAttackSpeed();
    }

    private static AgentAttackDataProvider.NormalAttackProfile resolveWeaponAttackProfile(Character bot) {
        if (bot == null) {
            return null;
        }

        Item weapon = bot.getInventory(InventoryType.EQUIPPED).getItem((short) -11);
        if (weapon == null) {
            return null;
        }

        return AgentAttackDataProvider.getInstance().getNormalAttackProfile(weapon.getItemId());
    }

    private static int normalizeAttackSpeed(int attackSpeed) {
        return AgentAttackTiming.normalizeAttackSpeed(attackSpeed);
    }

    private static int resolveEffectiveAttackSpeed(int baseAttackSpeed, Character bot) {
        int normalizedBaseSpeed = normalizeAttackSpeed(baseAttackSpeed);
        if (bot == null) {
            return normalizedBaseSpeed;
        }

        int speedDelta = 0;
        Integer booster = bot.getBuffedValue(BuffStat.BOOSTER);
        if (booster != null) {
            speedDelta += booster;
        }
        Integer speedInfusion = bot.getBuffedValue(BuffStat.SPEED_INFUSION);
        if (speedInfusion != null) {
            speedDelta += speedInfusion;
        }

        return Math.max(2, normalizedBaseSpeed + speedDelta);
    }

    private static int adjustAttackDelayMillis(int baseDelayMillis, int effectiveAttackSpeed) {
        return AgentAttackTiming.adjustDelayMillis(baseDelayMillis, effectiveAttackSpeed);
    }

}
