package server.agents.capabilities.build;

import server.agents.plans.AgentScriptTaskStateRuntime;

import client.Character;
import client.inventory.Equip;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import server.agents.capabilities.inventory.AgentInventorySellTrashService;
import server.agents.capabilities.build.AgentMakerRuntime;
import server.agents.integration.AgentMakerGatewayRuntime;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.InventoryGateway;
import server.agents.integration.MakerGateway;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;

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
 * etc stacks) and "disassemble trash" (break down trash equips). Both run through the live
 * maker gateway player path, one operation per {@link #STEP_INTERVAL_MS} ms, and
 * self-interrupt when the player issues a new directive (follow/stop/move/...): see
 * {@link AgentScriptTaskStateRuntime#activityEpoch(AgentRuntimeEntry)}.
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
     *  when nothing remains, otherwise a live maker failure status. */
    @FunctionalInterface
    private interface BatchStep {
        int run(Character agent);
    }

    private AgentMakerService() {
    }

    public static void handleMakeCrystals(AgentRuntimeEntry entry, InventoryGateway inventory) {
        handleMakeCrystals(entry, inventory, AgentMakerGatewayRuntime.maker());
    }

    public static void handleMakeCrystals(AgentRuntimeEntry entry, InventoryGateway inventory, MakerGateway maker) {
        Character bot = AgentRuntimeIdentityRuntime.bot(entry);
        if (bot == null || !guardStart(entry, bot, maker)) {
            return;
        }

        Queue<Integer> leftovers = collectLeftoverQueue(bot, inventory);   // one entry per craft, holding the leftover itemid
        if (leftovers.isEmpty()) {
            AgentMakerRuntime.replyNow(entry,
                    "I don't have any leftovers I can turn into monster crystals (need 100+ of a drop)");
            return;
        }

        startBatch(entry, leftovers.size(), CRYSTAL_VERBS, "crystal",
                agent -> {
                    Integer leftover = leftovers.poll();
                    return leftover == null ? NO_MORE : maker.makeLeftoverCrystal(agent, leftover);
                });
    }

    public static void handleDisassembleTrash(AgentRuntimeEntry entry, InventoryGateway inventory) {
        handleDisassembleTrash(entry, inventory, AgentMakerGatewayRuntime.maker());
    }

    public static void handleDisassembleTrash(AgentRuntimeEntry entry, InventoryGateway inventory, MakerGateway maker) {
        Character bot = AgentRuntimeIdentityRuntime.bot(entry);
        if (bot == null || !guardStart(entry, bot, maker)) {
            return;
        }

        int total = collectDisassemblableTrash(entry, bot, inventory, maker).size();
        if (total == 0) {
            AgentMakerRuntime.replyNow(entry, "no trash equips I can disassemble");
            return;
        }

        // Re-scan each step rather than caching slots: a freed EQUIP slot can be refilled by
        // looted gear during the 5s gaps, so always disassemble a currently-trash equip.
        startBatch(entry, total, DISASSEMBLE_VERBS, "trash equip",
                agent -> {
                    List<Equip> trash = collectDisassemblableTrash(entry, bot, inventory, maker);
                    return trash.isEmpty() ? NO_MORE : maker.disassembleEquip(agent, trash.get(0).getPosition());
                });
    }

    /** Trash equips (SSOT: {@link AgentInventorySellTrashService#collectSellTrashEquips}) that actually
     *  have a Maker disassembly recipe — others would just abort the batch. */
    private static List<Equip> collectDisassemblableTrash(AgentRuntimeEntry entry, Character bot,
                                                          InventoryGateway inventory, MakerGateway maker) {
        List<Equip> out = new ArrayList<>();
        for (Item item : AgentInventorySellTrashService.collectSellTrashEquips(entry, bot, inventory)) {
            if (item instanceof Equip equip && maker.canDisassemble(equip.getItemId())) {
                out.add(equip);
            }
        }
        return out;
    }

    private static boolean guardStart(AgentRuntimeEntry entry, Character bot, MakerGateway maker) {
        if (ACTIVE.contains(bot.getId())) {
            AgentMakerRuntime.replyNow(entry, "still working on the last batch, hang on");
            return false;
        }
        if (maker.getMakerSkillLevel(bot) < 1) {
            AgentMakerRuntime.replyNow(entry, "I can't - I don't have the Maker skill");
            return false;
        }
        return true;
    }

    private static Queue<Integer> collectLeftoverQueue(Character bot, InventoryGateway inventory) {
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
                if (crafts <= 0 || inventory.getMakerCrystalFromLeftover(itemId) == -1) {
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

    private static void startBatch(AgentRuntimeEntry entry, int total, String[] verbs, String noun, BatchStep step) {
        Character bot = AgentRuntimeIdentityRuntime.bot(entry);
        String verb = verbs[ThreadLocalRandom.current().nextInt(verbs.length)];
        String msg = "ok " + verb + " " + total + " " + plural(noun, total);
        if (total > LONG_BATCH_THRESHOLD) {
            msg += ", will take a while";
        }
        AgentMakerRuntime.replyNow(entry, msg);

        ACTIVE.add(bot.getId());
        int epoch = AgentScriptTaskStateRuntime.activityEpoch(entry);
        AgentMakerRuntime.afterRandomDelay(900, 1100, () -> runStep(entry, step, noun, epoch, 0));
    }

    private static void runStep(AgentRuntimeEntry entry, BatchStep step, String noun, int epoch, int done) {
        Character bot = AgentRuntimeIdentityRuntime.bot(entry);
        if (bot == null || !bot.isLoggedin()) {
            if (bot != null) {
                ACTIVE.remove(bot.getId());
            }
            return;
        }

        if (!AgentScriptTaskStateRuntime.isCurrentActivityEpoch(entry, epoch)) {   // player issued a new command — disrupt
            ACTIVE.remove(bot.getId());
            AgentMakerRuntime.replyNow(entry, "ok, stopping - " + done + " " + plural(noun, done) + " done");
            return;
        }

        if (!AgentClientGatewayRuntime.clients().hasClient(bot)) {
            ACTIVE.remove(bot.getId());
            return;
        }
        if (!AgentClientGatewayRuntime.clients().tryAcquire(bot)) {
            // transient contention (a trade/packet in flight) — retry without consuming the step.
            // The bot keeps doing whatever it's doing; crafting just slots into the next free moment.
            AgentMakerRuntime.afterRandomDelay(600, 900, () -> runStep(entry, step, noun, epoch, done));
            return;
        }
        int status;
        try {
            status = step.run(bot);
        } finally {
            AgentClientGatewayRuntime.clients().release(bot);
        }

        if (status == NO_MORE) {
            ACTIVE.remove(bot.getId());
            AgentMakerRuntime.replyNow(entry, "done - " + done + " " + plural(noun, done));
            return;
        }
        if (status != 0) {
            ACTIVE.remove(bot.getId());
            AgentMakerRuntime.replyNow(entry, abortReason((short) status, noun, done));
            return;
        }

        AgentMakerRuntime.afterDelay(STEP_INTERVAL_MS, () -> runStep(entry, step, noun, epoch, done + 1));
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

}
