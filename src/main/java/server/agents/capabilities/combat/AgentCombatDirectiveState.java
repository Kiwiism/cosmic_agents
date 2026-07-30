package server.agents.capabilities.combat;

import server.agents.runtime.state.AgentCapabilityStateKey;

import java.util.Objects;

public final class AgentCombatDirectiveState {
    public static final AgentCapabilityStateKey<AgentCombatDirectiveState> STATE_KEY =
            new AgentCapabilityStateKey<>("combat.directive", AgentCombatDirectiveState.class,
                    AgentCombatDirectiveState::new);

    private AgentCombatDirective directive;

    public synchronized AgentCombatDirective directive() { return directive; }

    public synchronized boolean assign(AgentCombatDirective directive) {
        if (Objects.equals(this.directive, directive)) {
            return false;
        }
        this.directive = directive;
        return true;
    }

    public synchronized void clear() { directive = null; }
}
