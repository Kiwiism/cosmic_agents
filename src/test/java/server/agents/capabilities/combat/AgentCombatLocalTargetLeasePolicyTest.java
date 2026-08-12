package server.agents.capabilities.combat;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.Monster;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentCombatLocalTargetLeasePolicyTest {
    @Test
    void emptyLocalAreaPromotesMapWidePreferredTarget() {
        Fixture fixture = fixture();

        List<Monster> selected = promote(
                fixture, List.of(fixture.localFallback), 1_000);

        assertEquals(List.of(fixture.remotePreferred), selected);
    }

    @Test
    void incompleteMapWideRouteFallsBackToEligibleLocalMob() {
        Fixture fixture = fixture();

        List<Monster> selected = AgentCombatTargetRuntime.promoteMapWidePreferredTargets(
                fixture.entry, fixture.agent, List.of(fixture.localFallback),
                1_000, target -> false);

        assertEquals(List.of(fixture.localFallback), selected);
    }

    @Test
    void activeLeaseKeepsNearbyEligibleMobAndReleasesOnlyAtEmptyThreshold() {
        Fixture fixture = fixture();
        AgentCombatLocalTargetLeaseState state = fixture.entry.capabilityStates()
                .require(AgentCombatLocalTargetLeaseState.STATE_KEY);
        state.beginMapWideTravel(100, "", 44, 1_000, 25_000);
        state.observeRegion(100, "", 44, 2_000, 25_000, 3);

        assertEquals(List.of(fixture.localFallback),
                promote(fixture, List.of(fixture.localFallback), 2_100));
        assertEquals(List.of(fixture.localFallback),
                promote(fixture, List.of(fixture.localFallback), 2_200));
        assertEquals(List.of(fixture.remotePreferred),
                promote(fixture, List.of(fixture.localFallback), 2_300));
    }

    @Test
    void nearbyPreferredMobAlwaysRetainsLocalPriorityDuringLease() {
        Fixture fixture = fixture();
        AgentCombatLocalTargetLeaseState state = fixture.entry.capabilityStates()
                .require(AgentCombatLocalTargetLeaseState.STATE_KEY);
        state.beginMapWideTravel(100, "", 44, 1_000, 25_000);
        state.observeRegion(100, "", 44, 2_000, 25_000, 3);

        for (int scan = 0; scan < 5; scan++) {
            assertEquals(List.of(fixture.localPreferred),
                    promote(fixture, List.of(fixture.localPreferred), 2_100 + scan));
        }
    }

    @Test
    void despawnedTravelTargetDoesNotBlockReplacementMapWideTarget() {
        Fixture fixture = fixture();
        AgentCombatLocalTargetLeaseState state = fixture.entry.capabilityStates()
                .require(AgentCombatLocalTargetLeaseState.STATE_KEY);
        state.beginMapWideTravel(100, "", 44, 1_000, 25_000);

        assertEquals(List.of(fixture.remotePreferred),
                promote(fixture, List.of(fixture.localFallback), 2_000));
    }

    @Test
    void periodicSearchReusesLivingRemoteTargetWhileTravelling() {
        Fixture fixture = fixture();
        AgentGrindTargetStateRuntime.setTarget(fixture.entry, fixture.remotePreferred);
        fixture.entry.capabilityStates().require(AgentCombatLocalTargetLeaseState.STATE_KEY)
                .beginMapWideTravel(100, "", 44, 1_000, 25_000);

        assertEquals(List.of(fixture.remotePreferred),
                promote(fixture, List.of(), 2_000));
    }

    @Test
    void preferredLocalRespawnCancelsRemoteTravelContinuation() {
        Fixture fixture = fixture();
        AgentGrindTargetStateRuntime.setTarget(fixture.entry, fixture.remotePreferred);
        AgentCombatLocalTargetLeaseState state = fixture.entry.capabilityStates()
                .require(AgentCombatLocalTargetLeaseState.STATE_KEY);
        state.beginMapWideTravel(100, "", 44, 1_000, 25_000);

        assertEquals(List.of(fixture.localPreferred),
                promote(fixture, List.of(fixture.localPreferred), 2_000));
        assertEquals(AgentCombatLocalTargetLeaseState.Phase.INACTIVE,
                state.snapshot(2_000).phase());
    }

    @Test
    void emptyMapWidePreferredPopulationPromotesSpawnPressureFallback() {
        Fixture fixture = fixture();
        AgentCombatDirectiveRuntime.assignPreferences(
                fixture.entry, Set.of(1), Set.of(2));
        when(fixture.agent.getMap().getAllMonsters())
                .thenReturn(List.of(fixture.localFallback));

        assertEquals(List.of(fixture.localFallback),
                promote(fixture, List.of(), 1_000));
    }

    private static Fixture fixture() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentCombatObjectiveTargetStateRuntime.setTargetPreferences(entry, Set.of(1), Set.of(2));
        Monster remotePreferred = monster(1, 1000);
        Monster localPreferred = monster(1, 10);
        Monster localFallback = monster(2, 20);
        MapleMap map = mock(MapleMap.class);
        when(map.getPerceptionSnapshot()).thenReturn(null);
        when(map.getAllMonsters()).thenReturn(List.of(remotePreferred));
        when(remotePreferred.getMap()).thenReturn(map);
        Character agent = mock(Character.class);
        when(agent.getMap()).thenReturn(map);
        when(agent.getMapId()).thenReturn(100);
        when(agent.getPosition()).thenReturn(new Point(0, 100));
        return new Fixture(entry, agent, remotePreferred, localPreferred, localFallback);
    }

    private static List<Monster> promote(Fixture fixture,
                                         List<Monster> localCandidates,
                                         long nowMs) {
        return AgentCombatTargetRuntime.promoteMapWidePreferredTargets(
                fixture.entry, fixture.agent, localCandidates, nowMs, target -> true);
    }

    private static Monster monster(int id, int x) {
        Monster monster = mock(Monster.class);
        when(monster.getId()).thenReturn(id);
        when(monster.isAlive()).thenReturn(true);
        when(monster.getPosition()).thenReturn(new Point(x, 100));
        return monster;
    }

    private record Fixture(AgentRuntimeEntry entry,
                           Character agent,
                           Monster remotePreferred,
                           Monster localPreferred,
                           Monster localFallback) {
    }
}
