package server.life.autonomy;

import org.junit.jupiter.api.Test;
import server.life.MobSkillId;
import server.life.MobSkillType;
import server.life.autonomy.alishar.AlisharActorBehavior;

import java.awt.Point;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerMobActionCatalogTest {
    @Test
    void loadsAlisharOrdinaryAttackAndExplicitSkillActionsFromWz() {
        ServerMobActionCatalog.MonsterActions actions =
                ServerMobActionCatalog.forMob(AlisharActorBehavior.MOB_ID);

        assertEquals(1, actions.attacks().size());
        BossAction.OrdinaryAttack attack = actions.attacks().getFirst();
        assertEquals(0, attack.attackIndex());
        assertEquals(1, attack.actionNumber());
        assertEquals(5, attack.mpCost());
        assertEquals(1_155, attack.impactDelayMs());
        assertTrue(attack.animationTimeMs() > 0);
        assertTrue(attack.magic());
        assertEquals(new Point(-320, -210), attack.lt());
        assertEquals(new Point(110, 0), attack.rb());

        assertEquals(List.of(
                        new MobSkillId(MobSkillType.SUMMON, 29),
                        new MobSkillId(MobSkillType.SUMMON, 30),
                        new MobSkillId(MobSkillType.SUMMON, 31),
                        new MobSkillId(MobSkillType.SEAL, 1),
                        new MobSkillId(MobSkillType.DARKNESS, 1)),
                actions.skills().stream().map(action -> action.mobSkill().getId()).toList());
        assertTrue(actions.skills().stream().allMatch(action -> action.actionNumber() == 1));
        assertTrue(actions.skills().stream().allMatch(action -> action.animationTimeMs() == 800));
        assertTrue(actions.skills().stream().allMatch(action -> action.effectDelayMs() == 800));
        assertEquals(new Point(-300, -120), actions.skills().get(3).lt());
        assertEquals(new Point(300, 120), actions.skills().get(3).rb());
    }
}
