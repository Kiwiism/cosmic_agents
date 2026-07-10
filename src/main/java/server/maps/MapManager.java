/*
    This file is part of the HeavenMS MapleStory Server
    Copyleft (L) 2016 - 2019 RonanLana

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation version 3 as published by
    the Free Software Foundation. You may not use, modify or distribute
    this program under any other version of the GNU Affero General Public
    License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package server.maps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripting.event.EventInstanceManager;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MapManager {
    @FunctionalInterface
    interface MapLoader {
        MapleMap load(int mapId, int world, int channel, EventInstanceManager event);
    }

    private static final Logger log = LoggerFactory.getLogger(MapManager.class);
    private static final long IDLE_MAP_DIAGNOSTIC_MS = 30 * 60 * 1000L;
    private static final long DORMANT_UPDATE_SKIP_MS = configuredLong("cosmic.maps.dormantSkipMillis", 60_000L);
    private static final long IDLE_MAP_UNLOAD_MS = configuredLong("cosmic.maps.idleUnloadMillis", 30 * 60 * 1000L);
    private static final boolean IDLE_MAP_UNLOAD_ENABLED = configuredBoolean("cosmic.maps.idleUnloadEnabled", false);
    private static final int HIGH_WATER_OBJECT_WARN = 500;
    private static final int HIGH_WATER_DROP_WARN = 200;
    private static final int HIGH_WATER_REACTOR_WARN = 100;
    private static final int HIGH_WATER_MONSTER_WARN = 200;

    private final int channel;
    private final int world;
    private final MapLoader mapLoader;
    private EventInstanceManager event;

    private final Map<Integer, MapleMap> maps = new HashMap<>();
    private final Map<Integer, MapHighWatermark> highWatermarks = new ConcurrentHashMap<>();
    private final AtomicLong skippedDormantUpdates = new AtomicLong();
    private final AtomicLong unloadedMaps = new AtomicLong();

    private final Lock mapsRLock;
    private final Lock mapsWLock;

    public MapManager(EventInstanceManager eim, int world, int channel) {
        this(eim, world, channel, MapFactory::loadMapFromWz);
    }

    MapManager(EventInstanceManager eim, int world, int channel, MapLoader mapLoader) {
        this.world = world;
        this.channel = channel;
        this.event = eim;
        this.mapLoader = mapLoader;

        ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        this.mapsRLock = readWriteLock.readLock();
        this.mapsWLock = readWriteLock.writeLock();
    }

    public MapleMap resetMap(int mapid) {
        MapleMap removed;
        mapsWLock.lock();
        try {
            removed = maps.remove(mapid);
            highWatermarks.remove(mapid);
        } finally {
            mapsWLock.unlock();
        }
        if (removed != null) {
            removed.dispose();
        }

        return getMap(mapid);
    }

    private synchronized MapleMap loadMapFromWz(int mapid, boolean cache) {
        MapleMap map;

        if (cache) {
            mapsRLock.lock();
            try {
                map = maps.get(mapid);
            } finally {
                mapsRLock.unlock();
            }

            if (map != null) {
                return map;
            }
        }

        map = mapLoader.load(mapid, world, channel, event);

        if (cache && map != null) {
            mapsWLock.lock();
            try {
                maps.put(mapid, map);
            } finally {
                mapsWLock.unlock();
            }
        }

        return map;
    }

    public MapleMap getMap(int mapid) {
        MapleMap map;

        mapsRLock.lock();
        try {
            map = maps.get(mapid);
        } finally {
            mapsRLock.unlock();
        }

        MapleMap result = (map != null) ? map : loadMapFromWz(mapid, true);
        if (result != null) {
            result.markAccessed();
        }
        return result;
    }

    public MapleMap getLoadedMap(int mapid) {
        mapsRLock.lock();
        try {
            return maps.get(mapid);
        } finally {
            mapsRLock.unlock();
        }
    }

    public MapleMap getDisposableMap(int mapid) {
        return loadMapFromWz(mapid, false);
    }

    public boolean isMapLoaded(int mapId) {
        mapsRLock.lock();
        try {
            return maps.containsKey(mapId);
        } finally {
            mapsRLock.unlock();
        }
    }

    public Map<Integer, MapleMap> getMaps() {
        mapsRLock.lock();
        try {
            return new HashMap<>(maps);
        } finally {
            mapsRLock.unlock();
        }
    }

    public int loadedMapCount() {
        mapsRLock.lock();
        try {
            return maps.size();
        } finally {
            mapsRLock.unlock();
        }
    }

    public int activeMapCount() {
        int count = 0;
        for (MapleMap map : getMaps().values()) {
            if (map.isActiveForMaintenance()) {
                count++;
            }
        }
        return count;
    }

    public int idleMapCandidateCount() {
        int count = 0;
        for (MapleMap map : getMaps().values()) {
            if (!map.isActiveForMaintenance() && map.getIdleTimeMillis() >= IDLE_MAP_DIAGNOSTIC_MS) {
                count++;
            }
        }
        return count;
    }

    public void updateMaps() {
        for (MapleMap map : getMaps().values()) {
            long startedNs = server.monitoring.SlowOperationLogger.start();
            boolean active = map.isActiveForMaintenance();
            if (!active && map.shouldSkipDormantUpdate(DORMANT_UPDATE_SKIP_MS)) {
                skippedDormantUpdates.incrementAndGet();
                boolean unloaded = false;
                if (IDLE_MAP_UNLOAD_ENABLED && map.isSafeToUnload(IDLE_MAP_UNLOAD_MS)) {
                    unloaded = unloadIfStillIdle(map, IDLE_MAP_UNLOAD_MS);
                }
                if (!unloaded) {
                    recordHighWatermarks(map);
                }
                continue;
            }
            if (!active && map.getIdleTimeMillis() >= IDLE_MAP_DIAGNOSTIC_MS) {
                log.debug("Idle map tick candidate world={} channel={} map={} idleMs={} objects={}",
                        world, channel, map.getId(), map.getIdleTimeMillis(), map.getLoadedObjectCount());
            }
            map.respawn();
            map.mobMpRecovery();
            recordHighWatermarks(map);
            server.monitoring.SlowOperationLogger.warnIfSlow("map-update map=" + map.getId() + " active=" + active,
                    startedNs, 250);
        }
    }

    boolean unloadIfStillIdle(MapleMap map, long idleThresholdMillis) {
        boolean removed = false;
        mapsWLock.lock();
        try {
            if (maps.get(map.getId()) == map && map.isSafeToUnload(idleThresholdMillis)) {
                maps.remove(map.getId());
                highWatermarks.remove(map.getId());
                removed = true;
            }
        } finally {
            mapsWLock.unlock();
        }
        if (removed) {
            map.dispose();
            long total = unloadedMaps.incrementAndGet();
            log.info("Unloaded idle map world={} channel={} map={} totalUnloaded={}",
                    world, channel, map.getId(), total);
        }
        return removed;
    }

    public long skippedDormantUpdateCount() {
        return skippedDormantUpdates.get();
    }

    public long unloadedMapCount() {
        return unloadedMaps.get();
    }

    private void recordHighWatermarks(MapleMap map) {
        int objectCount = map.getLoadedObjectCount();
        int dropCount = map.getDroppedItemCount();
        int reactorCount = map.countReactors();
        int monsterCount = map.getSpawnedMonsterCount();

        MapHighWatermark watermark = highWatermarks.computeIfAbsent(map.getId(), ignored -> new MapHighWatermark());
        boolean warn = false;
        if (objectCount > watermark.objects) {
            watermark.objects = objectCount;
            warn |= objectCount >= HIGH_WATER_OBJECT_WARN;
        }
        if (dropCount > watermark.drops) {
            watermark.drops = dropCount;
            warn |= dropCount >= HIGH_WATER_DROP_WARN;
        }
        if (reactorCount > watermark.reactors) {
            watermark.reactors = reactorCount;
            warn |= reactorCount >= HIGH_WATER_REACTOR_WARN;
        }
        if (monsterCount > watermark.monsters) {
            watermark.monsters = monsterCount;
            warn |= monsterCount >= HIGH_WATER_MONSTER_WARN;
        }

        if (warn) {
            log.warn("Map high-water world={} channel={} map={} objects={} drops={} reactors={} monsters={}",
                    world, channel, map.getId(), watermark.objects, watermark.drops, watermark.reactors, watermark.monsters);
        }
    }

    public void dispose() {
        Map<Integer, MapleMap> disposing;
        mapsWLock.lock();
        try {
            disposing = new HashMap<>(maps);
            maps.clear();
            highWatermarks.clear();
        } finally {
            mapsWLock.unlock();
        }
        for (MapleMap map : disposing.values()) {
            map.dispose();
        }

        this.event = null;
    }

    private static boolean configuredBoolean(String propertyName, boolean defaultValue) {
        String value = System.getProperty(propertyName);
        if (value == null || value.isBlank()) {
            value = System.getenv(propertyName.toUpperCase().replace('.', '_'));
        }
        return value == null || value.isBlank() ? defaultValue : Boolean.parseBoolean(value);
    }

    private static long configuredLong(String propertyName, long defaultValue) {
        String value = System.getProperty(propertyName);
        if (value == null || value.isBlank()) {
            value = System.getenv(propertyName.toUpperCase().replace('.', '_'));
        }
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Math.max(0L, Long.parseLong(value));
        } catch (NumberFormatException e) {
            log.warn("Ignoring invalid map lifecycle setting {}={}", propertyName, value);
            return defaultValue;
        }
    }

    private static final class MapHighWatermark {
        private int objects;
        private int drops;
        private int reactors;
        private int monsters;
    }

}
