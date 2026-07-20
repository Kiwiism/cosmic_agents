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
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapleIslandWorldResourceDeferralPolicyTest {
    @Test
    void pioReactorWaitUsesTrainingThenBariAsSeparateRetryStages() throws Exception {
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
        AmherstPlanProgressService progressService = new AmherstPlanProgressService();
        AmherstPlanProgressSnapshot progress = progressService.ensureObjectives(
                AmherstPlanProgressSnapshot.empty(card.planId(), 1), card, 1L);
        List<AmherstPlanObjective> firstStage =
                MapleIslandWorldResourceDeferralPolicy.INSTANCE.independentAlternatives(
                        card, pioReactor, progress, 1);

        assertEquals(List.of(1040, 8020, 8021, 8022, 8023, 8024, 8025,
                        1039, 1041, 1042, 1043, 1044), firstStage.stream()
                .flatMap(objective -> objective.allQuestIds().stream())
                .distinct()
                .toList());

        for (AmherstPlanObjective objective : firstStage) {
            progress = progressService.reconcile(
                    progress, objective.objectiveId(), true, "test complete", 2L);
        }
        List<AmherstPlanObjective> resumedFirstStage =
                MapleIslandWorldResourceDeferralPolicy.INSTANCE.independentAlternatives(
                        card, pioReactor, progress, 1);
        assertEquals(List.of(1045), resumedFirstStage.stream()
                .flatMap(objective -> objective.allQuestIds().stream())
                .distinct()
                .toList());

        List<AmherstPlanObjective> secondStage =
                MapleIslandWorldResourceDeferralPolicy.INSTANCE.independentAlternatives(
                        card, pioReactor, progress, 2);
        assertEquals(List.of(1045), secondStage.stream()
                .flatMap(objective -> objective.allQuestIds().stream())
                .distinct()
                .toList());
        for (AmherstPlanObjective objective : secondStage) {
            progress = progressService.reconcile(
                    progress, objective.objectiveId(), true, "test complete", 3L);
        }
        assertTrue(MapleIslandWorldResourceDeferralPolicy.INSTANCE.independentAlternatives(
                card, pioReactor, progress, 2).isEmpty());
        assertTrue(MapleIslandWorldResourceDeferralPolicy.INSTANCE.independentAlternatives(
                card, pioReactor, progress, 3).isEmpty());
    }
}
