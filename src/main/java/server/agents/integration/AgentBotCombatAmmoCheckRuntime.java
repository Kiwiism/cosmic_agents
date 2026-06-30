package server.agents.integration;

import client.Character;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.WeaponType;
import server.StatEffect;
import server.agents.capabilities.combat.AgentAttackExecutionProvider;
import server.agents.capabilities.combat.AgentCombatAmmoCounter;
import server.agents.capabilities.combat.AgentCombatAmmoPolicy;
import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.bots.BotEntry;
import server.bots.BotInventoryManager;
import server.bots.BotManager;

public final class AgentBotCombatAmmoCheckRuntime {
    private AgentBotCombatAmmoCheckRuntime() {
    }

    public static void tickAmmoCheck(BotEntry entry, Character bot, int ammoLowWarn, int potLowWarn) {
        WeaponType weaponType = AgentAttackExecutionProvider.getEquippedWeaponType(bot);
        boolean mage = weaponType == WeaponType.WAND || weaponType == WeaponType.STAFF;
        boolean rangedAmmoWeapon = AgentCombatAmmoCounter.isRangedAmmoWeapon(weaponType);
        int mpPotionCount = 0;
        int ammo = Integer.MAX_VALUE;
        if (mage) {
            for (Item item : bot.getInventory(InventoryType.USE).list()) {
                if (item.getQuantity() <= 0) {
                    continue;
                }
                StatEffect effect = BotInventoryManager.itemEffect(item.getItemId());
                if (effect == null || !effect.getStatups().isEmpty()) {
                    continue;
                }
                if (effect.getMp() > 0 || effect.getMpRate() > 0) {
                    mpPotionCount += item.getQuantity();
                    if (mpPotionCount >= potLowWarn) {
                        break;
                    }
                }
            }
        } else if (rangedAmmoWeapon) {
            ammo = AgentCombatAmmoCounter.countAmmo(bot, weaponType);
        }

        AgentCombatAmmoPolicy.AmmoCheckDecision decision = AgentCombatAmmoPolicy.ammoCheckDecision(
                mage,
                rangedAmmoWeapon,
                mpPotionCount,
                ammo,
                ammoLowWarn,
                AgentBotAmmoStateRuntime.ammoWarnSent(entry),
                AgentBotAmmoStateRuntime.noAmmo(entry));
        switch (decision) {
            case CLEAR_WARNING_STATE -> AgentBotAmmoStateRuntime.clearAmmoWarningState(entry);
            case MAGE_NO_MP_POTS -> {
                AgentBotAmmoStateRuntime.setNoAmmo(entry, true);
                if (AgentBotModeStateRuntime.grinding(entry)) {
                    BotManager.getInstance().issueFollowOwner(entry);
                    AgentBotCombatRuntime.sayMapNow(bot, BotManager.randomReply(AgentDialogueCatalog.combatMpPotsOutReplies()));
                }
            }
            case PROJECTILE_LOW_AMMO -> {
                AgentBotAmmoStateRuntime.setAmmoWarnSent(entry, true);
                AgentBotCombatRuntime.sayMapNow(bot, BotManager.randomReply(AgentDialogueCatalog.combatAmmoLowReplies()));
            }
            case PROJECTILE_NO_AMMO -> {
                AgentBotAmmoStateRuntime.setNoAmmo(entry, true);
                if (AgentBotModeStateRuntime.grinding(entry)) {
                    BotManager.getInstance().issueFollowOwner(entry);
                    AgentBotCombatRuntime.sayMapNow(bot, BotManager.randomReply(AgentDialogueCatalog.combatAmmoOutReplies()));
                }
            }
            case NO_CHANGE -> {
            }
        }
    }
}
