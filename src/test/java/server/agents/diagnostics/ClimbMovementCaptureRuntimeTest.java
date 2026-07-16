package server.agents.diagnostics;

import client.Character;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Point;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClimbMovementCaptureRuntimeTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void boundsCaptureCopiesAndWritesNativeMovementReport() throws Exception {
        Character owner = character(910_001, "CaptureOwner", 1010000, new Point(10, 20), 0);
        Character target = character(910_002, "Climber", 1010000, new Point(432, 120), 16);
        String previousOutputDirectory = System.getProperty("agents.movement.capture.dir");
        System.setProperty("agents.movement.capture.dir", temporaryDirectory.toString());

        try {
            assertTrue(ClimbMovementCaptureRuntime.start(owner, target, 2).success());
            byte[] movement = absoluteRopeMovement();
            ClimbMovementCaptureRuntime.recordNative(target, movement);
            movement[2] = 99;
            ClimbMovementCaptureRuntime.recordNative(target, absoluteRopeMovement());
            ClimbMovementCaptureRuntime.recordNative(target, absoluteRopeMovement());

            ClimbMovementCaptureRuntime.Status status = ClimbMovementCaptureRuntime.status(owner);
            assertTrue(status.active());
            assertEquals(2, status.packetCount());
            assertTrue(status.limitReached());

            ClimbMovementCaptureRuntime.StopResult stopped = ClimbMovementCaptureRuntime.stop(owner);
            assertTrue(stopped.success());
            assertEquals(2, stopped.packetCount());
            assertNotNull(stopped.reportPath());
            assertTrue(Files.isRegularFile(stopped.reportPath()));

            String report = Files.readString(stopped.reportPath());
            assertTrue(report.contains("source=NATIVE_CLIENT"));
            assertTrue(report.contains("stance=16(rope-right)"));
            assertTrue(report.contains("raw: 01 00 64 00"), "capture must retain its own byte copy");
            assertFalse(ClimbMovementCaptureRuntime.status(owner).active());
        } finally {
            ClimbMovementCaptureRuntime.clear(owner);
            restoreProperty("agents.movement.capture.dir", previousOutputDirectory);
        }
    }

    @Test
    void clearDiscardsSyntheticCaptureWithoutWritingReport() throws Exception {
        Character owner = character(920_001, "CaptureOwner2", 1010100, new Point(), 0);
        Character target = character(920_002, "AgentTarget", 1010100, new Point(213, -745), 17);
        String previousOutputDirectory = System.getProperty("agents.movement.capture.dir");
        System.setProperty("agents.movement.capture.dir", temporaryDirectory.toString());

        try {
            assertTrue(ClimbMovementCaptureRuntime.start(owner, target, 10).success());
            ClimbMovementCaptureRuntime.recordSynthetic(target, absoluteRopeMovement());
            assertTrue(ClimbMovementCaptureRuntime.clear(owner).success());
            assertFalse(ClimbMovementCaptureRuntime.status(owner).active());
            try (var files = Files.list(temporaryDirectory)) {
                assertEquals(0, files.count());
            }
        } finally {
            ClimbMovementCaptureRuntime.clear(owner);
            restoreProperty("agents.movement.capture.dir", previousOutputDirectory);
        }
    }

    private static Character character(int id, String name, int mapId, Point position, int stance) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getName()).thenReturn(name);
        when(character.getMapId()).thenReturn(mapId);
        when(character.getPosition()).thenReturn(position);
        when(character.getStance()).thenReturn(stance);
        return character;
    }

    private static byte[] absoluteRopeMovement() {
        return new byte[]{
                1, 0,
                100, 0,
                56, (byte) 0xFF,
                3, 0,
                (byte) 0xFC, (byte) 0xFF,
                7, 0,
                16,
                120, 0
        };
    }

    private static void restoreProperty(String name, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, previousValue);
        }
    }
}
