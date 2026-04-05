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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void warriorLevel16BuildStartsPowerStrikeAndSlashBlastAfterHpSkills() {
        Character bot = mock(Character.class);
        BotEntry entry = new BotEntry(bot, mock(Character.class), mock(ScheduledFuture.class));
        int warriorBook = GameConstants.getSkillBook(Warrior.IMPROVED_HPREC / 10000);
        int[] remainingSps = new int[5];
        remainingSps[warriorBook] = 19;
        Map<Integer, Integer> skillLevels = new HashMap<>();
        Map<Integer, Skill> skills = new HashMap<>();

        skills.put(Warrior.IMPROVED_HPREC, mockSkill(Warrior.IMPROVED_HPREC));
        skills.put(Warrior.IMPROVED_MAXHP, mockSkill(Warrior.IMPROVED_MAXHP));
        skills.put(Warrior.POWER_STRIKE, mockSkill(Warrior.POWER_STRIKE));
        skills.put(Warrior.SLASH_BLAST, mockSkill(Warrior.SLASH_BLAST));

        when(bot.getJob()).thenReturn(Job.WARRIOR);
        when(bot.getRemainingSps()).thenReturn(remainingSps);
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
                return skills.get(skillId);
            });

            BotBuildManager.autoAssignSp(entry, bot);
        }

        assertEquals(0, remainingSps[warriorBook]);
        assertEquals(5, skillLevels.getOrDefault(Warrior.IMPROVED_HPREC, 0));
        assertEquals(10, skillLevels.getOrDefault(Warrior.IMPROVED_MAXHP, 0));
        assertEquals(1, skillLevels.getOrDefault(Warrior.POWER_STRIKE, 0));
        assertEquals(3, skillLevels.getOrDefault(Warrior.SLASH_BLAST, 0));
        assertNull(skillLevels.get(Warrior.ENDURE));
        assertNull(skillLevels.get(Warrior.IRON_BODY));
    }

    @Test
    void warriorRespecRebuildsIncorrectFirstJobAllocation() {
        Character bot = mock(Character.class);
        BotEntry entry = new BotEntry(bot, mock(Character.class), mock(ScheduledFuture.class));
        int warriorBook = GameConstants.getSkillBook(Warrior.IMPROVED_HPREC / 10000);
        int[] remainingSps = new int[5];
        Map<Integer, Integer> skillLevels = new HashMap<>();
        Map<Integer, Skill> skills = new HashMap<>();

        skills.put(Warrior.IMPROVED_HPREC, mockSkill(Warrior.IMPROVED_HPREC));
        skills.put(Warrior.IMPROVED_MAXHP, mockSkill(Warrior.IMPROVED_MAXHP));
        skills.put(Warrior.POWER_STRIKE, mockSkill(Warrior.POWER_STRIKE));
        skills.put(Warrior.SLASH_BLAST, mockSkill(Warrior.SLASH_BLAST));

        skillLevels.put(Warrior.IMPROVED_HPREC, 9);
        skillLevels.put(Warrior.IMPROVED_MAXHP, 10);

        Map<Skill, Character.SkillEntry> learnedSkills = new LinkedHashMap<>();
        learnedSkills.put(skills.get(Warrior.IMPROVED_HPREC), new Character.SkillEntry((byte) 9, 0, -1));
        learnedSkills.put(skills.get(Warrior.IMPROVED_MAXHP), new Character.SkillEntry((byte) 10, 0, -1));

        when(bot.getJob()).thenReturn(Job.WARRIOR);
        when(bot.getRemainingSps()).thenReturn(remainingSps);
        when(bot.getSkills()).thenReturn(learnedSkills);
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
                return skills.get(skillId);
            });

            assertEquals("ok, rebuilt my sp using the bot build", BotBuildManager.respecSp(entry, bot));
        }

        assertEquals(0, remainingSps[warriorBook]);
        assertEquals(5, skillLevels.getOrDefault(Warrior.IMPROVED_HPREC, 0));
        assertEquals(10, skillLevels.getOrDefault(Warrior.IMPROVED_MAXHP, 0));
        assertEquals(1, skillLevels.getOrDefault(Warrior.POWER_STRIKE, 0));
        assertEquals(3, skillLevels.getOrDefault(Warrior.SLASH_BLAST, 0));
    }

    private static Skill mockSkill(int skillId) {
        Skill skill = mock(Skill.class);
        when(skill.getId()).thenReturn(skillId);
        return skill;
    }
}
