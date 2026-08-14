package server.agents.economy.catalog;

import org.junit.jupiter.api.Test;
import server.agents.economy.scenario.EconomyConfigLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CatalogBundleLoaderTest {
    @Test
    void configuredSourcesExistAndFormOneImmutableVersion() {
        var config = new EconomyConfigLoader().load().config();
        CatalogBundleDescriptor first = new CatalogBundleLoader().load(config.catalog);
        CatalogBundleDescriptor second = new CatalogBundleLoader().load(config.catalog);

        assertEquals("victoria-v83-authoritative", first.bundleId());
        assertEquals(64, first.adaptiveRevision().length());
        assertEquals(64, first.version().length());
        assertEquals(9, first.resourceHashes().size());
        assertEquals(first, second);
    }
}
