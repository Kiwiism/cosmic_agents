package server.agents.diagnostics.skillprobe;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** Writes a probe run as a Markdown readiness report under {@link #REPORT_DIR}. */
public final class SkillProbeReportWriter {
    public static final Path REPORT_DIR = Path.of("docs", "agents", "evidence", "skillprobe");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private SkillProbeReportWriter() {
    }

    public static Path write(SkillProbeReport report) throws IOException {
        Files.createDirectories(REPORT_DIR);
        String file = report.job().name().toLowerCase() + "-L" + report.level() + "-"
                + STAMP.format(Instant.ofEpochMilli(report.startedAtMs())) + ".md";
        Path path = REPORT_DIR.resolve(file);
        Files.writeString(path, render(report), StandardCharsets.UTF_8);
        return path;
    }

    public static String render(SkillProbeReport report) {
        StringBuilder md = new StringBuilder();
        md.append("# Skill probe: ").append(report.job()).append(" L").append(report.level())
                .append(" (").append(report.agentName()).append(")\n\n");
        md.append("- Run: ").append(Instant.ofEpochMilli(report.startedAtMs())).append(" UTC, ")
                .append((report.finishedAtMs() - report.startedAtMs()) / 1000).append("s\n");
        md.append("- Weapon: ").append(report.weaponType()).append("\n");
        md.append("- Dummy mob: ").append(report.options().mobId()).append(", map: ")
                .append(report.options().mapId()).append("\n");
        md.append("- Result: **").append(report.okCount()).append("/").append(report.probedCount())
                .append(" probed skills OK**, ").append(report.failures().size()).append(" failed, ")
                .append(report.outcomes().size() - report.probedCount()).append(" not probed\n\n");

        if (!report.notes().isEmpty()) {
            md.append("## Notes\n\n");
            for (String note : report.notes()) {
                md.append("- ").append(note).append('\n');
            }
            md.append('\n');
        }

        md.append("## Stages\n\n| Stage | Count |\n|---|---:|\n");
        for (Map.Entry<SkillProbeOutcome.Stage, Integer> entry : report.stageCounts().entrySet()) {
            md.append("| ").append(entry.getKey()).append(" | ").append(entry.getValue()).append(" |\n");
        }
        md.append('\n');

        md.append("## Failures\n\n");
        if (report.failures().isEmpty()) {
            md.append("None.\n\n");
        } else {
            md.append("| Skill | Id | Lv | Kind | Stage | Detail |\n|---|---:|---:|---|---|---|\n");
            for (SkillProbeOutcome outcome : report.failures()) {
                appendRow(md, outcome);
            }
            md.append('\n');
        }

        md.append("## All skills\n\n");
        md.append("| Skill | Id | Lv | Kind | Bucket | Stage | Hit/Miss | Mob HP delta | Signals | ms | Detail |\n");
        md.append("|---|---:|---:|---|---|---|---:|---:|---|---:|---|\n");
        for (SkillProbeOutcome outcome : report.outcomes()) {
            md.append("| ").append(cell(outcome.skillName()))
                    .append(" | ").append(outcome.skillId())
                    .append(" | ").append(outcome.skillLevel()).append('/').append(outcome.maxLevel())
                    .append(" | ").append(outcome.kind())
                    .append(" | ").append(outcome.bucket())
                    .append(" | ").append(outcome.stage())
                    .append(" | ").append(outcome.hitLines()).append('/').append(outcome.missLines())
                    .append(" | ").append(outcome.mobHpDelta())
                    .append(" | ").append(cell(String.join(" ", outcome.autobanSignals())))
                    .append(" | ").append(outcome.elapsedMs())
                    .append(" | ").append(cell(outcome.detail()))
                    .append(" |\n");
        }
        return md.toString();
    }

    private static void appendRow(StringBuilder md, SkillProbeOutcome outcome) {
        md.append("| ").append(cell(outcome.skillName()))
                .append(" | ").append(outcome.skillId())
                .append(" | ").append(outcome.skillLevel())
                .append(" | ").append(outcome.kind())
                .append(" | ").append(outcome.stage())
                .append(" | ").append(cell(outcome.detail()))
                .append(" |\n");
    }

    private static String cell(String text) {
        return text == null ? "" : text.replace("|", "\\|").replace("\n", " ");
    }
}
