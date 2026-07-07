package server.agents.capabilities.navigation;

import server.agents.capabilities.movement.AgentMovementProfile;

import client.Character;
import client.SkillFactory;
import constants.skills.Evan;
import constants.skills.FPMage;
import constants.skills.Shadower;
import server.agents.capabilities.movement.AgentMovementTargetSnapshot;
import server.agents.integration.AgentBotNavigationDebugStateRuntime;
import server.agents.integration.AgentBotMovementTargetRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.agents.integration.AgentBotSessionLifecycleSideEffects;
import server.agents.runtime.AgentRuntimeEntry;
import server.StatEffect;
import server.TimerManager;
import server.maps.MapleMap;
import server.maps.Mist;
import tools.PacketCreator;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

public final class AgentNavigationDebugOverlay {
    private static final int AUTO_CLEAR_MS = 30_000;
    private static final int MAX_MISTS = 900;
    private static final int NODE_SIZE = 10;
    private static final int GRAPH_NODE_SIZE = 8;
    private static final int LINE_THICKNESS = 4;
    private static final int PATH_THICKNESS = 6;
    private static final int DOTTED_SPACING = 22;
    private static final int GRAPH_EDGE_SPACING = 60;
    private static final int GRAPH_REGION_SPACING = 90;
    private static final int PATH_EDGE_SPACING = 28;

    private static final Map<Integer, OverlayState> overlaysByViewerId = new ConcurrentHashMap<>();

    private AgentNavigationDebugOverlay() {
    }

    public static synchronized String showGraph(Character viewer) {
        if (!overlayEffectsAvailable()) {
            return "Bot nav overlay unavailable: no supported mist skill effect is loaded on this server build.";
        }

        OverlayBuilder overlay = new OverlayBuilder(viewer);
        AgentNavigationGraph graph = AgentNavigationGraphService.getGraph(viewer.getMap(), AgentMovementProfile.fromCharacter(viewer));
        Set<String> drawnEdges = new HashSet<>();

        for (AgentNavigationGraph.Region region : graph.regions) {
            overlay.drawApproxRegion(region, OverlayType.REGION);
            overlay.drawNode(region.centerPoint(), OverlayType.NODE, GRAPH_NODE_SIZE);
        }

        for (AgentNavigationGraph.Region region : graph.regions) {
            for (AgentNavigationGraph.Edge edge : graph.getOutgoing(region.id)) {
                if (edge.type == AgentNavigationGraph.EdgeType.WALK) {
                    continue;
                }

                String key = canonicalEdgeKey(edge);
                if (!drawnEdges.add(key)) {
                    continue;
                }

                overlay.drawSparseLine(edge.startPoint, edge.endPoint, overlayTypeForEdge(edge.type), LINE_THICKNESS, GRAPH_EDGE_SPACING);
            }
        }

        replaceOverlay(viewer, overlay.objectIds());
        return buildGraphMessage(graph, overlay);
    }

    public static synchronized String showPath(Character viewer, String botName) {
        if (!overlayEffectsAvailable()) {
            return "Bot nav overlay unavailable: no supported mist skill effect is loaded on this server build.";
        }

        BotSelection selection = selectBotEntry(viewer, botName);
        if (selection.errorMessage != null) {
            return selection.errorMessage;
        }
        AgentRuntimeEntry entry = selection.entry;

        Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
        if (bot.getMapId() != viewer.getMapId()) {
            return "Bot '" + bot.getName() + "' is on map " + bot.getMapId() + ", not your current map.";
        }

        AgentMovementTargetSnapshot targetSnapshot = AgentBotMovementTargetRuntime.snapshot(entry);
        Point targetPos = targetSnapshot.primaryTargetPosition();
        AgentNavigationGraph graph = AgentNavigationGraphService.getGraph(bot.getMap(), AgentMovementProfile.fromCharacter(bot));
        int startRegionId = AgentNavigationRegionService.resolveCurrentRegionId(graph, entry, bot.getMap(), bot.getPosition());
        int targetRegionId = AgentNavigationRegionService.resolveTargetRegionId(graph, entry, bot.getMap(), targetPos);
        List<AgentNavigationGraph.Edge> path = startRegionId >= 0 && targetRegionId >= 0 && startRegionId != targetRegionId
                ? AgentNavigationPathService.findPath(graph, bot, startRegionId, targetRegionId, targetPos)
                : List.of();

        OverlayBuilder overlay = new OverlayBuilder(viewer);
        drawRegion(overlay, graph.getRegion(startRegionId), OverlayType.REGION);
        if (targetRegionId != startRegionId) {
            drawRegion(overlay, graph.getRegion(targetRegionId), OverlayType.TRANSITION);
        }

        for (AgentNavigationGraph.Edge edge : path) {
            overlay.drawSparseLine(edge.startPoint, edge.endPoint, OverlayType.PATH, PATH_THICKNESS, PATH_EDGE_SPACING);
        }

        AgentNavigationGraph.Edge activeEdge = activeNavigationEdge(entry);
        if (activeEdge != null) {
            overlay.drawSparseLine(activeEdge.startPoint, activeEdge.endPoint, OverlayType.CURRENT_EDGE, PATH_THICKNESS + 2, PATH_EDGE_SPACING);
            overlay.drawNode(activeEdge.startPoint, OverlayType.CURRENT_EDGE, NODE_SIZE + 2);
            overlay.drawNode(activeEdge.endPoint, OverlayType.CURRENT_EDGE, NODE_SIZE + 2);
        }

        overlay.drawNode(bot.getPosition(), OverlayType.CURRENT_EDGE, NODE_SIZE + 4);
        overlay.drawNode(targetPos, OverlayType.TRANSITION, NODE_SIZE + 4);
        Point navTargetPos = AgentBotNavigationDebugStateRuntime.navTargetPosition(entry);
        if (navTargetPos != null) {
            overlay.drawNode(navTargetPos, OverlayType.PATH, NODE_SIZE + 2);
        }

        replaceOverlay(viewer, overlay.objectIds());
        return buildPathMessage(bot, startRegionId, targetRegionId, path, entry, overlay);
    }

    public static synchronized String clear(Character viewer) {
        OverlayState state = overlaysByViewerId.remove(viewer.getId());
        clearOverlay(viewer, state);
        return "Bot nav overlay cleared.";
    }

    public static synchronized String pathLog(Character viewer, String botName, String note) {
        BotSelection selection = selectBotEntry(viewer, botName);
        if (selection.errorMessage != null) {
            return selection.errorMessage;
        }
        AgentRuntimeEntry entry = selection.entry;

        if (!AgentBotNavigationDebugStateRuntime.isPathLogging(entry)) {
            AgentBotNavigationDebugStateRuntime.startPathLogging(entry);
            return "Path logging started for '" + AgentBotRuntimeIdentityRuntime.botName(entry) + "'. Run !botnav pathlog again to dump.";
        }

        AgentMovementTargetSnapshot targetSnapshot = AgentBotMovementTargetRuntime.snapshot(entry);
        String filePath = AgentBotNavigationDebugStateRuntime.dumpPathLog(entry, targetSnapshot, note);
        return "Path log for '" + AgentBotRuntimeIdentityRuntime.botName(entry) + "' dumped: " + filePath;
    }

    private static void drawRegion(OverlayBuilder overlay, AgentNavigationGraph.Region region, OverlayType type) {
        if (region == null) {
            return;
        }
        overlay.drawApproxRegion(region, type);
    }

    private static BotSelection selectBotEntry(Character viewer, String botName) {
        if (botName == null || botName.isBlank()) {
            var entries = AgentBotSessionLifecycleSideEffects.getBotEntries(viewer.getId());
            if (entries.isEmpty()) {
                return new BotSelection(null, "No owned bot found. Spawn one first or use !botnav path <botName>.");
            }
            if (entries.size() > 1) {
                String names = entries.stream()
                        .map(AgentBotRuntimeIdentityRuntime::botName)
                        .sorted(String::compareToIgnoreCase)
                        .reduce((left, right) -> left + ", " + right)
                        .orElse("");
                return new BotSelection(null, "Multiple owned bots: " + names + ". Use !botnav path <botName>.");
            }
            return new BotSelection(entries.getFirst(), null);
        }
        AgentRuntimeEntry entry = AgentBotSessionLifecycleSideEffects.getBotEntry(viewer.getId(), botName);
        if (entry == null) {
            return new BotSelection(null, "No owned bot named '" + botName + "' found.");
        }
        return new BotSelection(entry, null);
    }

    private static String buildGraphMessage(AgentNavigationGraph graph, OverlayBuilder overlay) {
        return "Bot nav graph overlay: regions=" + graph.regions.size()
                + ", mists=" + overlay.objectIds().size()
                + (overlay.truncated() ? " (truncated)" : "")
                + ", auto-clear " + (AUTO_CLEAR_MS / 1000) + "s.";
    }

    private static String buildPathMessage(Character bot,
                                           int startRegionId,
                                           int targetRegionId,
                                           List<AgentNavigationGraph.Edge> path,
                                           AgentRuntimeEntry entry,
                                           OverlayBuilder overlay) {
        AgentNavigationGraph.Edge activeEdge = activeNavigationEdge(entry);
        String currentEdge = activeEdge == null ? "none" : activeEdge.type.name();
        String status;
        if (startRegionId == targetRegionId && activeEdge == null) {
            status = "same-region/local";
        } else if (activeEdge != null) {
            status = "committed";
        } else if (path.isEmpty()) {
            status = "no-path";
        } else {
            status = "path-only";
        }
        return "Bot nav path for '" + bot.getName() + "': region " + startRegionId + " -> " + targetRegionId
                + ", edges=" + path.size()
                + ", committed=" + currentEdge
                + ", status=" + status
                + ", mists=" + overlay.objectIds().size()
                + (overlay.truncated() ? " (truncated)" : "")
                + ", auto-clear " + (AUTO_CLEAR_MS / 1000) + "s.";
    }

    private static AgentNavigationGraph.Edge activeNavigationEdge(AgentRuntimeEntry entry) {
        Object edge = AgentBotNavigationDebugStateRuntime.activeNavigationEdge(entry);
        return edge instanceof AgentNavigationGraph.Edge navEdge ? navEdge : null;
    }

    private static void replaceOverlay(Character viewer, List<Integer> objectIds) {
        OverlayState previous = overlaysByViewerId.remove(viewer.getId());
        clearOverlay(viewer, previous);

        ScheduledFuture<?> clearTask = TimerManager.getInstance().schedule(() -> clear(viewer), AUTO_CLEAR_MS);
        overlaysByViewerId.put(viewer.getId(), new OverlayState(objectIds, clearTask));
    }

    private static void clearOverlay(Character viewer, OverlayState state) {
        if (state == null) {
            return;
        }
        if (state.clearTask != null) {
            state.clearTask.cancel(false);
        }
        for (int objectId : state.objectIds) {
            viewer.sendPacket(PacketCreator.removeMist(objectId));
        }
    }

    private static String canonicalEdgeKey(AgentNavigationGraph.Edge edge) {
        Point first = edge.startPoint;
        Point second = edge.endPoint;
        if (first.x > second.x || (first.x == second.x && first.y > second.y)) {
            Point swapped = first;
            first = second;
            second = swapped;
        }
        return edge.type + ":" + first.x + ":" + first.y + ":" + second.x + ":" + second.y;
    }

    private static boolean overlayEffectsAvailable() {
        return firstAvailableEffect(Evan.RECOVERY_AURA, FPMage.POISON_MIST, Shadower.SMOKE_SCREEN) != null;
    }

    private static StatEffect firstAvailableEffect(int... skillIds) {
        for (int skillId : skillIds) {
            var skill = SkillFactory.getSkill(skillId);
            if (skill != null) {
                return skill.getEffect(1);
            }
        }
        return null;
    }

    private static OverlayType overlayTypeForEdge(AgentNavigationGraph.EdgeType edgeType) {
        return switch (edgeType) {
            case DROP, PORTAL -> OverlayType.TRANSITION;
            case JUMP, CLIMB -> OverlayType.PATH;
            case WALK -> OverlayType.REGION;
        };
    }

    private enum OverlayType {
        REGION,
        TRANSITION,
        NODE,
        PATH,
        CURRENT_EDGE
    }

    private static final class OverlayState {
        private final List<Integer> objectIds;
        private final ScheduledFuture<?> clearTask;

        private OverlayState(List<Integer> objectIds, ScheduledFuture<?> clearTask) {
            this.objectIds = new ArrayList<>(objectIds);
            this.clearTask = clearTask;
        }
    }

    private record BotSelection(AgentRuntimeEntry entry, String errorMessage) {
    }

    private static final class OverlayBuilder {
        private final Character viewer;
        private final MapleMap map;
        private final List<Integer> objectIds = new ArrayList<>();
        private boolean truncated = false;

        private OverlayBuilder(Character viewer) {
            this.viewer = viewer;
            this.map = viewer.getMap();
        }

        private List<Integer> objectIds() {
            return objectIds;
        }

        private boolean truncated() {
            return truncated;
        }

        private void drawNode(Point center, OverlayType type, int size) {
            int half = Math.max(1, size / 2);
            drawRect(new Rectangle(center.x - half, center.y - half, Math.max(2, size), Math.max(2, size)), type);
        }

        private void drawLine(Point start, Point end, OverlayType type, int thickness) {
            if (truncated) {
                return;
            }

            int dx = end.x - start.x;
            int dy = end.y - start.y;
            if (Math.abs(dy) <= thickness || Math.abs(dx) <= thickness) {
                int minX = Math.min(start.x, end.x) - Math.max(1, thickness / 2);
                int minY = Math.min(start.y, end.y) - Math.max(1, thickness / 2);
                int width = Math.max(2, Math.abs(dx) + thickness);
                int height = Math.max(2, Math.abs(dy) + thickness);
                drawRect(new Rectangle(minX, minY, width, height), type);
                return;
            }

            drawSparseLine(start, end, type, thickness, DOTTED_SPACING);
        }

        private void drawSparseLine(Point start, Point end, OverlayType type, int thickness, int spacing) {
            if (truncated) {
                return;
            }

            int dx = end.x - start.x;
            int dy = end.y - start.y;
            double distance = start.distance(end);
            int steps = Math.max(1, (int) Math.ceil(distance / Math.max(1, spacing)));
            for (int i = 0; i <= steps && !truncated; i++) {
                double t = i / (double) steps;
                int x = (int) Math.round(start.x + dx * t);
                int y = (int) Math.round(start.y + dy * t);
                drawNode(new Point(x, y), type, thickness + 2);
            }
        }

        private void drawApproxRegion(AgentNavigationGraph.Region region, OverlayType type) {
            if (region == null) {
                return;
            }

            Point left = region.leftPoint();
            Point right = region.rightPoint();
            if (left.distance(right) <= Math.max(40, GRAPH_REGION_SPACING / 2.0)) {
                drawLine(left, right, type, LINE_THICKNESS);
                return;
            }

            drawSparseLine(left, right, type, LINE_THICKNESS, GRAPH_REGION_SPACING);
        }

        private void drawRect(Rectangle rectangle, OverlayType type) {
            if (truncated || objectIds.size() >= MAX_MISTS) {
                truncated = true;
                return;
            }

            Rectangle box = normalize(rectangle);
            Mist mist = new Mist(box, viewer, effectFor(type));
            mist.setObjectId(map.allocateMapObjectId());
            viewer.sendPacket(mist.makeFakeSpawnData(1));
            objectIds.add(mist.getObjectId());
        }

        private Rectangle normalize(Rectangle rectangle) {
            int x = Math.min(rectangle.x, rectangle.x + rectangle.width);
            int y = Math.min(rectangle.y, rectangle.y + rectangle.height);
            int width = Math.max(2, Math.abs(rectangle.width));
            int height = Math.max(2, Math.abs(rectangle.height));
            return new Rectangle(x, y, width, height);
        }

        private StatEffect effectFor(OverlayType type) {
            return switch (type) {
                case REGION, CURRENT_EDGE -> firstAvailableEffect(Shadower.SMOKE_SCREEN, FPMage.POISON_MIST);
                case TRANSITION, NODE -> firstAvailableEffect(FPMage.POISON_MIST, Shadower.SMOKE_SCREEN);
                case PATH -> firstAvailableEffect(Evan.RECOVERY_AURA, FPMage.POISON_MIST, Shadower.SMOKE_SCREEN);
            };
        }
    }
}
