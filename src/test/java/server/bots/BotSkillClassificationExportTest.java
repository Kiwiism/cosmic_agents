package server.bots;

import client.Skill;
import client.SkillFactory;
import org.junit.jupiter.api.Test;
import server.StatEffect;
import tools.Pair;
import client.BuffStat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Debugging export: lists every skill in Skill.wz and how the bot classifies it
 * (ATTACK / AOE_ATTACK / HEAL / SUPPORT_BUFF / SUMMON / BLACKLISTED / MOB_DEBUFF /
 * CHARGED_ATTACK / INSTANT_UTILITY / PASSIVE), with the WZ description string for each.
 *
 * This is NOT a pass/fail regression test — it always passes. It exists so a human can
 * eyeball the bot's combat classification across the whole skill table and spot
 * misclassifications. It reuses {@link BotCombatManager}'s real predicates as the single
 * source of truth (no parallel reimplementation), so the report always matches live bot
 * behaviour.
 *
 * Run:  mvn test -Dtest=BotSkillClassificationExportTest
 * Output: tmp/bot-skill-classification.tsv  (full table, spreadsheet-friendly)
 *         tmp/bot-skill-classification.md   (grouped summary + flagged issues)
 */
public class BotSkillClassificationExportTest {

    private static final Path WZ_SKILL_DIR = Path.of("wz", "Skill.wz");
    private static final Path WZ_STRING_SKILL = Path.of("wz", "String.wz", "Skill.img.xml");
    private static final Path OUT_DIR = Path.of("tmp");
    private static final Pattern IMGDIR_ID = Pattern.compile("<imgdir name=\"(\\d{4,})\"");

    /** WZ String.img desc text per skill id, loaded once. */
    private final Map<Integer, String> descriptions = new java.util.HashMap<>();

    private record Row(int id, String name, int job, String category, String issue, String desc,
                       int skillType, boolean action, boolean overTime, int durationMs,
                       int mpCon, int hpCon, boolean hasDamage, boolean hasMatk,
                       int mobCount, int hits, int statupCount, String statups) {}

    @Test
    public void exportSkillClassification() throws IOException {
        SkillFactory.loadAllSkills();
        loadDescriptions();
        TreeSet<Integer> ids = collectSkillIds();
        List<Row> rows = new ArrayList<>();

        for (int id : ids) {
            Skill skill = SkillFactory.getSkill(id);
            if (skill == null || skill.getMaxLevel() <= 0) {
                continue;
            }
            StatEffect fx = skill.getEffect(skill.getMaxLevel());
            if (fx == null) {
                continue;
            }
            rows.add(buildRow(skill, fx));
        }

        Files.createDirectories(OUT_DIR);
        writeTsv(rows);
        writeMarkdown(rows);

        System.out.println("[skill-classification] exported " + rows.size()
                + " skills -> " + OUT_DIR.resolve("bot-skill-classification.md").toAbsolutePath());
    }

    private Row buildRow(Skill skill, StatEffect fx) {
        int id = skill.getId();
        String name = safeName(id);
        int job = id / 10000;

        String category = classify(skill, fx);
        String issue = detectIssue(skill, fx, category);

        StringBuilder statups = new StringBuilder();
        for (Pair<BuffStat, Integer> s : fx.getStatups()) {
            if (statups.length() > 0) statups.append('|');
            statups.append(s.getLeft().name()).append(s.getRight() >= 0 ? "+" : "").append(s.getRight());
        }

        return new Row(id, name, job, category, issue, descriptions.getOrDefault(id, ""),
                skill.getSkillType(), skill.getAction(), fx.isOverTime(), fx.getDuration(),
                fx.getMpCon(), fx.getHpCon(), fx.hasDamage(), fx.hasMatk(),
                fx.getMobCount(), BotCombatManager.effectiveHitCount(fx),
                fx.getStatups().size(), statups.toString());
    }

    /**
     * Mirrors the precedence in {@link BotCombatManager} recompute(): heal → attack → support →
     * passive, calling the real predicates. Skills the bot does NOT act on are sub-labelled
     * data-drivenly (no skill-id lists) so a human can see WHY they were excluded:
     *   MOB_DEBUFF      — overTime + duration + no caster statup (mobCount/bbox); Threaten/Slow/...
     *   CHARGED_ATTACK  — overTime + no duration but declares damage/matk; Big Bang/Energy Drain.
     *   INSTANT_UTILITY — overTime + no duration, no offense; Dispel/Resurrection/Time Leap/...
     */
    private String classify(Skill skill, StatEffect fx) {
        int id = skill.getId();
        if (BotCombatManager.isHealSkill(id)) {
            return BotCombatManager.isActiveHealSkill(skill, fx) ? "HEAL" : "HEAL_INACTIVE";
        }
        if (BotCombatManager.isActiveAttackSkill(skill, fx)) {
            return fx.getMobCount() >= 2 ? "AOE_ATTACK" : "ATTACK";
        }
        if (BotCombatManager.isSummonSkill(fx)) {
            return "SUMMON";
        }
        if (BotCombatManager.isActiveSupportSkill(skill, fx)) {
            return BotCombatManager.BUFF_BLACKLIST.contains(id) ? "BLACKLISTED" : "SUPPORT_BUFF";
        }
        boolean rawBuff = fx.isOverTime() && (skill.getAction() || skill.getSkillType() == 2);
        if (rawBuff) {
            if (fx.getDuration() <= 0) {
                return (fx.hasDamage() || fx.hasMatk()) ? "CHARGED_ATTACK" : "INSTANT_UTILITY";
            }
            return "MOB_DEBUFF"; // duration but no caster statup (isActiveSupportSkill rejected it)
        }
        if (BotCombatManager.BUFF_BLACKLIST.contains(id)) {
            return "BLACKLISTED";
        }
        return "PASSIVE";
    }

    private String detectIssue(Skill skill, StatEffect fx, String category) {
        // Every non-acted-on WZ buff variant carries a one-line reason so the flagged section
        // documents why each is (correctly) kept out of the rebuff loop.
        return switch (category) {
            case "SUMMON" ->
                    "summon (SUMMON/PUPPET statup); own bucket, not rebuffed (needs spawn-pos cast path)";
            case "MOB_DEBUFF" ->
                    "mob-targeting debuff (mobCount+bbox, no caster statup); excluded from rebuff loop";
            case "CHARGED_ATTACK" ->
                    "declares damage/matk but flagged overTime + no duration; not rebuffed, not used as attack";
            case "INSTANT_UTILITY" ->
                    "one-shot effect, no duration; excluded from rebuff loop";
            default -> "";
        };
    }

    private void loadDescriptions() throws IOException {
        if (!Files.isRegularFile(WZ_STRING_SKILL)) {
            return;
        }
        String content = Files.readString(WZ_STRING_SKILL, StandardCharsets.UTF_8);
        // <imgdir name="ID">...<string name="desc" value="..."/>
        // Tempered match: between the skill imgdir and its desc, forbid another skill imgdir
        // (3+ digit name) so a desc-less skill can't borrow the following skill's description.
        Matcher m = Pattern.compile(
                "<imgdir name=\"(\\d{3,})\">(?:(?!<imgdir name=\"\\d{3,}).)*?<string name=\"desc\" value=\"([^\"]*)\"")
                .matcher(content);
        while (m.find()) {
            String d = m.group(2).replace("\\n", " ").replace("|", "/").trim();
            if (d.length() > 160) {
                d = d.substring(0, 157) + "...";
            }
            descriptions.putIfAbsent(Integer.parseInt(m.group(1)), d);
        }
    }

    private TreeSet<Integer> collectSkillIds() throws IOException {
        TreeSet<Integer> ids = new TreeSet<>();
        if (!Files.isDirectory(WZ_SKILL_DIR)) {
            return ids;
        }
        try (var files = Files.list(WZ_SKILL_DIR)) {
            for (Path file : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".img.xml"))::iterator) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                Matcher m = IMGDIR_ID.matcher(content);
                while (m.find()) {
                    ids.add(Integer.parseInt(m.group(1)));
                }
            }
        }
        return ids;
    }

    private void writeTsv(List<Row> rows) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("id\tname\tjob\tcategory\tissue\tskillType\taction\toverTime\tdurationMs")
                .append("\tmpCon\thpCon\thasDamage\thasMatk\tmobCount\thits\tstatupCount\tstatups\tdesc\n");
        for (Row r : rows) {
            sb.append(r.id()).append('\t').append(r.name()).append('\t').append(r.job()).append('\t')
                    .append(r.category()).append('\t').append(r.issue()).append('\t')
                    .append(r.skillType()).append('\t').append(r.action()).append('\t')
                    .append(r.overTime()).append('\t').append(r.durationMs()).append('\t')
                    .append(r.mpCon()).append('\t').append(r.hpCon()).append('\t')
                    .append(r.hasDamage()).append('\t').append(r.hasMatk()).append('\t')
                    .append(r.mobCount()).append('\t').append(r.hits()).append('\t')
                    .append(r.statupCount()).append('\t').append(r.statups()).append('\t')
                    .append(r.desc()).append('\n');
        }
        Files.writeString(OUT_DIR.resolve("bot-skill-classification.tsv"), sb.toString());
    }

    private void writeMarkdown(List<Row> rows) throws IOException {
        Map<String, List<Row>> byCat = new LinkedHashMap<>();
        for (Row r : rows) {
            byCat.computeIfAbsent(r.category(), k -> new ArrayList<>()).add(r);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# Bot skill classification\n\n");
        sb.append("Generated by `BotSkillClassificationExportTest`. Reuses `BotCombatManager` ")
                .append("predicates (single source of truth). Total skills: ").append(rows.size()).append("\n\n");

        sb.append("## Category counts\n\n");
        for (Map.Entry<String, List<Row>> e : byCat.entrySet()) {
            sb.append("- **").append(e.getKey()).append("**: ").append(e.getValue().size()).append('\n');
        }

        // Issues first — the actionable section.
        List<Row> flagged = rows.stream().filter(r -> !r.issue().isEmpty()).toList();
        sb.append("\n## ⚠ Flagged for review (").append(flagged.size()).append(")\n\n");
        sb.append("| id | name | job | category | durMs | statups | issue | desc |\n");
        sb.append("|----|------|-----|----------|-------|---------|-------|------|\n");
        for (Row r : flagged) {
            sb.append("| ").append(r.id()).append(" | ").append(r.name()).append(" | ").append(r.job())
                    .append(" | ").append(r.category()).append(" | ").append(r.durationMs())
                    .append(" | ").append(r.statups()).append(" | ").append(r.issue())
                    .append(" | ").append(r.desc()).append(" |\n");
        }

        for (Map.Entry<String, List<Row>> e : byCat.entrySet()) {
            sb.append("\n## ").append(e.getKey()).append(" (").append(e.getValue().size()).append(")\n\n");
            sb.append("| id | name | job | durMs | mpCon | hits | mobs | statups | desc |\n");
            sb.append("|----|------|-----|-------|-------|------|------|---------|------|\n");
            for (Row r : e.getValue()) {
                sb.append("| ").append(r.id()).append(" | ").append(r.name()).append(" | ").append(r.job())
                        .append(" | ").append(r.durationMs()).append(" | ").append(r.mpCon())
                        .append(" | ").append(r.hits()).append(" | ").append(r.mobCount())
                        .append(" | ").append(r.statups()).append(" | ").append(r.desc()).append(" |\n");
            }
        }
        Files.writeString(OUT_DIR.resolve("bot-skill-classification.md"), sb.toString());
    }

    private static String safeName(int id) {
        try {
            String n = SkillFactory.getSkillName(id);
            return (n == null || n.isBlank()) ? "?" : n.replace('|', '/').replace('\n', ' ').trim();
        } catch (RuntimeException ex) {
            return "?";
        }
    }
}
