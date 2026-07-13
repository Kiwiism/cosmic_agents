package server.agents.plans.amherst;

import org.junit.jupiter.api.Test;
import server.agents.testing.MutablePrimitiveGatewayFixture;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

class AmherstObjectiveReconcilerTest {
    @Test
    void completedAndStartedQuestStatesAreDistinguished() throws Exception {
        AmherstPlanCard card = load();
        var fixture = new MutablePrimitiveGatewayFixture();
        AmherstObjectiveReconciler reconciler = new AmherstObjectiveReconciler(fixture.gateway);
        AmherstPlanObjective complete1031 = card.objectives().stream()
                .filter(objective -> objective.kind() == AmherstPlanObjectiveKind.QUEST_CHAIN)
                .filter(objective -> objective.questIds().contains(1031))
                .findFirst().orElseThrow();

        fixture.quests.put(1031, 1);
        assertFalse(reconciler.reconcile(card, complete1031, fixture.agent).satisfied());
        fixture.quests.put(1031, 2);
        assertTrue(reconciler.reconcile(card, complete1031, fixture.agent).satisfied());
    }

    @Test
    void optionalUnavailableQuestIsSatisfiedButAvailableQuestIsNot() throws Exception {
        AmherstPlanCard card = load();
        var fixture = new MutablePrimitiveGatewayFixture();
        AmherstObjectiveReconciler reconciler = new AmherstObjectiveReconciler(fixture.gateway);
        AmherstPlanObjective optional = new AmherstPlanObjective(
                "optional-quest", AmherstPlanObjectiveKind.QUEST_CHAIN_IF_AVAILABLE,
                0, 0, 1000000, null, List.of(1031), null, List.of(),
                null, List.of(), List.of(), List.of(), null, null);

        when(fixture.gateway.canStartQuest(any(), anyInt(), anyInt())).thenReturn(false);
        assertTrue(reconciler.reconcile(card, optional, fixture.agent).satisfied());
        when(fixture.gateway.canStartQuest(any(), anyInt(), anyInt())).thenReturn(true);
        assertFalse(reconciler.reconcile(card, optional, fixture.agent).satisfied());
    }

    @Test
    void itemUseAndKillProgressUseAuthoritativeLiveState() throws Exception {
        AmherstPlanCard card = load();
        var fixture = new MutablePrimitiveGatewayFixture();
        AmherstObjectiveReconciler reconciler = new AmherstObjectiveReconciler(fixture.gateway);
        AmherstPlanObjective use = card.objectives().stream()
                .filter(objective -> objective.kind() == AmherstPlanObjectiveKind.USE_ITEM)
                .findFirst().orElseThrow();
        AmherstPlanObjective kills = card.objectives().stream()
                .filter(objective -> objective.kind() == AmherstPlanObjectiveKind.KILL_MOBS)
                .findFirst().orElseThrow();

        fixture.quests.put(1021, 1);
        fixture.items.put(2010007, 0);
        assertTrue(reconciler.reconcile(card, use, fixture.agent).satisfied());

        fixture.quests.put(kills.questId(), 1);
        for (int i = 0; i < kills.mobIds().size(); i++) {
            fixture.progress.put(MutablePrimitiveGatewayFixture.key(kills.questId(), kills.mobIds().get(i)),
                    kills.counts().get(i));
        }
        kills.itemIds().forEach(itemId -> fixture.items.merge(itemId, 1, Integer::sum));
        assertTrue(reconciler.reconcile(card, kills, fixture.agent).satisfied());
    }

    private static AmherstPlanCard load() throws Exception {
        return new AmherstPlanCardLoader().load(Path.of(
                "docs", "agents", "plans", "maple-island-amherst-subphase.plan.json"));
    }
}
