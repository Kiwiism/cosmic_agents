package server.agents.runtime.activity.control;

/** Platform boundary for bringing a selected offline Agent into neutral live control. */
@FunctionalInterface
public interface AgentDirectorSessionPort {
    SpawnResult spawnIdle(int characterId, int world, int channel, long nowMs);

    record SpawnResult(
            boolean accepted,
            int characterId,
            String agentName,
            boolean alreadyLive,
            String reason) {
        public SpawnResult {
            agentName = agentName == null ? "" : agentName.trim();
            reason = reason == null ? "" : reason.trim();
        }
        public static SpawnResult live(
                int characterId, String name, boolean alreadyLive, String reason) {
            return new SpawnResult(true, characterId, name, alreadyLive, reason);
        }
        public static SpawnResult rejected(String reason) {
            return new SpawnResult(false, 0, "", false, reason);
        }
    }
}
