package client.command.commands.gm5;

import client.Character;
import client.Client;
import client.command.Command;
import config.YamlConfig;
import server.agents.capabilities.combat.AgentMobReactionMetrics;

public class AgentMobDebugCommand extends Command {
    {
        setDescription("Show observed Agent mob-reaction counters and feature flags.");
    }

    @Override
    public void execute(Client client, String[] params) {
        Character player = client.getPlayer();
        boolean reactionEnabled = YamlConfig.config.agents != null
                && YamlConfig.config.agents.combat != null
                && YamlConfig.config.agents.combat.observedMobReaction != null
                && YamlConfig.config.agents.combat.observedMobReaction.enabled;
        boolean aggroEnabled = YamlConfig.config.agents != null
                && YamlConfig.config.agents.combat != null
                && YamlConfig.config.agents.combat.lastHitAggro != null
                && YamlConfig.config.agents.combat.lastHitAggro.enabled;
        long timeoutMs = YamlConfig.config.agents == null
                || YamlConfig.config.agents.combat == null
                || YamlConfig.config.agents.combat.lastHitAggro == null
                ? 10_000L : YamlConfig.config.agents.combat.lastHitAggro.targetTimeoutMs;

        player.dropMessage(6, "Agent mob response: observedReaction=" + reactionEnabled
                + " lastHitAggro=" + aggroEnabled + " targetTimeoutMs=" + timeoutMs);
        player.dropMessage(6, AgentMobReactionMetrics.snapshot().summary());
    }
}
