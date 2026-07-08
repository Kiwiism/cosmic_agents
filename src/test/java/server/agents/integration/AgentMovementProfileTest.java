package server.agents.integration;

import server.agents.capabilities.navigation.AgentNavigationMapLoader;

import server.agents.capabilities.movement.AgentMovementProfile;

import client.Character;
import org.junit.jupiter.api.Test;
import server.maps.FieldLimit;
import server.maps.MapleMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentMovementProfileTest {
    @Test
    void shouldBucketStatsDownToNearestFivePointStep() {
        AgentMovementProfile profile = new AgentMovementProfile(109, 117);

        assertEquals(105, profile.totalSpeedStat());
        assertEquals(115, profile.totalJumpStat());
    }

    @Test
    void shouldLeaveExactFivePointBucketsUnchanged() {
        AgentMovementProfile profile = new AgentMovementProfile(105, 120);

        assertEquals(105, profile.totalSpeedStat());
        assertEquals(120, profile.totalJumpStat());
    }

    @Test
    void shouldCapEffectivePhysicsStats() {
        AgentMovementProfile profile = new AgentMovementProfile(240, 130);

        assertEquals(200, profile.totalSpeedStat());
        assertEquals(123, profile.totalJumpStat());
    }

    @Test
    void shouldUseBaseStatsWhenMapForcesMovementSkillLimit() {
        MapleMap map = new MapleMap(100000202, 0, 0, 100000000, 1.0f);
        map.setFieldLimit((int) FieldLimit.MOVEMENTSKILLS.getValue());
        Character character = mock(Character.class);
        when(character.getMap()).thenReturn(map);
        when(character.getTotalMoveSpeedStat()).thenReturn(140);
        when(character.getTotalJumpStat()).thenReturn(123);

        AgentMovementProfile profile = AgentMovementProfile.fromCharacter(character);

        assertEquals(AgentMovementProfile.base(), profile);
    }

    @Test
    void shouldLoadPetWalkingRoadMovementSkillLimit() {
        MapleMap map = AgentNavigationMapLoader.loadMapGeometry(100000202);

        assertTrue(FieldLimit.MOVEMENTSKILLS.check(map.getFieldLimit()));
    }
}
