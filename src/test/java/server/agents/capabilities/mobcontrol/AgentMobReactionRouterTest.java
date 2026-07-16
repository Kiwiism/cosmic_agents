package server.agents.capabilities.mobcontrol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentMobReactionRouterTest {
    @Test
    void parsesModesCaseInsensitivelyAndRejectsInvalidStartupValue() {
        assertEquals(AgentMobReactionMode.OFF, AgentMobReactionMode.parse("off"));
        assertEquals(AgentMobReactionMode.SYNTHETIC, AgentMobReactionMode.parse("Synthetic"));
        assertEquals(AgentMobReactionMode.PHYSICS, AgentMobReactionMode.parse("PHYSICS"));
        assertThrows(IllegalArgumentException.class, () -> AgentMobReactionMode.parse("both"));
    }

    @Test
    void eachModeResolvesToExactlyOneStrategy() {
        assertInstanceOf(OffMobReactionStrategy.class,
                AgentMobReactionRouter.strategy(AgentMobReactionMode.OFF));
        assertInstanceOf(SyntheticMobReactionStrategy.class,
                AgentMobReactionRouter.strategy(AgentMobReactionMode.SYNTHETIC));
        assertInstanceOf(PhysicsMobReactionStrategy.class,
                AgentMobReactionRouter.strategy(AgentMobReactionMode.PHYSICS));
    }
}
