package server.agents.economy.integration.cosmic;

import server.ItemInformationProvider;
import server.agents.economy.market.MarketObservation;

public final class StallOfferFlavorRenderer implements StallOfferTextRenderer {
    public static final String DEFAULT_TEMPLATE =
            "hey i wana offer {offer} meso for {item_stats} {item_name} thanks!";
    private final String template;

    public StallOfferFlavorRenderer(String template) {
        if (template == null || template.isBlank()) throw new IllegalArgumentException("offer template is blank");
        this.template = template;
    }

    @Override
    public String render(MarketObservation listing, long offeredMesos) {
        String name = null;
        try {
            name = ItemInformationProvider.getInstance().getName(listing.itemId());
        } catch (RuntimeException | LinkageError unavailableDuringIsolatedTest) {
            // The live server initializes the provider; isolated economy tests deliberately do not.
        }
        if (name == null || name.isBlank()) name = "item " + listing.itemId();
        Object weaponAttack = listing.attributes().get("watk");
        String stats = weaponAttack instanceof Number number && number.intValue() > 0
                ? "wa" + number.intValue() : "";
        return template.replace("{offer}", mesos(offeredMesos))
                .replace("{item_stats}", stats).replace("{item_name}", name)
                .replaceAll("\\s+", " ").trim();
    }

    private static String mesos(long value) {
        if (value >= 1_000_000 && value % 1_000_000 == 0)
            return (value / 1_000_000) + "m";
        if (value >= 1_000_000 && value % 100_000 == 0)
            return (value / 1_000_000d) + "m";
        if (value >= 1_000 && value % 100 == 0)
            return (value / 1_000) + "k";
        return Long.toString(value);
    }
}
