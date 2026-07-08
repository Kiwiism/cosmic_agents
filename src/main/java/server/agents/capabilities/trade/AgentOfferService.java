package server.agents.capabilities.trade;

import server.agents.capabilities.equipment.AgentEquipRecommendation;

import server.agents.auth.AgentOwnershipService;
import server.agents.capabilities.combat.AgentAttackExecutionProvider;


import client.BotClient;
import client.Character;
import client.inventory.Equip;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.WeaponType;
import config.YamlConfig;
import constants.inventory.ItemConstants;
import server.ItemInformationProvider;
import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.capabilities.dialogue.AgentDialogueSelector;
import server.agents.capabilities.inventory.AgentInventoryItemPolicy;
import server.agents.integration.AgentOfferRuntime;
import server.agents.integration.AgentOfferStateRuntime;
import server.agents.runtime.AgentPendingActionStateRuntime;
import server.agents.runtime.AgentReplyChannelStateRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.AgentSessionLifecycleSideEffects;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.capabilities.equipment.AgentEquipmentService;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class AgentOfferService {
    private static final Pattern POSITIVE_CONFIRM_PATTERN = Pattern.compile(
            "\\b(yes|yep|yeah|yea|y|ok|sure|confirm|do\\s+it|go\\s+(ahead|for\\s+it))\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NEGATIVE_CONFIRM_PATTERN = Pattern.compile(
            "\\b(no|nope|nah|nvm|never\\s*mind|dont|don't|not\\s+now|skip)\\b",
            Pattern.CASE_INSENSITIVE);

    private enum GearOfferNeed {
        CURRENT,
        FUTURE
    }

    private record GearOfferChoice(Item item, GearOfferNeed need) {}

    private AgentOfferService() {}

    public static boolean hasOfferReservation(AgentRuntimeEntry entry) {
        return AgentOfferStateRuntime.hasOfferReservation(entry);
    }

    public static boolean hasPendingOffer(AgentRuntimeEntry entry) {
        return AgentOfferStateRuntime.hasPendingOffer(entry);
    }

    public static void notifyOwnerGainedEquip(AgentRuntimeEntry entry, Character bot, Item item) {
        if (AgentOfferRuntime.isOwnerIdleForOffer(entry)) {
            return;
        }
        if (AgentOfferStateRuntime.hasRequestedUpgradeItem(entry, item.getItemId())) {
            return;
        }
        if (AgentPendingActionStateRuntime.hasPendingAction(entry) || AgentPendingTradeStateRuntime.hasActiveSequence(entry) || hasOfferReservation(entry)) {
            return;
        }
        Character owner = AgentRuntimeIdentityRuntime.owner(entry);
        if (owner == null) {
            return;
        }

        if (AgentEquipmentService.findRecommendationForItem(bot, owner, item) == null) {
            return;
        }

        AgentOfferStateRuntime.rememberRequestedUpgradeItem(entry, item.getItemId());
        createOwnerUpgradeRequest(entry, bot, owner, item);
    }

    public static void requestBestUpgradeFromOwner(AgentRuntimeEntry entry, Character bot) {
        Character owner = AgentRuntimeIdentityRuntime.owner(entry);
        if (owner == null) {
            return;
        }
        if (AgentPendingActionStateRuntime.hasPendingAction(entry) || AgentPendingTradeStateRuntime.hasActiveSequence(entry) || hasOfferReservation(entry)) {
            AgentOfferRuntime.replyNow(entry, AgentDialogueCatalog.offerBusyReply());
            return;
        }
        List<AgentEquipRecommendation> recs = AgentEquipmentService.findRecommendedEquips(bot, owner);
        if (recs.isEmpty()) {
            AgentOfferRuntime.replyNow(entry, AgentDialogueCatalog.offerNoUpgradeNeededReply());
            return;
        }
        Item candidate = recs.get(0).candidate();
        AgentOfferStateRuntime.rememberRequestedUpgradeItem(entry, candidate.getItemId());
        createOwnerUpgradeRequest(entry, bot, owner, candidate);
    }

    public static boolean offerBestRecommendedGear(AgentRuntimeEntry entry, Character bot, Character owner) {
        if (owner == null) {
            return false;
        }

        // Self-equip first so any item that would upgrade the bot stays on the bot
        // rather than being offered to the owner.
        AgentEquipmentService.autoEquip(bot, owner, AgentOfferStateRuntime.pendingLootOfferItem(entry));

        GearOfferChoice choice = findBestGearOffer(entry, owner, bot);
        if (choice != null) {
            return offerGearItem(entry, bot, owner, choice.item(), choice.need());
        }

        Item throwingStar = findBestThrowingStarOffer(owner, bot);
        return throwingStar != null && offerGearItem(entry, bot, owner, throwingStar, GearOfferNeed.CURRENT);
    }

    public static boolean offerBestGearToSibling(AgentRuntimeEntry entry, Character bot) {
        Character owner = AgentRuntimeIdentityRuntime.owner(entry);
        if (owner == null) {
            return false;
        }

        // Self-equip first: priority is self → owner → sibling, so don't hand gear
        // to a sibling if this bot could actually wear it.
        AgentEquipmentService.autoEquip(bot, owner, AgentOfferStateRuntime.pendingLootOfferItem(entry));

        List<? extends AgentRuntimeEntry> siblings = AgentSessionLifecycleSideEffects.getBotEntries(owner.getId());
        for (AgentRuntimeEntry sibling : siblings) {
            if (sibling == entry) {
                continue;
            }
            Character siblingBot = AgentRuntimeIdentityRuntime.bot(sibling);
            if (siblingBot == null || siblingBot.getMapId() != bot.getMapId()) {
                continue;
            }
            GearOfferChoice choice = findBestGearOffer(entry, siblingBot, bot);
            if (choice != null) {
                return offerGearItem(entry, bot, siblingBot, choice.item(), choice.need());
            }
        }

        Character starRecipient = findWeakestThrowingStarRecipient(owner, bot);
        if (starRecipient == null) {
            return false;
        }
        Item throwingStar = findBestThrowingStarOffer(starRecipient, bot);
        return throwingStar != null
                && offerGearItem(entry, bot, starRecipient, throwingStar, GearOfferNeed.CURRENT);
    }

    public static void scheduleLootOfferPrompt(AgentRuntimeEntry entry, Character bot, Item item, long delayMs) {
        Character owner = AgentRuntimeIdentityRuntime.owner(entry);
        long now = System.currentTimeMillis();
        if (owner == null
                || item == null
                || AgentOfferStateRuntime.hasPendingGearPromptAfter(entry, now)
                || AgentOfferRuntime.isOwnerIdleForOffer(entry)
                || AgentPendingActionStateRuntime.hasPendingAction(entry)
                || AgentPendingTradeStateRuntime.hasActiveSequence(entry)
                || hasOfferReservation(entry)
                || !AgentInventoryItemPolicy.hasItem(bot, item)) {
            return;
        }

        Character recipient = findLootOfferRecipient(entry, bot, item);
        if (recipient == null) {
            return;
        }

        AgentPendingActionStateRuntime.clearPendingDropCategory(entry);
        AgentOfferStateRuntime.setPendingLootOffer(entry, item, recipient.getId(), 0L, false);

        long scheduledAt = now + Math.max(0L, delayMs);
        AgentOfferStateRuntime.reserveGearPrompt(entry, scheduledAt);
        AgentOfferRuntime.afterDelay(delayMs, () -> promptLootOfferAfterLoot(entry, bot, item, recipient.getId(), scheduledAt));
    }

    public static boolean handlePendingOfferResponse(AgentRuntimeEntry entry, Character speaker, String message) {
        expirePendingOffer(entry);
        if (!hasPendingOffer(entry)
                || speaker == null
                || !AgentOfferStateRuntime.pendingOfferRecipientIs(entry, speaker)) {
            return false;
        }

        if (POSITIVE_CONFIRM_PATTERN.matcher(message).find()) {
            if (AgentOfferStateRuntime.pendingLootOfferBotRequesting(entry)) {
                clearPendingOffer(entry);
                AgentOfferRuntime.afterRandomDelay(400, 600, () ->
                        AgentOfferRuntime.replyNow(entry, AgentDialogueCatalog.offerOwnerRequestingTradeReply()));
            } else {
                Item item = AgentOfferStateRuntime.pendingLootOfferItem(entry);
                AgentPendingActionStateRuntime.clearPendingDropCategory(entry);
                AgentOfferStateRuntime.clearPendingOfferForAcceptedTransfer(entry);
                AgentOfferRuntime.afterRandomDelay(900, 1100, () -> {
                    AgentOfferStateRuntime.clearPendingOfferItem(entry);
                    AgentInventoryTransferService.startTradeTransfer(
                            item, speaker, entry, AgentRuntimeIdentityRuntime.bot(entry));
                });
            }
            return true;
        }
        if (NEGATIVE_CONFIRM_PATTERN.matcher(message).find()) {
            clearPendingOffer(entry);
            AgentOfferRuntime.afterRandomDelay(400, 600, () -> {
                Character owner = AgentRuntimeIdentityRuntime.owner(entry);
                if (owner != null && speaker.getId() == owner.getId()) {
                    AgentOfferRuntime.replyNow(entry, AgentDialogueCatalog.offerKeepItemReply());
                } else {
                    AgentOfferRuntime.sayMapNow(AgentRuntimeIdentityRuntime.bot(entry), AgentDialogueCatalog.offerKeepItemReply());
                }
            });
            return true;
        }

        return false;
    }

    public static void expirePendingOffer(AgentRuntimeEntry entry) {
        if (AgentOfferStateRuntime.pendingOfferExpired(entry, System.currentTimeMillis())) {
            clearPendingOffer(entry);
        }
    }

    public static void clearPendingOfferForOwnerAsk(AgentRuntimeEntry entry) {
        clearPendingOffer(entry);
    }

    private static void createOwnerUpgradeRequest(AgentRuntimeEntry entry, Character bot, Character owner, Item ownerItem) {
        // Audience for the specifier is the bot itself: it's describing why the item
        // is good for it, so format stats relative to the bot's job.
        String itemDesc = formatItemSpecifier(ownerItem, bot);

        AgentPendingActionStateRuntime.clearPendingDropCategory(entry);
        AgentOfferStateRuntime.setPendingLootOffer(entry, ownerItem, owner.getId(), System.currentTimeMillis() + 45_000L, true);

        String promptTemplate = AgentDialogueSelector.randomReply(AgentDialogueCatalog.ownerUpgradeRequestPromptTemplates());
        AgentOfferRuntime.queueSay(entry, AgentDialogueCatalog.formatOwnerUpgradeRequestPrompt(promptTemplate, itemDesc));
    }

    private static boolean offerGearItem(AgentRuntimeEntry entry, Character bot, Character recipient, Item item,
                                         GearOfferNeed need) {
        if (AgentPendingActionStateRuntime.hasPendingAction(entry) || AgentPendingTradeStateRuntime.hasActiveSequence(entry) || hasOfferReservation(entry)
                || !AgentInventoryItemPolicy.hasItem(bot, item)) {
            return false;
        }
        AgentPendingActionStateRuntime.clearPendingDropCategory(entry);
        AgentOfferStateRuntime.setPendingLootOffer(entry, item, recipient.getId(), System.currentTimeMillis() + 30_000L, false);
        long promptDelayMs = AgentOfferRuntime.queueSayWithEstimatedDelay(entry,
                buildLootOfferPrompt(recipient, AgentRuntimeIdentityRuntime.owner(entry), item, need == GearOfferNeed.FUTURE));
        scheduleBotLootOfferAutoAccept(entry, recipient, promptDelayMs);
        return true;
    }

    private static void promptLootOfferAfterLoot(AgentRuntimeEntry entry, Character bot, Item item, int recipientId, long scheduledAt) {
        if (!AgentOfferStateRuntime.isReservedGearPrompt(entry, scheduledAt)) {
            return;
        }
        AgentOfferStateRuntime.clearGearPrompt(entry);

        if (!AgentOfferStateRuntime.pendingOfferMatches(entry, item, recipientId)) {
            clearPendingOffer(entry);
            return;
        }

        Character owner = AgentRuntimeIdentityRuntime.owner(entry);
        Character recipient = resolveReservedOfferRecipient(entry, bot, recipientId);
        if (owner == null
                || AgentPendingActionStateRuntime.hasPendingAction(entry)
                || AgentPendingTradeStateRuntime.hasActiveSequence(entry)
                || recipient == null
                || !AgentInventoryItemPolicy.hasItem(bot, item)) {
            clearPendingOffer(entry);
            return;
        }

        if (ItemConstants.getInventoryType(item.getItemId()) == InventoryType.EQUIP
                && AgentEquipmentService.shouldReserveOwnedItem(bot, item)) {
            clearPendingOffer(entry);
            return;
        }
        GearOfferNeed need = gearOfferNeed(entry, recipient, bot, item);
        if (ItemConstants.getInventoryType(item.getItemId()) == InventoryType.EQUIP && need == null) {
            clearPendingOffer(entry);
            return;
        }
        AgentPendingActionStateRuntime.clearPendingDropCategory(entry);
        AgentOfferStateRuntime.setPendingLootOffer(entry, item, recipient.getId(), System.currentTimeMillis() + 30_000L, false);
        long promptDelayMs = AgentOfferRuntime.queueSayWithEstimatedDelay(entry,
                buildLootOfferPrompt(recipient, owner, item, need == GearOfferNeed.FUTURE));
        scheduleBotLootOfferAutoAccept(entry, recipient, promptDelayMs);
    }

    private static void scheduleBotLootOfferAutoAccept(AgentRuntimeEntry entry, Character recipient, long promptDelayMs) {
        if (!(recipient.getClient() instanceof BotClient)) {
            return;
        }
        long replyDelayMs = promptDelayMs + AgentOfferRuntime.randomDelayMs(1800, 2200);
        AgentOfferRuntime.afterDelay(replyDelayMs, () -> autoAcceptLootOffer(entry, recipient));
    }

    private static void autoAcceptLootOffer(AgentRuntimeEntry entry, Character recipientBot) {
        if (!hasPendingOffer(entry) || !AgentOfferStateRuntime.pendingOfferRecipientIs(entry, recipientBot)) {
            return;
        }
        AgentOfferRuntime.sayNow(recipientBot,
                AgentReplyChannelStateRuntime.replyChannel(entry),
                AgentDialogueSelector.randomReply(AgentDialogueCatalog.offerAcceptReplies()));
        handlePendingOfferResponse(entry, recipientBot, "yes");
    }

    public static String buildLootOfferPrompt(String recipientName, String itemName, boolean targetIsOwner) {
        return buildSharedLootOfferPrompt(recipientName, itemName, false);
    }

    public static String buildLootOfferPrompt(String recipientName, String itemName, boolean targetIsOwner, boolean forLater) {
        return buildSharedLootOfferPrompt(recipientName, itemName, forLater);
    }

    private static String buildSharedLootOfferPrompt(String recipientName, String itemName, boolean forLater) {
        String format = AgentDialogueSelector.randomReply(AgentDialogueCatalog.lootOfferPromptTemplates(forLater));
        return AgentDialogueCatalog.formatLootOfferPrompt(format, recipientName, itemName);
    }

    private static String buildLootOfferPrompt(Character recipient, Character owner, Item item, boolean forLater) {
        String itemDesc = formatItemSpecifier(item, recipient);
        return buildSharedLootOfferPrompt(recipient.getName(), itemDesc, forLater);
    }

    /**
     * Returns "<spec> <itemName>" with up to 2 stat tokens in priority order:
     * 1) att (or matt for mage audience), 2) main stat, 3) secondary stat.
     * Tokens with value 0 are skipped — att/matt is NOT gated by slot type, since
     * gloves/capes/earrings can also carry att or matt. Audience's job decides
     * which stats are "main"/"secondary" and whether matt outranks att.
     * Weapon att is rendered without a leading "+" ("30 att maple bow"); all
     * other tokens use "+" since they are bonus values ("+3 str", "+3 att").
     */
    static String formatItemSpecifier(Item item, Character audience) {
        String name = ItemInformationProvider.getInstance().getName(item.getItemId());
        if (name == null || name.isBlank()) {
            name = String.valueOf(item.getItemId());
        }
        if (!(item instanceof Equip eq) || audience == null) {
            return name;
        }

        int jobId = audience.getJob() == null ? 0 : audience.getJob().getId();
        boolean mageBranch = isMageBranch(jobId);
        boolean weapon = ItemConstants.isWeapon(item.getItemId());
        char[] order = mainSecondaryStats(jobId);

        int attVal = mageBranch ? eq.getMatk() : eq.getWatk();
        String attLabel = mageBranch ? "matt" : "att";
        int mainVal = statValue(eq, order[0]);
        int secVal = statValue(eq, order[1]);

        List<String> tokens = new ArrayList<>(2);
        if (attVal > 0) {
            tokens.add(weapon ? (attVal + " " + attLabel) : ("+" + attVal + " " + attLabel));
        }
        if (tokens.size() < 2 && mainVal > 0) {
            tokens.add("+" + mainVal + " " + statName(order[0]));
        }
        if (tokens.size() < 2 && secVal > 0) {
            tokens.add("+" + secVal + " " + statName(order[1]));
        }

        if (tokens.isEmpty()) {
            return name;
        }
        return String.join(" ", tokens) + " " + name;
    }

    private static boolean isMageBranch(int jobId) {
        return (jobId >= 200 && jobId < 300)
                || (jobId >= 1200 && jobId < 1300)
                || jobId == 2001
                || (jobId >= 2200 && jobId < 2300);
    }

    // Returns 2-char [main, secondary] stat codes: s=str, d=dex, i=int, l=luk
    private static char[] mainSecondaryStats(int jobId) {
        // Magician branches: INT main, LUK secondary
        if (isMageBranch(jobId)) return new char[]{'i', 'l'};
        // Bowman / Wind Archer: DEX main, STR secondary
        if ((jobId >= 300 && jobId < 400) || (jobId >= 1300 && jobId < 1400)) return new char[]{'d', 's'};
        // Thief / Night Walker: LUK main, DEX secondary
        if ((jobId >= 400 && jobId < 500) || (jobId >= 1400 && jobId < 1500)) return new char[]{'l', 'd'};
        // Pirate gunslinger sub-branch: DEX main, STR secondary
        if (jobId >= 520 && jobId < 530) return new char[]{'d', 's'};
        // Pirate brawler sub-branch + Thunderbreaker: STR main, DEX secondary
        if ((jobId >= 510 && jobId < 520) || (jobId >= 1500 && jobId < 1600)) return new char[]{'s', 'd'};
        // Warrior / Dawn Warrior / Aran / Pirate-beginner / fallback: STR main, DEX secondary
        return new char[]{'s', 'd'};
    }

    private static int statValue(Equip eq, char code) {
        return switch (code) {
            case 's' -> eq.getStr();
            case 'd' -> eq.getDex();
            case 'i' -> eq.getInt();
            case 'l' -> eq.getLuk();
            default -> 0;
        };
    }

    private static String statName(char code) {
        return switch (code) {
            case 's' -> "str";
            case 'd' -> "dex";
            case 'i' -> "int";
            case 'l' -> "luk";
            default -> "";
        };
    }

    private static Character findLootOfferRecipient(AgentRuntimeEntry entry, Character bot, Item item) {
        Character owner = AgentRuntimeIdentityRuntime.owner(entry);
        if (owner == null) {
            return null;
        }
        if (ItemConstants.isThrowingStar(item.getItemId())) {
            if (isBetterThrowingStarForRecipient(owner, bot, item)) {
                return owner;
            }
            return findWeakestThrowingStarRecipient(owner, bot, item);
        }

        if (AgentEquipmentService.shouldReserveOwnedItem(bot, item)) {
            return null;
        }

        if (gearOfferNeed(entry, owner, bot, item) != null) {
            return owner;
        }
        for (Character member : eligibleBotRecipients(owner, bot)) {
            if (gearOfferNeed(entry, member, bot, item) != null) {
                return member;
            }
        }
        return null;
    }

    private static GearOfferChoice findBestGearOffer(AgentRuntimeEntry entry, Character recipient, Character donor) {
        List<Equip> offerable = collectOfferableEquips(donor);
        offerable.removeIf(equip -> !isWeaponOfferCompatible(recipient, equip));
        List<AgentEquipRecommendation> current =
                AgentEquipmentService.findRecommendedEquipsFromItems(recipient, offerable);
        if (!current.isEmpty()) {
            return new GearOfferChoice(current.get(0).candidate(), GearOfferNeed.CURRENT);
        }
        if (AgentOfferStateRuntime.proactiveUpgradeOffers(entry)) {
            ItemInformationProvider ii = ItemInformationProvider.getInstance();
            for (Equip equip : offerable) {
                if (AgentEquipmentService.wouldReserveIncomingItem(recipient, ii, equip)) {
                    return new GearOfferChoice(equip, GearOfferNeed.FUTURE);
                }
            }
        }
        return null;
    }

    private static List<Equip> collectOfferableEquips(Character donor) {
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        Inventory equipInv = donor.getInventory(InventoryType.EQUIP);
        List<Equip> offerable = new ArrayList<>();
        for (Item item : equipInv.list()) {
            if (!(item instanceof Equip equip) || ii.isCash(item.getItemId())) {
                continue;
            }
            if (item.isUntradeable() && !YamlConfig.config.server.UNTRADEABLE_ITEMS_TRADEABLE) {
                continue;
            }
            if (AgentEquipmentService.shouldReserveOwnedItem(donor, item)) {
                continue;
            }
            offerable.add(equip);
        }
        return offerable;
    }
    private static GearOfferNeed gearOfferNeed(AgentRuntimeEntry entry, Character recipient, Character donor, Item item) {
        if (!isWeaponOfferCompatible(recipient, item)) {
            return null;
        }
        if (AgentEquipmentService.findRecommendationForItem(recipient, donor, item) != null) {
            return GearOfferNeed.CURRENT;
        }
        if (AgentOfferStateRuntime.proactiveUpgradeOffers(entry) && item instanceof Equip equip) {
            if (AgentEquipmentService.wouldReserveIncomingItem(recipient, ItemInformationProvider.getInstance(), equip)) {
                return GearOfferNeed.FUTURE;
            }
        }
        return null;
    }

    public static boolean isWeaponOfferCompatible(Character recipient, Item item) {
        if (!(item instanceof Equip equip)) {
            return true;
        }
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        if (!ItemConstants.isWeapon(equip.getItemId())) {
            return true;
        }
        return isWeaponOfferCompatible(recipient, ii.getWeaponType(equip.getItemId()));
    }

    public static boolean isWeaponOfferCompatible(Character recipient, WeaponType weaponType) {
        return AgentEquipmentService.isWeaponCompatible(recipient, weaponType);
    }

    public static boolean isReservedForOtherRecipients(AgentRuntimeEntry entry, Character donor, Item item) {
        if (entry == null || donor == null || item == null) {
            return false;
        }

        Character owner = AgentRuntimeIdentityRuntime.owner(entry);
        if (owner == null) {
            return false;
        }

        if (ItemConstants.isThrowingStar(item.getItemId())) {
            if (isBetterThrowingStarForRecipient(owner, donor, item)) {
                return true;
            }
            for (Character member : eligibleBotRecipients(owner, donor)) {
                if (isBetterThrowingStarForRecipient(member, donor, item)) {
                    return true;
                }
            }
            return false;
        }

        if (ItemConstants.getInventoryType(item.getItemId()) != InventoryType.EQUIP) {
            return false;
        }
        if (!(item instanceof Equip equip)) {
            return false;
        }
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        // Trade-classification path: only the FUTURE (Pareto self-reserve) check is used here.
        // The IMMEDIATE optimizer-DP check that gearOfferNeed() also runs is intentionally
        // skipped — it's expensive and its picks are essentially a subset of the FUTURE set,
        // so it adds no signal for "should this item be held back from a player→bot trade?".
        // Proactive offer paths still call gearOfferNeed() directly and keep both checks.
        if (isFutureReservedForRecipient(owner, equip, ii)) {
            return true;
        }
        for (Character member : eligibleBotRecipients(owner, donor)) {
            if (isFutureReservedForRecipient(member, equip, ii)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFutureReservedForRecipient(Character recipient, Equip equip, ItemInformationProvider ii) {
        if (!isWeaponOfferCompatible(recipient, equip)) {
            return false;
        }
        return AgentEquipmentService.wouldReserveIncomingItem(recipient, ii, equip);
    }

    private static Character findWeakestThrowingStarRecipient(Character owner, Character donor) {
        Character bestRecipient = null;
        int bestCurrentWatk = Integer.MAX_VALUE;
        for (Character member : eligibleBotRecipients(owner, donor)) {
            Item candidate = findBestThrowingStarOffer(member, donor);
            if (candidate == null) {
                continue;
            }
            int currentWatk = bestThrowingStarAttack(member);
            if (currentWatk < bestCurrentWatk) {
                bestRecipient = member;
                bestCurrentWatk = currentWatk;
            }
        }
        return bestRecipient;
    }

    private static Character findWeakestThrowingStarRecipient(Character owner, Character donor, Item item) {
        Character bestRecipient = null;
        int bestCurrentWatk = Integer.MAX_VALUE;
        for (Character member : eligibleBotRecipients(owner, donor)) {
            if (!isBetterThrowingStarForRecipient(member, donor, item)) {
                continue;
            }
            int currentWatk = bestThrowingStarAttack(member);
            if (currentWatk < bestCurrentWatk) {
                bestRecipient = member;
                bestCurrentWatk = currentWatk;
            }
        }
        return bestRecipient;
    }

    private static List<Character> eligibleBotRecipients(Character owner, Character donor) {
        AgentOwnershipService ownership = AgentOwnershipService.getInstance();
        return owner.getPartyMembersOnSameMap().stream()
                .filter(member -> member != null)
                .filter(member -> member.getId() != owner.getId())
                .filter(member -> member.getId() != donor.getId())
                .filter(member -> member.getClient() instanceof BotClient)
                .filter(member -> ownership.isAuthorizedOwner(member.getId(), owner.getId()))
                .toList();
    }

    private static Item findBestThrowingStarOffer(Character recipient, Character donor) {
        Inventory useInv = donor.getInventory(InventoryType.USE);
        Item best = null;
        int bestWatk = 0;
        for (Item item : useInv.list()) {
            if (!isBetterThrowingStarForRecipient(recipient, donor, item)) {
                continue;
            }
            int watk = throwingStarAttack(item);
            if (watk > bestWatk) {
                best = item;
                bestWatk = watk;
            }
        }
        return best;
    }

    static boolean isBetterThrowingStarForRecipient(Character recipient, Character donor, Item candidate) {
        if (candidate == null || !ItemConstants.isThrowingStar(candidate.getItemId())) {
            return false;
        }
        if (AgentAttackExecutionProvider.getEquippedWeaponType(recipient) != WeaponType.CLAW) {
            return false;
        }
        int candidateWatk = throwingStarAttack(candidate);
        if (candidateWatk < bestThrowingStarAttack(recipient)) {
            return false;
        }
        return AgentAttackExecutionProvider.getEquippedWeaponType(donor) != WeaponType.CLAW
                || candidateWatk < bestThrowingStarAttack(donor);
    }

    private static int bestThrowingStarAttack(Character character) {
        Inventory useInv = character.getInventory(InventoryType.USE);
        int best = 0;
        for (Item item : useInv.list()) {
            if (ItemConstants.isThrowingStar(item.getItemId())) {
                best = Math.max(best, throwingStarAttack(item));
            }
        }
        return best;
    }

    private static int throwingStarAttack(Item item) {
        return ItemInformationProvider.getInstance().getWatkForProjectile(item.getItemId());
    }

    private static Character resolveReservedOfferRecipient(AgentRuntimeEntry entry, Character bot, int recipientId) {
        Character owner = AgentRuntimeIdentityRuntime.owner(entry);
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

    private static void clearPendingOffer(AgentRuntimeEntry entry) {
        AgentPendingActionStateRuntime.clearPendingDropCategory(entry);
        AgentOfferStateRuntime.clearPendingOffer(entry);
        AgentOfferStateRuntime.clearGearPrompt(entry);
    }
}
