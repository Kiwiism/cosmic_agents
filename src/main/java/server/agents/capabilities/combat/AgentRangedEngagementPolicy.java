package server.agents.capabilities.combat;

/** Pure ranged-spacing policy. Live movement and attack execution remain outside this class. */
public final class AgentRangedEngagementPolicy {
    private AgentRangedEngagementPolicy() {
    }

    public record Input(boolean targetInDegenerateBand,
                        boolean degenerateAttackAlreadyCommitted,
                        boolean priorityTargetPresent,
                        boolean retreatRequested,
                        boolean canFireWithoutDegenerateAttack) {
    }

    public record Decision(boolean allowOneDegenerateAttack,
                           boolean retreat,
                           boolean attackGateOpen) {
    }

    public static Decision decide(Input input) {
        boolean allowOneDegenerateAttack = input.targetInDegenerateBand()
                && !input.degenerateAttackAlreadyCommitted()
                && !input.priorityTargetPresent();
        boolean retreat = input.degenerateAttackAlreadyCommitted()
                || (input.retreatRequested() && !allowOneDegenerateAttack);
        boolean attackGateOpen = !retreat
                || input.canFireWithoutDegenerateAttack()
                || allowOneDegenerateAttack;
        return new Decision(allowOneDegenerateAttack, retreat, attackGateOpen);
    }
}
