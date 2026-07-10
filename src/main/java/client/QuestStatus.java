/*
	This file is part of the OdinMS Maple Story Server
    Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
		       Matthias Butz <matze@odinms.de>
		       Jan Christian Meyer <vimes@odinms.de>

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
package client;

import server.quest.Quest;
import tools.StringUtil;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author Matze
 */
public class QuestStatus {
    public enum Status {
        UNDEFINED(-1),
        NOT_STARTED(0),
        STARTED(1),
        COMPLETED(2);
        final int status;

        Status(int id) {
            status = id;
        }

        public int getId() {
            return status;
        }

        public static Status getById(int id) {
            for (Status l : Status.values()) {
                if (l.getId() == id) {
                    return l;
                }
            }
            return null;
        }
    }

    private final short questID;
    private Status status;
    //private boolean updated;   //maybe this can be of use for someone?
    private final Map<Integer, String> progress = new LinkedHashMap<>();
    private final List<Integer> medalProgress = new LinkedList<>();
    private int npc;
    private long completionTime, expirationTime;
    private int forfeited = 0, completed = 0;
    private String customData;
    private Runnable persistenceDirtyMarker = () -> { };

    void setPersistenceDirtyMarker(Runnable persistenceDirtyMarker) {
        this.persistenceDirtyMarker = Objects.requireNonNull(persistenceDirtyMarker);
    }

    private void markPersistenceDirty() {
        persistenceDirtyMarker.run();
    }

    public QuestStatus(Quest quest, Status status) {
        this.questID = quest.getId();
        this.setStatus(status);
        this.completionTime = System.currentTimeMillis();
        this.expirationTime = 0;
        //this.updated = true;
        if (status == Status.STARTED) {
            registerMobs();
        }
    }

    public QuestStatus(Quest quest, Status status, int npc) {
        this.questID = quest.getId();
        this.setStatus(status);
        this.setNpc(npc);
        this.completionTime = System.currentTimeMillis();
        this.expirationTime = 0;
        //this.updated = true;
        if (status == Status.STARTED) {
            registerMobs();
        }
    }

    public Quest getQuest() {
        return Quest.getInstance(questID);
    }

    public short getQuestID() {
        return questID;
    }

    public synchronized Status getStatus() {
        return status;
    }

    public final synchronized void setStatus(Status status) {
        if (this.status != status) {
            this.status = status;
            markPersistenceDirty();
        }
    }
    
    /*
    public boolean wasUpdated() {
        return updated;
    }
    
    private void setUpdated() {
        this.updated = true;
    }
    
    public void resetUpdated() {
        this.updated = false;
    }
    */

    public synchronized int getNpc() {
        return npc;
    }

    public final synchronized void setNpc(int npc) {
        if (this.npc != npc) {
            this.npc = npc;
            markPersistenceDirty();
        }
    }

    private void registerMobs() {
        for (int i : Quest.getInstance(questID).getRelevantMobs()) {
            progress.put(i, "000");
        }
        //this.setUpdated();
    }

    public synchronized boolean addMedalMap(int mapid) {
        if (medalProgress.contains(mapid)) {
            return false;
        }
        medalProgress.add(mapid);
        markPersistenceDirty();
        //this.setUpdated();
        return true;
    }

    public synchronized int getMedalProgress() {
        return medalProgress.size();
    }

    public synchronized List<Integer> getMedalMaps() {
        return Collections.unmodifiableList(new LinkedList<>(medalProgress));
    }

    public synchronized boolean progress(int id) {
        String currentStr = progress.get(id);
        if (currentStr == null) {
            return false;
        }

        int current = Integer.parseInt(currentStr);
        if (current >= this.getQuest().getMobAmountNeeded(id)) {
            return false;
        }

        String str = StringUtil.getLeftPaddedStr(Integer.toString(++current), '0', 3);
        progress.put(id, str);
        markPersistenceDirty();
        //this.setUpdated();
        return true;
    }

    public synchronized void setProgress(int id, String pr) {
        boolean changed = !progress.containsKey(id) || !Objects.equals(progress.get(id), pr);
        progress.put(id, pr);
        if (changed) {
            markPersistenceDirty();
        }
        //this.setUpdated();
    }

    public synchronized boolean madeProgress() {
        return progress.size() > 0;
    }

    public synchronized String getProgress(int id) {
        String ret = progress.get(id);
        if (ret == null) {
            return "";
        } else {
            return ret;
        }
    }

    public synchronized void resetProgress(int id) {
        setProgress(id, "000");
    }

    public synchronized void resetAllProgress() {
        boolean changed = false;
        for (Map.Entry<Integer, String> entry : progress.entrySet()) {
            if (!"000".equals(entry.getValue())) {
                entry.setValue("000");
                changed = true;
            }
        }
        if (changed) {
            markPersistenceDirty();
        }
    }

    public synchronized Map<Integer, String> getProgress() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(progress));
    }

    public short getInfoNumber() {
        Quest q = this.getQuest();
        Status s = this.getStatus();

        return q.getInfoNumber(s);
    }

    public String getInfoEx(int index) {
        Quest q = this.getQuest();
        Status s = this.getStatus();

        return q.getInfoEx(s, index);
    }

    public List<String> getInfoEx() {
        Quest q = this.getQuest();
        Status s = this.getStatus();

        return q.getInfoEx(s);
    }

    public synchronized long getCompletionTime() {
        return completionTime;
    }

    public synchronized void setCompletionTime(long completionTime) {
        if (this.completionTime != completionTime) {
            this.completionTime = completionTime;
            markPersistenceDirty();
        }
    }

    public synchronized long getExpirationTime() {
        return expirationTime;
    }

    public synchronized void setExpirationTime(long expirationTime) {
        if (this.expirationTime != expirationTime) {
            this.expirationTime = expirationTime;
            markPersistenceDirty();
        }
    }

    public synchronized int getForfeited() {
        return forfeited;
    }

    public synchronized int getCompleted() {
        return completed;
    }

    public synchronized void setForfeited(int forfeited) {
        if (forfeited >= this.forfeited) {
            if (this.forfeited != forfeited) {
                this.forfeited = forfeited;
                markPersistenceDirty();
            }
        } else {
            throw new IllegalArgumentException("Can't set forfeits to something lower than before.");
        }
    }

    public synchronized void setCompleted(int completed) {
        if (completed >= this.completed) {
            if (this.completed != completed) {
                this.completed = completed;
                markPersistenceDirty();
            }
        } else {
            throw new IllegalArgumentException("Can't set completes to something lower than before.");
        }
    }

    public final synchronized void setCustomData(final String customData) {
        if (!Objects.equals(this.customData, customData)) {
            this.customData = customData;
            markPersistenceDirty();
        }
    }

    public final synchronized String getCustomData() {
        return customData;
    }

    public synchronized String getProgressData() {
        StringBuilder str = new StringBuilder();
        for (String ps : progress.values()) {
            str.append(ps);
        }
        return str.toString();
    }

    public synchronized PersistenceSnapshot persistenceSnapshot() {
        return new PersistenceSnapshot(questID, status.getId(), completionTime, expirationTime,
                forfeited, completed, new LinkedHashMap<>(progress), new LinkedList<>(medalProgress));
    }

    static QuestStatus fromPersistenceSnapshot(Quest quest, PersistenceSnapshot snapshot) {
        QuestStatus restored = new QuestStatus(quest, Status.getById(snapshot.status()));
        restored.completionTime = snapshot.completionTime();
        restored.expirationTime = snapshot.expirationTime();
        restored.forfeited = snapshot.forfeited();
        restored.completed = snapshot.completed();
        restored.progress.clear();
        restored.progress.putAll(snapshot.progress());
        restored.medalProgress.clear();
        restored.medalProgress.addAll(snapshot.medalMaps());
        return restored;
    }

    public record PersistenceSnapshot(short questId, int status, long completionTime,
                                      long expirationTime, int forfeited, int completed,
                                      Map<Integer, String> progress, List<Integer> medalMaps) {
        public PersistenceSnapshot {
            progress = Collections.unmodifiableMap(new LinkedHashMap<>(progress));
            medalMaps = Collections.unmodifiableList(new LinkedList<>(medalMaps));
        }
    }
}
