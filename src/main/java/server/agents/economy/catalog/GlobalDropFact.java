package server.agents.economy.catalog;

public record GlobalDropFact(int itemId, int chance, int continentId,
                             int minimumQuantity, int maximumQuantity, int questId,
                             int minimumMobLevel, int maximumMobLevel) {
    public GlobalDropFact(int itemId, int chance, int continentId,
                          int minimumQuantity, int maximumQuantity, int questId) {
        this(itemId, chance, continentId, minimumQuantity, maximumQuantity, questId, 0, 255);
    }

    public GlobalDropFact {
        if (itemId <= 0 || chance < 0 || continentId < -1 || minimumQuantity < 0
                || maximumQuantity < minimumQuantity || questId < 0 || minimumMobLevel < 0
                || maximumMobLevel < minimumMobLevel || maximumMobLevel > 255) throw new IllegalArgumentException();
    }

    public boolean isEligibleForMobLevel(int mobLevel) {
        return mobLevel >= minimumMobLevel && mobLevel <= maximumMobLevel;
    }
}
