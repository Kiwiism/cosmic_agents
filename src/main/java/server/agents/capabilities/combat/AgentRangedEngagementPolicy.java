package server.agents.capabilities.combat;

/** Pure ranged-spacing policy. Live movement and attack execution remain outside this class. */
public final class AgentRangedEngagementPolicy {
    private AgentRangedEngagementPolicy() {
    }

    public record Input(boolean targetInDegenerateBand,
                        boolean degenerateAttackAlreadyCommitted,
                        boolean priorityTargetPresent,
                        boolean retreatRequested,
                        boolean canFireWithoutDegenerateAttack,
                        boolean bossSpacingRequired) {
    }

    public record Decision(boolean allowOneDegenerateAttack,
                           boolean retreat,
                           boolean attackGateOpen) {
    }

    public static Decision decide(Input input) {
        boolean allowOneDegenerateAttack = input.targetInDegenerateBand()
                && !input.degenerateAttackAlreadyCommitted()
                && !input.priorityTargetPresent()
                && !input.bossSpacingRequired();
        boolean retreat = input.bossSpacingRequired()
                || input.degenerateAttackAlreadyCommitted()
                || (input.retreatRequested() && !allowOneDegenerateAttack);
        boolean attackGateOpen = !input.bossSpacingRequired()
                && (!retreat || input.canFireWithoutDegenerateAttack() || allowOneDegenerateAttack);
        return new Decision(allowOneDegenerateAttack, retreat, attackGateOpen);
    }
}
