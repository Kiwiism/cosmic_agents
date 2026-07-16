package server.agents.integration.cosmic;

import client.Character;
import server.agents.capabilities.combat.AgentMonsterControlService;
import server.agents.capabilities.combat.AgentSyntheticMobReactionService;
import server.agents.runtime.simulation.AgentSimulationMapPresenceListener;
import server.integration.AgentPresenceProvider;
import server.life.Monster;
import server.maps.MapleMap;

public enum CosmicAgentPresenceProvider implements AgentPresenceProvider {
    INSTANCE;

    private final AgentSimulationMapPresenceListener simulationListener =
            AgentSimulationMapPresenceListener.production();

    @Override
    public boolean isAgent(Character chr) {
        return CosmicCharacterGateway.INSTANCE.isAgentCharacter(chr);
    }

    @Override
    public void mapObservationChanged(MapleMap map, boolean observed) {
        simulationListener.observationChanged(map, observed);
    }

    @Override
    public void agentLeftMap(MapleMap map) {
        AgentMonsterControlService.releaseHiddenSimulationControllers(map);
    }

    @Override
    public void mobHitAccepted(Character attacker, Monster monster,
                               int appliedDamage, long reactionDelayMs) {
        AgentSyntheticMobReactionService.acceptedHit(
                attacker, monster, appliedDamage, reactionDelayMs);
    }
}
