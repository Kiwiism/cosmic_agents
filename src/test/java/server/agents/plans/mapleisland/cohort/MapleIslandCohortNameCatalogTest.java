package server.agents.plans.mapleisland.cohort;

import client.Character;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapleIslandCohortNameCatalogTest {
    @Test
    void allFiveThousandNamesAreUniqueAndAcceptedByCharacterPolicy() {
        Set<String> normalized = new HashSet<>();
        java.util.List<String> rejected = new java.util.ArrayList<>();

        for (String candidate : MapleIslandCohortNameCatalog.candidates()) {
            if (!Character.isValidCharacterNameSyntax(candidate)) {
                rejected.add(candidate);
            }
            assertTrue(normalized.add(candidate.toLowerCase(Locale.ROOT)),
                    () -> "Duplicate generated name: " + candidate);
        }

        assertTrue(rejected.isEmpty(), () -> "Rejected generated names: " + rejected);
        assertEquals(MapleIslandCohortNameCatalog.CANDIDATE_COUNT, normalized.size());
    }

    @Test
    void accountCandidatesFitTheLegacyAccountColumnAndAreUnique() {
        Set<String> names = new HashSet<>();
        for (int ordinal = 1; ordinal <= MapleIslandCohortNameCatalog.CANDIDATE_COUNT; ordinal++) {
            String candidate = MapleIslandCohortNameCatalog.accountCandidate(ordinal);
            assertTrue(candidate.length() <= 13, candidate);
            assertTrue(names.add(candidate.toLowerCase(Locale.ROOT)), candidate);
        }
        assertEquals(MapleIslandCohortNameCatalog.CANDIDATE_COUNT, names.size());
    }
}
