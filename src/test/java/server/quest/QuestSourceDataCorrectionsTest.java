package server.quest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestSourceDataCorrectionsTest {
    @Test
    void correctsOnlyPirateFourthTrainingGreenMushroomRequirement() {
        assertEquals(1110100, QuestSourceDataCorrections.mobRequirementId(2196, 9101000));
        assertEquals(9101000, QuestSourceDataCorrections.mobRequirementId(2195, 9101000));
        assertEquals(1110100, QuestSourceDataCorrections.mobRequirementId(2196, 1110100));
    }

    @Test
    void realQuestRelevantMobsUseTheCorrectedOrdinaryGreenMushroom() {
        assertTrue(Quest.getInstance(2196).getRelevantMobs().contains(1110100));
        assertFalse(Quest.getInstance(2196).getRelevantMobs().contains(9101000));
    }
}
