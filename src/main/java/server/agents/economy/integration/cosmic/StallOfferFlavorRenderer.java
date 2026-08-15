package server.agents.economy.integration.cosmic;

import server.ItemInformationProvider;
import server.agents.economy.market.MarketObservation;

final class StallOfferFlavorRenderer {
    private StallOfferFlavorRenderer() { }

    static String render(MarketObservation listing, long offeredMesos) {
        String name = null;
        try {
            name = ItemInformationProvider.getInstance().getName(listing.itemId());
        } catch (RuntimeException | LinkageError unavailableDuringIsolatedTest) {
            // The live server initializes the provider; isolated economy tests deliberately do not.
        }
        if (name == null || name.isBlank()) name = "item " + listing.itemId();
        Object weaponAttack = listing.attributes().get("watk");
        String stats = weaponAttack instanceof Number number && number.intValue() > 0
                ? " wa" + number.intValue() : "";
        return "hey, offering " + mesos(offeredMesos) + " for your" + stats + " " + name
                + ". lmk, thanks!";
    }

    private static String mesos(long value) {
        if (value >= 1_000_000 && value % 1_000_000 == 0)
            return (value / 1_000_000) + "m";
        if (value >= 1_000_000 && value % 100_000 == 0)
            return (value / 1_000_000d) + "m";
        if (value >= 1_000 && value % 100 == 0)
            return (value / 1_000) + "k";
        return value + " mesos";
    }
}
