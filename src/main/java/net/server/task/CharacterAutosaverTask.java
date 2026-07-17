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
package net.server.task;

import client.Character;
import config.YamlConfig;
import net.server.PlayerStorage;
import net.server.world.World;
import server.ThreadManager;
import server.monitoring.CharacterSaveDiagnostics.SaveReason;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.concurrent.TimeUnit.HOURS;

/**
 * @author Ronan
 */
public class CharacterAutosaverTask extends BaseTask implements Runnable {  // thanks Alex09 (Alex-0000) for noticing these runnable classes are tasks, "workers" runs them
    private static final long DEFAULT_WINDOW_MS = HOURS.toMillis(1);
    private static final long DEFAULT_DISPATCH_INTERVAL_MS = 250L;
    private static final int DEFAULT_MAX_DISPATCH_PER_RUN = 8;
    private static final Set<Integer> pendingCharacterIds = ConcurrentHashMap.newKeySet();
    private static final AtomicLong accepted = new AtomicLong();
    private static final AtomicLong coalesced = new AtomicLong();
    private static final AtomicLong backpressured = new AtomicLong();

    private final long dispatchIntervalMs;
    private final CharacterAutosaveCoordinator coordinator;

    @Override
    public void run() {
        if (!YamlConfig.config.server.USE_AUTOSAVE) {
            return;
        }

        PlayerStorage ps = wserv.getPlayerStorage();
        coordinator.run(ps);
    }

    public CharacterAutosaverTask(World world) {
        this(
                world,
                positiveLongProperty("cosmic.persistence.autosaveWindowMs", DEFAULT_WINDOW_MS),
                positiveLongProperty("cosmic.persistence.autosaveDispatchIntervalMs", DEFAULT_DISPATCH_INTERVAL_MS),
                positiveIntProperty("cosmic.persistence.autosaveMaxDispatchPerRun", DEFAULT_MAX_DISPATCH_PER_RUN));
    }

    CharacterAutosaverTask(World world, long windowMs, long dispatchIntervalMs, int maxDispatchPerRun) {
        super(world);
        this.dispatchIntervalMs = dispatchIntervalMs;
        this.coordinator = new CharacterAutosaveCoordinator(
                windowMs,
                maxDispatchPerRun,
                System::currentTimeMillis,
                CharacterAutosaveCoordinator.randomizedOrder(),
                CharacterAutosaverTask::submitAutosave);
    }

    public long dispatchIntervalMs() {
        return dispatchIntervalMs;
    }

    public static String runtimeDiagnostics() {
        return "autosaveDispatch pending=" + pendingCharacterIds.size()
                + " accepted=" + accepted.get()
                + " coalesced=" + coalesced.get()
                + " backpressured=" + backpressured.get();
    }

    private static CharacterAutosaveCoordinator.SubmissionResult submitAutosave(Character character) {
        int characterId = character.getId();
        if (!pendingCharacterIds.add(characterId)) {
            coalesced.incrementAndGet();
            return CharacterAutosaveCoordinator.SubmissionResult.COALESCED;
        }

        boolean submitted = ThreadManager.getInstance().newAutosaveTask(() -> {
            try {
                if (YamlConfig.config.server.USE_AUTOSAVE && character.isLoggedin()) {
                    character.saveCharToDB(false, SaveReason.AUTO_SAVE);
                }
            } finally {
                pendingCharacterIds.remove(characterId);
            }
        });
        if (!submitted) {
            pendingCharacterIds.remove(characterId);
            backpressured.incrementAndGet();
            return CharacterAutosaveCoordinator.SubmissionResult.FULL;
        }
        accepted.incrementAndGet();
        return CharacterAutosaveCoordinator.SubmissionResult.ACCEPTED;
    }

    private static long positiveLongProperty(String property, long defaultValue) {
        long configured = Long.getLong(property, defaultValue);
        return configured > 0L ? configured : defaultValue;
    }

    private static int positiveIntProperty(String property, int defaultValue) {
        int configured = Integer.getInteger(property, defaultValue);
        return configured > 0 ? configured : defaultValue;
    }
}
