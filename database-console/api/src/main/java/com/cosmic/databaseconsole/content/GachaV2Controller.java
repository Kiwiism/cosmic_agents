package com.cosmic.databaseconsole.content;

import com.cosmic.databaseconsole.audit.AuditService;
import com.cosmic.databaseconsole.bridge.BridgeClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/gacha-v2")
public class GachaV2Controller {
    private final NamedParameterJdbcTemplate game;
    private final AuditService audit;
    private final BridgeClient bridge;
    private final boolean enabled;

    public GachaV2Controller(@Qualifier("gameJdbc") NamedParameterJdbcTemplate game, AuditService audit,
                             BridgeClient bridge, @Value("${cosmic.gacha-v2.enabled:false}") boolean enabled) {
        this.game = game;
        this.audit = audit;
        this.bridge = bridge;
        this.enabled = enabled;
    }

    @GetMapping("/status")
    Map<String, Object> status() {
        Map<String, Object> server = bridge.health();
        boolean runtimeEnabled = Boolean.TRUE.equals(server.get("gachaV2Enabled"));
        return Map.of("consoleEnabled", enabled, "runtimeEnabled", runtimeEnabled,
                "active", enabled && runtimeEnabled);
    }

    @GetMapping("/global-drops")
    List<Map<String, Object>> globalDrops() {
        requireEnabled();
        return game.queryForList("""
                SELECT d.*, c.name AS item_name, c.description, c.properties_json
                FROM drop_data_global d
                LEFT JOIN cosmic_database_console.catalog_entities c
                  ON c.entity_type='ITEM' AND c.entity_id=d.itemid
                ORDER BY d.minimum_mob_level, d.continent, d.chance DESC, d.itemid
                """, Map.of());
    }

    @PostMapping("/global-drops")
    @Transactional("gameTransactionManager")
    Map<String, Object> add(@Valid @RequestBody GlobalDropRequest body, Principal principal,
                            HttpServletRequest request) {
        requireEnabled();
        validate(body);
        game.update("""
                INSERT INTO drop_data_global(continent, itemid, minimum_quantity, maximum_quantity,
                    questid, chance, minimum_mob_level, maximum_mob_level, comments)
                VALUES (:continent, :itemId, :minimum, :maximum, :questId, :chance,
                    :minimumMobLevel, :maximumMobLevel, :comments)
                """, parameters(body));
        Long id = game.queryForObject("SELECT LAST_INSERT_ID()", Map.of(), Long.class);
        boolean active = bridge.reloadDrops();
        audit.record(principal, "GACHA_V2_GLOBAL_DROP_CREATE", "GLOBAL_DROP", id, body.reason(),
                null, body, active ? "ACTIVE" : "SAVED_RELOAD_PENDING", request);
        return Map.of("saved", true, "active", active, "id", id == null ? 0 : id);
    }

    @PutMapping("/global-drops/{id}")
    @Transactional("gameTransactionManager")
    Map<String, Object> update(@PathVariable long id, @Valid @RequestBody GlobalDropRequest body,
                               Principal principal, HttpServletRequest request) {
        requireEnabled();
        validate(body);
        Map<String, Object> before = requiredRow(id);
        MapSqlParameterSource parameters = parameters(body).addValue("id", id);
        game.update("""
                UPDATE drop_data_global SET continent=:continent, itemid=:itemId,
                    minimum_quantity=:minimum, maximum_quantity=:maximum, questid=:questId,
                    chance=:chance, minimum_mob_level=:minimumMobLevel,
                    maximum_mob_level=:maximumMobLevel, comments=:comments WHERE id=:id
                """, parameters);
        Map<String, Object> after = requiredRow(id);
        boolean active = bridge.reloadDrops();
        audit.record(principal, "GACHA_V2_GLOBAL_DROP_UPDATE", "GLOBAL_DROP", id, body.reason(),
                before, after, active ? "ACTIVE" : "SAVED_RELOAD_PENDING", request);
        return Map.of("saved", true, "active", active, "drop", after);
    }

    @DeleteMapping("/global-drops/{id}")
    @Transactional("gameTransactionManager")
    Map<String, Object> delete(@PathVariable long id, @RequestParam @NotBlank String reason,
                               Principal principal, HttpServletRequest request) {
        requireEnabled();
        Map<String, Object> before = requiredRow(id);
        game.update("DELETE FROM drop_data_global WHERE id=:id", Map.of("id", id));
        boolean active = bridge.reloadDrops();
        audit.record(principal, "GACHA_V2_GLOBAL_DROP_DELETE", "GLOBAL_DROP", id, reason,
                before, null, active ? "ACTIVE" : "SAVED_RELOAD_PENDING", request);
        return Map.of("deleted", true, "active", active);
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Gacha V2 is disabled");
        }
    }

    private void validate(GlobalDropRequest body) {
        if (body.maximumQuantity() < body.minimumQuantity()) {
            throw new IllegalArgumentException("Maximum quantity must be at least the minimum quantity");
        }
        if (body.maximumMobLevel() < body.minimumMobLevel()) {
            throw new IllegalArgumentException("Maximum mob level must be at least the minimum mob level");
        }
    }

    private MapSqlParameterSource parameters(GlobalDropRequest body) {
        return new MapSqlParameterSource().addValue("continent", body.continent())
                .addValue("itemId", body.itemId()).addValue("minimum", body.minimumQuantity())
                .addValue("maximum", body.maximumQuantity()).addValue("questId", body.questId())
                .addValue("chance", body.chance()).addValue("minimumMobLevel", body.minimumMobLevel())
                .addValue("maximumMobLevel", body.maximumMobLevel()).addValue("comments", body.comments());
    }

    private Map<String, Object> requiredRow(long id) {
        List<Map<String, Object>> rows = game.queryForList(
                "SELECT * FROM drop_data_global WHERE id=:id", Map.of("id", id));
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Global drop not found");
        }
        return rows.getFirst();
    }

    public record GlobalDropRequest(@Min(-1) int continent, @Min(1) int itemId,
                                    @Min(0) int minimumQuantity, @Min(0) int maximumQuantity,
                                    @Min(0) int questId, @Min(0) @Max(1_000_000) int chance,
                                    @Min(0) @Max(255) int minimumMobLevel,
                                    @Min(0) @Max(255) int maximumMobLevel,
                                    String comments, @NotBlank String reason) {}
}
