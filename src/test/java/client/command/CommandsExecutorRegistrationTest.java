package client.command;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandsExecutorRegistrationTest {
    @Test
    void reorganizedCommandsHaveExpectedRanksAndAliases() throws Exception {
        Map<String, Command> commands = registeredCommands();

        assertRanks(commands, 0, "language", "online", "rates", "gacha", "mapowner", "mobdrops");
        assertRanks(commands, 1, "goto", "resetap", "resetsp");
        assertRanks(commands, 2, "toggleexp", "level", "job", "meso");
        assertRanks(commands, 3, "warpto", "warphere", "expeditions", "healplayer", "papulatus");
        assertRanks(commands, 4, "givemeso", "giveitem", "hpmp", "maxhpmp");
        assertRanks(commands, 5, "reloadevents", "monitor", "mobcapture", "serverhealth");
        assertRanks(commands, 6, "spawnbot", "deleteagent", "healagent", "kpqtest", "agentpop");

        assertEquals(commands.get("help").getClass(), commands.get("commands").getClass());
        assertEquals(commands.get("warphere").getClass(), commands.get("summon").getClass());
        assertEquals(commands.get("warpto").getClass(), commands.get("follow").getClass());
    }

    @Test
    void obsoleteNamesAndRemovedCommandsAreUnavailable() throws Exception {
        Map<String, Command> commands = registeredCommands();
        for (String removed : new String[]{
                "changel", "showrates", "gachalist", "mylawn", "whatdropsfrom", "givems",
                "online2", "reach", "expeds", "healperson", "pap", "deletechar", "delchar",
                "mobreactioncapture", "expdebug", "botllm", "botperfdebug"
        }) {
            assertFalse(commands.containsKey(removed), removed + " should not be registered");
        }
        assertTrue(commands.containsKey("spawnbot"));
    }

    private static void assertRanks(Map<String, Command> commands, int rank, String... names) {
        for (String name : names) {
            assertTrue(commands.containsKey(name), name + " should be registered");
            assertEquals(rank, commands.get(name).getRank(), name + " rank");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Command> registeredCommands() throws Exception {
        Field field = CommandsExecutor.class.getDeclaredField("registeredCommands");
        field.setAccessible(true);
        return (Map<String, Command>) field.get(CommandsExecutor.getInstance());
    }
}
