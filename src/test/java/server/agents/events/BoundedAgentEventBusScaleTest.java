package server.agents.events;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BoundedAgentEventBusScaleTest {
    private static final int AGENTS = 2000;
    private static final int CAPACITY = 8;

    @Test
    void twoThousandAgentQueuesRemainStrictlyBoundedAndDrainable() {
        List<BoundedAgentEventBus> buses = new ArrayList<>(AGENTS);
        long delivered[] = {0L};
        for (int agentId = 1; agentId <= AGENTS; agentId++) {
            BoundedAgentEventBus bus = new BoundedAgentEventBus(CAPACITY);
            bus.subscribe("scale.test", ignored -> delivered[0]++);
            for (int sequence = 0; sequence < CAPACITY * 2; sequence++) {
                bus.publish(new AgentDomainEvent(agentId, sequence, "scale.test",
                        String.valueOf(sequence), Map.of()));
            }
            buses.add(bus);
        }

        assertEquals((long) AGENTS * CAPACITY,
                buses.stream().map(BoundedAgentEventBus::snapshot)
                        .mapToLong(AgentEventBusSnapshot::queued).sum());
        assertEquals((long) AGENTS * CAPACITY,
                buses.stream().map(BoundedAgentEventBus::snapshot)
                        .mapToLong(AgentEventBusSnapshot::dropped).sum());

        for (BoundedAgentEventBus bus : buses) {
            assertEquals(CAPACITY, bus.drain(CAPACITY));
            assertEquals(0, bus.snapshot().queued());
            bus.close();
        }
        assertEquals((long) AGENTS * CAPACITY, delivered[0]);
    }
}
