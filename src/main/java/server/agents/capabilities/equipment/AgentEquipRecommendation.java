package server.agents.capabilities.equipment;

import client.inventory.Equip;

public record AgentEquipRecommendation(short targetSlot, Equip current, Equip candidate) {
}
