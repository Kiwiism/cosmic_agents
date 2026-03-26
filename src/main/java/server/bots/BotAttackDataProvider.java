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
        private final int attack;
        private final int attackDelayMillis;
        private final String afterImage;
        private final Rectangle rightFacingBounds;
        private final List<String> sourceActions;
        private final String sourcePath;

        private NormalAttackProfile(int attackSpeed, int attack, int attackDelayMillis, String afterImage, Rectangle rightFacingBounds,
                                    List<String> sourceActions, String sourcePath) {
            this.attackSpeed = attackSpeed;
            this.attack = attack;
            this.attackDelayMillis = attackDelayMillis;
            this.afterImage = afterImage;
            this.rightFacingBounds = rightFacingBounds != null ? new Rectangle(rightFacingBounds) : null;
            this.sourceActions = List.copyOf(sourceActions);
            this.sourcePath = sourcePath;
        }

        int getAttackSpeed() {
            return attackSpeed;
        }

        int getAttack() {
            return attack;
        }

        int getAttackDelayMillis() {
            return attackDelayMillis;
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
        private final int attackDelayMillis;
        private final List<String> sourceActions;
        private final String sourcePath;

        private AttackBoundsData(Rectangle bounds, int attackDelayMillis, List<String> sourceActions, String sourcePath) {
            this.bounds = new Rectangle(bounds);
            this.attackDelayMillis = attackDelayMillis;
            this.sourceActions = List.copyOf(sourceActions);
            this.sourcePath = sourcePath;
        }
    }

    private final Map<Integer, NormalAttackProfile> normalAttackProfiles = new HashMap<>();
    private volatile Map<String, Integer> bodyStanceDurations = null;

    private BotAttackDataProvider() {
    }

    /**
     * Returns the total animation duration in milliseconds for the given body stance
     * (e.g. "swingO1", "shoot1"), loaded from {@code Character/00002000.img.xml}.
     * This mirrors OpenStory's {@code BodyDrawInfo::get_delay} summed over all stance frames,
     * which is the authoritative timing source used by {@code Char::get_attackdelay}.
     * Returns 0 if the stance is not found.
     */
    int getBodyStanceDurationMs(String stanceName) {
        if (bodyStanceDurations == null) {
            synchronized (this) {
                if (bodyStanceDurations == null) {
                    bodyStanceDurations = loadBodyStanceDurations();
                }
            }
        }
        return bodyStanceDurations.getOrDefault(stanceName, 0);
    }

    private Map<String, Integer> loadBodyStanceDurations() {
        Path bodyFile = WZFiles.CHARACTER.getFile().resolve("00002000.img.xml");
        if (!Files.isRegularFile(bodyFile)) {
            log.warn("Bot attack timing: body animation file not found at {}", bodyFile);
            return Map.of();
        }

        Document doc = parseXmlDocument(bodyFile);
        if (doc == null) {
            return Map.of();
        }

        Map<String, Integer> durations = new HashMap<>();
        for (Element stanceEl : getNamedChildren(doc.getDocumentElement())) {
            String stanceName = stanceEl.getAttribute("name");
            if (stanceName.isBlank()) {
                continue;
            }

            int totalDelay = 0;
            for (Element frameEl : getNamedChildren(stanceEl)) {
                // Frames with an "action" child are action-redirect frames (no direct delay)
                if (findNamedChild(frameEl, "action") != null) {
                    continue;
                }
                // Default 100 ms matches OpenStory's BodyDrawInfo fallback
                totalDelay += getIntValue(findNamedChild(frameEl, "delay"), 100);
            }

            if (totalDelay > 0) {
                durations.put(stanceName, totalDelay);
            }
        }

        log.info("Bot attack timing: loaded {} body stances from {}", durations.size(), bodyFile.getFileName());
        return Map.copyOf(durations);
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
        int attack = getIntValue(findNamedChild(info, "attack"), 0);
        int reqLevel = getIntValue(findNamedChild(info, "reqLevel"), 0);

        AttackBoundsData afterImageData = loadAfterimageBounds(afterImage, reqLevel);
        AttackBoundsData weaponActionData = loadWeaponActionBounds(weaponRoot, weaponFile);

        // Prefer afterimage for hit bounds (cleaner per-level data), but always use weapon
        // action delay for timing — afterimage frame delays only cover the trail display window
        // and are far shorter than the full swing animation.
        AttackBoundsData forBounds = afterImageData != null ? afterImageData : weaponActionData;
        if (forBounds == null) {
            return new NormalAttackProfile(attackSpeed, attack, 0, afterImage, null, List.of(), weaponFile.toString());
        }

        int delay = weaponActionData != null && weaponActionData.attackDelayMillis > 0
                ? weaponActionData.attackDelayMillis
                : afterImageData != null ? afterImageData.attackDelayMillis : 0;

        return new NormalAttackProfile(attackSpeed, attack, delay, afterImage, forBounds.bounds,
                forBounds.sourceActions, forBounds.sourcePath);
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
        int totalDelay = 0;
        int delayedActions = 0;
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
            int actionDelay = sumActionDelayMillis(action);
            if (actionDelay > 0) {
                totalDelay += actionDelay;
                delayedActions++;
            }
        }

        if (bounds == null) {
            return null;
        }

        return new AttackBoundsData(bounds, averageDelay(totalDelay, delayedActions), new ArrayList<>(actionNames), afterimageFile.toString());
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
        int totalDelay = 0;
        int delayedActions = 0;
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

            int actionDelay = sumActionDelayMillis(child);
            if (actionDelay > 0) {
                totalDelay += actionDelay;
                delayedActions++;
            }
        }

        if (bounds == null) {
            return null;
        }

        return new AttackBoundsData(bounds, averageDelay(totalDelay, delayedActions), new ArrayList<>(actionNames), weaponFile.toString());
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

    private static int sumActionDelayMillis(Element action) {
        if (action == null) {
            return 0;
        }

        int totalDelay = 0;
        for (Element child : getNamedChildren(action)) {
            if ("int".equals(child.getTagName()) && "delay".equals(child.getAttribute("name"))) {
                totalDelay += getIntAttribute(child, "value", 0);
                continue;
            }
            totalDelay += sumActionDelayMillis(child);
        }
        return totalDelay;
    }

    private static int averageDelay(int totalDelay, int delayedActions) {
        if (totalDelay <= 0 || delayedActions <= 0) {
            return 0;
        }
        return Math.max(1, totalDelay / delayedActions);
    }
}
