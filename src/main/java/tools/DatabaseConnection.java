package tools;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import config.YamlConfig;
import database.note.NoteRowMapper;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * @author Frz (Big Daddy)
 * @author The Real Spookster - some modifications to this beautiful code
 * @author Ronan - some connection pool to this beautiful code
 */
public class DatabaseConnection {
    private static final Logger log = LoggerFactory.getLogger(DatabaseConnection.class);
    private static HikariDataSource dataSource;
    private static Jdbi jdbi;

    public static Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new IllegalStateException("Unable to get connection - connection pool is uninitialized");
        }

        return dataSource.getConnection();
    }

    public static String poolStats() {
        if (dataSource == null || dataSource.getHikariPoolMXBean() == null) {
            return "dbPool=uninitialized";
        }
        return "dbPool active=" + dataSource.getHikariPoolMXBean().getActiveConnections()
                + " idle=" + dataSource.getHikariPoolMXBean().getIdleConnections()
                + " total=" + dataSource.getHikariPoolMXBean().getTotalConnections()
                + " waiting=" + dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection()
                + " max=" + dataSource.getMaximumPoolSize()
                + " connTimeoutMs=" + dataSource.getConnectionTimeout();
    }

    public static Handle getHandle() {
        if (jdbi == null) {
            throw new IllegalStateException("Unable to get handle - connection pool is uninitialized");
        }

        return jdbi.open();
    }

    private static String getDbUrl() {
        // Environment variables override what's defined in the config file
        // This feature is used for the Docker support
        String hostOverride = System.getenv("DB_HOST");
        String host = hostOverride != null ? hostOverride : YamlConfig.config.server.DB_HOST;
        String dbUrl = String.format(YamlConfig.config.server.DB_URL_FORMAT, host);
        return dbUrl;
    }

    public static String configuredUsername() {
        return setting("DB_USER", YamlConfig.config.server.DB_USER);
    }

    public static String configuredPassword() {
        return setting("DB_PASSWORD", YamlConfig.config.server.DB_PASS);
    }

    private static HikariConfig getConfig() {
        HikariConfig config = new HikariConfig();

        config.setJdbcUrl(getDbUrl());
        config.setUsername(configuredUsername());
        config.setPassword(configuredPassword());

        final int initFailTimeoutSeconds = YamlConfig.config.server.INIT_CONNECTION_POOL_TIMEOUT;
        config.setInitializationFailTimeout(SECONDS.toMillis(initFailTimeoutSeconds));
        config.setConnectionTimeout(SECONDS.toMillis(intSetting("cosmic.db.connectionTimeoutSeconds", "COSMIC_DB_CONNECTION_TIMEOUT_SECONDS", 30))); // Hikari default
        config.setMaximumPoolSize(intSetting("cosmic.db.maxPoolSize", "COSMIC_DB_MAX_POOL_SIZE", 10)); // Hikari default

        config.addDataSourceProperty("cachePrepStmts", true);
        config.addDataSourceProperty("prepStmtCacheSize", 25);
        config.addDataSourceProperty("prepStmtCacheSqlLimit", 2048);

        return config;
    }

    private static String setting(String environmentName, String fallback) {
        String value = System.getenv(environmentName);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int intSetting(String propertyName, String environmentName, int defaultValue) {
        String propertyValue = System.getProperty(propertyName);
        String rawValue = propertyValue != null ? propertyValue : System.getenv(environmentName);
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }

        try {
            int parsed = Integer.parseInt(rawValue.trim());
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException e) {
            log.warn("Ignoring invalid integer setting {} / {}='{}'", propertyName, environmentName, rawValue);
        }
        return defaultValue;
    }

    /**
     * Initiate connection to the database
     *
     * @return true if connection to the database initiated successfully, false if not successful
     */
    public static boolean initializeConnectionPool() {
        if (dataSource != null) {
            return true;
        }

        final HikariConfig config = getConfig();
        log.info("Initializing database connection pool. Connecting to:'{}' with user:'{}'", config.getJdbcUrl(),
                config.getUsername());
        Instant initStart = Instant.now();
        try {
            dataSource = new HikariDataSource(config);
            initializeJdbi(dataSource);
            long initDuration = Duration.between(initStart, Instant.now()).toMillis();
            log.info("Connection pool initialized in {} ms", initDuration);
            return true;
        } catch (Exception e) {
            long timeout = Duration.between(initStart, Instant.now()).getSeconds();
            log.error("Failed to initialize database connection pool. Gave up after {} seconds.", timeout);
        }

        // Timed out - failed to initialize
        return false;
    }

    private static void initializeJdbi(DataSource dataSource) {
        jdbi = Jdbi.create(dataSource)
                .registerRowMapper(new NoteRowMapper());
    }
}
