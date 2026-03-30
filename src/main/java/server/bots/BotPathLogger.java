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

/**
 * Records per-tick navigation snapshots for a single bot and dumps them to a human-readable file.
 * Attach to BotEntry.pathLogger to start recording; call dumpToFile() to write the report.
 */
final class BotPathLogger {
    private static final int MAX_TICKS = 120; // 6s at 50ms tick
    private static final Path LOG_DIR = Path.of("logs", "bot-nav");
    private static final DateTimeFormatter FILE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmss");
    private static final DateTimeFormatter HEADER_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private record TickRecord(
            long elapsedMs,
            int botX, int botY,
            int ownerX, int ownerY,
            int botRegionId,
            String physState,
            String navEdge,
            String navDecision,  // reuse / new / exec / clear / same-region / no-path / no-ai
            String navTarget,
            boolean consumedTick,
            boolean stuck
    ) {}

    private final String botName;
    private final int mapId;
    private final long startMs = System.currentTimeMillis();
    private final Deque<TickRecord> history = new ArrayDeque<>(MAX_TICKS + 1);

    BotPathLogger(String botName, int mapId) {
        this.botName = botName;
        this.mapId = mapId;
    }

    void record(BotEntry entry, Point rawTargetPos, int botRegionId, boolean consumedTick) {
        Point botPos = entry.bot.getPosition();
        long elapsed = System.currentTimeMillis() - startMs;

        TickRecord rec = new TickRecord(
                elapsed,
                botPos.x, botPos.y,
                rawTargetPos.x, rawTargetPos.y,
                botRegionId,
                physState(entry),
                navEdgeSummary(entry),
                entry.lastNavDecision,
                navTargetSummary(entry),
                consumedTick,
                computeStuck(botPos.x, botPos.y)
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
    String dumpToFile(BotEntry entry, Point ownerPos, String note) {
        LocalDateTime now = LocalDateTime.now();
        String filename = "pathlog-" + botName + "-" + now.format(FILE_FMT) + ".txt";

        BotNavigationGraph graph = BotNavigationGraphProvider.getGraph(entry.bot.getMap());
        Point botPos = entry.bot.getPosition();
        int botRegionId = BotNavigationManager.resolveCurrentRegionId(graph, entry, entry.bot.getMap(), botPos);
        int ownerRegionId = graph.findRegionId(entry.bot.getMap(), ownerPos);

        StringBuilder sb = new StringBuilder(4096);
        appendHeader(sb, now, note);
        appendCurrentState(sb, entry, ownerPos, botPos, botRegionId, ownerRegionId);
        appendCurrentPath(sb, entry, ownerPos, ownerRegionId, botRegionId, graph);
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

    // -------------------------------------------------------------------------
    // Report sections
    // -------------------------------------------------------------------------

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

    private void appendCurrentState(StringBuilder sb, BotEntry entry, Point ownerPos,
                                    Point botPos, int botRegionId, int ownerRegionId) {
        sb.append("--- CURRENT STATE ---\n");
        sb.append("Bot:   (").append(botPos.x).append(", ").append(botPos.y)
                .append(")  region=").append(botRegionId).append("\n");
        sb.append("Owner: (").append(ownerPos.x).append(", ").append(ownerPos.y)
                .append(")  region=").append(ownerRegionId).append("\n");
        sb.append("Physics:    ").append(physState(entry)).append("\n");
        sb.append("Nav edge:   ").append(navEdgeSummary(entry)).append("\n");
        sb.append("Nav target: ").append(navTargetSummary(entry))
                .append("  targetRegion=").append(entry.navTargetRegionId).append("\n");
        sb.append("Last nav decision: ").append(entry.lastNavDecision).append("\n");
        sb.append("Cooldowns:  jumpMs=").append(entry.jumpCooldownMs)
                .append("  ropeGrabMs=").append(entry.ropeGrabCooldownMs).append("\n");
        sb.append("Mode:       ").append(entry.following ? "follow" : entry.grinding ? "grind" : "idle").append("\n");
        sb.append("Stuck:      ").append(computeStuck(botPos.x, botPos.y) ? "YES ***" : "no").append("\n");
        sb.append("\n");
    }

    private void appendCurrentPath(StringBuilder sb, BotEntry entry, Point ownerPos,
                                   int ownerRegionId, int botRegionId, BotNavigationGraph graph) {
        sb.append("--- CURRENT A* PATH ---\n");
        if (botRegionId < 0 || ownerRegionId < 0) {
            sb.append("  unknown region  botRegion=").append(botRegionId)
                    .append(" ownerRegion=").append(ownerRegionId).append("\n");
        } else if (botRegionId == ownerRegionId) {
            sb.append("  same region — no inter-region path\n");
        } else {
            List<BotNavigationGraph.Edge> path = BotNavigationManager.findPath(
                    graph, entry.bot, botRegionId, ownerRegionId, ownerPos);
            if (path.isEmpty()) {
                sb.append("  no path found\n");
            } else {
                for (int i = 0; i < path.size(); i++) {
                    sb.append("  ").append(i + 1).append(". ").append(edgeStr(path.get(i))).append("\n");
                }
            }
        }
        sb.append("\n");
    }

    private void appendHistory(StringBuilder sb) {
        sb.append("--- TICK HISTORY (oldest first, ").append(history.size()).append(" ticks) ---\n");
        for (TickRecord rec : history) {
            sb.append(String.format("[+%5dms] bot=(%4d,%4d) owner=(%4d,%4d) r=%-3d  %-38s  nav=%-6s %-50s  tgt=%s%s%s%n",
                    rec.elapsedMs,
                    rec.botX, rec.botY,
                    rec.ownerX, rec.ownerY,
                    rec.botRegionId,
                    rec.physState,
                    rec.navDecision,
                    rec.navEdge,
                    rec.navTarget,
                    rec.consumedTick ? " [exec]" : "",
                    rec.stuck ? " *** STUCK ***" : ""));
        }
    }

    // -------------------------------------------------------------------------
    // Stuck detection — last 5 ticks all within 8px
    // -------------------------------------------------------------------------

    private boolean computeStuck(int x, int y) {
        if (history.size() < 5) {
            return false;
        }
        return history.stream()
                .skip(history.size() - 5)
                .allMatch(r -> Math.abs(r.botX - x) <= 8 && Math.abs(r.botY - y) <= 8);
    }

    // -------------------------------------------------------------------------
    // State formatters
    // -------------------------------------------------------------------------

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
                + (e.launchStepX != 0 ? " stepX=" + e.launchStepX : "");
    }

    private static String navTargetSummary(BotEntry entry) {
        if (entry.navTargetPos == null) {
            return "none";
        }
        return "(" + entry.navTargetPos.x + "," + entry.navTargetPos.y + ")"
                + (entry.navPreciseTarget ? "[precise]" : "");
    }

    private static String edgeStr(BotNavigationGraph.Edge e) {
        return e.type + " r" + e.fromRegionId + "->r" + e.toRegionId
                + "  (" + e.startPoint.x + "," + e.startPoint.y
                + ")->(" + e.endPoint.x + "," + e.endPoint.y + ")"
                + (e.launchStepX != 0 ? "  stepX=" + e.launchStepX : "")
                + "  cost=" + e.cost;
    }
}
