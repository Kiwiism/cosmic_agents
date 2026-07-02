package server.agents.capabilities.combat;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotGrindSearchStateRuntime;
import server.bots.BotEntry;
import server.life.Monster;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class AgentGrindTargetSearchServiceTest {
    @Test
    void keepsCurrentTargetAndPlanWhenAiTickIsNotDue() {
        BotEntry entry = new BotEntry(mock(Character.class), mock(Character.class), null);
        Character agent = mock(Character.class);
        Monster current = mock(Monster.class);
        AgentAttackPlan plan = mock(AgentAttackPlan.class);
        AtomicInteger searches = new AtomicInteger();

        AgentGrindTargetSearchService.SearchResult result = AgentGrindTargetSearchService.searchIfDue(
                entry,
                agent,
                current,
                plan,
                false,
                1_000L,
                hooks(searches, mock(Monster.class)));

        assertSame(current, result.target());
        assertSame(plan, result.attackPlan());
        assertEquals(0, searches.get());
        assertEquals(0L, AgentBotGrindSearchStateRuntime.nextSearchAtMs(entry));
    }

    @Test
    void adoptsSearchedTargetAndInvalidatesPlanWhenCurrentTargetIsMissing() {
        BotEntry entry = new BotEntry(mock(Character.class), mock(Character.class), null);
        Character agent = mock(Character.class);
        Monster searched = mock(Monster.class);
        AtomicInteger searches = new AtomicInteger();

        AgentGrindTargetSearchService.SearchResult result = AgentGrindTargetSearchService.searchIfDue(
                entry,
                agent,
                null,
                null,
                true,
                1_000L,
                hooks(searches, searched));

        assertSame(searched, result.target());
        assertNull(result.attackPlan());
        assertEquals(1, searches.get());
        assertEquals(1_250L, AgentBotGrindSearchStateRuntime.nextSearchAtMs(entry));
    }

    private static AgentGrindTargetSearchService.SearchHooks hooks(AtomicInteger searches, Monster target) {
        return new AgentGrindTargetSearchService.SearchHooks(
                (entry, agent) -> {
                    searches.incrementAndGet();
                    return target;
                },
                (entry, agent) -> {
                    searches.incrementAndGet();
                    return target;
                },
                250L);
    }
}
