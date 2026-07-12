package server.partner;

import client.Character;
import client.Job;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Agent E dialogue adapter; gameplay and persistence remain in the service. */
public final class AdventurerPartnerNpcService {
    public static final AdventurerPartnerNpcService INSTANCE =
            new AdventurerPartnerNpcService(AdventurerPartnerService.getInstance());

    private final AdventurerPartnerService service;

    AdventurerPartnerNpcService(AdventurerPartnerService service) {
        this.service = service;
    }

    public boolean enabled(int npcId) {
        return service.isEnabledForNpc(npcId);
    }

    public String mainMenu(Character player) {
        Optional<AdventurerPartnerService.PartnerOverview> found = service.overview(player);
        StringBuilder menu = new StringBuilder(
                "Even the strongest adventurers need someone they can trust.\r\n\r\n");
        if (found.isEmpty()) {
            menu.append("#dNo adventuring Partner is registered.#k\r\n\r\n")
                    .append("#L0#Register an adventuring partner#l\r\n");
        } else {
            AdventurerPartnerService.PartnerOverview overview = found.get();
            PartnerRosterCandidate partner = overview.partner();
            menu.append("#bRegistered Partner: ").append(partner.name()).append("#k\r\n")
                    .append("Level ").append(partner.level()).append(' ')
                    .append(jobName(partner.jobId())).append("\r\n")
                    .append("Status: ").append(statusName(overview)).append("\r\n")
                    .append("Current Mode: #b").append(modeName(overview.currentMode()))
                    .append("#k\r\n\r\n")
                    .append("#L1#Unregister adventuring partner#l\r\n");
            if (overview.currentMode() == PartnerMode.DOUBLE_PARTNER) {
                menu.append("#L4#Change to Solo Tag Mode#l\r\n");
                if (canActivate(overview)) {
                    menu.append("#L2#Invite my partner#l\r\n");
                }
            } else {
                menu.append("#L5#Change to Double Partner Mode#l\r\n");
                if (canActivate(overview)) {
                    menu.append("#L3#Prepare Solo Tag#l\r\n");
                }
            }
            menu.append("#L6#Release my partner#l\r\n");
        }
        return menu.append("#L7#Explain the Adventurer Partner Program#l\r\n")
                .append("#L8#Continue with Agent E's regular duties#l\r\n")
                .append("#L9#Leave#l")
                .toString();
    }

    public String rosterMenu(Character player) {
        List<PartnerRosterEntry> roster = service.roster(player);
        if (roster.isEmpty()) {
            return "You have no other characters in this world who can join the program.";
        }
        StringBuilder menu = new StringBuilder("Choose an adventurer to register. Every candidate must be offline and available.\r\n\r\n");
        for (PartnerRosterEntry entry : roster) {
            String label = entry.name() + " - Level " + entry.level() + " " + jobName(entry.jobId());
            if (entry.eligible()) {
                menu.append("#L").append(entry.characterId()).append('#').append(label).append("#l\r\n");
            } else {
                menu.append("#r").append(label).append(" - ").append(entry.rejectionReason()).append("#k\r\n");
            }
        }
        return menu.toString();
    }

    public String register(Character player, int partnerCharacterId) {
        return execute(() -> {
            PartnerLink link = service.register(player, partnerCharacterId);
            return "Registration complete. " + partnerName(link, player.getId())
                    + " is now your adventuring Partner. Registration alone does not bring them into the field.";
        });
    }

    public String invite(Character player) {
        return execute(() -> {
            PartnerLink link = service.registeredLink(player)
                    .orElseThrow(() -> new IllegalStateException("No adventuring Partner is registered."));
            if (link.preferredMode() != PartnerMode.DOUBLE_PARTNER) {
                throw new IllegalStateException("Change to Double Partner Mode before inviting your Partner.");
            }
            ActivePartnerSession active = service.activate(player, PartnerMode.DOUBLE_PARTNER);
            return "I'll notify " + partnerName(active.link(), player.getId())
                    + ". Stay nearby while they enter the field. Both of you will remain visible, and your Partner "
                    + "will follow you until given another task. Use Nimble Feet to exchange roles.";
        });
    }

    public String prepareSoloTag(Character player) {
        return execute(() -> {
            service.prepareSoloTag(player);
            return "Solo Tag is ready. Use Nimble Feet whenever you want to tag your Partner into action. "
                    + "The inactive profile is loaded safely and remains dormant.";
        });
    }

    public String changeToSoloTag(Character player) {
        return execute(() -> {
            service.changeToSoloTag(player);
            return "Double Partner Mode has ended and Solo Tag is ready. "
                    + "Use Nimble Feet to switch characters.";
        });
    }

    public String changeToDoublePartner(Character player) {
        return execute(() -> {
            service.changeToDoublePartner(player);
            return "Solo Tag has ended and Double Partner Mode is selected.";
        });
    }

    public boolean doublePartnerModeSelected(Character player) {
        return service.registeredLink(player)
                .map(link -> link.preferredMode() == PartnerMode.DOUBLE_PARTNER)
                .orElse(false);
    }

    public String release(Character player) {
        return execute(() -> {
            PartnerLink link = service.registeredLink(player)
                    .orElseThrow(() -> new IllegalStateException("No adventuring Partner is registered."));
            String partnerName = partnerName(link, player.getId());
            AdventurerPartnerService.ReleaseResult result = service.releaseOrReset(
                    player, "Released through Agent E");
            if (result.partnerOnlineIndependently() && !result.changedRuntimeState()) {
                return partnerName + " is currently adventuring independently. "
                        + "No Partner-managed session was active, so their login was not interrupted.";
            }
            if (!result.changedRuntimeState()) {
                return partnerName + " is already offline and the Partner session is clear.";
            }
            return partnerName + " has returned to their own assignment. Their canonical progress was saved, "
                    + "and the Partner session was cleared.";
        });
    }

    public String unregister(Character player) {
        return execute(() -> {
            service.unregister(player);
            return "The Partner registration has been removed. Both characters keep all of their canonical progress.";
        });
    }

    public String explanation() {
        return "The Adventurer Partner Program links two independent characters from the same account and world. "
                + "Your Partner's IGN, level, job, status, and selected mode are shown in my main menu.\r\n\r\n"
                + "#bSolo Tag Mode#k safely loads one active and one dormant profile. Prepare Solo Tag, then use "
                + "Nimble Feet to switch without moving the actor or camera.\r\n\r\n"
                + "#bDouble Partner Mode#k invites your Partner as an Agent. Both actors remain visible, and Nimble "
                + "Feet exchanges their profiles only while they are in the same map.\r\n\r\n"
                + "#bRelease my partner#k restores canonical ownership, saves progress, logs out the Partner Agent, "
                + "and clears a stuck Partner-managed session. It never disconnects a character being played independently.";
    }

    private static boolean canActivate(AdventurerPartnerService.PartnerOverview overview) {
        return !overview.presence().active()
                && overview.presence() != AdventurerPartnerService.PartnerPresence.RECOVERY_REQUIRED;
    }

    private static String statusName(AdventurerPartnerService.PartnerOverview overview) {
        return switch (overview.presence()) {
            case OFFLINE -> "#dOffline#k";
            case ONLINE_INDEPENDENTLY -> "#rOnline independently#k";
            case SOLO_TAG_READY -> "#gSolo Tag ready#k";
            case DOUBLE_PARTNER_ACTIVE -> "#gDouble Partner active#k";
            case DOUBLE_PARTNER_OTHER_MAP -> "#rActive in another map#k";
            case RECOVERY_REQUIRED -> "#rRecovery required - use Release/reset#k";
        };
    }

    private PartnerRosterCandidate serviceCharacter(int characterId) {
        return service.findCharacter(characterId)
                .orElseThrow(() -> new IllegalStateException("The registered Partner no longer exists."));
    }

    private String partnerName(PartnerLink link, int currentCharacterId) {
        return serviceCharacter(link.partnerOf(currentCharacterId)).name();
    }

    private static String modeName(PartnerMode mode) {
        return mode == PartnerMode.SOLO_TAG ? "Solo Tag Mode" : "Double Partner Mode";
    }

    private static String jobName(int jobId) {
        Job job = Job.getById(jobId);
        if (job == null) {
            return "Unknown Job";
        }
        String[] words = job.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(java.lang.Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private static String execute(NpcOperation operation) {
        try {
            return operation.run();
        } catch (RuntimeException failure) {
            String message = failure.getMessage();
            return message == null || message.isBlank()
                    ? "The Partner Program could not complete that request."
                    : message;
        }
    }

    @FunctionalInterface
    private interface NpcOperation {
        String run();
    }
}
