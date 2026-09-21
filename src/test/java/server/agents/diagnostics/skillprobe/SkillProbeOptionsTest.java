package server.agents.diagnostics.skillprobe;

import client.Job;
import client.inventory.WeaponType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillProbeOptionsTest {
    @Test
    void parsesAgentNameWithDefaults() {
        SkillProbeOptions options = SkillProbeOptions.parse(new String[]{"Hadrik"});
        assertEquals("Hadrik", options.agentName());
        assertNull(options.job());
        assertEquals(0, options.level());
        assertEquals(SkillProbeOptions.DEFAULT_MOB_ID, options.mobId());
        assertEquals(SkillProbeOptions.DEFAULT_MAP_ID, options.mapId());
        assertTrue(options.probeBuffs());
        assertFalse(options.wantsShaping());
    }

    @Test
    void parsesJobByNameOrIdAndDerivesLevel() {
        assertEquals(Job.FIGHTER, SkillProbeOptions.parse(new String[]{"a", "job=fighter"}).job());
        assertEquals(Job.CRUSADER, SkillProbeOptions.parse(new String[]{"a", "job=111"}).job());
        assertEquals(30, SkillProbeOptions.parse(new String[]{"a", "job=fighter"}).level());
        assertEquals(70, SkillProbeOptions.parse(new String[]{"a", "job=crusader"}).level());
        assertEquals(120, SkillProbeOptions.parse(new String[]{"a", "job=hero"}).level());
        assertEquals(10, SkillProbeOptions.parse(new String[]{"a", "job=warrior"}).level());
        assertEquals(45, SkillProbeOptions.parse(new String[]{"a", "job=fighter", "level=45"}).level());
    }

    @Test
    void parsesFlagsAndOverrides() {
        SkillProbeOptions options = SkillProbeOptions.parse(
                new String[]{"a", "mob=9300094", "map=100000000", "weapon=1302000", "nobuffs", "noshape", "keepmobs"});
        assertEquals(9300094, options.mobId());
        assertEquals(100000000, options.mapId());
        assertEquals(1302000, options.weaponItemId());
        assertFalse(options.probeBuffs());
        assertFalse(options.shape());
        assertTrue(options.keepMobs());
        assertFalse(options.profileBuild());
        assertTrue(SkillProbeOptions.parse(new String[]{"a", "build=profile"}).profileBuild());
        assertThrows(IllegalArgumentException.class, () -> SkillProbeOptions.parse(new String[]{"a", "build=nope"}));
    }

    @Test
    void rejectsBadInput() {
        assertThrows(IllegalArgumentException.class, () -> SkillProbeOptions.parse(new String[0]));
        assertThrows(IllegalArgumentException.class, () -> SkillProbeOptions.parse(new String[]{"a", "job=notajob"}));
        assertThrows(IllegalArgumentException.class, () -> SkillProbeOptions.parse(new String[]{"a", "level=abc"}));
        assertThrows(IllegalArgumentException.class, () -> SkillProbeOptions.parse(new String[]{"a", "bogus"}));
    }

    @Test
    void defaultWeaponFollowsBranch() {
        assertEquals(1302000, AgentSkillProbeService.defaultWeaponFor(Job.FIGHTER));
        assertEquals(1432000, AgentSkillProbeService.defaultWeaponFor(Job.DRAGONKNIGHT));
        assertEquals(1372005, AgentSkillProbeService.defaultWeaponFor(Job.CLERIC));
        assertEquals(1462000, AgentSkillProbeService.defaultWeaponFor(Job.CROSSBOWMAN));
        assertEquals(1452002, AgentSkillProbeService.defaultWeaponFor(Job.RANGER));
        assertEquals(1332007, AgentSkillProbeService.defaultWeaponFor(Job.CHIEFBANDIT));
        assertEquals(1472000, AgentSkillProbeService.defaultWeaponFor(Job.HERMIT));
        assertEquals(1492000, AgentSkillProbeService.defaultWeaponFor(Job.GUNSLINGER));
        assertEquals(1482000, AgentSkillProbeService.defaultWeaponFor(Job.BRAWLER));
        assertEquals(2070000, AgentSkillProbeService.defaultAmmoFor(WeaponType.CLAW));
        assertEquals(0, AgentSkillProbeService.defaultAmmoFor(WeaponType.SWORD1H));
    }

    @Test
    void reportRendersFailuresAndSummary() {
        SkillProbeOutcome ok = new SkillProbeOutcome(1101004, "Slash Blast", 20, 20, "ACTIVE_ATTACK",
                SkillProbeOutcome.Kind.ATTACK, SkillProbeOutcome.Stage.OK, "route=MELEE", 1, 0, 40L, List.of(), 300L);
        SkillProbeOutcome failed = new SkillProbeOutcome(1111005, "Coma", 30, 30, "ACTIVE_ATTACK",
                SkillProbeOutcome.Kind.ATTACK, SkillProbeOutcome.Stage.PLAN_COMBO_ORBS_REQUIRED,
                "combo finisher without combo orbs", 0, 0, 0L, List.of(), 5L);
        SkillProbeOutcome skipped = SkillProbeOutcome.notProbed(1110000, "Improving MP Recovery", 20, 20, "IGNORE",
                SkillProbeOutcome.Kind.PASSIVE_OR_OTHER, "passive/utility");
        SkillProbeOptions options = SkillProbeOptions.parse(new String[]{"Hadrik", "job=crusader"});
        SkillProbeReport report = new SkillProbeReport("Hadrik", Job.CRUSADER, 70, WeaponType.SWORD1H, options,
                List.of("note"), List.of(ok, failed, skipped), 1_000L, 4_000L, null);

        assertEquals(2, report.probedCount());
        assertEquals(1, report.okCount());
        assertEquals(List.of(failed), report.failures());
        String md = SkillProbeReportWriter.render(report);
        assertTrue(md.contains("# Skill probe: CRUSADER L70 (Hadrik)"));
        assertTrue(md.contains("**1/2 probed skills OK**"));
        assertTrue(md.contains("| Coma | 1111005 | 30 | ATTACK | PLAN_COMBO_ORBS_REQUIRED |"));
        assertTrue(md.contains("| Improving MP Recovery | 1110000 | 20/20 | PASSIVE_OR_OTHER | IGNORE | NOT_PROBED |"));
        List<String> chat = report.chatSummary();
        assertTrue(chat.get(0).contains("1/2 skills OK, 1 failed, 1 not probed, 3s"));
        assertTrue(chat.get(1).contains("Coma (1111005): PLAN_COMBO_ORBS_REQUIRED"));
    }
}
