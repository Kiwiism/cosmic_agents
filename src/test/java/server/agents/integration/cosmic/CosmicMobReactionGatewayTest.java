package server.agents.integration.cosmic;

import client.Character;
import client.Client;
import config.YamlConfig;
import net.server.channel.handlers.AbstractDealDamageHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.combat.AgentMobReactionMetrics;
import server.agents.capabilities.combat.MonsterAggroTargetService;
import server.integration.AgentPresence;
import server.life.Monster;
import server.life.MonsterStats;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CosmicMobReactionGatewayTest {
    private boolean originalReactionEnabled;
    private boolean originalAggroEnabled;
    private long originalTimeoutMs;

    @BeforeEach
    void saveConfig() {
        originalReactionEnabled = YamlConfig.config.agents.combat.observedMobReaction.enabled;
        originalAggroEnabled = YamlConfig.config.agents.combat.lastHitAggro.enabled;
        originalTimeoutMs = YamlConfig.config.agents.combat.lastHitAggro.targetTimeoutMs;
        ScheduledFuture<?> scheduled = mock(ScheduledFuture.class);
        CosmicMonsterPursuitRuntime.installLoopSchedulerForTest((action, periodMs) -> scheduled);
    }

    @AfterEach
    void restoreGlobals() {
        YamlConfig.config.agents.combat.observedMobReaction.enabled = originalReactionEnabled;
        YamlConfig.config.agents.combat.lastHitAggro.enabled = originalAggroEnabled;
        YamlConfig.config.agents.combat.lastHitAggro.targetTimeoutMs = originalTimeoutMs;
        AgentPresence.install(null);
        CosmicMonsterPursuitRuntime.resetForTest();
    }

    @Test
    void observedAttackPreparesControllerWithoutApplyingDamageAgain() {
        YamlConfig.config.agents.combat.observedMobReaction.enabled = true;
        MapleMap map = observedMap();
        Character agent = character(10, "agent", map);
        Character observer = character(20, "observer", map);
        AgentPresence.install(candidate -> candidate == agent);
        when(map.getAllPlayers()).thenReturn(List.of(agent, observer));
        Monster monster = monster(100, map, 100, true);
        when(map.getMonsterByOid(100)).thenReturn(monster);
        long protectedBefore = AgentMobReactionMetrics.snapshot().duplicateDamageProtections();

        CosmicMobReactionGateway.INSTANCE.prepareObservedAttack(attack(100, 100), agent);

        verify(monster).aggroSwitchController(observer, true);
        verify(map, never()).damageMonster(eq(agent), eq(monster), anyInt());
        assertEquals(protectedBefore + 1,
                AgentMobReactionMetrics.snapshot().duplicateDamageProtections());
    }

    @Test
    void multiHitMultiTargetPreparesEachMobOnceAndNeverDuplicatesDamage() {
        YamlConfig.config.agents.combat.observedMobReaction.enabled = true;
        MapleMap map = observedMap();
        Character agent = character(10, "agent", map);
        Character observer = character(20, "observer", map);
        AgentPresence.install(candidate -> candidate == agent);
        when(map.getAllPlayers()).thenReturn(List.of(agent, observer));
        Monster first = monster(110, map, 50, true);
        Monster second = monster(111, map, 50, true);
        when(map.getMonsterByOid(110)).thenReturn(first);
        when(map.getMonsterByOid(111)).thenReturn(second);
        AbstractDealDamageHandler.AttackInfo attack = attack(110, 25, 50, 75);
        attack.targets.put(111, new AbstractDealDamageHandler.AttackTarget(
                (short) 120, List.of(75, 25, 50)));

        CosmicMobReactionGateway.INSTANCE.prepareObservedAttack(attack, agent);

        verify(first).aggroSwitchController(observer, true);
        verify(second).aggroSwitchController(observer, true);
        verify(map, never()).damageMonster(eq(agent), eq(first), anyInt());
        verify(map, never()).damageMonster(eq(agent), eq(second), anyInt());
    }

    @Test
    void acceptedMultiHitUsesPerLineKnockbackOutcomeInsteadOfTotalDamage() {
        YamlConfig.config.agents.combat.observedMobReaction.enabled = true;
        YamlConfig.config.agents.combat.lastHitAggro.enabled = true;
        MapleMap map = observedMap();
        Character agent = character(10, "agent", map);
        Character observer = character(20, "observer", map);
        AgentPresence.install(candidate -> candidate == agent);
        when(map.getAllPlayers()).thenReturn(List.of(agent, observer));
        Monster monster = monster(112, map, 50, true);
        when(map.getMonsterByOid(112)).thenReturn(monster);

        CosmicMobReactionGateway.INSTANCE.prepareObservedAttack(attack(112, 40, 40), agent);
        assertTrue(CosmicMobReactionGateway.INSTANCE.handleAcceptedDamage(monster, agent, 80, 40));

        MonsterAggroTargetService.Snapshot snapshot = MonsterAggroTargetService.inspect(
                monster, System.currentTimeMillis(), 10_000L);
        assertEquals(80, snapshot.damage());
        assertEquals("hurt-only", snapshot.reaction());
    }

    @Test
    void acceptedDamageOverridesPreparedEstimateInLogicalTargetState() {
        YamlConfig.config.agents.combat.observedMobReaction.enabled = true;
        YamlConfig.config.agents.combat.lastHitAggro.enabled = true;
        MapleMap map = observedMap();
        Character agent = character(10, "agent", map);
        Character observer = character(20, "observer", map);
        AgentPresence.install(candidate -> candidate == agent);
        when(map.getAllPlayers()).thenReturn(List.of(agent, observer));
        Monster monster = monster(113, map, 50, true);

        CosmicMobReactionGateway.INSTANCE.prepareObservedAttack(attack(113, 100), agent);
        assertTrue(CosmicMobReactionGateway.INSTANCE.handleAcceptedDamage(monster, agent, 1, 1));

        MonsterAggroTargetService.Snapshot snapshot = MonsterAggroTargetService.inspect(
                monster, System.currentTimeMillis(), 10_000L);
        assertEquals(1, snapshot.damage());
        assertEquals("hurt-only", snapshot.reaction());
    }

    @Test
    void belowThresholdAndImmobileHitsDoNotPrepareKnockback() {
        YamlConfig.config.agents.combat.observedMobReaction.enabled = true;
        MapleMap map = observedMap();
        Character agent = character(10, "agent", map);
        Character observer = character(20, "observer", map);
        AgentPresence.install(candidate -> candidate == agent);
        when(map.getAllPlayers()).thenReturn(List.of(agent, observer));
        Monster below = monster(101, map, 100, true);
        Monster immobile = monster(102, map, 100, false);
        when(map.getMonsterByOid(101)).thenReturn(below);
        when(map.getMonsterByOid(102)).thenReturn(immobile);
        long knockbackBefore = AgentMobReactionMetrics.snapshot().knockbackPrepared();

        CosmicMobReactionGateway.INSTANCE.prepareObservedAttack(attack(101, 99), agent);
        CosmicMobReactionGateway.INSTANCE.prepareObservedAttack(attack(102, 100), agent);

        assertEquals(knockbackBefore, AgentMobReactionMetrics.snapshot().knockbackPrepared());
        verify(below).aggroSwitchController(observer, true);
        verify(immobile).aggroSwitchController(observer, true);
    }

    @Test
    void equalAndAboveThresholdPrepareNormalClientKnockback() {
        YamlConfig.config.agents.combat.observedMobReaction.enabled = true;
        MapleMap map = observedMap();
        Character agent = character(10, "agent", map);
        Character observer = character(20, "observer", map);
        AgentPresence.install(candidate -> candidate == agent);
        when(map.getAllPlayers()).thenReturn(List.of(agent, observer));
        Monster equal = monster(103, map, 100, true);
        Monster above = monster(104, map, 100, true);
        when(map.getMonsterByOid(103)).thenReturn(equal);
        when(map.getMonsterByOid(104)).thenReturn(above);
        long knockbackBefore = AgentMobReactionMetrics.snapshot().knockbackPrepared();

        CosmicMobReactionGateway.INSTANCE.prepareObservedAttack(attack(103, 100), agent);
        CosmicMobReactionGateway.INSTANCE.prepareObservedAttack(attack(104, 101), agent);

        assertEquals(knockbackBefore + 2, AgentMobReactionMetrics.snapshot().knockbackPrepared());
    }

    @Test
    void agentOnlyMapKeepsLightweightPath() {
        YamlConfig.config.agents.combat.observedMobReaction.enabled = true;
        MapleMap map = mock(MapleMap.class);
        Character agent = character(10, "agent", map);
        AgentPresence.install(candidate -> candidate == agent);
        when(map.isObservedByPlayer()).thenReturn(false);
        when(map.getAllPlayers()).thenReturn(List.of(agent));
        Monster monster = monster(105, map, 1, true);
        when(map.getMonsterByOid(105)).thenReturn(monster);

        CosmicMobReactionGateway.INSTANCE.prepareObservedAttack(attack(105, 100), agent);

        verify(monster, never()).aggroSwitchController(eq(agent), eq(true));
        verify(monster, never()).aggroAutoAggroUpdate(agent);
    }

    @Test
    void latestValidAgentAndPlayerHitsTransferLogicalAggro() {
        YamlConfig.config.agents.combat.lastHitAggro.enabled = true;
        YamlConfig.config.agents.combat.lastHitAggro.targetTimeoutMs = 10_000L;
        MapleMap map = observedMap();
        Character agent = character(10, "agent", map);
        Character player = character(20, "player", map);
        AgentPresence.install(candidate -> candidate == agent);
        when(map.getAllPlayers()).thenReturn(List.of(agent, player));
        Monster monster = monster(106, map, 100, true);

        assertTrue(CosmicMobReactionGateway.INSTANCE.handleAcceptedDamage(monster, agent, 100));
        assertEquals(10, MonsterAggroTargetService.inspect(monster,
                System.currentTimeMillis(), 10_000L).targetId());

        assertTrue(CosmicMobReactionGateway.INSTANCE.handleAcceptedDamage(monster, player, 50));
        assertEquals(20, MonsterAggroTargetService.inspect(monster,
                System.currentTimeMillis(), 10_000L).targetId());

        assertTrue(CosmicMobReactionGateway.INSTANCE.handleAcceptedDamage(monster, agent, 50));
        assertEquals(10, MonsterAggroTargetService.inspect(monster,
                System.currentTimeMillis(), 10_000L).targetId());
        verify(monster, times(3)).aggroSwitchController(player, true);
    }

    @Test
    void staleLogicalTargetCannotSurviveMapChange() {
        YamlConfig.config.agents.combat.lastHitAggro.enabled = true;
        MapleMap map = observedMap();
        MapleMap otherMap = mock(MapleMap.class);
        Character agent = character(10, "agent", map);
        Character observer = character(20, "observer", map);
        AgentPresence.install(candidate -> candidate == agent);
        when(map.getAllPlayers()).thenReturn(List.of(agent, observer));
        Monster monster = monster(107, map, 100, true);
        assertTrue(CosmicMobReactionGateway.INSTANCE.handleAcceptedDamage(monster, agent, 100));

        when(agent.getMap()).thenReturn(otherMap);

        assertFalse(MonsterAggroTargetService.inspect(monster,
                System.currentTimeMillis(), 10_000L).hasTarget());
    }

    @Test
    void disabledFlagsPreserveLegacyAggroAndDamagePath() {
        YamlConfig.config.agents.combat.observedMobReaction.enabled = false;
        YamlConfig.config.agents.combat.lastHitAggro.enabled = false;
        MapleMap map = observedMap();
        Character agent = character(10, "agent", map);
        AgentPresence.install(candidate -> candidate == agent);
        Monster monster = monster(108, map, 100, true);

        CosmicMobReactionGateway.INSTANCE.prepareObservedAttack(attack(108, 100), agent);

        assertFalse(CosmicMobReactionGateway.INSTANCE.handleAcceptedDamage(monster, agent, 100));
        verify(monster, never()).aggroSwitchController(any(Character.class), eq(true));
        verify(map, never()).damageMonster(eq(agent), eq(monster), anyInt());
        assertFalse(MonsterAggroTargetService.inspect(monster,
                System.currentTimeMillis(), 10_000L).hasTarget());
    }

    private static AbstractDealDamageHandler.AttackInfo attack(int oid, int... lines) {
        AbstractDealDamageHandler.AttackInfo attack = new AbstractDealDamageHandler.AttackInfo();
        attack.targets = new HashMap<>();
        attack.targets.put(oid, new AbstractDealDamageHandler.AttackTarget((short) 0,
                java.util.Arrays.stream(lines).boxed().toList()));
        return attack;
    }

    private static MapleMap observedMap() {
        MapleMap map = mock(MapleMap.class);
        when(map.isObservedByPlayer()).thenReturn(true);
        return map;
    }

    private static Character character(int id, String name, MapleMap map) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getName()).thenReturn(name);
        when(character.getMap()).thenReturn(map);
        when(character.getClient()).thenReturn(mock(Client.class));
        when(character.getPosition()).thenReturn(new Point(id, 0));
        when(character.isAlive()).thenReturn(true);
        when(character.isLoggedinWorld()).thenReturn(true);
        when(character.isChangingMaps()).thenReturn(false);
        return character;
    }

    private static Monster monster(int oid, MapleMap map, int pushed, boolean mobile) {
        Monster monster = mock(Monster.class);
        MonsterStats stats = mock(MonsterStats.class);
        when(stats.getPushed()).thenReturn(pushed);
        when(monster.getObjectId()).thenReturn(oid);
        when(monster.getMap()).thenReturn(map);
        when(monster.getStats()).thenReturn(stats);
        when(monster.getPosition()).thenReturn(new Point(0, 0));
        when(monster.isAlive()).thenReturn(true);
        when(monster.isMobile()).thenReturn(mobile);
        when(map.getMonsterByOid(oid)).thenReturn(monster);
        return monster;
    }
}
