package server.agents.social.persistence;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.social.memory.SocialMemoryStore;

import java.util.Optional;

/** Lazy optional owner for the independent social-memory connection pool. */
public final class SocialMemoryDatabaseRuntime {
    private static final Logger log = LoggerFactory.getLogger(SocialMemoryDatabaseRuntime.class);
    private static final Object LOCK = new Object();
    private static volatile HikariDataSource dataSource;
    private static volatile long nextInitializationAttemptAtMs;

    private SocialMemoryDatabaseRuntime() {
    }

    public static Optional<SocialMemoryStore> store() {
        if (!SocialPostgresDataSource.enabled()) {
            return Optional.empty();
        }
        HikariDataSource current = dataSource;
        if (current != null) {
            return Optional.of(new JdbcSocialMemoryStore(current));
        }
        synchronized (LOCK) {
            if (dataSource != null) {
                return Optional.of(new JdbcSocialMemoryStore(dataSource));
            }
            long nowMs = System.currentTimeMillis();
            if (nowMs < nextInitializationAttemptAtMs) {
                return Optional.empty();
            }
            HikariDataSource created = null;
            try {
                created = SocialPostgresDataSource.fromEnvironment();
                new SocialDatabaseVerifier(created).verify();
                dataSource = created;
                return Optional.of(new JdbcSocialMemoryStore(created));
            } catch (RuntimeException failure) {
                if (created != null) {
                    created.close();
                }
                nextInitializationAttemptAtMs = nowMs + 30_000L;
                log.warn("Social memory database unavailable; continuing with bounded memory only: {}",
                        failure.toString());
                return Optional.empty();
            }
        }
    }

    public static void close() {
        synchronized (LOCK) {
            if (dataSource != null) {
                dataSource.close();
                dataSource = null;
            }
            nextInitializationAttemptAtMs = 0L;
        }
    }
}
