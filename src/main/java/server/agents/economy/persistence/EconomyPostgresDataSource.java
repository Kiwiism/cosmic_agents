package server.agents.economy.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.util.Map;

/** Creates the separate evidence pool from environment-only credentials. */
public final class EconomyPostgresDataSource {
    private EconomyPostgresDataSource() { }

    public static HikariDataSource fromEnvironment() { return fromEnvironment(System.getenv()); }

    static HikariDataSource fromEnvironment(Map<String, String> environment) {
        String host = value(environment, "ECONOMY_DB_HOST", "127.0.0.1");
        String port = value(environment, "ECONOMY_DB_PORT", "5433");
        String database = value(environment, "ECONOMY_DB_NAME", "cosmic_economy");
        String user = required(environment, "ECONOMY_DB_USER");
        String password = required(environment, "ECONOMY_DB_PASSWORD");
        HikariConfig config = new HikariConfig();
        config.setPoolName("economy-postgres");
        config.setDriverClassName("org.postgresql.Driver");
        config.setJdbcUrl("jdbc:postgresql://" + host + ':' + port + '/' + database);
        config.setUsername(user); config.setPassword(password);
        config.setMaximumPoolSize(8); config.setMinimumIdle(1);
        config.setAutoCommit(true); config.setConnectionTimeout(10_000);
        return new HikariDataSource(config);
    }

    private static String required(Map<String, String> environment, String key) {
        String value = environment.get(key);
        if (value == null || value.isBlank())
            throw new IllegalStateException(key + " is required for the separate economy database");
        return value;
    }
    private static String value(Map<String, String> environment, String key, String fallback) {
        String value = environment.get(key); return value == null || value.isBlank() ? fallback : value;
    }
}
