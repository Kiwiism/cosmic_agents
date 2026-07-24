package server.agents.capabilities.dialogue;

import client.Character;
import server.agents.capabilities.contracts.AgentResourceCategory;
import server.agents.capabilities.contracts.AgentSupplyUrgency;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.integration.AgentPacketGatewayRuntime;
import server.agents.integration.AgentRelationshipRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.progression.events.AgentProgressionDialogueReactionService;
import server.agents.resources.events.AgentResourceDialogueReactionService;
import server.agents.operations.events.AgentOperationalDialogueReactionService;
import client.Job;

import java.util.List;

/** Live Cosmic projection boundary for observer-gated dialogue intents. */
public final class AgentDialogueProjectionRuntime {
    private AgentDialogueProjectionRuntime() {
    }

    public static boolean hasAudience(AgentRuntimeEntry entry, int agentId,
                                      AgentDialogueAudience audience) {
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (agent == null || agent.getId() != agentId || audience == null) {
            return false;
        }
        Character interactionTarget = AgentRelationshipRuntime.interactionTarget(entry);
        return switch (audience) {
            case NEARBY_REAL_PLAYER -> agent.getMap() != null
                    && AgentMapGatewayRuntime.map().isObservedByPlayer(agent.getMap());
            case DIRECT_PLAYER -> isRealPlayer(interactionTarget);
            case PARTY_REAL_PLAYERS -> false;
            case OPERATOR_ONLY -> isRealPlayer(interactionTarget) && interactionTarget.isGM();
        };
    }

    public static void project(AgentRuntimeEntry entry, AgentDialogueIntentEvent intent) {
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (agent == null || agent.getId() != intent.agentId()) {
            return;
        }
        String message = render(intent);
        if (!message.isBlank()) {
            AgentPacketGatewayRuntime.packets().broadcastChatText(
                    agent,
                    AgentChatTextSanitizer.sanitize(message),
                    false,
                    0);
        }
    }

    static String render(AgentDialogueIntentEvent intent) {
        if (AgentProgressionDialogueReactionService.LEVEL_INTENT.equals(intent.intentKey())) {
            return "level " + intent.parameters().getOrDefault("level", "") + "!";
        }
        if (AgentProgressionDialogueReactionService.JOB_INTENT.equals(intent.intentKey())) {
            try {
                Job job = Job.getById(Integer.parseInt(intent.parameters().getOrDefault("jobId", "-1")));
                return job == null ? "job advancement complete!"
                        : "job advancement complete - "
                        + AgentDialogueReportFormatter.jobDisplayName(job) + "!";
            } catch (NumberFormatException ignored) {
                return "job advancement complete!";
            }
        }
        if (AgentProgressionDialogueReactionService.QUEST_INTENT.equals(intent.intentKey())) {
            return "quest complete!";
        }
        if (AgentResourceDialogueReactionService.INVENTORY_FULL_INTENT.equals(intent.intentKey())) {
            String inventoryType = intent.parameters().getOrDefault("inventoryType", "").toLowerCase();
            return inventoryType.isBlank() ? "inventory is full!" : inventoryType + " inventory is full!";
        }
        if (AgentResourceDialogueReactionService.SCROLL_INTENT.equals(intent.intentKey())) {
            return switch (intent.parameters().getOrDefault("result", "")) {
                case "SUCCESS" -> "the scroll worked!";
                case "CURSE" -> "the item was destroyed...";
                case "FAIL" -> "the scroll failed.";
                default -> "";
            };
        }
        if (AgentOperationalDialogueReactionService.LIFE_STATE_INTENT.equals(intent.intentKey())) {
            return switch (intent.parameters().getOrDefault("state", "")) {
                case "DEAD" -> AgentDialogueSelector.randomReply(AgentDialogueCatalog.combatDeathReplies());
                case "ALIVE" -> "back!";
                default -> "";
            };
        }
        if (AgentTownLifeDialogueReactionService.SOCIAL_INTENT.equals(intent.intentKey())) {
            return variant(intent, List.of(
                    "nice day to hang around town.",
                    "taking a break before heading out.",
                    "this place is pretty lively today.",
                    "just checking out the harbor."));
        }
        if (AgentTownLifeDialogueReactionService.SPARRING_INTENT.equals(intent.intentKey())) {
            return variant(intent, List.of(
                    "wanna practice a little?",
                    "just warming up.",
                    "that was close!",
                    "okay, one more swing."));
        }
        if (AgentTownLifeDialogueReactionService.ARRIVAL_INTENT.equals(intent.intentKey())) {
            return variant(intent, List.of(
                    "I've reached Lith Harbor. I'll look around town for a while.",
                    "Made it to Victoria Island. Time to explore Lith Harbor.",
                    "I'm going to finish my errand, then spend some time around town.",
                    "The ship made it! I'll head into Lith Harbor and see what's happening."));
        }
        if (!AgentSupplyDialogueReactionService.INTENT_KEY.equals(intent.intentKey())) {
            return "";
        }
        try {
            AgentResourceCategory category = AgentResourceCategory.valueOf(
                    intent.parameters().getOrDefault("category", ""));
            AgentSupplyUrgency urgency = AgentSupplyUrgency.valueOf(
                    intent.parameters().getOrDefault("urgency", ""));
            List<String> replies = switch (category) {
                case HP_POTION -> AgentDialogueCatalog.potRequestHpReplies();
                case MP_POTION -> AgentDialogueCatalog.potRequestMpReplies();
                case ARROW -> AgentDialogueCatalog.arrowRequestReplies();
                case CROSSBOW_BOLT -> AgentDialogueCatalog.boltRequestReplies();
                case THROWING_STAR, BULLET -> urgency == AgentSupplyUrgency.EMPTY
                        ? AgentDialogueCatalog.combatAmmoOutReplies()
                        : AgentDialogueCatalog.combatAmmoLowReplies();
                default -> List.of();
            };
            return replies.isEmpty() ? "" : AgentDialogueSelector.randomReply(replies);
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    private static boolean isRealPlayer(Character character) {
        return AgentClientGatewayRuntime.clients().isRealPlayer(character);
    }

    private static String variant(AgentDialogueIntentEvent intent, List<String> lines) {
        try {
            int index = Integer.parseInt(intent.parameters().getOrDefault("variant", "0"));
            return lines.get(Math.floorMod(index, lines.size()));
        } catch (NumberFormatException ignored) {
            return lines.getFirst();
        }
    }
}
