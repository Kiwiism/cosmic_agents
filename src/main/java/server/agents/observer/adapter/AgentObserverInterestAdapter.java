package server.agents.observer.adapter;

import server.agents.observer.SpectatorAgentSignalService;
import server.observer.ObserverInterestAdapter;

public final class AgentObserverInterestAdapter implements ObserverInterestAdapter {
    @Override
    public void sampleWorld(int world) {
        SpectatorAgentSignalService.sampleWorld(world);
    }
}
