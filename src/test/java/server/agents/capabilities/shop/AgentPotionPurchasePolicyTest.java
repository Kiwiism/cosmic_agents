package server.agents.capabilities.shop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPotionPurchasePolicyTest {
    @Test
    void shouldSizePurchaseFromRecoveryCapacityInsteadOfStackCount() {
        int quantity = AgentPotionPurchasePolicy.quantityToTarget(
                2_000,
                20,
                300,
                1_000,
                10.0,
                0,
                true);

        assertEquals(27, quantity);
    }

    @Test
    void shouldApplyMinimumAndVisitAndCarryBounds() {
        assertEquals(10, AgentPotionPurchasePolicy.quantityToTarget(
                9_900, 20, 100, 1_000, 10.0, 0, true));
        assertEquals(5, AgentPotionPurchasePolicy.quantityToTarget(
                0, 195, 100, 1_000, 10.0, 0, true));
        assertEquals(7, AgentPotionPurchasePolicy.quantityToTarget(
                0, 20, 100, 1_000, 10.0, 93, true));
    }

    @Test
    void shouldSplitNormalBudgetWhenBothResourcesNeedStock() {
        assertEquals(500, AgentPotionPurchasePolicy.normalSpendBudget(1_000, true));
        assertEquals(1_000, AgentPotionPurchasePolicy.normalSpendBudget(1_000, false));
        assertTrue(AgentPotionPurchasePolicy.triggerReserveBars(false)
                > AgentPotionPurchasePolicy.triggerReserveBars(true));
    }
}
