package server.agents.capabilities.expedition;

/** Scenario-owned build evidence retained by the shared lobby for status output. */
public record AgentExpeditionPreparedMember(
        String job,
        String build,
        double minimumHitChance,
        int weaponItemId,
        int weaponAttack) {

    public AgentExpeditionPreparedMember {
        if (job == null || job.isBlank() || build == null || build.isBlank()
                || minimumHitChance < 0.0d || minimumHitChance > 1.0d
                || weaponItemId <= 0 || weaponAttack < 0) {
            throw new IllegalArgumentException("complete expedition member build evidence is required");
        }
    }
}
