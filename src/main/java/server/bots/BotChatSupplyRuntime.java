package server.bots;

import client.Character;
import client.inventory.WeaponType;
import server.agents.capabilities.dialogue.AgentChatSupplyRequestFlow;
import server.agents.capabilities.dialogue.AgentSupplyRequestOutcomeFlow;

final class BotChatSupplyRuntime {
    private BotChatSupplyRuntime() {
    }

    static AgentChatSupplyRequestFlow.SupplyRequestCallbacks supplyRequestCallbacks(BotEntry entry) {
        return new AgentChatSupplyRequestFlow.SupplyRequestCallbacks() {
            @Override
            public void requestPotion(boolean hpPotion) {
                BotManager.after(BotManager.randMs(500, 700), () -> handleNeedPotionCommand(entry, hpPotion));
            }

            @Override
            public void requestAnyPotion() {
                BotManager.after(BotManager.randMs(500, 700), () -> handleNeedAnyPotionCommand(entry));
            }

            @Override
            public void requestAmmo() {
                BotManager.after(BotManager.randMs(500, 700), () -> handleNeedAmmoCommand(entry));
            }
        };
    }

    static void handleRequestUpgradeCommand(BotEntry entry, Character bot) {
        BotOfferManager.clearPendingOfferForOwnerAsk(entry);
        if (BotPotionManager.requestLowSuppliesFromOwnerAsk(entry, bot)) {
            return;
        }
        BotOfferManager.requestBestUpgradeFromOwner(entry, bot);
    }

    private static void handleNeedAnyPotionCommand(BotEntry entry) {
        if (entry.owner == null) {
            return;
        }
        int[] pots = BotPotionManager.countPotions(entry.owner);
        handleNeedPotionCommand(entry, pots[0] <= pots[1]);
    }

    private static void handleNeedPotionCommand(BotEntry entry, boolean forHp) {
        BotPotionManager.OwnerPotShareResult result = BotPotionManager.offerPotShareToOwner(entry, forHp);
        String reply = AgentSupplyRequestOutcomeFlow.potionShareReply(
                result == BotPotionManager.OwnerPotShareResult.NO_DONOR,
                forHp);
        if (reply != null) {
            BotChatReplyRuntime.queueReply(entry, reply);
        }
    }

    private static void handleNeedAmmoCommand(BotEntry entry) {
        Character owner = entry.owner;
        if (owner == null) {
            return;
        }
        WeaponType weaponType = BotAttackExecutionProvider.getEquippedWeaponType(owner);
        if (weaponType != WeaponType.BOW && weaponType != WeaponType.CROSSBOW) {
            BotChatReplyRuntime.queueReply(entry, AgentSupplyRequestOutcomeFlow.ammoNotNeededReply());
            return;
        }
        BotAmmoManager.OwnerAmmoShareResult result = BotAmmoManager.offerAmmoShareToOwner(entry, weaponType);
        String reply = AgentSupplyRequestOutcomeFlow.ammoShareReply(
                result == BotAmmoManager.OwnerAmmoShareResult.NO_DONOR);
        if (reply != null) {
            BotChatReplyRuntime.queueReply(entry, reply);
        }
    }
}
