package server.agents.capabilities.equipment;

import client.inventory.Equip;

import java.util.Map;

/**
 * Result of the legacy equipment optimizer: the chosen weapon and non-ring slot picks.
 * Picks omit slots the optimizer chose to leave empty.
 */
public record AgentEquipmentOptimizerResult(Equip weapon, Map<Short, Equip> picks) {
}
