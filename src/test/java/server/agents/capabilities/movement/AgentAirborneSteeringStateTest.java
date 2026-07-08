package server.agents.capabilities.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentAirborneSteeringStateTest {
    @Test
    void defaultsMatchLegacyAgentRuntimeEntryFields() {
        AgentAirborneSteeringState state = new AgentAirborneSteeringState();

        assertEquals(0, state.velocityX());
        assertEquals(0.0, state.steeringVelocityX());
        assertFalse(state.fixedAirArc());
    }

    @Test
    void storesCommittedAndCorrectiveAirVelocity() {
        AgentAirborneSteeringState state = new AgentAirborneSteeringState();

        state.setVelocityX(-8);
        state.setSteeringVelocityX(1.25);

        assertEquals(-8, state.velocityX());
        assertEquals(1.25, state.steeringVelocityX());
    }

    @Test
    void clampsSteeringCorrection() {
        AgentAirborneSteeringState state = new AgentAirborneSteeringState();

        state.addClampedSteeringVelocityX(2.0, 1.5);
        assertEquals(1.5, state.steeringVelocityX());

        state.addClampedSteeringVelocityX(-5.0, 1.5);
        assertEquals(-1.5, state.steeringVelocityX());
    }

    @Test
    void storesFixedAirArcFlag() {
        AgentAirborneSteeringState state = new AgentAirborneSteeringState();

        state.setFixedAirArc(true);
        assertTrue(state.fixedAirArc());

        state.setFixedAirArc(false);
        assertFalse(state.fixedAirArc());
    }
}
