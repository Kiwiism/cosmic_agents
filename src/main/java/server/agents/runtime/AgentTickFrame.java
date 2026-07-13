package server.agents.runtime;

public interface AgentTickFrame {
    AgentTickSliceResult runNextSlice();

    boolean isComplete();
}
