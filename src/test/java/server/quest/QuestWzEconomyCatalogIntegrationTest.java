package server.quest;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestWzEconomyCatalogIntegrationTest {
    @Test
    void localWzContainsExactCursedDollDemandWaves() {
        Assumptions.assumeTrue(System.getProperty("wz-path") != null,
                "local WZ integration evidence is optional in ordinary unit-test environments");
        List<Quest> matches = Quest.allQuests().stream()
                .filter(quest -> quest.getCompleteItemRequirements().containsKey(4000031))
                .toList();

        assertEquals(List.of(100, 200, 400, 600, 1000), matches.stream()
                .map(quest -> quest.getCompleteItemRequirements().get(4000031)).toList());
    }
}
