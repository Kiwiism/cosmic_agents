package server.agents.capabilities.shop;

import org.junit.jupiter.api.Test;
import server.agents.integration.InventoryGateway;
import server.agents.runtime.AgentRuntimeHandle;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class AgentShopPurchaseSequenceTest {
    @Test
    void recordsOnlyTheFirstPurchaseShortfall() {
        TestAgentRuntimeHandle entry = new TestAgentRuntimeHandle();
        InventoryGateway inventory = mock(InventoryGateway.class);
        List<String> bought = new ArrayList<>();
        AgentShopPurchaseSequence<TestAgentRuntimeHandle> sequence =
                new AgentShopPurchaseSequence<>(entry, null, inventory, new Point(10, 20), List.of(), bought, null);

        AgentShopBuyReport fullPurchase =
                new AgentShopBuyReport(2000000, 10, 10, AgentShopShortfallReason.NONE);
        assertSame(sequence, sequence.withFirstShortfall(fullPurchase));
        assertNull(sequence.firstShortfall());

        AgentShopBuyReport firstShortfall =
                new AgentShopBuyReport(2000001, 2, 5, AgentShopShortfallReason.NO_MESO);
        AgentShopPurchaseSequence<TestAgentRuntimeHandle> withShortfall = sequence.withFirstShortfall(firstShortfall);

        assertEquals(firstShortfall, withShortfall.firstShortfall());
        assertSame(entry, withShortfall.entry());
        assertSame(inventory, withShortfall.inventory());
        assertSame(bought, withShortfall.bought());

        AgentShopBuyReport laterShortfall =
                new AgentShopBuyReport(2000002, 1, 3, AgentShopShortfallReason.NO_SPACE);
        assertEquals(firstShortfall, withShortfall.withFirstShortfall(laterShortfall).firstShortfall());
    }

    private static final class TestAgentRuntimeHandle implements AgentRuntimeHandle {
    }
}
