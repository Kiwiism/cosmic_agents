package server.agents.capabilities.equipment;

import client.inventory.Equip;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentEquipmentDebugReportFormatterTest {
    @Test
    void itemHeaderPreservesLegacySelfReserveColumn() {
        assertTrue(AgentEquipmentDebugReportFormatter.itemHeader(true).contains("SELF"));
        assertTrue(AgentEquipmentDebugReportFormatter.itemHeader(false).contains("reqs"));
    }

    @Test
    void appendItemRowPreservesNameFallbackTruncationStatsAndRequirements() {
        Equip equip = mock(Equip.class);
        when(equip.getItemId()).thenReturn(1002001);
        when(equip.getStr()).thenReturn((short) 1);
        when(equip.getDex()).thenReturn((short) 2);
        when(equip.getInt()).thenReturn((short) 3);
        when(equip.getLuk()).thenReturn((short) 4);
        when(equip.getWatk()).thenReturn((short) 5);
        when(equip.getMatk()).thenReturn((short) 6);
        when(equip.getWdef()).thenReturn((short) 7);
        when(equip.getMdef()).thenReturn((short) 8);
        when(equip.getAcc()).thenReturn((short) 9);
        when(equip.getAvoid()).thenReturn((short) 10);
        when(equip.getHp()).thenReturn((short) 11);
        when(equip.getMp()).thenReturn((short) 12);

        AgentEquipmentDebugReportFormatter.ItemInfo itemInfo = mock(AgentEquipmentDebugReportFormatter.ItemInfo.class);
        when(itemInfo.getName(1002001)).thenReturn("This name is deliberately longer than thirty chars");
        when(itemInfo.getEquipmentSlot(1002001)).thenReturn("Cp");
        when(itemInfo.getEquipLevelReq(1002001)).thenReturn(25);
        when(itemInfo.getEquipStats(1002001)).thenReturn(Map.of(
                "reqJob", 1,
                "reqSTR", 4,
                "reqDEX", 5,
                "reqINT", 6,
                "reqLUK", 7,
                "reqPOP", 8));

        StringBuilder row = new StringBuilder();
        AgentEquipmentDebugReportFormatter.appendItemRow(row, itemInfo, equip, (short) -9, true);

        String text = row.toString();
        assertTrue(text.startsWith("-9  This name is deliberately long"));
        assertTrue(text.contains("Cp"));
        assertTrue(text.contains("Y"));
        assertTrue(text.contains("lv25 job1 str4 dex5 int6 luk7 pop8"));
    }

    @Test
    void safeMapIdReturnsMinusOneForNullAgent() {
        assertEquals(-1, AgentEquipmentDebugReportFormatter.safeMapId(null));
    }
}
