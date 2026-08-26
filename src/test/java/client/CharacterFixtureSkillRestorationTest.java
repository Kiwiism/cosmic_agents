package client;

import constants.skills.Magician;
import constants.skills.Warrior;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CharacterFixtureSkillRestorationTest {
    @Test
    void restoringWarriorPassiveDoesNotMutateCapturedPool() throws Exception {
        Character character = newCharacter();
        Skill passive = skill(Warrior.IMPROVED_MAXHP, 10);

        character.restoreFixtureSkillLevel(passive, 1, 10);

        assertEquals(50, character.getRawMaxHp());
        assertEquals(5, character.getRawMaxMp());
        assertEquals(1, character.getSkillLevel(passive));
        assertEquals(10, character.getMasterLevel(passive));
    }

    @Test
    void restoringMagicianPassiveDoesNotMutateCapturedPool() throws Exception {
        Character character = newCharacter();
        Skill passive = skill(Magician.IMPROVED_MAX_MP_INCREASE, 10);

        character.restoreFixtureSkillLevel(passive, 4, 10);

        assertEquals(50, character.getRawMaxHp());
        assertEquals(5, character.getRawMaxMp());
        assertEquals(4, character.getSkillLevel(passive));
        assertEquals(10, character.getMasterLevel(passive));
    }

    private static Skill skill(int id, int maximumLevel) {
        Skill skill = mock(Skill.class);
        when(skill.getId()).thenReturn(id);
        when(skill.getMaxLevel()).thenReturn(maximumLevel);
        return skill;
    }

    private static Character newCharacter() throws Exception {
        Constructor<Character> constructor = Character.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }
}
