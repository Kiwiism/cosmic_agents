package server.agents.runtime.activity;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;

/** Safe replacement boundary used before a primary controller admits a new session. */
public final class AgentActivityAdmissionCoordinator {
    private final AgentActivityControllerRegistry registry;

    public AgentActivityAdmissionCoordinator(AgentActivityControllerRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("Activity controller registry is required");
        }
        this.registry = registry;
    }

    public boolean prepare(
            String targetControllerId,
            AgentRuntimeEntry entry,
            Character agent,
            String reason,
            long nowMs) {
        if (entry == null || agent == null || targetControllerId == null
                || targetControllerId.isBlank()) {
            return false;
        }
        AgentActivityController target = registry.find(targetControllerId).orElseThrow(() ->
                new IllegalArgumentException("Unknown activity controller: " + targetControllerId));
        if (!target.exclusive()) {
            throw new IllegalArgumentException(
                    "Only an exclusive controller requires admission preparation: "
                            + targetControllerId);
        }
        for (AgentActivityController controller : registry.controllers()) {
            if (!controller.id().equals(targetControllerId)
                    && controller.exclusive()
                    && controller.active(entry, agent)
                    && !controller.requestStop(entry, agent, reason, nowMs)) {
                return false;
            }
        }
        return true;
    }

    /** Emergency-only replacement path; ordinary callers use {@link #prepare}. */
    public void prepareNow(
            String targetControllerId,
            AgentRuntimeEntry entry,
            Character agent,
            String reason,
            long nowMs) {
        if (entry == null || agent == null || targetControllerId == null
                || targetControllerId.isBlank()) {
            return;
        }
        AgentActivityController target = registry.find(targetControllerId).orElseThrow(() ->
                new IllegalArgumentException("Unknown activity controller: " + targetControllerId));
        if (!target.exclusive()) {
            throw new IllegalArgumentException(
                    "Only an exclusive controller requires admission preparation: "
                            + targetControllerId);
        }
        for (AgentActivityController controller : registry.controllers()) {
            if (!controller.id().equals(targetControllerId)
                    && controller.exclusive()
                    && controller.active(entry, agent)) {
                controller.forceStop(entry, agent, reason, nowMs);
            }
        }
    }
}
