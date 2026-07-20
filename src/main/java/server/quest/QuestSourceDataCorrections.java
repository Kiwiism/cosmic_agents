package server.quest;

/** Narrow, documented corrections for internally contradictory quest WZ records. */
public final class QuestSourceDataCorrections {
    private static final int PIRATE_FOURTH_TRAINING_QUEST_ID = 2_196;
    private static final int INCORRECT_GREEN_MUSHROOM_VARIANT_ID = 9_101_000;
    private static final int GREEN_MUSHROOM_ID = 1_110_100;

    private QuestSourceDataCorrections() {
    }

    public static int mobRequirementId(int questId, int sourceMobId) {
        // QuestInfo/Say for 2196 repeatedly specify 10 ordinary Green Mushrooms (1110100).
        // Only Check.img contains 9101000, which has no normal Victoria spawn and contradicts the quest text.
        if (questId == PIRATE_FOURTH_TRAINING_QUEST_ID
                && sourceMobId == INCORRECT_GREEN_MUSHROOM_VARIANT_ID) {
            return GREEN_MUSHROOM_ID;
        }
        return sourceMobId;
    }
}
