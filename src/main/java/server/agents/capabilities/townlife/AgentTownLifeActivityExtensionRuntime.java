package server.agents.capabilities.townlife;

import client.Character;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.runtime.AgentRuntimeEntry;

/** Executes registered local extensions without giving them session or travel ownership. */
final class AgentTownLifeActivityExtensionRuntime {
    private AgentTownLifeActivityExtensionRuntime() {
    }

    static AgentTownLifeActivityResult tick(
            AgentRuntimeEntry entry,
            Character agent,
            AgentTownLifeState state,
            long nowMs) {
        if (entry == null || agent == null || state == null
                || state.activity() != AgentTownLifeState.Activity.LOCAL_ACTIVITY) {
            return AgentTownLifeActivityResult.NONE;
        }
        AgentTownLifeExtensionState extensionState = entry.capabilityStates()
                .require(AgentTownLifeExtensionState.STATE_KEY);
        if (extensionState.handlerId().isBlank()) {
            AgentTownLifeProfile profile = AgentTownLifeProfileRepository.defaultRepository()
                    .require(state.townMapId());
            String handlerId = profile.extensions().activityHandlers().stream()
                    .filter(id -> AgentTownLifeActivityExtensionRegistry.defaultRegistry()
                            .find(id).isPresent())
                    .findFirst().orElse("");
            if (handlerId.isBlank()) {
                return AgentTownLifeActivityResult.FAILED;
            }
            extensionState.prepare(handlerId, state.nextActionAtMs());
        }
        AgentTownLifeActivityExtension extension =
                AgentTownLifeActivityExtensionRegistry.defaultRegistry()
                        .find(extensionState.handlerId()).orElse(null);
        if (extension == null) {
            return AgentTownLifeActivityResult.FAILED;
        }
        if (nowMs >= extensionState.deadlineMs()) {
            cancel(entry, agent, state, nowMs);
            return AgentTownLifeActivityResult.TIMED_OUT;
        }
        AgentTownLifeActivityExtension.Context context = context(
                agent, state, nowMs, extensionState.deadlineMs());
        AgentTownLifeActivityExtension.Result result;
        try {
            if (!extensionState.started()) {
                result = extension.start(context);
                extensionState.markStarted();
            } else {
                result = extension.tick(context);
            }
        } catch (RuntimeException failure) {
            return AgentTownLifeActivityResult.FAILED;
        }
        return switch (result == null ? AgentTownLifeActivityExtension.Result.FAILED : result) {
            case ACTIVE -> AgentTownLifeActivityResult.ACTIVE;
            case SUCCEEDED -> AgentTownLifeActivityResult.COMPLETED;
            case BLOCKED, FAILED -> AgentTownLifeActivityResult.FAILED;
            case CANCELLED -> AgentTownLifeActivityResult.CANCELLED;
        };
    }

    static void cancel(
            AgentRuntimeEntry entry, Character agent, AgentTownLifeState state, long nowMs) {
        if (entry == null) {
            return;
        }
        AgentTownLifeExtensionState extensionState = entry.capabilityStates()
                .require(AgentTownLifeExtensionState.STATE_KEY);
        if (!extensionState.handlerId().isBlank() && extensionState.started() && agent != null) {
            AgentTownLifeActivityExtension extension =
                    AgentTownLifeActivityExtensionRegistry.defaultRegistry()
                            .find(extensionState.handlerId()).orElse(null);
            if (extension != null) {
                try {
                    extension.cancel(context(
                            agent, state, nowMs,
                            Math.max(nowMs, extensionState.deadlineMs())));
                } catch (RuntimeException ignored) {
                    // Cleanup must remain total even when a town-specific adapter fails.
                }
            }
        }
        extensionState.clear();
    }

    static void clear(AgentRuntimeEntry entry) {
        if (entry != null) {
            entry.capabilityStates().require(AgentTownLifeExtensionState.STATE_KEY).clear();
        }
    }

    private static AgentTownLifeActivityExtension.Context context(
            Character agent, AgentTownLifeState state, long nowMs, long deadlineMs) {
        boolean hasClient = AgentClientGatewayRuntime.clients().hasClient(agent);
        int world = Math.max(0, hasClient
                ? AgentClientGatewayRuntime.clients().world(agent) : agent.getWorld());
        int channel = Math.max(0, hasClient
                ? AgentClientGatewayRuntime.clients().channel(agent) : 0);
        return new AgentTownLifeActivityExtension.Context(
                agent.getId(), world, channel, state.townMapId(), state.venueId(),
                nowMs, Math.max(nowMs, deadlineMs));
    }
}
