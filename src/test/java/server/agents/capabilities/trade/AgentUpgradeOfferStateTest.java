package server.agents.capabilities.trade;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentUpgradeOfferStateTest {
    @Test
    void defaultsPreserveLegacyBotEntryValues() {
        AgentUpgradeOfferState state = new AgentUpgradeOfferState();

        assertTrue(state.proactiveUpgradeOffers());
        assertEquals(0L, state.nextGearSuggestionAt());
        assertFalse(state.spawnUpgradeCheckDone());
        assertFalse(state.hasRequestedUpgradeItem(1002000));
    }

    @Test
    void storesProactiveOfferAndSpawnCheckFlags() {
        AgentUpgradeOfferState state = new AgentUpgradeOfferState();

        state.setProactiveUpgradeOffers(false);
        state.setSpawnUpgradeCheckDone(true);

        assertFalse(state.proactiveUpgradeOffers());
        assertTrue(state.spawnUpgradeCheckDone());
    }

    @Test
    void storesNextGearSuggestionTimestamp() {
        AgentUpgradeOfferState state = new AgentUpgradeOfferState();

        state.setNextGearSuggestionAt(12_000L);

        assertEquals(12_000L, state.nextGearSuggestionAt());
    }

    @Test
    void tracksAndClearsRequestedUpgradeItems() {
        AgentUpgradeOfferState state = new AgentUpgradeOfferState();

        state.rememberRequestedUpgradeItem(1002000);
        state.rememberRequestedUpgradeItem(1002001);

        assertTrue(state.hasRequestedUpgradeItem(1002000));
        assertTrue(state.hasRequestedUpgradeItem(1002001));

        state.clearRequestedUpgradeItems();

        assertFalse(state.hasRequestedUpgradeItem(1002000));
        assertFalse(state.hasRequestedUpgradeItem(1002001));
    }
}
