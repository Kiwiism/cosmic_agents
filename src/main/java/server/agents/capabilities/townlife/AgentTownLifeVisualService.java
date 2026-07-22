package server.agents.capabilities.townlife;

import client.Character;
import server.agents.capabilities.presentation.AgentWeaponFlourishService;

import java.awt.Point;

final class AgentTownLifeVisualService {
    private AgentTownLifeVisualService() {
    }

    static boolean flourish(Character agent, Point facingPoint) {
        return AgentWeaponFlourishService.flourish(agent, facingPoint);
    }
}
