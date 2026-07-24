package server.agents.capabilities.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.AgentCapabilityStatus;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentCapabilityResourceCancellationTest {
    @Test
    void cancellationReleasesEveryCapabilityResource() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentExecutableCapability<TestCommand> capability =
                new AgentExecutableCapability<>() {
                    @Override
                    public String id() {
                        return "test-resource-capability";
                    }

                    @Override
                    public Set<AgentCapabilityResource> requiredResources(TestCommand command) {
                        return Set.of(AgentCapabilityResource.MOVEMENT,
                                AgentCapabilityResource.COMBAT);
                    }

                    @Override
                    public AgentCapabilityStep tick(
                            AgentCapabilityContext context, TestCommand command) {
                        return AgentCapabilityStep.running("working", true);
                    }
                };
        assertTrue(AgentCapabilityRuntime.assign(entry, new AgentCapabilityInvocation<>(
                capability, new TestCommand(), 1_000L, 0)));
        assertTrue(AgentCapabilityRuntime.tick(entry, agent, 100L));
        assertEquals(2, entry.capabilityStates()
                .require(AgentCapabilityResourceLockState.STATE_KEY).size(100L));

        AgentCapabilityRuntime.cancelNow(entry, agent, 200L);

        assertEquals(0, entry.capabilityStates()
                .require(AgentCapabilityResourceLockState.STATE_KEY).size(200L));
    }

    @Test
    void childHandoffReusesParentResourceOwner() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentExecutableCapability<TestCommand> child = capability(
                "child-navigation",
                (context, command) -> AgentCapabilityStep.terminal(
                        AgentCapabilityResult.success("arrived")));
        AgentExecutableCapability<TestCommand> parent = capability(
                "parent-navigation",
                (context, command) -> context.childResult() == null
                        ? AgentCapabilityStep.handoff(new AgentCapabilityInvocation<>(
                        child, command, 1_000L, 0), "navigate first")
                        : AgentCapabilityStep.terminal(
                        AgentCapabilityResult.success("parent completed")));

        assertTrue(AgentCapabilityRuntime.assign(entry, new AgentCapabilityInvocation<>(
                parent, new TestCommand(), 2_000L, 0)));
        assertTrue(AgentCapabilityRuntime.tick(entry, agent, 100L));
        assertTrue(AgentCapabilityRuntime.tick(entry, agent, 200L));
        assertTrue(AgentCapabilityRuntime.tick(entry, agent, 300L));

        assertEquals(0, entry.capabilityStates()
                .require(AgentCapabilityResourceLockState.STATE_KEY).size(300L));
        assertEquals(AgentCapabilityStatus.SUCCESS,
                entry.capabilityRuntimeState().lastResult().status());
    }

    private static AgentExecutableCapability<TestCommand> capability(
            String id, CapabilityTick tick) {
        return new AgentExecutableCapability<>() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public Set<AgentCapabilityResource> requiredResources(TestCommand command) {
                return Set.of(AgentCapabilityResource.MOVEMENT);
            }

            @Override
            public AgentCapabilityStep tick(
                    AgentCapabilityContext context, TestCommand command) {
                return tick.tick(context, command);
            }
        };
    }

    @FunctionalInterface
    private interface CapabilityTick {
        AgentCapabilityStep tick(AgentCapabilityContext context, TestCommand command);
    }

    private record TestCommand() implements AgentCapabilityCommand {
        @Override
        public String type() {
            return "test";
        }
    }
}
