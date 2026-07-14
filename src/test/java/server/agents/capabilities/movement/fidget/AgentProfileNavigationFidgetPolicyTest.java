package server.agents.capabilities.movement.fidget;

import org.junit.jupiter.api.Test;
import server.agents.profiles.AgentBehaviorProfile;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentProfileNavigationFidgetPolicyTest {
    @Test
    void navigationUsesOnlyExistingStationaryFidgetsUntilMovingModesAreCapabilitySafe() {
        AgentBehaviorProfile.Movement movement = new AgentBehaviorProfile.Movement(
                true,
                EnumSet.allOf(AgentFidgetMode.class).stream()
                        .filter(mode -> mode != AgentFidgetMode.NONE)
                        .collect(java.util.stream.Collectors.toSet()),
                new AgentBehaviorProfile.DelayRange(1000, 2000),
                new AgentBehaviorProfile.DelayRange(2000, 3000));

        assertEquals(
                java.util.Set.of(AgentFidgetMode.WAIT, AgentFidgetMode.PRONE, AgentFidgetMode.SPAM_PRONE),
                java.util.Set.copyOf(AgentProfileNavigationFidgetPolicy.safeModes(movement)));
    }
}
