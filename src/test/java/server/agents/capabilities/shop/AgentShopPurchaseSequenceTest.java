package server.agents.capabilities.shop;

import org.junit.jupiter.api.Test;
import server.bots.BotEntry;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class AgentShopPurchaseSequenceTest {
    @Test
    void recordsOnlyTheFirstPurchaseShortfall() {
        BotEntry entry = new BotEntry(null, null, null);
        List<String> bought = new ArrayList<>();
        AgentShopPurchaseSequence sequence =
                new AgentShopPurchaseSequence(entry, null, new Point(10, 20), List.of(), bought, null);

        AgentShopBuyReport fullPurchase =
                new AgentShopBuyReport(2000000, 10, 10, AgentShopShortfallReason.NONE);
        assertSame(sequence, sequence.withFirstShortfall(fullPurchase));
        assertNull(sequence.firstShortfall());

        AgentShopBuyReport firstShortfall =
                new AgentShopBuyReport(2000001, 2, 5, AgentShopShortfallReason.NO_MESO);
        AgentShopPurchaseSequence withShortfall = sequence.withFirstShortfall(firstShortfall);

        assertEquals(firstShortfall, withShortfall.firstShortfall());
        assertSame(entry, withShortfall.entry());
        assertSame(bought, withShortfall.bought());

        AgentShopBuyReport laterShortfall =
                new AgentShopBuyReport(2000002, 1, 3, AgentShopShortfallReason.NO_SPACE);
        assertEquals(firstShortfall, withShortfall.withFirstShortfall(laterShortfall).firstShortfall());
    }
}
