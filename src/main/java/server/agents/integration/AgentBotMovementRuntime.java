package server.agents.integration;

import client.Character;
import server.agents.capabilities.dialogue.AgentEmote;

import server.agents.capabilities.dialogue.AgentChatMovementFlow;
import server.bots.BotEntry;
import server.agents.capabilities.supplies.AgentPotionService;

import java.awt.Point;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Agent-owned movement chat facade over temporary bot-side movement side
 * effects.
 */
public final class AgentBotMovementRuntime {
    private AgentBotMovementRuntime() {
    }

    public static AgentChatMovementFlow.MovementCallbacks movementCallbacks(BotEntry entry) {
        return new AgentChatMovementFlow.MovementCallbacks() {
            @Override
            public boolean farmHere() {
                Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
                Point dest = owner != null ? new Point(owner.getPosition()) : null;
                if (dest == null) {
                    return false;
                }
                AgentBotMovementSchedulerRuntime.afterRandomDelay(1000, 1500, () -> {
                    AgentBotMovementStatusRuntime.prepareMovementActiveMode(entry);
                    AgentBotMovementCommandRuntime.farmHere(entry, dest);
                    AgentBotMovementReplyRuntime.replyNow(entry, AgentChatMovementFlow.moveHereReply());
                });
                return true;
            }

            @Override
            public boolean patrol() {
                Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
                Point ownerPos = owner != null ? new Point(owner.getPosition()) : null;
                if (ownerPos == null) {
                    return false;
                }
                AgentBotMovementSchedulerRuntime.afterRandomDelay(1000, 1500, () -> {
                    AgentBotMovementStatusRuntime.prepareMovementActiveMode(entry);
                    AgentBotMovementCommandRuntime.patrol(entry, ownerPos);
                    AgentBotMovementReplyRuntime.replyNow(entry, AgentChatMovementFlow.moveHereReply());
                });
                return true;
            }

            @Override
            public boolean moveHere() {
                Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
                Point dest = owner != null ? new Point(owner.getPosition()) : null;
                if (dest == null) {
                    return false;
                }
                AgentBotMovementSchedulerRuntime.afterRandomDelay(1000, 1500, () -> {
                    AgentBotMovementCommandRuntime.moveTo(entry, dest, true);
                    AgentBotMovementReplyRuntime.replyNow(entry, AgentChatMovementFlow.moveHereReply());
                });
                return true;
            }

            @Override
            public void follow() {
                AgentBotMovementSchedulerRuntime.afterRandomDelay(1500, 2000, () -> {
                    AgentBotActiveModeRuntime.autoEquipAndSuggestGearToSiblings(entry);
                    AgentBotMovementReplyRuntime.replyNow(entry, AgentChatMovementFlow.followReply());
                    AgentPotionService.checkPotShareOnModeStart(entry, bot(entry));
                    AgentBotMovementSchedulerRuntime.afterRandomDelay(250, 750,
                            () -> AgentBotMovementCommandRuntime.followOwner(entry));
                });
            }

            @Override
            public void grind() {
                AgentBotMovementSchedulerRuntime.afterRandomDelay(1500, 2000, () -> {
                    AgentBotMovementStatusRuntime.prepareMovementActiveMode(entry);
                    AgentBotMovementReplyRuntime.replyNow(entry, AgentPotionService.grindStartMessage(bot(entry)));
                    AgentBotMovementSchedulerRuntime.afterRandomDelay(250, 750, () -> {
                        AgentBotMovementCommandRuntime.grind(entry);
                        AgentBotMovementStatusRuntime.checkMovementStatus(entry, bot(entry));
                    });
                });
            }

            @Override
            public void stop() {
                AgentBotMovementSchedulerRuntime.afterRandomDelay(900, 1100, () -> {
                    AgentBotMovementCommandRuntime.stop(entry);
                    AgentBotActiveModeRuntime.autoEquipAndSuggestGearToSiblings(entry);
                    AgentBotMovementSchedulerRuntime.afterRandomDelay(1400, 1600, () ->
                            AgentBotMovementReplyRuntime.replyNow(entry, AgentChatMovementFlow.stopReply()));
                });
            }

            @Override
            public void fidget() {
                AgentBotMovementSchedulerRuntime.afterRandomDelay(250, 500, () -> {
                    bot(entry).changeFaceExpression(AgentBotMovementStatusRuntime.randomFidgetExpression());
                    AgentBotFidgetSideEffects.maybeStartSocialFidget(entry);
                });
            }

            @Override
            public void greeting() {
                AgentBotMovementSchedulerRuntime.afterRandomDelay(900, 1100, () -> {
                    bot(entry).changeFaceExpression(AgentEmote.HAPPY.getValue());
                    AgentBotFidgetSideEffects.maybeStartGreetingFidget(entry, ThreadLocalRandom.current().nextInt(100));
                    AgentBotMovementReplyRuntime.queueReply(entry, AgentChatMovementFlow.greetingReply());
                    AgentBotMovementStatusRuntime.checkMovementStatus(entry, bot(entry));
                });
            }
        };
    }

    private static Character bot(BotEntry entry) {
        return AgentBotRuntimeIdentityRuntime.bot(entry);
    }
}
