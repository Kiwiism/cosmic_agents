package server.agents.catalog;

import java.nio.file.Path;

public final class AgentCatalogService {
    private final CatalogBundle bundle;
    private final CatalogQueryService queries;

    private AgentCatalogService(CatalogBundle bundle) {
        this.bundle = bundle;
        this.queries = new CatalogQueryService(bundle);
    }

    public static AgentCatalogService loadFromRepoRoot(Path rootPath) {
        CatalogBundle bundle = new CatalogBundleLoader().load(CatalogLoadOptions.fromRepoRoot(rootPath));
        return new AgentCatalogService(bundle);
    }

    public static AgentCatalogService load(CatalogLoadOptions options) {
        return new AgentCatalogService(new CatalogBundleLoader().load(options));
    }

    public CatalogBundle bundle() {
        return bundle;
    }

    public CatalogQueryService queries() {
        return queries;
    }
}
