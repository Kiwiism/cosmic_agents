package server.agents.plans.mapleisland;

import server.agents.capabilities.runtime.AgentCapabilityResult;
import server.agents.plans.amherst.AmherstObjectiveProgress;
import server.agents.plans.amherst.AmherstObjectiveProgressStatus;
import server.agents.plans.amherst.AmherstPlanCard;
import server.agents.plans.amherst.AmherstPlanObjective;
import server.agents.plans.amherst.AmherstPlanObjectiveDeferralPolicy;
import server.agents.plans.amherst.AmherstPlanObjectiveKind;
import server.agents.plans.amherst.AmherstPlanProgressSnapshot;

import java.util.List;
import java.util.function.Predicate;

/** Maple Island plan extension declaring staged quest work that is independent of Pio's boxes. */
final class MapleIslandWorldResourceDeferralPolicy implements AmherstPlanObjectiveDeferralPolicy {
    static final MapleIslandWorldResourceDeferralPolicy INSTANCE =
            new MapleIslandWorldResourceDeferralPolicy();
    private static final int PIO_RECYCLED_GOODS_QUEST_ID = 1008;
    private static final int LAST_ALTERNATIVE_STAGE = config.AgentTuning.intValue("server.agents.plans.mapleisland.MapleIslandWorldResourceDeferralPolicy.LAST_ALTERNATIVE_STAGE");

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
        return switch (deferralStage) {
            case 1 -> pendingObjectives(card, progress,
                    MapleIslandWorldResourceDeferralPolicy::isMariaAndLucasPreparation);
            case 2 -> pendingObjectives(card, progress,
                    MapleIslandWorldResourceDeferralPolicy::isYoonaMaiAndLucasCompletion);
            case 3 -> pendingObjectives(card, progress,
                    objective -> objective.allQuestIds().contains(1045));
            default -> List.of();
        };
    }

    @Override
    public int alternativeStageCount(AmherstPlanCard card, AmherstPlanObjective blocked) {
        return supports(card, blocked) ? LAST_ALTERNATIVE_STAGE : 0;
    }

    @Override
    public boolean waitForWorldResourceAfterAlternatives(
            AmherstPlanCard card,
            AmherstPlanObjective blocked,
            AgentCapabilityResult result,
            int nextDeferralStage) {
        return supports(card, blocked) && nextDeferralStage > LAST_ALTERNATIVE_STAGE;
    }

    private static List<AmherstPlanObjective> pendingObjectives(
            AmherstPlanCard card,
            AmherstPlanProgressSnapshot progress,
            Predicate<AmherstPlanObjective> stageSelector) {
        return card.objectives().stream()
                .filter(stageSelector)
                .filter(objective -> !satisfied(progress, objective.objectiveId()))
                .toList();
    }

    private static boolean isMariaAndLucasPreparation(AmherstPlanObjective objective) {
        return (objective.kind() == AmherstPlanObjectiveKind.QUEST_COMPLETE
                && objective.allQuestIds().contains(1037))
                || (objective.kind() == AmherstPlanObjectiveKind.QUEST_CHAIN
                && objective.allQuestIds().contains(1038))
                || (objective.kind() == AmherstPlanObjectiveKind.QUEST_START
                && objective.allQuestIds().contains(1040));
    }

    private static boolean isYoonaMaiAndLucasCompletion(AmherstPlanObjective objective) {
        boolean yoonaOrMai = objective.allQuestIds().stream().anyMatch(questId ->
                questId >= 8020 && questId <= 8025
                        || questId == 1039
                        || questId >= 1041 && questId <= 1044);
        boolean lucasCompletion = objective.kind() == AmherstPlanObjectiveKind.QUEST_COMPLETE
                && objective.allQuestIds().contains(1040);
        return yoonaOrMai || lucasCompletion;
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
