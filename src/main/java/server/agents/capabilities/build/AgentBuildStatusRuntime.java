package server.agents.capabilities.build;

import server.agents.capabilities.equipment.AgentEquipRecommendation;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatStatusRuntime;
import server.agents.integration.AgentReplyRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.capabilities.dialogue.AgentPendingActionStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.capabilities.dialogue.AgentStatusStateRuntime;
import server.agents.capabilities.equipment.AgentEquipmentService;
import server.agents.capabilities.trade.AgentOfferService;

import java.util.List;

/**
 * Agent-owned bridge for build/status side effects and recommendation checks.
 */
public final class AgentBuildStatusRuntime {
    private AgentBuildStatusRuntime() {
    }

    public static void checkBuildStatus(AgentRuntimeEntry entry, Character bot) {
        if (entry.isPartnerManaged()) {
            return;
        }
        AgentChatStatusRuntime.checkStatus(
                AgentStatusStateRuntime.statusCheckState(entry),
                statusCheckActions(entry, bot));
    }

    public static AgentChatStatusRuntime.StatusCheckActions statusCheckActions(AgentRuntimeEntry entry, Character bot) {
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
                AgentReplyRuntime.queueReply(entry, message);
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
                Character owner = AgentRuntimeIdentityRuntime.owner(entry);
                AgentChatStatusRuntime.maybeSuggestGear(
                        AgentStatusStateRuntime.gearSuggestionState(entry),
                        AgentChatStatusRuntime.gearSuggestionActions(
                                owner != null,
                                () -> AgentOfferService.offerBestRecommendedGear(entry, bot, owner)),
                        System.currentTimeMillis());
            }

            @Override
            public void maybeSuggestGearToSiblings() {
                Character owner = AgentRuntimeIdentityRuntime.owner(entry);
                AgentChatStatusRuntime.maybeSuggestGear(
                        AgentStatusStateRuntime.gearSuggestionState(entry),
                        AgentChatStatusRuntime.gearSuggestionActions(
                                owner != null,
                                () -> AgentOfferService.offerBestGearToSibling(entry, bot)),
                        System.currentTimeMillis());
            }

            @Override
            public boolean canOfferSpawnUpgrade() {
                return AgentRuntimeIdentityRuntime.owner(entry) != null
                        && !AgentChatStatusRuntime.isOwnerIdle(AgentStatusStateRuntime.statusState(entry))
                        && !AgentPendingActionStateRuntime.hasPendingAction(entry)
                        && !AgentOfferService.hasPendingOffer(entry);
            }

            @Override
            public void offerSpawnUpgradeIfAvailable() {
                Character owner = AgentRuntimeIdentityRuntime.owner(entry);
                List<AgentEquipRecommendation> recs =
                        AgentEquipmentService.findRecommendedEquips(bot, owner);
                if (!recs.isEmpty()) {
                    AgentOfferService.notifyOwnerGainedEquip(entry, bot, recs.get(0).candidate());
                }
            }
        };
    }
}
