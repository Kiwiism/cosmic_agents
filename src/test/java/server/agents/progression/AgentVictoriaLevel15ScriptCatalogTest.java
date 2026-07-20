package server.agents.progression;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentVictoriaLevel15ScriptCatalogTest {
    private static final Pattern TAXI_MAPS = Pattern.compile("var\\s+maps\\s*=\\s*\\[([^]]+)]");

    @Test
    void taxiSelectionsAndNativeStarterKitsMatchTheNpcScripts() throws IOException {
        String taxiScript = script(1002000);
        Matcher matcher = TAXI_MAPS.matcher(taxiScript);
        assertTrue(matcher.find(), "Lith Harbor taxi destination array is missing");
        List<Integer> destinations = Arrays.stream(matcher.group(1).split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .toList();

        for (AgentVictoriaLevel15Catalog.Career career
                : AgentVictoriaLevel15CatalogRepository.defaultRepository().catalog().careers()) {
            assertEquals(career.townMapId(), destinations.get(career.taxiSelection()),
                    "taxi selection drift for first job " + career.firstJobId());
            String instructorScript = script(career.instructorNpcId());
            for (int starterItemId : career.starterKitItemIds()) {
                assertTrue(instructorScript.contains("gainItem(" + starterItemId + ","),
                        () -> "instructor " + career.instructorNpcId()
                                + " no longer grants cataloged starter item " + starterItemId);
            }
        }
    }

    private static String script(int npcId) throws IOException {
        return Files.readString(Path.of("scripts", "npc", npcId + ".js"));
    }
}
