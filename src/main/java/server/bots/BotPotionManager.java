package server.bots;

import client.Character;
import client.Skill;
import client.SkillFactory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.keybind.KeyBinding;
import constants.skills.Crusader;
import constants.skills.DawnWarrior;
import constants.skills.Magician;
import constants.skills.Warrior;
import constants.skills.WhiteKnight;
import server.ItemInformationProvider;
import server.StatEffect;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

final class BotPotionManager {
    private static final List<String> GRIND_REPLIES = List.of(
            "ok", "on it", "lets get it", "farming time", "got it",
            "sure", "ok boss", "time to grind",
            "lets farm", "hunting time", "aye, killing stuff",
            "lezgo", "gonna get some kills", "on it boss",
            "time to work", "lets do this");
    private static final List<String> POT_REQUEST_HP_MSGS = List.of(
            "anyone have HP pots? running low",
            "low on HP pots, does anyone have some?",
            "need HP pots!! anyone?",
            "HP pots? who has some",
            "anyone got HP pots to spare?");
    private static final List<String> POT_REQUEST_MP_MSGS = List.of(
            "anyone have MP pots? running low",
            "low on MP pots, does anyone have some?",
            "need MP pots!! anyone?",
            "MP pots? who has some",
            "anyone got MP pots to spare?");
    private static final List<String> POT_OFFER_HP_MSGS = List.of(
            "got some HP pots, inv u",
            "yep i have HP pots, inv u",
            "sure, got spare HP pots",
            "coming, inv",
            "got you");
    private static final List<String> POT_OFFER_MP_MSGS = List.of(
            "got some MP pots, inv u",
            "yep i have MP pots, inv u",
            "sure, got spare MP pots",
            "coming, inv",
            "got you");

    // ownerCharId -> shared HP/MP 30 s request cooldown
    private static final Map<Integer, Long> potShareCooldownUntil = new ConcurrentHashMap<>();
    // ownerCharId -> category-specific 10 min failed-request backoff
    private static final Map<Integer, Long> potShareHpBackoffUntil = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> potShareMpBackoffUntil = new ConcurrentHashMap<>();

    private BotPotionManager() {
    }

    static int[] countPotions(Character bot) {
        List<Item> items = bot.getInventory(InventoryType.USE).list().stream()
                .filter(item -> BotInventoryManager.isRecoveryPotion(item.getItemId()))
                .toList();
        return countPotions(items, BotInventoryManager::itemEffect);
    }

    static int[] countPotions(List<Item> items, Function<Integer, StatEffect> effectLookup) {
        int hp = 0;
        int mp = 0;
        for (Item item : items) {
            StatEffect effect = effectLookup.apply(item.getItemId());
            if (effect == null) {
                continue;
            }
            int quantity = item.getQuantity();
            if (effect.getHp() > 0 || effect.getHpRate() > 0) {
                hp += quantity;
            }
            if (effect.getMp() > 0 || effect.getMpRate() > 0) {
                mp += quantity;
            }
        }
        return new int[]{hp, mp};
    }

    static void setupAutopotForBot(Character bot) {
        ItemInformationProvider infoProvider = ItemInformationProvider.getInstance();
        int hpItemId = -1;
        int mpItemId = -1;
        int bestHp = 0;
        int bestMp = 0;
        for (Item item : bot.getInventory(InventoryType.USE).list()) {
            if (item.getQuantity() <= 0) {
                continue;
            }

            StatEffect effect;
            try {
                effect = infoProvider.getItemEffect(item.getItemId());
            } catch (Exception exception) {
                continue;
            }
            if (effect == null) {
                continue;
            }

            int hpGain = resolveEffectiveRecoveryAmount(bot, effect, true);
            if (hpGain > bestHp) {
                bestHp = hpGain;
                hpItemId = item.getItemId();
            }

            int mpGain = resolveEffectiveRecoveryAmount(bot, effect, false);
            if (mpGain > bestMp) {
                bestMp = mpGain;
                mpItemId = item.getItemId();
            }
        }

        if (hpItemId > 0) {
            bot.changeKeybinding(91, new KeyBinding(2, hpItemId));
            bot.setAutopotHpAlert(BotManager.cfg.AUTOPOT_HP_THRESH);
        } else {
            bot.getKeymap().remove(91);
            bot.setAutopotHpAlert(0f);
        }

        if (mpItemId > 0) {
            bot.changeKeybinding(92, new KeyBinding(2, mpItemId));
            bot.setAutopotMpAlert(BotManager.cfg.AUTOPOT_MP_THRESH);
        } else {
            bot.getKeymap().remove(92);
            bot.setAutopotMpAlert(0f);
        }
    }

    static String grindStartMessage(Character bot) {
        int[] pots = countPotions(bot);
        int hp = pots[0];
        int mp = pots[1];
        String base = BotManager.randomReply(GRIND_REPLIES);
        if (hp >= BotManager.cfg.POT_LOW_WARN && mp >= BotManager.cfg.POT_LOW_WARN) {
            return base;
        }

        StringBuilder message = new StringBuilder(base).append(", but");
        if (hp < BotManager.cfg.POT_LOW_WARN) {
            message.append(" only ").append(hp).append(" HP pots");
        }
        if (hp < BotManager.cfg.POT_LOW_WARN && mp < BotManager.cfg.POT_LOW_WARN) {
            message.append(" and");
        }
        if (mp < BotManager.cfg.POT_LOW_WARN) {
            message.append(" only ").append(mp).append(" MP pots");
        }
        return message.append(" left").toString();
    }

    static void tickPotionCheck(BotEntry entry, Character bot) {
        if (entry.potCheckTimerMs > 0) {
            entry.potCheckTimerMs = BotMovementManager.tickDown(entry.potCheckTimerMs);
            return;
        }
        entry.potCheckTimerMs = BotMovementManager.delayAfterCurrentTick(BotManager.cfg.POT_CHECK_INTERVAL_MS);

        setupAutopotForBot(bot);
        BotCombatManager.tickAmmoCheck(entry, bot);

        if (!entry.grinding && !entry.following) {
            return;
        }
        BotAmmoManager.tickAmmoShareCheck(entry, bot);

        int[] pots = countPotions(bot);
        if (pots[0] >= BotManager.cfg.POT_LOW_WARN) {
            entry.potShareRequestedHp = false;
        } else if (!entry.potShareRequestedHp && requestPotShare(entry, bot, true)) {
            entry.potShareRequestedHp = true;
        }

        if (pots[1] >= BotManager.cfg.POT_LOW_WARN) {
            entry.potShareRequestedMp = false;
        } else if (!entry.potShareRequestedMp && requestPotShare(entry, bot, false)) {
            entry.potShareRequestedMp = true;
        }

        if (!entry.grinding) {
            return;
        }
        if (pots[0] < BotManager.cfg.POT_STOP && bot.getHp() < bot.getMaxHp() * 0.4f) {
            entry.grinding = false;
            entry.following = true;
            BotManager.getInstance().botSay(bot, "low on pots!! walking to you");
            bot.changeFaceExpression(Emote.GLARE.getValue());
        }
    }

    static void checkPotShareOnModeStart(BotEntry entry, Character bot) {
        entry.potShareRequestedHp = false;
        entry.potShareRequestedMp = false;
        BotAmmoManager.checkAmmoShareOnModeStart(entry, bot);
        int[] pots = countPotions(bot);
        if (pots[0] < BotManager.cfg.POT_LOW_WARN && requestPotShare(entry, bot, true)) {
            entry.potShareRequestedHp = true;
        }
        if (pots[1] < BotManager.cfg.POT_LOW_WARN && requestPotShare(entry, bot, false)) {
            entry.potShareRequestedMp = true;
        }
    }

    static void tickPassiveRecovery(BotEntry entry, Character bot) {
        boolean hpFull = bot.getHp() >= bot.getCurrentMaxHp();
        boolean mpFull = bot.getMp() >= bot.getCurrentMaxMp();
        if (hpFull && mpFull) {
            entry.mpRecoveryTimerMs = 0;
            return;
        }
        if (entry.mpRecoveryTimerMs > 0) {
            entry.mpRecoveryTimerMs = BotMovementManager.tickDown(entry.mpRecoveryTimerMs);
            return;
        }

        entry.mpRecoveryTimerMs = BotMovementManager.delayAfterCurrentTick(BotManager.cfg.MP_RECOVERY_INTERVAL_MS);

        int hpRecovery = hpFull ? 0 : calculatePassiveHpRecovery(entry, bot);
        int mpRecovery = mpFull ? 0 : calculatePassiveMpRecovery(entry, bot);
        if (hpRecovery <= 0 && mpRecovery <= 0) {
            return;
        }

        bot.addMPHP(hpRecovery, mpRecovery);
    }

    static boolean requestPotShare(BotEntry entry, Character bot, boolean forHp) {
        Character owner = entry.owner;
        if (owner == null || bot.getTrade() != null || entry.pendingTradeCategory != null) {
            return false;
        }

        long now = System.currentTimeMillis();
        Map<Integer, Long> categoryBackoff = forHp ? potShareHpBackoffUntil : potShareMpBackoffUntil;
        if (now < categoryBackoff.getOrDefault(owner.getId(), 0L)) {
            return false;
        }
        if (now < potShareCooldownUntil.getOrDefault(owner.getId(), 0L)) {
            return false;
        }
        potShareCooldownUntil.put(owner.getId(), now + 30_000L);

        BotManager.getInstance().botSay(bot, BotManager.randomReply(forHp ? POT_REQUEST_HP_MSGS : POT_REQUEST_MP_MSGS));

        List<BotEntry> siblings = BotManager.getInstance().getBotEntries(owner.getId());
        BotEntry bestEntry = null;
        int bestCount = 0;
        for (BotEntry sibling : siblings) {
            if (sibling == entry || sibling.bot == null || sibling.bot.getMapId() != bot.getMapId()) {
                continue;
            }
            int[] pots = countPotions(sibling.bot);
            int count = forHp ? pots[0] : pots[1];
            if (count > bestCount) {
                bestCount = count;
                bestEntry = sibling;
            }
        }

        if (bestEntry == null) {
            categoryBackoff.put(owner.getId(), now + 10 * 60_000L);
            return true;
        }

        BotEntry donorEntry = bestEntry;
        Character donorBot = donorEntry.bot;
        boolean qualifies = bestCount > BotManager.cfg.POT_LOW_WARN * 3;
        int maxQty = bestCount / 3;
        Character needyBot = bot;
        if (qualifies) {
            BotManager.after(BotManager.randMs(2000, 3000), () -> {
                if (donorBot.getTrade() != null || donorEntry.pendingTradeCategory != null) {
                    return;
                }
                List<Item> items = BotInventoryManager.collectPotShareItems(donorBot, forHp, maxQty);
                if (items.isEmpty()) {
                    return;
                }
                BotManager.getInstance().botSay(donorBot, BotManager.randomReply(forHp ? POT_OFFER_HP_MSGS : POT_OFFER_MP_MSGS));
                BotManager.after(BotManager.randMs(900, 1100), () ->
                        BotInventoryManager.startPotShareTransfer(items, needyBot, donorEntry, donorBot, maxQty));
            });
        } else {
            categoryBackoff.put(owner.getId(), now + 10 * 60_000L);
            String ownerName = owner.getName();
            List<String> noQualMessages = List.of(
                    "low too, maybe " + ownerName + " has some?",
                    "wish i could help, try " + ownerName + "?",
                    "i'm low too :/ check with " + ownerName,
                    "barely have any myself, ask " + ownerName);
            BotManager.after(BotManager.randMs(4000, 6000), () ->
                    BotManager.getInstance().botSay(donorBot, BotManager.randomReply(noQualMessages)));
        }
        return true;
    }

    private static int calculatePassiveHpRecovery(BotEntry entry, Character bot) {
        int recovery = BotManager.cfg.BASE_HP_RECOVERY;
        if (!isStandingStillForRecovery(entry)) {
            return recovery;
        }

        recovery += getFlatHpRecoveryBonus(bot, Warrior.IMPROVED_HPREC);
        return recovery;
    }

    private static int calculatePassiveMpRecovery(BotEntry entry, Character bot) {
        int recovery = BotManager.cfg.BASE_MP_RECOVERY;
        if (!isStandingStillForRecovery(entry)) {
            return recovery;
        }

        recovery += getFlatMpRecoveryBonus(bot, Crusader.IMPROVING_MPREC);
        recovery += getFlatMpRecoveryBonus(bot, WhiteKnight.IMPROVING_MP_RECOVERY);
        recovery += getFlatMpRecoveryBonus(bot, DawnWarrior.INCREASED_MP_RECOVERY);
        recovery += getMagicianMpRecoveryBonus(bot);
        return recovery;
    }

    private static boolean isStandingStillForRecovery(BotEntry entry) {
        if (entry.inAir || entry.climbing) {
            return false;
        }
        return entry.moveDir == 0
                && BotPhysicsEngine.isStandingStance(BotPhysicsEngine.resolveStance(entry));
    }

    private static int getFlatHpRecoveryBonus(Character bot, int skillId) {
        Skill skill = SkillFactory.getSkill(skillId);
        if (skill == null) {
            return 0;
        }
        int level = bot.getSkillLevel(skill);
        if (level <= 0) {
            return 0;
        }
        return skill.getEffect(level).getHp();
    }

    private static int getFlatMpRecoveryBonus(Character bot, int skillId) {
        Skill skill = SkillFactory.getSkill(skillId);
        if (skill == null) {
            return 0;
        }
        int level = bot.getSkillLevel(skill);
        if (level <= 0) {
            return 0;
        }
        return skill.getEffect(level).getMp();
    }

    private static int getMagicianMpRecoveryBonus(Character bot) {
        Skill skill = SkillFactory.getSkill(Magician.IMPROVED_MP_RECOVERY);
        if (skill == null) {
            return 0;
        }
        int level = bot.getSkillLevel(skill);
        if (level <= 0) {
            return 0;
        }
        return Math.max(0, (bot.getInt() / 10) * level);
    }

    private static int resolveEffectiveRecoveryAmount(Character bot, StatEffect effect, boolean hp) {
        if (effect == null) {
            return 0;
        }
        int flat = hp ? effect.getHp() : effect.getMp();
        if (flat > 0) {
            return flat;
        }
        double rate = hp ? effect.getHpRate() : effect.getMpRate();
        if (rate <= 0) {
            return 0;
        }
        int max = hp ? bot.getCurrentMaxHp() : bot.getCurrentMaxMp();
        return (int) Math.ceil(max * rate);
    }
}
