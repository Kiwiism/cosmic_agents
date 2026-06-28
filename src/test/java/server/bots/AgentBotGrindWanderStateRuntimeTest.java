package server.bots;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotGrindWanderStateRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotGrindWanderStateRuntimeTest {
    @Test
    void adaptsAndNormalizesWanderDirection() {
        BotEntry entry = new BotEntry(null, null, null);

        assertEquals(0, AgentBotGrindWanderStateRuntime.wanderDirection(entry));

        AgentBotGrindWanderStateRuntime.setWanderDirection(entry, 10);
        assertEquals(1, AgentBotGrindWanderStateRuntime.wanderDirection(entry));

        AgentBotGrindWanderStateRuntime.setWanderDirection(entry, -10);
        assertEquals(-1, AgentBotGrindWanderStateRuntime.wanderDirection(entry));

        AgentBotGrindWanderStateRuntime.clearWanderDirection(entry);
        assertEquals(0, AgentBotGrindWanderStateRuntime.wanderDirection(entry));
    }

    @Test
    void ensureWanderDirectionKeepsExistingDirectionOrChoosesOne() {
        BotEntry entry = new BotEntry(null, null, null);

        int chosen = AgentBotGrindWanderStateRuntime.ensureWanderDirection(entry);

        assertTrue(chosen == -1 || chosen == 1);
        assertEquals(chosen, AgentBotGrindWanderStateRuntime.wanderDirection(entry));
        assertEquals(chosen, AgentBotGrindWanderStateRuntime.ensureWanderDirection(entry));
    }
}
