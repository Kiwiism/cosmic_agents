package com.cosmic.databaseconsole.dashboard;

import com.cosmic.databaseconsole.bridge.BridgeClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DashboardController {
    private final NamedParameterJdbcTemplate game;
    private final JdbcTemplate console;
    private final BridgeClient bridge;

    public DashboardController(@Qualifier("gameJdbc") NamedParameterJdbcTemplate game, JdbcTemplate console,
                               BridgeClient bridge) {
        this.game = game;
        this.console = console;
        this.bridge = bridge;
    }

    @GetMapping("/health")
    Map<String, Object> health() {
        return Map.of("status", "UP", "service", "cosmic-database-console-api");
    }

    @GetMapping("/dashboard")
    Map<String, Object> dashboard() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("accounts", count("accounts"));
        metrics.put("characters", count("characters"));
        metrics.put("guilds", count("guilds"));
        metrics.put("reports", count("reports"));
        metrics.put("drops", count("drop_data"));
        metrics.put("shops", count("shops"));
        metrics.put("catalogEntities", console.queryForObject("SELECT COUNT(*) FROM catalog_entities", Long.class));
        metrics.put("catalogMaps", console.queryForObject(
                "SELECT COUNT(*) FROM catalog_entities WHERE entity_type='MAP'", Long.class));
        metrics.put("regions", console.queryForObject(
                "SELECT COUNT(DISTINCT region_code) FROM catalog_map_life", Long.class));
        metrics.put("mobSpawnPoints", console.queryForObject(
                "SELECT COALESCE(SUM(spawn_count),0) FROM catalog_map_life WHERE life_type='m'", Long.class));
        metrics.put("npcPlacements", console.queryForObject(
                "SELECT COALESCE(SUM(spawn_count),0) FROM catalog_map_life WHERE life_type='n'", Long.class));
        metrics.put("jobs", console.queryForObject("SELECT COUNT(*) FROM catalog_jobs", Long.class));
        metrics.put("queuedOperations", console.queryForObject(
                "SELECT COUNT(*) FROM console_queued_operations WHERE status = 'PENDING'", Long.class));
        return Map.of("metrics", metrics, "server", bridge.health());
    }

    private long count(String table) {
        Long count = game.getJdbcTemplate().queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return count == null ? 0 : count;
    }
}
