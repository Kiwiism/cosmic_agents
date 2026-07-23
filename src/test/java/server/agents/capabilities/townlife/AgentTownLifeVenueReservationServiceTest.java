package server.agents.capabilities.townlife;

import client.Character;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.maps.reservation.CharacterSpaceReservationRuntime;

import java.awt.Point;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTownLifeVenueReservationServiceTest {
    @AfterEach
    void clearReservations() {
        CharacterSpaceReservationRuntime.clear();
    }

    @Test
    void reservesDistinctAuthoredSlotsForAGroupAndReleasesThemTogether() {
        Character first = agent(101);
        Character second = agent(102);
        Character third = agent(103);
        AgentTownLifeProfile.Venue venue = AgentTownLifeProfileRepository.defaultRepository()
                .require(LithHarborTownLifeCatalog.LITH_HARBOR_MAP_ID)
                .venue("central-benches").orElseThrow();

        var reservations = AgentTownLifeVenueReservationService.reserveGroup(
                List.of(first, second, third), venue, 7);

        assertEquals(3, reservations.size());
        assertEquals(3, reservations.values().stream().distinct().count());
        AgentTownLifeVenueReservationService.releaseGroup(List.of(101, 102, 103));
        assertEquals(0, CharacterSpaceReservationRuntime.occupiedCount());
    }

    @Test
    void refusesAGroupLargerThanTheVenue() {
        AgentTownLifeProfile.Venue venue = new AgentTownLifeProfile.Venue(
                "tiny", "Tiny", AgentTownLifeState.District.LOWER,
                AgentTownLifeState.PlatformKind.MINI, 2,
                List.of(AgentTownLifeProfile.Affordance.SOCIAL),
                List.of(new AgentTownLifeProfile.VenueSpot(10, 20, -1),
                        new AgentTownLifeProfile.VenueSpot(80, 20, -1)), 0);

        assertTrue(AgentTownLifeVenueReservationService.reserveGroup(
                List.of(agent(1), agent(2), agent(3)), venue, 1).isEmpty());
    }

    private static Character agent(int id) {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(id);
        when(agent.getMapId()).thenReturn(LithHarborTownLifeCatalog.LITH_HARBOR_MAP_ID);
        when(agent.getWorld()).thenReturn(0);
        when(agent.getPosition()).thenReturn(new Point(0, 0));
        return agent;
    }
}
