package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.bots.BotEntry;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentLiveTickGateRuntimeTest {
    @Test
    void commonTickConsumptionShortCircuitsRemainingLiveGates() {
        BotEntry entry = mock(BotEntry.class);
        Character agent = mock(Character.class);
        Character leader = mock(Character.class);
        Character followAnchor = mock(Character.class);
        List<String> calls = new ArrayList<>();

        try (MockedStatic<AgentCommonTickRuntime> commonTick = mockStatic(AgentCommonTickRuntime.class)) {
            commonTick.when(() -> AgentCommonTickRuntime.runCommonTickSystems(
                            any(BotEntry.class),
                            any(Character.class),
                            any(Character.class),
                            anyBoolean(),
                            any()))
                    .thenAnswer(invocation -> {
                        calls.add("common");
                        return true;
                    });

            boolean consumed = AgentLiveTickGateRuntime.tickLiveGates(
                    new AgentLiveTickGateService.Context(
                            entry,
                            agent,
                            leader,
                            followAnchor,
                            new Point(10, 20),
                            true),
                    false,
                    tickEntry -> calls.add("script"),
                    grindEntry -> calls.add("grind"),
                    followEntry -> calls.add("follow"),
                    650,
                    1200,
                    2);

            assertTrue(consumed);
            assertEquals(List.of("common"), calls);
        }
    }
}
