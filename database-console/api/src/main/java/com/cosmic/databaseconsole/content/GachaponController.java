package com.cosmic.databaseconsole.content;

import com.cosmic.databaseconsole.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/gachapon")
public class GachaponController {
    private static final Pattern METHOD = Pattern.compile(
            "get(Common|Uncommon|Rare)Items\\(\\).*?return new int\\[]\\s*\\{(.*?)\\};", Pattern.DOTALL);
    private static final Pattern NUMBER = Pattern.compile("\\b\\d{7,8}\\b");
    private final JdbcTemplate console;
    private final AuditService audit;
    private final Path sourceRoot;
    private static final Map<String, Integer> NPCS = Map.ofEntries(
            Map.entry("HENESYS", 9100100), Map.entry("ELLINIA", 9100101),
            Map.entry("PERION", 9100102), Map.entry("KERNING_CITY", 9100103),
            Map.entry("SLEEPYWOOD", 9100104), Map.entry("MUSHROOM_SHRINE", 9100105),
            Map.entry("SHOWA_SPA_MALE", 9100106), Map.entry("SHOWA_SPA_FEMALE", 9100107),
            Map.entry("LUDIBRIUM", 9100108), Map.entry("NEW_LEAF_CITY", 9100109),
            Map.entry("EL_NATH", 9100110), Map.entry("NAUTILUS_HARBOR", 9100117));

    public GachaponController(JdbcTemplate console, AuditService audit,
                              @Value("${cosmic.project-path:../..}") String projectPath) {
        this.console = console;
        this.audit = audit;
        this.sourceRoot = Path.of(projectPath).toAbsolutePath().normalize()
                .resolve("src/main/java/server/gachapon");
    }

    @GetMapping
    List<Map<String, Object>> locations() throws Exception {
        seedFromSources();
        return console.queryForList("""
                SELECT g.location_code, MAX(COALESCE(g.npc_id, loc.entity_id)) npc_id, COUNT(*) item_count,
                    SUM(tier=0) common_count, SUM(tier=1) uncommon_count, SUM(tier=2) rare_count,
                    MAX(g.source_path) source_path, MAX(n.name) npc_name, MAX(loc.region_name) region_name,
                    GROUP_CONCAT(DISTINCT CONCAT(loc.map_id, ':', COALESCE(m.name,loc.map_id))
                      ORDER BY m.name SEPARATOR ' | ') locations
                FROM console_gachapon_entries g
                LEFT JOIN catalog_map_life loc ON loc.life_type='n'
                  AND loc.entity_id=COALESCE(g.npc_id, CASE g.location_code
                    WHEN 'HENESYS' THEN 9100100 WHEN 'ELLINIA' THEN 9100101 WHEN 'PERION' THEN 9100102
                    WHEN 'KERNING_CITY' THEN 9100103 WHEN 'SLEEPYWOOD' THEN 9100104
                    WHEN 'MUSHROOM_SHRINE' THEN 9100105 WHEN 'SHOWA_SPA_MALE' THEN 9100106
                    WHEN 'SHOWA_SPA_FEMALE' THEN 9100107 WHEN 'LUDIBRIUM' THEN 9100108
                    WHEN 'NEW_LEAF_CITY' THEN 9100109 WHEN 'EL_NATH' THEN 9100110
                    WHEN 'NAUTILUS_HARBOR' THEN 9100117 END)
                LEFT JOIN catalog_entities n ON n.entity_type='NPC' AND n.entity_id=loc.entity_id
                LEFT JOIN catalog_entities m ON m.entity_type='MAP' AND m.entity_id=loc.map_id
                GROUP BY g.location_code ORDER BY g.location_code
                """);
    }

    @GetMapping("/{location}")
    List<Map<String, Object>> entries(@PathVariable String location) throws Exception {
        seedFromSources();
        return console.queryForList("""
                SELECT g.*, c.name item_name, c.description, c.properties_json
                FROM console_gachapon_entries g LEFT JOIN catalog_entities c
                  ON c.entity_type='ITEM' AND c.entity_id=g.item_id
                WHERE g.location_code=? ORDER BY g.tier, g.position
                """, location.toUpperCase(Locale.ROOT));
    }

    @PostMapping("/{location}")
    Map<String, Object> add(@PathVariable String location, @Valid @RequestBody GachaRequest body,
                            Principal principal, HttpServletRequest request) {
        Integer position = console.queryForObject("""
                SELECT COALESCE(MAX(position), -1)+1 FROM console_gachapon_entries
                WHERE location_code=? AND tier=?
                """, Integer.class, location.toUpperCase(Locale.ROOT), body.tier());
        console.update("""
                INSERT INTO console_gachapon_entries(location_code, npc_id, tier, item_id, position,
                    source_kind, source_path) VALUES (?, ?, ?, ?, ?, 'CONSOLE_OVERRIDE', 'cosmic_database_console.console_gachapon_entries')
                """, location.toUpperCase(Locale.ROOT), body.npcId(), body.tier(), body.itemId(), position);
        audit.record(principal, "GACHAPON_ITEM_CREATE", "GACHAPON", location, body.reason(), null, body,
                "ACTIVE_DATABASE_OVERRIDE", request);
        return Map.of("saved", true);
    }

    @PutMapping("/{location}/{id}")
    Map<String, Object> update(@PathVariable String location, @PathVariable long id,
                               @Valid @RequestBody GachaRequest body, Principal principal,
                               HttpServletRequest request) {
        Map<String, Object> before = console.queryForMap("SELECT * FROM console_gachapon_entries WHERE id=?", id);
        console.update("""
                UPDATE console_gachapon_entries SET tier=?, item_id=?, npc_id=?, enabled=?
                WHERE id=? AND location_code=?
                """, body.tier(), body.itemId(), body.npcId(), body.enabled(), id,
                location.toUpperCase(Locale.ROOT));
        Map<String, Object> after = console.queryForMap("SELECT * FROM console_gachapon_entries WHERE id=?", id);
        audit.record(principal, "GACHAPON_ITEM_UPDATE", "GACHAPON", id, body.reason(), before, after,
                "ACTIVE_DATABASE_OVERRIDE", request);
        return after;
    }

    @DeleteMapping("/{location}/{id}")
    Map<String, Object> delete(@PathVariable String location, @PathVariable long id,
                               @RequestParam @NotBlank String reason, Principal principal,
                               HttpServletRequest request) {
        Map<String, Object> before = console.queryForMap("SELECT * FROM console_gachapon_entries WHERE id=?", id);
        console.update("DELETE FROM console_gachapon_entries WHERE id=? AND location_code=?", id,
                location.toUpperCase(Locale.ROOT));
        audit.record(principal, "GACHAPON_ITEM_DELETE", "GACHAPON", id, reason, before, null,
                "ACTIVE_DATABASE_OVERRIDE", request);
        return Map.of("deleted", true);
    }

    private void seedFromSources() throws Exception {
        Integer count = console.queryForObject("SELECT COUNT(*) FROM console_gachapon_entries", Integer.class);
        if (count != null && count > 0 || !Files.isDirectory(sourceRoot)) return;
        try (var paths = Files.list(sourceRoot)) {
            for (Path path : paths.filter(p -> p.getFileName().toString().endsWith(".java"))
                    .filter(p -> !p.getFileName().toString().equals("Gachapon.java"))
                    .filter(p -> !p.getFileName().toString().equals("GachaponItems.java")).toList()) {
                String source = Files.readString(path);
                Matcher methods = METHOD.matcher(source);
                String location = camelToCode(path.getFileName().toString().replace(".java", ""));
                Integer npcId = NPCS.get(location);
                while (methods.find()) {
                    int tier = switch (methods.group(1)) {
                        case "Uncommon" -> 1;
                        case "Rare" -> 2;
                        default -> 0;
                    };
                    Matcher numbers = NUMBER.matcher(methods.group(2).replaceAll("/\\*.*?\\*/", " "));
                    int position = 0;
                    while (numbers.find()) {
                        console.update("""
                                INSERT INTO console_gachapon_entries(location_code, npc_id, tier, item_id, position,
                                    source_kind, source_path) VALUES (?, ?, ?, ?, ?, 'JAVA_SOURCE', ?)
                                """, location, npcId, tier, Integer.parseInt(numbers.group()), position++,
                                "src/main/java/server/gachapon/" + path.getFileName());
                    }
                }
            }
        }
    }

    private String camelToCode(String value) {
        return value.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
    }

    public record GachaRequest(@Min(0) @Max(2) int tier, @Min(1) int itemId,
                               Integer npcId, boolean enabled, @NotBlank String reason) {}
}
