package server.agents.progression;

import client.Character;
import config.YamlConfig;
import server.agents.capabilities.dialogue.AgentChatTextSanitizer;
import server.agents.integration.AgentPacketGatewayRuntime;

/** Optional one-shot intention narration for the Victoria level-15 MVP journey. */
public final class VictoriaFirstJobNarrator {
    private VictoriaFirstJobNarrator() {
    }

    public static void announce(Character agent,
                                AgentCareerProgressionState state,
                                AgentCareerBuildBundle bundle) {
        if (agent == null || state == null || bundle == null
                || !config.AgentYamlConfig.config.agent.AGENT_VICTORIA_INTENTION_CHAT_ENABLED) {
            return;
        }
        String message = message(state.stage(), bundle);
        if (message == null || !state.markStageAnnounced(state.stage())) {
            return;
        }
        AgentPacketGatewayRuntime.packets().broadcastChatText(
                agent, AgentChatTextSanitizer.sanitize(message), false, 0);
    }

    static String message(AgentCareerProgressionState.Stage stage,
                          AgentCareerBuildBundle bundle) {
        return switch (stage) {
            case COMPLETE_BIGGS_AT_OLAF -> "I'm going to walk across Lith Harbor and complete Biggs' quest with Olaf.";
            case COMPLETE_OLAF_LESSON -> "I'm going to finish Olaf's lesson and reach level 10.";
            case START_CAREER_PATH -> "I'm going to tell Olaf I want to become a " + careerName(bundle) + ".";
            case TRAVEL_TO_PRE_JOB_GRIND, GRIND_TO_JOB_LEVEL ->
                    "I need a little more training before my first job advancement.";
            case RETURN_TO_LITH_FOR_TAXI -> "I'm heading back to Lith Harbor to find the taxi.";
            case TAKE_TAXI -> "I'm going to ask Phil for a ride to " + destinationName(bundle) + ".";
            case ENTER_INSTRUCTOR_ROOM, COMPLETE_CAREER_PATH, ADVANCE_FIRST_JOB ->
                    "I'm going to meet my job instructor and become a " + careerName(bundle) + ".";
            case TRAVEL_TO_INITIAL_SHOP, INITIAL_SHOPPING -> "I'm buying a few supplies before training.";
            case RETURN_TO_INSTRUCTOR, INSTRUCTOR_TRAINING ->
                    "I'm going back to my instructor to start the training quests.";
            case HOME_QUEST_PACK, POST_HOME_DECISION, ROTATION_QUEST_PACK,
                    GRIND_TO_MILESTONE, FINAL_RETURN_TO_INSTRUCTOR ->
                    "I'm continuing my Victoria Island training toward level 15.";
            case COMPLETE -> "I finished my level 15 Victoria Island training run.";
            case WAITING_FOR_MAPLE_ISLAND, TRAVEL_TO_LITH, BLOCKED -> null;
        };
    }

    private static String careerName(AgentCareerBuildBundle bundle) {
        return switch (bundle.firstJobId()) {
            case 100 -> "warrior";
            case 200 -> "magician";
            case 300 -> "bowman";
            case 400 -> "thief";
            case 500 -> "pirate";
            default -> "first-job adventurer";
        };
    }

    private static String destinationName(AgentCareerBuildBundle bundle) {
        return switch (bundle.firstJobId()) {
            case 100 -> "Perion";
            case 200 -> "Ellinia";
            case 300 -> "Henesys";
            case 400 -> "Kerning City";
            case 500 -> "Nautilus Harbor";
            default -> "my instructor's town";
        };
    }
}
