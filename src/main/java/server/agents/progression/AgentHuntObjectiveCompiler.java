package server.agents.progression;

import java.util.ArrayList;
import java.util.List;

/** Compiles authored quest-pack constraints into the universal hunt contract. */
final class AgentHuntObjectiveCompiler {
    private static final int DEFAULT_RECOMMENDED_AGENTS = config.AgentTuning.intValue(
            "server.agents.progression.AgentHuntObjectiveCompiler.DEFAULT_RECOMMENDED_AGENTS");
    private static final int DEFAULT_MAXIMUM_AGENTS = config.AgentTuning.intValue(
            "server.agents.progression.AgentHuntObjectiveCompiler.DEFAULT_MAXIMUM_AGENTS");

    private AgentHuntObjectiveCompiler() {
    }

    static AgentHuntObjectiveSpec sharedQuestPack(
            String selectionId,
            AgentVictoriaSharedQuestPackCatalog.Step step,
            List<AgentHuntSelectionRequest.ObjectiveDemand> objectives) {
        if (step == null || step.mapId() <= 0) {
            throw new IllegalArgumentException("a shared hunt requires an authored primary map");
        }
        List<AgentVictoriaQuestRuntimeCatalog.HuntMap> preferredMaps = new ArrayList<>();
        preferredMaps.add(map(1, step.mapId(), step.preferredMobIds()));
        int rank = 2;
        for (int fallbackMapId : step.fallbackMapIds()) {
            preferredMaps.add(map(rank++, fallbackMapId, step.preferredMobIds()));
        }
        return new AgentHuntObjectiveSpec(selectionId, objectives, preferredMaps, true);
    }

    private static AgentVictoriaQuestRuntimeCatalog.HuntMap map(
            int rank, int mapId, List<Integer> targetMobIds) {
        return new AgentVictoriaQuestRuntimeCatalog.HuntMap(
                rank,
                mapId,
                DEFAULT_RECOMMENDED_AGENTS,
                DEFAULT_MAXIMUM_AGENTS,
                targetMobIds);
    }
}
