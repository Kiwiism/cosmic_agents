package server.agents.integration.cosmic;

import server.agents.observer.SpectatorAgentSignalService;
import server.observer.ObserverInterestAdapter;

public final class AgentObserverInterestAdapter implements ObserverInterestAdapter {
    @Override
    public void sampleWorld(int world) {
        SpectatorAgentSignalService.sampleWorld(world);
    }
}
