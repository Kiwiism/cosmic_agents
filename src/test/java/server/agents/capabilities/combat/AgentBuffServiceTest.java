package server.agents.capabilities.combat;

import client.BuffStat;
import client.Character;
import client.Job;
import org.junit.jupiter.api.Test;
import server.StatEffect;
import tools.Pair;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentBuffServiceTest {
    @Test
    void shouldOnlyUseMatkForMagesAndWatkForNonMages() {
        Character mage = mock(Character.class);
        when(mage.getJobStyle()).thenReturn(Job.MAGICIAN);

        Character nonMage = mock(Character.class);
        when(nonMage.getJobStyle()).thenReturn(Job.WARRIOR);

        assertTrue(AgentBuffService.isRelevantBuffStat(mage, BuffStat.MATK));
        assertFalse(AgentBuffService.isRelevantBuffStat(mage, BuffStat.WATK));

        assertFalse(AgentBuffService.isRelevantBuffStat(nonMage, BuffStat.MATK));
        assertTrue(AgentBuffService.isRelevantBuffStat(nonMage, BuffStat.WATK));

        assertTrue(AgentBuffService.isRelevantBuffStat(mage, BuffStat.ACC));
        assertTrue(AgentBuffService.isRelevantBuffStat(nonMage, BuffStat.ACC));
    }

    @Test
    void cheapCapSkipsAtkBuffsStrongerThanTwelve() {
        Character warrior = mock(Character.class);
        when(warrior.getJobStyle()).thenReturn(Job.WARRIOR);

        Character mage = mock(Character.class);
        when(mage.getJobStyle()).thenReturn(Job.MAGICIAN);

        // +12 WATK is the cap: kept.
        assertFalse(AgentBuffService.exceedsCheapAtkCap(warrior, fxWith(BuffStat.WATK, 12)));
        // +13 WATK exceeds the cap: skipped in cheap mode.
        assertTrue(AgentBuffService.exceedsCheapAtkCap(warrior, fxWith(BuffStat.WATK, 13)));
        // +20 MATK for a mage exceeds the cap.
        assertTrue(AgentBuffService.exceedsCheapAtkCap(mage, fxWith(BuffStat.MATK, 20)));

        // Irrelevant stat for the job is ignored: a warrior never cares about MATK.
        assertFalse(AgentBuffService.exceedsCheapAtkCap(warrior, fxWith(BuffStat.MATK, 99)));
        // Non-atk stats are never capped.
        assertFalse(AgentBuffService.exceedsCheapAtkCap(warrior, fxWith(BuffStat.ACC, 99)));
    }

    private static StatEffect fxWith(BuffStat stat, int value) {
        StatEffect fx = mock(StatEffect.class);
        when(fx.getStatups()).thenReturn(List.of(new Pair<>(stat, value)));
        return fx;
    }
}
