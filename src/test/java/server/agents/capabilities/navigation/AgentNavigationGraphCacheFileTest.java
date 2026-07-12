package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import server.agents.capabilities.movement.AgentMovementProfile;

import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentNavigationGraphCacheFileTest {
    private static final int VERSION = 46;
    private static final int MAP_ID = 10000;
    private static final AgentMovementProfile PROFILE = AgentMovementProfile.base();

    @TempDir
    Path temporaryDirectory;

    @Test
    void atomicallyRoundTripsAllowedGraphTypes() throws Exception {
        Path file = temporaryDirectory.resolve("graph.bin");
        AgentNavigationGraph graph = new AgentNavigationGraph(
                MAP_ID, VERSION, PROFILE, List.of(), Map.of(), Map.of(), Map.of(), Set.of());

        AgentNavigationGraphCacheFile.write(file, graph);
        AgentNavigationGraph loaded = AgentNavigationGraphCacheFile.read(
                file, VERSION, MAP_ID, PROFILE);

        assertNotNull(loaded);
        assertEquals(MAP_ID, loaded.mapId);
        assertEquals(PROFILE, loaded.movementProfile);
        try (var files = Files.list(temporaryDirectory)) {
            assertEquals(List.of(file), files.toList());
        }
    }

    @Test
    void roundTripsGraphContainingRopeRegion() throws Exception {
        Path file = temporaryDirectory.resolve("rope-graph.bin");
        AgentNavigationGraph.Region rope = new AgentNavigationGraph.Region(1, 100, 50, 200, true);
        AgentNavigationGraph graph = new AgentNavigationGraph(
                MAP_ID, VERSION, PROFILE,
                List.of(rope), Map.of(rope.id, rope), Map.of(), Map.of(rope.id, List.of()), Set.of());

        AgentNavigationGraphCacheFile.write(file, graph);
        AgentNavigationGraph loaded = AgentNavigationGraphCacheFile.read(
                file, VERSION, MAP_ID, PROFILE);

        assertNotNull(loaded);
        assertEquals(1, loaded.regions.size());
        assertEquals(rope.id, loaded.regions.getFirst().id);
    }

    @Test
    void rejectsAndDeletesUnexpectedSerializedType() throws Exception {
        Path file = temporaryDirectory.resolve("unexpected.bin");
        try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(file))) {
            out.writeObject(new Date());
        }

        assertThrows(java.io.IOException.class,
                () -> AgentNavigationGraphCacheFile.read(file, VERSION, MAP_ID, PROFILE));
        assertFalse(Files.exists(file));
    }

    @Test
    void rejectsAndDeletesOversizedFileBeforeDeserialization() throws Exception {
        Path file = temporaryDirectory.resolve("oversized.bin");
        try (SeekableByteChannel channel = Files.newByteChannel(
                file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            channel.position(AgentNavigationGraphCacheFile.MAX_CACHE_FILE_BYTES);
            channel.write(ByteBuffer.wrap(new byte[]{0}));
        }

        assertNull(AgentNavigationGraphCacheFile.read(file, VERSION, MAP_ID, PROFILE));
        assertFalse(Files.exists(file));
    }
}
