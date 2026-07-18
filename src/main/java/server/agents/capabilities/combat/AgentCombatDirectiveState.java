package server.agents.capabilities.combat;

import server.agents.runtime.state.AgentCapabilityStateKey;

public final class AgentCombatDirectiveState {
    public static final AgentCapabilityStateKey<AgentCombatDirectiveState> STATE_KEY =
            new AgentCapabilityStateKey<>("combat.directive", AgentCombatDirectiveState.class,
                    AgentCombatDirectiveState::new);

    private AgentCombatDirective directive;

    public synchronized AgentCombatDirective directive() { return directive; }
    public synchronized void assign(AgentCombatDirective directive) { this.directive = directive; }
    public synchronized void clear() { directive = null; }
}
