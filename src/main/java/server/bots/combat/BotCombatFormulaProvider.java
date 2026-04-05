package server.bots.combat;

import client.BuffStat;
import client.Character;
import client.Skill;
import client.SkillFactory;
import client.inventory.Equip;
import client.inventory.InventoryType;
import client.inventory.Item;
import constants.skills.BlazeWizard;
import constants.skills.Cleric;
import constants.skills.DragonKnight;
import constants.skills.Evan;
import constants.skills.FPMage;
import constants.skills.Hermit;
import constants.skills.ILMage;
import constants.skills.NightLord;
import constants.skills.NightWalker;
import constants.skills.Rogue;
import constants.skills.Shadower;
import net.server.channel.handlers.AbstractDealDamageHandler;
import server.StatEffect;
import server.life.Monster;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class BotCombatFormulaProvider {
    public record DamageProfile(int minDamage, int maxDamage, boolean magicAttack, boolean alwaysHit) {
    }

    private static final double MIN_HIT_CHANCE = 0.01d;
    private static final double MAX_HIT_CHANCE = 1.0d;
    private static final BotCombatFormulaProvider instance = new BotCombatFormulaProvider();

    public static BotCombatFormulaProvider getInstance() {
        return instance;
    }

    public int getTotalAccuracy(Character bot) {
        int derivedAccuracy = (int) Math.floor(bot.getTotalDex() * 0.8d + bot.getTotalLuk() * 0.5d);
        return Math.max(0, derivedAccuracy + getFlatAccuracy(bot));
    }

    public int getTotalMagicAccuracy(Character bot) {
        // Magic accuracy = 5 × (floor(INT/10) + floor(LUK/10))  — per cat123/Eric client research
        int derivedMagicAccuracy = 5 * ((int) Math.floor(bot.getTotalInt() / 10.0)
                + (int) Math.floor(bot.getTotalLuk() / 10.0));
        return Math.max(0, derivedMagicAccuracy);
    }

    public int getTotalAvoidability(Character bot) {
        int derivedAvoidability = (int) Math.floor(bot.getTotalDex() * 0.25d + bot.getTotalLuk() * 0.5d);
        return Math.max(0, derivedAvoidability + getFlatAvoidability(bot));
    }

    public double calculateMobHitChance(Character bot, Monster monster) {
        return calculateMobHitChance(bot, monster, false);
    }

    public double calculateMobHitChance(Character bot, Monster monster, boolean magicAttack) {
        if (magicAttack) {
            return calculateMagicMobHitChance(getTotalMagicAccuracy(bot), bot.getLevel(), monster.getLevel(), monster.getAvoidability());
        }
        return calculatePhysicalMobHitChance(getTotalAccuracy(bot), bot.getLevel(), monster.getLevel(), monster.getAvoidability());
    }

    double calculateMobHitChance(int accuracy, int botLevel, int monsterLevel, int monsterAvoidability) {
        return calculatePhysicalMobHitChance(accuracy, botLevel, monsterLevel, monsterAvoidability);
    }

    public double calculatePhysicalMobHitChance(int accuracy, int botLevel, int monsterLevel, int monsterAvoidability) {
        // Source: cat123/Eric client research (RaGEZONE, Mar 2026)
        // accuracy_rate = accuracy * 100 / (levelDelta * 10 + 255)
        // hit if random(0.7, 1.3) * accuracy_rate >= avoid
        // => hitChance = (1.3 - avoid/accuracy_rate) / 0.6
        int levelDelta = Math.max(0, monsterLevel - botLevel);
        if (monsterAvoidability <= 0) return MAX_HIT_CHANCE;
        double accuracyRate = accuracy * 100.0 / (levelDelta * 10 + 255);
        double hitChance = (1.3 - monsterAvoidability / accuracyRate) / 0.6;
        return Math.max(MIN_HIT_CHANCE, Math.min(MAX_HIT_CHANCE, hitChance));
    }

    public double calculateMagicMobHitChance(int magicAccuracy, int botLevel, int monsterLevel, int monsterAvoidability) {
        // Same accuracy_rate scaling as physical; random bounds are (0.5, 1.2) for magic
        // => hitChance = (1.2 - avoid/accuracy_rate) / 0.7
        int levelDelta = Math.max(0, monsterLevel - botLevel);
        if (monsterAvoidability <= 0) return MAX_HIT_CHANCE;
        double accuracyRate = magicAccuracy * 100.0 / (levelDelta * 10 + 255);
        double hitChance = (1.2 - monsterAvoidability / accuracyRate) / 0.7;
        return Math.max(MIN_HIT_CHANCE, Math.min(MAX_HIT_CHANCE, hitChance));
    }

    public double calculateBotAvoidChance(Character bot, Monster monster) {
        return calculateBotAvoidChance(monster.getAccuracy(), monster.getLevel(), bot.getLevel(), getTotalAvoidability(bot));
    }

    public double calculateBotAvoidChance(int monsterAccuracy, int monsterLevel, int botLevel, int botAvoidability) {
        int levelDelta = Math.max(0, botLevel - monsterLevel);
        double hitChance = monsterAccuracy / (((1.84d + 0.07d * levelDelta) * botAvoidability) + 1.0d);
        hitChance = Math.max(MIN_HIT_CHANCE, hitChance);
        return Math.min(MAX_HIT_CHANCE, hitChance);
    }

    public boolean doesMobHit(Character bot, Monster monster) {
        return doesMobHit(calculateBotAvoidChance(bot, monster));
    }

    public boolean doesMobHit(double hitChance) {
        double normalizedHitChance = Math.max(0.0d, Math.min(MAX_HIT_CHANCE, hitChance));
        return ThreadLocalRandom.current().nextDouble() <= normalizedHitChance;
    }

    public List<Integer> rollDamageLines(Character bot, Monster monster, int hits, int minDamage, int maxDamage) {
        return rollDamageLines(bot, monster, hits, minDamage, maxDamage, false);
    }

    public List<Integer> rollDamageLines(Character bot, Monster monster, int hits, int minDamage, int maxDamage, boolean magicAttack) {
        return rollDamageLines(hits, minDamage, maxDamage, calculateMobHitChance(bot, monster, magicAttack));
    }

    public List<Integer> rollDamageLines(int hits, int minDamage, int maxDamage, double hitChance) {
        int normalizedMinDamage = Math.max(0, minDamage);
        int normalizedMaxDamage = Math.max(normalizedMinDamage, maxDamage);
        double normalizedHitChance = Math.max(0.0d, Math.min(MAX_HIT_CHANCE, hitChance));

        List<Integer> damageLines = new ArrayList<>(Math.max(0, hits));
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < hits; i++) {
            if (random.nextDouble() > normalizedHitChance) {
                damageLines.add(0);
                continue;
            }

            damageLines.add(normalizedMinDamage < normalizedMaxDamage
                    ? random.nextInt(normalizedMinDamage, normalizedMaxDamage + 1)
                    : normalizedMaxDamage);
        }

        return damageLines;
    }

    public DamageProfile resolveDamageProfile(Character bot, int skillId, int skillLevel, boolean magicAttack) {
        Skill skill = skillId != 0 ? SkillFactory.getSkill(skillId) : null;
        StatEffect effect = skill != null && skillLevel > 0 ? skill.getEffect(skillLevel) : null;
        return resolveDamageProfile(bot, skillId, effect, magicAttack);
    }

    public DamageProfile resolveDamageProfile(Character bot, int skillId, StatEffect effect, boolean magicAttack) {
        if (effect != null && effect.getFixDamage() > 0) {
            int fixedDamage = Math.max(1, effect.getFixDamage());
            return new DamageProfile(fixedDamage, fixedDamage, magicAttack, true);
        }

        return magicAttack
                ? resolveMagicDamageProfile(bot, skillId, effect)
                : resolvePhysicalDamageProfile(bot, skillId, effect);
    }

    public AbstractDealDamageHandler.AttackTarget makeTarget(Character bot, Monster monster, int hits,
                                                      DamageProfile damageProfile, int hitDelayMs) {
        int[] adjustedDamage = damageProfile.alwaysHit()
                ? new int[]{damageProfile.minDamage(), damageProfile.maxDamage()}
                : applyMonsterDefense(bot, monster, damageProfile.minDamage(), damageProfile.maxDamage(),
                damageProfile.magicAttack());
        List<Integer> lines = damageProfile.alwaysHit()
                ? rollDamageLines(hits, adjustedDamage[0], adjustedDamage[1], 1.0d)
                : rollDamageLines(bot, monster, hits, adjustedDamage[0], adjustedDamage[1], damageProfile.magicAttack());
        int normalizedHitDelay = Math.max(0, Math.min(Short.MAX_VALUE, hitDelayMs));
        return new AbstractDealDamageHandler.AttackTarget((short) normalizedHitDelay, lines);
    }

    private DamageProfile resolvePhysicalDamageProfile(Character bot, int skillId, StatEffect effect) {
        int watk = Math.max(0, bot.getTotalWatk());
        long maxDamage;
        long minDamage;

        if (skillId == Rogue.LUCKY_SEVEN
                || skillId == NightWalker.LUCKY_SEVEN
                || skillId == NightLord.TRIPLE_THROW
                || skillId == NightWalker.TRIPLE_THROW) {
            maxDamage = (long) (bot.getTotalLuk() * 5L) * (long) Math.ceil(watk / 100.0d);
            minDamage = Math.max(1L, Math.round(maxDamage * 0.8d));
        } else if (skillId == DragonKnight.DRAGON_ROAR) {
            maxDamage = (long) (bot.getTotalStr() * 4L + bot.getTotalDex()) * (long) Math.ceil(watk / 100.0d);
            minDamage = Math.max(1L, Math.round(maxDamage * 0.8d));
        } else if (skillId == NightLord.VENOMOUS_STAR || skillId == Shadower.VENOMOUS_STAB) {
            maxDamage = (long) Math.ceil((18.5d * (bot.getTotalStr() + bot.getTotalLuk()) + bot.getTotalDex() * 2.0d)
                    / 100.0d * bot.calculateMaxBaseDamage(watk));
            minDamage = Math.max(1L, Math.round(maxDamage * 0.8d));
        } else if (skillId == Hermit.SHADOW_MESO && effect != null) {
            maxDamage = (long) Math.floor(effect.getMoneyCon() * 10.0d * 1.5d);
            minDamage = maxDamage;
        } else {
            maxDamage = bot.calculateMaxBaseDamage(watk);
            minDamage = bot.calculateMinBaseDamage(watk);
        }

        if (skillId != 0 && effect != null && skillId != Hermit.SHADOW_MESO) {
            int skillDamage = effect.getDamage();
            if (skillDamage > 0) {
                maxDamage = maxDamage * skillDamage / 100L;
                minDamage = minDamage * skillDamage / 100L;
            }
        }

        return normalizeDamageProfile(minDamage, maxDamage, false, false);
    }

    private DamageProfile resolveMagicDamageProfile(Character bot, int skillId, StatEffect effect) {
        long maxDamage;
        if (skillId == Cleric.HEAL && effect != null) {
            maxDamage = Math.round((bot.getTotalInt() * 4.8d + bot.getTotalLuk() * 4.0d)
                    * bot.getTotalMagic() / 1000.0d);
            maxDamage = maxDamage * Math.max(0, effect.getHp()) / 100L;
        } else {
            maxDamage = bot.calculateMaxBaseMagicDamage(bot.getTotalMagic());
            maxDamage = applyMagicAmplification(bot, maxDamage);
            if (effect != null && effect.getMatk() > 0) {
                maxDamage *= effect.getMatk();
            }
        }

        long minDamage = Math.max(1L, Math.round(maxDamage * 0.8d));
        return normalizeDamageProfile(minDamage, maxDamage, true, false);
    }

    private long applyMagicAmplification(Character bot, long baseDamage) {
        int amplificationSkillId = switch (bot.getJob()) {
            case IL_ARCHMAGE, IL_MAGE -> ILMage.ELEMENT_AMPLIFICATION;
            case FP_ARCHMAGE, FP_MAGE -> FPMage.ELEMENT_AMPLIFICATION;
            case BLAZEWIZARD3, BLAZEWIZARD4 -> BlazeWizard.ELEMENT_AMPLIFICATION;
            case EVAN7, EVAN8, EVAN9, EVAN10 -> Evan.MAGIC_AMPLIFICATION;
            default -> 0;
        };
        if (amplificationSkillId == 0) {
            return baseDamage;
        }

        Skill amplificationSkill = SkillFactory.getSkill(amplificationSkillId);
        if (amplificationSkill == null) {
            return baseDamage;
        }

        int amplificationLevel = bot.getSkillLevel(amplificationSkill);
        if (amplificationLevel <= 0) {
            return baseDamage;
        }

        StatEffect amplificationEffect = amplificationSkill.getEffect(amplificationLevel);
        return baseDamage * amplificationEffect.getY() / 100L;
    }

    private int[] applyMonsterDefense(Character bot, Monster target, int minDamage, int maxDamage, boolean magicAttack) {
        int levelDelta = Math.max(0, target.getLevel() - bot.getLevel());
        double adjustedMinDamage;
        double adjustedMaxDamage;
        if (magicAttack) {
            int monsterMagicDefense = target.getMdef();
            adjustedMaxDamage = maxDamage - monsterMagicDefense * 0.5 * (1.0 + 0.01 * levelDelta);
            adjustedMinDamage = minDamage - monsterMagicDefense * 0.6 * (1.0 + 0.01 * levelDelta);
        } else {
            int monsterWeaponDefense = target.getWdef();
            double levelFactor = 1.0 - 0.01 * levelDelta;
            adjustedMaxDamage = maxDamage * levelFactor - monsterWeaponDefense * 0.5;
            adjustedMinDamage = minDamage * levelFactor - monsterWeaponDefense * 0.6;
        }
        int normalizedMinDamage = Math.max(1, (int) adjustedMinDamage);
        int normalizedMaxDamage = Math.max(normalizedMinDamage, (int) adjustedMaxDamage);
        return new int[]{normalizedMinDamage, normalizedMaxDamage};
    }

    private DamageProfile normalizeDamageProfile(long minDamage, long maxDamage,
                                                 boolean magicAttack, boolean alwaysHit) {
        int normalizedMaxDamage = clampDamage(maxDamage);
        int normalizedMinDamage = Math.min(normalizedMaxDamage, clampDamage(minDamage));
        return new DamageProfile(normalizedMinDamage, normalizedMaxDamage, magicAttack, alwaysHit);
    }

    private int clampDamage(long damage) {
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, damage));
    }

    private int getFlatAccuracy(Character bot) {
        Integer buffedAccuracy = bot.getBuffedValue(BuffStat.ACC);
        int buffAccuracy = buffedAccuracy != null ? buffedAccuracy : 0;
        var equippedInventory = bot.getInventory(InventoryType.EQUIPPED);
        if (equippedInventory == null) {
            return buffAccuracy;
        }

        int equipAccuracy = 0;
        for (Item item : equippedInventory) {
            if (item instanceof Equip equip) {
                equipAccuracy += equip.getAcc();
            }
        }
        return buffAccuracy + equipAccuracy;
    }

    private int getFlatAvoidability(Character bot) {
        Integer buffedAvoidability = bot.getBuffedValue(BuffStat.AVOID);
        int buffAvoidability = buffedAvoidability != null ? buffedAvoidability : 0;
        var equippedInventory = bot.getInventory(InventoryType.EQUIPPED);
        if (equippedInventory == null) {
            return buffAvoidability;
        }

        int equipAvoidability = 0;
        for (Item item : equippedInventory) {
            if (item instanceof Equip equip) {
                equipAvoidability += equip.getAvoid();
            }
        }
        return buffAvoidability + equipAvoidability;
    }
}
