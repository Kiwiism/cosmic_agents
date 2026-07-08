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
import server.agents.capabilities.dialogue.AgentDialogueSelector;
import server.agents.capabilities.inventory.AgentUseItemClassificationPolicy;
import server.agents.runtime.AgentRuntimeEntry;

public final class AgentCombatAmmoCheckRuntime {
    private AgentCombatAmmoCheckRuntime() {
    }

    public static void tickAmmoCheck(AgentRuntimeEntry entry, Character bot, int ammoLowWarn, int potLowWarn) {
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
                StatEffect effect = AgentUseItemClassificationPolicy.itemEffect(item.getItemId());
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
                AgentAmmoStateRuntime.ammoWarnSent(entry),
                AgentAmmoStateRuntime.noAmmo(entry));
        switch (decision) {
            case CLEAR_WARNING_STATE -> AgentAmmoStateRuntime.clearAmmoWarningState(entry);
            case MAGE_NO_MP_POTS -> {
                AgentAmmoStateRuntime.setNoAmmo(entry, true);
                if (AgentModeStateRuntime.grinding(entry)) {
                    AgentMovementCommandRuntime.followOwner(entry);
                    AgentCombatRuntime.sayMapNow(bot, AgentDialogueSelector.randomReply(AgentDialogueCatalog.combatMpPotsOutReplies()));
                }
            }
            case PROJECTILE_LOW_AMMO -> {
                AgentAmmoStateRuntime.setAmmoWarnSent(entry, true);
                AgentCombatRuntime.sayMapNow(bot, AgentDialogueSelector.randomReply(AgentDialogueCatalog.combatAmmoLowReplies()));
            }
            case PROJECTILE_NO_AMMO -> {
                AgentAmmoStateRuntime.setNoAmmo(entry, true);
                if (AgentModeStateRuntime.grinding(entry)) {
                    AgentMovementCommandRuntime.followOwner(entry);
                    AgentCombatRuntime.sayMapNow(bot, AgentDialogueSelector.randomReply(AgentDialogueCatalog.combatAmmoOutReplies()));
                }
            }
            case NO_CHANGE -> {
            }
        }
    }
}
