package server.agents.capabilities.behavior;

import client.Character;
import constants.id.ItemId;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentEmote;
import server.agents.capabilities.movement.AgentChairService;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.personality.AgentPersonalityAssignment;
import server.agents.personality.AgentPersonalityProfile;
import server.agents.personality.AgentPersonalityProfileRepository;
import server.agents.personality.AgentPersonalityState;
import server.agents.runtime.AgentForegroundPauseRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentPioRelaxerInterludeRuntimeTest {
    @Test
    void restSitsAtReservedSpotThenResumesPausedPlan() {
        Fixture fixture = fixture(AgentPioRelaxerInterludeState.Mode.REST, 15_000L);

        assertTrue(AgentPioRelaxerInterludeRuntime.tick(
                fixture.entry, fixture.agent, 2_000L, true, fixture.gateway));
        assertEquals(AgentPioRelaxerInterludeState.Stage.ACTIVE, fixture.state.stage());
        verify(fixture.gateway).sitChair(fixture.agent, ItemId.RELAXER);

        assertFalse(AgentPioRelaxerInterludeRuntime.tick(
                fixture.entry, fixture.agent, 17_000L, true, fixture.gateway));
        assertFalse(fixture.state.active());
        assertFalse(AgentForegroundPauseRuntime.paused(fixture.entry));
    }

    @Test
    void playfulSequenceShowsF2AndAlternatesBackToStanding() {
        Fixture fixture = fixture(AgentPioRelaxerInterludeState.Mode.PLAYFUL, 10_000L);

        assertTrue(AgentPioRelaxerInterludeRuntime.tick(
                fixture.entry, fixture.agent, 2_000L, true, fixture.gateway));
        verify(fixture.gateway).sitChair(fixture.agent, ItemId.RELAXER);
        verify(fixture.agent).changeFaceExpression(AgentEmote.HAPPY.getValue());

        when(fixture.agent.getChair()).thenReturn(ItemId.RELAXER);
        try (MockedStatic<AgentChairService> chair = mockStatic(AgentChairService.class)) {
            assertTrue(AgentPioRelaxerInterludeRuntime.tick(
                    fixture.entry, fixture.agent, fixture.state.nextToggleAtMs(), true, fixture.gateway));
            chair.verify(() -> AgentChairService.stand(fixture.entry, fixture.agent));
        }
    }

    private static Fixture fixture(AgentPioRelaxerInterludeState.Mode mode, long durationMs) {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(77);
        when(agent.getMapId()).thenReturn(1_000_000);
        when(agent.getChair()).thenReturn(-1);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        when(gateway.alive(agent)).thenReturn(true);
        when(gateway.itemCount(agent, ItemId.RELAXER)).thenReturn(1);
        when(gateway.grounded(agent)).thenReturn(true);
        when(gateway.position(agent)).thenReturn(new Point(300, 274));
        when(gateway.sitChair(agent, ItemId.RELAXER)).thenReturn(true);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentPersonalityProfile profile = AgentPersonalityProfileRepository.defaultRepository()
                .find("restless-v1").orElseThrow();
        entry.capabilityStates().require(AgentPersonalityState.STATE_KEY).assign(
                new AgentPersonalityAssignment(
                        1, 77, "RuntimeFixture", profile.profileId(), profile.profileVersion(), 99L, 0L),
                profile, true);
        AgentPioRelaxerInterludeState state = entry.capabilityStates()
                .require(AgentPioRelaxerInterludeState.STATE_KEY);
        state.request(mode, durationMs, 1_000L);
        state.assignSpot(new Point(300, 274));
        AgentForegroundPauseRuntime.pause(
                entry, AgentPioRelaxerInterludeRuntime.PAUSE_REASON, 1_000L);
        return new Fixture(entry, agent, gateway, state);
    }

    private record Fixture(AgentRuntimeEntry entry,
                           Character agent,
                           PrimitiveCapabilityGateway gateway,
                           AgentPioRelaxerInterludeState state) {
    }
}
