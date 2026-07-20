package server.agents.progression;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.capabilities.build.profiles.AgentApBuildProfileService;
import server.agents.capabilities.build.profiles.AgentSpBuildProfileService;
import server.agents.runtime.AgentRuntimeEntry;

import java.io.IOException;
import java.util.List;

/** Restores a durable assignment or makes one deterministic first-time assignment. */
public final class AgentCareerBuildBundleService {
    private static final Logger log = LoggerFactory.getLogger(AgentCareerBuildBundleService.class);
    private static final AgentCareerAssignmentStore STORE = FileAgentCareerAssignmentStore.runtimeDefault();

    private AgentCareerBuildBundleService() {
    }

    public static AgentCareerBuildBundle restoreOrAssign(AgentRuntimeEntry entry, long nowMs) {
        Character agent = entry == null ? null : entry.bot();
        if (agent == null || agent.getId() <= 0) {
            return null;
        }
        try {
            return restoreOrAssign(entry, agent, nowMs,
                    AgentCareerBuildBundleRepository.defaultRepository(), STORE);
        } catch (IOException | RuntimeException failure) {
            log.warn("Could not restore Agent career build bundle for {} ({})",
                    agent.getName(), agent.getId(), failure);
            return null;
        }
    }

    public static AgentCareerBuildBundle assignForTest(AgentRuntimeEntry entry,
                                                        String bundleId,
                                                        long nowMs) throws IOException {
        Character agent = entry == null ? null : entry.bot();
        if (agent == null || agent.getId() <= 0) {
            throw new IllegalArgumentException("a live Agent is required");
        }
        return assignForTest(entry, agent, bundleId, nowMs,
                AgentCareerBuildBundleRepository.defaultRepository(), STORE);
    }

    static AgentCareerBuildBundle assignForTest(AgentRuntimeEntry entry,
                                                 Character agent,
                                                 String bundleId,
                                                 long nowMs,
                                                 AgentCareerBuildBundleRepository repository,
                                                 AgentCareerAssignmentStore store) throws IOException {
        AgentCareerBuildBundle bundle = repository
                .find(bundleId)
                .orElseThrow(() -> new IllegalArgumentException("unknown career bundle " + bundleId));
        store.save(new AgentCareerAssignment(1, agent.getId(), agent.getName(),
                bundle.bundleId(), bundle.bundleVersion(), nowMs));
        AgentApBuildProfileService.select(entry, bundle.apProfileId());
        AgentSpBuildProfileService.select(entry, bundle.spProfileId());
        entry.capabilityStates().require(AgentCareerProgressionState.STATE_KEY).assign(bundle);
        return bundle;
    }

    static AgentCareerBuildBundle restoreOrAssign(AgentRuntimeEntry entry,
                                                   Character agent,
                                                   long nowMs,
                                                   AgentCareerBuildBundleRepository repository,
                                                   AgentCareerAssignmentStore store) throws IOException {
        AgentCareerAssignment assignment = store.load(agent.getId()).orElse(null);
        AgentCareerBuildBundle bundle;
        if (assignment == null) {
            List<AgentCareerBuildBundle> choices = compatibleChoices(repository.all(), agent.getJob().getId());
            bundle = choices.get(Math.floorMod(agent.getId(), choices.size()));
            assignment = new AgentCareerAssignment(1, agent.getId(), agent.getName(),
                    bundle.bundleId(), bundle.bundleVersion(), nowMs);
            store.save(assignment);
        } else {
            String assignedBundleId = assignment.bundleId();
            bundle = repository.find(assignedBundleId)
                    .orElseThrow(() -> new IOException(
                            "assigned career bundle no longer exists: " + assignedBundleId));
            if (bundle.bundleVersion() != assignment.bundleVersion()) {
                throw new IOException("assigned career bundle version changed without migration: "
                        + assignment.bundleId());
            }
        }
        AgentApBuildProfileService.select(entry, bundle.apProfileId());
        AgentSpBuildProfileService.select(entry, bundle.spProfileId());
        entry.capabilityStates().require(AgentCareerProgressionState.STATE_KEY).assign(bundle);
        return bundle;
    }

    private static List<AgentCareerBuildBundle> compatibleChoices(List<AgentCareerBuildBundle> all, int jobId) {
        if (jobId == 0) {
            return all;
        }
        int firstJobId = jobId < 1_000 ? (jobId / 100) * 100 : jobId;
        List<AgentCareerBuildBundle> compatible = all.stream()
                .filter(bundle -> bundle.firstJobId() == firstJobId)
                .toList();
        return compatible.isEmpty() ? all : compatible;
    }
}
