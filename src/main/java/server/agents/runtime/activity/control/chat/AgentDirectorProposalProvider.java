package server.agents.runtime.activity.control.chat;

import server.agents.runtime.activity.control.AgentDirectorExecutiveView;

import java.util.Optional;

@FunctionalInterface
public interface AgentDirectorProposalProvider {
    Optional<AgentDirectorModelSelection> select(
            AgentDirectorExecutiveView view, String operatorPrompt);
}
