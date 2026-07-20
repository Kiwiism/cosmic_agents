package server.agents.progression;

/** WZ- and shop-data-verified first supply stop for each first job. */
public final class AgentCareerShopCatalog {
    public record ShopStop(int mapId, int npcId) {
    }

    private AgentCareerShopCatalog() {
    }

    public static ShopStop forBundle(AgentCareerBuildBundle bundle) {
        AgentVictoriaLevel15Catalog.Career career =
                AgentVictoriaLevel15CatalogRepository.defaultRepository().careerFor(bundle);
        return new ShopStop(career.shopMapId(), career.shopNpcId());
    }
}
