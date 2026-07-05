package com.cosmic.databaseconsole.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
public class AuditService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AuditService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void record(Principal actor, String action, String entityType, Object entityKey, String reason,
                       Object before, Object after, String outcome, HttpServletRequest request) {
        String username = actor == null ? "console" : actor.getName();
        jdbc.update("""
                INSERT INTO console_audit_log(actor_user_id, action, entity_type, entity_key, reason,
                    before_json, after_json, outcome, remote_address)
                VALUES ((SELECT id FROM console_users WHERE username = ?), ?, ?, ?, ?, CAST(? AS JSON),
                    CAST(? AS JSON), ?, ?)
                """, username, action, entityType, String.valueOf(entityKey), reason,
                encode(before), encode(after), outcome, request.getRemoteAddr());
    }

    private String encode(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to serialize audit value", exception);
        }
    }
}
