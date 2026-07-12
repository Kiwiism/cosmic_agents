package server.agents.integration;

public record AgentCharacterStateSnapshot(
        int jobId,
        int level,
        int hp,
        int maxHp,
        int mp,
        int maxMp,
        boolean alive) {
}
