package server.partner;

import client.Character;
import config.AdventurerPartnerConfig;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

public final class PartnerTriggerPolicy {
    public Result validate(AdventurerPartnerConfig config, ActivePartnerSession active) {
        if (config == null || !config.enabled) {
            return Result.rejected("The Adventurer Partner Program is disabled.");
        }
        if (active == null || active.runtime().status() != PartnerLifecycleStatus.ACTIVE) {
            return Result.rejected("No active Partner session is ready to switch.");
        }
        Character human = active.humanActor();
        Character partner = active.partnerActorOrDormantProfile();
        String humanReason = validateActor(human, true);
        if (humanReason != null) {
            return Result.rejected(humanReason);
        }
        if (active.runtime().mode() == PartnerMode.DOUBLE_PARTNER) {
            String partnerReason = validateActor(partner, false);
            if (partnerReason != null) {
                return Result.rejected("Your Partner cannot switch right now: " + partnerReason);
            }
            if (config.sameMapRequired && human.getMap() != partner.getMap()) {
                return Result.rejected("You and your Partner must be in the same map.");
            }
            AgentRuntimeEntry entry = active.agentEntry();
            if (entry == null || !AgentRuntimeRegistry.isActiveSession(entry, entry.sessionGeneration())) {
                return Result.rejected("Your Partner's Agent runtime is unavailable.");
            }
            if (entry.transitionBarrierState().isPaused()) {
                return Result.rejected("Your Partner is already entering another transition.");
            }
        }
        if (human.isProfileTransitioning() || partner.isProfileTransitioning()) {
            return Result.rejected("A Partner switch is already in progress.");
        }
        if (human.getProfileOwnerCharacterId() == partner.getProfileOwnerCharacterId()) {
            return Result.rejected("Both actors cannot hold the same Partner profile.");
        }
        String humanEffectReason = human.profileTransitionBlockReason();
        if (humanEffectReason != null) {
            return Result.rejected(humanEffectReason);
        }
        String partnerEffectReason = partner.profileTransitionBlockReason();
        if (partnerEffectReason != null) {
            return Result.rejected(partnerEffectReason);
        }
        return Result.accepted();
    }

    private String validateActor(Character actor, boolean requireHumanClient) {
        if (actor == null || !actor.isLoggedin()) {
            return "A character is disconnecting or being removed.";
        }
        if (!actor.isAlive()) {
            return "A character is defeated.";
        }
        if (actor.isChangingMaps()) {
            return "Wait until the map transition finishes.";
        }
        if (actor.isAwayFromWorld()) {
            return "Switching is unavailable in Cash Shop or MTS.";
        }
        if (actor.getTrade() != null) {
            return "Close the trade window first.";
        }
        if (actor.getShop() != null || actor.getPlayerShop() != null || actor.getHiredMerchant() != null) {
            return "Close the shop or hired merchant first.";
        }
        if (actor.getMiniGame() != null) {
            return "Leave the minigame first.";
        }
        if (actor.getEventInstance() != null || actor.getPartyQuest() != null) {
            return "This event or party-quest state cannot switch profiles.";
        }
        if (requireHumanClient && actor.getClient() != null
                && (actor.getClient().getCM() != null || actor.getClient().getQM() != null)) {
            return "Finish the current NPC or quest conversation first.";
        }
        return null;
    }

    public record Result(boolean allowed, String reason) {
        private static Result accepted() {
            return new Result(true, null);
        }

        private static Result rejected(String reason) {
            return new Result(false, reason);
        }
    }
}
