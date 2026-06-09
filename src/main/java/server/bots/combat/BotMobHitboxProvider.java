package server.bots.combat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
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

import static server.bots.combat.BotWzXml.findNamedChild;
import static server.bots.combat.BotWzXml.getIntAttribute;
import static server.bots.combat.BotWzXml.getIntValue;

public final class BotMobHitboxProvider {
    private static final Logger log = LoggerFactory.getLogger(BotMobHitboxProvider.class);
    private static final BotMobHitboxProvider instance = new BotMobHitboxProvider();

    // Sentinel stored in the cache when a mob has no usable hitbox frame. Using a sentinel rather
    // than leaving the key unmapped prevents repeated XML re-parsing and re-logging on every tick
    // for mobs we already know are unresolvable.
    private static final Rectangle UNRESOLVED_BOUNDS = new Rectangle(Integer.MIN_VALUE, 0, 0, 0);

    // Frame group fallback chain: body-pose groups only. Attack/hit frames can embed weapon
    // swing extents (e.g. mob 8500003's attack1 lt/rb covers a 130x100 weapon arc), so using
    // those would inflate the body rect. Flying mobs expose fly/0 instead of stand/0.
    private static final String[] FRAME_GROUP_FALLBACK = {"stand", "move", "fly"};

    private final Map<Integer, Rectangle> boundsByMobId = new ConcurrentHashMap<>();
    private volatile Path cachedMobRoot = null;

    public static BotMobHitboxProvider getInstance() {
        return instance;
    }

    public Rectangle getMobBounds(Monster mob) {
        if (mob == null) {
            return null;
        }

        return getMobBounds(mob.getId(), mob.getPosition(), mob.isFacingLeft());
    }

    public Rectangle getMobBounds(int mobId, Point position, boolean facingLeft) {
        ensureCurrentMobRoot();
        Rectangle modelBounds = boundsByMobId.computeIfAbsent(mobId, this::loadMobBounds);
        if (modelBounds == UNRESOLVED_BOUNDS) {
            return null;
        }

        return calculateWorldBounds(modelBounds, position, facingLeft);
    }

    private void ensureCurrentMobRoot() {
        Path currentMobRoot = WZFiles.MOB.getFile();
        Path previousMobRoot = cachedMobRoot;
        if (previousMobRoot != null && previousMobRoot.equals(currentMobRoot)) {
            return;
        }

        synchronized (boundsByMobId) {
            if (cachedMobRoot != null && cachedMobRoot.equals(currentMobRoot)) {
                return;
            }
            boundsByMobId.clear();
            cachedMobRoot = currentMobRoot;
        }
    }

    private Rectangle loadMobBounds(int mobId) {
        Path mobFile = WZFiles.MOB.getFile().resolve(String.format("%07d.img.xml", mobId));
        if (!Files.isRegularFile(mobFile)) {
            log.debug("Bot mob hitbox: no WZ file for mob {} — caching miss", mobId);
            return UNRESOLVED_BOUNDS;
        }

        Document document = parseXmlDocument(mobFile);
        if (document == null) {
            return UNRESOLVED_BOUNDS;
        }

        Rectangle bounds = loadFrameBounds(document.getDocumentElement());
        if (bounds == null) {
            log.debug("Bot mob hitbox: no lt/rb bounds on any of {} for mob {} — caching miss",
                    String.join(",", FRAME_GROUP_FALLBACK), mobId);
            return UNRESOLVED_BOUNDS;
        }
        return bounds;
    }

    private Rectangle loadFrameBounds(Element root) {
        Element linkedRoot = resolveLinkedRoot(root);
        for (String frameGroup : FRAME_GROUP_FALLBACK) {
            Element group = findNamedChild(linkedRoot, frameGroup);
            if (group == null) {
                continue;
            }
            Element frame = findNamedChild(group, "0");
            if (frame == null) {
                continue;
            }
            Rectangle bounds = toBounds(findNamedChild(frame, "lt"), findNamedChild(frame, "rb"));
            if (bounds != null) {
                return bounds;
            }
        }
        return null;
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

}
