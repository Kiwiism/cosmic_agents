package server.life.autonomy;

import org.junit.jupiter.api.Test;
import server.life.MobSkillId;
import server.life.MobSkillType;
import server.life.autonomy.alishar.AlisharActorBehavior;
import server.life.autonomy.papapixie.PapaPixieActorBehavior;

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

    @Test
    void loadsPapaPixieCastWindowsAndExplicitSkillAnimationsFromWz() {
        ServerMobActionCatalog.MonsterActions actions =
                ServerMobActionCatalog.forMob(PapaPixieActorBehavior.MOB_ID);

        assertEquals(1, actions.attacks().size());
        BossAction.OrdinaryAttack attack = actions.attacks().getFirst();
        assertEquals(1_950, attack.impactDelayMs());
        assertEquals(2_730, attack.animationTimeMs());
        assertEquals(new Point(-320, -140), attack.lt());
        assertEquals(new Point(30, 10), attack.rb());

        assertEquals(List.of(1, 1, 1, 2, 3, 3),
                actions.skills().stream().map(BossAction.Skill::actionNumber).toList());
        assertEquals(List.of(2_730, 2_730, 2_730, 910, 910, 910),
                actions.skills().stream().map(BossAction.Skill::animationTimeMs).toList());
        assertEquals(actions.skills().stream().map(BossAction.Skill::animationTimeMs).toList(),
                actions.skills().stream().map(BossAction.Skill::effectDelayMs).toList());
    }

    @Test
    void resolvesLinkedChronosSummonAttackAnimations() {
        ServerMobActionCatalog.MonsterActions platoon =
                ServerMobActionCatalog.forMob(9_300_016);
        ServerMobActionCatalog.MonsterActions master =
                ServerMobActionCatalog.forMob(9_300_017);

        assertEquals(1, platoon.attacks().size());
        assertEquals(1, master.attacks().size());
        assertTrue(platoon.attacks().getFirst().magic());
        assertTrue(master.attacks().getFirst().magic());
        assertEquals(800, platoon.attacks().getFirst().impactDelayMs());
        assertEquals(800, master.attacks().getFirst().impactDelayMs());
        assertEquals(new Point(-227, -239), platoon.attacks().getFirst().lt());
        assertEquals(new Point(173, 161), platoon.attacks().getFirst().rb());
    }
}
