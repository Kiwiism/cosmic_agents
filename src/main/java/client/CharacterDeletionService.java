package client;

import net.server.Server;
import net.server.world.Party;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.monitoring.SlowOperationLogger;
import tools.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class CharacterDeletionService {
    private static final Logger log = LoggerFactory.getLogger(CharacterDeletionService.class);

    private CharacterDeletionService() {
    }

    public enum Result {
        SUCCESS(0, "Character deleted."),
        NOT_FOUND(0x09, "Character could not be found."),
        ERROR(0x09, "Character could not be deleted."),
        GUILD_LEADER(0x16, "Character is currently a guild leader."),
        PENDING_WORLD_TRANSFER(0x1A, "Character has a pending world transfer."),
        FAMILY_WITH_MEMBERS(0x1D, "Character is still linked to other family members.");

        private final int packetStatus;
        private final String commandMessage;

        Result(int packetStatus, String commandMessage) {
            this.packetStatus = packetStatus;
            this.commandMessage = commandMessage;
        }

        public int getPacketStatus() {
            return packetStatus;
        }

        public String getCommandMessage() {
            return commandMessage;
        }

        public boolean isSuccess() {
            return this == SUCCESS;
        }
    }

    public static Result checkDeletionEligibility(int cid) {
        long startedNs = SlowOperationLogger.start();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT `world`, `guildid`, `guildrank`, `familyId` FROM characters WHERE id = ?");
             PreparedStatement ps2 = con.prepareStatement("SELECT COUNT(*) AS rowcount FROM worldtransfers WHERE `characterid` = ? AND completionTime IS NULL")) {
            ps.setInt(1, cid);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Result.NOT_FOUND;
                }

                int world = rs.getInt("world");
                int guildId = rs.getInt("guildid");
                int guildRank = rs.getInt("guildrank");
                int familyId = rs.getInt("familyId");

                if (guildId != 0 && guildRank <= 1) {
                    return Result.GUILD_LEADER;
                }

                if (familyId != -1) {
                    Family family = Server.getInstance().getWorld(world).getFamily(familyId);
                    if (family != null && family.getTotalMembers() > 1) {
                        return Result.FAMILY_WITH_MEMBERS;
                    }
                }
            }

            ps2.setInt(1, cid);
            try (ResultSet rs = ps2.executeQuery()) {
                rs.next();
                if (rs.getInt("rowcount") > 0) {
                    return Result.PENDING_WORLD_TRANSFER;
                }
            }

            return Result.SUCCESS;
        } catch (SQLException e) {
            log.error("Failed to validate chrId {} for deletion", cid, e);
            return Result.ERROR;
        } finally {
            SlowOperationLogger.warnIfSlow("character-delete-eligibility cid=" + cid, startedNs, 1_000);
        }
    }

    public static Result deleteCharacter(int cid, int senderAccId) {
        Result eligibility = checkDeletionEligibility(cid);
        if (!eligibility.isSuccess()) {
            return eligibility;
        }

        if (deleteCharacterInternal(cid, senderAccId)) {
            return Result.SUCCESS;
        }

        return Result.ERROR;
    }

    private static boolean deleteCharacterInternal(int cid, int senderAccId) {
        long startedNs = SlowOperationLogger.start();
        try {
            Client deleteClient = new Client(Client.Type.LOGIN, -1, "character-delete", null, 0, 1);
            Character chr = Character.loadCharFromDB(cid, deleteClient, false);
            deleteClient.setWorld(chr.getWorld());

            Integer partyid = chr.getWorldServer().getCharacterPartyid(cid);
            if (partyid != null) {
                deleteClient.setPlayer(chr);

                Party party = chr.getWorldServer().getParty(partyid);
                chr.setParty(party);
                chr.getMPC();
                chr.leaveParty();   // keep login deletion behaviour identical for party cleanup

                deleteClient.setPlayer(null);
            }

            return Character.deleteCharFromDB(chr, senderAccId);
        } catch (SQLException | RuntimeException ex) {
            log.error("Failed to delete chrId {}", cid, ex);
            return false;
        } finally {
            SlowOperationLogger.warnIfSlow("character-delete cid=" + cid, startedNs, 5_000);
        }
    }
}
