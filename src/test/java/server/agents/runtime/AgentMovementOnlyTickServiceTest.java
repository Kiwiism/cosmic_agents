package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentMovementOnlyTickService;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class AgentMovementOnlyTickServiceTest {
    @Test
    void returnsWhenEntryHasNoAgentOrTarget() {
        List<String> calls = new ArrayList<>();

        AgentMovementOnlyTickService.stepMovementOnly(
                new AgentRuntimeEntry(null, mock(Character.class), null),
                new Point(10, 20),
                true,
                1000L,
                hooks(calls).build());
        AgentMovementOnlyTickService.stepMovementOnly(
                entry(),
                null,
                true,
                1000L,
                hooks(calls).build());

        assertEquals(List.of(), calls);
    }

    @Test
    void idleConsumesFirst() {
        List<String> calls = new ArrayList<>();

        AgentMovementOnlyTickService.stepMovementOnly(
                entry(),
                new Point(10, 20),
                true,
                1000L,
                hooks(calls).idleConsumes().build());

        assertEquals(List.of("idle"), calls);
    }

    @Test
    void followMapSyncConsumesBeforeRecovery() {
        List<String> calls = new ArrayList<>();

        AgentMovementOnlyTickService.stepMovementOnly(
                entry(),
                new Point(10, 20),
                true,
                1000L,
                hooks(calls).followMapConsumes().build());

        assertEquals(List.of("idle", "shopPending", "followMap"), calls);
    }

    @Test
    void partyRecoveryConsumesBeforeTargetRecovery() {
        List<String> calls = new ArrayList<>();

        AgentMovementOnlyTickService.stepMovementOnly(
                entry(),
                new Point(10, 20),
                true,
                1000L,
                hooks(calls).partyRecoveryConsumes().build());

        assertEquals(List.of("idle", "shopPending", "followMap", "anchor", "partyRecovery"), calls);
    }

    @Test
    void targetRecoveryConsumesBeforeMapChange() {
        List<String> calls = new ArrayList<>();

        AgentMovementOnlyTickService.stepMovementOnly(
                entry(),
                new Point(10, 20),
                true,
                1000L,
                hooks(calls).targetRecoveryConsumes().build());

        assertEquals(List.of("idle", "shopPending", "followMap", "anchor", "partyRecovery", "targetRecovery"), calls);
    }

    @Test
    void mapChangeConsumesBeforeShopVisit() {
        List<String> calls = new ArrayList<>();

        AgentMovementOnlyTickService.stepMovementOnly(
                entry(),
                new Point(10, 20),
                true,
                1000L,
                hooks(calls).mapChangeConsumes().build());

        assertEquals(List.of("idle", "shopPending", "followMap", "anchor", "partyRecovery", "targetRecovery", "mapChange"), calls);
    }

    @Test
    void shopVisitWithDelayConsumesWithoutMovementCore() {
        List<String> calls = new ArrayList<>();

        AgentMovementOnlyTickService.stepMovementOnly(
                entry(),
                new Point(10, 20),
                true,
                1000L,
                hooks(calls).shopPending().shopDelay(100).build());

        assertEquals(List.of(
                "idle", "shopPending", "anchor", "partyRecovery", "targetRecovery",
                "mapChange", "shopPending", "shopTick", "shopTarget", "shopDelay"), calls);
    }

    @Test
    void shopVisitWithTargetStepsMovementCore() {
        List<String> calls = new ArrayList<>();

        AgentMovementOnlyTickService.stepMovementOnly(
                entry(),
                new Point(10, 20),
                false,
                1000L,
                hooks(calls).shopPending().shopTarget(new Point(30, 40)).build());

        assertEquals(List.of(
                "idle", "shopPending", "anchor", "partyRecovery", "targetRecovery",
                "mapChange", "shopPending", "shopTick", "shopTarget", "shopDelay", "core:false:30,40"), calls);
    }

    @Test
    void followIdleFastPathConsumesBeforeFinalMovementCore() {
        List<String> calls = new ArrayList<>();

        AgentMovementOnlyTickService.stepMovementOnly(
                entry(),
                new Point(10, 20),
                true,
                1000L,
                hooks(calls).followIdleConsumes().build());

        assertEquals(List.of(
                "idle", "shopPending", "followMap", "anchor", "partyRecovery",
                "targetRecovery", "mapChange", "shopPending", "followIdle:1000"), calls);
    }

    @Test
    void fallsThroughToMovementCore() {
        List<String> calls = new ArrayList<>();

        AgentMovementOnlyTickService.stepMovementOnly(
                entry(),
                new Point(10, 20),
                true,
                1000L,
                hooks(calls).build());

        assertEquals(List.of(
                "idle", "shopPending", "followMap", "anchor", "partyRecovery",
                "targetRecovery", "mapChange", "shopPending", "followIdle:1000", "core:true:10,20"), calls);
    }

    private static HookBuilder hooks(List<String> calls) {
        return new HookBuilder(calls);
    }

    private static AgentRuntimeEntry entry() {
        return new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
    }

    private static final class HookBuilder {
        private final List<String> calls;
        private boolean idleConsumes;
        private boolean followMapConsumes;
        private boolean partyRecoveryConsumes;
        private boolean targetRecoveryConsumes;
        private boolean mapChangeConsumes;
        private boolean shopPending;
        private boolean shopTickConsumes;
        private int shopDelay;
        private Point shopTarget;
        private boolean followIdleConsumes;

        private HookBuilder(List<String> calls) {
            this.calls = calls;
        }

        private HookBuilder idleConsumes() {
            idleConsumes = true;
            return this;
        }

        private HookBuilder followMapConsumes() {
            followMapConsumes = true;
            return this;
        }

        private HookBuilder partyRecoveryConsumes() {
            partyRecoveryConsumes = true;
            return this;
        }

        private HookBuilder targetRecoveryConsumes() {
            targetRecoveryConsumes = true;
            return this;
        }

        private HookBuilder mapChangeConsumes() {
            mapChangeConsumes = true;
            return this;
        }

        private HookBuilder shopPending() {
            shopPending = true;
            return this;
        }

        private HookBuilder shopDelay(int delay) {
            shopDelay = delay;
            return this;
        }

        private HookBuilder shopTarget(Point target) {
            shopTarget = target;
            return this;
        }

        private HookBuilder followIdleConsumes() {
            followIdleConsumes = true;
            return this;
        }

        private AgentMovementOnlyTickService.MovementOnlyHooks build() {
            return new AgentMovementOnlyTickService.MovementOnlyHooks(
                    (entry, agent) -> {
                        calls.add("idle");
                        return idleConsumes;
                    },
                    (entry, agent) -> {
                        calls.add("shopPending");
                        return shopPending;
                    },
                    (entry, agent, leader) -> {
                        calls.add("followMap");
                        return followMapConsumes;
                    },
                    (entry, leader) -> {
                        calls.add("anchor");
                        return leader;
                    },
                    (entry, agent, anchor) -> {
                        calls.add("partyRecovery");
                        return partyRecoveryConsumes;
                    },
                    (entry, agent, target) -> {
                        calls.add("targetRecovery");
                        return targetRecoveryConsumes;
                    },
                    (entry, agent) -> {
                        calls.add("mapChange");
                        return mapChangeConsumes;
                    },
                    (entry, agent) -> {
                        calls.add("shopTick");
                        return shopTickConsumes;
                    },
                    entry -> {
                        calls.add("shopTarget");
                        return shopTarget;
                    },
                    entry -> {
                        calls.add("shopDelay");
                        return shopDelay;
                    },
                    (entry, agent, target, nowMs) -> {
                        calls.add("followIdle:" + nowMs);
                        return followIdleConsumes;
                    },
                    (entry, target, runAiTick) ->
                            calls.add("core:" + runAiTick + ":" + target.x + "," + target.y));
        }
    }
}

