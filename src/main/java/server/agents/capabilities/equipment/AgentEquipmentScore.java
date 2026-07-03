package server.agents.capabilities.equipment;

/**
 * Lexicographic auto-equip score. Damage is compared before useful stat sum.
 */
public record AgentEquipmentScore(int damage, int statSum) {
}
