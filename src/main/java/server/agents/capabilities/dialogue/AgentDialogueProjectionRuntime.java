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
import server.agents.progression.events.AgentQuestProgressDialogueReactionService;
import server.agents.resources.events.AgentResourceDialogueReactionService;
import server.agents.operations.events.AgentOperationalDialogueReactionService;
import server.agents.capabilities.partyquest.hpq.AgentHpqSessionRegistry;
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
        if (!shouldProject(intent, AgentHpqSessionRegistry.active(intent.agentId()))) {
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

    static boolean shouldProject(AgentDialogueIntentEvent intent, boolean activeHpqMember) {
        return intent != null
                && !activeHpqMember
                && !AgentFieldNarrationService.POSTURE_INTENT.equals(intent.intentKey());
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
        if (AgentQuestProgressDialogueReactionService.INTENT_KEY.equals(intent.intentKey())) {
            String current = intent.parameters().getOrDefault("currentCount", "");
            String required = intent.parameters().getOrDefault("requiredCount", "");
            String targetName = intent.parameters().getOrDefault("targetName", "").trim();
            String progress = "Quest progress: " + current + "/" + required
                    + (targetName.isBlank() ? "" : " " + targetName);
            return "50".equals(intent.parameters().getOrDefault("milestonePercent", ""))
                    ? progress + " - halfway there."
                    : progress + " - almost done.";
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
        if (AgentTownLifeTestNarrationService.ACTIVITY_INTENT.equals(intent.intentKey())) {
            String activity = friendly(intent.parameters().getOrDefault("activity", "activity"));
            String venue = intent.parameters().getOrDefault("venue", "a nearby spot");
            return switch (intent.parameters().getOrDefault("phase", "")) {
                case "SELECTED" -> "I'm going to " + activity + " at " + venue + ".";
                case "ORIENTING" -> "I'm " + activity + " here for about "
                        + intent.parameters().getOrDefault("remainingSeconds", "a few") + " seconds.";
                case "COMPLETED" -> "I've finished " + activity + ".";
                case "ABANDONED" -> "I couldn't reach that spot, so I'll choose another activity.";
                case "TIMED_OUT" -> "That activity took too long, so I'm moving on.";
                default -> "";
            };
        }
        if (AgentTownLifeTestNarrationService.LIFECYCLE_INTENT.equals(intent.intentKey())) {
            return switch (intent.parameters().getOrDefault("phase", "")) {
                case "STARTED" -> "I'm entering TownLife for this test.";
                case "EXIT_REQUESTED" -> "I'll finish what I'm doing, then leave TownLife.";
                case "EXITED" -> "I've finished this TownLife visit.";
                case "FORCED" -> "My TownLife visit was stopped immediately.";
                case "TIMED_OUT" -> "My TownLife exit timed out, so the visit was closed.";
                default -> "";
            };
        }
        if (AgentTownLifeTestNarrationService.ENCOUNTER_INTENT.equals(intent.intentKey())) {
            String peer = intent.parameters().getOrDefault("peerName", "friend");
            boolean showOff = "PLAYFUL_SPARRING".equals(
                    intent.parameters().getOrDefault("encounterType", ""));
            boolean initiator = "INITIATOR".equals(intent.parameters().getOrDefault("role", ""));
            return switch (intent.parameters().getOrDefault("phase", "")) {
                case "ACTIVE" -> initiator
                        ? (showOff ? peer + ", want to practice a little?"
                        : "Hey " + peer + ", taking a break too?")
                        : (showOff ? "Sure, show me what you've got!"
                        : "Yeah, I'll hang around here for a bit.");
                case "REACTING" -> showOff ? "Nice move!" : "This is a good place to relax.";
                case "CLOSING" -> "I'll catch you again later, " + peer + ".";
                default -> "";
            };
        }
        if (AgentTownLifeTestNarrationService.SCENARIO_INTENT.equals(intent.intentKey())) {
            String detail = intent.parameters().getOrDefault("detail", "");
            return switch (intent.parameters().getOrDefault("phase", "")) {
                case "STARTED_VISIT" -> "I'm starting TownLife cycle "
                        + intent.parameters().getOrDefault("cycle", "") + ".";
                case "EXITED_VISIT" -> "I've left TownLife and will move to my standby spot.";
                case "STAGING" -> "I'm going to wait near " + detail + ".";
                case "OUTSIDE_IDLE" -> "I'm outside TownLife now, waiting near " + detail + ".";
                case "REENTERING" -> "My wait is over; I'm re-entering TownLife.";
                case "COMPLETED" -> "The TownLife cycle test is complete.";
                case "FAILED" -> "The TownLife cycle test stopped: " + detail + ".";
                case "STOP_REQUESTED" -> "I'll finish this activity and stop the TownLife test.";
                default -> "";
            };
        }
        if (AgentFieldNarrationService.LIFECYCLE_INTENT.equals(intent.intentKey())) {
            return switch (intent.parameters().getOrDefault("phase", "")) {
                case "REQUESTED" -> "I'm requesting a field assignment.";
                case "ADMITTED" -> "I've joined this field session.";
                case "FORMING" -> "I'm waiting for the formation assignment.";
                case "GRINDING" -> "I'm starting my assigned grind routine.";
                case "RESTING" -> "I'm taking a short break at a safe spot.";
                case "SUSPENDED" -> "My field routine is paused for maintenance.";
                case "RESUMED" -> "Maintenance is done; I'm resuming my field assignment.";
                case "DRAINING" -> "I'll finish my current action, then leave this field session.";
                case "EXITED" -> "I've left this field session.";
                case "FAILED" -> "I couldn't continue this field session: "
                        + intent.parameters().getOrDefault("reason", "unknown reason") + '.';
                default -> "";
            };
        }
        if (AgentFieldNarrationService.ASSIGNMENT_INTENT.equals(intent.intentKey())) {
            String role = friendly(intent.parameters().getOrDefault("role", "roamer"));
            String anchor = intent.parameters().getOrDefault("anchor", "the assigned anchor");
            String regions = intent.parameters().getOrDefault("regions", "");
            return "I'm the " + role + " in slot "
                    + intent.parameters().getOrDefault("partySlot", "?") + ", covering " + anchor
                    + (regions.isBlank() ? "." : " across regions " + regions + '.');
        }
        if (AgentFieldNarrationService.POPULATION_INTENT.equals(intent.intentKey())) {
            return "The field population is now "
                    + intent.parameters().getOrDefault("population", "?")
                    + "; our formation will rebalance.";
        }
        if (AgentFieldNarrationService.REST_INTENT.equals(intent.intentKey())) {
            return switch (intent.parameters().getOrDefault("phase", "")) {
                case "STARTED" -> "I'm heading to a safe spot near "
                        + intent.parameters().getOrDefault("target", "the portal") + " for about "
                        + intent.parameters().getOrDefault("seconds", "a few") + " seconds.";
                case "ARRIVED" -> "I've reached my field rest spot.";
                case "COMPLETED" -> "My break is over; I'm returning to the grind.";
                case "CANCELLED" -> "I'm cancelling the break and returning to my assignment.";
                default -> "";
            };
        }
        if (AgentFieldNarrationService.POSTURE_INTENT.equals(intent.intentKey())) {
            String posture = friendly(intent.parameters().getOrDefault("phase", "combat"));
            String reason = intent.parameters().getOrDefault("reason", "");
            return "I'm switching to " + posture + (reason.isBlank() ? "." : ": " + reason + '.');
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

    private static String friendly(String value) {
        return value == null ? "spending some time" : value.toLowerCase().replace('_', ' ');
    }
}
