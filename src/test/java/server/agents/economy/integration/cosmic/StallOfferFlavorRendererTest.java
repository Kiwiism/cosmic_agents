package server.agents.economy.integration.cosmic;

import org.junit.jupiter.api.Test;
import server.agents.economy.market.MarketObservation;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StallOfferFlavorRendererTest {
    @Test
    void rendersConfiguredGenericTemplateWithoutAffectingStructuredAmount() {
        var listing = new MarketObservation("o", "buyer", Instant.EPOCH, 910000001, "2",
                "stall:3", 1302013, 1, 400_000, 1, 1, 400_000, "fp",
                Map.of("watk", 50), MarketObservation.State.LISTED);
        String text = new StallOfferFlavorRenderer(
                "hey i wana offer {offer} meso for {item_stats} {item_name} thanks!")
                .render(listing, 250_000);

        assertTrue(text.startsWith("hey i wana offer 250k meso for wa50 "));
        assertTrue(text.endsWith(" thanks!"));
    }
}
