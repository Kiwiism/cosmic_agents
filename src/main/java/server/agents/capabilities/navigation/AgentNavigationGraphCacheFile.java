package server.agents.capabilities.navigation;

import server.agents.capabilities.movement.AgentMovementProfile;

import java.awt.Point;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

final class AgentNavigationGraphCacheFile {
    static final long MAX_CACHE_FILE_BYTES = 64L * 1024L * 1024L;
    private static final long MAX_GRAPH_REFERENCES = 500_000L;
    private static final long MAX_GRAPH_ARRAY_LENGTH = 500_000L;
    private static final long MAX_GRAPH_DEPTH = 64L;
    private static final Set<Class<?>> ALLOWED_CLASSES = Set.of(
            AgentNavigationGraph.class,
            AgentNavigationGraph.Region.class,
            AgentNavigationGraph.Segment.class,
            AgentNavigationGraph.Edge.class,
            AgentNavigationGraph.EdgeType.class,
            AgentMovementProfile.class,
            Point.class,
            ArrayList.class,
            HashMap.class,
            HashSet.class,
            Integer.class,
            Number.class,
            Enum.class);

    private AgentNavigationGraphCacheFile() {
    }

    static AgentNavigationGraph read(Path file,
                                     int expectedVersion,
                                     int expectedMapId,
                                     AgentMovementProfile expectedProfile)
            throws IOException, ClassNotFoundException {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        if (Files.size(file) > MAX_CACHE_FILE_BYTES) {
            Files.deleteIfExists(file);
            return null;
        }

        try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(file))) {
            in.setObjectInputFilter(AgentNavigationGraphCacheFile::filterInput);
            Object loaded = in.readObject();
            if (!(loaded instanceof AgentNavigationGraph graph)
                    || graph.version != expectedVersion
                    || graph.mapId != expectedMapId
                    || graph.movementProfile == null
                    || graph.movementProfile.totalSpeedStat() != expectedProfile.totalSpeedStat()
                    || graph.movementProfile.totalJumpStat() != expectedProfile.totalJumpStat()) {
                Files.deleteIfExists(file);
                return null;
            }
            return graph;
        } catch (IOException | ClassNotFoundException failure) {
            Files.deleteIfExists(file);
            throw failure;
        }
    }

    static void write(Path file, AgentNavigationGraph graph) throws IOException {
        Path directory = file.getParent();
        Files.createDirectories(directory);
        Path temporaryFile = Files.createTempFile(directory, file.getFileName().toString(), ".tmp");
        try {
            try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(temporaryFile))) {
                out.writeObject(graph);
            }
            try {
                Files.move(temporaryFile, file,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporaryFile, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private static ObjectInputFilter.Status filterInput(ObjectInputFilter.FilterInfo info) {
        if (info.depth() > MAX_GRAPH_DEPTH
                || info.references() > MAX_GRAPH_REFERENCES
                || info.arrayLength() > MAX_GRAPH_ARRAY_LENGTH
                || info.streamBytes() > MAX_CACHE_FILE_BYTES) {
            return ObjectInputFilter.Status.REJECTED;
        }

        Class<?> serialClass = info.serialClass();
        if (serialClass == null) {
            return ObjectInputFilter.Status.UNDECIDED;
        }
        if (serialClass.isPrimitive() || ALLOWED_CLASSES.contains(serialClass)) {
            return ObjectInputFilter.Status.ALLOWED;
        }
        if (serialClass.isArray()) {
            Class<?> componentType = serialClass.getComponentType();
            if (componentType.isPrimitive()
                    || componentType == Object.class
                    || ALLOWED_CLASSES.contains(componentType)
                    || componentType.getName().equals("java.util.Map$Entry")) {
                return ObjectInputFilter.Status.ALLOWED;
            }
        }
        return ObjectInputFilter.Status.REJECTED;
    }
}
