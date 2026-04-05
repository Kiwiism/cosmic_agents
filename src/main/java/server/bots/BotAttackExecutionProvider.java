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

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

final class BotAttackExecutionProvider {
    record BasicAttackSpec(int display, List<String> actions) {
        String primaryAction() {
            return actions.isEmpty() ? "swingO1" : actions.get(0);
        }

        String actionForVariant(int variantOffset) {
            if (actions.isEmpty()) {
                return "swingO1";
            }
            int normalizedIndex = Math.max(0, Math.min(variantOffset, actions.size() - 1));
            return actions.get(normalizedIndex);
        }

        int stanceIdForVariant(int variantOffset) {
            return attackStanceId(actionForVariant(variantOffset));
        }
    }

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
        boolean useDegenerateCloseRange = shouldDegenerateRangedAttack(weaponType,
                bot != null ? bot.getPosition() : null, targetPosition);
        BasicAttackSpec attackSpec = basicAttackSpec(baseDisplay, weaponType, useDegenerateCloseRange);
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
        BotAttackDataProvider provider = BotAttackDataProvider.getInstance();

        int rawAnimationDelayMs = provider.getBodyStanceDurationMs(action);
        if (rawAnimationDelayMs <= 0) {
            rawAnimationDelayMs = profile.getAttackDelayMillis();
        }
        int rawHitDelayMs = provider.getBodyStanceDelayBeforeFrameMs(action, profile.getAfterimageFirstFrame(action));

        int cooldownMs = toCooldownMs(adjustAttackDelayMillis(rawAnimationDelayMs, profile.getAttackSpeed(), effectiveAttackSpeed));
        int hitDelayMs = adjustAttackDelayMillis(rawHitDelayMs, profile.getAttackSpeed(), effectiveAttackSpeed);
        int stance = closeRangeRoute ? closeRangePacketFields.stance() : attackStanceId(action);
        Rectangle hitBox = closeRangeRoute
                ? closeRangeBasicHitBox(bot.getPosition(), facingLeft)
                : profile.hasBoundingBox() ? profile.calculateBoundingBox(bot.getPosition(), facingLeft) : null;

        return new BasicAttackData(hitBox, display, direction, direction, stance, effectiveAttackSpeed,
                hitDelayMs, cooldownMs, route);
    }

    private static BasicAttackData fallbackBasicAttackData(boolean facingLeft, int baseAttackSpeed,
                                                           WeaponType weaponType, Character bot, Point targetPosition) {
        boolean useDegenerateCloseRange = shouldDegenerateRangedAttack(weaponType,
                bot != null ? bot.getPosition() : null, targetPosition);
        BasicAttackSpec attackSpec = basicAttackSpec(weaponType, useDegenerateCloseRange);
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
        int rawAnimationDelayMs = BotAttackDataProvider.getInstance().getBodyStanceDurationMs(action);
        if (rawAnimationDelayMs <= 0) {
            rawAnimationDelayMs = 600;
        }
        int adjustedAnimationDelayMs = adjustAttackDelayMillis(rawAnimationDelayMs, baseAttackSpeed, effectiveAttackSpeed);
        Rectangle hitBox = closeRangeRoute && bot != null
                ? closeRangeBasicHitBox(bot.getPosition(), facingLeft)
                : null;

        return new BasicAttackData(hitBox, display, direction, direction,
                closeRangeRoute ? closeRangePacketFields.stance() : attackSpec.stanceIdForVariant(variantOffset),
                effectiveAttackSpeed, defaultHitDelayMs(adjustedAnimationDelayMs), toCooldownMs(adjustedAnimationDelayMs),
                route);
    }

    static BasicAttackSpec basicAttackSpec(int attackGroup, WeaponType fallbackWeaponType) {
        return basicAttackSpec(attackGroup, fallbackWeaponType, false);
    }

    static BasicAttackSpec basicAttackSpec(int attackGroup, WeaponType fallbackWeaponType, boolean degenerate) {
        if (degenerate) {
            return switch (attackGroup) {
                case 3 -> new BasicAttackSpec(3, List.of("swingT1", "swingT3"));
                case 4 -> new BasicAttackSpec(4, List.of("swingT1", "stabT1"));
                case 7 -> new BasicAttackSpec(7, List.of("swingT1", "stabT1"));
                case 9 -> new BasicAttackSpec(9, List.of("swingP1", "stabT2"));
                default -> basicAttackSpec(fallbackWeaponType, false);
            };
        }

        return switch (attackGroup) {
            case 1 -> new BasicAttackSpec(1, List.of("stabO1", "stabO2", "swingO1", "swingO2", "swingO3"));
            case 2 -> new BasicAttackSpec(2, List.of("stabT1", "swingP1"));
            case 3 -> new BasicAttackSpec(3, List.of("shoot1"));
            case 4 -> new BasicAttackSpec(4, List.of("shoot2"));
            case 5 -> new BasicAttackSpec(5, List.of("stabO1", "stabO2", "swingT1", "swingT2", "swingT3"));
            case 6 -> new BasicAttackSpec(6, List.of("swingO1", "swingO2"));
            case 7 -> new BasicAttackSpec(7, List.of("swingO1", "swingO2"));
            case 9 -> new BasicAttackSpec(9, List.of("handgun"));
            default -> basicAttackSpec(fallbackWeaponType);
        };
    }

    static BasicAttackSpec basicAttackSpec(WeaponType weaponType) {
        return basicAttackSpec(weaponType, false);
    }

    static BasicAttackSpec basicAttackSpec(WeaponType weaponType, boolean degenerate) {
        if (weaponType == null) {
            return new BasicAttackSpec(1, List.of("stabO1", "stabO2", "swingO1", "swingO2", "swingO3"));
        }
        if (degenerate) {
            return switch (weaponType) {
                case BOW -> new BasicAttackSpec(3, List.of("swingT1", "swingT3"));
                case CROSSBOW -> new BasicAttackSpec(4, List.of("swingT1", "stabT1"));
                case CLAW -> new BasicAttackSpec(7, List.of("swingT1", "stabT1"));
                case GUN -> new BasicAttackSpec(9, List.of("swingP1", "stabT2"));
                default -> basicAttackSpec(weaponType, false);
            };
        }
        return switch (weaponType) {
            case BOW -> new BasicAttackSpec(3, List.of("shoot1"));
            case CROSSBOW -> new BasicAttackSpec(4, List.of("shoot2"));
            case SPEAR_SWING, SPEAR_STAB, POLE_ARM_SWING, POLE_ARM_STAB ->
                    new BasicAttackSpec(2, List.of("stabT1", "swingP1"));
            case GENERAL2H_SWING, GENERAL2H_STAB, SWORD2H ->
                    new BasicAttackSpec(5, List.of("stabO1", "stabO2", "swingT1", "swingT2", "swingT3"));
            case WAND, STAFF -> new BasicAttackSpec(6, List.of("swingO1", "swingO2"));
            case CLAW -> new BasicAttackSpec(7, List.of("swingO1", "swingO2"));
            case GUN -> new BasicAttackSpec(9, List.of("handgun"));
            default -> new BasicAttackSpec(1, List.of("stabO1", "stabO2", "swingO1", "swingO2", "swingO3"));
        };
    }

    static int attackStanceId(String actionName) {
        return switch (actionName) {
            case "shot", "handgun" -> 10;
            case "shoot1" -> 11;
            case "shoot2" -> 12;
            case "stabO1" -> 15;
            case "stabO2" -> 16;
            case "stabT1" -> 18;
            case "stabT2" -> 19;
            case "swingO1" -> 23;
            case "swingO2" -> 24;
            case "swingO3" -> 25;
            case "swingP1" -> 27;
            case "swingP2" -> 28;
            case "swingT1" -> 30;
            case "swingT2" -> 31;
            case "swingT3" -> 32;
            default -> 0;
        };
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

    static List<String> resolveAttackActions(BasicAttackSpec attackSpec, List<String> sourceActions) {
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
        return shouldDegenerateRangedAttack(weaponType, botPos, targetPos)
                && !isBasicAttackInRange(botPos, targetPos);
    }

    static Point retreatTargetPosition(Point botPos, Point targetPos) {
        int retreatDirection = targetPos.x >= botPos.x ? -1 : 1;
        return new Point(botPos.x + retreatDirection * BotCombatManager.cfg.RANGED_RETREAT_DISTANCE_X, botPos.y);
    }

    static SkillAttackTiming resolveSkillAttackTiming(Skill skill, String action, Character bot,
                                                      BasicAttackData fallbackAttackData) {
        int fallbackHitDelayMs = fallbackAttackData != null ? fallbackAttackData.hitDelayMs() : defaultHitDelayMs(600);
        int fallbackCooldownMs = fallbackAttackData != null ? fallbackAttackData.cooldownMs() : toCooldownMs(600);
        return resolveSkillAttackTiming(action, resolveSkillAttackDelayMillis(skill),
                resolveBaseWeaponAttackSpeed(bot), resolveWeaponAttackSpeed(bot),
                fallbackHitDelayMs, fallbackCooldownMs);
    }

    static SkillAttackTiming resolveSkillAttackTiming(String action, int rawSkillDelayMs,
                                                      int baseWeaponAttackSpeed, int effectiveWeaponAttackSpeed,
                                                      int fallbackHitDelayMs, int fallbackCooldownMs) {
        BotAttackDataProvider provider = BotAttackDataProvider.getInstance();
        int rawActionCooldownMs = provider.getBodyActionDurationMs(action);
        if (rawActionCooldownMs > 0) {
            int rawActionHitDelayMs = provider.getBodyActionAttackDelayMs(action, 0);
            int adjustedActionCooldownMs = adjustAttackDelayMillis(rawActionCooldownMs,
                    baseWeaponAttackSpeed, effectiveWeaponAttackSpeed);
            int adjustedActionHitDelayMs = rawActionHitDelayMs >= 0
                    ? adjustAttackDelayMillis(rawActionHitDelayMs, baseWeaponAttackSpeed, effectiveWeaponAttackSpeed)
                    : defaultHitDelayMs(adjustedActionCooldownMs);
            return new SkillAttackTiming(adjustedActionHitDelayMs,
                    Math.max(toCooldownMs(adjustedActionCooldownMs), fallbackCooldownMs));
        }

        return resolveSkillAttackTiming(rawSkillDelayMs, baseWeaponAttackSpeed, effectiveWeaponAttackSpeed,
                fallbackHitDelayMs, fallbackCooldownMs);
    }

    static SkillAttackTiming resolveSkillAttackTiming(int rawSkillDelayMs,
                                                      int baseWeaponAttackSpeed, int effectiveWeaponAttackSpeed,
                                                      int fallbackHitDelayMs, int fallbackCooldownMs) {
        if (rawSkillDelayMs <= 0) {
            return new SkillAttackTiming(fallbackHitDelayMs, fallbackCooldownMs);
        }

        int adjustedSkillDelayMs =
                adjustAttackDelayMillis(rawSkillDelayMs, baseWeaponAttackSpeed, effectiveWeaponAttackSpeed);
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

    private static BasicAttackSpec resolveWeaponAttackSpec(Character bot, WeaponType weaponType) {
        Item weapon = bot != null ? bot.getInventory(InventoryType.EQUIPPED).getItem((short) -11) : null;
        if (weapon != null) {
            BotAttackDataProvider.NormalAttackProfile attackProfile =
                    BotAttackDataProvider.getInstance().getNormalAttackProfile(weapon.getItemId());
            if (attackProfile != null && attackProfile.getAttack() > 0) {
                return basicAttackSpec(attackProfile.getAttack(), weaponType);
            }
        }
        return basicAttackSpec(weaponType);
    }

    private static String sampleWeaponAttackAction(Character bot, WeaponType weaponType) {
        Item weapon = bot != null ? bot.getInventory(InventoryType.EQUIPPED).getItem((short) -11) : null;
        if (weapon != null) {
            BotAttackDataProvider.NormalAttackProfile attackProfile =
                    BotAttackDataProvider.getInstance().getNormalAttackProfile(weapon.getItemId());
            if (attackProfile != null) {
                BasicAttackSpec attackSpec = basicAttackSpec(attackProfile.getAttack(), weaponType);
                return sampleAttackAction(resolveAttackActions(attackSpec, attackProfile.getSourceActions()),
                        attackSpec.primaryAction());
            }
        }

        BasicAttackSpec attackSpec = basicAttackSpec(weaponType);
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
        Item weapon = bot.getInventory(InventoryType.EQUIPPED).getItem((short) -11);
        if (weapon == null) {
            return 4;
        }

        BotAttackDataProvider.NormalAttackProfile attackProfile =
                BotAttackDataProvider.getInstance().getNormalAttackProfile(weapon.getItemId());
        if (attackProfile == null) {
            return 4;
        }

        return attackProfile.getAttackSpeed();
    }

    private static int normalizeAttackSpeed(int attackSpeed) {
        if (attackSpeed <= 0) {
            return 4;
        }
        return attackSpeed;
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

    private static int adjustAttackDelayMillis(int baseDelayMillis, int baseAttackSpeed, int effectiveAttackSpeed) {
        if (baseDelayMillis <= 0) {
            return 0;
        }

        float effectiveSpeedFactor = toAttackSpeedFactor(effectiveAttackSpeed);
        if (effectiveSpeedFactor <= 0f) {
            return baseDelayMillis;
        }

        return Math.max(1, Math.round(baseDelayMillis / effectiveSpeedFactor));
    }

    private static float toAttackSpeedFactor(int attackSpeed) {
        return 1.7f - (attackSpeed / 10f);
    }
}
