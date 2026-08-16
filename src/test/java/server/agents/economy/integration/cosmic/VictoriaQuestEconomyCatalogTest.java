package server.agents.economy.integration.cosmic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VictoriaQuestEconomyCatalogTest {
    @Test
    void exposesOnlyGeneratorApprovedVictoriaStarts() {
        VictoriaQuestEconomyCatalog catalog = new VictoriaQuestEconomyCatalog(
                "/agents/catalogs/adaptive/victoria-quest-facts.json", "eligible-now");

        assertFalse(catalog.eligibleAtLevel(25).isEmpty());
        assertTrue(catalog.eligibleAtLevel(25).stream().allMatch(entry ->
                entry.startNpcId() > 0 && entry.completeNpcId() > 0));
        assertTrue(catalog.version().startsWith("victoria-quest-facts-"));
    }
}
