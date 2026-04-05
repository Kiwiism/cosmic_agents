package server.bots.combat;

import client.Character;
import constants.game.CharacterStance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import provider.wz.WZFiles;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BotCharacterHitboxProvider {
    private static final Logger log = LoggerFactory.getLogger(BotCharacterHitboxProvider.class);
    private static final BotCharacterHitboxProvider instance = new BotCharacterHitboxProvider();

    private final Map<String, Rectangle> boundsByAction = new ConcurrentHashMap<>();
    private volatile Path cachedCharacterRoot = null;

    public static BotCharacterHitboxProvider getInstance() {
        return instance;
    }

    public Rectangle getBotBounds(Character bot) {
        if (bot == null) {
            return null;
        }

        return getBotBounds(bot.getStance(), bot.getPosition());
    }

    public Rectangle getBotBounds(int stance, Point position) {
        ensureCurrentCharacterRoot();

        String action = CharacterStance.bodyHitboxAction(stance);
        Rectangle modelBounds = boundsByAction.computeIfAbsent(action, this::loadActionBounds);
        if (modelBounds == null && !"stand1".equals(action)) {
            modelBounds = boundsByAction.computeIfAbsent("stand1", this::loadActionBounds);
        }
        if (modelBounds == null) {
            return null;
        }

        return calculateWorldBounds(modelBounds, position, CharacterStance.isFacingLeft(stance));
    }

    private void ensureCurrentCharacterRoot() {
        Path currentCharacterRoot = WZFiles.CHARACTER.getFile();
        Path previousCharacterRoot = cachedCharacterRoot;
        if (previousCharacterRoot != null && previousCharacterRoot.equals(currentCharacterRoot)) {
            return;
        }

        synchronized (boundsByAction) {
            if (cachedCharacterRoot != null && cachedCharacterRoot.equals(currentCharacterRoot)) {
                return;
            }
            boundsByAction.clear();
            cachedCharacterRoot = currentCharacterRoot;
        }
    }

    private Rectangle loadActionBounds(String action) {
        Path bodyFile = WZFiles.CHARACTER.getFile().resolve("00002000.img.xml");
        if (!Files.isRegularFile(bodyFile)) {
            log.warn("Bot character hitbox: body animation file not found at {}", bodyFile);
            return null;
        }

        Document document = parseXmlDocument(bodyFile);
        if (document == null) {
            return null;
        }

        Element actionNode = findNamedChild(document.getDocumentElement(), action);
        Element frameNode = actionNode != null ? firstFrameNode(actionNode) : null;
        Rectangle bounds = toBounds(frameNode);
        if (bounds == null) {
            log.debug("Bot character hitbox: no drawable bounds for action {}", action);
        }
        return bounds;
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

    private Rectangle toBounds(Element frameNode) {
        if (frameNode == null) {
            return null;
        }

        Rectangle combined = null;
        Node child = frameNode.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element canvas = (Element) child;
                if (!"canvas".equals(canvas.getTagName())) {
                    child = child.getNextSibling();
                    continue;
                }

                Rectangle canvasBounds = toCanvasBounds(canvas);
                if (canvasBounds == null) {
                    child = child.getNextSibling();
                    continue;
                }

                combined = combined == null ? canvasBounds : combined.union(canvasBounds);
            }
            child = child.getNextSibling();
        }

        return combined;
    }

    private Rectangle toCanvasBounds(Element canvas) {
        int width = getIntAttribute(canvas, "width", 0);
        int height = getIntAttribute(canvas, "height", 0);
        if (width <= 0 || height <= 0) {
            return null;
        }

        Element origin = findNamedChild(canvas, "origin");
        int originX = getIntAttribute(origin, "x", 0);
        int originY = getIntAttribute(origin, "y", 0);
        return new Rectangle(-originX, -originY, width, height);
    }

    private static Element firstFrameNode(Element actionNode) {
        Element frame = findNamedChild(actionNode, "0");
        if (frame != null) {
            return frame;
        }

        Node child = actionNode.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                return (Element) child;
            }
            child = child.getNextSibling();
        }
        return null;
    }

    private Document parseXmlDocument(Path path) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(path.toFile());
        } catch (ParserConfigurationException | SAXException | IOException e) {
            log.warn("Failed to load bot character hitbox data from {}", path, e);
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
}
