package server.agents.runtime.activity.control;

import server.agents.runtime.activity.world.AgentFileWorldDirectiveInbox;
import server.agents.runtime.activity.world.AgentFileWorldDirectorSessionStore;
import server.agents.runtime.activity.world.AgentWorldDirective;
import server.agents.runtime.activity.world.AgentWorldDirectiveEnvelope;
import server.agents.runtime.activity.world.AgentWorldDirectiveInbox;
import server.agents.runtime.activity.world.AgentWorldDirectiveSource;
import server.agents.runtime.activity.world.AgentWorldDirectiveStatus;
import server.agents.runtime.activity.world.AgentWorldDirectiveType;
import server.agents.runtime.activity.world.AgentWorldDirectorMode;
import server.agents.runtime.activity.world.AgentWorldDirectorSession;
import server.agents.runtime.activity.world.AgentWorldDirectorSessionStore;

/** Headless operator API. It owns durable intent, never child-system execution. */
public final class AgentWorldDirectorControlService {
    private final AgentWorldDirectorSessionStore sessions;
    private final AgentWorldDirectiveInbox directives;

    public AgentWorldDirectorControlService(
            AgentWorldDirectorSessionStore sessions,
            AgentWorldDirectiveInbox directives) {
        if (sessions == null || directives == null) {
            throw new IllegalArgumentException("Director session and directive stores are required");
        }
        this.sessions = sessions;
        this.directives = directives;
    }

    public static AgentWorldDirectorControlService runtimeDefault() {
        return new AgentWorldDirectorControlService(
                AgentFileWorldDirectorSessionStore.runtimeDefault(),
                AgentFileWorldDirectiveInbox.runtimeDefault());
    }

    public AgentWorldDirectorSession setMode(
            int agentId, AgentWorldDirectorMode mode, String reason, long nowMs) {
        if (mode == null || agentId <= 0 || nowMs < 0L) {
            throw new IllegalArgumentException("Agent, mode, and current time are required");
        }
        AgentWorldDirectorSession current = sessions.load(agentId)
                .orElseGet(() -> AgentWorldDirectorSession.create(agentId, mode, nowMs));
        AgentWorldDirectorSession updated = current.mode() == mode
                ? current : current.withMode(mode, reason, nowMs);
        sessions.save(updated);
        return updated;
    }

    public AgentWorldDirectivePreview preview(AgentWorldDirective directive, long nowMs) {
        if (directive == null) throw new IllegalArgumentException("directive is required");
        AgentWorldDirectorMode mode = sessions.load(directive.agentId())
                .map(AgentWorldDirectorSession::mode).orElse(AgentWorldDirectorMode.DISABLED);
        if (directive.expiredAt(nowMs)) {
            return new AgentWorldDirectivePreview(directive, mode, false, "directive is expired");
        }
        if (directive.type() == AgentWorldDirectiveType.SET_MODE) {
            return new AgentWorldDirectivePreview(directive, mode, true, "mode changes are permitted");
        }
        if (mode == AgentWorldDirectorMode.EMERGENCY_HOLD
                && directive.type() != AgentWorldDirectiveType.RESUME) {
            return new AgentWorldDirectivePreview(
                    directive, mode, false, "Agent is in Emergency Hold");
        }
        boolean operator = directive.source() == AgentWorldDirectiveSource.OPERATOR;
        boolean permitted = operator ? mode.acceptsOperatorDirectives()
                : mode.allowsAutomaticProposals();
        return new AgentWorldDirectivePreview(directive, mode, permitted,
                permitted ? "directive is eligible for durable submission"
                        : "Director mode does not authorize this directive source");
    }

    public AgentWorldDirectiveEnvelope submit(AgentWorldDirective directive, long nowMs) {
        AgentWorldDirectivePreview preview = preview(directive, nowMs);
        if (!preview.accepted()) throw new IllegalStateException(preview.reason());
        return directives.submit(directive, nowMs);
    }

    public AgentWorldDirectiveEnvelope cancel(
            int agentId, String directiveId, String reason, long nowMs) {
        return directives.resolve(agentId, directiveId,
                AgentWorldDirectiveStatus.CANCELLED, reason, nowMs);
    }

    public AgentWorldControlStatus status(int agentId) {
        AgentWorldDirectorSession session = sessions.load(agentId)
                .orElseThrow(() -> new IllegalStateException("no World Director session for Agent"));
        return new AgentWorldControlStatus(session, directives.list(agentId));
    }
}
