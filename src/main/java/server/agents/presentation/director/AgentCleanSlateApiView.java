package server.agents.presentation.director;

import server.agents.administration.AgentCleanSlatePreview;
import server.agents.administration.AgentCleanSlateResult;
import server.agents.administration.AgentCleanSlateTarget;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AgentCleanSlateApiView {
    private AgentCleanSlateApiView() {
    }

    public static Map<String, Object> preview(AgentCleanSlatePreview preview) {
        return map(
                "resetId", preview.resetId(),
                "target", target(preview.target()),
                "eligible", preview.eligible(),
                "blockers", preview.blockers(),
                "resetScope", preview.resetScope(),
                "retainedScope", preview.retainedScope(),
                "confirmationToken", preview.confirmationToken(),
                "confirmationPhrase", preview.confirmationPhrase(),
                "expiresAtMs", preview.expiresAtMs());
    }

    public static Map<String, Object> result(AgentCleanSlateResult result) {
        return map(
                "resetId", result.resetId(),
                "success", result.success(),
                "message", result.message(),
                "target", target(result.target()),
                "warnings", result.warnings(),
                "executedAtMs", result.executedAtMs());
    }

    private static Map<String, Object> target(AgentCleanSlateTarget target) {
        return map(
                "characterId", target.characterId(),
                "name", target.name(),
                "world", target.world(),
                "level", target.level(),
                "jobId", target.jobId(),
                "mapId", target.mapId(),
                "experience", target.experience(),
                "mesos", target.mesos(),
                "ordinaryItemCount", target.ordinaryItemCount(),
                "preservedItemCount", target.preservedItemCount(),
                "questCount", target.questCount(),
                "skillCount", target.skillCount(),
                "activeAgent", target.activeAgent(),
                "interactiveAllowed", target.interactiveAllowed(),
                "dedicatedAccount", target.dedicatedAccount(),
                "merchantStateClear", target.merchantStateClear());
    }

    private static Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put((String) values[index], values[index + 1]);
        }
        return Map.copyOf(result);
    }
}
