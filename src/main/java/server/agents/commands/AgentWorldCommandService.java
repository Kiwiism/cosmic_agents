package server.agents.commands;

import client.Character;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.cosmic.CosmicAgentWorldContextFactory;
import server.agents.plans.AgentPlanRepository;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.activity.world.AgentWorldActivityAdapterCatalog;
import server.agents.runtime.activity.world.AgentWorldActivityIntent;
import server.agents.runtime.activity.world.AgentWorldControlledRoute;
import server.agents.runtime.activity.world.AgentWorldControlledRouteValidator;
import server.agents.runtime.activity.world.AgentWorldDirectorJournalEntry;
import server.agents.runtime.activity.world.AgentWorldDirectorPreparationConfig;
import server.agents.runtime.activity.world.AgentWorldDirectorSession;
import server.agents.runtime.activity.world.AgentWorldShadowReport;
import server.agents.runtime.activity.world.AgentWorldShadowSessionService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Observation-only GM facade for World Director preparation. */
public final class AgentWorldCommandService {
    private static final AgentWorldDirectorPreparationConfig CONFIG =
            AgentWorldDirectorPreparationConfig.defaults();
    private static final AgentWorldShadowSessionService SHADOW =
            AgentWorldShadowSessionService.runtimeDefault();

    private AgentWorldCommandService() {
    }

    public static List<String> execute(Character operator, String[] params, long nowMs) {
        if (operator == null || nowMs < 0L) return List.of("A live operator is required.");
        if (!CONFIG.commandDrivenShadowEnabled()) {
            return List.of("World Director preparation diagnostics are disabled.");
        }
        if (params == null || params.length == 0 || "help".equals(params[0])) return help();
        try {
            return switch (params[0]) {
                case "inspect" -> inspect(operator, params, nowMs, false);
                case "explain" -> inspect(operator, params, nowMs, true);
                case "shadow" -> shadow(operator, params, nowMs);
                case "adapters" -> adapters();
                case "routes" -> routes();
                default -> help();
            };
        } catch (IllegalArgumentException | IllegalStateException failure) {
            return List.of("World Director preparation: " + failure.getMessage());
        }
    }

    private static List<String> inspect(
            Character operator, String[] params, long nowMs, boolean explain) {
        if (params.length != 2) return help();
        Selection selection = select(operator, params[1]);
        AgentWorldShadowReport report = SHADOW.inspect(CosmicAgentWorldContextFactory.capture(
                selection.entry(), selection.agent(), nowMs));
        return reportLines(report, explain);
    }

    private static List<String> shadow(Character operator, String[] params, long nowMs) {
        if (params.length < 3) return help();
        Selection selection = select(operator, params[2]);
        return switch (params[1]) {
            case "start" -> {
                AgentWorldShadowReport report = SHADOW.start(
                        CosmicAgentWorldContextFactory.capture(
                                selection.entry(), selection.agent(), nowMs));
                ArrayList<String> lines = new ArrayList<>();
                lines.add("Shadow observation enabled for " + selection.agent().getName()
                        + "; sampling is command-driven and cannot own the Agent.");
                lines.addAll(reportLines(report, true));
                yield List.copyOf(lines);
            }
            case "sample" -> reportLines(SHADOW.sample(
                    CosmicAgentWorldContextFactory.capture(
                            selection.entry(), selection.agent(), nowMs)), true);
            case "status" -> shadowStatus(selection.agent().getId());
            case "report" -> shadowReport(selection.agent().getId());
            case "stop" -> {
                AgentWorldDirectorSession stopped = SHADOW.stop(selection.agent().getId(),
                        "operator stopped command-driven shadow observation", nowMs);
                yield List.of("Shadow observation paused for " + selection.agent().getName()
                        + " after " + stopped.observationCount() + " samples.");
            }
            default -> help();
        };
    }

    private static List<String> shadowStatus(int agentId) {
        AgentWorldDirectorSession session = SHADOW.session(agentId).orElse(null);
        if (session == null) return List.of("No World Director shadow session exists.");
        return List.of("World Director shadow | mode=" + session.mode() + " phase="
                + session.phase() + " samples=" + session.observationCount()
                + " selected=" + blank(session.selectedProposalId()) + " actual="
                + (session.observedActivityKind() == null ? "IDLE"
                : session.observedActivityKind()) + " | liveOwnership="
                + session.mayOwnActivity() + '.');
    }

    private static List<String> shadowReport(int agentId) {
        List<AgentWorldDirectorJournalEntry> entries = SHADOW.recent(agentId, 10);
        if (entries.isEmpty()) return List.of("No World Director shadow samples were recorded.");
        ArrayList<String> lines = new ArrayList<>();
        lines.add("Recent command-driven shadow samples: " + entries.size());
        for (AgentWorldDirectorJournalEntry entry : entries) {
            lines.add(entry.capturedAtMs() + " | actual="
                    + (entry.actualActivityKind() == null ? "IDLE" : entry.actualActivityKind())
                    + " | selected=" + blank(entry.selectedProposalId()) + " | "
                    + entry.decisionEvidence());
        }
        return List.copyOf(lines);
    }

    private static List<String> reportLines(AgentWorldShadowReport report, boolean explain) {
        ArrayList<String> lines = new ArrayList<>();
        var context = report.context();
        lines.add(context.agentName() + " | Lv" + context.level() + " job=" + context.jobId()
                + " map=" + context.mapId() + " | actual="
                + (context.currentActivityKind() == null ? "IDLE" : context.currentActivityKind())
                + '/' + blank(context.currentControllerId()) + " | plan="
                + blank(context.currentPlanId()) + '.');
        lines.add("Quests active/completed=" + context.activeQuestIds().size() + '/'
                + context.completedQuestIds().size() + " | career="
                + blank(context.careerStage()) + " | Squishy Shoes="
                + context.ownsSquishyShoes() + '.');
        lines.add("Shadow choice=" + (report.decision().kind() == null ? "IDLE"
                : report.decision().kind()) + ':' + blank(report.decision().proposalId())
                + " | " + report.decision().evidence());
        if (!explain) return List.copyOf(lines);
        report.milestones().statuses().entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().name()))
                .forEach(entry -> lines.add("  milestone " + entry.getKey() + '='
                        + entry.getValue() + " | "
                        + report.milestones().evidence().getOrDefault(entry.getKey(), "")));
        report.intents().stream().map(AgentWorldActivityIntent::proposal)
                .sorted(Comparator.comparingInt(
                        server.agents.runtime.activity.world.AgentWorldActivityProposal::priority)
                        .reversed().thenComparing(
                                server.agents.runtime.activity.world.AgentWorldActivityProposal::proposalId))
                .forEach(proposal -> lines.add("  proposal " + proposal.proposalId()
                        + " | " + proposal.kind() + " priority=" + proposal.priority()
                        + " utility=" + proposal.utility() + " eligible=" + proposal.eligible()
                        + " | " + proposal.evidence()));
        return List.copyOf(lines);
    }

    private static List<String> adapters() {
        return AgentWorldActivityAdapterCatalog.current().all().stream()
                .map(coverage -> coverage.kind() + " | complete=" + coverage.complete()
                        + " | adapter=" + blank(coverage.adapterClassName()) + " | "
                        + coverage.evidence()).toList();
    }

    private static List<String> routes() {
        AgentWorldActivityAdapterCatalog adapters = AgentWorldActivityAdapterCatalog.current();
        java.util.Set<String> planIds = AgentPlanRepository.defaultRepository().all().stream()
                .map(server.agents.plans.AgentPlanDefinition::planId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        ArrayList<String> lines = new ArrayList<>();
        for (AgentWorldControlledRoute route : List.of(
                AgentWorldControlledRoute.level15(), AgentWorldControlledRoute.level30())) {
            AgentWorldControlledRouteValidator.Result validation =
                    AgentWorldControlledRouteValidator.validate(route, adapters, planIds);
            lines.add(route.routeId() + " | valid=" + validation.valid()
                    + " | stages=" + route.stages().size());
            validation.issues().forEach(issue -> lines.add("  gap: " + issue));
        }
        return List.copyOf(lines);
    }

    private static Selection select(Character operator, String name) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByName(operator.getId(), name);
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (entry == null || agent == null) {
            throw new IllegalArgumentException("that Agent is not active in your cohort");
        }
        return new Selection(entry, agent);
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }

    private static List<String> help() {
        return List.of(
                "!agentworld inspect <agent-name> | explain <agent-name>",
                "!agentworld shadow start|sample|status|report|stop <agent-name>",
                "!agentworld adapters | routes",
                "Preparation is observation-only: it cannot admit, stop, transfer, or tick an activity.");
    }

    private record Selection(AgentRuntimeEntry entry, Character agent) {
    }
}
