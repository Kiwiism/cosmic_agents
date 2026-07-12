package client.profile;

import client.BotClient;
import client.Character;
import client.Client;
import client.Disease;
import net.server.PlayerBuffValueHolder;
import net.server.Server;
import server.life.MobSkill;
import tools.Pair;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Canonical Cosmic loader/saver adapter for detached Partner profiles. */
public final class CosmicCharacterProfileRepository implements CharacterProfileRepository {
    public static final CosmicCharacterProfileRepository INSTANCE = new CosmicCharacterProfileRepository();

    private CosmicCharacterProfileRepository() {
    }

    @Override
    public Character loadDetached(int ownerCharacterId, int world, int channel) throws SQLException {
        return loadDetached(ownerCharacterId, world, channel, true);
    }

    @Override
    public Character loadDetachedForValidation(int ownerCharacterId, int world, int channel) throws SQLException {
        return loadDetached(ownerCharacterId, world, channel, false);
    }

    private Character loadDetached(int ownerCharacterId,
                                   int world,
                                   int channel,
                                   boolean consumeTransientState) throws SQLException {
        Client client = new BotClient(world, channel);
        Character profileHolder = Character.loadCharFromDB(
                ownerCharacterId, client, true, consumeTransientState);
        if (profileHolder == null) {
            throw new SQLException("Canonical profile " + ownerCharacterId + " could not be loaded");
        }
        client.setPlayer(profileHolder);
        client.setAccID(profileHolder.getAccountID());
        if (consumeTransientState) {
            restoreTransientState(profileHolder);
        }
        profileHolder.suspendProfileRuntimeTasks();
        return profileHolder;
    }

    @Override
    public void restoreTransientState(Character profileHolder) {
        if (profileHolder == null) {
            throw new IllegalArgumentException("Profile holder is required");
        }
        int ownerId = profileHolder.getProfileOwnerCharacterId();
        List<PlayerBuffValueHolder> buffs =
                Server.getInstance().getPlayerBuffStorage().getBuffsFromStorage(ownerId);
        if (buffs != null) {
            long now = Server.getInstance().getCurrentTime();
            List<Pair<Long, PlayerBuffValueHolder>> timedBuffs = new ArrayList<>(buffs.size());
            for (PlayerBuffValueHolder buff : buffs) {
                timedBuffs.add(new Pair<>(now - buff.usedTime, buff));
            }
            timedBuffs.sort((left, right) -> left.getLeft().compareTo(right.getLeft()));
            profileHolder.silentGiveBuffs(timedBuffs);
        }
        Map<Disease, Pair<Long, MobSkill>> diseases =
                Server.getInstance().getPlayerBuffStorage().getDiseasesFromStorage(ownerId);
        if (diseases != null) {
            profileHolder.silentApplyDiseases(diseases);
        }
    }

    @Override
    public void storeTransientStateForLogout(Character profileHolder) {
        if (profileHolder == null) {
            throw new IllegalArgumentException("Profile holder is required");
        }
        int ownerId = profileHolder.getProfileOwnerCharacterId();
        Server.getInstance().getPlayerBuffStorage().addBuffsToStorage(
                ownerId, profileHolder.getAllBuffs());
        Server.getInstance().getPlayerBuffStorage().addDiseasesToStorage(
                ownerId, profileHolder.getAllDiseases());
    }

    @Override
    public void saveCanonical(Character profileHolder) {
        if (profileHolder == null) {
            throw new IllegalArgumentException("Profile holder is required");
        }
        RuntimeException failure = null;
        try {
            profileHolder.saveCooldownsOrThrow();
        } catch (RuntimeException cooldownFailure) {
            failure = cooldownFailure;
        }
        try {
            profileHolder.saveCanonicalProfileOrThrow();
        } catch (RuntimeException profileFailure) {
            if (failure == null) {
                failure = profileFailure;
            } else {
                failure.addSuppressed(profileFailure);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
