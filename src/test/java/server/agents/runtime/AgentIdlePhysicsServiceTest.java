package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotModeStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentIdlePhysicsServiceTest {
    @Test
    void tickIdleReturnsFalseWithoutPhysicsWhenAgentIsInActiveMode() {
        Character agent = characterWithStance(0);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        AgentBotModeStateRuntime.setFollowing(entry, true);
        Counters counters = new Counters();

        boolean consumed = AgentIdlePhysicsService.tickIdleEntry(entry, agent, hooks(counters, false, 0, 0));

        assertFalse(consumed);
        counters.assertNoSideEffects();
    }

    @Test
    void tickIdleConsumesAndTicksSwimmingWhenInAirOnSwimMap() {
        Character agent = characterWithStance(0);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        AgentBotMovementStateRuntime.setInAir(entry, true);
        Counters counters = new Counters();

        boolean consumed = AgentIdlePhysicsService.tickIdleEntry(entry, agent, hooks(counters, true, 0, 0));

        assertTrue(consumed);
        assertEquals(1, counters.swimming.get());
        assertEquals(0, counters.airborne.get());
    }

    @Test
    void physicsOnlyTicksAirborneWhenInAirOutsideSwimMap() {
        Character agent = characterWithStance(0);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        AgentBotMovementStateRuntime.setInAir(entry, true);
        Counters counters = new Counters();

        AgentIdlePhysicsService.tickPhysicsOnly(entry, agent, hooks(counters, false, 0, 0));

        assertEquals(0, counters.swimming.get());
        assertEquals(1, counters.airborne.get());
    }

    @Test
    void groundedMismatchIdlesAndBroadcasts() {
        Character agent = characterWithStance(1);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        Counters counters = new Counters();

        AgentIdlePhysicsService.tickPhysicsOnly(entry, agent, hooks(counters, false, 2, 1));

        assertEquals(1, counters.idleOnGround.get());
        assertEquals(1, counters.broadcasts.get());
    }

    @Test
    void groundedMatchingStanceHasNoSideEffects() {
        Character agent = characterWithStance(2);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        Counters counters = new Counters();

        AgentIdlePhysicsService.tickPhysicsOnly(entry, agent, hooks(counters, false, 2, 2));

        counters.assertNoSideEffects();
    }

    private static AgentIdlePhysicsService.PhysicsHooks hooks(Counters counters,
                                                              boolean swimMap,
                                                              int expectedIdleStance,
                                                              int currentResolvedStance) {
        return new AgentIdlePhysicsService.PhysicsHooks(
                ignored -> swimMap,
                ignored -> counters.swimming.incrementAndGet(),
                ignored -> counters.airborne.incrementAndGet(),
                ignored -> expectedIdleStance,
                ignored -> currentResolvedStance,
                (entry, agent) -> counters.idleOnGround.incrementAndGet(),
                ignored -> counters.broadcasts.incrementAndGet());
    }

    private static Character characterWithStance(int stance) {
        Character character = mock(Character.class);
        when(character.getStance()).thenReturn(stance);
        return character;
    }

    private static final class Counters {
        private final AtomicInteger swimming = new AtomicInteger();
        private final AtomicInteger airborne = new AtomicInteger();
        private final AtomicInteger idleOnGround = new AtomicInteger();
        private final AtomicInteger broadcasts = new AtomicInteger();

        private void assertNoSideEffects() {
            assertEquals(0, swimming.get());
            assertEquals(0, airborne.get());
            assertEquals(0, idleOnGround.get());
            assertEquals(0, broadcasts.get());
        }
    }
}
