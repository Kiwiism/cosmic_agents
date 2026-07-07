package server.agents.integration;

import server.agents.capabilities.combat.AgentAttackExecutionProvider;

import client.Character;
import client.inventory.WeaponType;
import server.agents.capabilities.dialogue.AgentChatSupplyRequestFlow;
import server.agents.capabilities.dialogue.AgentSupplyRequestOutcomeFlow;
import server.agents.capabilities.supplies.AgentAmmoService;
import server.agents.runtime.AgentRuntimeEntry;
import server.bots.BotEntry;
import server.agents.capabilities.trade.AgentOfferService;
import server.agents.capabilities.supplies.AgentPotionService;

/**
 * Agent-owned supply chat callback facade over temporary bot-side potion,
 * ammo, and upgrade-offer side effects.
 */
public final class AgentBotSupplyRuntime {
    private AgentBotSupplyRuntime() {
    }

    public static AgentChatSupplyRequestFlow.SupplyRequestCallbacks supplyRequestCallbacks(BotEntry entry) {
        return new AgentChatSupplyRequestFlow.SupplyRequestCallbacks() {
            @Override
            public void requestPotion(boolean hpPotion) {
                AgentBotSchedulerRuntime.afterRandomDelay(500, 700, () -> handleNeedPotionCommand(entry, hpPotion));
            }

            @Override
            public void requestAnyPotion() {
                AgentBotSchedulerRuntime.afterRandomDelay(500, 700, () -> handleNeedAnyPotionCommand(entry));
            }

            @Override
            public void requestAmmo() {
                AgentBotSchedulerRuntime.afterRandomDelay(500, 700, () -> handleNeedAmmoCommand(entry));
            }
        };
    }

    public static void handleRequestUpgradeCommand(AgentRuntimeEntry entry, Character bot) {
        BotEntry botEntry = asBotEntry(entry);
        AgentOfferService.clearPendingOfferForOwnerAsk(botEntry);
        if (AgentPotionService.requestLowSuppliesFromOwnerAsk(botEntry, bot)) {
            return;
        }
        AgentOfferService.requestBestUpgradeFromOwner(botEntry, bot);
    }

    public static void handleNeedAnyPotionCommand(BotEntry entry) {
        Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
        if (owner == null) {
            return;
        }
        int[] pots = AgentPotionService.countPotions(owner);
        handleNeedPotionCommand(entry, pots[0] <= pots[1]);
    }

    public static void handleNeedPotionCommand(BotEntry entry, boolean forHp) {
        AgentPotionService.OwnerPotShareResult result = AgentPotionService.offerPotShareToOwner(entry, forHp);
        String reply = AgentSupplyRequestOutcomeFlow.potionShareReply(
                result == AgentPotionService.OwnerPotShareResult.NO_DONOR,
                forHp);
        if (reply != null) {
            AgentBotReplyRuntime.queueReply(entry, reply);
        }
    }

    public static void handleNeedAmmoCommand(BotEntry entry) {
        Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
        if (owner == null) {
            return;
        }
        WeaponType weaponType = AgentAttackExecutionProvider.getEquippedWeaponType(owner);
        if (weaponType != WeaponType.BOW && weaponType != WeaponType.CROSSBOW) {
            AgentBotReplyRuntime.queueReply(entry, AgentSupplyRequestOutcomeFlow.ammoNotNeededReply());
            return;
        }
        AgentAmmoService.OwnerAmmoShareResult result = AgentAmmoService.offerAmmoShareToOwner(entry, weaponType);
        String reply = AgentSupplyRequestOutcomeFlow.ammoShareReply(
                result == AgentAmmoService.OwnerAmmoShareResult.NO_DONOR);
        if (reply != null) {
            AgentBotReplyRuntime.queueReply(entry, reply);
        }
    }

    private static BotEntry asBotEntry(AgentRuntimeEntry entry) {
        return (BotEntry) entry;
    }
}
