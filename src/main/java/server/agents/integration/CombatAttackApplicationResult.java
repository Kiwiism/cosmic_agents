package server.agents.integration;

/** Authoritative result returned after the shared damage handler processes an Agent attack. */
public record CombatAttackApplicationResult(Status status, Reason reason) {
    public enum Status {
        APPLIED,
        REJECTED
    }

    public enum Reason {
        NONE,
        HANDLER_REJECTED
    }

    public boolean applied() {
        return status == Status.APPLIED;
    }

    public static CombatAttackApplicationResult appliedResult() {
        return new CombatAttackApplicationResult(Status.APPLIED, Reason.NONE);
    }

    public static CombatAttackApplicationResult rejected(Reason reason) {
        return new CombatAttackApplicationResult(Status.REJECTED, reason);
    }
}
