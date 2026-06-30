package server.agents.capabilities.build;

import client.Character;
import client.Client;
import client.inventory.Equip;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.processor.action.MakerProcessor;
import server.ItemInformationProvider;
import server.agents.capabilities.inventory.AgentInventorySellTrashService;
import server.agents.integration.AgentBotMakerRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.agents.integration.AgentBotScriptTaskStateRuntime;
import server.bots.BotEntry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles the bot Maker batch commands: "make monster crystals" (convert monster-leftover
 * etc stacks) and "disassemble trash" (break down trash equips). Both run through the shared
 * {@link MakerProcessor} player path, one operation per {@link #STEP_INTERVAL_MS} ms, and
 * self-interrupt when the player issues a new directive (follow/stop/move/...): see
 * {@link AgentBotScriptTaskStateRuntime#activityEpoch(BotEntry)}.
 */
public final class AgentMakerService {
    private static final int LEFTOVERS_PER_CRYSTAL = 100;   // Maker type-3 recipe req count
    private static final long STEP_INTERVAL_MS = 5000L;     // 5 seconds per operation
    private static final int LONG_BATCH_THRESHOLD = 10;     // "will take a while" past this many ops
    private static final int NO_MORE = Integer.MIN_VALUE;   // batch step sentinel: nothing left to do
    private static final Set<Integer> ACTIVE = ConcurrentHashMap.newKeySet();

    private static final String[] CRYSTAL_VERBS = {"creating", "making", "crafting"};
    private static final String[] DISASSEMBLE_VERBS = {"disassembling", "breaking down", "scrapping"};

    /** One Maker operation under a held client lock. Returns 0 on success, {@link #NO_MORE}
     *  when nothing remains, otherwise a {@link MakerProcessor} failure status. */
    @FunctionalInterface
    private interface BatchStep {
        int run(Client c);
    }

    private AgentMakerService() {
    }

    public static void handleMakeCrystals(BotEntry entry) {
        Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
        if (bot == null || !guardStart(entry, bot)) {
            return;
        }

        Queue<Integer> leftovers = collectLeftoverQueue(bot);   // one entry per craft, holding the leftover itemid
        if (leftovers.isEmpty()) {
            AgentBotMakerRuntime.replyNow(entry,
                    "I don't have any leftovers I can turn into monster crystals (need 100+ of a drop)");
            return;
        }

        startBatch(entry, leftovers.size(), CRYSTAL_VERBS, "crystal",
                c -> {
                    Integer leftover = leftovers.poll();
                    return leftover == null ? NO_MORE : MakerProcessor.makeLeftoverCrystal(c, leftover);
                });
    }

    public static void handleDisassembleTrash(BotEntry entry) {
        Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
        if (bot == null || !guardStart(entry, bot)) {
            return;
        }

        int total = collectDisassemblableTrash(entry, bot).size();
        if (total == 0) {
            AgentBotMakerRuntime.replyNow(entry, "no trash equips I can disassemble");
            return;
        }

        // Re-scan each step rather than caching slots: a freed EQUIP slot can be refilled by
        // looted gear during the 5s gaps, so always disassemble a currently-trash equip.
        startBatch(entry, total, DISASSEMBLE_VERBS, "trash equip",
                c -> {
                    List<Equip> trash = collectDisassemblableTrash(entry, bot);
                    return trash.isEmpty() ? NO_MORE : MakerProcessor.disassembleEquip(c, trash.get(0).getPosition());
                });
    }

    /** Trash equips (SSOT: {@link AgentInventorySellTrashService#collectSellTrashEquips}) that actually
     *  have a Maker disassembly recipe — others would just abort the batch. */
    private static List<Equip> collectDisassemblableTrash(BotEntry entry, Character bot) {
        List<Equip> out = new ArrayList<>();
        for (Item item : AgentInventorySellTrashService.collectSellTrashEquips(entry, bot)) {
            if (item instanceof Equip equip && MakerProcessor.canDisassemble(equip.getItemId())) {
                out.add(equip);
            }
        }
        return out;
    }

    private static boolean guardStart(BotEntry entry, Character bot) {
        if (ACTIVE.contains(bot.getId())) {
            AgentBotMakerRuntime.replyNow(entry, "still working on the last batch, hang on");
            return false;
        }
        if (MakerProcessor.getMakerSkillLevel(bot) < 1) {
            AgentBotMakerRuntime.replyNow(entry, "I can't - I don't have the Maker skill");
            return false;
        }
        return true;
    }

    private static Queue<Integer> collectLeftoverQueue(Character bot) {
        Queue<Integer> queue = new LinkedList<>();
        Inventory etc = bot.getInventory(InventoryType.ETC);
        etc.lockInventory();
        try {
            Set<Integer> seen = new HashSet<>();
            for (Item item : etc.list()) {
                int itemId = item.getItemId();
                if (!seen.add(itemId)) {
                    continue;   // count each distinct leftover once; countById sums all stacks
                }
                int crafts = etc.countById(itemId) / LEFTOVERS_PER_CRYSTAL;
                if (crafts <= 0 || itemInformation().getMakerCrystalFromLeftover(itemId) == -1) {
                    continue;
                }
                for (int i = 0; i < crafts; i++) {
                    queue.add(itemId);
                }
            }
        } finally {
            etc.unlockInventory();
        }
        return queue;
    }

    private static void startBatch(BotEntry entry, int total, String[] verbs, String noun, BatchStep step) {
        Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
        String verb = verbs[ThreadLocalRandom.current().nextInt(verbs.length)];
        String msg = "ok " + verb + " " + total + " " + plural(noun, total);
        if (total > LONG_BATCH_THRESHOLD) {
            msg += ", will take a while";
        }
        AgentBotMakerRuntime.replyNow(entry, msg);

        ACTIVE.add(bot.getId());
        int epoch = AgentBotScriptTaskStateRuntime.activityEpoch(entry);
        AgentBotMakerRuntime.afterRandomDelay(900, 1100, () -> runStep(entry, step, noun, epoch, 0));
    }

    private static void runStep(BotEntry entry, BatchStep step, String noun, int epoch, int done) {
        Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
        if (bot == null || !bot.isLoggedin()) {
            if (bot != null) {
                ACTIVE.remove(bot.getId());
            }
            return;
        }

        if (!AgentBotScriptTaskStateRuntime.isCurrentActivityEpoch(entry, epoch)) {   // player issued a new command — disrupt
            ACTIVE.remove(bot.getId());
            AgentBotMakerRuntime.replyNow(entry, "ok, stopping - " + done + " " + plural(noun, done) + " done");
            return;
        }

        Client c = bot.getClient();
        if (c == null) {
            ACTIVE.remove(bot.getId());
            return;
        }
        if (!c.tryacquireClient()) {
            // transient contention (a trade/packet in flight) — retry without consuming the step.
            // The bot keeps doing whatever it's doing; crafting just slots into the next free moment.
            AgentBotMakerRuntime.afterRandomDelay(600, 900, () -> runStep(entry, step, noun, epoch, done));
            return;
        }
        int status;
        try {
            status = step.run(c);
        } finally {
            c.releaseClient();
        }

        if (status == NO_MORE) {
            ACTIVE.remove(bot.getId());
            AgentBotMakerRuntime.replyNow(entry, "done - " + done + " " + plural(noun, done));
            return;
        }
        if (status != 0) {
            ACTIVE.remove(bot.getId());
            AgentBotMakerRuntime.replyNow(entry, abortReason((short) status, noun, done));
            return;
        }

        AgentBotMakerRuntime.afterDelay(STEP_INTERVAL_MS, () -> runStep(entry, step, noun, epoch, done + 1));
    }

    private static String abortReason(short status, String noun, int done) {
        String reason = switch (status) {
            case 1 -> "ran out of materials";
            case 2 -> "ran out of mesos";
            case 5 -> "my inventory's full";
            default -> "hit a snag";
        };
        return reason + ", stopping - " + done + " " + plural(noun, done) + " done";
    }

    private static String plural(String noun, int count) {
        return count == 1 ? noun : noun + "s";
    }

    private static ItemInformationProvider itemInformation() {
        return ItemInformationProvider.getInstance();
    }
}
