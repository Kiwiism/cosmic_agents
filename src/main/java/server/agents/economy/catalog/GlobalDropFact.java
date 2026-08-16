package server.agents.economy.catalog;

public record GlobalDropFact(int itemId, int chance, int continentId,
                             int minimumQuantity, int maximumQuantity, int questId) {
    public GlobalDropFact {
        if (itemId <= 0 || chance < 0 || continentId < -1 || minimumQuantity < 0
                || maximumQuantity < minimumQuantity || questId < 0) throw new IllegalArgumentException();
    }
}
