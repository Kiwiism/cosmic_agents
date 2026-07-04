package server.bots;

import server.agents.capabilities.movement.AgentMovementProfile;
import server.agents.capabilities.movement.AgentMovementKinematicsService;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotMovementReportRuntime;
import server.maps.MapleMap;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentBotMovementReportRuntimeTest {
    @Test
    void movementStatsReportUsesBotMovementAndPhysicsData() {
        Character bot = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        when(bot.getMap()).thenReturn(map);
        when(bot.getTotalMoveSpeedStat()).thenReturn(120);
        when(bot.getTotalJumpStat()).thenReturn(110);
        AgentMovementProfile profile = AgentMovementProfile.fromCharacter(bot);

        List<String> report = AgentBotMovementReportRuntime.movementStatsReport(bot);

        assertEquals(List.of(
                "speed 120% jump 110%",
                String.format(Locale.ROOT, "walk %.1f px/s, %d px/tick, climb %d, hforce %.1f",
                        profile.walkVelocityPxs(),
                        AgentMovementKinematicsService.walkStep(map, profile),
                        AgentMovementKinematicsService.climbStepPerTick(),
                        profile.hForcePxs()),
                String.format(Locale.ROOT, "jump %.1f, rope %.1f, max %.1f px, reach %d/%d px",
                        AgentMovementKinematicsService.jumpForcePerTick(profile),
                        AgentMovementKinematicsService.ropeJumpForcePerTick(profile),
                        AgentMovementKinematicsService.calculateMaxJumpHeight(profile),
                        AgentMovementKinematicsService.maxJumpHorizontalTravel(map, profile),
                        AgentMovementKinematicsService.maxRopeJumpHorizontalTravel(map, profile))
        ), report);
    }
}
