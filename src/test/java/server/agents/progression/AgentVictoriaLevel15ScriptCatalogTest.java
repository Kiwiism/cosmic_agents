package server.agents.progression;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentVictoriaLevel15ScriptCatalogTest {
    private static final Pattern TAXI_MAPS = Pattern.compile("var\\s+maps\\s*=\\s*\\[([^]]+)]");
    private static final Map<Integer, Integer> SHARED_TOWN_TAXIS = Map.of(
            100000000, 1012000,
            101000000, 1032000,
            102000000, 1022001,
            103000000, 1052016,
            120000000, 1092014);

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

    @Test
    void everySharedTownTaxiSelectionMatchesItsNpcScript() throws IOException {
        for (Map.Entry<Integer, Integer> taxi : SHARED_TOWN_TAXIS.entrySet()) {
            List<Integer> destinations = taxiDestinations(taxi.getValue());
            AgentVictoriaSharedQuestPackCatalog.Town town =
                    AgentVictoriaSharedQuestPackCatalog.town(taxi.getKey());

            assertEquals(taxi.getValue(), town.taxiNpcId(),
                    "taxi NPC drift for town " + taxi.getKey());
            for (int destinationMapId : SHARED_TOWN_TAXIS.keySet()) {
                if (destinationMapId == taxi.getKey()) {
                    continue;
                }
                int selection = town.selectionFor(destinationMapId);
                assertTrue(selection >= 0 && selection < destinations.size(),
                        "missing taxi selection from " + taxi.getKey()
                                + " to " + destinationMapId);
                assertEquals(destinationMapId, destinations.get(selection),
                        "taxi selection drift from " + taxi.getKey()
                                + " to " + destinationMapId);
            }
        }
    }

    private static List<Integer> taxiDestinations(int npcId) throws IOException {
        Matcher matcher = TAXI_MAPS.matcher(script(npcId));
        assertTrue(matcher.find(), "taxi destination array is missing for NPC " + npcId);
        return Arrays.stream(matcher.group(1).split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .toList();
    }

    private static String script(int npcId) throws IOException {
        return Files.readString(Path.of("scripts", "npc", npcId + ".js"));
    }
}
