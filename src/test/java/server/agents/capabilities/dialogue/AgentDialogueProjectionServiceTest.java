package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentDialogueProjectionServiceTest {
    @Test
    void suppressesWithoutObserverAndAppliesCooldownWithObserver() {
        List<AgentDialogueIntentEvent> projected = new ArrayList<>();
        boolean[] observed = {false};
        AgentDialogueProjectionService service = new AgentDialogueProjectionService(
                (agentId, audience) -> observed[0], projected::add);
        AgentDialogueIntentEvent first = intent(100);
        service.onAgentEvent(first);
        observed[0] = true;
        service.onAgentEvent(first);
        service.onAgentEvent(intent(150));
        service.onAgentEvent(intent(201));

        assertEquals(2, projected.size());
    }

    private static AgentDialogueIntentEvent intent(long nowMs) {
        return new AgentDialogueIntentEvent(1, nowMs, "supply.hp.low",
                AgentDialogueAudience.NEARBY_REAL_PLAYER, "hp-low", 100, Map.of());
    }
}
