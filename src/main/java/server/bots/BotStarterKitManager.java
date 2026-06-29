package server.bots;

import client.Character;
import client.Job;
import client.inventory.InventoryType;
import client.inventory.manipulator.InventoryManipulator;
import constants.inventory.ItemConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.capabilities.build.AgentStarterItemGrant;
import server.agents.capabilities.build.AgentStarterKitCatalog;
import server.agents.integration.AgentBotBuildStatusRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class BotStarterKitManager {
    private static final Logger log = LoggerFactory.getLogger(BotStarterKitManager.class);

    public static void advanceJob(BotEntry entry, Job newJob) {
        Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
        Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
        Job oldJob = bot.getJob();
        bot.changeJob(newJob);
        BotBuildManager.handleJobAdvance(entry, bot, oldJob, newJob);
        grantStarterKitIfEligible(bot, oldJob, newJob);
        BotEquipManager.autoEquip(bot, owner, null);
        AgentBotBuildStatusRuntime.checkBuildStatus(entry, bot);
    }

    static List<AgentStarterItemGrant> starterKitFor(Job job) {
        return AgentStarterKitCatalog.firstJobKitFor(job);
    }

    static boolean isFirstJobAdvancement(Job oldJob, Job newJob) {
        return AgentStarterKitCatalog.isFirstJobAdvancement(oldJob, newJob);
    }

    private static void grantStarterKitIfEligible(Character bot, Job oldJob, Job newJob) {
        if (!isFirstJobAdvancement(oldJob, newJob)) {
            return;
        }

        List<AgentStarterItemGrant> starterKit = starterKitFor(newJob);
        if (starterKit.isEmpty()) {
            return;
        }
        if (!canHoldStarterKit(bot, starterKit)) {
            log.warn("Bot '{}' could not receive {} starter kit due to inventory space", bot.getName(), newJob);
            return;
        }

        for (AgentStarterItemGrant grant : starterKit) {
            if (!InventoryManipulator.addById(bot.getClient(), grant.itemId(), grant.quantity())) {
                log.warn("Bot '{}' failed to receive starter item {} x{} for job {}",
                        bot.getName(), grant.itemId(), grant.quantity(), newJob);
            }
        }
    }

    private static boolean canHoldStarterKit(Character bot, List<AgentStarterItemGrant> starterKit) {
        Map<InventoryType, Integer> requiredSlots = new EnumMap<>(InventoryType.class);
        for (AgentStarterItemGrant grant : starterKit) {
            InventoryType inventoryType = ItemConstants.getInventoryType(grant.itemId());
            if (inventoryType == InventoryType.EQUIP) {
                requiredSlots.merge(InventoryType.EQUIP, 1, Integer::sum);
                continue;
            }
            if (!bot.canHold(grant.itemId(), grant.quantity())) {
                return false;
            }
        }

        for (Map.Entry<InventoryType, Integer> requirement : requiredSlots.entrySet()) {
            if (bot.getInventory(requirement.getKey()).getNumFreeSlot() < requirement.getValue()) {
                return false;
            }
        }
        return true;
    }

}
