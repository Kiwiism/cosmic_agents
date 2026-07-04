package com.cosmic.databaseconsole.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authenticationManager;

    public AuthController(JdbcTemplate jdbc, PasswordEncoder encoder, AuthenticationConfiguration authenticationConfiguration)
            throws Exception {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.authenticationManager = authenticationConfiguration.getAuthenticationManager();
    }

    @GetMapping("/setup/status")
    Map<String, Object> setupStatus() {
        Integer users = jdbc.queryForObject("SELECT COUNT(*) FROM console_users", Integer.class);
        return Map.of("required", users == null || users == 0);
    }

    @PostMapping("/setup")
    @Transactional
    Map<String, Object> setup(@Valid @RequestBody SetupRequest request) {
        Integer users = jdbc.queryForObject("SELECT COUNT(*) FROM console_users", Integer.class);
        if (users != null && users > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Initial setup has already been completed");
        }
        jdbc.update("INSERT INTO console_users(username, display_name, password_hash) VALUES (?, ?, ?)",
                request.username(), request.displayName(), encoder.encode(request.password()));
        jdbc.update("""
                INSERT INTO console_user_roles(user_id, role_id)
                SELECT u.id, r.id FROM console_users u CROSS JOIN console_roles r
                WHERE u.username = ? AND r.name = 'OWNER'
                """, request.username());
        return Map.of("created", true, "username", request.username());
    }

    @PostMapping("/auth/login")
    Map<String, Object> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        servletRequest.getSession(true).setAttribute("SPRING_SECURITY_CONTEXT", context);
        jdbc.update("UPDATE console_users SET last_login_at = CURRENT_TIMESTAMP WHERE username = ?", request.username());
        return Map.of("authenticated", true, "username", authentication.getName());
    }

    @GetMapping("/auth/me")
    Map<String, Object> me(Principal principal, Authentication authentication) {
        return Map.of(
                "username", principal.getName(),
                "roles", authentication.getAuthorities().stream().map(Object::toString).toList());
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record SetupRequest(
            @NotBlank @Size(min = 3, max = 64) String username,
            @NotBlank @Size(min = 2, max = 96) String displayName,
            @NotBlank @Size(min = 5, max = 128) String password) {}
}
