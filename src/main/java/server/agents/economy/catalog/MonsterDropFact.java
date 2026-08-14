package server.agents.economy.catalog;

public record MonsterDropFact(int monsterId, int itemId, int chance,
                              int minimumQuantity, int maximumQuantity, int questId) {
    public MonsterDropFact {
        if (monsterId <= 0 || itemId < 0 || chance < 0 || minimumQuantity < 0
                || maximumQuantity < minimumQuantity || questId < 0) {
            throw new IllegalArgumentException("Invalid authoritative monster drop fact");
        }
    }
}
