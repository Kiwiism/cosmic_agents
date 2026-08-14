package server.quest.requirements;

import org.junit.jupiter.api.Test;
import provider.Data;
import server.quest.Quest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ItemRequirementEvidenceTest {
    @Test
    void exposesAnImmutableExactRequirementMap() {
        Data root = mock(Data.class);
        Data row = mock(Data.class);
        Data id = mock(Data.class);
        Data count = mock(Data.class);
        when(root.getChildren()).thenReturn(List.of(row));
        when(row.getChildByPath("id")).thenReturn(id);
        when(row.getChildByPath("count")).thenReturn(count);
        when(id.getData()).thenReturn(4000031);
        when(count.getData()).thenReturn(100);

        ItemRequirement requirement = new ItemRequirement(mock(Quest.class), root);

        assertEquals(Map.of(4000031, 100), requirement.getRequiredItems());
        assertThrows(UnsupportedOperationException.class,
                () -> requirement.getRequiredItems().put(1, 1));
    }
}
