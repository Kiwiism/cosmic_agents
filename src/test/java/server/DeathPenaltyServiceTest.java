package server;

import client.Character;
import client.Job;
import constants.game.ExpTable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeathPenaltyServiceTest {
    @Test
    void appliesTheLiveV83NonTownLowLukRule() {
        Character character = mock(Character.class);
        when(character.getJob()).thenReturn(Job.WARRIOR);
        when(character.getLevel()).thenReturn(30);
        when(character.getLuk()).thenReturn(4);
        when(character.getExp()).thenReturn(50_000);

        DeathPenaltyService.Result result = DeathPenaltyService.apply(character,
                new DeathPenaltyService.FieldContext(100000001, false, 0));

        int expected = ExpTable.getExpNeededForLevel(30) / 10;
        assertEquals(expected, result.experienceLost());
        assertEquals(DeathPenaltyService.Reason.EXPERIENCE_LOST, result.reason());
        assertFalse(result.prevented());
        verify(character).loseExp(expected, false, false);
    }

    @Test
    void beginnersNeverLoseExperience() {
        Character character = mock(Character.class);
        when(character.getJob()).thenReturn(Job.BEGINNER);

        DeathPenaltyService.Result result = DeathPenaltyService.apply(character,
                new DeathPenaltyService.FieldContext(100000001, false, 0));

        assertEquals(0, result.experienceLost());
        assertEquals(DeathPenaltyService.Reason.BEGINNER, result.reason());
    }
}
