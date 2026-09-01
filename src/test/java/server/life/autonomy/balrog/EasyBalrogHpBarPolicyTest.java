package server.life.autonomy.balrog;

import org.junit.jupiter.api.Test;
import server.life.Monster;
import server.life.MonsterStats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EasyBalrogHpBarPolicyTest {
    @Test
    void fakeBodyHidesGaugeUntilItBecomesReal() {
        Monster body = monster(EasyBalrogEncounterService.BODY_ID);

        body.setFake(true);
        assertFalse(body.hasBossHPBar());

        body.setFake(false);
        assertTrue(body.hasBossHPBar());
    }

    @Test
    void overrideDoesNotReplaceExistingWzStyleOrAffectOtherBosses() {
        MonsterStats styled = new MonsterStats();
        styled.setTagColor(4);
        styled.setTagBgColor(3);
        EasyBalrogHpBarPolicy.applyMissingStyle(
                EasyBalrogEncounterService.BODY_ID, styled);
        assertEquals(4, styled.getTagColor());
        assertEquals(3, styled.getTagBgColor());

        MonsterStats other = new MonsterStats();
        EasyBalrogHpBarPolicy.applyMissingStyle(8800000, other);
        assertEquals(0, other.getTagColor());
        assertEquals(0, other.getTagBgColor());
    }

    private static Monster monster(int id) {
        MonsterStats stats = new MonsterStats();
        stats.hp = 100;
        stats.mp = 100;
        stats.boss = true;
        EasyBalrogHpBarPolicy.applyMissingStyle(id, stats);
        return new Monster(id, stats);
    }
}
