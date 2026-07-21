package server.agents.capabilities.dialogue.semantic;

import server.agents.personality.AgentPersonalityProfile;

import java.util.List;
import java.util.Map;

/** Deterministic template realization with personality-weighted wording. */
public final class AgentDeterministicDialogueRealizer implements AgentDialogueRealizer {
    @Override
    public String realize(AgentSemanticDialogueAct act, AgentPersonalityProfile profile) {
        List<String> templates = AgentDialogueTemplateRegistry.templates(act.topicId(), act.actKey());
        if (templates.isEmpty()) {
            return "";
        }
        int expressiveness = profile == null ? 50 : profile.traits().expressiveness();
        int band = expressiveness < 34 ? 0 : expressiveness > 66 ? 2 : 1;
        int start = templates.size() * band / 3;
        int end = Math.max(start + 1, templates.size() * (band + 1) / 3);
        end = Math.min(end, templates.size());
        int selected = start + Math.floorMod(mix(act.variationSeed()), end - start);
        return substitute(templates.get(selected), act.parameters());
    }

    private static String substitute(String template, Map<String, String> parameters) {
        String result = template;
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result.replaceAll("\\{[A-Za-z0-9_]+}", "").replaceAll("\\s+", " ").trim();
    }

    private static int mix(long value) {
        long mixed = value ^ (value >>> 33);
        mixed *= 0xff51afd7ed558ccdl;
        mixed ^= mixed >>> 33;
        return (int) (mixed ^ (mixed >>> 32));
    }
}
