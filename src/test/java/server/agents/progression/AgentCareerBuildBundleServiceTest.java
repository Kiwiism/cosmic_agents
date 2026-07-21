package server.agents.progression;

import client.Character;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import server.agents.runtime.AgentRuntimeEntry;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentCareerBuildBundleServiceTest {
    @TempDir
    Path temp;

    @Test
    void explicitTestAssignmentOverwritesDurableBundleAndSelectsBothProfiles() throws Exception {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(91);
        when(agent.getName()).thenReturn("BuildFixture");
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        FileAgentCareerAssignmentStore store = new FileAgentCareerAssignmentStore(temp);

        AgentCareerBuildBundle bundle = AgentCareerBuildBundleService.assignForTest(
                entry, agent, "warrior-standard-v1", 500L,
                AgentCareerBuildBundleRepository.defaultRepository(), store);

        assertEquals("warrior-standard-v1", store.load(91).orElseThrow().bundleId());
        assertEquals(bundle, entry.capabilityStates().require(AgentCareerProgressionState.STATE_KEY).bundle());
        assertEquals(bundle.apProfileId(), entry.apBuildProfileState().profile().profileId());
        assertEquals(bundle.spProfileId(), entry.spBuildProfileState().profile().profileId());
        assertEquals(AgentProgressionProfileRepository.defaultRepository().deterministicFor(91).profileId(),
                entry.capabilityStates().require(AgentProgressionProfileState.STATE_KEY)
                        .profile().profileId());
    }
}
