package server.agents.administration;

public record AgentCleanSlateTarget(
        int characterId,
        String name,
        int accountId,
        int world,
        int level,
        int jobId,
        int mapId,
        int experience,
        int mesos,
        int ordinaryItemCount,
        int preservedItemCount,
        int questCount,
        int skillCount,
        boolean activeAgent,
        boolean interactiveAllowed,
        boolean dedicatedAccount,
        boolean merchantStateClear,
        String fingerprint) {
    public AgentCleanSlateTarget {
        name = name == null ? "" : name.trim();
        fingerprint = fingerprint == null ? "" : fingerprint.trim();
        if (characterId <= 0 || accountId <= 0 || name.isEmpty() || world < 0
                || level <= 0 || jobId < 0 || mapId < 0 || experience < 0 || mesos < 0
                || ordinaryItemCount < 0 || preservedItemCount < 0
                || questCount < 0 || skillCount < 0 || fingerprint.isEmpty()) {
            throw new IllegalArgumentException("valid clean-slate target is required");
        }
    }
}
