package server.agents.progression;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileAgentMushroomKingdomFarmProgressStoreTest {
    @TempDir Path directory;

    @Test
    void roundTripsBoundedFarmHistoryByCharacter() throws Exception {
        FileAgentMushroomKingdomFarmProgressStore store =
                new FileAgentMushroomKingdomFarmProgressStore(directory);
        AgentMushroomKingdomFarmProgress progress = new AgentMushroomKingdomFarmProgress(
                1, 27, 10, 3, 1_000L, 86_401_000L, 0,
                2, 1, 2_000L, "ten Yeti runs completed");

        store.save(progress);

        assertEquals(progress, store.load(27).orElseThrow());
    }
}
