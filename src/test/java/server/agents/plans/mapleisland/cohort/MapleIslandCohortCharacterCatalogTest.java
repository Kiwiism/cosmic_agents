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

    @Test
    void earlyCohortVariesEveryVisibleAppearanceDimension() {
        Set<Integer> skins = new HashSet<>();
        Set<Integer> faces = new HashSet<>();
        Set<Integer> hairs = new HashSet<>();
        Set<Integer> tops = new HashSet<>();
        Set<Integer> bottoms = new HashSet<>();
        Set<Integer> shoes = new HashSet<>();
        Set<Integer> weapons = new HashSet<>();
        for (int ordinal = 0; ordinal < 100; ordinal++) {
            MapleIslandCohortCharacterTemplate template =
                    MapleIslandCohortCharacterCatalog.template(ordinal);
            skins.add(template.skin());
            faces.add(template.face());
            hairs.add(template.hair());
            tops.add(template.top());
            bottoms.add(template.bottom());
            shoes.add(template.shoes());
            weapons.add(template.weapon());
        }
        assertEquals(4, skins.size());
        assertEquals(6, faces.size());
        assertEquals(24, hairs.size());
        assertEquals(7, tops.size());
        assertEquals(4, bottoms.size());
        assertEquals(4, shoes.size());
        assertEquals(3, weapons.size());
    }

    private static String signature(MapleIslandCohortCharacterTemplate template) {
        return template.gender() + ":" + template.skin() + ":" + template.face() + ":"
                + template.hair() + ":" + template.top() + ":" + template.bottom() + ":"
                + template.shoes() + ":" + template.weapon();
    }
}
