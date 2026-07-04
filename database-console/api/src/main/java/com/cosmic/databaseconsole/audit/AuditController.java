package com.cosmic.databaseconsole.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/audit")
public class AuditController {
    private final JdbcTemplate jdbc;

    public AuditController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    List<Map<String, Object>> audit(@RequestParam(defaultValue = "100") int limit) {
        return jdbc.queryForList("""
                SELECT a.id, u.username, a.action, a.entity_type, a.entity_key, a.reason,
                       a.before_json, a.after_json, a.outcome, a.remote_address, a.created_at
                FROM console_audit_log a LEFT JOIN console_users u ON u.id = a.actor_user_id
                ORDER BY a.id DESC LIMIT ?
                """, Math.clamp(limit, 1, 500));
    }
}
