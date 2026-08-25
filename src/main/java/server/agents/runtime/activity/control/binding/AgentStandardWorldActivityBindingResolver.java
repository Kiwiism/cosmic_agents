package server.agents.runtime.activity.control.binding;

import client.Character;
import server.agents.capabilities.partyquest.AgentPartyQuestRuntime;
import server.agents.capabilities.partyquest.AgentPartyQuestSystem;
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
import server.agents.integration.AgentEconomyRuntime;
import server.agents.runtime.commerce.AgentCommerceSessionRegistryRuntime;
import server.agents.runtime.commerce.AgentFileCommerceSessionStore;
import server.agents.runtime.field.AgentFieldAdmissionMode;
import java.util.Set;

/** Live map-local bindings for independently admissible primary activities. */
public final class AgentStandardWorldActivityBindingResolver
        implements AgentWorldActivityBindingResolver {
    private static final Set<AgentActivityKind> SUPPORTED = Set.of(
            AgentActivityKind.QUESTING, AgentActivityKind.HUNTING,
            AgentActivityKind.TOWN_LIFE, AgentActivityKind.COMMERCE,
            AgentActivityKind.PARTY_QUEST);

    private final AgentWorldDirectiveRequestCompiler compiler;
    private final AgentLiveActivityFacadeRegistry facades;
    private final AgentWorldMapTransfer mapTransfer;

    public AgentStandardWorldActivityBindingResolver(
            AgentWorldDirectiveRequestCompiler compiler,
            AgentLiveActivityFacadeRegistry facades) {
        this(compiler, facades, new AgentVictoriaWorldMapTransfer());
    }

    public AgentStandardWorldActivityBindingResolver(
            AgentWorldDirectiveRequestCompiler compiler,
            AgentLiveActivityFacadeRegistry facades,
            AgentWorldMapTransfer mapTransfer) {
        if (compiler == null || facades == null || mapTransfer == null) {
            throw new IllegalArgumentException(
                    "request compiler, live facades, and map transfer are required");
        }
        this.compiler = compiler;
        this.facades = facades;
        this.mapTransfer = mapTransfer;
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
            return binding(source, rollback, target, target, entry, agent, 0);
        }
        if (request instanceof AgentWorldTypedActivityRequest.Hunting hunting) {
            FieldActivitySessionAdapter target = new FieldActivitySessionAdapter(
                    entry, agent, hunting.request(), AgentFieldAdmissionMode.CREATE_OR_JOIN);
            return binding(source, rollback, target, target, entry, agent,
                    hunting.request().visit().mapId());
        }
        if (request instanceof AgentWorldTypedActivityRequest.PartyQuest partyQuest) {
            AgentWorldTypedActivityRequest.AgentPartyQuestVisitRequest visit =
                    partyQuest.request();
            AgentPartyQuestSystem system = AgentPartyQuestRuntime.requireSystem(visit.scenarioId());
            PartyQuestActivitySessionAdapter target = new PartyQuestActivitySessionAdapter(
                    agent.getId(), nowMs -> system.requestEntry(
                    entry, agent, visit.scenarioId(), visit.partySize(),
                    visit.maximumRuns(), nowMs));
            return new AgentWorldActivityBinding(source,
                    (agentId, kind, nowMs) -> {
                        String blocker = system.entryBlocker(
                                agent, visit.scenarioId(), visit.partySize(), visit.maximumRuns());
                        return blocker.isEmpty()
                                ? server.agents.runtime.activity.session.AgentActivityPreflightPort.Result.allowed()
                                : server.agents.runtime.activity.session.AgentActivityPreflightPort.Result.blocked(blocker);
                    },
                    nowMs -> mapTransfer.travel(
                            entry, agent, system.definition().recruitMapId(), nowMs),
                    target, rollback, target);
        }
        if (request instanceof AgentWorldTypedActivityRequest.Commerce commerce) {
            var sessions = AgentEconomyRuntime.sessionPort().orElseThrow(() ->
                    new IllegalStateException("managed Commerce runtime is not installed"));
            var target = AgentCommerceSessionRegistryRuntime.prepare(
                    agent.getId(), sessions, AgentFileCommerceSessionStore.runtimeDefault(),
                    commerce.request());
            return binding(source, rollback, target, target, entry, agent,
                    constants.id.MapId.FM_ENTRANCE);
        }
        AgentWorldTypedActivityRequest.TownLife town =
                (AgentWorldTypedActivityRequest.TownLife) request;
        TownLifeActivitySessionAdapter target = new TownLifeActivitySessionAdapter(
                entry, agent, town.request(), AgentTownLifeAdmissionMode.MANUAL_ONLY,
                agent.getId());
        return binding(source, rollback, target, target, entry, agent,
                town.request().visit().townMapId());
    }

    private AgentWorldActivityBinding binding(
            AgentActivitySourcePort source,
            AgentActivityRollbackPort rollback,
            server.agents.runtime.activity.session.AgentActivityTargetPort target,
            server.agents.runtime.activity.session.AgentActivityOutcomePort outcome,
            AgentRuntimeEntry entry,
            Character agent,
            int requiredMapId) {
        return new AgentWorldActivityBinding(source,
                (agentId, kind, nowMs) ->
                        server.agents.runtime.activity.session.AgentActivityPreflightPort.Result.allowed(),
                nowMs -> requiredMapId == 0 || agent.getMapId() == requiredMapId
                        ? server.agents.runtime.activity.session.AgentActivityTransferPort.Result.ready()
                        : mapTransfer.travel(entry, agent, requiredMapId, nowMs),
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
