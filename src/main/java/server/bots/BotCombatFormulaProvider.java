package server.bots;

import client.BuffStat;
import client.Character;
import client.inventory.Equip;
import client.inventory.InventoryType;
import client.inventory.Item;
import server.life.Monster;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

final class BotCombatFormulaProvider {
    private static final double MIN_HIT_CHANCE = 0.01d;
    private static final double MAX_HIT_CHANCE = 1.0d;
    private static final BotCombatFormulaProvider instance = new BotCombatFormulaProvider();

    static BotCombatFormulaProvider getInstance() {
        return instance;
    }

    int getTotalAccuracy(Character bot) {
        int derivedAccuracy = (int) Math.floor(bot.getTotalDex() * 0.8d + bot.getTotalLuk() * 0.5d);
        return Math.max(0, derivedAccuracy + getFlatAccuracy(bot));
    }

    int getTotalMagicAccuracy(Character bot) {
        // Magic accuracy = 5 × (floor(INT/10) + floor(LUK/10))  — per cat123/Eric client research
        int derivedMagicAccuracy = 5 * ((int) Math.floor(bot.getTotalInt() / 10.0)
                + (int) Math.floor(bot.getTotalLuk() / 10.0));
        return Math.max(0, derivedMagicAccuracy);
    }

    int getTotalAvoidability(Character bot) {
        int derivedAvoidability = (int) Math.floor(bot.getTotalDex() * 0.25d + bot.getTotalLuk() * 0.5d);
        return Math.max(0, derivedAvoidability + getFlatAvoidability(bot));
    }

    double calculateMobHitChance(Character bot, Monster monster) {
        return calculateMobHitChance(bot, monster, false);
    }

    double calculateMobHitChance(Character bot, Monster monster, boolean magicAttack) {
        if (magicAttack) {
            return calculateMagicMobHitChance(getTotalMagicAccuracy(bot), bot.getLevel(), monster.getLevel(), monster.getAvoidability());
        }
        return calculatePhysicalMobHitChance(getTotalAccuracy(bot), bot.getLevel(), monster.getLevel(), monster.getAvoidability());
    }

    double calculateMobHitChance(int accuracy, int botLevel, int monsterLevel, int monsterAvoidability) {
        return calculatePhysicalMobHitChance(accuracy, botLevel, monsterLevel, monsterAvoidability);
    }

    double calculatePhysicalMobHitChance(int accuracy, int botLevel, int monsterLevel, int monsterAvoidability) {
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

    double calculateMagicMobHitChance(int magicAccuracy, int botLevel, int monsterLevel, int monsterAvoidability) {
        // Same accuracy_rate scaling as physical; random bounds are (0.5, 1.2) for magic
        // => hitChance = (1.2 - avoid/accuracy_rate) / 0.7
        int levelDelta = Math.max(0, monsterLevel - botLevel);
        if (monsterAvoidability <= 0) return MAX_HIT_CHANCE;
        double accuracyRate = magicAccuracy * 100.0 / (levelDelta * 10 + 255);
        double hitChance = (1.2 - monsterAvoidability / accuracyRate) / 0.7;
        return Math.max(MIN_HIT_CHANCE, Math.min(MAX_HIT_CHANCE, hitChance));
    }

    double calculateBotAvoidChance(Character bot, Monster monster) {
        return calculateBotAvoidChance(monster.getAccuracy(), monster.getLevel(), bot.getLevel(), getTotalAvoidability(bot));
    }

    double calculateBotAvoidChance(int monsterAccuracy, int monsterLevel, int botLevel, int botAvoidability) {
        int levelDelta = Math.max(0, botLevel - monsterLevel);
        double hitChance = monsterAccuracy / (((1.84d + 0.07d * levelDelta) * botAvoidability) + 1.0d);
        hitChance = Math.max(MIN_HIT_CHANCE, hitChance);
        return Math.min(MAX_HIT_CHANCE, hitChance);
    }

    boolean doesMobHit(Character bot, Monster monster) {
        return doesMobHit(calculateBotAvoidChance(bot, monster));
    }

    boolean doesMobHit(double hitChance) {
        double normalizedHitChance = Math.max(0.0d, Math.min(MAX_HIT_CHANCE, hitChance));
        return ThreadLocalRandom.current().nextDouble() <= normalizedHitChance;
    }

    List<Integer> rollDamageLines(Character bot, Monster monster, int hits, int minDamage, int maxDamage) {
        return rollDamageLines(bot, monster, hits, minDamage, maxDamage, false);
    }

    List<Integer> rollDamageLines(Character bot, Monster monster, int hits, int minDamage, int maxDamage, boolean magicAttack) {
        return rollDamageLines(hits, minDamage, maxDamage, calculateMobHitChance(bot, monster, magicAttack));
    }

    List<Integer> rollDamageLines(int hits, int minDamage, int maxDamage, double hitChance) {
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
