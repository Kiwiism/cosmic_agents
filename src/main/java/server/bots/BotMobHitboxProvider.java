package server.bots;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import provider.wz.WZFiles;
import server.life.Monster;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class BotMobHitboxProvider {
    private static final Logger log = LoggerFactory.getLogger(BotMobHitboxProvider.class);
    private static final BotMobHitboxProvider instance = new BotMobHitboxProvider();

    private final Map<Integer, Rectangle> standBoundsByMobId = new ConcurrentHashMap<>();

    static BotMobHitboxProvider getInstance() {
        return instance;
    }

    Rectangle getMobBounds(Monster mob) {
        if (mob == null) {
            return null;
        }

        return getMobBounds(mob.getId(), mob.getPosition(), mob.isFacingLeft());
    }

    Rectangle getMobBounds(int mobId, Point position, boolean facingLeft) {
        Rectangle modelBounds = standBoundsByMobId.computeIfAbsent(mobId, this::loadStandBounds);
        if (modelBounds == null) {
            return null;
        }

        return calculateWorldBounds(modelBounds, position, facingLeft);
    }

    private Rectangle loadStandBounds(int mobId) {
        Path mobFile = WZFiles.MOB.getFile().resolve(String.format("%07d.img.xml", mobId));
        if (!Files.isRegularFile(mobFile)) {
            return null;
        }

        Document document = parseXmlDocument(mobFile);
        if (document == null) {
            return null;
        }

        Rectangle bounds = loadStandBounds(document.getDocumentElement());
        if (bounds == null) {
            log.debug("Bot mob hitbox: no stand/0 lt-rb bounds for mob {}", mobId);
        }
        return bounds;
    }

    private Rectangle loadStandBounds(Element root) {
        Element linkedRoot = resolveLinkedRoot(root);
        Element standFrame = findNamedChild(linkedRoot, "stand");
        if (standFrame != null) {
            standFrame = findNamedChild(standFrame, "0");
        }

        if (standFrame == null) {
            Element move = findNamedChild(linkedRoot, "move");
            if (move != null) {
                standFrame = findNamedChild(move, "0");
            }
        }

        if (standFrame == null) {
            return null;
        }

        Element lt = findNamedChild(standFrame, "lt");
        Element rb = findNamedChild(standFrame, "rb");
        return toBounds(lt, rb);
    }

    private Element resolveLinkedRoot(Element root) {
        Element info = findNamedChild(root, "info");
        int linkedMobId = getIntValue(findNamedChild(info, "link"), 0);
        if (linkedMobId <= 0) {
            return root;
        }

        Path linkedFile = WZFiles.MOB.getFile().resolve(String.format("%07d.img.xml", linkedMobId));
        if (!Files.isRegularFile(linkedFile)) {
            return root;
        }

        Document linkedDocument = parseXmlDocument(linkedFile);
        return linkedDocument != null ? linkedDocument.getDocumentElement() : root;
    }

    private Rectangle calculateWorldBounds(Rectangle modelBounds, Point origin, boolean facingLeft) {
        int left = modelBounds.x;
        int right = modelBounds.x + modelBounds.width;
        if (facingLeft) {
            int originalLeft = left;
            left = -right;
            right = -originalLeft;
        }

        return new Rectangle(origin.x + left, origin.y + modelBounds.y, right - left, modelBounds.height);
    }

    private Rectangle toBounds(Element lt, Element rb) {
        if (lt == null || rb == null) {
            return null;
        }

        int left = Math.min(getIntAttribute(lt, "x", 0), getIntAttribute(rb, "x", 0));
        int right = Math.max(getIntAttribute(lt, "x", 0), getIntAttribute(rb, "x", 0));
        int top = Math.min(getIntAttribute(lt, "y", 0), getIntAttribute(rb, "y", 0));
        int bottom = Math.max(getIntAttribute(lt, "y", 0), getIntAttribute(rb, "y", 0));
        if (left >= right || top >= bottom) {
            return null;
        }

        return new Rectangle(left, top, right - left, bottom - top);
    }

    private Document parseXmlDocument(Path path) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(path.toFile());
        } catch (ParserConfigurationException | SAXException | IOException e) {
            log.warn("Failed to load bot mob hitbox data from {}", path, e);
            return null;
        }
    }

    private static Element findNamedChild(Element parent, String name) {
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

    private static int getIntAttribute(Element element, String name, int defaultValue) {
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

    private static int getIntValue(Element element, int defaultValue) {
        return getIntAttribute(element, "value", defaultValue);
    }
}
