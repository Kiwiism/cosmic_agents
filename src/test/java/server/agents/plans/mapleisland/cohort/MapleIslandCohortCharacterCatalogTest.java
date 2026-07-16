package server.agents.plans.mapleisland.cohort;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapleIslandCohortCharacterCatalogTest {
    @Test
    void enumeratesEveryCombinationExactlyOnceAndBalancesEarlyCohorts() {
        Set<String> combinations = new HashSet<>();
        int male = 0;
        int female = 0;
        for (int ordinal = 0; ordinal < MapleIslandCohortCharacterCatalog.COMBINATION_COUNT; ordinal++) {
            MapleIslandCohortCharacterTemplate template =
                    MapleIslandCohortCharacterCatalog.template(ordinal);
            assertEquals(ordinal, template.ordinal());
            assertTrue(combinations.add(signature(template)),
                    () -> "Duplicate character template " + template);
            if (ordinal < 100) {
                if (template.gender() == 0) {
                    male++;
                } else {
                    female++;
                }
            }
        }
        assertEquals(MapleIslandCohortCharacterCatalog.COMBINATION_COUNT, combinations.size());
        assertEquals(50, male);
        assertEquals(50, female);
    }

    private static String signature(MapleIslandCohortCharacterTemplate template) {
        return template.gender() + ":" + template.skin() + ":" + template.face() + ":"
                + template.hair() + ":" + template.top() + ":" + template.bottom() + ":"
                + template.shoes() + ":" + template.weapon();
    }
}
