package server.agents.runtime;

import server.agents.capabilities.dialogue.llm.AgentLlmReplyCoordinator;
import server.agents.commands.AgentDismissCommandService;
import server.agents.commands.AgentRecruitCommandService;
import server.agents.capabilities.movement.AgentFormationService;
import server.agents.capabilities.movement.AgentFormationRuntime;
import server.agents.capabilities.trade.AgentTransferCommandService;

import server.agents.capabilities.movement.AgentMovementPhysicsConfig;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatCommandClassifier;
import server.agents.capabilities.dialogue.AgentChatIngressService;
import server.agents.capabilities.dialogue.AgentChatRuntime;
import server.agents.commands.AgentTargetedCommandMatch;
import server.agents.capabilities.dialogue.AgentTargetedChatRouteService;
import server.agents.capabilities.dialogue.AgentUntargetedChatRouteService;
import server.agents.capabilities.dialogue.llm.AgentLlmConfig;
import server.agents.capabilities.supplies.AgentGroupSupplyResponderSelector;
import server.agents.capabilities.trade.AgentPendingOfferChatRouteService;
import server.agents.commands.AgentCommandTypoSuggester;
import server.agents.commands.AgentReplyChannel;
import server.agents.capabilities.follow.AgentActivityStateRuntime;
import server.agents.commands.AgentCommandTargetResolver;
import server.agents.integration.AgentReplyRuntime;
import server.agents.commands.AgentReplyChannelStateRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;

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
                AgentMovementPhysicsConfig.configuredFollowYCap());
    }

    public static <E extends AgentRuntimeEntry> void handleChat(Character leader,
                                  String message,
                                  AgentReplyChannel channel,
                                  Map<Integer, List<E>> entriesByLeader,
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

    private static <E extends AgentRuntimeEntry> AgentChatIngressService.Hooks<E> chatIngressHooks(
            Map<Integer, List<E>> entriesByLeader,
            AgentRecruitCommandService.RecruitAction recruitAction,
            AgentTransferCommandService.TransferAction transferAction,
            AgentDismissCommandService.DismissAction dismissAction,
            AgentFormationService.FormationState defaultFormation,
            int defaultFollowStaggerPx,
            int defaultSnapRangePx) {
        return new AgentChatIngressService.Hooks<>(
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

    private static <E extends AgentRuntimeEntry> AgentTargetedChatRouteService.Hooks<E> targetedChatHooks() {
        return new AgentTargetedChatRouteService.Hooks<>(
                (entries, message) -> {
                    var match = AgentCommandTargetResolver.resolveTargetedAgent(entries, message);
                    return new AgentTargetedCommandMatch<>(match.entry(), match.commandText(), match.feedbackMessage());
                },
                AgentChatCommandClassifier::matchFollowTarget,
                AgentFollowTargetRuntime::applyFollowTargetCommand,
                AgentReplyChannelStateRuntime::setReplyChannel,
                () -> AgentLlmConfig.typoSuggesterEnabled,
                AgentCommandTypoSuggester::suggest,
                AgentReplyRuntime::queueReply,
                AgentChatRouteRuntime::handleAgentChat,
                AgentChatRuntime::wasLastChatHandled,
                System::currentTimeMillis,
                AgentRuntimeIdentityRuntime::owner,
                AgentActivityStateRuntime::recordLastOwnerCommand,
                () -> AgentLlmConfig.enabled,
                AgentLlmReplyCoordinator::maybeRespond,
                Character::yellowMessage);
    }

    private static <E extends AgentRuntimeEntry> AgentUntargetedChatRouteService.Hooks<E> untargetedChatHooks() {
        return new AgentUntargetedChatRouteService.Hooks<>(
                AgentChatCommandClassifier::matchFollowTarget,
                AgentFollowTargetRuntime::applyFollowTargetCommand,
                AgentChatCommandClassifier::isGroupSupplyRequest,
                (leader, entries) -> AgentGroupSupplyResponderSelector.select(
                        leader,
                        entries,
                        AgentRuntimeIdentityRuntime::botMapId),
                AgentReplyChannelStateRuntime::setReplyChannel,
                AgentChatRouteRuntime::handleAgentChat,
                () -> AgentLlmConfig.typoSuggesterEnabled,
                AgentCommandTypoSuggester::suggest,
                AgentReplyRuntime::queueReply);
    }

    private static void handleAgentChat(AgentRuntimeEntry entry, String message) {
        AgentChatRuntime.handleChat(message, new AgentChatOrchestratorContext(entry));
    }
}
