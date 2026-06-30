package server.agents.integration;

import server.agents.capabilities.combat.AgentAttackExecutionProvider;

import client.Character;
import client.inventory.WeaponType;
import server.agents.capabilities.dialogue.AgentChatSupplyRequestFlow;
import server.agents.capabilities.dialogue.AgentSupplyRequestOutcomeFlow;
import server.agents.capabilities.supplies.AgentAmmoService;
import server.bots.BotEntry;
import server.bots.BotOfferManager;
import server.bots.BotPotionManager;

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
                AgentBotSupplySchedulerRuntime.afterRandomDelay(500, 700, () -> handleNeedPotionCommand(entry, hpPotion));
            }

            @Override
            public void requestAnyPotion() {
                AgentBotSupplySchedulerRuntime.afterRandomDelay(500, 700, () -> handleNeedAnyPotionCommand(entry));
            }

            @Override
            public void requestAmmo() {
                AgentBotSupplySchedulerRuntime.afterRandomDelay(500, 700, () -> handleNeedAmmoCommand(entry));
            }
        };
    }

    public static void handleRequestUpgradeCommand(BotEntry entry, Character bot) {
        BotOfferManager.clearPendingOfferForOwnerAsk(entry);
        if (BotPotionManager.requestLowSuppliesFromOwnerAsk(entry, bot)) {
            return;
        }
        BotOfferManager.requestBestUpgradeFromOwner(entry, bot);
    }

    public static void handleNeedAnyPotionCommand(BotEntry entry) {
        if (entry.owner() == null) {
            return;
        }
        int[] pots = BotPotionManager.countPotions(entry.owner());
        handleNeedPotionCommand(entry, pots[0] <= pots[1]);
    }

    public static void handleNeedPotionCommand(BotEntry entry, boolean forHp) {
        BotPotionManager.OwnerPotShareResult result = BotPotionManager.offerPotShareToOwner(entry, forHp);
        String reply = AgentSupplyRequestOutcomeFlow.potionShareReply(
                result == BotPotionManager.OwnerPotShareResult.NO_DONOR,
                forHp);
        if (reply != null) {
            AgentBotSupplyReplyRuntime.queueReply(entry, reply);
        }
    }

    public static void handleNeedAmmoCommand(BotEntry entry) {
        Character owner = entry.owner();
        if (owner == null) {
            return;
        }
        WeaponType weaponType = AgentAttackExecutionProvider.getEquippedWeaponType(owner);
        if (weaponType != WeaponType.BOW && weaponType != WeaponType.CROSSBOW) {
            AgentBotSupplyReplyRuntime.queueReply(entry, AgentSupplyRequestOutcomeFlow.ammoNotNeededReply());
            return;
        }
        AgentAmmoService.OwnerAmmoShareResult result = AgentAmmoService.offerAmmoShareToOwner(entry, weaponType);
        String reply = AgentSupplyRequestOutcomeFlow.ammoShareReply(
                result == AgentAmmoService.OwnerAmmoShareResult.NO_DONOR);
        if (reply != null) {
            AgentBotSupplyReplyRuntime.queueReply(entry, reply);
        }
    }
}
