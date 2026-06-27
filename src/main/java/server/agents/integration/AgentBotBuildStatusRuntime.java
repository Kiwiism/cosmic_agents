package server.agents.integration;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatStatusRuntime;
import server.bots.BotBuildManager;
import server.bots.BotEntry;
import server.bots.BotEquipManager;
import server.bots.BotOfferManager;

import java.util.List;

/**
 * Temporary Agent-owned bridge for build/status side effects while the build,
 * equipment, and offer implementations still live in the bot runtime.
 */
public final class AgentBotBuildStatusRuntime {
    private AgentBotBuildStatusRuntime() {
    }

    public static void checkBuildStatus(BotEntry entry, Character bot) {
        AgentChatStatusRuntime.checkStatus(
                AgentBotStatusRuntime.statusCheckState(entry),
                statusCheckActions(entry, bot));
    }

    public static AgentChatStatusRuntime.StatusCheckActions statusCheckActions(BotEntry entry, Character bot) {
        return new AgentChatStatusRuntime.StatusCheckActions() {
            @Override
            public String buildJobPrompt() {
                return BotBuildManager.buildJobPrompt(entry, bot);
            }

            @Override
            public String buildSpVariantPrompt() {
                return BotBuildManager.buildSpVariantPrompt(entry, bot);
            }

            @Override
            public String buildApPrompt() {
                return BotBuildManager.buildApPrompt(entry, bot);
            }

            @Override
            public void queueReply(String message) {
                AgentBotReplyRuntime.queueReply(entry, message);
            }

            @Override
            public void autoAssignSp() {
                BotBuildManager.autoAssignSp(entry, bot);
            }

            @Override
            public void autoAssignAp() {
                BotBuildManager.autoAssignAp(entry, bot);
            }

            @Override
            public void maybeSuggestRecommendedGear() {
                AgentChatStatusRuntime.maybeSuggestGear(
                        AgentBotStatusRuntime.gearSuggestionState(entry),
                        AgentChatStatusRuntime.gearSuggestionActions(
                                entry.owner() != null,
                                () -> BotOfferManager.offerBestRecommendedGear(entry, bot, entry.owner())),
                        System.currentTimeMillis());
            }

            @Override
            public void maybeSuggestGearToSiblings() {
                AgentChatStatusRuntime.maybeSuggestGear(
                        AgentBotStatusRuntime.gearSuggestionState(entry),
                        AgentChatStatusRuntime.gearSuggestionActions(
                                entry.owner() != null,
                                () -> BotOfferManager.offerBestGearToSibling(entry, bot)),
                        System.currentTimeMillis());
            }

            @Override
            public boolean canOfferSpawnUpgrade() {
                return entry.owner() != null
                        && !AgentChatStatusRuntime.isOwnerIdle(AgentBotStatusRuntime.statusState(entry))
                        && entry.pendingAction() == null
                        && !BotOfferManager.hasPendingOffer(entry);
            }

            @Override
            public void offerSpawnUpgradeIfAvailable() {
                Character owner = entry.owner();
                List<BotEquipManager.EquipRecommendation> recs =
                        BotEquipManager.findRecommendedEquips(bot, owner);
                if (!recs.isEmpty()) {
                    BotOfferManager.notifyOwnerGainedEquip(entry, bot, recs.get(0).candidate());
                }
            }
        };
    }
}
