package server.agents.capabilities.partyquest.lpq;

import org.junit.jupiter.api.Test;
import scripting.reactor.ReactorActionManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLpqReactorScriptContractTest {
    private static final List<Integer> LPQ_ONE_SHOT_REACTORS = List.of(
            2_200_002, 2_201_001, 2_201_002, 2_201_003, 2_202_003, 2_202_004);

    @Test
    void everyLpqReactorSideEffectHasAnEventInstanceClaim() throws IOException {
        for (int reactorId : LPQ_ONE_SHOT_REACTORS) {
            Path script = Path.of("scripts", "reactor", reactorId + ".js");
            String source = Files.readString(script);
            assertTrue(source.contains("claimEventReactorAction(\"lpq\")"),
                    () -> "LPQ reactor " + reactorId + " lacks a one-shot event claim");
        }
    }

    @Test
    void coordinatorAndScriptsShareTheSamePerObjectClaimKey() {
        assertEquals("reactorAction:lpq:922010501:1000000004",
                ReactorActionManager.eventReactorActionKey(
                        "lpq", 922_010_501, 1_000_000_004));
    }

    @Test
    void lpqBonusRetainsTheAuthoredOneMinuteTimer() throws IOException {
        String script = Files.readString(Path.of("scripts/event/LudiPQ.js"));

        assertTrue(script.contains("var eventTime = 45;"));
        assertTrue(script.contains("var bonusTime = 1;"));
        assertTrue(script.contains("eim.startEventTimer(bonusTime * 60000);"));
        assertEquals(1, occurrences(script, "eim.getInstanceMap(922010500).resetPQ(level);"));
    }

    private static int occurrences(String source, String value) {
        return (source.length() - source.replace(value, "").length()) / value.length();
    }
}
