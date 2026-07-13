package server.agents.runtime.simulation;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.scheduler.AgentScheduler;
import server.maps.MapleMap;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

public final class AgentSimulationMapPresenceListener {
    private static final Logger log = LoggerFactory.getLogger(AgentSimulationMapPresenceListener.class);
    private final BooleanSupplier simulationEnabled;
    private final Predicate<AgentRuntimeEntry> wakeAgent;

    public AgentSimulationMapPresenceListener(BooleanSupplier simulationEnabled,
                                              Predicate<AgentRuntimeEntry> wakeAgent) {
        if (simulationEnabled == null || wakeAgent == null) {
            throw new IllegalArgumentException("Agent map-presence listener dependencies are required");
        }
        this.simulationEnabled = simulationEnabled;
        this.wakeAgent = wakeAgent;
    }

    public void observationChanged(MapleMap map, boolean observed) {
        if (map == null || !simulationEnabled.getAsBoolean()) {
            return;
        }
        for (Character character : map.getAllPlayers()) {
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByCharacterInstance(character);
            if (entry != null) {
                try {
                    wakeAgent.test(entry);
                } catch (RuntimeException failure) {
                    log.warn("Failed to wake Agent {} for map-observation transition",
                            character.getId(), failure);
                }
            }
        }
    }

    public static AgentSimulationMapPresenceListener production() {
        return new AgentSimulationMapPresenceListener(
                () -> Boolean.getBoolean("agents.scheduler.simulation.enabled"),
                AgentScheduler::wake);
    }
}
