package server.bots;


import server.agents.integration.AgentBotReplyRuntime;
import server.agents.integration.AgentBotActiveModeRuntime;
import server.agents.capabilities.dialogue.AgentChatMovementFlow;

import java.awt.Point;
import java.util.concurrent.ThreadLocalRandom;

final class BotChatMovementRuntime {
    private BotChatMovementRuntime() {
    }

    static AgentChatMovementFlow.MovementCallbacks movementCallbacks(BotEntry entry) {
        return new AgentChatMovementFlow.MovementCallbacks() {
            @Override
            public boolean farmHere() {
                Point dest = entry.owner != null ? new Point(entry.owner.getPosition()) : null;
                if (dest == null) {
                    return false;
                }
                BotManager.after(BotManager.randMs(1000, 1500), () -> {
                    BotChatStatusRuntime.prepareActiveModeEntry(entry);
                    BotManager.getInstance().issueFarmHere(entry, dest);
                    BotManager.getInstance().botReply(entry, AgentChatMovementFlow.moveHereReply());
                });
                return true;
            }

            @Override
            public boolean patrol() {
                Point ownerPos = entry.owner != null ? new Point(entry.owner.getPosition()) : null;
                if (ownerPos == null) {
                    return false;
                }
                BotManager.after(BotManager.randMs(1000, 1500), () -> {
                    BotChatStatusRuntime.prepareActiveModeEntry(entry);
                    BotManager.getInstance().issuePatrol(entry, ownerPos);
                    BotManager.getInstance().botReply(entry, AgentChatMovementFlow.moveHereReply());
                });
                return true;
            }

            @Override
            public boolean moveHere() {
                Point dest = entry.owner != null ? new Point(entry.owner.getPosition()) : null;
                if (dest == null) {
                    return false;
                }
                BotManager.after(BotManager.randMs(1000, 1500), () -> {
                    BotManager.getInstance().issueMoveTo(entry, dest, true);
                    BotManager.getInstance().botReply(entry, AgentChatMovementFlow.moveHereReply());
                });
                return true;
            }

            @Override
            public void follow() {
                BotManager.after(BotManager.randMs(1500, 2000), () -> {
                    AgentBotActiveModeRuntime.autoEquipAndSuggestGearToSiblings(entry);
                    BotManager.getInstance().botReply(entry, AgentChatMovementFlow.followReply());
                    BotPotionManager.checkPotShareOnModeStart(entry, entry.bot);
                    BotManager.after(BotManager.randMs(250, 750), () -> BotManager.getInstance().issueFollowOwner(entry));
                });
            }

            @Override
            public void grind() {
                BotManager.after(BotManager.randMs(1500, 2000), () -> {
                    BotChatStatusRuntime.prepareActiveModeEntry(entry);
                    BotManager.getInstance().botReply(entry, BotPotionManager.grindStartMessage(entry.bot));
                    BotManager.after(BotManager.randMs(250, 750), () -> {
                        BotManager.getInstance().issueGrind(entry);
                        BotChatStatusRuntime.checkBotStatus(entry, entry.bot);
                    });
                });
            }

            @Override
            public void stop() {
                BotManager.after(BotManager.randMs(900, 1100), () -> {
                    BotManager.getInstance().issueStop(entry);
                    AgentBotActiveModeRuntime.autoEquipAndSuggestGearToSiblings(entry);
                    BotManager.after(BotManager.randMs(1400, 1600), () ->
                            BotManager.getInstance().botReply(entry, AgentChatMovementFlow.stopReply()));
                });
            }

            @Override
            public void fidget() {
                BotManager.after(BotManager.randMs(250, 500), () -> {
                    entry.bot.changeFaceExpression(BotChatStatusRuntime.randomFidgetExpression());
                    BotFidgetManager.maybeStartSocialFidget(entry);
                });
            }

            @Override
            public void greeting() {
                BotManager.after(BotManager.randMs(900, 1100), () -> {
                    entry.bot.changeFaceExpression(Emote.HAPPY.getValue());
                    BotFidgetManager.maybeStartGreetingFidget(entry, ThreadLocalRandom.current().nextInt(100));
                    AgentBotReplyRuntime.queueReply(entry, AgentChatMovementFlow.greetingReply());
                    BotChatStatusRuntime.checkBotStatus(entry, entry.bot);
                });
            }
        };
    }
}
