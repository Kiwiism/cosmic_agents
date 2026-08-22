package server.agents.social.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.util.Map;

/** Creates the optional social-memory pool, isolated from Cosmic and economy databases. */
public final class SocialPostgresDataSource {
    private SocialPostgresDataSource() {
    }

    public static boolean enabled() {
        return Boolean.parseBoolean(System.getenv().getOrDefault("SOCIAL_DB_ENABLED", "false"));
    }

    public static HikariDataSource fromEnvironment() {
        return fromEnvironment(System.getenv());
    }

    static HikariDataSource fromEnvironment(Map<String, String> environment) {
        String host = value(environment, "SOCIAL_DB_HOST", "127.0.0.1");
        String port = value(environment, "SOCIAL_DB_PORT", "5434");
        String database = value(environment, "SOCIAL_DB_NAME", "cosmic_social");
        String user = required(environment, "SOCIAL_DB_USER");
        String password = required(environment, "SOCIAL_DB_PASSWORD");
        HikariConfig config = new HikariConfig();
        config.setPoolName("social-postgres");
        config.setDriverClassName("org.postgresql.Driver");
        config.setJdbcUrl("jdbc:postgresql://" + host + ':' + port + '/' + database);
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(0);
        config.setAutoCommit(true);
        config.setConnectionTimeout(5_000);
        config.setValidationTimeout(2_000);
        return new HikariDataSource(config);
    }

    private static String required(Map<String, String> environment, String key) {
        String value = environment.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " is required when SOCIAL_DB_ENABLED=true");
        }
        return value;
    }

    private static String value(Map<String, String> environment, String key, String fallback) {
        String value = environment.get(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
