package server.bots;

import client.Character;
import client.Job;
import client.inventory.Equip;
import client.inventory.WeaponType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Combinatorial-correctness tests for {@link BotEquipManager#solveForWeapon}.
 *
 * Each scenario is constructed so a per-slot greedy solver would fall into a "trap":
 * each non-weapon slot offers a small immediate-damage pick (+WATK 1) and a chain pick
 * (+DEX 5). The +WATK trap is the obvious greedy choice (more immediate damage on the
 * currently-equipped weapon), but only the +DEX chain across many slots accumulates
 * enough DEX to unlock a much stronger weapon. The Pareto-DP must enumerate the full
 * combination, not just per-slot best.
 *
 * Tests stub {@link BotEquipManager.OptimizerHooks} via lambdas — Mockito cannot
 * instrument {@link server.ItemInformationProvider} in unit tests due to its WZ-data
 * static initializer, so the optimizer is decoupled from II behind this small interface.
 */
class BotEquipOptimizerTest {

    private static final int W0_ID = 1100000; // weak bow, no req
    private static final int W1_ID = 1100001; // strong bow, requires DEX 60

    private static final short S_HAT = -1, S_FACE = -2, S_EYE = -3, S_EAR = -4,
                                S_SHOES = -7, S_GLOVE = -8, S_CAPE = -9;
    private static final short[] CHAIN_SLOTS = {S_HAT, S_FACE, S_EYE, S_EAR, S_SHOES, S_GLOVE, S_CAPE};

    @Test
    void dpPicksChainOverTrapToUnlockStrongerWeapon() {
        Character bot = mockBowman();
        Equip w0 = weapon(W0_ID, /*watk*/ 20, /*pos*/ -11);
        Equip w1 = weapon(W1_ID, /*watk*/ 100, /*pos*/ 1);

        Map<Integer, Integer> reqDexByItem = new HashMap<>();
        reqDexByItem.put(W0_ID, 0);
        reqDexByItem.put(W1_ID, 60);

        Map<Short, List<Equip>> bySlot = new LinkedHashMap<>();
        int pos = 2;
        for (short slot : CHAIN_SLOTS) {
            Equip trap = armor(2000 + slot, /*dex*/ 0, /*watk*/ 1, pos++);
            Equip chain = armor(3000 + slot, /*dex*/ 5, /*watk*/ 0, pos++);
            bySlot.put(slot, List.of(trap, chain));
            reqDexByItem.put(trap.getItemId(), 0);
            reqDexByItem.put(chain.getItemId(), 0);
        }

        BotEquipManager.OptimizerHooks hooks = bowHooks(reqDexByItem);
        Map<Short, Equip> currentBySlot = new HashMap<>();
        currentBySlot.put((short) -11, w0);

        BotEquipManager.StatSnapshot naked = new BotEquipManager.StatSnapshot(
                /*str*/ 4, /*dex*/ 30, /*int_*/ 4, /*luk*/ 4,
                /*watk*/ 0, /*magic*/ 0, /*flatAcc*/ 0,
                /*level*/ 50, /*fame*/ 0, Job.BOWMAN);

        BotEquipManager.MapDamageProfile mob =
                new BotEquipManager.MapDamageProfile(/*wdef*/ 50, /*avoid*/ 30, /*level*/ 55);

        BotEquipManager.DpResult resultW1 = BotEquipManager.solveForWeapon(
                bot, hooks, naked, w1, asList(CHAIN_SLOTS), currentBySlot, bySlot, mob);

        assertNotNull(resultW1, "DP should find a feasible chain that unlocks W1");
        // Base DEX 30 + 7 × 5 = 65 ≥ 60 → W1 validates. Any +WATK trap pick would short
        // the DEX budget by 5 and fail W1's req, so the DP must pick chain at every slot.
        for (short slot : CHAIN_SLOTS) {
            Equip pick = resultW1.picks().get(slot);
            assertNotNull(pick, "expected a pick at slot " + slot + " for W1");
            assertEquals((short) 5, pick.getDex(),
                    "slot " + slot + " under W1 must pick the DEX-5 chain item, not the WATK trap");
            assertEquals((short) 0, pick.getWatk(),
                    "slot " + slot + " under W1 must NOT pick the WATK trap");
        }
        assertTrue(resultW1.score().damage() > 0, "W1 chain score should be positive");
    }

    @Test
    void dpScoresW1ChainHigherThanW0Trap() {
        // Side-by-side comparison: solving for W1 with the chain picks must outscore solving
        // for W0 with whatever picks the DP chooses. Validates that the outer weapon-loop
        // would prefer the chain-unlock branch in a real autoEquip call.
        Character bot = mockBowman();
        Equip w0 = weapon(W0_ID, 20, -11);
        Equip w1 = weapon(W1_ID, 100, 1);

        Map<Integer, Integer> reqDexByItem = new HashMap<>();
        reqDexByItem.put(W0_ID, 0);
        reqDexByItem.put(W1_ID, 60);

        Map<Short, List<Equip>> bySlot = new LinkedHashMap<>();
        int pos = 2;
        for (short slot : CHAIN_SLOTS) {
            Equip trap = armor(2000 + slot, 0, /*watk*/ 1, pos++);
            Equip chain = armor(3000 + slot, /*dex*/ 5, 0, pos++);
            bySlot.put(slot, List.of(trap, chain));
            reqDexByItem.put(trap.getItemId(), 0);
            reqDexByItem.put(chain.getItemId(), 0);
        }

        BotEquipManager.OptimizerHooks hooks = bowHooks(reqDexByItem);
        Map<Short, Equip> currentBySlot = new HashMap<>();
        currentBySlot.put((short) -11, w0);

        BotEquipManager.StatSnapshot naked = new BotEquipManager.StatSnapshot(
                4, 30, 4, 4, 0, 0, 0, 50, 0, Job.BOWMAN);
        BotEquipManager.MapDamageProfile mob =
                new BotEquipManager.MapDamageProfile(50, 30, 55);

        BotEquipManager.DpResult resultW0 = BotEquipManager.solveForWeapon(
                bot, hooks, naked, w0, asList(CHAIN_SLOTS), currentBySlot, bySlot, mob);
        BotEquipManager.DpResult resultW1 = BotEquipManager.solveForWeapon(
                bot, hooks, naked, w1, asList(CHAIN_SLOTS), currentBySlot, bySlot, mob);

        assertNotNull(resultW0);
        assertNotNull(resultW1);
        assertTrue(resultW1.score().damage() > resultW0.score().damage(),
                "W1+chain damage must beat W0 best — got W1=" + resultW1.score().damage()
                        + " W0=" + resultW0.score().damage());
    }

    @Test
    void dpRejectsW1WhenChainIsTooShort() {
        // Only 5 chain slots available; chain max DEX = 25. Base 30 + 25 = 55 < 60 → no
        // feasible state for W1 → solveForWeapon returns null. Validates the validator.
        Character bot = mockBowman();
        Equip w1 = weapon(W1_ID, 100, 1);

        Map<Integer, Integer> reqDexByItem = new HashMap<>();
        reqDexByItem.put(W1_ID, 60);

        short[] shortChain = {S_HAT, S_FACE, S_EYE, S_EAR, S_SHOES}; // only 5 slots
        Map<Short, List<Equip>> bySlot = new LinkedHashMap<>();
        int pos = 2;
        for (short slot : shortChain) {
            Equip chain = armor(3000 + slot, /*dex*/ 5, 0, pos++);
            bySlot.put(slot, List.of(chain));
            reqDexByItem.put(chain.getItemId(), 0);
        }

        BotEquipManager.OptimizerHooks hooks = bowHooks(reqDexByItem);
        Map<Short, Equip> currentBySlot = new HashMap<>();

        BotEquipManager.StatSnapshot naked = new BotEquipManager.StatSnapshot(
                4, 30, 4, 4, 0, 0, 0, 50, 0, Job.BOWMAN);

        BotEquipManager.DpResult result = BotEquipManager.solveForWeapon(
                bot, hooks, naked, w1, asList(shortChain), currentBySlot, bySlot, null);

        assertEquals(null, result,
                "expected no feasible state for W1 when chain is too short to clear DEX 60 req");
    }

    // ------------------------------------------------------------------ helpers

    private static List<Short> asList(short[] arr) {
        List<Short> out = new ArrayList<>(arr.length);
        for (short s : arr) out.add(s);
        return out;
    }

    private static Character mockBowman() {
        Character bot = mock(Character.class);
        when(bot.getJob()).thenReturn(Job.BOWMAN);
        when(bot.getLevel()).thenReturn(50);
        when(bot.getFame()).thenReturn(0);
        return bot;
    }

    private static Equip weapon(int id, int watk, int pos) {
        Equip e = mock(Equip.class);
        when(e.getItemId()).thenReturn(id);
        when(e.getWatk()).thenReturn((short) watk);
        when(e.getPosition()).thenReturn((short) pos);
        return e;
    }

    private static Equip armor(int id, int dex, int watk, int pos) {
        Equip e = mock(Equip.class);
        when(e.getItemId()).thenReturn(id);
        when(e.getDex()).thenReturn((short) dex);
        when(e.getWatk()).thenReturn((short) watk);
        when(e.getPosition()).thenReturn((short) pos);
        return e;
    }

    /** Hooks for a bow scenario: weapons are 2H BOW; reqs are DEX-only via the lookup map. */
    private static BotEquipManager.OptimizerHooks bowHooks(Map<Integer, Integer> reqDexByItem) {
        return new BotEquipManager.OptimizerHooks() {
            @Override public boolean isTwoHanded(int itemId) { return itemId == W0_ID || itemId == W1_ID; }
            @Override public WeaponType getWeaponType(int itemId) {
                return (itemId == W0_ID || itemId == W1_ID) ? WeaponType.BOW : null;
            }
            @Override public boolean isOverall(int itemId) { return false; }
            @Override public boolean meetsReqs(Equip e, Job job, int lvl, int s, int d, int i, int l, int f) {
                int reqDex = reqDexByItem.getOrDefault(e.getItemId(), 0);
                return d >= reqDex;
            }
        };
    }
}
