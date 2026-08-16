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

/*
   @Author: Arthur L - Refactored command content into modules
*/
package client.command.commands.gm1;

import client.Character;
import client.Client;
import client.command.Command;
import constants.game.GameConstants;
import constants.id.NpcId;
import server.maps.FieldLimit;
import server.maps.MapFactory;
import server.maps.MapleMap;
import server.maps.MiniDungeonInfo;
import server.maps.Portal;
import server.agents.field.AgentFieldObservationCatalogRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public class GotoCommand extends Command {

    {
        setDescription("Warp to a predefined or numbered observation map.");

        List<Entry<String, Integer>> towns = new ArrayList<>(GameConstants.GOTO_TOWNS.entrySet());
        sortGotoEntries(towns);

        try {
            // thanks shavit for noticing goto areas getting loaded from wz needlessly only for the name retrieval

            for (Map.Entry<String, Integer> e : towns) {
                GOTO_TOWNS_INFO += ("'" + e.getKey() + "' - #b" + (MapFactory.loadPlaceName(e.getValue())) + "#k\r\n");
            }

            List<Entry<String, Integer>> areas = new ArrayList<>(GameConstants.GOTO_AREAS.entrySet());
            sortGotoEntries(areas);
            for (Map.Entry<String, Integer> e : areas) {
                GOTO_AREAS_INFO += ("'" + e.getKey() + "' - #b" + (MapFactory.loadPlaceName(e.getValue())) + "#k\r\n");
            }
        } catch (Exception e) {
            monitoring.RuntimeFailureLogger.log(e);

            GOTO_TOWNS_INFO = "(none)";
            GOTO_AREAS_INFO = "(none)";
        }

    }

    public static String GOTO_TOWNS_INFO = "";
    public static String GOTO_AREAS_INFO = "";

    private static void sortGotoEntries(List<Entry<String, Integer>> listEntries) {
        listEntries.sort((e1, e2) -> e1.getValue().compareTo(e2.getValue()));
    }

    @Override
    public void execute(Client c, String[] params) {
        Character player = c.getPlayer();
        if (params.length < 1) {
            String sendStr = "Syntax: #b@goto <map name>#k. Available areas:\r\n\r\n#rTowns:#k\r\n" + GOTO_TOWNS_INFO;
            if (player.isGM()) {
                sendStr += ("\r\n#rAreas:#k\r\n" + GOTO_AREAS_INFO);
            }

            player.getAbstractPlayerInteraction().npcTalk(NpcId.SPINEL, sendStr);
            return;
        }

        if (!player.isAlive()) {
            player.dropMessage(1, "This command cannot be used when you're dead.");
            return;
        }

        if (!player.isGM()) {
            if (player.getEventInstance() != null || MiniDungeonInfo.isDungeonMap(player.getMapId()) || FieldLimit.CANNOTMIGRATE.check(player.getMap().getFieldLimit())) {
                player.dropMessage(1, "This command can not be used in this map.");
                return;
            }
        }

        if ("map".equalsIgnoreCase(params[0])) {
            executeObservationMapGoto(c, player, params);
            return;
        }

        Map<String, Integer> gotomaps;
        if (player.isGM()) {
            gotomaps = new HashMap<>(GameConstants.GOTO_AREAS);     // distinct map registry for GM/users suggested thanks to Vcoc
            gotomaps.putAll(GameConstants.GOTO_TOWNS);  // thanks Halcyon (UltimateMors) for pointing out duplicates on listed entries functionality
        } else {
            gotomaps = GameConstants.GOTO_TOWNS;
        }

        if (gotomaps.containsKey(params[0])) {
            MapleMap target = c.getChannelServer().getMapFactory().getMap(gotomaps.get(params[0]));

            // expedition issue with this command detected thanks to Masterrulax
            warp(player, target);
        } else {
            // detailed info on goto available areas suggested thanks to Vcoc
            String sendStr = "Area '#r" + params[0] + "#k' is not available. Available areas:\r\n\r\n#rTowns:#k" + GOTO_TOWNS_INFO;
            if (player.isGM()) {
                sendStr += ("\r\n#rAreas:#k\r\n" + GOTO_AREAS_INFO);
            }

            player.getAbstractPlayerInteraction().npcTalk(NpcId.SPINEL, sendStr);
        }
    }

    private static void executeObservationMapGoto(
            Client client, Character player, String[] params) {
        if (!player.isGM()) {
            player.dropMessage(1, "Numbered observation maps are available only to GMs.");
            return;
        }
        AgentFieldObservationCatalogRepository repository =
                AgentFieldObservationCatalogRepository.defaultRepository();
        if (params.length < 2 || "list".equalsIgnoreCase(params[1])) {
            int page = parsePage(params);
            showObservationMapList(player, repository, page);
            return;
        }
        String selector = params[1].toLowerCase(Locale.ROOT);
        AgentFieldObservationCatalogRepository.NumberedMap selected;
        switch (selector) {
            case "next" -> selected = repository.relativeMap(player.getMapId(), 1);
            case "prev", "previous" -> selected = repository.relativeMap(player.getMapId(), -1);
            case "rand", "random" -> selected = randomObservationMap(repository, player.getMapId());
            default -> {
                try {
                    int number = Integer.parseInt(selector);
                    selected = repository.numberedMap(number).orElse(null);
                } catch (NumberFormatException invalid) {
                    selected = null;
                }
                if (selected == null) {
                    player.dropMessage(1, "Use @goto map <1-" + repository.numberedMaps().size()
                            + "|next|prev|rand|list>.");
                    return;
                }
            }
        }
        MapleMap target = client.getChannelServer().getMapFactory()
                .getMap(selected.map().mapId());
        warp(player, target);
        player.dropMessage(5, "Observation map " + selected.number() + '/'
                + repository.numberedMaps().size() + ": " + selected.map().mapName()
                + " (" + selected.map().mapId() + ").");
    }

    private static AgentFieldObservationCatalogRepository.NumberedMap randomObservationMap(
            AgentFieldObservationCatalogRepository repository, int currentMapId) {
        List<AgentFieldObservationCatalogRepository.NumberedMap> maps = repository.numberedMaps();
        var current = repository.numberedMapForMapId(currentMapId);
        if (maps.size() <= 1 || current.isEmpty()) {
            return maps.get(ThreadLocalRandom.current().nextInt(maps.size()));
        }
        int index = ThreadLocalRandom.current().nextInt(maps.size() - 1);
        int currentIndex = current.get().number() - 1;
        return maps.get(index >= currentIndex ? index + 1 : index);
    }

    private static int parsePage(String[] params) {
        if (params.length < 3) return 1;
        try {
            return Math.max(1, Integer.parseInt(params[2]));
        } catch (NumberFormatException invalid) {
            return 1;
        }
    }

    private static void showObservationMapList(
            Character player,
            AgentFieldObservationCatalogRepository repository,
            int requestedPage) {
        int pageSize = 20;
        int pages = (repository.numberedMaps().size() + pageSize - 1) / pageSize;
        int page = Math.min(requestedPage, pages);
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, repository.numberedMaps().size());
        StringBuilder message = new StringBuilder("#rObservation maps " + page + '/'
                + pages + "#k\r\n");
        for (int index = start; index < end; index++) {
            var numbered = repository.numberedMaps().get(index);
            message.append(numbered.number()).append(" - #b")
                    .append(numbered.map().mapName()).append("#k (")
                    .append(numbered.map().mapId()).append(")\r\n");
        }
        message.append("\r\n@goto map <number|next|prev|rand>\r\n")
                .append("@goto map list <1-").append(pages).append('>');
        player.getAbstractPlayerInteraction().npcTalk(NpcId.SPINEL, message.toString());
    }

    private static void warp(Character player, MapleMap target) {
        Portal targetPortal = target.getRandomPlayerSpawnpoint();
        player.saveLocationOnWarp();
        player.changeMap(target, targetPortal);
    }
}
