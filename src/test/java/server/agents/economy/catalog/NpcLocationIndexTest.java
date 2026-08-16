package server.agents.economy.catalog;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class NpcLocationIndexTest {
    @Test
    void resolvesRealVictoriaShopNpcLocations() {
        NpcLocationIndex index = NpcLocationIndex.loadDefault();
        assertEquals(104000003, index.primaryMap(1001000).orElseThrow());
        assertEquals(64, index.revision().length());
        assertNull(index.apply(Integer.MAX_VALUE));
    }

    @Test
    void everyConfiguredRealShopNpcHasSourceMapEvidence() throws Exception {
        String sql;
        try (InputStream input = getClass().getResourceAsStream("/db/data/101-shops-data.sql")) {
            assertNotNull(input);
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        var matcher = Pattern.compile("\\((\\d+),\\s*(\\d+)\\)").matcher(sql);
        NpcLocationIndex index = NpcLocationIndex.loadDefault();
        List<Integer> missing = new ArrayList<>();
        while (matcher.find()) {
            int npcId = Integer.parseInt(matcher.group(2));
            if (index.primaryMap(npcId).isEmpty()) missing.add(npcId);
        }
        assertEquals(Set.of(9001002, 9090000), Set.copyOf(missing),
                "Only unplaced/special SQL shops may lack physical map evidence");
    }
}
