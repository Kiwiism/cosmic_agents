package server.agents.integration;

import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.runtime.AgentFormationService;

import server.agents.capabilities.navigation.AgentNavigationMapLoader;

import server.maps.MapleMap;
import server.maps.Rope;

import java.awt.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class BotMovementSimulationCli {
    private BotMovementSimulationCli() {
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }

        if (System.getProperty("wz-path") == null) {
            System.setProperty("wz-path", Path.of("wz").toAbsolutePath().toString());
        }

        int mapId = Integer.parseInt(args[0]);
        MapleMap map = AgentNavigationMapLoader.loadMapGeometry(mapId);
        BotMovementSimulationLab lab = BotMovementSimulationLab.fromMap(map);
        int nextId = 1;
        int ticks = 100;
        List<String> traceRequests = new ArrayList<>();

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--bot" -> {
                    SpawnSpec spec = parseSpawn(nextArg(args, ++i, "--bot"));
                    lab.spawnBot(spec.name(), nextId++, map, spec.position());
                }
                case "--owner" -> {
                    SpawnSpec spec = parseSpawn(nextArg(args, ++i, "--owner"));
                    lab.spawnActor(spec.name(), nextId++, map, spec.position());
                }
                case "--follow" -> {
                    String[] parts = nextArg(args, ++i, "--follow").split(":", 2);
                    if (parts.length != 2) {
                        throw new IllegalArgumentException("Expected --follow bot:owner");
                    }
                    lab.setFollow(parts[0].trim(), parts[1].trim());
                }
                case "--move" -> {
                    MoveSpec spec = parseMove(nextArg(args, ++i, "--move"));
                    lab.setMoveTarget(spec.botName(), spec.position(), spec.precise());
                }
                case "--formation" -> {
                    FormationSpec spec = parseFormation(nextArg(args, ++i, "--formation"));
                    lab.setFormation(spec.ownerName(), spec.type(), spec.px(), spec.snapRange());
                }
                case "--follow-offset" -> {
                    OffsetSpec spec = parseOffset(nextArg(args, ++i, "--follow-offset"));
                    lab.setFollowOffset(spec.botName(), spec.offsetX());
                }
                case "--ai-accum" -> {
                    AccumulatorSpec spec = parseAccumulator(nextArg(args, ++i, "--ai-accum"));
                    lab.setAiAccumulator(spec.botName(), spec.accumulatorMs());
                }
                case "--prime-map" -> lab.primeMapState(nextArg(args, ++i, "--prime-map").trim());
                case "--attach-rope" -> {
                    RopeAttachSpec spec = parseAttachRope(nextArg(args, ++i, "--attach-rope"));
                    lab.attachBotToRope(spec.botName(),
                            new Rope(spec.ropeX(), spec.topY(), spec.bottomY(), spec.ladder()),
                            spec.attachY());
                }
                case "--nav-edge" -> {
                    NavEdgeSpec spec = parseNavEdge(nextArg(args, ++i, "--nav-edge"));
                    lab.setNavState(spec.botName(), spec.edge(), spec.targetRegionId(), spec.precise());
                }
                case "--ticks" -> ticks = Integer.parseInt(nextArg(args, ++i, "--ticks"));
                case "--trace" -> traceRequests.add(nextArg(args, ++i, "--trace"));
                default -> throw new IllegalArgumentException("Unknown arg: " + arg);
            }
        }

        lab.step(ticks);
        if (traceRequests.isEmpty()) {
            for (String botName : lab.botNames()) {
                System.out.println(lab.describeCurrentState(botName));
            }
            return;
        }

        for (String request : traceRequests) {
            TraceSpec traceSpec = parseTrace(request);
            System.out.println("=== " + traceSpec.botName() + " ===");
            System.out.println(lab.describeCurrentState(traceSpec.botName()));
            for (String line : lab.formatRecentTrace(traceSpec.botName(), traceSpec.limit())) {
                System.out.println(line);
            }
        }
    }

    private static void printUsage() {
        System.out.println("Usage: BotMovementSimulationCli <mapId> [commands]");
        System.out.println("  --bot name:x,y");
        System.out.println("  --owner name:x,y");
        System.out.println("  --follow bot:owner");
        System.out.println("  --move bot:x,y[:precise]");
        System.out.println("  --formation owner:type:px:snap");
        System.out.println("  --follow-offset bot:offsetX");
        System.out.println("  --ai-accum bot:ms");
        System.out.println("  --prime-map bot");
        System.out.println("  --attach-rope bot:x,top,bottom,attachY[,ladder]");
        System.out.println("  --nav-edge bot:from:to:type:startX,startY:endX,endY:stepX:targetRegion:cost[:ropeX,ropeTopY,ropeBottomY][:precise]");
        System.out.println("  --ticks n");
        System.out.println("  --trace bot[:limit]");
    }

    private static String nextArg(String[] args, int index, String flag) {
        if (index >= args.length) {
            throw new IllegalArgumentException(flag + " requires a value");
        }
        return args[index];
    }

    private static SpawnSpec parseSpawn(String raw) {
        String[] parts = raw.split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Expected spawn as name:x,y");
        }
        return new SpawnSpec(parts[0].trim(), parsePoint(parts[1]));
    }

    private static MoveSpec parseMove(String raw) {
        String[] parts = raw.split(":", 3);
        if (parts.length < 2 || parts.length > 3) {
            throw new IllegalArgumentException("Expected move as bot:x,y[:precise]");
        }
        return new MoveSpec(
                parts[0].trim(),
                parsePoint(parts[1].trim()),
                parts.length == 3 && Boolean.parseBoolean(parts[2].trim()));
    }

    private static FormationSpec parseFormation(String raw) {
        String[] parts = raw.split(":");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Expected formation as owner:type:px:snap");
        }
        return new FormationSpec(
                parts[0].trim(),
                AgentFormationService.FormationType.valueOf(parts[1].trim().toUpperCase()),
                Integer.parseInt(parts[2].trim()),
                Integer.parseInt(parts[3].trim()));
    }

    private static OffsetSpec parseOffset(String raw) {
        String[] parts = raw.split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Expected follow offset as bot:offsetX");
        }
        return new OffsetSpec(parts[0].trim(), Integer.parseInt(parts[1].trim()));
    }

    private static AccumulatorSpec parseAccumulator(String raw) {
        String[] parts = raw.split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Expected AI accumulator as bot:ms");
        }
        return new AccumulatorSpec(parts[0].trim(), Integer.parseInt(parts[1].trim()));
    }

    private static RopeAttachSpec parseAttachRope(String raw) {
        String[] parts = raw.split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Expected rope attach as bot:x,top,bottom,attachY[,ladder]");
        }
        String[] coords = parts[1].split(",");
        if (coords.length < 4 || coords.length > 5) {
            throw new IllegalArgumentException("Expected rope coords as x,top,bottom,attachY[,ladder]");
        }
        return new RopeAttachSpec(
                parts[0].trim(),
                Integer.parseInt(coords[0].trim()),
                Integer.parseInt(coords[1].trim()),
                Integer.parseInt(coords[2].trim()),
                Integer.parseInt(coords[3].trim()),
                coords.length == 5 && Boolean.parseBoolean(coords[4].trim()));
    }

    private static NavEdgeSpec parseNavEdge(String raw) {
        String[] parts = raw.split(":");
        if (parts.length < 9 || parts.length > 11) {
            throw new IllegalArgumentException("Expected nav edge as bot:from:to:type:startX,startY:endX,endY:stepX:targetRegion:cost[:ropeX,ropeTopY,ropeBottomY][:precise]");
        }
        String botName = parts[0].trim();
        int fromRegionId = Integer.parseInt(parts[1].trim());
        int toRegionId = Integer.parseInt(parts[2].trim());
        AgentNavigationGraph.EdgeType type = AgentNavigationGraph.EdgeType.valueOf(parts[3].trim().toUpperCase());
        Point startPoint = parsePoint(parts[4].trim());
        Point endPoint = parsePoint(parts[5].trim());
        int stepX = Integer.parseInt(parts[6].trim());
        int targetRegionId = Integer.parseInt(parts[7].trim());
        int cost = Integer.parseInt(parts[8].trim());
        int ropeX = 0;
        int ropeTopY = 0;
        int ropeBottomY = 0;
        int nextIndex = 9;
        if (parts.length > nextIndex && parts[nextIndex].contains(",")) {
            String[] ropeParts = parts[nextIndex].split(",", 3);
            if (ropeParts.length != 3) {
                throw new IllegalArgumentException("Expected rope data as ropeX,ropeTopY,ropeBottomY");
            }
            ropeX = Integer.parseInt(ropeParts[0].trim());
            ropeTopY = Integer.parseInt(ropeParts[1].trim());
            ropeBottomY = Integer.parseInt(ropeParts[2].trim());
            nextIndex++;
        }
        boolean precise = parts.length > nextIndex && Boolean.parseBoolean(parts[nextIndex].trim());
        AgentNavigationGraph.Edge edge = new AgentNavigationGraph.Edge(
                fromRegionId, toRegionId, type, startPoint, endPoint,
                stepX, 0, ropeX, ropeTopY, ropeBottomY, cost);
        return new NavEdgeSpec(botName, edge, targetRegionId, precise);
    }

    private static TraceSpec parseTrace(String raw) {
        String[] parts = raw.split(":", 2);
        return new TraceSpec(parts[0].trim(), parts.length == 2 ? Integer.parseInt(parts[1].trim()) : 40);
    }

    private static Point parsePoint(String raw) {
        String[] parts = raw.split(",", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Expected point as x,y");
        }
        return new Point(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
    }

    private record SpawnSpec(String name, Point position) {
    }

    private record MoveSpec(String botName, Point position, boolean precise) {
    }

    private record FormationSpec(String ownerName, AgentFormationService.FormationType type, int px, int snapRange) {
    }

    private record OffsetSpec(String botName, int offsetX) {
    }

    private record AccumulatorSpec(String botName, int accumulatorMs) {
    }

    private record RopeAttachSpec(String botName, int ropeX, int topY, int bottomY, int attachY, boolean ladder) {
    }

    private record NavEdgeSpec(String botName, AgentNavigationGraph.Edge edge, int targetRegionId, boolean precise) {
    }

    private record TraceSpec(String botName, int limit) {
    }
}
