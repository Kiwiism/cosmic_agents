package server.agents.plans.mapleisland.cohort;

import server.agents.plans.mapleisland.cohort.MapleIslandCohortPoolSnapshot.Agent;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;

/** High-level provision, lease, activation, recovery, and release operations. */
public final class MapleIslandCohortPoolService {
    private final MapleIslandCohortPoolRegistry registry;
    private final MapleIslandCohortPoolProvisioner provisioner;
    private final IntPredicate isCharacterLive;

    public MapleIslandCohortPoolService(MapleIslandCohortPoolRegistry registry,
                                        MapleIslandCohortPoolProvisioner provisioner,
                                        IntPredicate isCharacterLive) {
        this.registry = registry;
        this.provisioner = provisioner;
        this.isCharacterLive = isCharacterLive;
    }

    public synchronized List<Agent> acquire(int count,
                                            String sessionId,
                                            int ownerCharacterId,
                                            int world,
                                            int channel,
                                            Set<Integer> excludedCharacterIds) throws Exception {
        provisioner.ensureLeaseCandidates(count, ownerCharacterId, world, channel,
                excludedCharacterIds, isCharacterLive);
        return registry.leaseAvailable(count, sessionId, ownerCharacterId,
                System.currentTimeMillis(), world, excludedCharacterIds, isCharacterLive);
    }

    public void markActive(int characterId, String sessionId, long resetAtMs) throws IOException {
        registry.markActive(characterId, sessionId, resetAtMs);
    }

    public void markBroken(int characterId, String sessionId, String error) throws IOException {
        registry.markBroken(characterId, sessionId, error);
    }

    public int releaseSession(String sessionId) throws IOException {
        return registry.releaseSession(sessionId, isCharacterLive);
    }

    public int recoverStaleLeases(Set<String> activeSessionIds) throws IOException {
        return registry.recoverStaleLeases(activeSessionIds, isCharacterLive);
    }

    public MapleIslandCohortPoolRegistry.Stats stats() {
        return registry.stats(isCharacterLive);
    }

    public MapleIslandCohortPoolSnapshot snapshot() {
        return registry.snapshot();
    }
}
