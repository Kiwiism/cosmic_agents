package server.agents.capabilities.movement;

import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPartyGatherServiceTest {
    @Test
    void gatherPhrasesCallThePartyTogether() {
        for (String message : List.of("gather up", "Gather here!", "everyone gather up", "group up for buffs",
                "regroup", "huddle up guys", "assemble", "ok everyone, come here", "everybody get over here",
                "come here everyone", "all of you come here pls", "party on me", "rally on me")) {
            assertTrue(AgentPartyGatherService.matchesAssemble(message), message);
        }
    }

    @Test
    void aBareComeHereStaysAFollowCommand() {
        for (String message : List.of("come here", "come here pls", "get over here", "follow me", "group",
                "gather the herbs over there", "i need to gather some stuff", "on me")) {
            assertFalse(AgentPartyGatherService.matchesAssemble(message), message);
        }
    }

    @Test
    void spotsAlternateOutwardSoTheOwnerKeepsTheMiddle() {
        assertEquals(List.of(-60, 60, -120, 120, -180), AgentPartyGatherService.spotOffsets(5, 60));
        assertEquals(List.of(), AgentPartyGatherService.spotOffsets(0, 60));
    }

    @Test
    void aSpotStaysOnTheOwnersGroundAndIsPulledInOverAnEdge() {
        Point owner = new Point(1000, 200);
        // Flat ground everywhere: the spot is where it was asked for.
        assertEquals(new Point(1120, 200), AgentPartyGatherService.groundedSpot(owner, 120, 60, x -> 200));
        // The platform ends at x=1090: 1120 would drop to the floor below, so the spot comes inward.
        assertEquals(new Point(1090, 200),
                AgentPartyGatherService.groundedSpot(owner, 120, 60, x -> x <= 1090 ? 200 : 500));
        // No ground beside the owner at all: stand with the owner rather than fall off.
        assertEquals(new Point(owner),
                AgentPartyGatherService.groundedSpot(owner, -60, 60, x -> null));
    }

    @Test
    void severalAgentsReadingOneMessageGatherThePartyOnce() {
        int owner = 7_700_001;
        assertTrue(AgentPartyGatherService.claimAssemble(owner, 10_000, 3_000));
        assertFalse(AgentPartyGatherService.claimAssemble(owner, 10_400, 3_000));
        assertTrue(AgentPartyGatherService.claimAssemble(owner + 1, 10_400, 3_000));
        assertTrue(AgentPartyGatherService.claimAssemble(owner, 13_000, 3_000));
    }
}
