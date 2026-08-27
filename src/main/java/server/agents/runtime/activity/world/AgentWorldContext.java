package server.agents.runtime.activity.world;

import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.progression.AgentMushroomKingdomFarmSnapshot;
import server.agents.progression.AgentPepeEquipmentSnapshot;

import java.util.Map;
import java.util.Set;

/** Immutable facts captured for one World Director evaluation. */
public record AgentWorldContext(
        long sequence,
        long capturedAtMs,
        int agentId,
        String agentName,
        int level,
        int jobId,
        int mapId,
        int hp,
        int maxHp,
        int mp,
        int maxMp,
        long meso,
        boolean alive,
        boolean ownsSquishyShoes,
        Set<Integer> activeQuestIds,
        Set<Integer> completedQuestIds,
        AgentActivityKind currentActivityKind,
        String currentControllerId,
        String currentSessionId,
        String currentPlanId,
        String careerStage,
        AgentPepeEquipmentSnapshot pepeEquipment,
        AgentMushroomKingdomFarmSnapshot mushroomKingdomFarming,
        Map<String, String> evidence) {

    public AgentWorldContext {
        if (sequence <= 0L || capturedAtMs < 0L || agentId <= 0 || level <= 0
                || jobId < 0 || mapId < 0 || hp < 0 || maxHp < 0 || mp < 0
                || maxMp < 0 || meso < 0L) {
            throw new IllegalArgumentException("valid immutable Agent world facts are required");
        }
        agentName = normalize(agentName);
        currentControllerId = normalize(currentControllerId);
        currentSessionId = normalize(currentSessionId);
        currentPlanId = normalize(currentPlanId);
        careerStage = normalize(careerStage);
        pepeEquipment = pepeEquipment == null ? AgentPepeEquipmentSnapshot.NONE : pepeEquipment;
        mushroomKingdomFarming = mushroomKingdomFarming == null
                ? AgentMushroomKingdomFarmSnapshot.EMPTY : mushroomKingdomFarming;
        activeQuestIds = Set.copyOf(activeQuestIds == null ? Set.of() : activeQuestIds);
        completedQuestIds = Set.copyOf(
                completedQuestIds == null ? Set.of() : completedQuestIds);
        evidence = Map.copyOf(evidence == null ? Map.of() : evidence);
    }

    /** Source-compatible constructor for existing callers without Mushroom Kingdom gear facts. */
    public AgentWorldContext(
            long sequence, long capturedAtMs, int agentId, String agentName, int level, int jobId,
            int mapId, int hp, int maxHp, int mp, int maxMp, long meso, boolean alive,
            boolean ownsSquishyShoes, Set<Integer> activeQuestIds, Set<Integer> completedQuestIds,
            AgentActivityKind currentActivityKind, String currentControllerId,
            String currentSessionId, String currentPlanId, String careerStage,
            Map<String, String> evidence) {
        this(sequence, capturedAtMs, agentId, agentName, level, jobId, mapId, hp, maxHp, mp,
                maxMp, meso, alive, ownsSquishyShoes, activeQuestIds, completedQuestIds,
                currentActivityKind, currentControllerId, currentSessionId, currentPlanId,
                careerStage, AgentPepeEquipmentSnapshot.NONE,
                AgentMushroomKingdomFarmSnapshot.EMPTY, evidence);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
