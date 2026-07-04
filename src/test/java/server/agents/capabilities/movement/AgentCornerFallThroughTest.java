package server.agents.capabilities.movement;

import client.Character;
import constants.game.CharacterStance;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.navigation.AgentNavigationMapLoader;
import server.agents.integration.AgentBotMovementPhysicsStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.bots.BotEntry;
import server.maps.Foothold;
import server.maps.MapleMap;

import java.awt.Point;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression for Agent falling through map edges at a wall/floor corner.
 * Real-map case: map 261020500 (Lab Area C-3), corner near (-712, 167).
 */
class AgentCornerFallThroughTest {
    private static final int MAP_ID = 261020500;
    private static final Point CORNER = new Point(-712, 167);

    @BeforeAll
    static void initWzPath() {
        System.setProperty("wz-path", Path.of("wz").toAbsolutePath().toString());
    }

    @Test
    void shouldNotFallThroughCornerUnderAnyKnockback() {
        MapleMap map = AgentNavigationMapLoader.loadMapGeometry(MAP_ID);
        AgentNavigationGraphService.rebuildGraph(map);

        List<String> failures = new ArrayList<>();
        int[][] scenarios = {
                {-711, -9, -22},
                {-712, -9, -22},
                {-712, -1, 0},
                {-711, -20, -22},
                {-705, -9, -22},
                {-712, 0, 0},
        };
        for (int[] scenario : scenarios) {
            String result = simulateKnock(map, scenario[0], scenario[1], (float) scenario[2]);
            if (result.contains("FELL-THROUGH")) {
                failures.add(result);
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(failures.isEmpty(),
                "Agent fell through the world in " + failures.size() + " scenario(s):\n"
                        + String.join("\n", failures));
    }

    private String simulateKnock(MapleMap map, int startX, int airVelocityX, float velocityY) {
        Point start = new Point(startX, CORNER.y);
        Character agent = mockAgent(start, map);
        BotEntry entry = new BotEntry(agent, null, null);
        AgentMovementPoseService.resetMotion(entry, agent.getPosition());
        AgentBotMovementStateRuntime.setFacingDirection(entry, airVelocityX < 0 ? -1 : 1);
        if (airVelocityX == 0 && velocityY == 0f) {
            AgentBotMovementStateRuntime.setInAir(entry, true);
            AgentBotMovementPhysicsStateRuntime.setPhysicsPosition(entry, start);
            AgentBotMovementPhysicsStateRuntime.setAirVelocityX(entry, 0);
        } else {
            AgentKnockbackMovementService.beginKnockback(entry, agent, agent.getPosition(), velocityY, airVelocityX);
        }

        int fallThroughY = CORNER.y + 600;
        boolean tunneled = false;
        Point last = start;
        for (int tick = 0; tick < 800; tick++) {
            if (!AgentBotMovementStateRuntime.inAir(entry)) {
                break;
            }
            AgentMotionTimerService.tickMotionTimers(entry);
            AgentAirbornePhysicsService.stepAirborne(entry, agent);
            last = agent.getPosition();
            if (last.y > fallThroughY) {
                tunneled = true;
                break;
            }
        }
        Point end = agent.getPosition();
        Foothold endFoothold = AgentGroundingService.findGroundFoothold(map, end);
        return String.format("start=(%d,%d) vX=%d vY=%.0f -> end=%s inAir=%b fh=%s %s",
                startX, CORNER.y, airVelocityX, velocityY, str(end), AgentBotMovementStateRuntime.inAir(entry),
                endFoothold == null ? "null" : "#" + endFoothold.getId(),
                tunneled ? "*** FELL-THROUGH ***" : "ok");
    }

    private static String str(Point point) {
        return "(" + point.x + "," + point.y + ")";
    }

    private static Character mockAgent(Point startPosition, MapleMap map) {
        Character agent = mock(Character.class);
        AtomicReference<Point> position = new AtomicReference<>(new Point(startPosition));
        AtomicInteger stance = new AtomicInteger(CharacterStance.STAND_RIGHT_STANCE);
        when(agent.getPosition()).thenAnswer(invocation -> new Point(position.get()));
        doAnswer(invocation -> {
            position.set(new Point(invocation.getArgument(0)));
            return null;
        }).when(agent).setPosition(any(Point.class));
        when(agent.getMap()).thenReturn(map);
        when(agent.getHp()).thenReturn(100);
        when(agent.getTotalMoveSpeedStat()).thenReturn(100);
        when(agent.getTotalJumpStat()).thenReturn(100);
        when(agent.getStance()).thenAnswer(invocation -> stance.get());
        doAnswer(invocation -> {
            stance.set(invocation.getArgument(0));
            return null;
        }).when(agent).setStance(anyInt());
        return agent;
    }
}
