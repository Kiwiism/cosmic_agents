package server.agents.capabilities.townlife;

import client.Character;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTownLifeControllerRuntimeTest {
    @AfterEach
    void resetController() {
        AgentTownLifeControllerRuntime.clearExternalController();
    }

    @Test
    void dialogueOnlyModeKeepsDeterministicTownLifeAuthority() {
        Character agent = agent(81);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentTownLifeState state = startedState(entry, agent.getId());
        AgentTownLifeControllerRuntime.setSupportLevel(entry, AgentTownLifeSupportLevel.DIALOGUE_ONLY);
        boolean[] invoked = {false};
        AgentTownLifeControllerRuntime.installExternalController(context -> {
            invoked[0] = true;
            return Optional.empty();
        });

        AgentTownLifeDecision decision = AgentTownLifeControllerRuntime.choose(entry, agent, state, 5_000L);

        assertFalse(invoked[0]);
        assertEquals("default-policy", decision.source());
    }

    @Test
    void decisionModeExposesImmutableContextAndAcceptsAValidatedVenueDirective() {
        Character agent = agent(82);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentTownLifeState state = startedState(entry, agent.getId());
        AgentTownLifeControllerRuntime.setSupportLevel(
                entry, AgentTownLifeSupportLevel.DIALOGUE_AND_DECISION);
        AtomicReference<AgentTownLifeDecisionContext> seen = new AtomicReference<>();
        AgentTownLifeControllerRuntime.installExternalController(context -> {
            seen.set(context);
            return Optional.of(new AgentTownLifeDirective(
                    AgentTownLifeState.Activity.REST, "central-benches", 0, null,
                    context.nowMs() + 5_000L, "test-controller", "decision-1"));
        });

        AgentTownLifeDecision decision = AgentTownLifeControllerRuntime.choose(entry, agent, state, 5_000L);

        assertEquals(AgentTownLifeState.Activity.REST, decision.activity());
        assertEquals("central-benches", decision.venueId());
        assertEquals("external:test-controller", decision.source());
        assertEquals("decision-1", decision.correlationId());
        assertEquals(9, seen.get().venues().size());
        assertEquals(AgentTownLifeDecisionContext.PersonalityView.neutral(), seen.get().personality());
        assertTrue(seen.get().venues().stream().noneMatch(venue -> venue.currentOccupancy() < 0));
    }

    @Test
    void invalidExternalVenueFallsBackToDefaultPolicy() {
        Character agent = agent(83);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentTownLifeState state = startedState(entry, agent.getId());
        AgentTownLifeControllerRuntime.setSupportLevel(
                entry, AgentTownLifeSupportLevel.DIALOGUE_AND_DECISION);
        AgentTownLifeControllerRuntime.installExternalController(context -> Optional.of(
                new AgentTownLifeDirective(AgentTownLifeState.Activity.REST, "missing", 0, null,
                        context.nowMs() + 1_000L, "test", "invalid")));

        AgentTownLifeDecision decision = AgentTownLifeControllerRuntime.choose(entry, agent, state, 5_000L);

        assertEquals("default-policy", decision.source());
    }

    private static Character agent(int id) {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(id);
        when(agent.getMapId()).thenReturn(LithHarborTownLifeCatalog.LITH_HARBOR_MAP_ID);
        return agent;
    }

    private static AgentTownLifeState startedState(AgentRuntimeEntry entry, int id) {
        AgentTownLifeState state = entry.capabilityStates().require(AgentTownLifeState.STATE_KEY);
        state.start(0L, id, LithHarborTownLifeCatalog.LITH_HARBOR_MAP_ID);
        state.markInitialPlacementComplete();
        state.assignRole(AgentTownLifeState.Role.WANDERER, 100_000L);
        state.transition(AgentTownLifeState.Stage.CHOOSE_ACTIVITY, 0L);
        return state;
    }
}
