package server.bots;

import client.BuffStat;
import client.Character;
import client.Job;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BotBuffManagerTest {
    @Test
    void shouldOnlyUseMatkForMagesAndWatkForNonMages() {
        Character mage = mock(Character.class);
        when(mage.getJobStyle()).thenReturn(Job.MAGICIAN);

        Character nonMage = mock(Character.class);
        when(nonMage.getJobStyle()).thenReturn(Job.WARRIOR);

        assertTrue(BotBuffManager.isRelevantBuffStat(mage, BuffStat.MATK));
        assertFalse(BotBuffManager.isRelevantBuffStat(mage, BuffStat.WATK));

        assertFalse(BotBuffManager.isRelevantBuffStat(nonMage, BuffStat.MATK));
        assertTrue(BotBuffManager.isRelevantBuffStat(nonMage, BuffStat.WATK));

        assertTrue(BotBuffManager.isRelevantBuffStat(mage, BuffStat.ACC));
        assertTrue(BotBuffManager.isRelevantBuffStat(nonMage, BuffStat.ACC));
    }
}
