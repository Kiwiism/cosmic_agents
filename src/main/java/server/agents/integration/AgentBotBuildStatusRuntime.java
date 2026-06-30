package server.agents.integration;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatStatusRuntime;
import server.agents.capabilities.build.AgentBuildService;
import server.bots.BotEntry;
import server.bots.BotEquipManager;
import server.agents.capabilities.trade.AgentOfferService;

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
                return AgentBuildService.buildJobPrompt(entry, bot);
            }

            @Override
            public String buildSpVariantPrompt() {
                return AgentBuildService.buildSpVariantPrompt(entry, bot);
            }

            @Override
            public String buildApPrompt() {
                return AgentBuildService.buildApPrompt(entry, bot);
            }

            @Override
            public void queueReply(String message) {
                AgentBotBuildReplyRuntime.queueReply(entry, message);
            }

            @Override
            public void autoAssignSp() {
                AgentBuildService.autoAssignSp(entry, bot);
            }

            @Override
            public void autoAssignAp() {
                AgentBuildService.autoAssignAp(entry, bot);
            }

            @Override
            public void maybeSuggestRecommendedGear() {
                AgentChatStatusRuntime.maybeSuggestGear(
                        AgentBotStatusRuntime.gearSuggestionState(entry),
                        AgentChatStatusRuntime.gearSuggestionActions(
                                entry.owner() != null,
                                () -> AgentOfferService.offerBestRecommendedGear(entry, bot, entry.owner())),
                        System.currentTimeMillis());
            }

            @Override
            public void maybeSuggestGearToSiblings() {
                AgentChatStatusRuntime.maybeSuggestGear(
                        AgentBotStatusRuntime.gearSuggestionState(entry),
                        AgentChatStatusRuntime.gearSuggestionActions(
                                entry.owner() != null,
                                () -> AgentOfferService.offerBestGearToSibling(entry, bot)),
                        System.currentTimeMillis());
            }

            @Override
            public boolean canOfferSpawnUpgrade() {
                return entry.owner() != null
                        && !AgentChatStatusRuntime.isOwnerIdle(AgentBotStatusRuntime.statusState(entry))
                        && !AgentBotPendingActionStateRuntime.hasPendingAction(entry)
                        && !AgentOfferService.hasPendingOffer(entry);
            }

            @Override
            public void offerSpawnUpgradeIfAvailable() {
                Character owner = entry.owner();
                List<BotEquipManager.EquipRecommendation> recs =
                        BotEquipManager.findRecommendedEquips(bot, owner);
                if (!recs.isEmpty()) {
                    AgentOfferService.notifyOwnerGainedEquip(entry, bot, recs.get(0).candidate());
                }
            }
        };
    }
}
