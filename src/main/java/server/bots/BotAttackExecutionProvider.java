package server.bots;

import client.BuffStat;
import client.Character;
import client.Skill;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.WeaponType;
import net.server.channel.handlers.AbstractDealDamageHandler;
import net.server.channel.handlers.CloseRangeDamageHandler;
import net.server.channel.handlers.MagicDamageHandler;
import net.server.channel.handlers.RangedAttackHandler;
import server.bots.combat.BotAttackDataProvider;
import server.bots.combat.BotAttackTiming;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

final class BotAttackExecutionProvider {
    record CloseRangePacketFields(int display, int direction, int stance) {
    }

    record SkillAttackTiming(int hitDelayMs, int cooldownMs) {
    }

    record BasicAttackData(Rectangle hitBox, int display, int direction, int rangedDirection,
                           int stance, int speed, int hitDelayMs, int cooldownMs, BotCombatManager.AttackRoute route) {
    }

    private BotAttackExecutionProvider() {
    }

    static BasicAttackData buildBasicAttackData(Character bot, Point targetPosition) {
        Item weapon = bot.getInventory(InventoryType.EQUIPPED).getItem((short) -11);
        if (weapon == null) {
            return fallbackBasicAttackData(targetPosition.x < bot.getPosition().x, 4, null, bot, targetPosition);
        }

        BotAttackDataProvider.NormalAttackProfile attackProfile =
                BotAttackDataProvider.getInstance().getNormalAttackProfile(weapon.getItemId());
        if (attackProfile == null) {
            return fallbackBasicAttackData(targetPosition.x < bot.getPosition().x, 4, null, bot, targetPosition);
        }

        WeaponType weaponType = server.ItemInformationProvider.getInstance().getWeaponType(weapon.getItemId());
        boolean facingLeft = targetPosition.x < bot.getPosition().x;
        return buildBasicAttackDataFromProfile(attackProfile, weaponType, facingLeft, bot, targetPosition);
    }

    private static BasicAttackData buildBasicAttackDataFromProfile(BotAttackDataProvider.NormalAttackProfile profile,
                                                                   WeaponType weaponType, boolean facingLeft,
                                                                   Character bot, Point targetPosition) {
        int baseDisplay = profile.getAttack();
        BotAttackDataProvider provider = BotAttackDataProvider.getInstance();
        boolean useDegenerateCloseRange = shouldDegenerateRangedAttack(weaponType,
                bot != null ? bot.getPosition() : null, targetPosition);
        BotAttackDataProvider.AttackAnimationSpec attackSpec =
                provider.getBasicAttackSpec(baseDisplay, weaponType, useDegenerateCloseRange);
        if (baseDisplay <= 0) {
            return fallbackBasicAttackData(facingLeft, profile.getAttackSpeed(), weaponType, bot, targetPosition);
        }

        String fallbackAction = attackSpec.primaryAction();
        List<String> candidateActions = resolveAttackActions(attackSpec, profile.getSourceActions());
        String action = sampleAttackAction(candidateActions, fallbackAction);
        int variantOffset = Math.max(0, attackSpec.actions().indexOf(action));
        BotCombatManager.AttackRoute route = useDegenerateCloseRange
                ? BotCombatManager.AttackRoute.CLOSE
                : determineBasicWeaponRoute(weaponType);
        boolean closeRangeRoute = route == BotCombatManager.AttackRoute.CLOSE;
        CloseRangePacketFields closeRangePacketFields = mimicCloseRangePacketFields(action, fallbackAction, facingLeft);
        int display = closeRangeRoute ? closeRangePacketFields.display() : baseDisplay + variantOffset;
        int direction = closeRangeRoute
                ? closeRangePacketFields.direction()
                : basicAttackDirectionId(action, fallbackAction);
        int effectiveAttackSpeed = resolveEffectiveAttackSpeed(profile.getAttackSpeed(), bot);

        int rawAnimationDelayMs = provider.getBodyStanceDurationMs(action);
        if (rawAnimationDelayMs <= 0) {
            rawAnimationDelayMs = 600;
        }
        int rawHitDelayMs = provider.getBodyStanceDelayBeforeFrameMs(action, profile.getAfterimageFirstFrame(action));

        int cooldownMs = toCooldownMs(adjustAttackDelayMillis(rawAnimationDelayMs, effectiveAttackSpeed));
        int hitDelayMs = adjustAttackDelayMillis(rawHitDelayMs, effectiveAttackSpeed);
        int stance = closeRangeRoute ? closeRangePacketFields.stance() : packetStanceId(action, fallbackAction);
        Rectangle hitBox = closeRangeRoute
                ? closeRangeBasicHitBox(bot.getPosition(), facingLeft)
                : profile.hasBoundingBox()
                ? profile.calculateBoundingBox(bot.getPosition(), facingLeft)
                : rangedBasicHitBox(route, bot, facingLeft);

        return new BasicAttackData(hitBox, display, direction, direction, stance, effectiveAttackSpeed,
                hitDelayMs, cooldownMs, route);
    }

    private static BasicAttackData fallbackBasicAttackData(boolean facingLeft, int baseAttackSpeed,
                                                           WeaponType weaponType, Character bot, Point targetPosition) {
        BotAttackDataProvider provider = BotAttackDataProvider.getInstance();
        boolean useDegenerateCloseRange = shouldDegenerateRangedAttack(weaponType,
                bot != null ? bot.getPosition() : null, targetPosition);
        BotAttackDataProvider.AttackAnimationSpec attackSpec = provider.getBasicAttackSpec(weaponType, useDegenerateCloseRange);
        String action = sampleAttackAction(attackSpec.actions(), attackSpec.primaryAction());
        int variantOffset = Math.max(0, attackSpec.actions().indexOf(action));
        BotCombatManager.AttackRoute route = useDegenerateCloseRange
                ? BotCombatManager.AttackRoute.CLOSE
                : determineBasicWeaponRoute(weaponType);
        boolean closeRangeRoute = route == BotCombatManager.AttackRoute.CLOSE;
        CloseRangePacketFields closeRangePacketFields =
                mimicCloseRangePacketFields(action, attackSpec.primaryAction(), facingLeft);
        int display = closeRangeRoute ? closeRangePacketFields.display() : attackSpec.display() + variantOffset;
        int direction = closeRangeRoute
                ? closeRangePacketFields.direction()
                : basicAttackDirectionId(action, attackSpec.primaryAction());
        int effectiveAttackSpeed = resolveEffectiveAttackSpeed(baseAttackSpeed, bot);
        int rawAnimationDelayMs = provider.getBodyStanceDurationMs(action);
        if (rawAnimationDelayMs <= 0) {
            rawAnimationDelayMs = 600;
        }
        int adjustedAnimationDelayMs = adjustAttackDelayMillis(rawAnimationDelayMs, effectiveAttackSpeed);
        Rectangle hitBox = closeRangeRoute && bot != null
                ? closeRangeBasicHitBox(bot.getPosition(), facingLeft)
                : bot != null ? rangedBasicHitBox(route, bot, facingLeft) : null;

        return new BasicAttackData(hitBox, display, direction, direction,
                closeRangeRoute ? closeRangePacketFields.stance() : packetStanceId(action, attackSpec.primaryAction()),
                effectiveAttackSpeed, defaultHitDelayMs(adjustedAnimationDelayMs), toCooldownMs(adjustedAnimationDelayMs),
                route);
    }

    static int basicAttackDirectionId(String actionName, String fallbackAction) {
        BotAttackDataProvider provider = BotAttackDataProvider.getInstance();
        int actionId = provider.getBodyActionId(actionName);
        if (actionId >= 0) {
            return actionId;
        }

        if (fallbackAction != null && !fallbackAction.equals(actionName)) {
            int fallbackActionId = provider.getBodyActionId(fallbackAction);
            if (fallbackActionId >= 0) {
                return fallbackActionId;
            }
        }

        return 0;
    }

    static CloseRangePacketFields mimicCloseRangePacketFields(String actionName, String fallbackAction, boolean facingLeft) {
        return new CloseRangePacketFields(0,
                basicAttackDirectionId(actionName, fallbackAction),
                facingLeft ? 0x80 : 0x00);
    }

    static int packetStanceId(String actionName, String fallbackAction) {
        BotAttackDataProvider provider = BotAttackDataProvider.getInstance();
        int stanceId = provider.getAttackStanceId(actionName);
        if (stanceId > 0) {
            return stanceId;
        }
        if (fallbackAction != null && !fallbackAction.equals(actionName)) {
            int fallbackStanceId = provider.getAttackStanceId(fallbackAction);
            if (fallbackStanceId > 0) {
                return fallbackStanceId;
            }
        }
        return 0;
    }

    static List<String> resolveAttackActions(BotAttackDataProvider.AttackAnimationSpec attackSpec, List<String> sourceActions) {
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

    static String resolveSkillAttackAction(Character bot, Skill skill, int skillLevel, WeaponType weaponType) {
        if (skill != null) {
            boolean twoHanded = isTwoHandedWeapon(bot);
            String skillAction = skill.resolveAnimationAction(skillLevel, twoHanded);
            if (skillAction != null) {
                return skillAction;
            }
        }
        return sampleWeaponAttackAction(bot, weaponType);
    }

    static void applyAttackRoute(BotCombatManager.AttackRoute route, AbstractDealDamageHandler.AttackInfo attack, Character bot) {
        switch (route) {
            case RANGED -> RangedAttackHandler.applyRangedAttackEffects(attack, bot, bot.getClient());
            case MAGIC -> MagicDamageHandler.applyMagicAttackEffects(attack, bot, bot.getClient());
            default -> CloseRangeDamageHandler.applyCloseRangeEffects(attack, bot, bot.getClient());
        }
    }

    static BotCombatManager.AttackRoute determineBasicAttackRoute(Character bot) {
        return determineBasicWeaponRoute(getEquippedWeaponType(bot));
    }

    static boolean canUseRangedAttackRoute(BotCombatManager.AttackRoute route, WeaponType weaponType, Point botPos, Point targetPos) {
        return route != BotCombatManager.AttackRoute.RANGED || !shouldDegenerateRangedAttack(weaponType, botPos, targetPos);
    }

    static BotCombatManager.AttackRoute determineSkillRoute(Character bot, int skillId) {
        if (isRangedSkill(skillId)) {
            return BotCombatManager.AttackRoute.RANGED;
        }

        WeaponType weaponType = getEquippedWeaponType(bot);
        if (weaponType == WeaponType.WAND || weaponType == WeaponType.STAFF) {
            return BotCombatManager.AttackRoute.MAGIC;
        }

        return determineWeaponRoute(weaponType);
    }

    static BotCombatManager.AttackRoute determineBasicWeaponRoute(WeaponType weaponType) {
        if (weaponType == null) {
            return BotCombatManager.AttackRoute.CLOSE;
        }

        return switch (weaponType) {
            case BOW, CROSSBOW, CLAW, GUN -> BotCombatManager.AttackRoute.RANGED;
            default -> BotCombatManager.AttackRoute.CLOSE;
        };
    }

    static WeaponType getEquippedWeaponType(Character bot) {
        Item weapon = bot.getInventory(InventoryType.EQUIPPED).getItem((short) -11);
        if (weapon == null) {
            return null;
        }

        return server.ItemInformationProvider.getInstance().getWeaponType(weapon.getItemId());
    }

    static boolean shouldDegenerateRangedAttack(WeaponType weaponType, Point botPos, Point targetPos) {
        if (!isDegenerateCapableRangedWeapon(weaponType) || botPos == null || targetPos == null) {
            return false;
        }

        int dx = Math.abs(targetPos.x - botPos.x);
        int dy = Math.abs(targetPos.y - botPos.y);
        return dx <= BotCombatManager.cfg.RANGED_DEGENERATE_RANGE_X
                && dy <= BotCombatManager.cfg.RANGED_DEGENERATE_RANGE_Y;
    }

    static boolean shouldRetreatFromNearbyTarget(WeaponType weaponType, Point botPos, Point targetPos) {
        return shouldDegenerateRangedAttack(weaponType, botPos, targetPos);
    }

    static Point retreatTargetPosition(Point botPos, Point targetPos) {
        int retreatDirection = targetPos.x >= botPos.x ? -1 : 1;
        return new Point(botPos.x + retreatDirection * BotCombatManager.cfg.RANGED_RETREAT_DISTANCE_X, botPos.y);
    }

    static SkillAttackTiming resolveSkillAttackTiming(Skill skill, String action, Character bot,
                                                      BasicAttackData fallbackAttackData) {
        int fallbackHitDelayMs = fallbackAttackData != null ? fallbackAttackData.hitDelayMs() : defaultHitDelayMs(600);
        int fallbackCooldownMs = fallbackAttackData != null ? fallbackAttackData.cooldownMs() : toCooldownMs(600);
        return resolveSkillAttackTiming(action, resolveWeaponAttackProfile(bot), resolveSkillAttackDelayMillis(skill),
                resolveWeaponAttackSpeed(bot),
                fallbackHitDelayMs, fallbackCooldownMs);
    }

    static SkillAttackTiming resolveSkillAttackTiming(String action,
                                                      BotAttackDataProvider.NormalAttackProfile weaponAttackProfile,
                                                      int rawSkillDelayMs,
                                                      int effectiveWeaponAttackSpeed,
                                                      int fallbackHitDelayMs, int fallbackCooldownMs) {
        BotAttackDataProvider provider = BotAttackDataProvider.getInstance();
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

    static SkillAttackTiming resolveSkillAttackTiming(int rawSkillDelayMs,
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

    private static BotAttackDataProvider.AttackAnimationSpec resolveWeaponAttackSpec(Character bot, WeaponType weaponType) {
        BotAttackDataProvider provider = BotAttackDataProvider.getInstance();
        Item weapon = bot != null ? bot.getInventory(InventoryType.EQUIPPED).getItem((short) -11) : null;
        if (weapon != null) {
            BotAttackDataProvider.NormalAttackProfile attackProfile =
                    provider.getNormalAttackProfile(weapon.getItemId());
            if (attackProfile != null && attackProfile.getAttack() > 0) {
                return provider.getBasicAttackSpec(attackProfile.getAttack(), weaponType);
            }
        }
        return provider.getBasicAttackSpec(weaponType);
    }

    private static String sampleWeaponAttackAction(Character bot, WeaponType weaponType) {
        BotAttackDataProvider provider = BotAttackDataProvider.getInstance();
        Item weapon = bot != null ? bot.getInventory(InventoryType.EQUIPPED).getItem((short) -11) : null;
        if (weapon != null) {
            BotAttackDataProvider.NormalAttackProfile attackProfile =
                    provider.getNormalAttackProfile(weapon.getItemId());
            if (attackProfile != null) {
                BotAttackDataProvider.AttackAnimationSpec attackSpec = provider.getBasicAttackSpec(attackProfile.getAttack(), weaponType);
                return sampleAttackAction(resolveAttackActions(attackSpec, attackProfile.getSourceActions()),
                        attackSpec.primaryAction());
            }
        }

        BotAttackDataProvider.AttackAnimationSpec attackSpec = provider.getBasicAttackSpec(weaponType);
        return sampleAttackAction(attackSpec.actions(), attackSpec.primaryAction());
    }

    private static BotCombatManager.AttackRoute determineWeaponRoute(WeaponType weaponType) {
        if (weaponType == null) {
            return BotCombatManager.AttackRoute.CLOSE;
        }

        return switch (weaponType) {
            case BOW, CROSSBOW, CLAW, GUN -> BotCombatManager.AttackRoute.RANGED;
            case WAND, STAFF -> BotCombatManager.AttackRoute.MAGIC;
            default -> BotCombatManager.AttackRoute.CLOSE;
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

    private static Rectangle closeRangeBasicHitBox(Point origin, boolean facingLeft) {
        int horizontalRange = BotCombatManager.cfg.ATTACK_RANGE_X;
        int top = origin.y - BotCombatManager.cfg.ATTACK_RANGE_Y;
        int height = BotCombatManager.cfg.ATTACK_RANGE_Y + BotCombatManager.cfg.ATTACK_DOWN_MAX;
        int left = facingLeft ? origin.x - horizontalRange : origin.x;
        return new Rectangle(left, top, horizontalRange, height);
    }

    private static Rectangle rangedBasicHitBox(BotCombatManager.AttackRoute route, Character bot, boolean facingLeft) {
        if (bot == null || bot.getPosition() == null) {
            return null;
        }

        return BotCombatManager.clientProjectileHitBox(bot, facingLeft, 1.0f);
    }

    private static boolean isBasicAttackInRange(Point botPos, Point targetPos) {
        int dx = Math.abs(targetPos.x - botPos.x);
        int dy = botPos.y - targetPos.y;
        boolean inHorizontalRange = dx <= BotCombatManager.cfg.ATTACK_RANGE_X;
        boolean inVerticalRange = dy >= -BotCombatManager.cfg.ATTACK_DOWN_MAX && dy <= BotCombatManager.cfg.ATTACK_RANGE_Y;
        return inHorizontalRange && inVerticalRange;
    }

    private static int resolveSkillAttackDelayMillis(Skill skill) {
        if (skill == null) {
            return 0;
        }
        return Math.max(0, skill.getAnimationTime());
    }

    private static int toCooldownMs(int attackDelayMillis) {
        return BotMovementManager.delayAfterCurrentTick(Math.max(0, attackDelayMillis));
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
        BotAttackDataProvider.NormalAttackProfile attackProfile = resolveWeaponAttackProfile(bot);
        if (attackProfile == null) {
            return 4;
        }

        return attackProfile.getAttackSpeed();
    }

    private static BotAttackDataProvider.NormalAttackProfile resolveWeaponAttackProfile(Character bot) {
        if (bot == null) {
            return null;
        }

        Item weapon = bot.getInventory(InventoryType.EQUIPPED).getItem((short) -11);
        if (weapon == null) {
            return null;
        }

        return BotAttackDataProvider.getInstance().getNormalAttackProfile(weapon.getItemId());
    }

    private static int normalizeAttackSpeed(int attackSpeed) {
        return BotAttackTiming.normalizeAttackSpeed(attackSpeed);
    }

    private static int resolveEffectiveAttackSpeed(int baseAttackSpeed, Character bot) {
        int normalizedBaseSpeed = normalizeAttackSpeed(baseAttackSpeed);
        if (bot == null) {
            return normalizedBaseSpeed;
        }

        Integer booster = bot.getBuffedValue(BuffStat.BOOSTER);
        if (booster == null) {
            return normalizedBaseSpeed;
        }

        return Math.max(2, normalizedBaseSpeed + booster);
    }

    private static int adjustAttackDelayMillis(int baseDelayMillis, int effectiveAttackSpeed) {
        return BotAttackTiming.adjustDelayMillis(baseDelayMillis, effectiveAttackSpeed);
    }

    private static float toAttackSpeedFactor(int attackSpeed) {
        return BotAttackTiming.toAttackSpeedFactor(attackSpeed);
    }
}
