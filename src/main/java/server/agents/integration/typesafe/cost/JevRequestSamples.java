package server.agents.integration.typesafe.cost;

import server.agents.capabilities.dialogue.jev.AgentChatIntentCatalog;
import server.agents.capabilities.partyquest.dialogue.AgentPartyQuestDialogueJudge;
import server.agents.capabilities.trade.jev.AgentOfferReplyJudge;
import server.agents.integration.typesafe.JevRequest;
import server.agents.integration.typesafe.TypeSafeDirectorProposalProvider;
import server.agents.integration.typesafe.state.JevState;
import server.security.typesafe.JevNameScreen;
import server.security.typesafe.JevReportTriage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One representative request per judgment kind, built with the production request builders so
 * measured or estimated token counts match what the server will actually send. The Director
 * samples mirror the provider's shape with six actions and five map candidates.
 */
public final class JevRequestSamples {
    private JevRequestSamples() {
    }

    /** Kind -> representative request, in the order projections are printed. */
    public static Map<String, JevRequest> all() {
        Map<String, JevRequest> samples = new LinkedHashMap<>();
        samples.put(AgentChatIntentCatalog.REQUEST_KIND, chatIntent());
        samples.put(AgentPartyQuestDialogueJudge.REQUEST_KIND, partyQuest());
        samples.put(AgentOfferReplyJudge.REQUEST_KIND, offerReply());
        samples.put(TypeSafeDirectorProposalProvider.SELECT_KIND, directorSelect());
        samples.put(TypeSafeDirectorProposalProvider.RANK_KIND, directorRank());
        samples.put(JevNameScreen.REQUEST_KIND, JevNameScreen.request("Adm1nJohn"));
        samples.put(JevReportTriage.REQUEST_KIND, JevReportTriage.request("ganre", "Bob",
                "he keeps spamming meso selling links in henesys",
                "Bob: cheap mesos 1m = 2usd pm me\nBob: fast delivery\nganre: stop\nBob: cheap mesos 1m = 2usd"));
        return samples;
    }

    public static JevRequest chatIntent() {
        JevState state = JevState.create()
                .put("message", "leroy can u farm around here pls", 100)
                .put("channel", "map chat", 70)
                .put("speaker", Map.of("name", "ganre", "is_owner", true, "level", 34), 90)
                .put("bot", Map.of("name", "Leroy", "level", 31, "job", "Fighter", "hp", "high",
                        "mp", "about half", "activity", "following its leader", "map", "Henesys Hunting Ground I"), 80)
                .put("last_owner_command", Map.of("text", "follow me", "when", "about 2 minutes ago"), 60);
        return AgentChatIntentCatalog.request(state.build(3_000).state());
    }

    public static JevRequest partyQuest() {
        return AgentPartyQuestDialogueJudge.request(
                AgentPartyQuestDialogueJudge.state("ganre", "wait how many do i need again", "Kerning City Party Quest",
                        "Stage 1", "each member asks Cloto for a question and reports how many coupons it needs", "party member"),
                List.of(new AgentPartyQuestDialogueJudge.Question("asks_coupon_count",
                        "Is the speaker asking how many coupons or tickets they need, or asking for the answer to their stage question?")));
    }

    public static JevRequest offerReply() {
        return AgentOfferReplyJudge.request(AgentOfferReplyJudge.state("nah keep it", "ganre", "Leroy", "Red Whip", false));
    }

    public static JevRequest directorSelect() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("operator_request", "get the bots some pots please");
        List<Map<String, Object>> rows = List.of(
                row("resupply", "Send Agents to resupply potions", "town_life"),
                row("train", "Move Agents to a training map", "hunting"),
                row("quest", "Resume the current quest plan", "questing"),
                row("market", "Visit the Free Market", "commerce"),
                row("kpq", "Run Kerning City Party Quest", "party_quest"),
                row("rest", "Return to town and idle", "town_life"));
        state.put("actions", rows);
        Map<String, Object> criteria = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            criteria.put(String.valueOf(row.get("id")), row.get("label"));
        }
        criteria.put("none_of_these", "the request does not ask for any of the listed actions");
        Map<String, Object> instructions = new LinkedHashMap<>();
        instructions.put("question", "Which listed action does `operator_request` ask the Director to perform?");
        instructions.put("inspect", "`operator_request` against each entry of `actions`");
        return JevRequest.builder(state).kind(TypeSafeDirectorProposalProvider.SELECT_KIND)
                .choice(TypeSafeDirectorProposalProvider.ACTION_QUESTION, instructions, criteria)
                .noul(TypeSafeDirectorProposalProvider.ACTIONABLE_QUESTION,
                        "Does `operator_request` ask the Director to do something now, rather than ask a question or make small talk?")
                .build();
    }

    public static JevRequest directorRank() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("operator_request", "somewhere with dense weak mobs for a level 25 fighter");
        state.put("requested_level", 25);
        state.put("agent_level_vs_requested", "about the same");
        List<Map<String, Object>> candidates = List.of(
                candidate("m0", "Henesys Hunting Ground I", "15 to 25", "flat"),
                candidate("m1", "Ant Tunnel I", "20 to 30", "multi-level"),
                candidate("m2", "Kerning Subway B1", "22 to 32", "flat"),
                candidate("m3", "Pig Beach", "10 to 20", "flat"),
                candidate("m4", "Land of Wild Boar", "25 to 35", "sloped"));
        state.put("candidates", candidates);
        JevRequest.Builder builder = JevRequest.builder(state).kind(TypeSafeDirectorProposalProvider.RANK_KIND);
        for (int index = 0; index < candidates.size(); index++) {
            Map<String, Object> instructions = new LinkedHashMap<>();
            instructions.put("question", "How well does candidate `candidates[" + index + "]` fit `operator_request` for a level `requested_level` Agent?");
            instructions.put("inspect", "`candidates[" + index + "]`, `operator_request`, `requested_level`");
            builder.score("fit_" + index, instructions, TypeSafeDirectorProposalProvider.FIT_LEVELS);
        }
        return builder.build();
    }

    private static Map<String, Object> row(String id, String label, String activity) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("label", label);
        row.put("activity", activity);
        row.put("destructive", false);
        return row;
    }

    private static Map<String, Object> candidate(String id, String map, String window, String terrain) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("map", map);
        row.put("level_window", window);
        row.put("requested_level_is", "about the same the window's lower bound");
        row.put("terrain", terrain);
        row.put("agents", "recommended 3, at most 6");
        row.put("tags", List.of("beginner"));
        row.put("hazards", List.of());
        row.put("catalog_note", "dense spawns, short respawn");
        return row;
    }
}
