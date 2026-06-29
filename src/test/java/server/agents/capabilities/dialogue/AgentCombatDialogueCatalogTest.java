package server.agents.capabilities.dialogue;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class AgentCombatDialogueCatalogTest {
    @Test
    void shouldExposeLegacyCombatDeathReplies() {
        assertEquals(List.of(
                "oops im dead", "gg", "rip me", "oww", "i died lol",
                "welp", "ouchh", "nooo", "ok i died", "i'll be right back"),
                AgentDialogueCatalog.combatDeathReplies());
    }

    @Test
    void shouldExposeLegacyCombatSupplyReplies() {
        assertEquals(List.of(
                "running low on ammo",
                "ammo getting low",
                "not much ammo left",
                "gonna need more ammo soon"),
                AgentDialogueCatalog.combatAmmoLowReplies());
        assertEquals(List.of(
                "out of ammo! heading back",
                "no ammo left, coming to you",
                "need ammo!! walking back",
                "im out of ammo, heading to you"),
                AgentDialogueCatalog.combatAmmoOutReplies());
        assertEquals(List.of(
                "out of MP pots! heading back",
                "no MP pots left, coming to you",
                "need MP pots!! walking back",
                "im out of MP pots, heading to you"),
                AgentDialogueCatalog.combatMpPotsOutReplies());
    }
}
