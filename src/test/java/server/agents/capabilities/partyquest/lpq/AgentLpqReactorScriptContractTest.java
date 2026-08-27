package server.agents.capabilities.partyquest.lpq;

import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataProviderFactory;
import provider.DataTool;
import provider.wz.WZFiles;
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

    @Test
    void everyLpqBoxMapIsAuthoredWithOneShotReactors() {
        for (int mapId : List.of(
                922_010_200, 922_010_201, 922_010_300,
                922_010_501, 922_010_502, 922_010_503,
                922_010_504, 922_010_505, 922_010_506,
                922_010_700, 922_011_000)) {
            Data map = DataProviderFactory.getDataProvider(WZFiles.MAP)
                    .getData("Map/Map9/" + mapId + ".img");
            Data reactors = map.getChildByPath("reactor");
            assertTrue(reactors.getChildren().stream().allMatch(reactor ->
                    DataTool.getInt("reactorTime", reactor, 0) == -1),
                    () -> "LPQ boxes must not respawn during map " + mapId);
        }
    }

    private static int occurrences(String source, String value) {
        return (source.length() - source.replace(value, "").length()) / value.length();
    }
}
