package server.agents.economy.activity;

import server.agents.events.AgentEvent;
import server.agents.events.AgentEventListener;

public final class ActivityCalibrationEventListener implements AgentEventListener<AgentEvent> {
    @Override
    public void onAgentEvent(AgentEvent event) {
        LiveActivityCalibrationRuntime.observe(event);
    }
}
