package server.bots.llm;

import client.Character;
import net.server.world.Party;
import net.server.world.PartyCharacter;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.bots.BotEntry;

public enum SenderRelation {
    OWNER, PARTY, STRANGER;

    public static SenderRelation resolve(BotEntry entry, Character sender) {
        Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
        if (entry == null || bot == null || sender == null) {
            return STRANGER;
        }
        Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
        if (owner != null && owner.getId() == sender.getId()) {
            return OWNER;
        }
        Party party = bot.getParty();
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
