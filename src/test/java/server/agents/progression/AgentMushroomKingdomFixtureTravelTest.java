package server.agents.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentMushroomKingdomFixtureTravelTest {
    @Test
    void secondJobBranchesReceiveTheirLeaderTownReturnScroll() {
        assertEquals(2030003, scroll("spearman"));
        assertEquals(2030002, scroll("fp-wizard"));
        assertEquals(2030001, scroll("crossbowman"));
        assertEquals(2030005, scroll("assassin"));
        assertEquals(2030000, scroll("brawler"));
    }

    private static int scroll(String branchId) {
        return AgentMushroomKingdomFixtureService.secondJobTownScrollItemId(
                AgentSecondJobCatalog.require(branchId));
    }
}
