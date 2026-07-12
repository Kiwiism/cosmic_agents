package server.life;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MonsterStatsCopyTest {
    @Test
    void copiesAllValuesAndSeparatesMutableCollections() {
        MonsterStats source = new MonsterStats();
        source.setHp(1234);
        source.setMp(567);
        source.setExp(890);
        source.setLevel(42);
        source.setPushed(321);
        source.setName("Test monster");
        source.setAnimationTime("move", 300);
        source.setEffectiveness(Element.FIRE, ElementalEffectiveness.WEAK);
        source.setRevives(List.of(1, 2));
        source.setSkills(Set.of(new MobSkillId(MobSkillType.ATTACK_UP, 1)));
        source.setFriendly(true);

        MonsterStats copy = source.copy();
        source.setAnimationTime("jump", 900);
        source.setEffectiveness(Element.ICE, ElementalEffectiveness.IMMUNE);

        assertEquals(1234, copy.getHp());
        assertEquals(567, copy.getMp());
        assertEquals(890, copy.getExp());
        assertEquals(42, copy.getLevel());
        assertEquals(321, copy.getPushed());
        assertEquals("Test monster", copy.getName());
        assertEquals(300, copy.getAnimationTime("move"));
        assertEquals(ElementalEffectiveness.WEAK, copy.getEffectiveness(Element.FIRE));
        assertEquals(List.of(1, 2), copy.getRevives());
        assertEquals(1, copy.getNoSkills());
        assertFalse(copy.animationTimes.containsKey("jump"));
        assertEquals(ElementalEffectiveness.NORMAL, copy.getEffectiveness(Element.ICE));
    }
}
