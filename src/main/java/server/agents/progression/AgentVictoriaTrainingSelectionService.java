package server.agents.progression;

import client.Character;
import server.agents.events.AgentDomainEvent;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentSessionEventRuntime;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Shared live selection boundary for ordinary Victoria training maps. */
final class AgentVictoriaTrainingSelectionService {
    private AgentVictoriaTrainingSelectionService() {
    }

    static Optional<AgentVictoriaTrainingCatalog.TrainingMap> select(
            AgentRuntimeEntry entry,
            Character agent,
            AgentVictoriaTrainingState state,
            AgentVictoriaTrainingCatalogRepository repository,
            int selectionLevel,
            long nowMs) {
        List<AgentVictoriaTrainingCatalog.TrainingChoice> choices =
                repository.choicesForLevel(selectionLevel);
        Set<Integer> eligible = new LinkedHashSet<>();
        for (AgentVictoriaTrainingCatalog.TrainingChoice choice : choices) {
            if (state.available(choice.mapId(), nowMs)
                    && AgentVictoriaTrainingRouteCatalog.canRoute(
                    agent.getMapId(), choice.mapId())) {
                eligible.add(choice.mapId());
            }
        }
        Map<Integer, Integer> occupancy =
                AgentVictoriaTrainingPopulation.snapshot(agent, eligible);
        AgentVictoriaTrainingMapSelector selector =
                new AgentVictoriaTrainingMapSelector(repository);
        AgentProgressionProfile profile = AgentProgressionProfileRuntime.profile(entry);
        Optional<AgentVictoriaTrainingMapSelector.Selection> selection = selector.select(
                selectionLevel, agent.getMapId(), occupancy, eligible, profile, agent.getId());
        selection.ifPresent(value -> {
            String reason = value.reason() + "; occupancy=" + value.occupancy();
            state.selected(value.map().mapId(), selectionLevel, reason, nowMs);
            AgentSessionEventRuntime.bus(entry).publish(new AgentDomainEvent(
                    agent.getId(), nowMs, "progression.map-selected",
                    "training:" + agent.getId() + ':' + nowMs,
                    Map.of("mapId", Integer.toString(value.map().mapId()),
                            "mapName", value.map().mapName(),
                            "level", Integer.toString(selectionLevel),
                            "occupancy", Integer.toString(value.occupancy()),
                            "reason", reason,
                            "purpose", "level-training")));
        });
        return selection.map(AgentVictoriaTrainingMapSelector.Selection::map);
    }

    static Set<Integer> targetMobIds(AgentVictoriaTrainingCatalog.TrainingMap map) {
        LinkedHashSet<Integer> targets = new LinkedHashSet<>();
        if (map != null) {
            for (AgentVictoriaTrainingCatalog.SpawnGroup spawn : map.spawns()) {
                if (!"hazard".equalsIgnoreCase(spawn.role())) {
                    targets.add(spawn.mobId());
                }
            }
        }
        return Set.copyOf(targets);
    }
}
