package server.bots;

import client.BotClient;
import client.Character;
import client.inventory.Item;
import server.ItemInformationProvider;

import java.util.List;
import java.util.regex.Pattern;

final class BotOfferManager {
    private static final Pattern POSITIVE_CONFIRM_PATTERN = Pattern.compile(
            "\\b(yes|yep|yeah|yea|y|ok|sure|confirm|do\\s+it|go\\s+(ahead|for\\s+it))\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NEGATIVE_CONFIRM_PATTERN = Pattern.compile(
            "\\b(no|nope|nah|nvm|never\\s*mind|dont|don't|not\\s+now|skip)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final List<String> BOT_ACCEPT_MSGS = List.of(
            "sure!", "ok!", "ty!", "yes pls", "thx!", "ooh nice, ty", "yes!");

    private BotOfferManager() {}

    static boolean hasOfferReservation(BotEntry entry) {
        return entry.pendingLootOfferItem != null
                && entry.pendingLootOfferRecipientId > 0;
    }

    static boolean hasPendingOffer(BotEntry entry) {
        return hasOfferReservation(entry) && entry.pendingLootOfferExpiresAt > 0L;
    }

    static void notifyOwnerGainedEquip(BotEntry entry, Character bot, Item item) {
        if (BotChatManager.isOwnerIdle(entry)) {
            return;
        }
        if (entry.requestedUpgradeItemIds.contains(item.getItemId())) {
            return;
        }
        if (entry.pendingAction != null || entry.pendingTradeCategory != null || hasOfferReservation(entry)) {
            return;
        }
        Character owner = entry.owner;
        if (owner == null) {
            return;
        }

        List<BotEquipManager.EquipRecommendation> recs = BotEquipManager.findRecommendedEquips(bot, owner);
        boolean isUpgrade = recs.stream().anyMatch(r -> r.candidate().getItemId() == item.getItemId());
        if (!isUpgrade) {
            return;
        }

        entry.requestedUpgradeItemIds.add(item.getItemId());
        createOwnerUpgradeRequest(entry, bot, owner, item);
    }

    static void requestBestUpgradeFromOwner(BotEntry entry, Character bot) {
        Character owner = entry.owner;
        if (owner == null) {
            return;
        }
        if (entry.pendingAction != null || entry.pendingTradeCategory != null || hasOfferReservation(entry)) {
            BotManager.getInstance().botSay(bot, "busy rn, ask me again in a bit");
            return;
        }
        List<BotEquipManager.EquipRecommendation> recs = BotEquipManager.findRecommendedEquips(bot, owner);
        if (recs.isEmpty()) {
            BotManager.getInstance().botSay(bot, "nothing i need from you rn, im good!");
            return;
        }
        Item candidate = recs.get(0).candidate();
        entry.requestedUpgradeItemIds.add(candidate.getItemId());
        createOwnerUpgradeRequest(entry, bot, owner, candidate);
    }

    static boolean offerBestRecommendedGear(BotEntry entry, Character bot, Character owner) {
        if (owner == null) {
            return false;
        }

        List<BotEquipManager.EquipRecommendation> recs = BotEquipManager.findRecommendedEquips(owner, bot);
        if (recs.isEmpty()) {
            return false;
        }

        return offerGearItem(entry, bot, owner, recs.get(0).candidate());
    }

    static boolean offerBestGearToSibling(BotEntry entry, Character bot) {
        Character owner = entry.owner;
        if (owner == null) {
            return false;
        }

        List<BotEntry> siblings = BotManager.getInstance().getBotEntries(owner.getId());
        for (BotEntry sibling : siblings) {
            if (sibling == entry || sibling.bot == null || sibling.bot.getMapId() != bot.getMapId()) {
                continue;
            }
            List<BotEquipManager.EquipRecommendation> recs = BotEquipManager.findRecommendedEquips(sibling.bot, bot);
            if (!recs.isEmpty()) {
                return offerGearItem(entry, bot, sibling.bot, recs.get(0).candidate());
            }
        }

        return false;
    }

    static void scheduleLootOfferPrompt(BotEntry entry, Character bot, Item item, long delayMs) {
        Character owner = entry.owner;
        long now = System.currentTimeMillis();
        if (owner == null
                || item == null
                || entry.pendingGearPromptAt > now
                || BotChatManager.isOwnerIdle(entry)
                || entry.pendingAction != null
                || entry.pendingTradeCategory != null
                || hasOfferReservation(entry)
                || !BotInventoryManager.hasItem(bot, item)) {
            return;
        }

        Character recipient = findLootOfferRecipient(entry, bot, item);
        if (recipient == null) {
            return;
        }

        entry.pendingDropCategory = null;
        entry.pendingLootOfferItem = item;
        entry.pendingLootOfferRecipientId = recipient.getId();
        entry.pendingLootOfferExpiresAt = 0L;
        entry.pendingLootOfferBotRequesting = false;

        long scheduledAt = now + Math.max(0L, delayMs);
        entry.pendingGearPromptAt = scheduledAt;
        BotManager.after(delayMs, () -> promptLootOfferAfterLoot(entry, bot, item, recipient.getId(), scheduledAt));
    }

    static boolean handlePendingOfferResponse(BotEntry entry, Character speaker, String message) {
        expirePendingOffer(entry);
        if (!hasPendingOffer(entry)
                || speaker == null
                || speaker.getId() != entry.pendingLootOfferRecipientId) {
            return false;
        }

        if (POSITIVE_CONFIRM_PATTERN.matcher(message).find()) {
            if (entry.pendingLootOfferBotRequesting) {
                clearPendingOffer(entry);
                BotManager.after(BotManager.randMs(400, 600), () ->
                        BotManager.getInstance().botSay(entry.bot, "ty! inv me?"));
            } else {
                Item item = entry.pendingLootOfferItem;
                entry.pendingDropCategory = null;
                entry.pendingLootOfferExpiresAt = 0L;
                entry.pendingLootOfferBotRequesting = false;
                entry.pendingLootOfferRecipientId = 0;
                BotManager.after(BotManager.randMs(900, 1100), () -> {
                    entry.pendingLootOfferItem = null;
                    BotInventoryManager.startTradeTransfer(item, speaker, entry, entry.bot);
                });
            }
            return true;
        }
        if (NEGATIVE_CONFIRM_PATTERN.matcher(message).find()) {
            clearPendingOffer(entry);
            BotManager.after(BotManager.randMs(400, 600), () ->
                    BotManager.getInstance().botSay(entry.bot, "ok, keeping it for now"));
            return true;
        }

        return false;
    }

    static void expirePendingOffer(BotEntry entry) {
        if (hasPendingOffer(entry) && System.currentTimeMillis() >= entry.pendingLootOfferExpiresAt) {
            clearPendingOffer(entry);
        }
    }

    private static void createOwnerUpgradeRequest(BotEntry entry, Character bot, Character owner, Item ownerItem) {
        String itemName = ItemInformationProvider.getInstance().getName(ownerItem.getItemId());
        if (itemName == null || itemName.isBlank()) {
            itemName = String.valueOf(ownerItem.getItemId());
        }

        entry.pendingDropCategory = null;
        entry.pendingLootOfferItem = ownerItem;
        entry.pendingLootOfferRecipientId = owner.getId();
        entry.pendingLootOfferExpiresAt = System.currentTimeMillis() + 45_000L;
        entry.pendingLootOfferBotRequesting = true;

        List<String> prompts = List.of(
                "hey, that " + itemName + " would be an upgrade for me, can i have it pls?",
                "Can I have your " + itemName + "?",
                "Your " + itemName + " would be better on me! trade it over?",
                "I could use that " + itemName + " of yours ;)",
                "that " + itemName + " is an upgrade for me, want to trade?");
        BotChatManager.queueBotSay(entry, BotManager.randomReply(prompts));
    }

    private static boolean offerGearItem(BotEntry entry, Character bot, Character recipient, Item item) {
        if (entry.pendingAction != null || entry.pendingTradeCategory != null || hasOfferReservation(entry)
                || !BotInventoryManager.hasItem(bot, item)) {
            return false;
        }
        entry.pendingDropCategory = null;
        entry.pendingLootOfferItem = item;
        entry.pendingLootOfferRecipientId = recipient.getId();
        entry.pendingLootOfferExpiresAt = System.currentTimeMillis() + 30_000L;
        entry.pendingLootOfferBotRequesting = false;
        long promptDelayMs = BotChatManager.queueBotSayWithEstimatedDelay(entry, buildLootOfferPrompt(recipient, entry.owner, item));
        scheduleBotLootOfferAutoAccept(entry, recipient, promptDelayMs);
        return true;
    }

    private static void promptLootOfferAfterLoot(BotEntry entry, Character bot, Item item, int recipientId, long scheduledAt) {
        if (entry.pendingGearPromptAt != scheduledAt) {
            return;
        }
        entry.pendingGearPromptAt = 0L;

        if (entry.pendingLootOfferItem != item || entry.pendingLootOfferRecipientId != recipientId) {
            clearPendingOffer(entry);
            return;
        }

        Character owner = entry.owner;
        Character recipient = resolveReservedOfferRecipient(entry, bot, recipientId);
        if (owner == null
                || entry.pendingAction != null
                || entry.pendingTradeCategory != null
                || recipient == null
                || !BotInventoryManager.hasItem(bot, item)) {
            clearPendingOffer(entry);
            return;
        }

        entry.pendingDropCategory = null;
        entry.pendingLootOfferItem = item;
        entry.pendingLootOfferRecipientId = recipient.getId();
        entry.pendingLootOfferExpiresAt = System.currentTimeMillis() + 30_000L;
        entry.pendingLootOfferBotRequesting = false;
        long promptDelayMs = BotChatManager.queueBotSayWithEstimatedDelay(entry, buildLootOfferPrompt(recipient, owner, item));
        scheduleBotLootOfferAutoAccept(entry, recipient, promptDelayMs);
    }

    private static void scheduleBotLootOfferAutoAccept(BotEntry entry, Character recipient, long promptDelayMs) {
        if (!(recipient.getClient() instanceof BotClient)) {
            return;
        }
        long replyDelayMs = promptDelayMs + BotManager.randMs(1800, 2200);
        BotManager.after(replyDelayMs, () -> autoAcceptLootOffer(entry, recipient));
    }

    private static void autoAcceptLootOffer(BotEntry entry, Character recipientBot) {
        if (!hasPendingOffer(entry) || entry.pendingLootOfferRecipientId != recipientBot.getId()) {
            return;
        }
        BotManager.getInstance().botSay(recipientBot, BotManager.randomReply(BOT_ACCEPT_MSGS));
        handlePendingOfferResponse(entry, recipientBot, "yes");
    }

    private static String buildLootOfferPrompt(String recipientName, String itemName, boolean targetIsOwner) {
        List<String> prompts = targetIsOwner
                ? List.of(
                        "I have %s, you want?",
                        "picked up %s, want it?",
                        "I got %s for you, want?")
                : List.of(
                        "%s, I have %s, you want?",
                        "%s, picked up %s, want it?",
                        "%s, I got %s if you want it");
        String format = BotManager.randomReply(prompts);
        return targetIsOwner ? String.format(format, itemName) : String.format(format, recipientName, itemName);
    }

    private static String buildLootOfferPrompt(Character recipient, Character owner, Item item) {
        String itemName = ItemInformationProvider.getInstance().getName(item.getItemId());
        if (itemName == null || itemName.isBlank()) {
            itemName = String.valueOf(item.getItemId());
        }
        boolean targetIsOwner = owner != null && recipient.getId() == owner.getId();
        return buildLootOfferPrompt(recipient.getName(), itemName, targetIsOwner);
    }

    private static Character findLootOfferRecipient(BotEntry entry, Character bot, Item item) {
        Character owner = entry.owner;
        if (owner == null) {
            return null;
        }
        if (BotEquipManager.findRecommendationForItem(owner, bot, item) != null) {
            return owner;
        }

        BotOwnershipService ownership = BotOwnershipService.getInstance();
        for (Character member : owner.getPartyMembersOnSameMap()) {
            if (member == null
                    || member.getId() == owner.getId()
                    || member.getId() == bot.getId()
                    || !(member.getClient() instanceof BotClient)
                    || !ownership.isAuthorizedOwner(member.getId(), owner.getId())) {
                continue;
            }
            if (BotEquipManager.findRecommendationForItem(member, bot, item) != null) {
                return member;
            }
        }
        return null;
    }

    private static Character resolveReservedOfferRecipient(BotEntry entry, Character bot, int recipientId) {
        Character owner = entry.owner;
        if (owner != null && owner.getId() == recipientId) {
            return owner;
        }
        if (bot.getMap() != null) {
            Character onMap = bot.getMap().getCharacterById(recipientId);
            if (onMap != null) {
                return onMap;
            }
        }
        if (owner != null) {
            for (Character member : owner.getPartyMembersOnSameMap()) {
                if (member != null && member.getId() == recipientId) {
                    return member;
                }
            }
        }
        return null;
    }

    private static void clearPendingOffer(BotEntry entry) {
        entry.pendingDropCategory = null;
        entry.pendingLootOfferItem = null;
        entry.pendingLootOfferRecipientId = 0;
        entry.pendingLootOfferExpiresAt = 0L;
        entry.pendingLootOfferBotRequesting = false;
    }
}
