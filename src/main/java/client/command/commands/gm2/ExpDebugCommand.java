/*
    This file is part of the HeavenMS MapleStory Server, commands OdinMS-based
    Copyleft (L) 2016 - 2019 RonanLana

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation version 3 as published by
    the Free Software Foundation. You may not use, modify or distribute
    this program under any other version of the GNU Affero General Public
    License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package client.command.commands.gm2;

import client.Character;
import client.Client;
import client.ExpDebugTracker;
import client.command.Command;

import java.util.List;

/**
 * @expdebug [start|stop|status|report]
 *
 * start  - Begin tracking EXP for all party members (including bots)
 * stop   - Stop tracking and display results + save to log
 * status - Show if tracking is active
 * report - Show current tracking data without stopping
 */
public class ExpDebugCommand extends Command {
    {
        setDescription("Debug EXP gain tracking for party members (including bots). Usage: @expdebug [start|stop|status|report]");
    }

    @Override
    public void execute(Client c, String[] params) {
        Character player = c.getPlayer();

        if (params.length < 1) {
            showHelp(player);
            return;
        }

        String action = params[0];

        switch (action) {
            case "start":
                handleStart(player);
                break;
            case "stop":
                handleStop(player);
                break;
            case "status":
                handleStatus(player);
                break;
            case "report":
                handleReport(player);
                break;
            default:
                showHelp(player);
                break;
        }
    }

    private void showHelp(Character player) {
        player.message("--- @expdebug Help ---");
        player.message("@expdebug start  - Start tracking EXP for party members");
        player.message("@expdebug stop   - Stop tracking, show results, save log");
        player.message("@expdebug status - Show if tracking is enabled");
        player.message("@expdebug report - Show current data without stopping");
        player.message("Logs are saved to: D:/GameServers/Maplestory/Cosmic/logs/expdebug/");
    }

    private void handleStart(Character player) {
        if (ExpDebugTracker.isTrackingEnabled()) {
            player.message("EXP tracking is already active. Use @expdebug stop to finish.");
            return;
        }

        ExpDebugTracker.setTrackingEnabled(true);
        List<ExpDebugTracker.ExpSession> sessions = ExpDebugTracker.startPartyTracking(player);

        player.message("--- EXP Debug Tracking Started ---");
        player.message("Tracking " + sessions.size() + " party members.");
        for (ExpDebugTracker.ExpSession s : sessions) {
            String type = s.isBot ? "[BOT]" : "[CHR]";
            player.message("  " + type + " " + s.characterName + " (Lvl " + s.level + ")");
        }
        player.message("Use @expdebug report to check progress, @expdebug stop to finish.");
    }

    private void handleStop(Character player) {
        if (!ExpDebugTracker.isTrackingEnabled()) {
            player.message("EXP tracking is not active. Use @expdebug start to begin.");
            return;
        }

        ExpDebugTracker.setTrackingEnabled(false);
        List<ExpDebugTracker.ExpSession> sessions = ExpDebugTracker.stopPartyTracking(player);

        if (sessions.isEmpty()) {
            player.message("No party members were being tracked.");
            return;
        }

        // Display results
        List<String> lines = ExpDebugTracker.formatResults(sessions);
        for (String line : lines) {
            player.message(line);
        }

        // Save to file
        String filePath = ExpDebugTracker.saveResultsToFile(sessions);
        player.message("Log saved to: " + filePath);
    }

    private void handleStatus(Character player) {
        if (ExpDebugTracker.isTrackingEnabled()) {
            player.message("EXP tracking is currently ACTIVE.");
        } else {
            player.message("EXP tracking is currently INACTIVE. Use @expdebug start to begin.");
        }
    }

    private void handleReport(Character player) {
        if (!ExpDebugTracker.isTrackingEnabled()) {
            player.message("EXP tracking is not active. Use @expdebug start to begin.");
            return;
        }

        // Get current party sessions for a live report
        List<ExpDebugTracker.ExpSession> sessions = ExpDebugTracker.startPartyTracking(player);
        // Don't stop them, just read current state
        List<String> lines = ExpDebugTracker.formatResults(sessions);
        for (String line : lines) {
            player.message(line);
        }
    }
}
