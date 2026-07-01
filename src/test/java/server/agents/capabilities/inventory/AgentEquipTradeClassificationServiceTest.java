package server.agents.capabilities.inventory;

import client.Character;
import client.inventory.InventoryType;
import client.inventory.Item;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.inventory.AgentEquipTradeClassificationService.SlowClassificationReport;
import server.agents.capabilities.inventory.AgentEquipTradeGroupService.AgentEquipTradeGroups;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentEquipTradeClassificationServiceTest {
    @Test
    void classifiesEquipBagItemsThroughLegacyGroupPolicy() {
        Character agent = namedCharacter("agent");
        Item normalHigh = item(3000, (short) 3);
        Item normalLow = item(1000, (short) 1);
        Item reservedOther = item(2000, (short) 2);
        Item reservedSelf = item(4000, (short) 4);

        AgentEquipTradeGroups groups = AgentEquipTradeClassificationService.classifyEquipTradeGroups(
                agent,
                callbacks(false,
                        List.of(normalHigh, reservedSelf, reservedOther, normalLow),
                        Set.of(reservedSelf),
                        item -> item == reservedOther || item == reservedSelf,
                        namedCharacter("owner"),
                        report -> {
                            throw new AssertionError("slow report should not be emitted without profiling");
                        }));

        assertEquals(List.of(normalLow, normalHigh), groups.normal());
        assertEquals(List.of(reservedOther), groups.reservedForOther());
        assertEquals(List.of(reservedSelf), groups.reservedForSelf());
    }

    @Test
    void slowProfileReportCarriesLegacyMetricsAndNames() {
        Character agent = namedCharacter("agent");
        Character owner = namedCharacter("owner");
        Item normal = item(1000, (short) 1);
        Item reservedOther = item(2000, (short) 2);
        Item reservedSelf = item(3000, (short) 3);
        AtomicReference<SlowClassificationReport> reportRef = new AtomicReference<>();

        AgentEquipTradeGroups groups = AgentEquipTradeClassificationService.classifyEquipTradeGroups(
                agent,
                callbacks(true,
                        List.of(normal, reservedOther, reservedSelf),
                        Set.of(reservedSelf),
                        item -> item == reservedOther,
                        owner,
                        reportRef::set));

        SlowClassificationReport report = reportRef.get();
        assertEquals(List.of(normal), groups.normal());
        assertEquals(List.of(reservedOther), groups.reservedForOther());
        assertEquals(List.of(reservedSelf), groups.reservedForSelf());
        assertEquals("agent", report.agentName());
        assertEquals("owner", report.ownerName());
        assertEquals(3, report.bagItems());
        assertEquals(1, report.selfKeep());
        assertEquals(1, report.normalItems());
        assertEquals(1, report.reservedOtherItems());
        assertEquals(1, report.reservedSelfItems());
        assertEquals(2, report.reservedOtherChecks());
        assertEquals(1, report.reservedOtherHits());
        assertTrue(report.elapsedNs() >= 0L);
    }

    @Test
    void callbackFactoryPreservesSuppliedDataSources() {
        Character agent = namedCharacter("agent");
        Character owner = namedCharacter("owner");
        Item item = item(1000, (short) 1);
        AtomicBoolean profileCalled = new AtomicBoolean();
        AtomicReference<Character> bagAgent = new AtomicReference<>();
        AtomicReference<Character> selfKeepAgent = new AtomicReference<>();
        AtomicReference<Item> reservedCheck = new AtomicReference<>();
        AtomicReference<SlowClassificationReport> reportRef = new AtomicReference<>();

        AgentEquipTradeGroups groups = AgentEquipTradeClassificationService.classifyEquipTradeGroups(
                agent,
                AgentEquipTradeClassificationService.ClassificationCallbacks.of(
                        () -> {
                            profileCalled.set(true);
                            return true;
                        },
                        () -> 0L,
                        currentAgent -> {
                            bagAgent.set(currentAgent);
                            return List.of(item);
                        },
                        currentAgent -> {
                            selfKeepAgent.set(currentAgent);
                            return Set.of();
                        },
                        checked -> {
                            reservedCheck.set(checked);
                            return false;
                        },
                        () -> owner,
                        reportRef::set));

        assertEquals(List.of(item), groups.normal());
        assertTrue(profileCalled.get());
        assertSame(agent, bagAgent.get());
        assertSame(agent, selfKeepAgent.get());
        assertSame(item, reservedCheck.get());
        assertEquals("owner", reportRef.get().ownerName());
    }

    @Test
    void profileBelowThresholdDoesNotWarn() {
        Character agent = namedCharacter("agent");
        Item item = item(1000, (short) 1);
        AtomicBoolean warned = new AtomicBoolean();

        AgentEquipTradeClassificationService.classifyEquipTradeGroups(
                agent,
                callbacks(true,
                        List.of(item),
                        Set.of(),
                        checked -> false,
                        null,
                        report -> warned.set(true),
                        Long.MAX_VALUE));

        assertFalse(warned.get());
    }

    private static AgentEquipTradeClassificationService.ClassificationCallbacks callbacks(
            boolean profile,
            List<Item> bagItems,
            Set<Item> selfKeep,
            java.util.function.Predicate<Item> reservedOther,
            Character owner,
            AgentEquipTradeClassificationService.SlowClassificationLogger logger) {
        return callbacks(profile, bagItems, selfKeep, reservedOther, owner, logger, 0L);
    }

    private static AgentEquipTradeClassificationService.ClassificationCallbacks callbacks(
            boolean profile,
            List<Item> bagItems,
            Set<Item> selfKeep,
            java.util.function.Predicate<Item> reservedOther,
            Character owner,
            AgentEquipTradeClassificationService.SlowClassificationLogger logger,
            long slowWarnNs) {
        return AgentEquipTradeClassificationService.ClassificationCallbacks.of(
                () -> profile,
                () -> slowWarnNs,
                ignored -> bagItems,
                ignored -> selfKeep,
                reservedOther,
                () -> owner,
                logger);
    }

    private static Character namedCharacter(String name) {
        Character character = mock(Character.class);
        when(character.getName()).thenReturn(name);
        return character;
    }

    private static Item item(int itemId, short position) {
        Item item = mock(Item.class);
        when(item.getInventoryType()).thenReturn(InventoryType.EQUIP);
        when(item.getItemId()).thenReturn(itemId);
        when(item.getPosition()).thenReturn(position);
        return item;
    }
}
