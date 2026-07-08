package server.agents.integration;


import server.agents.runtime.AgentSchedulerRuntime;
import client.Character;
import server.agents.capabilities.dialogue.AgentEmote;

import server.agents.capabilities.dialogue.AgentChatMovementFlow;
import server.agents.capabilities.movement.AgentMovementStatusRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.capabilities.supplies.AgentPotionService;

import java.awt.Point;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Agent-owned movement chat facade over temporary bot-side movement side
 * effects.
 */
public final class AgentMovementRuntime {
    private AgentMovementRuntime() {
    }

    public static AgentChatMovementFlow.MovementCallbacks movementCallbacks(AgentRuntimeEntry entry) {
        return new AgentChatMovementFlow.MovementCallbacks() {
            @Override
            public boolean farmHere() {
                Character owner = AgentRuntimeIdentityRuntime.owner(entry);
                Point dest = owner != null ? new Point(owner.getPosition()) : null;
                if (dest == null) {
                    return false;
                }
                AgentSchedulerRuntime.afterRandomDelay(1000, 1500, () -> {
                    AgentMovementStatusRuntime.prepareMovementActiveMode(entry);
                    AgentMovementCommandRuntime.farmHere(entry, dest);
                    AgentReplyRuntime.replyNow(entry, AgentChatMovementFlow.moveHereReply());
                });
                return true;
            }

            @Override
            public boolean patrol() {
                Character owner = AgentRuntimeIdentityRuntime.owner(entry);
                Point ownerPos = owner != null ? new Point(owner.getPosition()) : null;
                if (ownerPos == null) {
                    return false;
                }
                AgentSchedulerRuntime.afterRandomDelay(1000, 1500, () -> {
                    AgentMovementStatusRuntime.prepareMovementActiveMode(entry);
                    AgentMovementCommandRuntime.patrol(entry, ownerPos);
                    AgentReplyRuntime.replyNow(entry, AgentChatMovementFlow.moveHereReply());
                });
                return true;
            }

            @Override
            public boolean moveHere() {
                Character owner = AgentRuntimeIdentityRuntime.owner(entry);
                Point dest = owner != null ? new Point(owner.getPosition()) : null;
                if (dest == null) {
                    return false;
                }
                AgentSchedulerRuntime.afterRandomDelay(1000, 1500, () -> {
                    AgentMovementCommandRuntime.moveTo(entry, dest, true);
                    AgentReplyRuntime.replyNow(entry, AgentChatMovementFlow.moveHereReply());
                });
                return true;
            }

            @Override
            public void follow() {
                AgentSchedulerRuntime.afterRandomDelay(1500, 2000, () -> {
                    AgentActiveModeRuntime.autoEquipAndSuggestGearToSiblings(entry);
                    AgentReplyRuntime.replyNow(entry, AgentChatMovementFlow.followReply());
                    AgentPotionService.checkPotShareOnModeStart(entry, bot(entry));
                    AgentSchedulerRuntime.afterRandomDelay(250, 750,
                            () -> AgentMovementCommandRuntime.followOwner(entry));
                });
            }

            @Override
            public void grind() {
                AgentSchedulerRuntime.afterRandomDelay(1500, 2000, () -> {
                    AgentMovementStatusRuntime.prepareMovementActiveMode(entry);
                    AgentReplyRuntime.replyNow(entry, AgentPotionService.grindStartMessage(bot(entry)));
                    AgentSchedulerRuntime.afterRandomDelay(250, 750, () -> {
                        AgentMovementCommandRuntime.grind(entry);
                        AgentMovementStatusRuntime.checkMovementStatus(entry, bot(entry));
                    });
                });
            }

            @Override
            public void stop() {
                AgentSchedulerRuntime.afterRandomDelay(900, 1100, () -> {
                    AgentMovementCommandRuntime.stop(entry);
                    AgentActiveModeRuntime.autoEquipAndSuggestGearToSiblings(entry);
                    AgentSchedulerRuntime.afterRandomDelay(1400, 1600, () ->
                            AgentReplyRuntime.replyNow(entry, AgentChatMovementFlow.stopReply()));
                });
            }

            @Override
            public void fidget() {
                AgentSchedulerRuntime.afterRandomDelay(250, 500, () -> {
                    bot(entry).changeFaceExpression(AgentMovementStatusRuntime.randomFidgetExpression());
                    AgentFidgetSideEffects.maybeStartSocialFidget(entry);
                });
            }

            @Override
            public void greeting() {
                AgentSchedulerRuntime.afterRandomDelay(900, 1100, () -> {
                    bot(entry).changeFaceExpression(AgentEmote.HAPPY.getValue());
                    AgentFidgetSideEffects.maybeStartGreetingFidget(entry, ThreadLocalRandom.current().nextInt(100));
                    AgentReplyRuntime.queueReply(entry, AgentChatMovementFlow.greetingReply());
                    AgentMovementStatusRuntime.checkMovementStatus(entry, bot(entry));
                });
            }
        };
    }

    private static Character bot(AgentRuntimeEntry entry) {
        return AgentRuntimeIdentityRuntime.bot(entry);
    }
}
