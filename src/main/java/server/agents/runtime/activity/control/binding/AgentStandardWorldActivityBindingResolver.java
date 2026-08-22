package server.agents.runtime.activity.control.binding;

import client.Character;
import server.agents.capabilities.partyquest.kpq.AgentKpqDefinition;
import server.agents.capabilities.partyquest.kpq.AgentKpqLobbyAdmissionRuntime;
import server.agents.capabilities.townlife.AgentTownLifeAdmissionMode;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.control.facade.AgentLiveActivityFacade;
import server.agents.runtime.activity.control.facade.AgentLiveActivityFacadeRegistry;
import server.agents.runtime.activity.session.AgentActivityExitResult;
import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.activity.session.AgentActivityRollbackPort;
import server.agents.runtime.activity.session.AgentActivitySessionSnapshot;
import server.agents.runtime.activity.session.AgentActivitySourcePort;
import server.agents.runtime.activity.session.adapter.FieldActivitySessionAdapter;
import server.agents.runtime.activity.session.adapter.PartyQuestActivitySessionAdapter;
import server.agents.runtime.activity.session.adapter.QuestPlanActivitySessionAdapter;
import server.agents.runtime.activity.session.adapter.TownLifeActivitySessionAdapter;
import server.agents.runtime.field.AgentFieldAdmissionMode;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.progression.AgentVictoriaRouteRuntime;

import java.util.Set;

/** Live map-local bindings for independently admissible primary activities. */
public final class AgentStandardWorldActivityBindingResolver
        implements AgentWorldActivityBindingResolver {
    private static final Set<AgentActivityKind> SUPPORTED = Set.of(
            AgentActivityKind.QUESTING, AgentActivityKind.HUNTING,
            AgentActivityKind.TOWN_LIFE, AgentActivityKind.PARTY_QUEST);

    private final AgentWorldDirectiveRequestCompiler compiler;
    private final AgentLiveActivityFacadeRegistry facades;

    public AgentStandardWorldActivityBindingResolver(
            AgentWorldDirectiveRequestCompiler compiler,
            AgentLiveActivityFacadeRegistry facades) {
        if (compiler == null || facades == null) {
            throw new IllegalArgumentException("request compiler and live facades are required");
        }
        this.compiler = compiler;
        this.facades = facades;
    }

    public static Set<AgentActivityKind> supportedTargets() {
        return SUPPORTED;
    }

    @Override
    public AgentWorldActivityBinding bind(
            server.agents.runtime.activity.world.AgentWorldDirective directive,
            AgentRuntimeEntry entry,
            Character agent,
            AgentActivityKind sourceKind,
            String sourceSessionId) {
        AgentWorldTypedActivityRequest request = compiler.compile(directive);
        if (!SUPPORTED.contains(request.kind())) {
            throw new IllegalStateException(request.kind()
                    + " requires its aggregate admission owner before Director execution");
        }
        AgentLiveActivityFacade sourceFacade = sourceKind == null
                ? null : facades.bind(sourceKind, entry, agent);
        AgentActivitySourcePort source = sourceFacade == null ? idle(agent) : sourceFacade.source();
        AgentActivityRollbackPort rollback = sourceFacade == null
                ? (sessionId, nowMs) -> AgentActivityRollbackPort.Result.resumed("no source to restore")
                : sourceFacade.rollback();
        if (request instanceof AgentWorldTypedActivityRequest.Questing questing) {
            QuestPlanActivitySessionAdapter target =
                    new QuestPlanActivitySessionAdapter(entry, agent, questing.request());
            return binding(source, rollback, target, target, agent, 0);
        }
        if (request instanceof AgentWorldTypedActivityRequest.Hunting hunting) {
            FieldActivitySessionAdapter target = new FieldActivitySessionAdapter(
                    entry, agent, hunting.request(), AgentFieldAdmissionMode.CREATE_OR_JOIN);
            return binding(source, rollback, target, target, agent,
                    hunting.request().visit().mapId());
        }
        if (request instanceof AgentWorldTypedActivityRequest.PartyQuest partyQuest) {
            AgentWorldTypedActivityRequest.AgentPartyQuestVisitRequest visit =
                    partyQuest.request();
            PartyQuestActivitySessionAdapter target = new PartyQuestActivitySessionAdapter(
                    agent.getId(), nowMs -> AgentKpqLobbyAdmissionRuntime.requestEntry(
                    entry, agent, visit.scenarioId(), visit.partySize(),
                    visit.maximumRuns(), nowMs));
            return new AgentWorldActivityBinding(source,
                    (agentId, kind, nowMs) -> {
                        String blocker = AgentKpqLobbyAdmissionRuntime.blocker(
                                agent, visit.scenarioId(), visit.partySize(), visit.maximumRuns());
                        return blocker.isEmpty()
                                ? server.agents.runtime.activity.session.AgentActivityPreflightPort.Result.allowed()
                                : server.agents.runtime.activity.session.AgentActivityPreflightPort.Result.blocked(blocker);
                    },
                    nowMs -> kpqTransfer(entry, agent, nowMs), target, rollback, target);
        }
        AgentWorldTypedActivityRequest.TownLife town =
                (AgentWorldTypedActivityRequest.TownLife) request;
        TownLifeActivitySessionAdapter target = new TownLifeActivitySessionAdapter(
                entry, agent, town.request(), AgentTownLifeAdmissionMode.MANUAL_ONLY,
                agent.getId());
        return binding(source, rollback, target, target, agent,
                town.request().visit().townMapId());
    }

    private server.agents.runtime.activity.session.AgentActivityTransferPort.Result kpqTransfer(
            AgentRuntimeEntry entry, Character agent, long nowMs) {
        AgentVictoriaRouteRuntime.TravelOutcome outcome = AgentVictoriaRouteRuntime.travelStatus(
                entry, agent, AgentKpqDefinition.RECRUIT_MAP,
                AgentPrimitiveCapabilityGatewayRuntime.gateway(), nowMs);
        return switch (outcome.status()) {
            case ARRIVED -> server.agents.runtime.activity.session.AgentActivityTransferPort.Result.ready();
            case MOVING, PORTAL_UNAVAILABLE ->
                    server.agents.runtime.activity.session.AgentActivityTransferPort.Result.pending(
                            "traveling normally to the Kerning KPQ lobby", nowMs + 500L);
            case NO_ROUTE -> server.agents.runtime.activity.session.AgentActivityTransferPort.Result.failed(
                    "no Victoria route reaches the Kerning KPQ lobby");
        };
    }

    private AgentWorldActivityBinding binding(
            AgentActivitySourcePort source,
            AgentActivityRollbackPort rollback,
            server.agents.runtime.activity.session.AgentActivityTargetPort target,
            server.agents.runtime.activity.session.AgentActivityOutcomePort outcome,
            Character agent,
            int requiredMapId) {
        return new AgentWorldActivityBinding(source,
                (agentId, kind, nowMs) -> requiredMapId == 0 || agent.getMapId() == requiredMapId
                        ? server.agents.runtime.activity.session.AgentActivityPreflightPort.Result.allowed()
                        : server.agents.runtime.activity.session.AgentActivityPreflightPort.Result.blocked(
                        "travel must place the Agent in map " + requiredMapId + " first"),
                nowMs -> requiredMapId == 0 || agent.getMapId() == requiredMapId
                        ? server.agents.runtime.activity.session.AgentActivityTransferPort.Result.ready()
                        : server.agents.runtime.activity.session.AgentActivityTransferPort.Result.failed(
                        "Director does not hard-teleport between activity maps"),
                target, rollback, outcome);
    }

    private AgentActivitySourcePort idle(Character agent) {
        return new AgentActivitySourcePort() {
            @Override public AgentActivitySessionSnapshot snapshot(long nowMs) {
                return AgentActivitySessionSnapshot.idle(
                        AgentActivityKind.QUESTING, Integer.toString(agent.getId()));
            }
            @Override public AgentActivityExitResult requestGracefulExit(
                    String reason, long nowMs, long deadlineMs) {
                return AgentActivityExitResult.released("no source activity");
            }
        };
    }
}
