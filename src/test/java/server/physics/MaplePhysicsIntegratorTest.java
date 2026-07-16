package server.physics;

import org.junit.jupiter.api.Test;
import server.physics.foothold.FootholdPhysicsIndex;
import server.physics.foothold.FootholdSegment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaplePhysicsIntegratorTest {
    private static final double EPSILON = 1.0e-9;
    private final MaplePhysicsIntegrator integrator = new MaplePhysicsIntegrator();

    @Test
    void groundForceAndFrictionMatchJourneyConstants() {
        PhysicsTerrain terrain = flatTerrain();
        PhysicsBody body = groundedBody(50.0, 100.0, 1);

        integrator.step(body, new PhysicsInput(0.1, 0.0, true, false), terrain);
        assertEquals(0.1, body.accelerationX(), EPSILON);
        assertEquals(0.1, body.velocityX(), EPSILON);
        assertEquals(50.1, body.x(), EPSILON);

        integrator.step(body, new PhysicsInput(0.0, 0.0, true, false), terrain);
        assertEquals(-0.013333333333333334, body.accelerationX(), EPSILON);
        assertEquals(0.08666666666666667, body.velocityX(), EPSILON);

        integrator.step(body, new PhysicsInput(0.0, 0.0, true, false), terrain);
        assertEquals(0.0, body.velocityX(), EPSILON);
    }

    @Test
    void gravityFallsAndLandsOnFoothold() {
        PhysicsTerrain terrain = flatTerrain();
        PhysicsBody body = new PhysicsBody(50.0, 95.0, PhysicsMode.NORMAL);
        body.setFoothold(1, 0.0, 1);
        body.setGrounded(false);

        PhysicsStepResult first = integrator.step(body, PhysicsInput.NONE, terrain);
        assertEquals(MaplePhysicsConstants.GRAVITY, body.velocityY(), EPSILON);
        assertFalse(first.landed());

        boolean landed = false;
        for (int i = 0; i < 20; i++) {
            PhysicsStepResult result = integrator.step(body, PhysicsInput.NONE, terrain);
            landed |= result.landed();
        }
        assertTrue(landed);
        assertTrue(body.grounded());
        assertEquals(100.0, body.y(), EPSILON);
        assertEquals(0.0, body.velocityY(), EPSILON);
    }

    @Test
    void connectedFootholdTransitionUpdatesIdAndSlope() {
        FootholdSegment first = segment(1, 0, 2, 0, 100, 100, 100);
        FootholdSegment second = segment(2, 1, 0, 100, 100, 200, 80);
        PhysicsTerrain terrain = new FootholdPhysicsIndex(List.of(first, second));
        PhysicsBody body = groundedBody(99.5, 100.0, 1);
        body.setVelocity(2.0, 0.0);

        integrator.step(body, PhysicsInput.NONE, terrain);
        PhysicsStepResult transition = integrator.step(body, PhysicsInput.NONE, terrain);

        assertTrue(transition.changedFoothold());
        assertEquals(2, body.footholdId());
        assertEquals(-0.2, body.footholdSlope(), EPSILON);
    }

    @Test
    void wallCollisionStopsHorizontalVelocity() {
        FootholdSegment floor = segment(1, 0, 2, 0, 100, 100, 100);
        FootholdSegment wall = segment(2, 1, 0, 100, 50, 100, 100);
        PhysicsTerrain terrain = new FootholdPhysicsIndex(List.of(floor, wall));
        PhysicsBody body = groundedBody(99.0, 100.0, 1);
        body.setVelocity(2.0, 0.0);

        PhysicsStepResult result = integrator.step(body, PhysicsInput.NONE, terrain);

        assertTrue(result.hitWall());
        assertFalse(result.reachedEdge());
        assertEquals(100.0, body.x(), EPSILON);
        assertEquals(0.0, body.velocityX(), EPSILON);
    }

    @Test
    void edgeCollisionStopsWhenTurnAtEdgesIsRequested() {
        PhysicsTerrain terrain = flatTerrain();
        PhysicsBody body = groundedBody(99.0, 100.0, 1);
        body.setVelocity(2.0, 0.0);

        PhysicsStepResult result = integrator.step(
                body, new PhysicsInput(0.0, 0.0, true, false), terrain);

        assertTrue(result.reachedEdge());
        assertEquals(100.0, body.x(), EPSILON);
        assertEquals(0.0, body.velocityX(), EPSILON);
    }

    @Test
    void asymmetricInsetsKeepFeetInsideLeftAndRightEdges() {
        PhysicsTerrain terrain = flatTerrain();
        PhysicsBody left = groundedBody(5.0, 100.0, 1);
        left.setVelocity(-2.0, 0.0);
        PhysicsStepResult leftResult = integrator.step(left,
                new PhysicsInput(0.0, 0.0, true, false, 4.0, 7.0), terrain);
        assertTrue(leftResult.reachedEdge());
        assertEquals(4.0, left.x(), EPSILON);

        PhysicsBody right = groundedBody(95.0, 100.0, 1);
        right.setVelocity(2.0, 0.0);
        PhysicsStepResult rightResult = integrator.step(right,
                new PhysicsInput(0.0, 0.0, true, false, 4.0, 7.0), terrain);
        assertTrue(rightResult.reachedEdge());
        assertEquals(93.0, right.x(), EPSILON);
    }

    @Test
    void flyingAndSwimmingUseTheirReferenceFriction() {
        PhysicsTerrain terrain = flatTerrain();
        PhysicsBody flying = new PhysicsBody(50.0, 50.0, PhysicsMode.FLYING);
        flying.setVelocity(1.0, -1.0);
        integrator.step(flying, PhysicsInput.NONE, terrain);
        assertEquals(0.95, flying.velocityX(), EPSILON);
        assertEquals(-0.95, flying.velocityY(), EPSILON);

        PhysicsBody swimming = new PhysicsBody(50.0, 50.0, PhysicsMode.SWIMMING);
        swimming.setVelocity(1.0, -1.0);
        integrator.step(swimming, PhysicsInput.NONE, terrain);
        assertEquals(0.92, swimming.velocityX(), EPSILON);
        assertEquals(-0.89, swimming.velocityY(), EPSILON);
    }

    @Test
    void fixedDoesNotAccelerateAndIceIsExplicitlyUnsupported() {
        PhysicsTerrain terrain = flatTerrain();
        PhysicsBody fixed = groundedBody(50.0, 100.0, 1);
        fixed.setMode(PhysicsMode.FIXED);
        fixed.setVelocity(3.0, -2.0);
        PhysicsStepResult fixedResult = integrator.step(
                fixed, new PhysicsInput(5.0, -5.0, false, false), terrain);
        assertFalse(fixedResult.unsupportedMode());
        assertEquals(50.0, fixed.x(), EPSILON);

        PhysicsBody ice = groundedBody(50.0, 100.0, 1);
        ice.setMode(PhysicsMode.ICE);
        PhysicsStepResult iceResult = integrator.step(ice, PhysicsInput.NONE, terrain);
        assertTrue(iceResult.unsupportedMode());
    }

    @Test
    void uphillAndDownhillUsePreciseSlopeState() {
        PhysicsTerrain uphill = new FootholdPhysicsIndex(List.of(
                segment(1, 0, 0, 0, 100, 100, 80)));
        PhysicsBody up = new PhysicsBody(25.0, 95.0, PhysicsMode.NORMAL);
        up.setFoothold(1, -0.2, 1);
        up.setGrounded(true);
        integrator.step(up, new PhysicsInput(0.1, 0.0, true, false), uphill);
        assertTrue(up.x() > 25.0);
        assertEquals(uphill.foothold(1).groundY(up.x()), up.y(), EPSILON);
        assertEquals(-0.2, up.footholdSlope(), EPSILON);

        for (int step = 0; step < 30; step++) {
            integrator.step(up, new PhysicsInput(0.2, 0.0, false, false), uphill);
            assertEquals(uphill.foothold(1).groundY(up.x()), up.y(), EPSILON,
                    "grounded uphill motion must not penetrate the foothold");
        }

        PhysicsTerrain downhill = new FootholdPhysicsIndex(List.of(
                segment(1, 0, 0, 0, 80, 100, 100)));
        PhysicsBody down = new PhysicsBody(25.0, 85.0, PhysicsMode.NORMAL);
        down.setFoothold(1, 0.2, 1);
        down.setGrounded(true);
        integrator.step(down, new PhysicsInput(0.1, 0.0, true, false), downhill);
        assertTrue(down.x() > 25.0);
        assertEquals(downhill.foothold(1).groundY(down.x()), down.y(), EPSILON);
        assertEquals(0.2, down.footholdSlope(), EPSILON);
    }

    @Test
    void jumpLeavesGroundThenReturnsToPlatform() {
        PhysicsTerrain terrain = flatTerrain();
        PhysicsBody body = groundedBody(50.0, 100.0, 1);
        integrator.step(body, new PhysicsInput(0.05, -5.0, false, false), terrain);
        assertTrue(body.velocityY() < 0.0);
        boolean left = false;
        boolean landed = false;
        for (int i = 0; i < 100; i++) {
            PhysicsStepResult result = integrator.step(body, PhysicsInput.NONE, terrain);
            left |= result.leftGround();
            landed |= result.landed();
        }
        assertTrue(left);
        assertTrue(landed);
        assertEquals(100.0, body.y(), EPSILON);
    }

    @Test
    void flyingBodyIsConstrainedByMapBounds() {
        PhysicsTerrain terrain = flatTerrain();
        PhysicsBody body = new PhysicsBody(74.5, -199.5, PhysicsMode.FLYING);
        body.setVelocity(20.0, -20.0);
        integrator.step(body, PhysicsInput.NONE, terrain);
        assertTrue(body.x() <= terrain.bounds().right());
        assertTrue(body.y() >= terrain.bounds().top());
    }

    @Test
    void invalidStateRecoversToLastFoothold() {
        PhysicsTerrain terrain = flatTerrain();
        PhysicsBody body = groundedBody(50.0, 100.0, 1);
        body.setPosition(Double.NaN, Double.POSITIVE_INFINITY);
        body.setVelocity(Double.NaN, 2.0);

        PhysicsStepResult result = integrator.step(body, PhysicsInput.NONE, terrain);

        assertTrue(result.recovered());
        assertTrue(Double.isFinite(body.x()));
        assertTrue(Double.isFinite(body.y()));
        assertEquals(1, body.footholdId());
    }

    private static PhysicsBody groundedBody(double x, double y, int footholdId) {
        PhysicsBody body = new PhysicsBody(x, y, PhysicsMode.NORMAL);
        body.setFoothold(footholdId, 0.0, 1);
        body.setGrounded(true);
        return body;
    }

    private static PhysicsTerrain flatTerrain() {
        return new FootholdPhysicsIndex(List.of(segment(1, 0, 0, 0, 100, 100, 100)));
    }

    private static FootholdSegment segment(int id, int previous, int next,
                                           double x1, double y1, double x2, double y2) {
        return new FootholdSegment(id, previous, next, 1, 0,
                false, x1, y1, x2, y2);
    }
}
