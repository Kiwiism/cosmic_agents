package server.agents.capabilities.movement;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import static org.junit.jupiter.api.Assertions.assertFalse;

class AgentMovementProfileServiceTest {
    @Test
    void refreshMovementProfileReturnsFalseWhenProfileIsUnchanged() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentMovementStateRuntime.setMovementProfile(entry, AgentMovementProfile.base());

        assertFalse(AgentMovementProfileService.refreshMovementProfile(entry));
    }
}
