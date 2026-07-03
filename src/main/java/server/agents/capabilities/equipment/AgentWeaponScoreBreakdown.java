package server.agents.capabilities.equipment;

/**
 * Diagnostic breakdown for a weapon branch's normalized equipment score.
 */
public record AgentWeaponScoreBreakdown(int rawMax,
                                        int preCycleDamage,
                                        int cycleMs,
                                        int normalizedDamage) {
}
