package server.agents.plans.mapleisland;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.runtime.AgentCapabilityReasonCode;
import server.agents.capabilities.runtime.AgentCapabilityResult;
import server.agents.capabilities.AgentCapabilityStatus;
import server.agents.plans.amherst.AmherstPlanCard;
import server.agents.plans.amherst.AmherstPlanObjective;
import server.agents.plans.amherst.AmherstPlanObjectiveKind;
import server.agents.plans.amherst.AmherstPlanProgressService;
import server.agents.plans.amherst.AmherstPlanProgressSnapshot;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapleIslandWorldResourceDeferralPolicyTest {
    @Test
    void pioReactorWaitUsesThreeDistinctAlternativeStagesBeforeFinalWaiting() throws Exception {
        AmherstPlanCard card = AgentMapleIslandPlanRuntime.fullCard();
        AmherstPlanObjective pioReactor = card.objectives().stream()
                .filter(objective -> objective.kind() == AmherstPlanObjectiveKind.REACTOR_HIT)
                .filter(objective -> objective.allQuestIds().contains(1008))
                .findFirst()
                .orElseThrow();
        AgentCapabilityResult timeout = new AgentCapabilityResult(
                AgentCapabilityStatus.TIMED_OUT,
                AgentCapabilityReasonCode.DEADLINE_EXCEEDED,
                "reactor unavailable");

        assertTrue(MapleIslandWorldResourceDeferralPolicy.INSTANCE.canDefer(
                card, pioReactor, timeout));
        assertEquals(3, MapleIslandWorldResourceDeferralPolicy.INSTANCE
                .alternativeStageCount(card, pioReactor));
        AmherstPlanProgressService progressService = new AmherstPlanProgressService();
        AmherstPlanProgressSnapshot progress = progressService.ensureObjectives(
                AmherstPlanProgressSnapshot.empty(card.planId(), 1), card, 1L);
        List<AmherstPlanObjective> firstStage =
                MapleIslandWorldResourceDeferralPolicy.INSTANCE.independentAlternatives(
                        card, pioReactor, progress, 1);

        assertEquals(List.of(
                AmherstPlanObjectiveKind.QUEST_COMPLETE + ":1037",
                AmherstPlanObjectiveKind.QUEST_CHAIN + ":1038",
                AmherstPlanObjectiveKind.QUEST_START + ":1040"),
                firstStage.stream().map(MapleIslandWorldResourceDeferralPolicyTest::label).toList());

        for (AmherstPlanObjective objective : firstStage) {
            progress = progressService.reconcile(
                    progress, objective.objectiveId(), true, "test complete", 2L);
        }
        List<AmherstPlanObjective> resumedFirstStage =
                MapleIslandWorldResourceDeferralPolicy.INSTANCE.independentAlternatives(
                        card, pioReactor, progress, 1);
        assertTrue(resumedFirstStage.isEmpty());

        List<AmherstPlanObjective> secondStage =
                MapleIslandWorldResourceDeferralPolicy.INSTANCE.independentAlternatives(
                        card, pioReactor, progress, 2);
        assertEquals(List.of(8020, 8021, 8022, 8023, 8024, 8025,
                        1039, 1041, 1042, 1043, 1044, 1040), secondStage.stream()
                .flatMap(objective -> objective.allQuestIds().stream())
                .distinct()
                .toList());
        assertEquals(List.of(AmherstPlanObjectiveKind.QUEST_COMPLETE), secondStage.stream()
                .filter(objective -> objective.allQuestIds().contains(1040))
                .map(AmherstPlanObjective::kind)
                .toList());
        for (AmherstPlanObjective objective : secondStage) {
            progress = progressService.reconcile(
                    progress, objective.objectiveId(), true, "test complete", 3L);
        }
        List<AmherstPlanObjective> thirdStage =
                MapleIslandWorldResourceDeferralPolicy.INSTANCE.independentAlternatives(
                        card, pioReactor, progress, 3);
        assertEquals(List.of(1045), thirdStage.stream()
                .flatMap(objective -> objective.allQuestIds().stream())
                .distinct()
                .toList());
        for (AmherstPlanObjective objective : thirdStage) {
            progress = progressService.reconcile(
                    progress, objective.objectiveId(), true, "test complete", 4L);
        }
        assertTrue(MapleIslandWorldResourceDeferralPolicy.INSTANCE
                .independentAlternatives(card, pioReactor, progress, 3).isEmpty());
        assertFalse(MapleIslandWorldResourceDeferralPolicy.INSTANCE
                .waitForWorldResourceAfterAlternatives(card, pioReactor, timeout, 3));
        assertTrue(MapleIslandWorldResourceDeferralPolicy.INSTANCE
                .waitForWorldResourceAfterAlternatives(card, pioReactor, timeout, 4));
    }

    private static String label(AmherstPlanObjective objective) {
        return objective.kind() + ":" + objective.allQuestIds().getFirst();
    }
}
