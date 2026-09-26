package server.agents.capabilities.combat;

import client.BuffStat;
import client.Character;
import constants.skills.Cleric;
import constants.skills.Paladin;
import org.junit.jupiter.api.Test;
import server.StatEffect;
import server.agents.runtime.AgentPartyGatherRegistry;
import tools.Pair;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Bless reaches party members inside a 600x400 box around the caster (lt -300,-200 / rb 300,200). */
class AgentPartyBuffCoverageTest {
    private static final int BOT_ID = 1;

    @Test
    void aMemberJustOutsideTheBoxDoesNotCountAsNeedingTheBuff() {
        StatEffect bless = bless();
        // 350 px away: inside the old 400 px radius, outside Bless's box. The Agent used to recast for them forever.
        Character bot = bot(new Point(0, 0), member(2, new Point(350, 0), false));
        assertFalse(AgentCombatSupportPolicy.hasPartyMemberInBoxMissingBuff(bot, bless));
        assertFalse(AgentCombatSupportPolicy.wholePartyInBox(bot, bless));

        Character near = bot(new Point(0, 0), member(2, new Point(250, 50), false));
        assertTrue(AgentCombatSupportPolicy.hasPartyMemberInBoxMissingBuff(near, bless));
        assertTrue(AgentCombatSupportPolicy.wholePartyInBox(near, bless));
    }

    @Test
    void aMemberWhoAlreadyHasTheBuffDoesNotNeedIt() {
        Character bot = bot(new Point(0, 0), member(2, new Point(100, 0), true));
        assertFalse(AgentCombatSupportPolicy.hasPartyMemberInBoxMissingBuff(bot, bless()));
    }

    @Test
    void duringAGatheringAPartyBuffWaitsForStragglersUntilTheWaitEnds() {
        StatEffect bless = bless();
        Character bot = bot(new Point(0, 0), member(2, new Point(900, 0), false));
        AgentPartyGatherRegistry.Gathering gathering =
                AgentPartyGatherRegistry.open(9_900_001, 104040000, 0L, 12_000, 25_000);
        assertTrue(AgentCombatBuffRuntime.holdForParty(gathering, Cleric.BLESS, bless, bot, 1_000));
        assertFalse(AgentCombatBuffRuntime.holdForParty(gathering, Cleric.BLESS, bless, bot, 12_000));
        assertFalse(AgentCombatBuffRuntime.holdForParty(null, Cleric.BLESS, bless, bot, 1_000));
        // A charge has a box too, but it never reaches anyone else: nothing to wait for.
        assertFalse(AgentCombatBuffRuntime.holdForParty(gathering, Paladin.SWORD_HOLY_CHARGE, charge(), bot, 1_000));
        AgentPartyGatherRegistry.close(gathering);
    }

    private static StatEffect bless() {
        StatEffect effect = mock(StatEffect.class);
        when(effect.hasBoundingBox()).thenReturn(true);
        when(effect.getStatups()).thenReturn(List.of(new Pair<>(BuffStat.ACC, 20)));
        when(effect.calculateBoundingBox(any(Point.class), anyBoolean())).thenAnswer(call -> {
            Point at = call.getArgument(0);
            return new Rectangle(at.x - 300, at.y - 200, 600, 400);
        });
        return effect;
    }

    private static StatEffect charge() {
        StatEffect effect = mock(StatEffect.class);
        when(effect.hasBoundingBox()).thenReturn(true);
        when(effect.getStatups()).thenReturn(List.of(new Pair<>(BuffStat.WK_CHARGE, 1)));
        return effect;
    }

    private static Character bot(Point at, Character... members) {
        Character bot = mock(Character.class);
        when(bot.getId()).thenReturn(BOT_ID);
        when(bot.getPosition()).thenReturn(at);
        when(bot.getPartyMembersOnSameMap()).thenReturn(List.of(members));
        return bot;
    }

    private static Character member(int id, Point at, boolean buffed) {
        Character member = mock(Character.class);
        when(member.getId()).thenReturn(id);
        when(member.isAlive()).thenReturn(true);
        when(member.getPosition()).thenReturn(at);
        when(member.getBuffedValue(BuffStat.ACC)).thenReturn(buffed ? 20 : null);
        return member;
    }
}
