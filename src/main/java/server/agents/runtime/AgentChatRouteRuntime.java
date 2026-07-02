package server.agents.runtime;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatCommandClassifier;
import server.agents.capabilities.dialogue.AgentChatIngressService;
import server.agents.capabilities.dialogue.AgentChatRuntime;
import server.agents.capabilities.dialogue.AgentTargetedChatRouteService;
import server.agents.capabilities.dialogue.AgentUntargetedChatRouteService;
import server.agents.capabilities.dialogue.llm.AgentLlmConfig;
import server.agents.capabilities.dialogue.llm.AgentLlmReplyService;
import server.agents.capabilities.supplies.AgentGroupSupplyResponderSelector;
import server.agents.capabilities.trade.AgentPendingOfferChatRouteService;
import server.agents.commands.AgentCommandTypoSuggester;
import server.agents.commands.AgentReplyChannel;
import server.agents.integration.AgentBotActivityStateRuntime;
import server.agents.integration.AgentBotChatOrchestratorContext;
import server.agents.integration.AgentBotCommandParser;
import server.agents.integration.AgentBotManagerReplyRuntime;
import server.agents.integration.AgentBotReplyChannelStateRuntime;
import server.bots.BotEntry;
import server.bots.BotMovementManager;

import java.util.List;
import java.util.Map;

public final class AgentChatRouteRuntime {
    private AgentChatRouteRuntime() {
    }

    public static void handleChat(Character leader,
                                  String message,
                                  AgentReplyChannel channel,
                                  AgentRecruitCommandService.RecruitAction recruitAction,
                                  AgentTransferCommandService.TransferAction transferAction,
                                  AgentDismissCommandService.DismissAction dismissAction) {
        handleChat(
                leader,
                message,
                channel,
                AgentRuntimeRegistry.entriesByLeaderId(),
                recruitAction,
                transferAction,
                dismissAction,
                AgentFormationRuntime.defaultFormationState(),
                AgentRuntimeConfig.cfg.FOLLOW_STAGGER,
                BotMovementManager.configuredFollowYCap());
    }

    public static void handleChat(Character leader,
                                  String message,
                                  AgentReplyChannel channel,
                                  Map<Integer, List<BotEntry>> entriesByLeader,
                                  AgentRecruitCommandService.RecruitAction recruitAction,
                                  AgentTransferCommandService.TransferAction transferAction,
                                  AgentDismissCommandService.DismissAction dismissAction,
                                  AgentFormationService.FormationState defaultFormation,
                                  int defaultFollowStaggerPx,
                                  int defaultSnapRangePx) {
        AgentChatIngressService.handleChat(
                leader,
                message,
                channel,
                chatIngressHooks(
                        entriesByLeader,
                        recruitAction,
                        transferAction,
                        dismissAction,
                        defaultFormation,
                        defaultFollowStaggerPx,
                        defaultSnapRangePx));
    }

    private static AgentChatIngressService.Hooks chatIngressHooks(
            Map<Integer, List<BotEntry>> entriesByLeader,
            AgentRecruitCommandService.RecruitAction recruitAction,
            AgentTransferCommandService.TransferAction transferAction,
            AgentDismissCommandService.DismissAction dismissAction,
            AgentFormationService.FormationState defaultFormation,
            int defaultFollowStaggerPx,
            int defaultSnapRangePx) {
        return new AgentChatIngressService.Hooks(
                (speaker, text) -> AgentPendingOfferChatRouteService.handlePendingOfferResponse(
                        entriesByLeader.values(),
                        speaker,
                        text),
                (commandLeader, text) -> AgentLifecycleChatCommandRuntime.handleRecruitCommand(
                        commandLeader,
                        text,
                        recruitAction),
                (commandLeader, text) -> AgentLifecycleChatCommandRuntime.handleTransferCommand(
                        commandLeader,
                        text,
                        transferAction),
                (commandLeader, text) -> AgentFormationCommandRuntime.handleFormationCommand(
                        commandLeader,
                        text,
                        entriesByLeader::get,
                        defaultFormation,
                        defaultFollowStaggerPx,
                        defaultSnapRangePx),
                entriesByLeader::get,
                (commandLeader, text) -> AgentLifecycleChatCommandRuntime.handleDismissCommand(
                        commandLeader,
                        text,
                        dismissAction),
                (commandLeader, entries, text, replyChannel) -> AgentTargetedChatRouteService.handleTargetedChat(
                        commandLeader,
                        entries,
                        text,
                        replyChannel,
                        targetedChatHooks()),
                (commandLeader, entries, text, replyChannel) -> AgentUntargetedChatRouteService.handleUntargetedChat(
                        commandLeader,
                        entries,
                        text,
                        replyChannel,
                        untargetedChatHooks()));
    }

    private static AgentTargetedChatRouteService.Hooks targetedChatHooks() {
        return new AgentTargetedChatRouteService.Hooks(
                AgentBotCommandParser::resolveTargetedBot,
                AgentChatCommandClassifier::matchFollowTarget,
                AgentFollowTargetRuntime::applyFollowTargetCommand,
                AgentBotReplyChannelStateRuntime::setReplyChannel,
                () -> AgentLlmConfig.typoSuggesterEnabled,
                AgentCommandTypoSuggester::suggest,
                AgentBotManagerReplyRuntime::queueReply,
                AgentChatRouteRuntime::handleAgentChat,
                AgentChatRuntime::wasLastChatHandled,
                System::currentTimeMillis,
                AgentBotActivityStateRuntime::recordLastOwnerCommand,
                () -> AgentLlmConfig.enabled,
                AgentLlmReplyService::maybeRespond,
                Character::yellowMessage);
    }

    private static AgentUntargetedChatRouteService.Hooks untargetedChatHooks() {
        return new AgentUntargetedChatRouteService.Hooks(
                AgentChatCommandClassifier::matchFollowTarget,
                AgentFollowTargetRuntime::applyFollowTargetCommand,
                AgentChatCommandClassifier::isGroupSupplyRequest,
                AgentGroupSupplyResponderSelector::select,
                AgentBotReplyChannelStateRuntime::setReplyChannel,
                AgentChatRouteRuntime::handleAgentChat,
                () -> AgentLlmConfig.typoSuggesterEnabled,
                AgentCommandTypoSuggester::suggest,
                AgentBotManagerReplyRuntime::queueReply);
    }

    private static void handleAgentChat(BotEntry entry, String message) {
        AgentChatRuntime.handleChat(message, new AgentBotChatOrchestratorContext(entry));
    }
}
