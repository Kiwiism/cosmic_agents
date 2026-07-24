package server.agents.capabilities.behavior;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.personality.AgentPersonalityAssignment;
import server.agents.personality.AgentPersonalityProfile;
import server.agents.personality.AgentPersonalityProfileRepository;
import server.agents.personality.AgentPersonalityState;
import server.agents.runtime.AgentForegroundPauseRuntime;
import server.agents.progression.events.AgentQuestStateChangedEvent;
import server.agents.runtime.AgentRuntimeEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentPioRelaxerInterludeEventListenerTest {
    @Test
    void relaxedPersonalityRequestsBoundedRestAndPausesPlan() {
        AgentRuntimeEntry entry = entry("relaxed-v1", 42L);

        new AgentPioRelaxerInterludeEventListener(entry).onAgentEvent(pioCompleted());

        AgentPioRelaxerInterludeState state = entry.capabilityStates()
                .require(AgentPioRelaxerInterludeState.STATE_KEY);
        assertEquals(AgentPioRelaxerInterludeState.Mode.REST, state.mode());
        assertTrue(state.durationMs() >= 15_000L && state.durationMs() <= 30_000L);
        assertTrue(AgentForegroundPauseRuntime.paused(entry));
    }

    @Test
    void restlessPersonalityRequestsBoundedPlayfulSequence() {
        AgentRuntimeEntry entry = entry("restless-v1", 84L);

        new AgentPioRelaxerInterludeEventListener(entry).onAgentEvent(pioCompleted());

        AgentPioRelaxerInterludeState state = entry.capabilityStates()
                .require(AgentPioRelaxerInterludeState.STATE_KEY);
        assertEquals(AgentPioRelaxerInterludeState.Mode.PLAYFUL, state.mode());
        assertTrue(state.durationMs() >= 2_000L && state.durationMs() <= 10_000L);
        assertTrue(AgentForegroundPauseRuntime.paused(entry));
    }

    @Test
    void efficientPersonalityContinuesWithoutInterlude() {
        AgentRuntimeEntry entry = entry("efficient-v1", 126L);

        new AgentPioRelaxerInterludeEventListener(entry).onAgentEvent(pioCompleted());

        assertFalse(entry.capabilityStates().require(
                AgentPioRelaxerInterludeState.STATE_KEY).active());
        assertFalse(AgentForegroundPauseRuntime.paused(entry));
    }

    @Test
    void explorerPersonalityUsesThePlayfulRelaxerSequence() {
        AgentRuntimeEntry entry = entry("explorer-v1", 168L);

        new AgentPioRelaxerInterludeEventListener(entry).onAgentEvent(pioCompleted());

        assertEquals(AgentPioRelaxerInterludeState.Mode.PLAYFUL,
                entry.capabilityStates().require(
                        AgentPioRelaxerInterludeState.STATE_KEY).mode());
    }

    private static AgentRuntimeEntry entry(String profileId, long seed) {
        AgentPersonalityProfile profile = AgentPersonalityProfileRepository.defaultRepository()
                .find(profileId).orElseThrow();
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);
        entry.capabilityStates().require(AgentPersonalityState.STATE_KEY).assign(
                new AgentPersonalityAssignment(
                        1, 7, "PioFixture", profile.profileId(), profile.profileVersion(), seed, 0L),
                profile, true);
        return entry;
    }

    private static AgentQuestStateChangedEvent pioCompleted() {
        return new AgentQuestStateChangedEvent(
                7, 1_000L, AgentPioRelaxerInterludeEventListener.PIO_QUEST_ID,
                1, 2, 10_000, 1_000_000, null, "pio-complete");
    }
}
