package server.agents.economy.integration.cosmic;

import server.ItemInformationProvider;

import java.util.Map;

/** Renders observable chat from authoritative structured sale data. */
public final class OpenChatSaleFlavorRenderer {
    private final String template;

    public OpenChatSaleFlavorRenderer(String template) {
        if (template == null || template.isBlank()) throw new IllegalArgumentException("sale template is blank");
        this.template = template;
    }

    public String render(int itemId, Map<String, Object> attributes, long ask) {
        ItemText item = item(itemId, attributes);
        return template.replace("{ask}", mesos(ask)).replace("{item_stats}", item.stats())
                .replace("{item_name}", item.name()).replaceAll("\\s+", " ").trim();
    }

    public String renderOffer(int itemId, Map<String, Object> attributes, long offer) {
        ItemText item = item(itemId, attributes);
        return ("hey i wana offer " + mesos(offer) + " meso for ur " + item.stats() + ' '
                + item.name() + " lmk thanks!").replaceAll("\\s+", " ").trim();
    }

    public String renderAcceptance(long offer) {
        return "deal at " + mesos(offer) + ", trade me";
    }

    public String renderFailure() { return "sorry, trade didn't go through"; }

    private static ItemText item(int itemId, Map<String, Object> attributes) {
        String name = null;
        try { name = ItemInformationProvider.getInstance().getName(itemId); }
        catch (RuntimeException | LinkageError unavailableDuringIsolatedTest) { }
        if (name == null || name.isBlank()) name = "item " + itemId;
        Object weaponAttack = attributes == null ? null : attributes.get("watk");
        String stats = weaponAttack instanceof Number number && number.intValue() > 0
                ? "wa" + number.intValue()
                : attributes != null && attributes.containsKey("upgradeSlots") ? "clean" : "";
        return new ItemText(stats, name);
    }

    private static String mesos(long value) {
        if (value >= 1_000_000 && value % 1_000_000 == 0) return value / 1_000_000 + "m";
        if (value >= 1_000_000 && value % 100_000 == 0) return value / 1_000_000d + "m";
        if (value >= 1_000 && value % 100 == 0) return value / 1_000 + "k";
        return Long.toString(value);
    }

    private record ItemText(String stats, String name) { }
}
