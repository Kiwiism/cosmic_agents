package server.agents.plans.mapleisland;

import server.agents.capabilities.runtime.AgentCapabilityResult;
import server.agents.plans.amherst.AmherstObjectiveProgress;
import server.agents.plans.amherst.AmherstObjectiveProgressStatus;
import server.agents.plans.amherst.AmherstPlanCard;
import server.agents.plans.amherst.AmherstPlanObjective;
import server.agents.plans.amherst.AmherstPlanObjectiveDeferralPolicy;
import server.agents.plans.amherst.AmherstPlanProgressSnapshot;

import java.util.List;
import java.util.Set;

/** Maple Island plan extension declaring staged quest work that is independent of Pio's boxes. */
final class MapleIslandWorldResourceDeferralPolicy implements AmherstPlanObjectiveDeferralPolicy {
    static final MapleIslandWorldResourceDeferralPolicy INSTANCE =
            new MapleIslandWorldResourceDeferralPolicy();
    private static final int PIO_RECYCLED_GOODS_QUEST_ID = 1008;
    private static final Set<Integer> TRAINING_AND_LUCAS_QUEST_IDS = Set.of(
            1040,
            8020, 8021, 8022, 8023, 8024, 8025,
            1039,
            1041, 1042, 1043, 1044);
    private static final Set<Integer> BARI_QUEST_IDS = Set.of(1045);

    private MapleIslandWorldResourceDeferralPolicy() {
    }

    @Override
    public boolean canDefer(AmherstPlanCard card,
                            AmherstPlanObjective blocked,
                            AgentCapabilityResult result) {
        return supports(card, blocked);
    }

    @Override
    public List<AmherstPlanObjective> independentAlternatives(
            AmherstPlanCard card,
            AmherstPlanObjective blocked,
            AmherstPlanProgressSnapshot progress,
            int deferralStage) {
        if (!supports(card, blocked)) {
            return List.of();
        }
        if (deferralStage <= 1) {
            List<AmherstPlanObjective> trainingAndLucas = pendingObjectives(
                    card, progress, TRAINING_AND_LUCAS_QUEST_IDS);
            if (!trainingAndLucas.isEmpty()) {
                return trainingAndLucas;
            }
        }
        if (deferralStage <= 2) {
            return pendingObjectives(card, progress, BARI_QUEST_IDS);
        }
        return List.of();
    }

    private static List<AmherstPlanObjective> pendingObjectives(
            AmherstPlanCard card,
            AmherstPlanProgressSnapshot progress,
            Set<Integer> questIds) {
        return card.objectives().stream()
                .filter(objective -> objective.allQuestIds().stream()
                        .anyMatch(questIds::contains))
                .filter(objective -> !satisfied(progress, objective.objectiveId()))
                .toList();
    }

    private static boolean satisfied(AmherstPlanProgressSnapshot progress, String objectiveId) {
        AmherstObjectiveProgress objective = progress.objectives().get(objectiveId);
        return objective != null && objective.status() == AmherstObjectiveProgressStatus.SATISFIED;
    }

    private static boolean supports(AmherstPlanCard card, AmherstPlanObjective blocked) {
        return "maple-island-full-mvp".equals(card.planId())
                && blocked.kind().waitsForWorldResource()
                && blocked.allQuestIds().contains(PIO_RECYCLED_GOODS_QUEST_ID);
    }
}
