package server.agents.runtime;

import server.agents.capabilities.follow.AgentFollowTargetCandidateService;
import server.agents.capabilities.follow.AgentFollowTargetResolutionService;

import client.Character;
import server.agents.capabilities.dialogue.AgentDialogueSelector;
import server.agents.capabilities.supplies.AgentPotionService;
import server.agents.integration.AgentReplyRuntime;
import server.agents.runtime.AgentSchedulerRuntime;
import server.agents.capabilities.movement.AgentMovementCommandRuntime;
import server.agents.capabilities.trade.AgentOfferStateRuntime;
import server.agents.integration.AgentInventoryGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.capabilities.equipment.AgentEquipmentService;

import java.util.List;

/**
 * Temporary Agent-owned runtime bridge for follow-target command wiring.
 */
public final class AgentFollowTargetRuntime {
    private AgentFollowTargetRuntime() {
    }

    public static Character resolveFollowTarget(Character leader, String targetToken) {
        return AgentFollowTargetResolutionService.resolveFollowTarget(
                leader,
                targetToken,
                new AgentFollowTargetResolutionService.Hooks(
                        AgentFollowTargetRuntime::followTargetCandidates,
                        Character::yellowMessage));
    }

    public static List<Character> followTargetCandidates(Character leader) {
        return AgentFollowTargetCandidateService.candidates(
                leader,
                new AgentFollowTargetCandidateService.Hooks(AgentRuntimeRegistry::entriesForLeader));
    }

    public static boolean applyFollowTargetCommand(Character leader,
                                                   List<? extends AgentRuntimeEntry> entries,
                                                   String targetToken) {
        return AgentFollowTargetCommandService.applyFollowTargetCommand(
                leader,
                entries,
                targetToken,
                new AgentFollowTargetCommandService.Hooks(
                        AgentFollowTargetRuntime::resolveFollowTarget,
                        AgentFollowTargetRuntime::followTargetReply,
                        AgentReplyRuntime::queueReply,
                        () -> AgentRandom.randMs(250, 750),
                        AgentSchedulerRuntime::afterDelay,
                        AgentFollowTargetRuntime::autoEquipForFollow,
                        AgentFollowTargetRuntime::checkPotShareForFollow,
                        AgentMovementCommandRuntime::follow));
    }

    private static String followTargetReply(Character target) {
        return AgentDialogueSelector.randomReply(List.of(
                "ok",
                "k",
                "sure",
                "omw",
                "got it",
                "following " + target.getName(),
                "ok, following " + target.getName()));
    }

    private static void autoEquipForFollow(AgentRuntimeEntry entry) {
        AgentEquipmentService.autoEquip(
                AgentRuntimeIdentityRuntime.bot(entry),
                AgentRuntimeIdentityRuntime.owner(entry),
                AgentOfferStateRuntime.pendingLootOfferItem(entry));
    }

    private static void checkPotShareForFollow(AgentRuntimeEntry entry) {
        AgentPotionService.checkPotShareOnModeStart(
                entry,
                AgentRuntimeIdentityRuntime.bot(entry),
                AgentInventoryGatewayRuntime.inventory());
    }
}
