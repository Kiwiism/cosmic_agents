package server.agents.diagnostics.skillprobe;

import client.Job;
import client.inventory.WeaponType;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Everything one {@code !skillprobe} run learned, plus the short form for GM chat. */
public record SkillProbeReport(String agentName,
                               Job job,
                               int level,
                               WeaponType weaponType,
                               SkillProbeOptions options,
                               List<String> notes,
                               List<SkillProbeOutcome> outcomes,
                               long startedAtMs,
                               long finishedAtMs,
                               Path reportPath) {
    private static final int CHAT_FAILURE_LINES = config.AgentTuning.intValue("server.agents.diagnostics.skillprobe.SkillProbeReport.CHAT_FAILURE_LINES");

    public SkillProbeReport {
        notes = List.copyOf(notes);
        outcomes = List.copyOf(outcomes);
    }

    public SkillProbeReport withPath(Path path) {
        return new SkillProbeReport(agentName, job, level, weaponType, options, notes, outcomes,
                startedAtMs, finishedAtMs, path);
    }

    public long probedCount() {
        return outcomes.stream().filter(SkillProbeOutcome::probed).count();
    }

    public long okCount() {
        return outcomes.stream().filter(SkillProbeOutcome::ok).count();
    }

    public List<SkillProbeOutcome> failures() {
        return outcomes.stream().filter(o -> o.probed() && !o.ok()).toList();
    }

    public Map<SkillProbeOutcome.Stage, Integer> stageCounts() {
        Map<SkillProbeOutcome.Stage, Integer> counts = new EnumMap<>(SkillProbeOutcome.Stage.class);
        for (SkillProbeOutcome outcome : outcomes) {
            counts.merge(outcome.stage(), 1, Integer::sum);
        }
        return counts;
    }

    public List<String> chatSummary() {
        List<String> lines = new ArrayList<>();
        List<SkillProbeOutcome> failures = failures();
        lines.add("Skill probe " + agentName + " " + job + " L" + level + " (" + weaponType + "): "
                + okCount() + "/" + probedCount() + " skills OK, " + failures.size() + " failed, "
                + (outcomes.size() - probedCount()) + " not probed, "
                + (finishedAtMs - startedAtMs) / 1000 + "s.");
        for (int i = 0; i < Math.min(CHAT_FAILURE_LINES, failures.size()); i++) {
            SkillProbeOutcome failure = failures.get(i);
            lines.add("  " + failure.skillName() + " (" + failure.skillId() + "): " + failure.stage()
                    + " - " + truncate(failure.detail(), 90));
        }
        if (failures.size() > CHAT_FAILURE_LINES) {
            lines.add("  ... " + (failures.size() - CHAT_FAILURE_LINES) + " more in the report file.");
        }
        if (reportPath != null) {
            lines.add("Report: " + reportPath);
        }
        return lines;
    }

    static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }
}
