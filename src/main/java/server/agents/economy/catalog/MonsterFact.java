package server.agents.economy.catalog;

public record MonsterFact(int monsterId, int level, int experience) {
    public MonsterFact {
        if (monsterId <= 0 || level < 0 || experience < 0) throw new IllegalArgumentException();
    }
}
