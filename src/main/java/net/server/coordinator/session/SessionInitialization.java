package net.server.coordinator.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages session initialization using remote host (ip address).
 */
public class SessionInitialization {
    private final static Logger log = LoggerFactory.getLogger(SessionInitialization.class);
    private final Set<String> remoteHostsInInitState = ConcurrentHashMap.newKeySet();

    /**
     * Try to initialize a session. Should be called <em>before</em> any session initialization procedure.
     *
     * @return InitializationResult.SUCCESS if initialization was successful.
     * If it was successful, finalize() needs to be called shortly after,
     * or else the initialization will be left hanging in a bad state,
     * which means any subsequent initialization from the same remote host will fail.
     */
    public InitializationResult initialize(String remoteHost) {
        try {
            if (remoteHost == null || remoteHost.isBlank()) {
                return InitializationResult.ERROR;
            }
            return remoteHostsInInitState.add(remoteHost)
                    ? InitializationResult.SUCCESS
                    : InitializationResult.ALREADY_INITIALIZED;
        } catch (Exception e) {
            log.error("Failed to initialize session.", e);
            return InitializationResult.ERROR;
        }
    }

    /**
     * Finalize an initialization. Should be called <em>after</em> any session initialization procedure.
     */
    public void finalize(String remoteHost) {
        if (remoteHost != null) {
            remoteHostsInInitState.remove(remoteHost);
        }
    }
}
