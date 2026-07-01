package server.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CacheDiagnostics {
    private static final Logger log = LoggerFactory.getLogger(CacheDiagnostics.class);

    private CacheDiagnostics() {
    }

    public static void warnIfLarge(String cacheName, int size, int warnAt) {
        if (size >= warnAt) {
            log.warn("Cache {} size is {}. warnAt={}", cacheName, size, warnAt);
        }
    }
}
