package server.agents.capabilities.equipment;

import client.inventory.Equip;

import java.util.Map;

/**
 * Result of one fixed-weapon equipment DP branch.
 */
public record AgentEquipmentDpResult(Map<Short, Equip> picks,
                                     AgentEquipmentScore score,
                                     boolean paretoCapHit) {
}
