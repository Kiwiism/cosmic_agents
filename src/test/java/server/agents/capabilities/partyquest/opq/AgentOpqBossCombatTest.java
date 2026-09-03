package server.agents.capabilities.partyquest.opq;

import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataProviderFactory;
import provider.DataTool;
import provider.wz.WZFiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AgentOpqBossCombatTest {
    @Test
    void papaPixieContractComesFromAuthoritativeMobWz() {
        Data papaPixie = DataProviderFactory.getDataProvider(WZFiles.MOB).getData("9300039.img");
        assertNotNull(papaPixie);
        assertEquals(1, DataTool.getInt("info/boss", papaPixie, 0));
        assertEquals(65, DataTool.getInt("info/level", papaPixie, 0));
        assertEquals(672_000, DataTool.getInt("info/maxHP", papaPixie, 0));
        assertEquals(1_500, DataTool.getInt("info/pushed", papaPixie, 0));
    }

    @Test
    void cleanupAllowlistContainsEveryWzDefinedPapaPixieSummon() {
        Data mobSkills = DataProviderFactory.getDataProvider(WZFiles.SKILL).getData("MobSkill.img");
        assertNotNull(mobSkills);
        Set<Integer> summons = Set.of(
                DataTool.getInt("200/level/49/0", mobSkills, 0),
                DataTool.getInt("200/level/50/0", mobSkills, 0),
                DataTool.getInt("200/level/51/0", mobSkills, 0));
        assertEquals(Set.of(9_300_054, 9_300_055, 9_300_056), summons);
        assertEquals(summons, AgentOpqDefinition.PAPA_PIXIE_SUMMONS);
        assertTrue(AgentOpqDefinition.GARDEN_SETUP_MOBS.containsAll(summons));
        assertTrue(AgentOpqDefinition.ALL_COMBAT_MOBS.containsAll(summons));
    }

    @Test
    void authoredTriggerSpawnsPapaPixieAboveTheContinuousGardenFloor() throws Exception {
        String reactor = Files.readString(Path.of("scripts/reactor/2001016.js"));
        assertTrue(reactor.contains("rm.spawnMonster(9300039, 260, 490)"));

        Data garden = DataProviderFactory.getDataProvider(WZFiles.MAP)
                .getData("Map/Map9/920010800.img");
        assertNotNull(garden);
        Data footholds = garden.getChildByPath("foothold");
        assertNotNull(footholds);
        boolean floorBelowSpawn = footholds.getChildren().stream()
                .flatMap(page -> page.getChildren().stream())
                .flatMap(group -> group.getChildren().stream())
                .anyMatch(foothold -> DataTool.getInt("x1", foothold, Integer.MAX_VALUE) <= 260
                        && DataTool.getInt("x2", foothold, Integer.MIN_VALUE) >= 260
                        && DataTool.getInt("y1", foothold, 0) == 563
                        && DataTool.getInt("y2", foothold, 0) == 563);
        assertTrue(floorBelowSpawn);
    }

    @Test
    void bossRoutineUsesOrdinaryCombatWithoutInteractionShortcuts() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/server/agents/capabilities/partyquest/opq/AgentOpqCoordinator.java"));
        int start = source.indexOf("private static void tickPapaPixieCombat(");
        int end = source.indexOf("private static Monster papaPixie(", start);
        assertTrue(start >= 0 && end > start);
        String routine = source.substring(start, end);

        assertTrue(routine.contains("ACTIONS.grind(entry, targets)"));
        assertFalse(routine.contains("ACTIONS.navigate("));
        assertFalse(routine.contains("attackMonster("));
        assertFalse(routine.contains("teleport"));
        assertFalse(routine.contains("flyTo("));
        assertFalse(routine.contains("hitReactor("));
        assertFalse(routine.contains("stagePosition("));
        assertFalse(routine.contains("changeMap"));
        assertFalse(routine.contains("setHp("));
    }

    @Test
    void fullAgentPartyAssignsFourBossAttackersAndTwoSummonClearers() {
        List<AgentOpqMemberState.Role> roles = java.util.stream.IntStream.range(0, 6)
                .mapToObj(index -> AgentOpqCoordinator.papaPixieCombatRole(index, 6))
                .toList();

        assertEquals(4, roles.stream()
                .filter(role -> role == AgentOpqMemberState.Role.BOSS_ATTACKER).count());
        assertEquals(2, roles.stream()
                .filter(role -> role == AgentOpqMemberState.Role.BOSS_SUMMON_CLEARER).count());
    }

    @Test
    void summonClearersReturnToPapaWhenNoSummonIsAlive() {
        assertEquals(AgentOpqDefinition.PAPA_PIXIE_SUMMONS,
                AgentOpqCoordinator.papaPixieCombatTargets(
                        AgentOpqMemberState.Role.BOSS_SUMMON_CLEARER, true));
        assertEquals(Set.of(AgentOpqDefinition.PAPA_PIXIE),
                AgentOpqCoordinator.papaPixieCombatTargets(
                        AgentOpqMemberState.Role.BOSS_SUMMON_CLEARER, false));
        assertEquals(Set.of(AgentOpqDefinition.PAPA_PIXIE),
                AgentOpqCoordinator.papaPixieCombatTargets(
                        AgentOpqMemberState.Role.BOSS_ATTACKER, true));
    }

    @Test
    void bossProgressRequiresNewBossOrLowerHp() {
        AgentOpqMemberState member = new AgentOpqMemberState(
                1, AgentOpqMemberState.MemberType.AGENT);
        assertTrue(member.observeBossCombat(100, 1_000));
        assertFalse(member.observeBossCombat(100, 1_000));
        assertFalse(member.observeBossCombat(100, 1_100));
        assertTrue(member.observeBossCombat(100, 900));
        assertTrue(member.observeBossCombat(101, 1_000));
        member.clearBossCombat();
        assertTrue(member.observeBossCombat(101, 1_000));
    }

    @Test
    void sessionDistinguishesPreBossGardenFromPostBossCleanup() {
        AgentOpqSession session = new AgentOpqSession(
                AgentOpqSession.Mode.TEST_OBSERVATION, 1L, 99, 1_000L);
        session.observePapaPixie(false, 1_100L);
        assertFalse(session.papaPixieEngaged());
        assertFalse(session.papaPixieDefeated());

        session.observePapaPixie(true, 1_200L);
        assertTrue(session.papaPixieEngaged());
        assertFalse(session.papaPixieDefeated());
        session.observePapaPixie(false, 1_300L);
        assertTrue(session.papaPixieDefeated());
    }

    @Test
    void rootExitIsGuardedByFullMobAndDropDrain() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/server/agents/capabilities/partyquest/opq/AgentOpqCoordinator.java"));
        int root = source.indexOf("agent.getItemQuantity(AgentOpqDefinition.ROOT_OF_LIFE");
        int evenSeed = source.indexOf("agent.getItemQuantity(AgentOpqDefinition.EVEN_STRANGER_SEED", root);
        String rootFlow = source.substring(root, evenSeed);

        assertTrue(rootFlow.contains("GARDEN_SETUP_MOBS"));
        assertTrue(rootFlow.contains("collectGardenCleanupDrop"));
        assertTrue(rootFlow.contains("gardenStageDrained(agent)"));
        assertTrue(rootFlow.indexOf("gardenStageDrained(agent)")
                < rootFlow.indexOf("enterAuthoredPortal(entry, agent, 1"));
        assertTrue(source.contains("AgentLootEligibility.canBotTargetLoot"));
        assertTrue(source.contains("!AgentOpqDefinition.EXCLUSIVE_ITEMS.contains"));
    }
}
