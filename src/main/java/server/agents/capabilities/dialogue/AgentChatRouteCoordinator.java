package server.agents.capabilities.dialogue;

import server.agents.capabilities.dialogue.llm.AgentLlmReplyCoordinator;
import server.agents.commands.AgentLifecycleCommandCoordinator;
import server.agents.commands.AgentFormationCommandCoordinator;
import server.agents.commands.AgentDismissCommandService;
import server.agents.commands.AgentRecruitCommandService;
import server.agents.capabilities.movement.AgentFormationService;
import server.agents.capabilities.movement.AgentFormationRuntime;
import server.agents.capabilities.trade.AgentTransferCommandService;

import server.agents.capabilities.movement.AgentMovementPhysicsConfig;

import client.Character;
import server.agents.commands.AgentTargetedCommandMatch;
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
import server.agents.commands.AgentFollowTargetCommandCoordinator;
import server.agents.runtime.AgentRuntimeConfig;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentMailboxRuntime;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

public final class AgentChatRouteCoordinator {
    private AgentChatRouteCoordinator() {
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
                (commandLeader, text) -> AgentLifecycleCommandCoordinator.handleRecruitCommand(
                        commandLeader,
                        text,
                        recruitAction),
                (commandLeader, text) -> AgentLifecycleCommandCoordinator.handleTransferCommand(
                        commandLeader,
                        text,
                        transferAction),
                (commandLeader, text) -> dispatchFormationCommand(
                        commandLeader,
                        text,
                        entriesByLeader,
                        defaultFormation,
                        defaultFollowStaggerPx,
                        defaultSnapRangePx),
                entriesByLeader::get,
                (commandLeader, text) -> AgentLifecycleCommandCoordinator.handleDismissCommand(
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
                AgentChatRouteCoordinator::dispatchFollowTargetCommand,
                AgentChatRouteCoordinator::setReplyChannel,
                () -> AgentLlmConfig.typoSuggesterEnabled,
                AgentCommandTypoSuggester::suggest,
                AgentChatRouteCoordinator::queueReply,
                AgentChatRouteCoordinator::handleAgentChat,
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
                AgentChatRouteCoordinator::dispatchFollowTargetCommand,
                AgentChatCommandClassifier::isGroupSupplyRequest,
                (leader, entries) -> AgentGroupSupplyResponderSelector.select(
                        leader,
                        entries,
                        AgentRuntimeIdentityRuntime::botMapId),
                AgentChatRouteCoordinator::setReplyChannel,
                AgentChatRouteCoordinator::handleAgentChat,
                () -> AgentLlmConfig.typoSuggesterEnabled,
                AgentCommandTypoSuggester::suggest,
                AgentChatRouteCoordinator::queueReply);
    }

    private static void queueReply(
            AgentRuntimeEntry entry,
            String reply) {
        AgentMailboxRuntime.dispatch(entry, ignored -> {
            AgentReplyRuntime.queueReply(entry, reply);
            return null;
        });
    }

    private static void setReplyChannel(AgentRuntimeEntry entry, AgentReplyChannel channel) {
        AgentMailboxRuntime.dispatch(entry, ignored -> {
            AgentReplyChannelStateRuntime.setReplyChannel(entry, channel);
            return null;
        });
    }

    private static void dispatchFollowTargetCommand(
            Character leader,
            List<? extends AgentRuntimeEntry> entries,
            String targetToken) {
        Character target = AgentFollowTargetCommandCoordinator.resolveFollowTarget(leader, targetToken);
        if (target == null) {
            return;
        }
        for (AgentRuntimeEntry entry : entries) {
            AgentMailboxRuntime.dispatch(entry, ignored -> {
                AgentFollowTargetCommandCoordinator.applyResolvedFollowTargetCommand(entry, target);
                return null;
            });
        }
    }

    private static <E extends AgentRuntimeEntry> boolean dispatchFormationCommand(
            Character leader,
            String message,
            Map<Integer, List<E>> entriesByLeader,
            AgentFormationService.FormationState defaultFormation,
            int defaultFollowStaggerPx,
            int defaultSnapRangePx) {
        if (!server.agents.capabilities.movement.AgentFormationCommandService.matchesCommand(message)) {
            return false;
        }
        List<E> entries = entriesByLeader.get(leader.getId());
        if (entries == null || entries.isEmpty()) {
            return AgentFormationCommandCoordinator.handleFormationCommand(
                    leader,
                    message,
                    entriesByLeader::get,
                    defaultFormation,
                    defaultFollowStaggerPx,
                    defaultSnapRangePx);
        }
        AgentMailboxRuntime.dispatch(entries.get(0), ignored ->
                AgentFormationCommandCoordinator.handleFormationCommand(
                        leader,
                        message,
                        entriesByLeader::get,
                        defaultFormation,
                        defaultFollowStaggerPx,
                        defaultSnapRangePx));
        return true;
    }

    private static CompletionStage<Boolean> handleAgentChat(
            AgentRuntimeEntry entry,
            String message,
            AgentReplyChannel channel) {
        return AgentChatMailboxDispatcher.handleChat(entry, message, channel);
    }
}
