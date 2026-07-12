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
        Optional<PartnerLink> link = service.registeredLink(player);
        String status = link.map(value -> "#bRegistered Partner: " + partnerName(value, player.getId()) + "#k\r\n\r\n")
                .orElse("#dNo Partner is currently registered.#k\r\n\r\n");
        return "Even the strongest adventurers need someone they can trust. The Adventurer Partner Program "
                + "lets two adventurers from the same account train and travel as a team. "
                + "I can register one of your other adventurers as your partner.\r\n\r\n"
                + status
                + "#L0#Register an adventuring partner#l\r\n"
                + "#L1#View my registered partner#l\r\n"
                + "#L2#Invite my partner#l\r\n"
                + "#L3#Enter Solo Tag Mode#l\r\n"
                + "#L4#Change Partner Program mode#l\r\n"
                + "#L5#Release my partner#l\r\n"
                + "#L6#Unregister my partner#l\r\n"
                + "#L7#Explain the Adventurer Partner Program#l\r\n"
                + "#L8#Continue with Agent E's regular duties#l\r\n"
                + "#L9#Leave#l";
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

    public String view(Character player) {
        return execute(() -> {
            PartnerLink link = service.registeredLink(player)
                    .orElseThrow(() -> new IllegalStateException("No adventuring Partner is registered."));
            int partnerId = link.partnerOf(player.getId());
            PartnerRosterCandidate partner = serviceCharacter(partnerId);
            return "Registered Partner: #b" + partner.name() + "#k\r\n"
                    + "Level " + partner.level() + " " + jobName(partner.jobId()) + "\r\n"
                    + "Preferred mode: " + modeName(link.preferredMode()) + "\r\n"
                    + "This is an independent character with their own canonical progression and belongings.";
        });
    }

    public String invite(Character player) {
        return execute(() -> {
            ActivePartnerSession active = service.activate(player, PartnerMode.DOUBLE_PARTNER);
            return "I'll notify " + partnerName(active.link(), player.getId())
                    + ". Stay nearby while they enter the field. Both of you will remain visible, and your Partner "
                    + "will follow you until given another task. Use Nimble Feet to exchange roles.";
        });
    }

    public String enterSoloTag(Character player) {
        return execute(() -> {
            service.activate(player, PartnerMode.SOLO_TAG);
            return "Only one of you will remain in the field. Use Nimble Feet whenever you want to tag your Partner "
                    + "into action. The inactive profile is loaded but dormant.";
        });
    }

    public String changeMode(Character player, int modeSelection) {
        return execute(() -> {
            PartnerMode mode = modeSelection == 0 ? PartnerMode.SOLO_TAG : PartnerMode.DOUBLE_PARTNER;
            service.changeMode(player, mode);
            return "Preferred Partner Program mode changed to " + modeName(mode) + ".";
        });
    }

    public String release(Character player) {
        return execute(() -> {
            PartnerLink link = service.registeredLink(player)
                    .orElseThrow(() -> new IllegalStateException("No adventuring Partner is registered."));
            String partnerName = partnerName(link, player.getId());
            service.release(player, "Released through Agent E");
            return partnerName + " will return to their own assignment. Any progress they made has been safely recorded.";
        });
    }

    public String unregister(Character player) {
        return execute(() -> {
            service.unregister(player);
            return "The Partner registration has been removed. Both characters keep all of their canonical progress.";
        });
    }

    public String explanation() {
        return "The Adventurer Partner Program links two different characters from the same account and world. "
                + "They keep separate appearances, jobs, stats, inventories, equipment, skills, quests, and canonical IDs.\r\n\r\n"
                + "#bSolo Tag Mode#k keeps one actor in the field. Nimble Feet exchanges the active and dormant profiles "
                + "without moving the actor or camera.\r\n\r\n"
                + "#bDouble Partner Mode#k brings your Partner in as a real Agent actor. Nimble Feet exchanges complete "
                + "profile bindings while both actors keep their own positions and controllers.";
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
