package server.agents.presentation.director;

import server.agents.runtime.activity.control.AgentDirectorAction;
import server.agents.runtime.activity.control.AgentDirectorAgentDirectoryEntry;
import server.agents.runtime.activity.control.AgentDirectorExecutiveView;
import server.agents.runtime.activity.control.proposal.AgentDirectorProposal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stable v1 JSON projection; internal runtime records never cross the HTTP boundary directly. */
public record AgentDirectorApiView(
        int schemaVersion,
        long generatedAtMs,
        String contextRevision,
        Map<String, Object> agent,
        Map<String, Object> activity,
        Map<String, Object> energy,
        Map<String, Object> profile,
        Map<String, Object> resources,
        Map<String, Object> director,
        List<Map<String, Object>> actions,
        List<AgentDirectorProposal> proposals,
        List<Map<String, Object>> directives,
        List<Map<String, Object>> outcomes,
        List<Map<String, Object>> journey) {

    public static Map<String, Object> roster(AgentDirectorAgentDirectoryEntry entry) {
        return Map.of(
                "characterId", entry.characterId(), "name", entry.name(),
                "level", entry.level(), "jobId", entry.jobId(), "mapId", entry.mapId(),
                "online", entry.online(), "runtimeActive", entry.runtimeActive());
    }

    public static AgentDirectorApiView from(
            AgentDirectorExecutiveView view,
            List<AgentDirectorProposal> proposals,
            long generatedAtMs) {
        var context = view.context();
        var resources = view.resources();
        var session = view.directorSession();
        return new AgentDirectorApiView(
                1, generatedAtMs, view.contextRevision(),
                map(
                        "characterId", context.agentId(), "name", context.agentName(),
                        "level", context.level(), "jobId", context.jobId(),
                        "mapId", context.mapId(), "hp", context.hp(), "maxHp", context.maxHp(),
                        "mp", context.mp(), "maxMp", context.maxMp(), "meso", context.meso(),
                        "alive", context.alive(), "careerStage", context.careerStage()),
                map(
                        "kind", text(context.currentActivityKind()),
                        "now", view.activity().now(), "next", view.activity().next(),
                        "waitingOn", view.activity().waitingOn(),
                        "blockedBy", view.activity().blockedBy(),
                        "retained", view.activity().retained(),
                        "lastEvent", view.activity().lastEvent()),
                map(
                        "percent", view.energy().energyPercent(), "band", view.energy().band(),
                        "restDebtPercent", view.energy().restDebtPercent(),
                        "confidencePercent", view.energy().confidencePercent(),
                        "frustrationPercent", view.energy().frustrationPercent()),
                map(
                        "profileId", view.profile().profileId(),
                        "profileVersion", view.profile().profileVersion(),
                        "traits", view.profile().traits()),
                map(
                        "exp", resources.exp(), "remainingAp", resources.remainingAp(),
                        "remainingSp", resources.remainingSp(), "hpPotions", resources.hpPotions(),
                        "mpPotions", resources.mpPotions(), "weaponType", resources.weaponType(),
                        "ammunition", resources.ammunition(),
                        "ammunitionRequired", resources.ammunitionRequired(),
                        "ammunitionUnlimited", resources.ammunitionUnlimited(),
                        "freeInventorySlots", resources.freeInventorySlots()),
                map(
                        "mode", session.mode().name(), "phase", session.phase().name(),
                        "goalId", session.goalId(), "lastReason", session.lastReason(),
                        "updatedAtMs", session.updatedAtMs()),
                view.actions().stream().map(AgentDirectorApiView::action).toList(),
                List.copyOf(proposals == null ? List.of() : proposals),
                view.directives().stream().map(envelope -> map(
                        "directiveId", envelope.directive().directiveId(),
                        "actionId", envelope.directive().parameters()
                                .getOrDefault("directorActionId", ""),
                        "type", envelope.directive().type().name(),
                        "status", envelope.status().name(),
                        "reason", envelope.directive().reason(),
                        "resolution", envelope.resolution(),
                        "createdAtMs", envelope.directive().createdAtMs(),
                        "resolvedAtMs", envelope.resolvedAtMs())).toList(),
                view.pendingActivityOutcomes().stream().map(envelope -> map(
                        "outcomeId", envelope.outcomeId(), "acknowledged", envelope.acknowledged(),
                        "publishedAtMs", envelope.publishedAtMs(),
                        "status", envelope.outcome().phase().name(),
                        "reason", envelope.outcome().reason())).toList(),
                view.recentJourney().stream().map(event -> map(
                        "sequence", event.sequence(), "eventId", event.eventId(),
                        "occurredAtMs", event.occurredAtMs(), "type", event.type().name(),
                        "activityKind", text(event.activityKind()), "source", event.source(),
                        "reason", event.reason(), "evidence", event.evidence())).toList());
    }

    private static Map<String, Object> action(AgentDirectorAction action) {
        return map(
                "actionId", action.actionId(), "label", action.label(),
                "availability", action.availability().name(), "reason", action.reason(),
                "directiveType", action.directiveType().name(),
                "activityKind", text(action.targetActivityKind()),
                "parameters", action.parameters(), "priority", action.priority(),
                "destructive", action.destructive());
    }

    private static String text(Object value) { return value == null ? "" : value.toString(); }

    private static Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put((String) values[index], values[index + 1]);
        }
        return Map.copyOf(result);
    }
}
