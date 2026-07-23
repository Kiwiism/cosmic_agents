package server.agents.capabilities.townlife;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTownLifeProfileRepositoryTest {
    @Test
    void loadsIndependentlyTunableLithHarborProfile() {
        AgentTownLifeProfile profile = AgentTownLifeProfileRepository.defaultRepository()
                .require(LithHarborTownLifeCatalog.LITH_HARBOR_MAP_ID);

        assertEquals("lith-harbor", profile.profileId());
        assertEquals(14, profile.restSpots().size());
        assertEquals(8, profile.npcSpots().size());
        assertEquals(3, profile.shopMapIds().size());
        assertEquals(9, profile.venues().size());
        assertEquals(4, profile.trafficZones().size());
        assertEquals(0, profile.mapSeatId(profile.restSpots().get(2).point()));
        assertEquals("lith-harbor", profile.extensions().arrival());
        assertInstanceOf(LithHarborTownLifeArrivalExtension.class,
                AgentTownLifeArrivalExtensionRepository.forTown(profile.mapId()));
    }

    @Test
    void trafficZonesKeepTransitPointsOutOfAmbientOccupancy() {
        AgentTownLifeProfile profile = AgentTownLifeProfileRepository.defaultRepository()
                .require(LithHarborTownLifeCatalog.LITH_HARBOR_MAP_ID);

        assertTrue(profile.trafficZones().stream()
                .anyMatch(zone -> zone.type() == AgentTownLifeProfile.TrafficZoneType.LADDER));
        assertTrue(!profile.allowsOccupancy(new java.awt.Point(1470, 500)));
        assertTrue(profile.allowsOccupancy(new java.awt.Point(1575, 641)));
    }

    @Test
    void lithVenuesExposeSemanticAffordancesAndBoundedCapacity() {
        AgentTownLifeProfile profile = AgentTownLifeProfileRepository.defaultRepository()
                .require(LithHarborTownLifeCatalog.LITH_HARBOR_MAP_ID);
        AgentTownLifeProfile.Venue benches = profile.venue("central-benches").orElseThrow();

        assertEquals(9, benches.capacity());
        assertTrue(benches.supports(AgentTownLifeState.Activity.REST));
        assertTrue(benches.supports(AgentTownLifeState.Activity.SOCIAL));
        assertEquals(9, benches.spots().size());
        assertEquals(3, profile.venuesFor(AgentTownLifeState.Activity.SHOP_VISIT).size());
    }

    @Test
    void fallbackAssignmentsPopulateEveryVerticalDistrict() {
        AgentTownLifeProfile profile = AgentTownLifeProfileRepository.defaultRepository()
                .require(LithHarborTownLifeCatalog.LITH_HARBOR_MAP_ID);
        EnumSet<AgentTownLifeState.District> assigned = EnumSet.noneOf(
                AgentTownLifeState.District.class);

        for (int characterId = 1; characterId <= 100; characterId++) {
            assigned.add(AgentTownLifeDistributionPolicy.homeDistrict(
                    characterId, profile.distribution()));
        }

        assertTrue(assigned.contains(AgentTownLifeState.District.UPPER));
        assertTrue(assigned.contains(AgentTownLifeState.District.MIDDLE));
        assertTrue(assigned.contains(AgentTownLifeState.District.LOWER));
        assertTrue(assigned.contains(AgentTownLifeState.District.ANY));
    }

    @Test
    void loadTestUsesOnlyTheUpperLeftShipStagingPortal() {
        AgentTownLifeProfile profile = AgentTownLifeProfileRepository.defaultRepository()
                .require(LithHarborTownLifeCatalog.LITH_HARBOR_MAP_ID);
        long upper = java.util.stream.IntStream.rangeClosed(1, 100)
                .filter(i -> profile.arrivalPortal(i).equals("maple00"))
                .count();

        assertEquals(100, upper);
    }

    @Test
    void loadsValidatedWzBackedHenesysPilot() {
        AgentTownLifeProfile profile = AgentTownLifeProfileRepository.defaultRepository()
                .require(100000000);
        AgentTownLifeProfileValidator.Validation validation =
                AgentTownLifeProfileValidator.validate(profile);

        assertEquals("henesys", profile.profileId());
        assertEquals(29, profile.restSpots().size());
        assertEquals(7, profile.venues().size());
        assertEquals(100000100, profile.venue("henesys-market")
                .orElseThrow().destinationMapId());
        assertTrue(validation.valid(), () -> validation.errors().toString());
    }
}
