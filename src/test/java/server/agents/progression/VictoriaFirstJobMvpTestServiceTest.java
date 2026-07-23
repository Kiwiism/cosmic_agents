package server.agents.progression;

import org.junit.jupiter.api.Test;
import server.maps.MapleMap;
import server.maps.Portal;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VictoriaFirstJobMvpTestServiceTest {
    @Test
    void resolvesFiveSimpleJobAliasesAndOptionalBuildVariants() {
        assertEquals("warrior-standard-v1", bundle("warrior"));
        assertEquals("bowman-standard-v1", bundle("archer"));
        assertEquals("magician-standard-v1", bundle("mage"));
        assertEquals("thief-claw-standard-v1", bundle("thief"));
        assertEquals("pirate-gun-standard-v1", bundle("pirate"));
        assertEquals("thief-dagger-standard-v1", bundle("thief-dagger"));
        assertEquals("pirate-knuckle-standard-v1", bundle("pirate-knuckle"));
    }

    @Test
    void rejectsUnknownCareerInsteadOfSelectingSilently() {
        assertThrows(IllegalArgumentException.class,
                () -> VictoriaFirstJobMvpTestService.resolveBundle("beginner"));
    }

    @Test
    void resolvesAllThreeDocumentedStartVariantsAndRejectsUnknownOnes() {
        assertEquals("lv10", VictoriaFirstJobMvpTestService.resolveStartVariant("level10").variantId());
        assertEquals("lv9-olaf", VictoriaFirstJobMvpTestService.resolveStartVariant("olaf").variantId());
        assertEquals("lv9-grind", VictoriaFirstJobMvpTestService.resolveStartVariant("grind").variantId());
        assertThrows(IllegalArgumentException.class,
                () -> VictoriaFirstJobMvpTestService.resolveStartVariant("lv8"));
    }

    @Test
    void usesLithHarborShipArrivalInsteadOfOrdinaryTownSpawn() {
        MapleMap map = mock(MapleMap.class);
        Portal shipArrival = mock(Portal.class);
        Portal townSpawn = mock(Portal.class);
        when(shipArrival.getPosition()).thenReturn(new Point(4_188, -224));
        when(townSpawn.getPosition()).thenReturn(new Point(-495, -110));
        when(map.getPortal("in03")).thenReturn(shipArrival);
        when(map.getPortal(0)).thenReturn(townSpawn);

        assertEquals(new Point(4_188, -224),
                VictoriaFirstJobMvpTestService.lithHarborArrivalPosition(map));
    }

    @Test
    void distributesStartsAcrossNavigableShipPlatforms() {
        MapleMap map = mock(MapleMap.class);
        when(map.getPointBelow(any(Point.class))).thenAnswer(invocation -> {
            Point point = invocation.getArgument(0);
            return new Point(point.x, point.y + 1);
        });

        Point startOfMainDeck = VictoriaFirstJobMvpTestService.lithHarborArrivalPosition(map, 0);
        Point anotherShipStart = VictoriaFirstJobMvpTestService.lithHarborArrivalPosition(map, 801);
        Point thirdShipStart = VictoriaFirstJobMvpTestService.lithHarborArrivalPosition(map, 802);
        Point fourthShipStart = VictoriaFirstJobMvpTestService.lithHarborArrivalPosition(map, 803);

        assertEquals(new Point(2_800, -223), startOfMainDeck);
        assertNotEquals(startOfMainDeck, anotherShipStart);
        assertNotEquals(anotherShipStart, thirdShipStart);
        assertNotEquals(thirdShipStart, fourthShipStart);
        assertTrue(thirdShipStart.x >= 2_800);
        assertTrue(fourthShipStart.x >= 2_800);
    }

    @Test
    void neverFallsBackToOrdinaryPortalZero() {
        MapleMap map = mock(MapleMap.class);
        Portal townSpawn = mock(Portal.class);
        when(townSpawn.getPosition()).thenReturn(new Point(-495, -110));
        when(map.getPortal(0)).thenReturn(townSpawn);

        assertEquals(new Point(4_188, -224),
                VictoriaFirstJobMvpTestService.lithHarborArrivalPosition(map, 0));
    }

    private static String bundle(String alias) {
        return VictoriaFirstJobMvpTestService.resolveBundle(alias).bundleId();
    }
}
