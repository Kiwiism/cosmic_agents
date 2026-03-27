package server.bots;

import client.Character;
import client.Job;
import client.Skill;
import client.SkillFactory;
import constants.game.GameConstants;
import constants.skills.Warrior;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BotBuildManagerTest {
    @Test
    void initialSyncWarriorSpendsPendingSpWithoutPrompt() {
        Character bot = mock(Character.class);
        BotEntry entry = new BotEntry(bot, mock(Character.class), mock(ScheduledFuture.class));
        int warriorBook = GameConstants.getSkillBook(Warrior.IMPROVED_HPREC / 10000);
        int[] remainingSps = new int[5];
        remainingSps[warriorBook] = 1;
        Map<Integer, Integer> skillLevels = new HashMap<>();

        when(bot.getLevel()).thenReturn(10);
        when(bot.getJob()).thenReturn(Job.WARRIOR);
        when(bot.getRemainingSps()).thenReturn(remainingSps);
        when(bot.getRemainingAp()).thenReturn(0);
        when(bot.getSkillLevel(any(Skill.class))).thenAnswer(invocation -> {
            Skill skill = invocation.getArgument(0);
            return (byte) skillLevels.getOrDefault(skill.getId(), 0).intValue();
        });
        when(bot.getMasterLevel(any(Skill.class))).thenReturn(0);
        when(bot.getSkillExpiration(any(Skill.class))).thenReturn(0L);
        doAnswer(invocation -> {
            int delta = invocation.getArgument(0);
            int book = invocation.getArgument(1);
            remainingSps[book] += delta;
            return null;
        }).when(bot).gainSp(anyInt(), anyInt(), anyBoolean());
        doAnswer(invocation -> {
            Skill skill = invocation.getArgument(0);
            byte newLevel = invocation.getArgument(1);
            skillLevels.put(skill.getId(), (int) newLevel);
            return null;
        }).when(bot).changeSkillLevel(any(Skill.class), anyByte(), anyInt(), anyLong());

        try (MockedStatic<SkillFactory> skillFactory = mockStatic(SkillFactory.class)) {
            skillFactory.when(() -> SkillFactory.getSkill(anyInt())).thenAnswer(invocation -> {
                int skillId = invocation.getArgument(0);
                Skill skill = mock(Skill.class);
                when(skill.getId()).thenReturn(skillId);
                return skill;
            });

            BotBuildManager.checkLevelUp(entry, bot);
        }

        assertEquals(10, entry.lastKnownLevel);
        assertEquals(0, remainingSps[warriorBook]);
        assertEquals(1, skillLevels.getOrDefault(Warrior.IMPROVED_HPREC, 0));
    }

    @Test
    void initialSyncHeroKeepsPendingSpUntilVariantIsChosen() {
        Character bot = mock(Character.class);
        BotEntry entry = new BotEntry(bot, mock(Character.class), mock(ScheduledFuture.class));
        int[] remainingSps = new int[5];
        remainingSps[3] = 1;

        when(bot.getLevel()).thenReturn(120);
        when(bot.getJob()).thenReturn(Job.HERO);
        when(bot.getRemainingSps()).thenReturn(remainingSps);
        when(bot.getRemainingAp()).thenReturn(0);

        BotBuildManager.checkLevelUp(entry, bot);

        assertEquals(120, entry.lastKnownLevel);
        assertEquals(1, remainingSps[3]);
        verify(bot, never()).gainSp(anyInt(), anyInt(), anyBoolean());
        verify(bot, never()).changeSkillLevel(any(Skill.class), anyByte(), anyInt(), anyLong());
    }
}
