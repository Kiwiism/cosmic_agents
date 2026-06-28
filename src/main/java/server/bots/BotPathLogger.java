package server.bots;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import server.agents.capabilities.movement.AgentMovementTargetSnapshot;
import server.agents.integration.AgentBotModeStateRuntime;
import server.agents.integration.AgentBotMovementStuckStateRuntime;
import server.agents.integration.AgentBotNavigationDebugStateRuntime;
import server.agents.integration.AgentBotTickCadenceStateRuntime;
import server.agents.integration.AgentBotTickStateRuntime;
import server.maps.MapleMap;

/**
 * Records per-tick navigation snapshots for a single bot and dumps them to a human-readable file.
 * Attach to BotEntry.pathLogger to start recording; call dumpToFile() to write the report.
 */
public final class BotPathLogger {
    private static final int MAX_TICKS = 120; // 6s at 50ms tick
    private static final Path LOG_DIR = Path.of("logs", "bot-nav");
    private static final DateTimeFormatter FILE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmss");
    private static final DateTimeFormatter HEADER_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private record TickRecord(
            long elapsedMs,
            int botX, int botY,
            int ownerX, int ownerY,
            int goalX, int goalY,
            int steerX, int steerY,
            int botRegionId,
            String physState,
            String navEdge,
            String navDecision,
            String goalSource,
            String steerSource,
            String navTarget,
            boolean aiTick,
            boolean consumedTick,
            boolean stuck,
            boolean unstuck
    ) {}

    private record GraphSnapshot(
            BotMovementProfile requestedProfile,
            BotNavigationGraph graph,
            String source
    ) {}

    private final String botName;
    private final int mapId;
    private final long startMs = System.currentTimeMillis();
    private final Deque<TickRecord> history = new ArrayDeque<>(MAX_TICKS + 1);

    public BotPathLogger(String botName, int mapId) {
        this.botName = botName;
        this.mapId = mapId;
    }

    public void record(BotEntry entry,
                AgentMovementTargetSnapshot targetSnapshot,
                int botRegionId,
                boolean consumedTick,
                boolean aiTick) {
        Point botPos = entry.bot.getPosition();
        Point ownerPos = targetSnapshot.rawOwnerPosition();
        Point goalPos = targetSnapshot.primaryTargetPosition();
        Point steerPos = targetSnapshot.steeringTargetPosition();
        long elapsed = System.currentTimeMillis() - startMs;

        TickRecord rec = new TickRecord(
                elapsed,
                botPos.x, botPos.y,
                ownerPos.x, ownerPos.y,
                goalPos.x, goalPos.y,
                steerPos.x, steerPos.y,
                botRegionId,
                physState(entry),
                navEdgeSummary(entry),
                AgentBotNavigationDebugStateRuntime.decisionWithBlockReason(entry),
                targetSnapshot.primaryTargetSource(),
                targetSnapshot.steeringTargetSource(),
                navTargetSummary(entry),
                aiTick,
                consumedTick,
                computeStuck(botPos.x, botPos.y),
                AgentBotMovementStuckStateRuntime.hasUnstuckCooldown(entry) && consumedTick
        );

        if (history.size() >= MAX_TICKS) {
            history.pollFirst();
        }
        history.addLast(rec);
    }

    /**
     * Writes the full report to disk.
     *
     * @param note optional free-text comment included in the report header (may be null)
     * @return absolute file path, or an error string if the write failed
     */
    public String dumpToFile(BotEntry entry, AgentMovementTargetSnapshot targetSnapshot, String note) {
        LocalDateTime now = LocalDateTime.now();
        String filename = "pathlog-" + botName + "-" + now.format(FILE_FMT) + ".txt";

        GraphSnapshot graphSnapshot = resolveGraphSnapshot(entry);
        BotNavigationGraph graph = graphSnapshot.graph();
        Point botPos = entry.bot.getPosition();
        Point goalTargetPos = targetSnapshot.primaryTargetPosition();
        Point steeringTargetPos = targetSnapshot.steeringTargetPosition();
        int botRegionId = resolveCurrentRegionId(graph, entry, botPos);
        int rawOwnerRegionId = resolveTargetRegionId(graph, entry, targetSnapshot.rawOwnerPosition());
        int followAnchorRegionId = resolveTargetRegionId(graph, entry, targetSnapshot.followAnchorPosition());
        int followBaseRegionId = resolveTargetRegionId(graph, entry, targetSnapshot.followBasePosition());
        int followTargetRegionId = resolveTargetRegionId(graph, entry, targetSnapshot.followTargetPosition());
        int goalRegionId = resolveTargetRegionId(graph, entry, goalTargetPos);
        int steeringTargetRegionId = resolveTargetRegionId(graph, entry, steeringTargetPos);
        int moveTargetRegionId = targetSnapshot.moveTargetPosition() == null
                ? -1
                : resolveTargetRegionId(graph, entry, targetSnapshot.moveTargetPosition());
        int grindTargetRegionId = targetSnapshot.grindTargetPosition() == null
                ? -1
                : resolveTargetRegionId(graph, entry, targetSnapshot.grindTargetPosition());

        StringBuilder sb = new StringBuilder(4096);
        appendHeader(sb, now, note);
        appendCurrentState(sb, entry, targetSnapshot, botPos, botRegionId, rawOwnerRegionId,
                followAnchorRegionId, followBaseRegionId, followTargetRegionId, goalRegionId, steeringTargetPos,
                steeringTargetRegionId, moveTargetRegionId, grindTargetRegionId, graphSnapshot);
        appendCurrentPath(sb, entry, targetSnapshot, goalRegionId, rawOwnerRegionId, botRegionId, graph);
        appendHistory(sb);

        try {
            Files.createDirectories(LOG_DIR);
            Path file = LOG_DIR.resolve(filename);
            Files.writeString(file, sb.toString());
            return file.toAbsolutePath().toString();
        } catch (IOException e) {
            return "Failed to write log: " + e.getMessage();
        }
    }

    private static GraphSnapshot resolveGraphSnapshot(BotEntry entry) {
        MapleMap map = entry.bot.getMap();
        BotMovementProfile requestedProfile = entry.movementProfile == null
                ? BotMovementProfile.fromCharacter(entry.bot)
                : entry.movementProfile;
        BotNavigationGraph exact = BotNavigationGraphProvider.peekGraph(map, requestedProfile);
        if (exact != null) {
            return new GraphSnapshot(requestedProfile, exact, "exact");
        }

        BotNavigationGraph closest = BotNavigationGraphProvider.peekClosestGraph(map, requestedProfile);
        if (closest != null) {
            return new GraphSnapshot(requestedProfile, closest, "closest");
        }

        BotNavigationGraphProvider.warmGraphAsync(map, requestedProfile);
        return new GraphSnapshot(requestedProfile, null, "none/warming");
    }

    private static int resolveCurrentRegionId(BotNavigationGraph graph, BotEntry entry, Point point) {
        if (graph == null) {
            return -1;
        }
        return BotNavigationManager.resolveCurrentRegionId(graph, entry, entry.bot.getMap(), point);
    }

    private static int resolveTargetRegionId(BotNavigationGraph graph, BotEntry entry, Point point) {
        if (graph == null) {
            return -1;
        }
        return BotNavigationManager.resolveTargetRegionId(graph, entry, entry.bot.getMap(), point);
    }

    private void appendHeader(StringBuilder sb, LocalDateTime now, String note) {
        sb.append("=== Bot Path Log: ").append(botName).append(" ===\n");
        sb.append("Map: ").append(mapId).append("\n");
        sb.append("Captured: ").append(now.format(HEADER_FMT)).append("\n");
        sb.append("Ticks: ").append(history.size()).append(" recorded (max ").append(MAX_TICKS).append(")\n");
        if (note != null && !note.isBlank()) {
            sb.append("Note:  ").append(note).append("\n");
        }
        sb.append("\n");
    }

    private void appendCurrentState(StringBuilder sb,
                                    BotEntry entry,
                                    AgentMovementTargetSnapshot targetSnapshot,
                                    Point botPos,
                                    int botRegionId,
                                    int rawOwnerRegionId,
                                    int followAnchorRegionId,
                                    int followBaseRegionId,
                                    int followTargetRegionId,
                                    int goalRegionId,
                                    Point steeringTargetPos,
                                    int steeringTargetRegionId,
                                    int moveTargetRegionId,
                                    int grindTargetRegionId,
                                    GraphSnapshot graphSnapshot) {
        sb.append("--- CURRENT STATE ---\n");
        sb.append("Bot:        ").append(pointRegionStr(botPos, botRegionId)).append("\n");
        sb.append("Owner raw:  ").append(pointRegionStr(targetSnapshot.rawOwnerPosition(), rawOwnerRegionId)).append("\n");
        if (AgentBotModeStateRuntime.following(entry)) {
            sb.append("Follow anchor:")
                    .append(" ").append(targetSnapshot.followAnchorName())
                    .append(" ").append(pointRegionStr(targetSnapshot.followAnchorPosition(), followAnchorRegionId))
                    .append("\n");
        }
        appendMovementGraphState(sb, entry, graphSnapshot);
        sb.append("Formation:  ").append(targetSnapshot.formationType().toLowerCase())
                .append(" px=").append(targetSnapshot.formationPx())
                .append(" snap=").append(targetSnapshot.formationSnapRange())
                .append(" offsetX=").append(entry.followOffsetX).append("\n");
        if (AgentBotModeStateRuntime.following(entry) || !targetSnapshot.followBasePosition().equals(targetSnapshot.rawOwnerPosition())) {
            sb.append("Follow base:")
                    .append(" ").append(pointRegionStr(targetSnapshot.followBasePosition(), followBaseRegionId))
                    .append("  [anchor + formation offset]\n");
        }
        if (AgentBotModeStateRuntime.following(entry)) {
            sb.append("Follow tgt: ").append(pointRegionStr(targetSnapshot.followTargetPosition(), followTargetRegionId))
                    .append("  [after snap/clamp]\n");
        }
        if (targetSnapshot.moveTargetPosition() != null) {
            sb.append("Move target:").append(" ").append(pointRegionStr(targetSnapshot.moveTargetPosition(), moveTargetRegionId)).append("\n");
        }
        if (targetSnapshot.grindTargetPosition() != null) {
            sb.append("Grind tgt:  ").append(pointRegionStr(targetSnapshot.grindTargetPosition(), grindTargetRegionId)).append("\n");
        }
        sb.append("Goal:       ").append(pointRegionStr(targetSnapshot.primaryTargetPosition(), goalRegionId))
                .append("  [").append(targetSnapshot.primaryTargetSource()).append("]\n");
        sb.append("Steering:   ").append(pointRegionStr(steeringTargetPos, steeringTargetRegionId))
                .append("  [").append(targetSnapshot.steeringTargetSource()).append("]\n");
        sb.append("Physics:    ").append(physState(entry)).append("\n");
        sb.append("Nav edge:   ").append(navEdgeSummary(entry)).append("\n");
        sb.append("Nav target: ").append(navTargetSummary(entry))
                .append("  targetRegion=").append(AgentBotNavigationDebugStateRuntime.navTargetRegionId(entry)).append("\n");
        sb.append("Last nav decision: ").append(AgentBotNavigationDebugStateRuntime.lastDecision(entry));
        if (AgentBotNavigationDebugStateRuntime.lastEdgeBlockReason(entry) != null) {
            sb.append("  [blocked: ").append(AgentBotNavigationDebugStateRuntime.lastEdgeBlockReason(entry)).append("]");
        }
        sb.append("\n");
        sb.append("AI cadence:  every ").append(BotManager.cfg.AI_TICK_MS).append("ms")
                .append("  accum=").append(AgentBotTickCadenceStateRuntime.aiTickAccumulatorMs(entry)).append("ms")
                .append("  lastTick=").append(AgentBotTickStateRuntime.lastTickWasAi(entry) ? "AI" : "non-AI");
        long lastTickAtMs = AgentBotTickStateRuntime.lastTickAtMs(entry);
        if (lastTickAtMs > 0) {
            sb.append("  at=").append(lastTickAtMs);
        }
        sb.append("\n");
        sb.append("Mode:       ").append(AgentBotModeStateRuntime.following(entry)
                ? "follow"
                : AgentBotModeStateRuntime.grinding(entry) ? "grind" : "idle").append("\n");
        int stuckMs = AgentBotMovementStuckStateRuntime.stuckMs(entry);
        boolean isStuck = stuckMs >= 500 || computeStuck(botPos.x, botPos.y);
        sb.append("Stuck:      ").append(isStuck ? "YES (" + stuckMs + "ms) ***" : "no").append("\n");
        sb.append("\n");
    }

    private void appendMovementGraphState(StringBuilder sb, BotEntry entry, GraphSnapshot graphSnapshot) {
        BotMovementProfile requested = graphSnapshot.requestedProfile();
        sb.append("Movement:   speed=").append(requested.totalSpeedStat()).append("%")
                .append(" jump=").append(requested.totalJumpStat()).append("%")
                .append(" rawSpeed=").append(entry.bot.getTotalMoveSpeedStat()).append("%")
                .append(" rawJump=").append(entry.bot.getTotalJumpStat()).append("%")
                .append(" walkStep=").append(BotPhysicsEngine.walkStep(entry.bot.getMap(), requested))
                .append(" walkVel=").append(String.format("%.1f", requested.walkVelocityPxs()))
                .append(" jumpForce=").append(String.format("%.1f", requested.jumpSpeedPxs()))
                .append("\n");
        BotNavigationGraph graph = graphSnapshot.graph();
        if (graph == null) {
            sb.append("Graph:      none/warming requestedSpeed=").append(requested.totalSpeedStat()).append("%")
                    .append(" requestedJump=").append(requested.totalJumpStat()).append("%\n");
        } else {
            BotMovementProfile graphProfile = graph.movementProfile;
            sb.append("Graph:      ").append(graphSnapshot.source())
                    .append(" version=").append(graph.version)
                    .append(" speed=").append(graphProfile.totalSpeedStat()).append("%")
                    .append(" jump=").append(graphProfile.totalJumpStat()).append("%");
            if (!graphProfile.equals(requested)) {
                sb.append(" requestedSpeed=").append(requested.totalSpeedStat()).append("%")
                        .append(" requestedJump=").append(requested.totalJumpStat()).append("%");
            }
            sb.append("\n");
        }
        sb.append("Fallback:   heuristic=").append(AgentBotNavigationDebugStateRuntime.graphWarmupFallback(entry) ? "yes" : "no")
                .append(" closestGraph=").append("closest".equals(graphSnapshot.source()) ? "yes" : "no")
                .append("\n");
    }

    private void appendCurrentPath(StringBuilder sb,
                                   BotEntry entry,
                                   AgentMovementTargetSnapshot targetSnapshot,
                                   int goalRegionId,
                                   int rawOwnerRegionId,
                                   int botRegionId,
                                   BotNavigationGraph graph) {
        sb.append("--- CURRENT A* PATH ---\n");
        sb.append("Goal basis:  ").append(targetSnapshot.primaryTargetSource())
                .append(" ").append(pointRegionStr(targetSnapshot.primaryTargetPosition(), goalRegionId)).append("\n");
        appendPath(sb, entry, targetSnapshot.primaryTargetPosition(), goalRegionId, botRegionId, graph);
        if (!targetSnapshot.rawOwnerPosition().equals(targetSnapshot.primaryTargetPosition())) {
            sb.append("Raw owner:   ").append(pointRegionStr(targetSnapshot.rawOwnerPosition(), rawOwnerRegionId)).append("\n");
            appendPath(sb, entry, targetSnapshot.rawOwnerPosition(), rawOwnerRegionId, botRegionId, graph);
        }
        sb.append("\n");
    }

    private void appendPath(StringBuilder sb,
                            BotEntry entry,
                            Point targetPos,
                            int targetRegionId,
                            int botRegionId,
                            BotNavigationGraph graph) {
        if (graph == null) {
            sb.append("  graph unavailable - exact graph warming and no closest cached graph\n");
            return;
        }
        if (botRegionId < 0 || targetRegionId < 0) {
            sb.append("  unknown region  botRegion=").append(botRegionId)
                    .append(" targetRegion=").append(targetRegionId).append("\n");
        } else if (botRegionId == targetRegionId) {
            sb.append("  same region - no inter-region path\n");
        } else {
            List<BotNavigationGraph.Edge> path = BotNavigationManager.findPath(
                    graph, entry.bot, botRegionId, targetRegionId, targetPos);
            if (path.isEmpty()) {
                sb.append("  no path found\n");
            } else {
                for (int i = 0; i < path.size(); i++) {
                    sb.append("  ").append(i + 1).append(". ").append(edgeStr(path.get(i))).append("\n");
                }
            }
        }
    }

    private void appendHistory(StringBuilder sb) {
        sb.append("--- TICK HISTORY (oldest first, ").append(history.size()).append(" ticks) ---\n");
        for (TickRecord rec : history) {
            String goalSuffix = String.format(" goal=(%4d,%4d)[%s]", rec.goalX, rec.goalY, rec.goalSource);
            String steerSuffix = rec.goalX == rec.steerX
                    && rec.goalY == rec.steerY
                    && rec.goalSource.equals(rec.steerSource)
                    ? ""
                    : String.format(" steer=(%4d,%4d)[%s]", rec.steerX, rec.steerY, rec.steerSource);
            sb.append(String.format("[+%5dms] ai=%s bot=(%4d,%4d) own=(%4d,%4d)%s%s r=%-3d %-18s nav=%-8s edge=%-46s tgt=%s%s%s%n",
                    rec.elapsedMs,
                    rec.aiTick ? "Y" : "N",
                    rec.botX, rec.botY,
                    rec.ownerX, rec.ownerY,
                    goalSuffix,
                    steerSuffix,
                    rec.botRegionId,
                    rec.physState,
                    rec.navDecision,
                    rec.navEdge,
                    rec.navTarget,
                    rec.consumedTick ? " [exec]" : "",
                    rec.unstuck ? " *** UNSTUCK ***" : rec.stuck ? " *** STUCK ***" : "",
                    ""));
        }
    }

    private boolean computeStuck(int x, int y) {
        if (history.size() < 5) {
            return false;
        }
        return history.stream()
                .skip(history.size() - 5)
                .allMatch(r -> Math.abs(r.botX - x) <= 8 && Math.abs(r.botY - y) <= 8);
    }

    static String physState(BotEntry entry) {
        if (entry.climbing) {
            if (entry.climbRope != null) {
                return "ROPE(x=" + entry.climbRope.x()
                        + " top=" + entry.climbRope.topY()
                        + " bot=" + entry.climbRope.bottomY() + ")";
            }
            return "ROPE(? climbRope=null)";
        }
        if (entry.inAir) {
            return "AIR(velY=" + String.format("%.1f", entry.velY)
                    + " airVelX=" + entry.airVelX
                    + (entry.climbUpIntent ? " climbIntent" : "") + ")";
        }
        return "GND"
                + (entry.downJumpPending ? "(downJump)" : "")
                + (entry.crouching ? "(crouch)" : "");
    }

    static String navEdgeSummary(BotEntry entry) {
        BotNavigationGraph.Edge e = entry.navEdge;
        if (e == null) {
            return "none";
        }
        return e.type + " r" + e.fromRegionId + "->r" + e.toRegionId
                + " (" + e.startPoint.x + "," + e.startPoint.y
                + ")->(" + e.endPoint.x + "," + e.endPoint.y + ")"
                + launchWindowSummary(e)
                + (e.launchStepX != 0 ? " stepX=" + e.launchStepX : "");
    }

    private static String navTargetSummary(BotEntry entry) {
        Point navTargetPos = AgentBotNavigationDebugStateRuntime.navTargetPosition(entry);
        if (navTargetPos == null) {
            return "none";
        }
        return "(" + navTargetPos.x + "," + navTargetPos.y + ")"
                + (AgentBotNavigationDebugStateRuntime.navPreciseTarget(entry) ? "[precise]" : "");
    }

    private static String edgeStr(BotNavigationGraph.Edge e) {
        return e.type + " r" + e.fromRegionId + "->r" + e.toRegionId
                + "  (" + e.startPoint.x + "," + e.startPoint.y
                + ")->(" + e.endPoint.x + "," + e.endPoint.y + ")"
                + launchWindowSummary(e)
                + (e.launchStepX != 0 ? "  stepX=" + e.launchStepX : "")
                + "  cost=" + e.cost;
    }

    private static String launchWindowSummary(BotNavigationGraph.Edge edge) {
        if ((edge.type != BotNavigationGraph.EdgeType.JUMP
                && !(edge.type == BotNavigationGraph.EdgeType.DROP && edge.launchStepX == 0))
                || edge.launchMinX == edge.launchMaxX) {
            return "";
        }
        return " window=[" + edge.launchMinX + "," + edge.launchMaxX + "]";
    }

    private static String pointRegionStr(Point point, int regionId) {
        return "(" + point.x + ", " + point.y + ")  region=" + regionId;
    }
}
