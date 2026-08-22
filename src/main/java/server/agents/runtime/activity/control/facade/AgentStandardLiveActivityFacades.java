package server.agents.runtime.activity.control.facade;

import client.Character;
import server.agents.plans.AgentPlanSessionHandle;
import server.agents.plans.AgentUniversalPlanRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.session.AgentActivityExitResult;
import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.activity.session.AgentActivityRollbackPort;
import server.agents.runtime.activity.session.AgentActivitySourcePort;
import server.agents.runtime.activity.session.adapter.FieldActivitySessionAdapter;
import server.agents.runtime.activity.session.adapter.PartyQuestActivitySessionAdapter;
import server.agents.runtime.activity.session.adapter.QuestPlanActivitySessionAdapter;
import server.agents.runtime.activity.session.adapter.TownLifeActivitySessionAdapter;
import server.agents.runtime.commerce.AgentCommerceSessionRegistryRuntime;
import server.agents.runtime.field.AgentFieldActivityRuntime;

import java.util.List;

/** Current live facades and explicit rollback readiness for all primary systems. */
public final class AgentStandardLiveActivityFacades {
    private AgentStandardLiveActivityFacades() { }

    public static AgentLiveActivityFacadeRegistry registry() {
        return new AgentLiveActivityFacadeRegistry(List.of(
                provider(AgentActivityKind.QUESTING, AgentStandardLiveActivityFacades::questing),
                provider(AgentActivityKind.HUNTING, AgentStandardLiveActivityFacades::hunting),
                provider(AgentActivityKind.TOWN_LIFE, AgentStandardLiveActivityFacades::townLife),
                provider(AgentActivityKind.COMMERCE, AgentStandardLiveActivityFacades::commerce),
                provider(AgentActivityKind.PARTY_QUEST, AgentStandardLiveActivityFacades::partyQuest)));
    }

    private static AgentLiveActivityFacade questing(AgentRuntimeEntry entry, Character agent) {
        QuestPlanActivitySessionAdapter adapter =
                new QuestPlanActivitySessionAdapter(entry, agent, null);
        AgentActivityRollbackPort rollback = (sessionId, nowMs) -> {
            AgentPlanSessionHandle handle = AgentUniversalPlanRuntime.sessionHandle(entry);
            if (handle == null || !handle.sessionId().equals(sessionId)) {
                return AgentActivityRollbackPort.Result.rejected("quest source session is not retained");
            }
            return AgentUniversalPlanRuntime.resumeSession(entry, handle, nowMs)
                    ? AgentActivityRollbackPort.Result.resumed("quest session resumed")
                    : AgentActivityRollbackPort.Result.rejected("quest session is not suspended");
        };
        return new AgentLiveActivityFacade(AgentActivityKind.QUESTING, adapter, adapter,
                rollback, true, "suspend-at-step-boundary and exact-session resume are available");
    }

    private static AgentLiveActivityFacade hunting(AgentRuntimeEntry entry, Character agent) {
        FieldActivitySessionAdapter adapter = new FieldActivitySessionAdapter(entry, agent, null, null);
        AgentActivityRollbackPort rollback = adapter::resumeExact;
        return new AgentLiveActivityFacade(AgentActivityKind.HUNTING, adapter, adapter,
                rollback, true, "exact field session suspension and resume are available");
    }

    private static AgentLiveActivityFacade townLife(AgentRuntimeEntry entry, Character agent) {
        TownLifeActivitySessionAdapter adapter =
                new TownLifeActivitySessionAdapter(entry, agent, null, null, agent.getId());
        return new AgentLiveActivityFacade(AgentActivityKind.TOWN_LIFE, adapter, adapter,
                adapter::resumeExact, true,
                "TownLife finishes its activity then retains an exact suspended session");
    }

    private static AgentLiveActivityFacade commerce(AgentRuntimeEntry entry, Character agent) {
        int characterId = agent.getId();
        AgentActivitySourcePort source = new AgentActivitySourcePort() {
            @Override public server.agents.runtime.activity.session.AgentActivitySessionSnapshot
                    snapshot(long nowMs) {
                return AgentCommerceSessionRegistryRuntime.snapshot(characterId, nowMs);
            }
            @Override public AgentActivityExitResult requestGracefulExit(
                    String reason, long nowMs, long deadlineMs) {
                return AgentCommerceSessionRegistryRuntime.suspendExact(characterId, reason, nowMs);
            }
        };
        return new AgentLiveActivityFacade(AgentActivityKind.COMMERCE, source,
                nowMs -> AgentCommerceSessionRegistryRuntime.terminalOutcome(characterId, nowMs),
                (sessionId, nowMs) -> AgentCommerceSessionRegistryRuntime.resumeExact(
                        characterId, sessionId, nowMs)
                        ? AgentActivityRollbackPort.Result.resumed("Commerce session resumed")
                        : AgentActivityRollbackPort.Result.rejected(
                        "Commerce source session is not retained and suspended"),
                true, "Commerce checkpoint retains exact suspended market session state");
    }

    private static AgentLiveActivityFacade partyQuest(AgentRuntimeEntry entry, Character agent) {
        PartyQuestActivitySessionAdapter adapter =
                new PartyQuestActivitySessionAdapter(agent.getId(), null);
        return new AgentLiveActivityFacade(AgentActivityKind.PARTY_QUEST, adapter, adapter,
                adapter::resumeExact, true,
                "KPQ aggregate pauses and resumes the exact party session");
    }

    private static AgentLiveActivityFacade incomplete(
            AgentActivityKind kind,
            AgentActivitySourcePort source,
            server.agents.runtime.activity.session.AgentActivityOutcomePort outcome,
            String evidence) {
        return new AgentLiveActivityFacade(kind, source, outcome,
                (sessionId, nowMs) -> AgentActivityRollbackPort.Result.rejected(evidence),
                false, evidence);
    }

    private static AgentLiveActivityFacadeProvider provider(
            AgentActivityKind kind, Binder binder) {
        return new AgentLiveActivityFacadeProvider() {
            @Override public AgentActivityKind kind() { return kind; }
            @Override public AgentLiveActivityFacade bind(
                    AgentRuntimeEntry entry, Character agent) { return binder.bind(entry, agent); }
        };
    }

    @FunctionalInterface
    private interface Binder {
        AgentLiveActivityFacade bind(AgentRuntimeEntry entry, Character agent);
    }
}
