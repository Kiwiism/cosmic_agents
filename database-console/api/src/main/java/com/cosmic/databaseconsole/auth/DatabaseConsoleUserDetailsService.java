package com.cosmic.databaseconsole.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class DatabaseConsoleUserDetailsService implements UserDetailsService {
    private final JdbcTemplate jdbc;

    public DatabaseConsoleUserDetailsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        List<UserDetails> users = jdbc.query("""
                SELECT username, password_hash, enabled FROM console_users WHERE username = ?
                """, (rs, row) -> User.withUsername(rs.getString("username"))
                .password(rs.getString("password_hash"))
                .disabled(!rs.getBoolean("enabled"))
                .authorities(List.of())
                .build(), username);
        if (users.isEmpty()) {
            throw new UsernameNotFoundException(username);
        }
        UserDetails first = users.getFirst();
        Set<SimpleGrantedAuthority> authorities = new LinkedHashSet<>(jdbc.query("""
                SELECT CONCAT('ROLE_', r.name) AS authority
                FROM console_users u
                JOIN console_user_roles ur ON ur.user_id = u.id
                JOIN console_roles r ON r.id = ur.role_id
                WHERE u.username = ?
                UNION
                SELECT p.code AS authority
                FROM console_users u
                JOIN console_user_roles ur ON ur.user_id = u.id
                JOIN console_role_permissions rp ON rp.role_id = ur.role_id
                JOIN console_permissions p ON p.id = rp.permission_id
                WHERE u.username = ?
                """, (rs, row) -> new SimpleGrantedAuthority(rs.getString("authority")), username, username));
        return User.withUserDetails(first).authorities(authorities).build();
    }
}
