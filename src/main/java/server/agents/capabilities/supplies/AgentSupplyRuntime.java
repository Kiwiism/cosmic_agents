package server.agents.capabilities.supplies;


import server.agents.runtime.AgentSchedulerRuntime;
import server.agents.capabilities.combat.AgentAttackExecutionProvider;

import client.Character;
import client.inventory.WeaponType;
import server.agents.capabilities.dialogue.AgentChatSupplyRequestFlow;
import server.agents.capabilities.dialogue.AgentSupplyRequestOutcomeFlow;
import server.agents.integration.AgentReplyRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.AgentInventoryGatewayRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.capabilities.trade.AgentOfferService;

/**
 * Agent-owned supply chat callback facade over potion, ammo, and upgrade-offer
 * services while reply delivery and live identity lookup remain runtime
 * boundaries.
 */
public final class AgentSupplyRuntime {
    private AgentSupplyRuntime() {
    }

    public static AgentChatSupplyRequestFlow.SupplyRequestCallbacks supplyRequestCallbacks(AgentRuntimeEntry entry) {
        return new AgentChatSupplyRequestFlow.SupplyRequestCallbacks() {
            @Override
            public void requestPotion(boolean hpPotion) {
                AgentSchedulerRuntime.afterRandomDelay(500, 700, () -> handleNeedPotionCommand(entry, hpPotion));
            }

            @Override
            public void requestAnyPotion() {
                AgentSchedulerRuntime.afterRandomDelay(500, 700, () -> handleNeedAnyPotionCommand(entry));
            }

            @Override
            public void requestAmmo() {
                AgentSchedulerRuntime.afterRandomDelay(500, 700, () -> handleNeedAmmoCommand(entry));
            }
        };
    }

    public static void handleRequestUpgradeCommand(AgentRuntimeEntry entry, Character bot) {
        AgentOfferService.clearPendingOfferForOwnerAsk(entry);
        if (AgentPotionService.requestLowSuppliesFromOwnerAsk(entry, bot, AgentInventoryGatewayRuntime.inventory())) {
            return;
        }
        AgentOfferService.requestBestUpgradeFromOwner(entry, bot);
    }

    public static void handleNeedAnyPotionCommand(AgentRuntimeEntry entry) {
        Character owner = AgentRuntimeIdentityRuntime.owner(entry);
        if (owner == null) {
            return;
        }
        int[] pots = AgentPotionService.countPotions(owner);
        handleNeedPotionCommand(entry, pots[0] <= pots[1]);
    }

    public static void handleNeedPotionCommand(AgentRuntimeEntry entry, boolean forHp) {
        AgentPotionService.OwnerPotShareResult result = AgentPotionService.offerPotShareToOwner(entry, forHp);
        String reply = AgentSupplyRequestOutcomeFlow.potionShareReply(
                result == AgentPotionService.OwnerPotShareResult.NO_DONOR,
                forHp);
        if (reply != null) {
            AgentReplyRuntime.queueReply(entry, reply);
        }
    }

    public static void handleNeedAmmoCommand(AgentRuntimeEntry entry) {
        Character owner = AgentRuntimeIdentityRuntime.owner(entry);
        if (owner == null) {
            return;
        }
        WeaponType weaponType = AgentAttackExecutionProvider.getEquippedWeaponType(owner);
        if (weaponType != WeaponType.BOW && weaponType != WeaponType.CROSSBOW) {
            AgentReplyRuntime.queueReply(entry, AgentSupplyRequestOutcomeFlow.ammoNotNeededReply());
            return;
        }
        AgentAmmoService.OwnerAmmoShareResult result = AgentAmmoService.offerAmmoShareToOwner(
                entry,
                weaponType,
                AgentInventoryGatewayRuntime.inventory());
        String reply = AgentSupplyRequestOutcomeFlow.ammoShareReply(
                result == AgentAmmoService.OwnerAmmoShareResult.NO_DONOR);
        if (reply != null) {
            AgentReplyRuntime.queueReply(entry, reply);
        }
    }

}
