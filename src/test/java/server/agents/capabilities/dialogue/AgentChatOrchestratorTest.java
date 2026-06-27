package server.agents.capabilities.dialogue;

import client.Job;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentChatOrchestratorTest {
    @Test
    void pendingActionsRunBeforeSessionRequests() {
        TestContext context = new TestContext();
        context.pendingAction = AgentChatPendingAction.ITEM_CHOICE;
        context.pendingDropCategory = "scrolls";

        assertTrue(AgentChatOrchestrator.handle("drop", context));

        assertEquals(List.of("active", "choice:scrolls:false"), context.events);
    }

    @Test
    void sessionRequestsAreTerminalWhenNoPendingActionExists() {
        TestContext context = new TestContext();

        assertTrue(AgentChatOrchestrator.handle("logout", context));

        assertEquals(List.of("active", "logout"), context.events);
    }

    @Test
    void terminalFlowsPreserveLegacyOrderBeforeMovementAndReports() {
        TestContext context = new TestContext();

        assertTrue(AgentChatOrchestrator.handle("need hp pots", context));

        assertEquals(List.of("active", "hp:true"), context.events);
    }

    @Test
    void movementAndReportQueriesPreserveLegacyFallThrough() {
        TestContext context = new TestContext();

        assertFalse(AgentChatOrchestrator.handle("follow me", context));

        assertEquals(List.of("active", "follow"), context.events);
    }

    @Test
    void nonTerminalReportQueriesPreserveLegacyFallThrough() {
        TestContext context = new TestContext();

        assertFalse(AgentChatOrchestrator.handle("stats", context));

        assertEquals(List.of("active", "stats"), context.events);
    }

    @Test
    void helpReportIsTerminal() {
        TestContext context = new TestContext();

        assertTrue(AgentChatOrchestrator.handle("help", context));

        assertEquals(List.of("active", "help"), context.events);
    }

    @Test
    void transferCommandsAreTerminalBeforeReports() {
        TestContext context = new TestContext();

        assertTrue(AgentChatOrchestrator.handle("trade mesos", context));

        assertEquals(List.of("active", "transfer:mesos"), context.events);
    }

    @Test
    void jobAdvancementStillFallsThroughAsUnhandled() {
        TestContext context = new TestContext();

        assertFalse(AgentChatOrchestrator.handle("fighter", context));

        assertEquals(List.of("active", "job:FIGHTER"), context.events);
    }

    @Test
    void chatRuntimeOwnsHandledStateForTerminalCommands() {
        TestContext context = new TestContext();

        AgentChatRuntime.handleChat("help", context);

        assertTrue(AgentChatRuntime.wasLastChatHandled());
        assertEquals(List.of("active", "help"), context.events);
    }

    @Test
    void chatRuntimeClearsHandledStateForFallThroughCommands() {
        TestContext terminalContext = new TestContext();
        AgentChatRuntime.handleChat("help", terminalContext);
        assertTrue(AgentChatRuntime.wasLastChatHandled());

        TestContext fallThroughContext = new TestContext();
        AgentChatRuntime.handleChat("follow me", fallThroughContext);

        assertFalse(AgentChatRuntime.wasLastChatHandled());
        assertEquals(List.of("active", "follow"), fallThroughContext.events);
    }

    private static final class TestContext implements AgentChatOrchestrator.Context {
        private final List<String> events = new ArrayList<>();
        private String pendingAction;
        private String pendingDropCategory;
        private boolean waitingForSpVariant;
        private boolean waitingForApBuild;

        @Override
        public void markActive() {
            events.add("active");
        }

        @Override
        public boolean hasPendingAction() {
            return pendingAction != null;
        }

        @Override
        public AgentPendingChatActionFlow.PendingActionState pendingActionState() {
            return new AgentPendingChatActionFlow.PendingActionState() {
                @Override
                public String pendingAction() {
                    return pendingAction;
                }

                @Override
                public String pendingDropCategory() {
                    return pendingDropCategory;
                }

                @Override
                public void clearPendingAction() {
                    pendingAction = null;
                }

                @Override
                public void clearPendingDropCategory() {
                    pendingDropCategory = null;
                }
            };
        }

        @Override
        public AgentPendingChatActionFlow.PendingActionCallbacks pendingActionCallbacks() {
            return new AgentPendingChatActionFlow.PendingActionCallbacks() {
                @Override
                public void handleOwnerAwayChoice(String message) {
                    events.add("away-choice:" + message);
                }

                @Override
                public void executeItemChoice(String category, boolean trade) {
                    events.add("choice:" + category + ":" + trade);
                }

                @Override
                public void cancelItemChoice() {
                    events.add("choice-cancel");
                }

                @Override
                public void handleSkillTreeChoice(String message) {
                    events.add("skill-choice:" + message);
                }

                @Override
                public void confirmRelog() {
                    events.add("confirm-relog");
                }

                @Override
                public void confirmLogout() {
                    events.add("confirm-logout");
                }

                @Override
                public void cancelPendingAction(boolean dropAction) {
                    events.add("cancel:" + dropAction);
                }
            };
        }

        @Override
        public AgentChatSessionRequestFlow.SessionRequestCallbacks sessionRequestCallbacks() {
            return new AgentChatSessionRequestFlow.SessionRequestCallbacks() {
                @Override
                public void requestRelog() {
                    events.add("relog");
                }

                @Override
                public void requestLogout() {
                    events.add("logout");
                }

                @Override
                public void requestAway() {
                    events.add("away");
                }
            };
        }

        @Override
        public AgentChatSupplyRequestFlow.SupplyRequestCallbacks supplyRequestCallbacks() {
            return new AgentChatSupplyRequestFlow.SupplyRequestCallbacks() {
                @Override
                public void requestPotion(boolean hpPotion) {
                    events.add("hp:" + hpPotion);
                }

                @Override
                public void requestAnyPotion() {
                    events.add("any-pot");
                }

                @Override
                public void requestAmmo() {
                    events.add("ammo");
                }
            };
        }

        @Override
        public AgentChatSocialFlow.SocialCallbacks socialCallbacks() {
            return targetName -> events.add("fame:" + targetName);
        }

        @Override
        public AgentChatToggleFlow.ToggleCallbacks toggleCallbacks() {
            return new AgentChatToggleFlow.ToggleCallbacks() {
                @Override
                public void setSupport(boolean enabled) {
                    events.add("support:" + enabled);
                }

                @Override
                public void setHeals(boolean enabled) {
                    events.add("heals:" + enabled);
                }

                @Override
                public void setBuffConsumables(boolean enabled) {
                    events.add("buff:" + enabled);
                }

                @Override
                public void setBuffConsumablesCheapMode(boolean cheapMode) {
                    events.add("buff-cheap:" + cheapMode);
                }

                @Override
                public void setProactiveOffers(boolean enabled) {
                    events.add("offers:" + enabled);
                }
            };
        }

        @Override
        public AgentChatBuffQueryFlow.BuffQueryCallbacks buffQueryCallbacks() {
            return new AgentChatBuffQueryFlow.BuffQueryCallbacks() {
                @Override
                public void reportBuffList() {
                    events.add("buff-list");
                }

                @Override
                public void reportBuffDebug() {
                    events.add("buff-debug");
                }

                @Override
                public void reportSkillBuffDebug() {
                    events.add("skill-buff-debug");
                }
            };
        }

        @Override
        public AgentChatRespecFlow.RespecCallbacks respecCallbacks() {
            return new AgentChatRespecFlow.RespecCallbacks() {
                @Override
                public void respecAp() {
                    events.add("respec-ap");
                }

                @Override
                public void respecSp() {
                    events.add("respec-sp");
                }
            };
        }

        @Override
        public AgentChatEquipmentFlow.EquipmentCallbacks equipmentCallbacks() {
            return new AgentChatEquipmentFlow.EquipmentCallbacks() {
                @Override
                public boolean unequipSlot(String slotName) {
                    events.add("slot:" + slotName);
                    return true;
                }

                @Override
                public void unequipAll() {
                    events.add("unequip-all");
                }

                @Override
                public void autoEquipDebug() {
                    events.add("equip-debug");
                }

                @Override
                public void autoEquip() {
                    events.add("equip");
                }
            };
        }

        @Override
        public AgentChatMovementFlow.MovementCallbacks movementCallbacks() {
            return new AgentChatMovementFlow.MovementCallbacks() {
                @Override
                public boolean farmHere() {
                    events.add("farm-here");
                    return true;
                }

                @Override
                public boolean patrol() {
                    events.add("patrol");
                    return true;
                }

                @Override
                public boolean moveHere() {
                    events.add("move-here");
                    return true;
                }

                @Override
                public void follow() {
                    events.add("follow");
                }

                @Override
                public void grind() {
                    events.add("grind");
                }

                @Override
                public void stop() {
                    events.add("stop");
                }

                @Override
                public void fidget() {
                    events.add("fidget");
                }

                @Override
                public void greeting() {
                    events.add("greeting");
                }
            };
        }

        @Override
        public boolean isWaitingForSpVariant() {
            return waitingForSpVariant;
        }

        @Override
        public AgentChatBuildFlow.SpVariantCallbacks spVariantCallbacks() {
            return new AgentChatBuildFlow.SpVariantCallbacks() {
                @Override
                public void oneHanded() {
                    events.add("one-hand");
                }

                @Override
                public void twoHanded() {
                    events.add("two-hand");
                }
            };
        }

        @Override
        public boolean isWaitingForApBuild() {
            return waitingForApBuild;
        }

        @Override
        public AgentChatBuildFlow.ApBuildCallbacks apBuildCallbacks() {
            return new AgentChatBuildFlow.ApBuildCallbacks() {
                @Override
                public void requestBuildPrompt() {
                    events.add("ap-prompt");
                }

                @Override
                public void selectBuild(String message) {
                    events.add("ap-select:" + message);
                }
            };
        }

        @Override
        public AgentChatUtilityFlow.UtilityCallbacks utilityCallbacks() {
            return new AgentChatUtilityFlow.UtilityCallbacks() {
                @Override
                public void tradeInvite() {
                    events.add("trade-invite");
                }

                @Override
                public void sellTrash() {
                    events.add("sell-trash");
                }

                @Override
                public void makeCrystals() {
                    events.add("make-crystals");
                }

                @Override
                public void disassembleTrash() {
                    events.add("disassemble");
                }
            };
        }

        @Override
        public void handleTransferCommand(AgentChatTransferFlow.TransferCommand transferCommand, String message) {
            events.add("transfer:" + transferCommand.category());
        }

        @Override
        public AgentChatTransferFlow.ItemQueryCallbacks itemQueryCallbacks() {
            return itemName -> events.add("item:" + itemName);
        }

        @Override
        public AgentChatReportFlow.ReportCallbacks reportCallbacks() {
            return new AgentChatReportFlow.ReportCallbacks() {
                @Override
                public void help() {
                    events.add("help");
                }

                @Override
                public void requestUpgrade() {
                    events.add("upgrade");
                }

                @Override
                public void recommendedGear() {
                    events.add("gear");
                }

                @Override
                public void skills() {
                    events.add("skills");
                }

                @Override
                public void stats() {
                    events.add("stats");
                }

                @Override
                public void movementStats() {
                    events.add("movement-stats");
                }

                @Override
                public void range() {
                    events.add("range");
                }

                @Override
                public void build() {
                    events.add("build");
                }

                @Override
                public void inventory() {
                    events.add("inventory");
                }

                @Override
                public void mesos() {
                    events.add("mesos");
                }

                @Override
                public void exp() {
                    events.add("exp");
                }

                @Override
                public void inventorySlots() {
                    events.add("slots");
                }

                @Override
                public void scrolls() {
                    events.add("scrolls");
                }

                @Override
                public void potions() {
                    events.add("potions");
                }

                @Override
                public void debugStats() {
                    events.add("debug");
                }

                @Override
                public void critDebug() {
                    events.add("crit");
                }

                @Override
                public void potDebug() {
                    events.add("pot-debug");
                }
            };
        }

        @Override
        public Job currentJob() {
            return Job.WARRIOR;
        }

        @Override
        public int level() {
            return 30;
        }

        @Override
        public AgentChatJobAdvancementFlow.JobAdvancementCallbacks jobAdvancementCallbacks() {
            return job -> events.add("job:" + job.name());
        }
    }
}
