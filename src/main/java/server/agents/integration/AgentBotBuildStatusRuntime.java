package server.agents.integration;

import server.agents.capabilities.equipment.AgentEquipRecommendation;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatStatusRuntime;
import server.agents.capabilities.build.AgentBuildService;
import server.agents.runtime.AgentRuntimeEntry;
import server.bots.BotEntry;
import server.agents.capabilities.equipment.AgentEquipmentService;
import server.agents.capabilities.trade.AgentOfferService;

import java.util.List;

/**
 * Temporary Agent-owned bridge for build/status side effects while the build,
 * equipment, and offer implementations still live in the bot runtime.
 */
public final class AgentBotBuildStatusRuntime {
    private AgentBotBuildStatusRuntime() {
    }

    public static void checkBuildStatus(AgentRuntimeEntry entry, Character bot) {
        AgentChatStatusRuntime.checkStatus(
                AgentBotStatusRuntime.statusCheckState(entry),
                statusCheckActions(entry, bot));
    }

    public static AgentChatStatusRuntime.StatusCheckActions statusCheckActions(AgentRuntimeEntry entry, Character bot) {
        BotEntry botEntry = asBotEntry(entry);
        return new AgentChatStatusRuntime.StatusCheckActions() {
            @Override
            public String buildJobPrompt() {
                return AgentBuildService.buildJobPrompt(botEntry, bot);
            }

            @Override
            public String buildSpVariantPrompt() {
                return AgentBuildService.buildSpVariantPrompt(botEntry, bot);
            }

            @Override
            public String buildApPrompt() {
                return AgentBuildService.buildApPrompt(botEntry, bot);
            }

            @Override
            public void queueReply(String message) {
                AgentBotReplyRuntime.queueReply(entry, message);
            }

            @Override
            public void autoAssignSp() {
                AgentBuildService.autoAssignSp(botEntry, bot);
            }

            @Override
            public void autoAssignAp() {
                AgentBuildService.autoAssignAp(botEntry, bot);
            }

            @Override
            public void maybeSuggestRecommendedGear() {
                Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
                AgentChatStatusRuntime.maybeSuggestGear(
                        AgentBotStatusRuntime.gearSuggestionState(entry),
                        AgentChatStatusRuntime.gearSuggestionActions(
                                owner != null,
                                () -> AgentOfferService.offerBestRecommendedGear(botEntry, bot, owner)),
                        System.currentTimeMillis());
            }

            @Override
            public void maybeSuggestGearToSiblings() {
                Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
                AgentChatStatusRuntime.maybeSuggestGear(
                        AgentBotStatusRuntime.gearSuggestionState(entry),
                        AgentChatStatusRuntime.gearSuggestionActions(
                                owner != null,
                                () -> AgentOfferService.offerBestGearToSibling(botEntry, bot)),
                        System.currentTimeMillis());
            }

            @Override
            public boolean canOfferSpawnUpgrade() {
                return AgentBotRuntimeIdentityRuntime.owner(entry) != null
                        && !AgentChatStatusRuntime.isOwnerIdle(AgentBotStatusRuntime.statusState(entry))
                        && !AgentBotPendingActionStateRuntime.hasPendingAction(entry)
                        && !AgentOfferService.hasPendingOffer(entry);
            }

            @Override
            public void offerSpawnUpgradeIfAvailable() {
                Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
                List<AgentEquipRecommendation> recs =
                        AgentEquipmentService.findRecommendedEquips(bot, owner);
                if (!recs.isEmpty()) {
                    AgentOfferService.notifyOwnerGainedEquip(botEntry, bot, recs.get(0).candidate());
                }
            }
        };
    }

    private static BotEntry asBotEntry(AgentRuntimeEntry entry) {
        return (BotEntry) entry;
    }
}
