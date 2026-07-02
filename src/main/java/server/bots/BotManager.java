package server.bots;

import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.capabilities.quest.AgentPartyQuestSyncService;
import server.agents.capabilities.dialogue.AgentWhisperCommandService;
import server.agents.runtime.AgentInteractionRuntime;
import server.agents.runtime.AgentLifecycleService;
import server.agents.runtime.AgentLifecycleChatCommandRuntime;
import server.agents.runtime.AgentOfflineLoadRuntime;
import server.agents.runtime.AgentPartyLifecycleService;
import server.agents.runtime.AgentRuntimeConfig;
import server.agents.runtime.AgentRuntimeCleanupService;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentSpawnPositionService;
import server.agents.capabilities.social.AgentScrollReactionNotificationService;
import server.agents.capabilities.supplies.AgentPotionCheckRequestService;
import server.agents.capabilities.trade.AgentOwnerItemNotificationService;
import server.agents.capabilities.trade.AgentTradeDialogueService;
import server.agents.integration.AgentBotMovementCommandRuntime;
import server.agents.commands.AgentReplyChannel;
import client.Character;
import client.inventory.Item;
import server.maps.MapleMap;
import server.quest.Quest;

import java.awt.*;
import java.sql.SQLException;
import java.util.List;

public class BotManager {
    private static final BotManager instance = new BotManager();

    /** Compatibility alias for the Agent-owned runtime config. */
    public static AgentRuntimeConfig.Config cfg = AgentRuntimeConfig.cfg;

    public static BotManager getInstance() { return instance; }

    // Public facade for the !botcfg GM command.
    public static List<String> botCombatConfigLines() { return AgentCombatConfig.configFieldLines(); }
    public static String botCombatConfigLine(String name) { return AgentCombatConfig.configFieldLine(name); }
    public static String setBotCombatConfig(String name, String value) { return AgentCombatConfig.setConfigField(name, value); }


    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void registerBot(int ownerCharId, Character owner, Character bot) {
        AgentInteractionRuntime.registerAgent(ownerCharId, owner, bot);
    }

    public BotEntry registerSpawnedBot(int ownerCharId, Character owner, Character bot) {
        return AgentInteractionRuntime.registerSpawnedAgent(ownerCharId, owner, bot);
    }

    /** Spawn a registered bot for the given owner, placing it at the owner's current position in follow mode. */
    public AgentLifecycleService.AgentSpawnResult spawnBotForOwner(Character owner, String botName) {
        return AgentInteractionRuntime.spawnAgentForLeader(owner, botName);
    }

    public void joinBotToOwnerParty(Character owner, Character bot) {
        AgentPartyLifecycleService.joinAgentToLeaderParty(owner, bot);
    }

    public Character loadOfflineBot(int charId, int world, int channel, MapleMap targetMap, Point desiredPosition) throws SQLException {
        return AgentOfflineLoadRuntime.loadOfflineAgent(charId, world, channel, targetMap, desiredPosition);
    }

    public Point resolveSpawnPosition(MapleMap map, Point desiredPosition) {
        return AgentSpawnPositionService.resolveSpawnPosition(map, desiredPosition);
    }

    public void removeBot(int ownerCharId) {
        AgentRuntimeCleanupService.removeAgentsForLeader(ownerCharId);
    }

    /** Cancel and remove a bot by the bot character's own ID (used during shutdown/disconnect). */
    public boolean removeBotByCharId(int botCharId) {
        return AgentRuntimeCleanupService.removeAgentByCharacterId(botCharId);
    }

    /** Release bot-owned runtime state before this character leaves bot control. */
    public boolean cleanupBotRuntimeState(Character bot) {
        return AgentRuntimeCleanupService.cleanupAgentRuntimeState(bot);
    }

    /** Disown a bot by name - cancels its AI tick and leaves it idle in the map. */
    public boolean dismissBot(int ownerCharId, String botName) {
        return AgentLifecycleChatCommandRuntime.dismissAgent(ownerCharId, botName, AgentBotMovementCommandRuntime::stop);
    }

    /** Recruit an ownerless bot by name into the owner's group. Returns an error string on failure, null on success. */
    public String recruitBot(int ownerCharId, Character owner, String botName) {
        return AgentLifecycleChatCommandRuntime.recruitAgent(ownerCharId, owner, botName, this::registerBot);
    }

    /** Transfer a bot from this owner to another player in the same map. Returns an error string on failure, null on success. */
    public String giveBot(int ownerCharId, Character owner, String botName, String targetName) {
        return AgentLifecycleChatCommandRuntime.transferAgent(
                ownerCharId, owner, botName, targetName, AgentBotMovementCommandRuntime::stop, this::registerBot);
    }

    public Character getActiveOwnerByBotCharId(int botCharId) {
        return AgentRuntimeRegistry.activeLeaderByAgentCharacterId(botCharId);
    }

    public void requestBotPotionCheckSoon(Character bot) {
        AgentPotionCheckRequestService.requestPotionCheckSoon(bot);
    }

    public Character getBot(int ownerCharId) {
        return AgentRuntimeRegistry.firstAgent(ownerCharId);
    }

    BotEntry getFirstBotEntry(int ownerCharId) {
        return AgentRuntimeRegistry.firstEntry(ownerCharId);
    }

    public List<BotEntry> getBotEntries(int ownerCharId) {
        return AgentRuntimeRegistry.entriesForLeader(ownerCharId);
    }

    /** Called when the owner picks up or receives an item; notifies bots that might want it. */
    public void notifyOwnerGainedItem(Character owner, Item item) {
        AgentOwnerItemNotificationService.notifyOwnerGainedItem(owner, item);
    }

    /** Called when a trade recipient receives an item; skips circular own-bot trade scans. */
    public void notifyOwnerGainedTradeItem(Character recipient, Item item, Character source) {
        AgentOwnerItemNotificationService.notifyOwnerGainedTradeItem(recipient, item, source);
    }

    public void notifyNearbyBotsOfScroll(Character source,
                                         client.inventory.Equip.ScrollResult result,
                                         int scrollItemId,
                                         long delayMs) {
        AgentScrollReactionNotificationService.notifyNearbyAgentsOfScroll(source, result, scrollItemId, delayMs);
    }

    public BotEntry getBotEntry(int ownerCharId, String botName) {
        return AgentRuntimeRegistry.findByName(ownerCharId, botName);
    }

    public void syncPartyBotsQuestStart(Character source, Quest quest, int npc) {
        AgentPartyQuestSyncService.syncPartyAgentsQuestStart(source, quest, npc);
    }

    public void syncPartyBotsQuestProgress(Character source, int questId, int infoNumber, String progress) {
        AgentPartyQuestSyncService.syncPartyAgentsQuestProgress(source, questId, infoNumber, progress);
    }

    public void syncPartyBotsQuestComplete(Character source, Quest quest, int npc, Integer selection) {
        AgentPartyQuestSyncService.syncPartyAgentsQuestComplete(source, quest, npc, selection);
    }

    public String manualTradeGreeting() {
        return AgentTradeDialogueService.manualTradeGreeting();
    }

    public void handleChat(Character owner, String message, AgentReplyChannel channel) {
        AgentInteractionRuntime.handleLeaderChat(owner, message, channel);
    }

    public void reloginBot(int charId, int ownerCharId, int world, int channel) {
        AgentInteractionRuntime.reloginAgent(charId, ownerCharId, world, channel);
    }

    /**
     * Whisper-driven command to a specific owned bot. Bypasses the global name-
     * prefix routing in handleChat because the whisper target already identifies
     * the bot uniquely. No-op if target isn't a bot owned by the speaker.
     */
    public void handleWhisperToBot(Character owner, Character target, String message) {
        AgentWhisperCommandService.handleWhisperToAgent(owner, target, message);
    }

    // ===== Owned-bot accessors used by the androidequip.cpp BotEquipHandler =====
    /** Number of bots currently spawned (active) under this owner. */
    public int spawnedBotCount(int ownerCharId) {
        return AgentRuntimeRegistry.activeAgentCountForLeader(ownerCharId);
    }

    /** The Character objects of every spawned bot owned by the given player (empty if none). */
    public List<Character> getOwnedBotCharacters(int ownerCharId) {
        return AgentRuntimeRegistry.activeAgentCharactersForLeader(ownerCharId);
    }

}

