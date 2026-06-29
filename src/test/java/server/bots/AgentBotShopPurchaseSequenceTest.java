package server.bots;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotShopPurchaseSequence;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class AgentBotShopPurchaseSequenceTest {
    @Test
    void recordsOnlyTheFirstPurchaseShortfall() {
        BotEntry entry = new BotEntry(null, null, null);
        List<String> bought = new ArrayList<>();
        AgentBotShopPurchaseSequence sequence =
                new AgentBotShopPurchaseSequence(entry, null, new Point(10, 20), List.of(), bought, null);

        BotShopManager.BuyReport fullPurchase =
                new BotShopManager.BuyReport(2000000, 10, 10, BotShopManager.ShortfallReason.NONE);
        assertSame(sequence, sequence.withFirstShortfall(fullPurchase));
        assertNull(sequence.firstShortfall());

        BotShopManager.BuyReport firstShortfall =
                new BotShopManager.BuyReport(2000001, 2, 5, BotShopManager.ShortfallReason.NO_MESO);
        AgentBotShopPurchaseSequence withShortfall = sequence.withFirstShortfall(firstShortfall);

        assertEquals(firstShortfall, withShortfall.firstShortfall());
        assertSame(entry, withShortfall.entry());
        assertSame(bought, withShortfall.bought());

        BotShopManager.BuyReport laterShortfall =
                new BotShopManager.BuyReport(2000002, 1, 3, BotShopManager.ShortfallReason.NO_SPACE);
        assertEquals(firstShortfall, withShortfall.withFirstShortfall(laterShortfall).firstShortfall());
    }
}
