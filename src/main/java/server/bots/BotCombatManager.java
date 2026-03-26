package server.bots;

import client.BuffStat;
import client.Character;
import client.Skill;
import client.SkillFactory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.WeaponType;
import net.server.channel.handlers.AbstractDealDamageHandler;
import net.server.channel.handlers.CloseRangeDamageHandler;
import net.server.channel.handlers.MagicDamageHandler;
import net.server.channel.handlers.RangedAttackHandler;
import server.StatEffect;
import server.life.Monster;
import tools.PacketCreator;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

class BotCombatManager {

    private enum AttackRoute {
        CLOSE,
        RANGED,
        MAGIC
    }

    static final class AttackPlan {
        final int skillId;
        final int skillLevel;
        final int numDamage;
        final Rectangle hitBox;
        final List<Monster> targets;
        final AttackRoute route;
        final int display;
        final int direction;
        final int rangedDirection;
        final int speed;
        final int cooldownMs;

        AttackPlan(int skillId, int skillLevel, int numDamage, Rectangle hitBox, List<Monster> targets,
                   AttackRoute route, int display, int direction, int rangedDirection, int speed, int cooldownMs) {
            this.skillId = skillId;
            this.skillLevel = skillLevel;
            this.numDamage = numDamage;
            this.hitBox = hitBox;
            this.targets = targets;
            this.route = route;
            this.display = display;
            this.direction = direction;
            this.rangedDirection = rangedDirection;
            this.speed = speed;
            this.cooldownMs = cooldownMs;
        }

        boolean hasHitBox() {
            return hitBox != null;
        }

        Monster primaryTarget() {
            return targets.get(0);
        }
    }

    static class Config {
        // Physics (combat use only)
        public float KNOCKBACK_RISE = 18f;

        // Basic attack fallback when weapon data cannot produce a real normal-attack hit box.
        public int   ATTACK_RANGE_X  = 80;
        public int   ATTACK_RANGE_Y  = 50;
        public int   ATTACK_DOWN_MAX = 20;
        public int   ATTACK_JUMP_Y   = 130;

        // Grind / AoE
        public int   GRIND_SEEK_RANGE  = 800;
        public int   AOE_MOB_THRESHOLD = 2;

        // Mob damage
        public int   MOB_TOUCH_HALF_W = 40;
        public int   MOB_TOUCH_HALF_H = 30;
        public int   MOB_HIT_COOLDOWN_MS = 1500;
        public long  BOT_DEAD_MS      = 10_000L;
    }

    static Config cfg = new Config();

    private static final List<String> DEATH_REPLIES = List.of(
            "oops im dead", "gg", "rip me", "oww", "i died lol",
            "welp", "ouchh", "nooo", "ok i died", "i'll be right back");

    /** Check every alive monster on the map; if bot is inside its bounding box, apply a hit. */
    static void tickMobDamage(BotEntry entry, Character bot) {
        if (entry.mobHitCooldownMs > 0) {
            entry.mobHitCooldownMs = BotMovementManager.tickDown(entry.mobHitCooldownMs);
            return;
        }
        if (bot.getHp() <= 0) return;

        Point botPos = bot.getPosition();
        Config cc = BotCombatManager.cfg;
        for (Monster mob : bot.getMap().getAllMonsters()) {
            if (!mob.isAlive()) continue;
            Point mp = mob.getPosition();
            if (Math.abs(botPos.x - mp.x) <= cc.MOB_TOUCH_HALF_W
                    && Math.abs(botPos.y - mp.y) <= cc.MOB_TOUCH_HALF_H) {
                applyMobHit(entry, bot, mob);
                return;
            }
        }
    }

    /**
     * Apply one physical hit from {@code mob} to the bot.
     * Damage = PADamage +/- 15% (v83 mob attack approximation; no server-side WDEF available).
     */
    static void applyMobHit(BotEntry entry, Character bot, Monster mob) {
        Config cc = BotCombatManager.cfg;
        BotMovementManager.Config mc = BotMovementManager.cfg;
        int pa  = mob.getPADamage();
        int max = Math.max(1, pa * 115 / 100);
        int min = Math.max(1, pa * 85 / 100);
        int dmg = min < max ? ThreadLocalRandom.current().nextInt(min, max + 1) : max;

        bot.addMPHP(-dmg, 0);

        // direction: 0 = hit from left (knocked right), 1 = hit from right (knocked left)
        int dir = mob.getPosition().x < bot.getPosition().x ? 0 : 1;
        bot.getMap().broadcastMessage(bot,
                PacketCreator.damagePlayer(-1, mob.getId(), bot.getId(), dmg, 0,
                        dir, false, 0, false, 0, 0, 0), false);

        // Knock the bot back and hand motion over to the normal airborne physics.
        Point bp = bot.getPosition();
        int kbX = bp.x + (dir == 0 ? 30 : -30);
        bot.setPosition(new Point(kbX, bp.y));
        BotMovementManager.resetEntryState(entry);
        entry.velY = -cc.KNOCKBACK_RISE;
        entry.airVelX = (dir == 0 ? 1 : -1) * mc.STEP;
        int velXBcast = entry.airVelX * (1000 / mc.TICK_MS);
        int velYBcast = (int) (-entry.velY * (1000f / mc.TICK_MS));
        BotMovementManager.broadcastMovement(bot, velXBcast, velYBcast);

        entry.mobHitCooldownMs = BotMovementManager.delayAfterCurrentTick(cc.MOB_HIT_COOLDOWN_MS);

        if (bot.getHp() <= 0) {
            bot.setStance(mc.DEAD_STANCE);
            BotMovementManager.broadcastMovement(bot, 0, 0);
            BotManager.getInstance().botSay(bot, BotManager.randomReply(DEATH_REPLIES));
            entry.deadUntil = System.currentTimeMillis() + cc.BOT_DEAD_MS;
            BotMovementManager.resetEntryState(entry);
        }
    }

    static void rebuildSkillCacheIfNeeded(BotEntry entry, Character bot) {
        if (entry.cachedSkillJob == bot.getJob().getId()
                && entry.cachedSkillLevel == bot.getLevel()) {
            return;
        }

        entry.cachedSkillJob = bot.getJob().getId();
        entry.cachedSkillLevel = bot.getLevel();
        entry.attackSkillId = 0;
        entry.aoeSkillId = 0;
        entry.aoeSkillMobs = 1;
        entry.buffSkillIds.clear();

        int bestAtkHits = 0;
        int bestAoeScore = 0;

        for (Skill skill : bot.getSkills().keySet()) {
            int lvl = bot.getSkillLevel(skill);
            if (lvl <= 0) continue;

            StatEffect fx = skill.getEffect(lvl);
            int atk = fx.getAttackCount();
            int mobs = fx.getMobCount();
            int dur = fx.getDuration();

            if (atk > 0) {
                if (mobs >= 2) {
                    int score = mobs * atk;
                    if (score > bestAoeScore) {
                        bestAoeScore = score;
                        entry.aoeSkillId = skill.getId();
                        entry.aoeSkillMobs = mobs;
                    }
                } else if (atk > bestAtkHits) {
                    bestAtkHits = atk;
                    entry.attackSkillId = skill.getId();
                }
                continue;
            }

            if (dur <= 0) {
                continue;
            }

            entry.buffSkillIds.add(skill.getId());
            entry.nextBuffAt.putIfAbsent(skill.getId(), 0L);
        }
    }

    static void tickBuffs(BotEntry entry, Character bot) {
        if (!entry.following && !entry.grinding) return;
        if (entry.buffSkillIds.isEmpty()) return;
        if (bot.getMap().getAllMonsters().stream().noneMatch(Monster::isAlive)) return;

        long now = System.currentTimeMillis();
        for (int skillId : entry.buffSkillIds) {
            if (now < entry.nextBuffAt.getOrDefault(skillId, 0L)) continue;
            if (bot.skillIsCooling(skillId)) continue;

            Skill skill = SkillFactory.getSkill(skillId);
            int lvl = bot.getSkillLevel(skill);
            if (lvl <= 0) continue;

            StatEffect fx = skill.getEffect(lvl);
            fx.applyTo(bot, null);

            long dur = fx.getDuration();
            entry.nextBuffAt.put(skillId, now + (long) (dur * 0.9));
            if (fx.getCooldown() > 0) {
                bot.addCooldown(skillId, now, fx.getCooldown() * 1000L);
            }
        }
    }

    /** Returns a random monster from the nearest 3 within seek range, so multiple bots spread across targets. */
    static Monster findGrindTarget(Character bot) {
        Point botPos = bot.getPosition();
        double rangeSq = (double) BotCombatManager.cfg.GRIND_SEEK_RANGE * BotCombatManager.cfg.GRIND_SEEK_RANGE;
        List<Monster> candidates = new ArrayList<>();
        for (Monster m : bot.getMap().getAllMonsters()) {
            if (m.isAlive() && m.getPosition().distanceSq(botPos) <= rangeSq) {
                candidates.add(m);
            }
        }
        if (candidates.isEmpty()) return null;

        candidates.sort((a, b) -> Double.compare(a.getPosition().distanceSq(botPos), b.getPosition().distanceSq(botPos)));
        return candidates.get(ThreadLocalRandom.current().nextInt(Math.min(3, candidates.size())));
    }

    static AttackPlan planAttack(BotEntry entry, Character bot, Monster target) {
        AttackPlan aoeAttack = planAoeAttack(entry, bot, target);
        if (aoeAttack != null) {
            return aoeAttack;
        }

        AttackPlan skillAttack = planSingleTargetSkill(entry, bot, target);
        if (skillAttack != null) {
            return skillAttack;
        }

        BasicAttackData basicAttackData = buildBasicAttackData(bot, target);
        return new AttackPlan(0, 0, 1, basicAttackData.hitBox, List.of(target), determineBasicAttackRoute(bot),
                basicAttackData.display, basicAttackData.direction, basicAttackData.rangedDirection,
                basicAttackData.speed, basicAttackData.cooldownMs);
    }

    static boolean isTargetInAttackRange(AttackPlan attackPlan, Character bot, Monster target) {
        if (attackPlan.hasHitBox()) {
            return attackPlan.hitBox.contains(target.getPosition());
        }
        return isBasicAttackInRange(bot.getPosition(), target.getPosition());
    }

    static boolean isTargetJumpable(Point botPos, Point targetPos) {
        int dx = Math.abs(targetPos.x - botPos.x);
        if (dx > BotCombatManager.cfg.ATTACK_RANGE_X) {
            return false;
        }

        int dy = botPos.y - targetPos.y;
        return dy > BotCombatManager.cfg.ATTACK_RANGE_Y && dy <= BotCombatManager.cfg.ATTACK_JUMP_Y;
    }

    static void attackMonster(BotEntry entry, Character bot, AttackPlan attackPlan) {
        if (entry.attackCooldownMs > 0) {
            entry.attackCooldownMs = BotMovementManager.tickDown(entry.attackCooldownMs);
            return;
        }

        int watk = bot.getTotalWatk();
        int maxDmg = Math.max(1, bot.calculateMaxBaseDamage(watk));
        int minDmg = Math.max(1, bot.calculateMinBaseDamage(watk));

        Monster primaryTarget = attackPlan.primaryTarget();
        int numAttacked = attackPlan.targets.size();
        AbstractDealDamageHandler.AttackInfo attack = new AbstractDealDamageHandler.AttackInfo();
        attack.skill = attackPlan.skillId;
        attack.skilllevel = attackPlan.skillLevel;
        attack.numDamage = attackPlan.numDamage;
        attack.numAttacked = numAttacked;
        attack.numAttackedAndDamage = (numAttacked << 4) | attackPlan.numDamage;
        attack.speed = attackPlan.speed;
        attack.stance = primaryTarget.getPosition().x < bot.getPosition().x ? -128 : 0;
        attack.display = attackPlan.display;
        attack.direction = attackPlan.direction;
        attack.rangedirection = attackPlan.rangedDirection;
        attack.ranged = attackPlan.route == AttackRoute.RANGED;
        attack.magic = attackPlan.route == AttackRoute.MAGIC;
        attack.targets = new HashMap<>();

        for (Monster target : attackPlan.targets) {
            attack.targets.put(target.getObjectId(), makeTarget(attackPlan.numDamage, minDmg, maxDmg));
        }

        applyAttackRoute(attackPlan.route, attack, bot);
        entry.attackCooldownMs = attackPlan.cooldownMs;
    }

    private static AttackPlan planAoeAttack(BotEntry entry, Character bot, Monster primaryTarget) {
        if (entry.aoeSkillId == 0 || bot.skillIsCooling(entry.aoeSkillId)) {
            return null;
        }

        Skill skill = SkillFactory.getSkill(entry.aoeSkillId);
        int skillLevel = bot.getSkillLevel(skill);
        if (skillLevel <= 0) {
            return null;
        }

        StatEffect effect = skill.getEffect(skillLevel);
        Rectangle hitBox = calculateSkillHitBox(effect, bot, primaryTarget);
        if (hitBox == null) {
            return null;
        }

        List<Monster> targets = collectTargetsInHitBox(bot, primaryTarget, hitBox, Math.max(1, effect.getMobCount()));
        if (targets.size() < BotCombatManager.cfg.AOE_MOB_THRESHOLD) {
            return null;
        }

        int attackCount = Math.max(1, effect.getAttackCount());
        int direction = primaryTarget.getPosition().x < bot.getPosition().x ? 17 : 6;
        return new AttackPlan(entry.aoeSkillId, skillLevel, attackCount, hitBox, targets,
                determineSkillRoute(bot, entry.aoeSkillId), 0, direction, direction,
                resolveWeaponAttackSpeed(bot), toCooldownMs(resolveSkillAttackDelayMillis(skill)));
    }

    private static AttackPlan planSingleTargetSkill(BotEntry entry, Character bot, Monster primaryTarget) {
        if (entry.attackSkillId == 0 || bot.skillIsCooling(entry.attackSkillId)) {
            return null;
        }

        Skill skill = SkillFactory.getSkill(entry.attackSkillId);
        int skillLevel = bot.getSkillLevel(skill);
        if (skillLevel <= 0) {
            return null;
        }

        StatEffect effect = skill.getEffect(skillLevel);
        Rectangle hitBox = calculateSkillHitBox(effect, bot, primaryTarget);
        if (hitBox == null || !hitBox.contains(primaryTarget.getPosition())) {
            return null;
        }

        int attackCount = Math.max(1, effect.getAttackCount());
        int direction = primaryTarget.getPosition().x < bot.getPosition().x ? 17 : 6;
        return new AttackPlan(entry.attackSkillId, skillLevel, attackCount, hitBox, List.of(primaryTarget),
                determineSkillRoute(bot, entry.attackSkillId), 0, direction, direction,
                resolveWeaponAttackSpeed(bot), toCooldownMs(resolveSkillAttackDelayMillis(skill)));
    }

    private static Rectangle calculateSkillHitBox(StatEffect effect, Character bot, Monster primaryTarget) {
        if (!effect.hasBoundingBox()) {
            return null;
        }

        boolean facingLeft = primaryTarget.getPosition().x < bot.getPosition().x;
        return effect.calculateBoundingBox(bot.getPosition(), facingLeft);
    }

    private static List<Monster> collectTargetsInHitBox(Character bot, Monster primaryTarget, Rectangle hitBox, int maxTargets) {
        if (!hitBox.contains(primaryTarget.getPosition())) {
            return List.of();
        }

        List<Monster> targets = new ArrayList<>();
        targets.add(primaryTarget);
        if (maxTargets <= 1) {
            return targets;
        }

        List<Monster> secondaryTargets = new ArrayList<>();
        for (Monster monster : bot.getMap().getAllMonsters()) {
            if (!monster.isAlive() || monster.getObjectId() == primaryTarget.getObjectId()) {
                continue;
            }
            if (!hitBox.contains(monster.getPosition())) {
                continue;
            }
            secondaryTargets.add(monster);
        }

        secondaryTargets.sort(Comparator.comparingDouble(monster -> monster.getPosition().distanceSq(primaryTarget.getPosition())));
        for (Monster monster : secondaryTargets) {
            targets.add(monster);
            if (targets.size() >= maxTargets) {
                break;
            }
        }
        return targets;
    }

    private static boolean isBasicAttackInRange(Point botPos, Point targetPos) {
        int dx = Math.abs(targetPos.x - botPos.x);
        int dy = botPos.y - targetPos.y;
        boolean inHRange = dx <= BotCombatManager.cfg.ATTACK_RANGE_X;
        boolean inVRange = dy >= -BotCombatManager.cfg.ATTACK_DOWN_MAX && dy <= BotCombatManager.cfg.ATTACK_RANGE_Y;
        return inHRange && inVRange;
    }

    private static BasicAttackData buildBasicAttackData(Character bot, Monster primaryTarget) {
        Item weapon = bot.getInventory(InventoryType.EQUIPPED).getItem((short) -11);
        if (weapon == null) {
            return BasicAttackData.fallback(primaryTarget.getPosition().x < bot.getPosition().x);
        }

        BotAttackDataProvider.NormalAttackProfile attackProfile =
                BotAttackDataProvider.getInstance().getNormalAttackProfile(weapon.getItemId());
        if (attackProfile == null) {
            return BasicAttackData.fallback(primaryTarget.getPosition().x < bot.getPosition().x);
        }

        WeaponType weaponType = server.ItemInformationProvider.getInstance().getWeaponType(weapon.getItemId());
        boolean facingLeft = primaryTarget.getPosition().x < bot.getPosition().x;
        Rectangle hitBox = attackProfile.hasBoundingBox()
                ? attackProfile.calculateBoundingBox(bot.getPosition(), facingLeft)
                : null;
        return BasicAttackData.fromProfile(attackProfile, weaponType, hitBox, facingLeft, bot);
    }

    /**
     * Maps a weapon type to the primary body animation stance name used for that weapon,
     * matching OpenStory's {@code CharLook::getattackstance} attack-type groupings.
     * The returned string is a key in {@code Character/00002000.img} (BodyDrawInfo).
     */
    private static String primaryAttackStance(WeaponType weaponType) {
        if (weaponType == null) {
            return "swingO1";
        }
        return switch (weaponType) {
            case BOW                            -> "shoot1";
            case CROSSBOW                       -> "shoot2";
            case SPEAR_SWING, POLE_ARM_SWING    -> "swingP1";
            case SPEAR_STAB,  POLE_ARM_STAB     -> "stabT1";
            case GENERAL2H_SWING, SWORD2H       -> "swingT1";
            case GENERAL2H_STAB                 -> "stabT1";
            case GUN                            -> "shot";
            // 1H swords, axes, BW, daggers, wands, staves, claws, knuckles (attack types 1, 6, 7)
            default                             -> "swingO1";
        };
    }

    private static void applyAttackRoute(AttackRoute route, AbstractDealDamageHandler.AttackInfo attack, Character bot) {
        switch (route) {
            case RANGED -> RangedAttackHandler.applyRangedAttackEffects(attack, bot, bot.getClient());
            case MAGIC -> MagicDamageHandler.applyMagicAttackEffects(attack, bot, bot.getClient());
            default -> CloseRangeDamageHandler.applyCloseRangeEffects(attack, bot, bot.getClient());
        }
    }

    private static AttackRoute determineBasicAttackRoute(Character bot) {
        return determineWeaponRoute(getEquippedWeaponType(bot));
    }

    private static AttackRoute determineSkillRoute(Character bot, int skillId) {
        if (isRangedSkill(skillId)) {
            return AttackRoute.RANGED;
        }

        WeaponType weaponType = getEquippedWeaponType(bot);
        if (weaponType == WeaponType.WAND || weaponType == WeaponType.STAFF) {
            return AttackRoute.MAGIC;
        }

        return determineWeaponRoute(weaponType);
    }

    private static AttackRoute determineWeaponRoute(WeaponType weaponType) {
        if (weaponType == null) {
            return AttackRoute.CLOSE;
        }

        return switch (weaponType) {
            case BOW, CROSSBOW, CLAW, GUN -> AttackRoute.RANGED;
            case WAND, STAFF -> AttackRoute.MAGIC;
            default -> AttackRoute.CLOSE;
        };
    }

    private static WeaponType getEquippedWeaponType(Character bot) {
        Item weapon = bot.getInventory(InventoryType.EQUIPPED).getItem((short) -11);
        if (weapon == null) {
            return null;
        }

        return server.ItemInformationProvider.getInstance().getWeaponType(weapon.getItemId());
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

    private record BasicAttackData(Rectangle hitBox, int display, int direction, int rangedDirection, int speed, int cooldownMs) {
        private static BasicAttackData fromProfile(BotAttackDataProvider.NormalAttackProfile profile, WeaponType weaponType, Rectangle hitBox, boolean facingLeft, Character bot) {
            int baseDirection = profile.getAttack();
            if (baseDirection <= 0) {
                return fallback(facingLeft, hitBox);
            }

            int variantCount = Math.max(1, countMoveVariants(profile.getSourceActions()));
            int variantOffset = ThreadLocalRandom.current().nextInt(variantCount);
            int display = baseDirection + variantOffset;
            int direction = facingLeft ? display + 11 : display;
            int effectiveAttackSpeed = resolveEffectiveAttackSpeed(profile.getAttackSpeed(), bot);

            // Use body animation timing (Character/00002000.img stance frame delays), matching
            // OpenStory's BodyDrawInfo → Char::get_attackdelay pipeline. Fall back to the
            // weapon/afterimage WZ delay only when the body anim data is unavailable.
            String stance = primaryAttackStance(weaponType);
            int rawDelayMs = BotAttackDataProvider.getInstance().getBodyStanceDurationMs(stance);
            if (rawDelayMs <= 0) {
                rawDelayMs = profile.getAttackDelayMillis();
            }

            return new BasicAttackData(hitBox, display, direction, direction,
                    effectiveAttackSpeed,
                    toCooldownMs(adjustAttackDelayMillis(rawDelayMs, profile.getAttackSpeed(), effectiveAttackSpeed)));
        }

        private static BasicAttackData fallback(boolean facingLeft) {
            return fallback(facingLeft, null);
        }

        private static BasicAttackData fallback(boolean facingLeft, Rectangle hitBox) {
            int direction = facingLeft ? 17 : 6;
            return new BasicAttackData(hitBox, 0, direction, direction, 4, 0);
        }

        private static int countMoveVariants(List<String> sourceActions) {
            if (sourceActions == null || sourceActions.isEmpty()) {
                return 1;
            }

            int variantCount = 0;
            for (String sourceAction : sourceActions) {
                if (sourceAction == null || sourceAction.isBlank()) {
                    continue;
                }
                variantCount++;
            }
            return Math.max(1, variantCount);
        }
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

    private static int resolveWeaponAttackSpeed(Character bot) {
        Item weapon = bot.getInventory(InventoryType.EQUIPPED).getItem((short) -11);
        if (weapon == null) {
            return 4;
        }

        BotAttackDataProvider.NormalAttackProfile attackProfile =
                BotAttackDataProvider.getInstance().getNormalAttackProfile(weapon.getItemId());
        if (attackProfile == null) {
            return 4;
        }

        return resolveEffectiveAttackSpeed(attackProfile.getAttackSpeed(), bot);
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

        // Mirror OpenStory Char::get_attackdelay: effectiveDelay = rawDelay / (1.7 - speed/10).
        // baseDelayMillis is the raw WZ animation duration; divide by the effective speed factor
        // (which already incorporates any booster offset) to get the actual cooldown.
        float effectiveSpeedFactor = toAttackSpeedFactor(effectiveAttackSpeed);
        if (effectiveSpeedFactor <= 0f) {
            return baseDelayMillis;
        }

        return Math.max(1, Math.round(baseDelayMillis / effectiveSpeedFactor));
    }

    private static float toAttackSpeedFactor(int attackSpeed) {
        return 1.7f - (attackSpeed / 10f);
    }

    private static AbstractDealDamageHandler.AttackTarget makeTarget(int hits, int minDmg, int maxDmg) {
        List<Integer> lines = new ArrayList<>(hits);
        for (int i = 0; i < hits; i++) {
            lines.add(minDmg < maxDmg
                    ? ThreadLocalRandom.current().nextInt(minDmg, maxDmg + 1)
                    : maxDmg);
        }
        return new AbstractDealDamageHandler.AttackTarget((short) 305, lines);
    }

    static String describeDebugStats(BotEntry entry, Character bot) {
        Monster target = entry.grindTarget;
        if (target == null || !target.isAlive()) {
            target = findGrindTarget(bot);
        }

        AttackPlan plan = target != null ? planAttack(entry, bot, target) : null;
        String route = plan != null ? plan.route.name().toLowerCase() : determineBasicAttackRoute(bot).name().toLowerCase();
        int speed = plan != null ? plan.speed : resolveWeaponAttackSpeed(bot);
        double cooldownSeconds = (plan != null ? plan.cooldownMs : 0) / 1000.0;
        double remainingSeconds = entry.attackCooldownMs / 1000.0;
        String targetName = target != null ? target.getName() : "none";

        return String.format(
                "debug: route %s, atk speed %d, atk cd %.2fs, remaining %.2fs, tick %dms, ai %dms, target %s",
                route, speed, cooldownSeconds, remainingSeconds,
                BotMovementManager.cfg.TICK_MS, BotManager.cfg.AI_TICK_MS, targetName);
    }
}
