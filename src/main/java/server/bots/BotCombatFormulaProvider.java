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

    int getTotalAvoidability(Character bot) {
        int derivedAvoidability = (int) Math.floor(bot.getTotalDex() * 0.25d + bot.getTotalLuk() * 0.5d);
        return Math.max(0, derivedAvoidability + getFlatAvoidability(bot));
    }

    double calculateMobHitChance(Character bot, Monster monster) {
        return calculateMobHitChance(getTotalAccuracy(bot), bot.getLevel(), monster.getLevel(), monster.getAvoidability());
    }

    double calculateMobHitChance(int accuracy, int botLevel, int monsterLevel, int monsterAvoidability) {
        int levelDelta = Math.max(0, monsterLevel - botLevel);
        double hitChance = accuracy / (((1.84d + 0.07d * levelDelta) * monsterAvoidability) + 1.0d);
        hitChance = Math.max(MIN_HIT_CHANCE, hitChance);
        return Math.min(MAX_HIT_CHANCE, hitChance);
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
        return rollDamageLines(hits, minDamage, maxDamage, calculateMobHitChance(bot, monster));
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
