package server.bots.combat;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Shared WZ DOM read helpers for the bot combat WZ providers. Both {@link BotAttackDataProvider}
 * and {@link BotMobHitboxProvider} parse the same {@code *.img.xml} node grammar; these are the
 * single source of truth for navigating named children and reading int attributes/values so the
 * two providers no longer carry their own equivalent copies.
 */
final class BotWzXml {

    private BotWzXml() {
    }

    static Element findNamedChild(Element parent, String name) {
        if (parent == null) {
            return null;
        }

        Node child = parent.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) child;
                if (name.equals(element.getAttribute("name"))) {
                    return element;
                }
            }
            child = child.getNextSibling();
        }
        return null;
    }

    static int getIntAttribute(Element element, String name, int defaultValue) {
        if (element == null) {
            return defaultValue;
        }

        String value = element.getAttribute(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    static int getIntValue(Element element, int defaultValue) {
        return getIntAttribute(element, "value", defaultValue);
    }
}
