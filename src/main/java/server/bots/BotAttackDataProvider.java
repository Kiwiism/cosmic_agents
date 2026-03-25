package server.bots;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import provider.wz.WZFiles;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

final class BotAttackDataProvider {
    private static final Logger log = LoggerFactory.getLogger(BotAttackDataProvider.class);
    private static final BotAttackDataProvider instance = new BotAttackDataProvider();

    static BotAttackDataProvider getInstance() {
        return instance;
    }

    static final class NormalAttackProfile {
        private final int attackSpeed;
        private final String afterImage;
        private final Rectangle rightFacingBounds;
        private final List<String> sourceActions;
        private final String sourcePath;

        private NormalAttackProfile(int attackSpeed, String afterImage, Rectangle rightFacingBounds,
                                    List<String> sourceActions, String sourcePath) {
            this.attackSpeed = attackSpeed;
            this.afterImage = afterImage;
            this.rightFacingBounds = rightFacingBounds != null ? new Rectangle(rightFacingBounds) : null;
            this.sourceActions = List.copyOf(sourceActions);
            this.sourcePath = sourcePath;
        }

        int getAttackSpeed() {
            return attackSpeed;
        }

        String getAfterImage() {
            return afterImage;
        }

        boolean hasBoundingBox() {
            return rightFacingBounds != null;
        }

        List<String> getSourceActions() {
            return sourceActions;
        }

        String getSourcePath() {
            return sourcePath;
        }

        Rectangle calculateBoundingBox(Point origin, boolean facingLeft) {
            if (rightFacingBounds == null) {
                return null;
            }

            int leftOffset = rightFacingBounds.x;
            int rightOffset = rightFacingBounds.x + rightFacingBounds.width;
            if (facingLeft) {
                int originalLeftOffset = leftOffset;
                leftOffset = -rightOffset;
                rightOffset = -originalLeftOffset;
            }

            return new Rectangle(origin.x + leftOffset, origin.y + rightFacingBounds.y,
                    rightOffset - leftOffset, rightFacingBounds.height);
        }
    }

    private static final class AttackBoundsData {
        private final Rectangle bounds;
        private final List<String> sourceActions;
        private final String sourcePath;

        private AttackBoundsData(Rectangle bounds, List<String> sourceActions, String sourcePath) {
            this.bounds = new Rectangle(bounds);
            this.sourceActions = List.copyOf(sourceActions);
            this.sourcePath = sourcePath;
        }
    }

    private final Map<Integer, NormalAttackProfile> normalAttackProfiles = new HashMap<>();

    private BotAttackDataProvider() {
    }

    NormalAttackProfile getNormalAttackProfile(int itemId) {
        if (normalAttackProfiles.containsKey(itemId)) {
            return normalAttackProfiles.get(itemId);
        }

        NormalAttackProfile profile = loadNormalAttackProfile(itemId);
        normalAttackProfiles.put(itemId, profile);
        return profile;
    }

    private NormalAttackProfile loadNormalAttackProfile(int itemId) {
        Path weaponFile = WZFiles.CHARACTER.getFile()
                .resolve("Weapon")
                .resolve(String.format("%08d.img.xml", itemId));
        if (!Files.isRegularFile(weaponFile)) {
            return null;
        }

        Document weaponDocument = parseXmlDocument(weaponFile);
        if (weaponDocument == null) {
            return null;
        }

        Element weaponRoot = weaponDocument.getDocumentElement();
        Element info = findNamedChild(weaponRoot, "info");
        String afterImage = getStringValue(findNamedChild(info, "afterImage"));
        int attackSpeed = getIntValue(findNamedChild(info, "attackSpeed"), 0);
        int reqLevel = getIntValue(findNamedChild(info, "reqLevel"), 0);

        AttackBoundsData boundsData = loadAfterimageBounds(afterImage, reqLevel);
        if (boundsData == null) {
            boundsData = loadWeaponActionBounds(weaponRoot, weaponFile);
        }

        if (boundsData == null) {
            return new NormalAttackProfile(attackSpeed, afterImage, null, List.of(), weaponFile.toString());
        }

        return new NormalAttackProfile(attackSpeed, afterImage, boundsData.bounds,
                boundsData.sourceActions, boundsData.sourcePath);
    }

    private AttackBoundsData loadAfterimageBounds(String afterImage, int reqLevel) {
        if (afterImage == null || afterImage.isBlank()) {
            return null;
        }

        Path afterimageFile = WZFiles.CHARACTER.getFile()
                .resolve("Afterimage")
                .resolve(afterImage + ".img.xml");
        if (!Files.isRegularFile(afterimageFile)) {
            return null;
        }

        Document afterimageDocument = parseXmlDocument(afterimageFile);
        if (afterimageDocument == null) {
            return null;
        }

        Element root = afterimageDocument.getDocumentElement();
        Element levelBucket = findBestLevelBucket(root, reqLevel / 10);
        if (levelBucket == null) {
            return null;
        }

        Rectangle bounds = null;
        Set<String> actionNames = new LinkedHashSet<>();
        for (Element action : getNamedChildren(levelBucket)) {
            Element lt = findNamedChild(action, "lt");
            Element rb = findNamedChild(action, "rb");
            if (lt == null || rb == null) {
                continue;
            }

            Rectangle actionBounds = toRightFacingBounds(lt, rb);
            if (actionBounds == null) {
                continue;
            }

            bounds = bounds == null ? new Rectangle(actionBounds) : bounds.union(actionBounds);
            actionNames.add(action.getAttribute("name"));
        }

        if (bounds == null) {
            return null;
        }

        return new AttackBoundsData(bounds, new ArrayList<>(actionNames), afterimageFile.toString());
    }

    private Element findBestLevelBucket(Element root, int requestedBucket) {
        Element fallback = null;
        int bestBucket = Integer.MIN_VALUE;

        for (Element child : getNamedChildren(root)) {
            int bucket = parseInt(child.getAttribute("name"), Integer.MIN_VALUE);
            if (bucket == Integer.MIN_VALUE) {
                continue;
            }

            if (bucket == requestedBucket) {
                return child;
            }

            if (bucket <= requestedBucket && bucket > bestBucket) {
                bestBucket = bucket;
                fallback = child;
            }
        }

        return fallback;
    }

    private Rectangle toRightFacingBounds(Element lt, Element rb) {
        int leftX = getIntAttribute(lt, "x", 0);
        int topY = getIntAttribute(lt, "y", 0);
        int rightX = getIntAttribute(rb, "x", 0);
        int bottomY = getIntAttribute(rb, "y", 0);
        if (topY >= bottomY) {
            return null;
        }

        int normalizedLeft = Math.min(-rightX, -leftX);
        int normalizedRight = Math.max(-rightX, -leftX);
        if (normalizedLeft == normalizedRight) {
            return null;
        }

        return new Rectangle(normalizedLeft, topY, normalizedRight - normalizedLeft, bottomY - topY);
    }

    private AttackBoundsData loadWeaponActionBounds(Element root, Path weaponFile) {
        Rectangle bounds = null;
        Set<String> actionNames = new LinkedHashSet<>();

        for (Element child : getNamedChildren(root)) {
            String actionName = child.getAttribute("name");
            if (!isBasicAttackAction(actionName)) {
                continue;
            }

            for (Element frame : getNamedChildren(child)) {
                Element weaponCanvas = resolveWeaponCanvas(frame);
                Rectangle frameBounds = calculateFrameBounds(weaponCanvas);
                if (frameBounds == null) {
                    continue;
                }

                bounds = bounds == null ? new Rectangle(frameBounds) : bounds.union(frameBounds);
                actionNames.add(actionName);
            }
        }

        if (bounds == null) {
            return null;
        }

        return new AttackBoundsData(bounds, new ArrayList<>(actionNames), weaponFile.toString());
    }

    private static boolean isBasicAttackAction(String actionName) {
        return actionName.startsWith("swing")
                || actionName.startsWith("stab")
                || actionName.startsWith("shoot");
    }

    private Element resolveWeaponCanvas(Element frame) {
        Element weapon = findNamedChild(frame, "weapon");
        if (weapon == null) {
            return null;
        }

        if ("uol".equals(weapon.getTagName())) {
            String path = weapon.getAttribute("value");
            if (path == null || path.isBlank()) {
                return null;
            }

            Element resolved = resolveRelativePath(frame, path);
            return resolved != null && "canvas".equals(resolved.getTagName()) ? resolved : null;
        }

        return "canvas".equals(weapon.getTagName()) ? weapon : null;
    }

    private Rectangle calculateFrameBounds(Element weaponCanvas) {
        if (weaponCanvas == null) {
            return null;
        }

        int width = getIntAttribute(weaponCanvas, "width", 0);
        int height = getIntAttribute(weaponCanvas, "height", 0);
        if (width <= 0 || height <= 0) {
            return null;
        }

        Element origin = findNamedChild(weaponCanvas, "origin");
        if (origin == null) {
            return null;
        }

        Element map = findNamedChild(weaponCanvas, "map");
        Element anchor = findNamedChild(map, "navel");
        if (anchor == null) {
            anchor = findNamedChild(map, "hand");
        }

        int anchorX = getIntAttribute(anchor, "x", 0);
        int anchorY = getIntAttribute(anchor, "y", 0);
        int originX = getIntAttribute(origin, "x", 0);
        int originY = getIntAttribute(origin, "y", 0);
        return new Rectangle(anchorX - originX, anchorY - originY, width, height);
    }

    private Element resolveRelativePath(Element start, String path) {
        Element current = start;
        for (String segment : path.split("/")) {
            if (segment.isEmpty()) {
                continue;
            }

            if ("..".equals(segment)) {
                current = getParentElement(current);
            } else {
                current = findNamedChild(current, segment);
            }

            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private Document parseXmlDocument(Path path) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(path.toFile());
        } catch (ParserConfigurationException | SAXException | IOException e) {
            log.warn("Failed to load bot attack data from {}", path, e);
            return null;
        }
    }

    private static Element getParentElement(Element element) {
        Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() != Node.ELEMENT_NODE) {
            parent = parent.getParentNode();
        }
        return parent instanceof Element ? (Element) parent : null;
    }

    private static List<Element> getNamedChildren(Element parent) {
        List<Element> children = new ArrayList<>();
        if (parent == null) {
            return children;
        }

        Node child = parent.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                children.add((Element) child);
            }
            child = child.getNextSibling();
        }
        return children;
    }

    private static Element findNamedChild(Element parent, String name) {
        if (parent == null) {
            return null;
        }

        for (Element child : getNamedChildren(parent)) {
            if (name.equals(child.getAttribute("name"))) {
                return child;
            }
        }
        return null;
    }

    private static int getIntValue(Element element, int defaultValue) {
        return getIntAttribute(element, "value", defaultValue);
    }

    private static int getIntAttribute(Element element, String attributeName, int defaultValue) {
        if (element == null || !element.hasAttribute(attributeName)) {
            return defaultValue;
        }
        return parseInt(element.getAttribute(attributeName), defaultValue);
    }

    private static int parseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static String getStringValue(Element element) {
        if (element == null || !element.hasAttribute("value")) {
            return null;
        }
        return element.getAttribute("value");
    }
}
