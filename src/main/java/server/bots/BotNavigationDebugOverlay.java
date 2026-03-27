package server.bots;

import client.Character;
import client.SkillFactory;
import constants.skills.Evan;
import constants.skills.FPMage;
import constants.skills.Shadower;
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

public final class BotNavigationDebugOverlay {
    private static final int AUTO_CLEAR_MS = 30_000;
    private static final int MAX_MISTS = 900;
    private static final int NODE_SIZE = 10;
    private static final int LINE_THICKNESS = 4;
    private static final int PATH_THICKNESS = 6;
    private static final int DOTTED_SPACING = 22;

    private static final Map<Integer, OverlayState> overlaysByViewerId = new ConcurrentHashMap<>();

    private BotNavigationDebugOverlay() {
    }

    public static synchronized String showGraph(Character viewer) {
        if (!overlayEffectsAvailable()) {
            return "Bot nav overlay unavailable: no supported mist skill effect is loaded on this server build.";
        }

        OverlayBuilder overlay = new OverlayBuilder(viewer);
        BotNavigationGraph graph = BotNavigationGraphProvider.getGraph(viewer.getMap());
        Set<Point> nodes = new HashSet<>();
        Set<String> drawnEdges = new HashSet<>();

        for (BotNavigationGraph.Region region : graph.regions) {
            for (BotNavigationGraph.Segment segment : region.segments) {
                Point start = new Point(segment.x1, segment.y1);
                Point end = new Point(segment.x2, segment.y2);
                overlay.drawLine(start, end, OverlayType.REGION, LINE_THICKNESS);
                nodes.add(start);
                nodes.add(end);
            }
        }

        for (BotNavigationGraph.Region region : graph.regions) {
            for (BotNavigationGraph.Edge edge : graph.getOutgoing(region.id)) {
                if (edge.type == BotNavigationGraph.EdgeType.WALK) {
                    continue;
                }

                String key = canonicalEdgeKey(edge);
                if (!drawnEdges.add(key)) {
                    continue;
                }

                overlay.drawLine(edge.startPoint, edge.endPoint, overlayTypeForEdge(edge.type), LINE_THICKNESS);
                nodes.add(new Point(edge.startPoint));
                nodes.add(new Point(edge.endPoint));
            }
        }

        for (Point node : nodes) {
            overlay.drawNode(node, OverlayType.NODE, NODE_SIZE);
        }

        replaceOverlay(viewer, overlay.objectIds());
        return buildGraphMessage(graph, overlay);
    }

    public static synchronized String showPath(Character viewer, String botName) {
        if (!overlayEffectsAvailable()) {
            return "Bot nav overlay unavailable: no supported mist skill effect is loaded on this server build.";
        }

        BotEntry entry = findBotEntry(viewer, botName);
        if (entry == null) {
            return botName == null
                    ? "No owned bot found. Spawn one first or use !botnav path <botName>."
                    : "No owned bot named '" + botName + "' found.";
        }

        Character bot = entry.bot;
        if (bot.getMapId() != viewer.getMapId()) {
            return "Bot '" + bot.getName() + "' is on map " + bot.getMapId() + ", not your current map.";
        }

        Point rawTargetPos = resolveRawTargetPos(entry);
        BotNavigationGraph graph = BotNavigationGraphProvider.getGraph(bot.getMap());
        int startRegionId = graph.findRegionId(bot.getMap(), bot.getPosition());
        int targetRegionId = graph.findRegionId(bot.getMap(), rawTargetPos);
        List<BotNavigationGraph.Edge> path = startRegionId >= 0 && targetRegionId >= 0 && startRegionId != targetRegionId
                ? BotNavigationManager.findPath(graph, bot, startRegionId, targetRegionId, rawTargetPos)
                : List.of();

        OverlayBuilder overlay = new OverlayBuilder(viewer);
        drawRegion(overlay, graph.getRegion(startRegionId), OverlayType.REGION);
        if (targetRegionId != startRegionId) {
            drawRegion(overlay, graph.getRegion(targetRegionId), OverlayType.TRANSITION);
        }
        for (BotNavigationGraph.Edge edge : path) {
            overlay.drawLine(edge.startPoint, edge.endPoint, OverlayType.PATH, PATH_THICKNESS);
            overlay.drawNode(edge.startPoint, OverlayType.NODE, NODE_SIZE);
            overlay.drawNode(edge.endPoint, OverlayType.NODE, NODE_SIZE);
        }

        if (entry.navEdge != null) {
            overlay.drawLine(entry.navEdge.startPoint, entry.navEdge.endPoint, OverlayType.CURRENT_EDGE, PATH_THICKNESS + 2);
            overlay.drawNode(entry.navEdge.startPoint, OverlayType.CURRENT_EDGE, NODE_SIZE + 2);
            overlay.drawNode(entry.navEdge.endPoint, OverlayType.CURRENT_EDGE, NODE_SIZE + 2);
        }

        overlay.drawNode(bot.getPosition(), OverlayType.CURRENT_EDGE, NODE_SIZE + 4);
        overlay.drawNode(rawTargetPos, OverlayType.TRANSITION, NODE_SIZE + 4);
        if (entry.navTargetPos != null) {
            overlay.drawNode(entry.navTargetPos, OverlayType.PATH, NODE_SIZE + 2);
        }

        replaceOverlay(viewer, overlay.objectIds());
        return buildPathMessage(bot, startRegionId, targetRegionId, path, entry, overlay);
    }

    public static synchronized String clear(Character viewer) {
        OverlayState state = overlaysByViewerId.remove(viewer.getId());
        clearOverlay(viewer, state);
        return "Bot nav overlay cleared.";
    }

    private static void drawRegion(OverlayBuilder overlay, BotNavigationGraph.Region region, OverlayType type) {
        if (region == null) {
            return;
        }
        for (BotNavigationGraph.Segment segment : region.segments) {
            overlay.drawLine(new Point(segment.x1, segment.y1), new Point(segment.x2, segment.y2), type, LINE_THICKNESS);
        }
    }

    private static BotEntry findBotEntry(Character viewer, String botName) {
        BotManager botManager = BotManager.getInstance();
        if (botName == null || botName.isBlank()) {
            return botManager.getFirstBotEntry(viewer.getId());
        }
        return botManager.getBotEntry(viewer.getId(), botName);
    }

    private static Point resolveRawTargetPos(BotEntry entry) {
        if (entry.grinding && entry.grindTarget != null && entry.grindTarget.isAlive()
                && entry.grindTarget.getMap() == entry.bot.getMap()) {
            return entry.grindTarget.getPosition();
        }
        if (entry.owner != null) {
            return entry.owner.getPosition();
        }
        return entry.bot.getPosition();
    }

    private static String buildGraphMessage(BotNavigationGraph graph, OverlayBuilder overlay) {
        return "Bot nav graph overlay: regions=" + graph.regions.size()
                + ", mists=" + overlay.objectIds().size()
                + (overlay.truncated() ? " (truncated)" : "")
                + ", auto-clear " + (AUTO_CLEAR_MS / 1000) + "s.";
    }

    private static String buildPathMessage(Character bot,
                                           int startRegionId,
                                           int targetRegionId,
                                           List<BotNavigationGraph.Edge> path,
                                           BotEntry entry,
                                           OverlayBuilder overlay) {
        String currentEdge = entry.navEdge == null ? "none" : entry.navEdge.type.name();
        return "Bot nav path for '" + bot.getName() + "': region " + startRegionId + " -> " + targetRegionId
                + ", edges=" + path.size()
                + ", committed=" + currentEdge
                + ", mists=" + overlay.objectIds().size()
                + (overlay.truncated() ? " (truncated)" : "")
                + ", auto-clear " + (AUTO_CLEAR_MS / 1000) + "s.";
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

    private static String canonicalEdgeKey(BotNavigationGraph.Edge edge) {
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

    private static OverlayType overlayTypeForEdge(BotNavigationGraph.EdgeType edgeType) {
        return switch (edgeType) {
            case DROP, PORTAL -> OverlayType.TRANSITION;
            case JUMP, CLIMB, FLASH_JUMP, TELEPORT -> OverlayType.PATH;
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

            double distance = start.distance(end);
            int steps = Math.max(1, (int) Math.ceil(distance / DOTTED_SPACING));
            for (int i = 0; i <= steps && !truncated; i++) {
                double t = i / (double) steps;
                int x = (int) Math.round(start.x + dx * t);
                int y = (int) Math.round(start.y + dy * t);
                drawNode(new Point(x, y), type, thickness + 2);
            }
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
