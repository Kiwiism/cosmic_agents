package server.agents.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentTaxiDialogueSequenceTest {
    @Test
    void lithHarborPhilSelectsTravelBeforeSelectingTheDestination() {
        assertArrayEquals(new int[]{0, 1, 3, 0},
                AgentTaxiDialogueSequence.lithHarborPhil(3));
    }

    @Test
    void regularTownCabSelectsTheDestinationAtItsFirstMenu() {
        assertArrayEquals(new int[]{0, 3, 0},
                AgentTaxiDialogueSequence.regularTownCab(3));
    }

    @Test
    void rejectsMissingDestinationSelections() {
        assertThrows(IllegalArgumentException.class,
                () -> AgentTaxiDialogueSequence.lithHarborPhil(-1));
        assertThrows(IllegalArgumentException.class,
                () -> AgentTaxiDialogueSequence.regularTownCab(-1));
    }
}
