package server.agents.progression;

import server.agents.capabilities.contracts.AgentResourceCategory;

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

    public static ShopStop forSupply(AgentCareerBuildBundle bundle,
                                     AgentResourceCategory category,
                                     int currentMapId) {
        if (category != AgentResourceCategory.HP_POTION
                && category != AgentResourceCategory.MP_POTION) {
            return forBundle(bundle);
        }
        AgentVictoriaLevel15Catalog.Career nearest = null;
        int nearestDistance = Integer.MAX_VALUE;
        for (AgentVictoriaLevel15Catalog.Career candidate :
                AgentVictoriaLevel15CatalogRepository.defaultRepository().catalog().careers()) {
            int distance = AgentVictoriaTrainingRouteCatalog.distance(currentMapId, candidate.shopMapId());
            if (distance >= 0 && distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return nearest == null
                ? forBundle(bundle)
                : new ShopStop(nearest.shopMapId(), nearest.shopNpcId());
    }
}
