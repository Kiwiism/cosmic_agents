package server.agents.catalog;

import java.nio.file.Path;
import java.util.Objects;

public record CatalogLoadOptions(Path rootPath,
                                 Path gameCatalogDir,
                                 Path npcCatalogDir,
                                 Path agentLlmCatalogDir,
                                 Path overridesDir,
                                 Path reactorCatalogDir) {
    public CatalogLoadOptions {
        Objects.requireNonNull(rootPath, "rootPath");
        Objects.requireNonNull(gameCatalogDir, "gameCatalogDir");
        Objects.requireNonNull(npcCatalogDir, "npcCatalogDir");
        Objects.requireNonNull(agentLlmCatalogDir, "agentLlmCatalogDir");
        Objects.requireNonNull(overridesDir, "overridesDir");
        Objects.requireNonNull(reactorCatalogDir, "reactorCatalogDir");
    }

    public static CatalogLoadOptions fromRepoRoot(Path rootPath) {
        Path root = rootPath.toAbsolutePath().normalize();
        return new CatalogLoadOptions(
                root,
                root.resolve("tmp/game-catalog"),
                root.resolve("tmp/npc-catalog"),
                root.resolve("tmp/agent-llm-catalog"),
                root.resolve("docs/agents/catalog-overrides"),
                root.resolve("tmp/reactor-catalog"));
    }
}
