package server.agents.capabilities.dialogue.jev;

import server.agents.capabilities.dialogue.AgentBuildDialogueClassifier;
import server.agents.capabilities.dialogue.AgentChatCommandClassifier;
import server.agents.capabilities.dialogue.AgentSocialDialogueClassifier;
import server.agents.capabilities.movement.AgentPartyGatherService;

/**
 * Messages {@code AgentChatOrchestrator} acts on but reports as unhandled. The movement flow,
 * the report-flow stat queries and job-selection candidates keep their legacy fall-through
 * (see {@code AgentChatOrchestratorTest#movementAndReportQueriesPreserveLegacyFallThrough}),
 * so a {@code false} from the dispatcher does not mean the regexes missed. The intent judge must
 * skip these: judging them costs tokens and, in LIVE mode, would re-dispatch a command the bot
 * has already carried out.
 */
final class AgentChatFallThroughCommands {
    private AgentChatFallThroughCommands() {
    }

    static boolean handledDeterministically(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        return AgentPartyGatherService.matchesAssemble(message)
                || AgentChatCommandClassifier.isFarmHereCommand(message)
                || AgentChatCommandClassifier.isPatrolCommand(message)
                || AgentChatCommandClassifier.isMoveHereCommand(message)
                || AgentChatCommandClassifier.isFollowCommand(message)
                || AgentChatCommandClassifier.isGrindCommand(message)
                || AgentChatCommandClassifier.isStopCommand(message)
                || AgentChatCommandClassifier.isFidgetCommand(message)
                || AgentSocialDialogueClassifier.isGreeting(message)
                || AgentChatCommandClassifier.isStatsQuery(message)
                || AgentChatCommandClassifier.isMovementStatsQuery(message)
                || AgentChatCommandClassifier.isRangeQuery(message)
                || AgentBuildDialogueClassifier.isJobSelectionCandidate(message);
    }
}
