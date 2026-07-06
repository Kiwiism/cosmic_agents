package server.agents.runtime;

import client.Character;
import server.agents.capabilities.dialogue.AgentDialogueSelector;
import server.agents.capabilities.supplies.AgentPotionService;
import server.agents.integration.AgentBotReplyRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;
import server.agents.integration.AgentBotMovementCommandRuntime;
import server.agents.integration.AgentBotOfferStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.bots.BotEntry;
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

    public static boolean applyFollowTargetCommand(Character leader, List<BotEntry> entries, String targetToken) {
        return AgentFollowTargetCommandService.applyFollowTargetCommand(
                leader,
                entries,
                targetToken,
                new AgentFollowTargetCommandService.Hooks(
                        AgentFollowTargetRuntime::resolveFollowTarget,
                        AgentFollowTargetRuntime::followTargetReply,
                        (entry, reply) -> AgentBotReplyRuntime.queueReply(asBotEntry(entry), reply),
                        () -> AgentRandom.randMs(250, 750),
                        AgentBotSchedulerRuntime::afterDelay,
                        AgentFollowTargetRuntime::autoEquipForFollow,
                        AgentFollowTargetRuntime::checkPotShareForFollow,
                        (entry, target) -> AgentBotMovementCommandRuntime.follow(asBotEntry(entry), target)));
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
                AgentBotRuntimeIdentityRuntime.bot(entry),
                AgentBotRuntimeIdentityRuntime.owner(entry),
                AgentBotOfferStateRuntime.pendingLootOfferItem(entry));
    }

    private static void checkPotShareForFollow(AgentRuntimeEntry entry) {
        AgentPotionService.checkPotShareOnModeStart(
                asBotEntry(entry),
                AgentBotRuntimeIdentityRuntime.bot(entry));
    }

    private static BotEntry asBotEntry(AgentRuntimeEntry entry) {
        return (BotEntry) entry;
    }
}
