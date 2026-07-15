package server.agents.profiles;

import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBehaviorProfileRuntimeTest {
    @Test
    void mapleIslandQuesterLoadsExecutablePresentationSettings() {
        AgentBehaviorProfile profile = AgentBehaviorProfileRepository.mapleIslandQuester();

        assertEquals("maple-island-quester", profile.profileId());
        assertEquals(new AgentBehaviorProfile.DelayRange(600, 1400),
                profile.presentation().timing().beforeNpcInteractionMs());
        assertEquals(new AgentBehaviorProfile.DelayRange(900, 1800),
                profile.presentation().timing().betweenObjectivesMs());
        assertEquals("objective-only", profile.presentation().encounter().style());
        assertEquals("any", profile.presentation().rest().spotPreference());
        assertTrue(!profile.presentation().movement().navigationFidgetsEnabled());
        assertTrue(profile.presentation().movement().navigationFidgetModes().isEmpty());
    }

    @Test
    void assignedProfileSuppliesBoundedAgentSpecificDelays() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        assertEquals(0L, AgentBehaviorProfileRuntime.sampleNpcInteractionDelayMs(entry));
        assertEquals(0L, AgentBehaviorProfileRuntime.sampleBetweenObjectivesDelayMs(entry));

        AgentBehaviorProfileRuntime.assignMapleIslandQuester(entry);

        for (int sample = 0; sample < 100; sample++) {
            assertTrue(AgentBehaviorProfileRuntime.sampleNpcInteractionDelayMs(entry) >= 600);
            assertTrue(AgentBehaviorProfileRuntime.sampleNpcInteractionDelayMs(entry) <= 1400);
            assertTrue(AgentBehaviorProfileRuntime.sampleBetweenObjectivesDelayMs(entry) >= 900);
            assertTrue(AgentBehaviorProfileRuntime.sampleBetweenObjectivesDelayMs(entry) <= 1800);
        }
    }
}
