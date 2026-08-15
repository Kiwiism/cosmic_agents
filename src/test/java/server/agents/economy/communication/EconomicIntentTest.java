package server.agents.economy.communication;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EconomicIntentTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void retainsStructuredTermsIndependentlyOfFlavorText() {
        EconomicIntent value = new EconomicIntent(UUID.randomUUID(), UUID.randomUUID(), "buyer",
                "seller", EconomicIntent.Kind.MESO_OFFER, 1302013, "exact-kfan", 1, 250_000,
                910000001, "hey, 250k for that fan?", Map.of("source", "private-knowledge"),
                NOW, NOW.plusSeconds(600), EconomicIntent.Status.OPEN);

        assertEquals(250_000, value.mesos());
        assertEquals("exact-kfan", value.itemFingerprint());
        assertEquals("hey, 250k for that fan?", value.publicText());
    }

    @Test
    void rejectsSelfOffersAndNonPositiveNumericOffers() {
        assertThrows(IllegalArgumentException.class, () -> new EconomicIntent(UUID.randomUUID(),
                UUID.randomUUID(), "same", "same", EconomicIntent.Kind.BUY_INTEREST, 1302013,
                "", 1, 0, null, "", Map.of(), NOW, NOW.plusSeconds(1), EconomicIntent.Status.OPEN));
        assertThrows(IllegalArgumentException.class, () -> new EconomicIntent(UUID.randomUUID(),
                UUID.randomUUID(), "buyer", "seller", EconomicIntent.Kind.MESO_OFFER, 1302013,
                "", 1, 0, null, "", Map.of(), NOW, NOW.plusSeconds(1), EconomicIntent.Status.OPEN));
    }
}
