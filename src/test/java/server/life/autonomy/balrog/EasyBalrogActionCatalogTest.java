package server.life.autonomy.balrog;

import org.junit.jupiter.api.Test;
import server.life.MobSkillId;
import server.life.MobSkillType;
import server.life.autonomy.BossAction;
import server.life.autonomy.ServerMobActionCatalog;

import java.awt.Point;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EasyBalrogActionCatalogTest {
    @Test
    void loadsBodyAttacksAndHpGatedSkillsFromWz() {
        var actions = ServerMobActionCatalog.forMob(EasyBalrogBodyBehavior.MOB_ID);

        assertEquals(4, actions.attacks().size());
        BossAction.OrdinaryAttack tremor = actions.attacks().get(0);
        assertEquals(5, tremor.mpCost());
        assertEquals(3_240, tremor.impactDelayMs());
        assertEquals(new Point(-400, -600), tremor.lt());
        assertEquals(new Point(400, 0), tremor.rb());
        assertEquals(-4, tremor.areaStart());
        assertEquals(9, tremor.areaCount());
        assertEquals(9, tremor.selectedAreaCount());
        assertTrue(tremor.magic());
        assertTrue(tremor.tremble());

        BossAction.OrdinaryAttack pillars = actions.attacks().get(2);
        assertEquals(-5, pillars.areaStart());
        assertEquals(11, pillars.areaCount());
        assertEquals(3, pillars.selectedAreaCount());
        assertTrue(pillars.hasDistributedRegions());
        assertTrue(pillars.deadly());
        assertEquals(280, pillars.physicalAttack());

        BossAction.OrdinaryAttack dispel = actions.attacks().get(3);
        assertEquals(MobSkillType.DISPEL.getId(), dispel.diseaseSkill());
        assertEquals(12, dispel.diseaseLevel());
        assertEquals(List.of(
                        new MobSkillId(MobSkillType.PHYSICAL_AND_MAGIC_COUNTER, 4),
                        new MobSkillId(MobSkillType.UNDEAD, 3)),
                actions.skills().stream().map(action -> action.mobSkill().getId()).toList());
    }

    @Test
    void loadsBothClawRoutinesAndDistributedLayoutsFromWz() {
        var released = ServerMobActionCatalog.forMob(EasyBalrogReleasedClawBehavior.MOB_ID);
        assertEquals(2, released.attacks().size());
        assertEquals(7, released.attacks().getFirst().areaCount());
        assertEquals(3, released.attacks().getFirst().selectedAreaCount());
        assertEquals(336, released.attacks().getFirst().physicalAttack());
        assertEquals(List.of(162, 163), released.skills().stream()
                .map(action -> action.mobSkill().getId().level()).toList());

        var initial = ServerMobActionCatalog.forMob(EasyBalrogInitialClawBehavior.MOB_ID);
        assertEquals(3, initial.attacks().size());
        assertEquals(13, initial.attacks().getFirst().areaCount());
        assertEquals(4, initial.attacks().getFirst().selectedAreaCount());
        assertEquals(MobSkillType.REVERSE_INPUT.getId(),
                initial.attacks().get(1).diseaseSkill());
        assertEquals(3, initial.attacks().get(1).diseaseLevel());
    }

    @Test
    void loadsSummonedBalrogAttacksThroughTheirLinkedWzTemplates() {
        var junior = ServerMobActionCatalog.forMob(
                BalrogSummonedAddBehavior.JR_BALROG_ID);
        assertEquals(3, junior.attacks().size());
        assertTrue(junior.attacks().stream().allMatch(BossAction.OrdinaryAttack::magic));
        assertTrue(junior.skills().isEmpty());

        var crimson = ServerMobActionCatalog.forMob(
                BalrogSummonedAddBehavior.CRIMSON_BALROG_ID);
        assertEquals(2, crimson.attacks().size());
        assertTrue(crimson.attacks().stream().allMatch(BossAction.OrdinaryAttack::magic));
        assertTrue(crimson.skills().isEmpty());
    }
}
