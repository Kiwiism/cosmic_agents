package server.agents.integration.cosmic;

import client.Character;
import net.server.services.task.channel.MobPhysicsService;
import server.agents.capabilities.combat.AgentMonsterControlService;
import server.agents.capabilities.mobcontrol.AgentMobReactionRouter;
import server.agents.events.AgentEventPriority;
import server.agents.operations.events.AgentMobDamagedEvent;
import server.agents.operations.events.AgentOperationalEventPublisher;
import server.agents.runtime.simulation.AgentSimulationMapPresenceListener;
import server.integration.AgentPresenceProvider;
import server.integration.MobHitReactionContext;
import server.life.Monster;
import server.maps.MapleMap;

public enum CosmicAgentPresenceProvider implements AgentPresenceProvider {
    INSTANCE;

    private final AgentSimulationMapPresenceListener simulationListener =
            AgentSimulationMapPresenceListener.production();

    @Override
    public boolean isAgent(Character chr) {
        return CosmicCharacterGateway.INSTANCE.isHeadlessControlled(chr);
    }

    @Override
    public void mapObservationChanged(MapleMap map, boolean observed) {
        simulationListener.observationChanged(map, observed);
        if (!observed) {
            MobPhysicsService.releaseMapInstances(
                    map, MobPhysicsService.ReleaseReason.OBSERVER_LOSS);
        }
    }

    @Override
    public void agentLeftMap(MapleMap map) {
        AgentMonsterControlService.releaseHiddenSimulationControllers(map);
        MobPhysicsService.releaseDepartedAgents(map);
    }

    @Override
    public void mobHitAccepted(Character attacker, Monster monster, int appliedDamage,
                               MobHitReactionContext reactionContext) {
        AgentOperationalEventPublisher.publishFor(attacker,
                objectiveId -> new AgentMobDamagedEvent(
                        attacker.getId(), System.currentTimeMillis(), attacker.getMapId(),
                        monster.getId(), monster.getObjectId(), appliedDamage, objectiveId),
                AgentEventPriority.NORMAL);
        AgentMobReactionRouter.acceptedHit(
                attacker, monster, appliedDamage, reactionContext);
    }
}
