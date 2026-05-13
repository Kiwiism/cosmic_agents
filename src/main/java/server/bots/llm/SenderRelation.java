package server.bots.llm;

import client.Character;
import net.server.world.Party;
import net.server.world.PartyCharacter;
import server.bots.BotEntry;

public enum SenderRelation {
    OWNER, PARTY, STRANGER;

    public static SenderRelation resolve(BotEntry entry, Character sender) {
        if (entry == null || entry.getBot() == null || sender == null) {
            return STRANGER;
        }
        Character owner = entry.getOwner();
        if (owner != null && owner.getId() == sender.getId()) {
            return OWNER;
        }
        Party party = entry.getBot().getParty();
        if (party != null) {
            for (PartyCharacter member : party.getMembers()) {
                if (member.getId() == sender.getId()) {
                    return PARTY;
                }
            }
        }
        return STRANGER;
    }
}
