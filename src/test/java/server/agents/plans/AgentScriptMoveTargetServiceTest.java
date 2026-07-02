package server.agents.plans;

import client.Character;
import org.junit.jupiter.api.Test;
import server.bots.BotEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentScriptMoveTargetServiceTest {
    @Test
    void defaultRadiusPreservesNearTargetRejection() {
        BotEntry entry = entryWithBotAt(new Point(100, 100));

        assertFalse(AgentScriptMoveTargetService.isCheapMoveTarget(entry, new Point(150, 150), 100, 500, 500));
    }

    @Test
    void rejectsMissingBotOrTarget() {
        assertFalse(AgentScriptMoveTargetService.isCheapMoveTarget(null, new Point(10, 20), 100, 30, 40, 100));
        assertFalse(AgentScriptMoveTargetService.isCheapMoveTarget(entryWithBotAt(new Point(1, 2)), null, 100, 30, 40, 100));
    }

    @Test
    void rejectsMissingBotPosition() {
        BotEntry entry = entryWithBotAt(null);

        assertFalse(AgentScriptMoveTargetService.isCheapMoveTarget(entry, new Point(10, 20), 100, 30, 40, 100));
    }

    @Test
    void preservesNearTargetRejectionBeforeMapChecks() {
        BotEntry entry = entryWithBotAt(new Point(100, 100));

        assertFalse(AgentScriptMoveTargetService.isCheapMoveTarget(entry, new Point(150, 150), 100, 500, 500, 100));
    }

    private static BotEntry entryWithBotAt(Point position) {
        Character owner = mock(Character.class);
        Character bot = mock(Character.class);
        when(bot.getPosition()).thenReturn(position);
        return new BotEntry(bot, owner, null);
    }
}
