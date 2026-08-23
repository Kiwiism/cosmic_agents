package server.agents.progression;

import org.junit.jupiter.api.Test;
import server.agents.plans.mapleisland.cohort.MapleIslandCohortCharacterCatalog;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentMushroomKingdomCohortServiceTest {
    @Test
    void rosterCoversEveryExplorerSecondJobExactlyOnce() {
        var roster = AgentMushroomKingdomCohortService.roster();
        assertEquals(12, roster.size());
        assertEquals(AgentSecondJobCatalog.all().keySet(),
                roster.stream().map(AgentMushroomKingdomCohortService.CohortMember::branchId)
                        .collect(java.util.stream.Collectors.toSet()));
        assertEquals(12, roster.stream().map(AgentMushroomKingdomCohortService.CohortMember::name)
                .collect(java.util.stream.Collectors.toSet()).size());
        assertTrue(roster.stream().allMatch(member -> member.name().length() <= 12));
    }

    @Test
    void alternatingAppearanceOrdinalsProvideAnEqualGenderSplit() {
        Set<Integer> genders = new HashSet<>();
        int male = 0;
        int female = 0;
        for (int ordinal = 0; ordinal < AgentMushroomKingdomCohortService.roster().size(); ordinal++) {
            int gender = MapleIslandCohortCharacterCatalog.template(ordinal).gender();
            genders.add(gender);
            if (gender == 0) male++;
            if (gender == 1) female++;
        }
        assertEquals(Set.of(0, 1), genders);
        assertEquals(6, male);
        assertEquals(6, female);
    }
}
