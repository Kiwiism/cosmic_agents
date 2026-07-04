package server.bots;

import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.movement.AgentMovementPoseService;

import server.agents.capabilities.navigation.AgentNavigationGraph;

import server.agents.capabilities.navigation.AgentNavigationMapLoader;

import client.Character;
import constants.game.CharacterStance;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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
 * Regression for "bot falls through map edges at a wall/floor corner".
 * Real-map case: map 261020500 (Lab Area C-3), corner near (-712, 167).
 *
 * Root cause: floors fh#12/fh#13 extend to minDropX = -712, but the map is not "synthetic"
 * so the airborne side-collision boundary was mapArea.x = -711 -- one pixel right of the floor
 * tip. A bot pushed onto that overhanging pixel (e.g. by mob knockback) sat at x < boundaryX,
 * where {@code mapSideBoundaryCollision} early-returns, so a further leftward step was never
 * blocked; no foothold exists at any depth for x < -712, so the bot fell through the world.
 *
 * Fix: {@code effectiveLeftBoundaryX}/{@code effectiveRightBoundaryX} now extend a real-bounds
 * map's collision boundary to cover the full foothold extent, so no walkable pixel sits outside
 * the side wall.
 */
class BotCornerFallThroughTest {

    private static final int MAP_ID = 261020500;
    private static final Point CORNER = new Point(-712, 167);

    @BeforeAll
    static void initWzPath() {
        System.setProperty("wz-path", Path.of("wz").toAbsolutePath().toString());
    }

    /** Diagnostic only: dump the footholds around the reported corner so we can see the geometry. */
    /**
     * Sweep plausible in-game triggers at the left ledge tip and report which ones drop the
     * bot into the floor-less void left of the map boundary. Asserts NONE do.
     */
    @Test
    void shouldNotFallThroughCornerUnderAnyKnockback() {
        MapleMap map = AgentNavigationMapLoader.loadMapGeometry(MAP_ID);
        AgentNavigationGraphService.rebuildGraph(map);

        List<String> failures = new ArrayList<>();
        // startX, airVelX (knock dir*mag), velY (negative = up)
        int[][] scenarios = {
                {-711, -9, -22},   // standing at boundary, knocked left by mob from right
                {-712, -9, -22},   // standing on the exposed tip pixel, knocked left
                {-712, -1, 0},     // on the tip, tiny leftward nudge, no upward pop
                {-711, -20, -22},  // boundary, strong leftward knock
                {-705, -9, -22},   // a few px in, knocked left
                {-712, 0, 0},      // on the tip, just begin a plain fall (no horizontal)
        };
        for (int[] s : scenarios) {
            String r = simulateKnock(map, s[0], s[1], (float) s[2]);
            System.out.println(r);
            if (r.contains("FELL-THROUGH")) failures.add(r);
        }
        org.junit.jupiter.api.Assertions.assertTrue(failures.isEmpty(),
                "bot fell through the world in " + failures.size() + " scenario(s):\n"
                        + String.join("\n", failures));
    }

    private String simulateKnock(MapleMap map, int startX, int airVelX, float velY) {
        Point start = new Point(startX, CORNER.y);
        Character bot = mockBot(start, map);
        BotEntry entry = new BotEntry(bot, null, null);
        AgentMovementPoseService.resetMotion(entry, bot.getPosition());
        entry.facingDir = airVelX < 0 ? -1 : 1;
        if (airVelX == 0 && velY == 0f) {
            // plain fall off the tip
            entry.inAir = true;
            entry.physX = start.x;
            entry.physY = start.y;
            entry.airVelX = 0;
        } else {
            BotPhysicsEngine.beginKnockback(entry, bot, bot.getPosition(), velY, airVelX);
        }

        int fallThroughY = CORNER.y + 600; // below fh#13 (y=471) and anything legitimate
        boolean tunneled = false;
        Point last = start;
        for (int tick = 0; tick < 800; tick++) {
            if (!entry.inAir) break;
            BotPhysicsEngine.tickMotionTimers(entry);
            BotPhysicsEngine.stepAirborne(entry, bot);
            last = bot.getPosition();
            if (last.y > fallThroughY) { tunneled = true; break; }
        }
        Point end = bot.getPosition();
        Foothold endFh = BotPhysicsEngine.findGroundFoothold(map, end);
        return String.format("start=(%d,%d) vX=%d vY=%.0f -> end=%s inAir=%b fh=%s %s",
                startX, CORNER.y, airVelX, velY, str(end), entry.inAir,
                endFh == null ? "null" : "#" + endFh.getId(),
                tunneled ? "*** FELL-THROUGH ***" : "ok");
    }

    private static String str(Point p) {
        return "(" + p.x + "," + p.y + ")";
    }

    private static Character mockBot(Point startPosition, MapleMap map) {
        Character bot = mock(Character.class);
        AtomicReference<Point> position = new AtomicReference<>(new Point(startPosition));
        AtomicInteger stance = new AtomicInteger(CharacterStance.STAND_RIGHT_STANCE);
        when(bot.getPosition()).thenAnswer(invocation -> new Point(position.get()));
        doAnswer(invocation -> {
            position.set(new Point(invocation.getArgument(0)));
            return null;
        }).when(bot).setPosition(any(Point.class));
        when(bot.getMap()).thenReturn(map);
        when(bot.getHp()).thenReturn(100);
        when(bot.getTotalMoveSpeedStat()).thenReturn(100);
        when(bot.getTotalJumpStat()).thenReturn(100);
        when(bot.getStance()).thenAnswer(invocation -> stance.get());
        doAnswer(invocation -> {
            stance.set(invocation.getArgument(0));
            return null;
        }).when(bot).setStance(anyInt());
        return bot;
    }
}
