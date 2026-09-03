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
package client.command;

import client.Client;
import client.command.commands.gm0.ChangeLanguageCommand;
import client.command.commands.gm0.DisposeCommand;
import client.command.commands.gm0.DropLimitCommand;
import client.command.commands.gm0.EnableAuthCommand;
import client.command.commands.gm0.EquipLvCommand;
import client.command.commands.gm0.GachaCommand;
import client.command.commands.gm0.GmCommand;
import client.command.commands.gm0.HelpCommand;
import client.command.commands.gm0.JoinEventCommand;
import client.command.commands.gm0.LeaveEventCommand;
import client.command.commands.gm0.MapOwnerCommand;
import client.command.commands.gm0.OnlineCommand;
import client.command.commands.gm0.RanksCommand;
import client.command.commands.gm0.RatesCommand;
import client.command.commands.gm0.ReadPointsCommand;
import client.command.commands.gm0.ReportBugCommand;
import client.command.commands.gm0.StaffCommand;
import client.command.commands.gm0.StatDexCommand;
import client.command.commands.gm0.StatIntCommand;
import client.command.commands.gm0.StatLukCommand;
import client.command.commands.gm0.StatStrCommand;
import client.command.commands.gm0.TimeCommand;
import client.command.commands.gm0.ToggleExpCommand;
import client.command.commands.gm0.UptimeCommand;
import client.command.commands.gm1.BossHpCommand;
import client.command.commands.gm1.BuffMeCommand;
import client.command.commands.gm1.DressingRoomCashCommand;
import client.command.commands.gm1.DressingRoomCommand;
import client.command.commands.gm1.GotoCommand;
import client.command.commands.gm1.MobHpCommand;
import client.command.commands.gm1.ResetApCommand;
import client.command.commands.gm1.ResetSpCommand;
import client.command.commands.gm1.WhatDropsFromCommand;
import client.command.commands.gm1.WhoDropsCommand;
import client.command.commands.gm2.ApCommand;
import client.command.commands.gm2.BombCommand;
import client.command.commands.gm2.BuffCommand;
import client.command.commands.gm2.BuffMapCommand;
import client.command.commands.gm2.ClearDropsCommand;
import client.command.commands.gm2.ClearSavedLocationsCommand;
import client.command.commands.gm2.ClearSlotCommand;
import client.command.commands.gm2.DcCommand;
import client.command.commands.gm2.DupeCommand;
import client.command.commands.gm2.EmpowerMeCommand;
import client.command.commands.gm2.GmShopCommand;
import client.command.commands.gm2.HealCommand;
import client.command.commands.gm2.HideCommand;
import client.command.commands.gm2.IdCommand;
import client.command.commands.gm2.ItemCommand;
import client.command.commands.gm2.ItemDropCommand;
import client.command.commands.gm2.JailCommand;
import client.command.commands.gm2.JobCommand;
import client.command.commands.gm2.LevelCommand;
import client.command.commands.gm2.LevelProCommand;
import client.command.commands.gm2.LootCommand;
import client.command.commands.gm2.MaxSkillCommand;
import client.command.commands.gm2.MaxStatCommand;
import client.command.commands.gm2.MobSkillCommand;
import client.command.commands.gm2.ReachCommand;
import client.command.commands.gm2.RechargeCommand;
import client.command.commands.gm2.ResetSkillCommand;
import client.command.commands.gm2.SearchCommand;
import client.command.commands.gm2.SetSlotCommand;
import client.command.commands.gm2.SetStatCommand;
import client.command.commands.gm2.SpCommand;
import client.command.commands.gm2.SummonCommand;
import client.command.commands.gm2.UnBugCommand;
import client.command.commands.gm2.UnHideCommand;
import client.command.commands.gm2.UnJailCommand;
import client.command.commands.gm2.WarpAreaCommand;
import client.command.commands.gm2.WarpCommand;
import client.command.commands.gm2.WarpMapCommand;
import client.command.commands.gm2.WhereaMiCommand;
import client.command.commands.gm3.AirshowCommand;
import client.command.commands.gm3.BanCommand;
import client.command.commands.gm3.BotCfgCommand;
import client.command.commands.gm3.ChatCommand;
import client.command.commands.gm3.CheckDmgCommand;
import client.command.commands.gm3.ClosePortalCommand;
import client.command.commands.gm3.DebuffCommand;
import client.command.commands.gm3.EndEventCommand;
import client.command.commands.gm3.ExpedsCommand;
import client.command.commands.gm3.FaceCommand;
import client.command.commands.gm3.FameCommand;
import client.command.commands.gm3.FlyCommand;
import client.command.commands.gm3.MesoCommand;
import client.command.commands.gm3.GiveNxCommand;
import client.command.commands.gm3.GiveRpCommand;
import client.command.commands.gm3.GiveVpCommand;
import client.command.commands.gm3.HairCommand;
import client.command.commands.gm3.HealMapCommand;
import client.command.commands.gm3.HealPersonCommand;
import client.command.commands.gm3.HpMpCommand;
import client.command.commands.gm3.HurtCommand;
import client.command.commands.gm3.IgnoreCommand;
import client.command.commands.gm3.IgnoredCommand;
import client.command.commands.gm3.InMapCommand;
import client.command.commands.gm3.KillAllCommand;
import client.command.commands.gm3.KillCommand;
import client.command.commands.gm3.KillMapCommand;
import client.command.commands.gm3.MaxEnergyCommand;
import client.command.commands.gm3.MaxHpMpCommand;
import client.command.commands.gm3.MonitorCommand;
import client.command.commands.gm3.MonitorsCommand;
import client.command.commands.gm3.MusicCommand;
import client.command.commands.gm3.MuteMapCommand;
import client.command.commands.gm3.NightCommand;
import client.command.commands.gm3.NoticeCommand;
import client.command.commands.gm3.NpcCommand;
import client.command.commands.gm3.OpenPortalCommand;
import client.command.commands.gm3.PeCommand;
import client.command.commands.gm3.PosCommand;
import client.command.commands.gm3.QuestCompleteCommand;
import client.command.commands.gm3.QuestResetCommand;
import client.command.commands.gm3.QuestStartCommand;
import client.command.commands.gm3.RegenNavCommand;
import client.command.commands.gm3.ReloadDropsCommand;
import client.command.commands.gm3.ReloadEventsCommand;
import client.command.commands.gm3.ReloadMapCommand;
import client.command.commands.gm3.ReloadPortalsCommand;
import client.command.commands.gm3.ReloadShopsCommand;
import client.command.commands.gm3.RipCommand;
import client.command.commands.gm3.SeedCommand;
import client.command.commands.gm3.SpawnBotCommand;
import client.command.commands.gm3.SpawnCommand;
import client.command.commands.gm3.StartEventCommand;
import client.command.commands.gm3.StartMapEventCommand;
import client.command.commands.gm3.StopMapEventCommand;
import client.command.commands.gm3.TimerAllCommand;
import client.command.commands.gm3.TimerCommand;
import client.command.commands.gm3.TimerMapCommand;
import client.command.commands.gm3.ToggleCouponCommand;
import client.command.commands.gm3.UnBanCommand;
import client.command.commands.gm4.BossDropRateCommand;
import client.command.commands.gm4.CakeCommand;
import client.command.commands.gm4.DropRateCommand;
import client.command.commands.gm4.ExpRateCommand;
import client.command.commands.gm4.FishingRateCommand;
import client.command.commands.gm4.ForceVacCommand;
import client.command.commands.gm4.GiveItemCommand;
import client.command.commands.gm4.GiveMesoCommand;
import client.command.commands.gm4.HorntailCommand;
import client.command.commands.gm4.ItemVacCommand;
import client.command.commands.gm4.MesoRateCommand;
import client.command.commands.gm4.PapCommand;
import client.command.commands.gm4.PianusCommand;
import client.command.commands.gm4.PinkbeanCommand;
import client.command.commands.gm4.PlayerNpcCommand;
import client.command.commands.gm4.PlayerNpcRemoveCommand;
import client.command.commands.gm4.PmobCommand;
import client.command.commands.gm4.PmobRemoveCommand;
import client.command.commands.gm4.PnpcCommand;
import client.command.commands.gm4.PnpcRemoveCommand;
import client.command.commands.gm4.ProItemCommand;
import client.command.commands.gm4.QuestRateCommand;
import client.command.commands.gm4.ServerMessageCommand;
import client.command.commands.gm4.SetEqStatCommand;
import client.command.commands.gm4.TravelRateCommand;
import client.command.commands.gm4.ZakumCommand;
import client.command.commands.gm5.DebugCommand;
import client.command.commands.gm5.ClimbCaptureCommand;
import client.command.commands.gm5.IpListCommand;
import client.command.commands.gm5.MobCaptureCommand;
import client.command.commands.gm5.SetCommand;
import client.command.commands.gm5.ShowMoveLifeCommand;
import client.command.commands.gm5.ShowPacketsCommand;
import client.command.commands.gm5.ShowSessionsCommand;
import client.command.commands.gm6.ClearQuestCacheCommand;
import client.command.commands.gm6.AdoptTestAgentCommand;
import client.command.commands.gm6.AgentPopCommand;
import client.command.commands.gm6.AgentSchedulerCommand;
import client.command.commands.gm6.AgentWorldCommand;
import client.command.commands.gm6.KpqTestCommand;
import client.command.commands.gm6.HpqTestCommand;
import client.command.commands.gm6.LpqTestCommand;
import client.command.commands.gm6.LmpqTestCommand;
import client.command.commands.gm6.OpqTestCommand;
import client.command.commands.gm6.PpqTestCommand;
import client.command.commands.gm6.EpqTestCommand;
import client.command.commands.gm6.WarpLpqCommand;
import client.command.commands.gm6.MushroomTestCommand;
import client.command.commands.gm6.BalrogTestCommand;
import client.command.commands.gm6.SecondJobTestCommand;
import client.command.commands.gm6.AgentFieldCommand;
import client.command.commands.gm6.AmherstCommand;
import client.command.commands.gm6.VictoriaCommand;
import client.command.commands.gm6.MapleIslandCommand;
import client.command.commands.gm6.ObserverCommand;
import client.command.commands.gm6.SouthperryCommand;
import client.command.commands.gm6.ClearQuestCommand;
import client.command.commands.gm6.DCAllCommand;
import client.command.commands.gm6.DeleteAgentCommand;
import client.command.commands.gm6.DevtestCommand;
import client.command.commands.gm6.EraseAllPNpcsCommand;
import client.command.commands.gm6.FreeMarketSpotsCommand;
import client.command.commands.gm6.EconomyCommand;
import client.command.commands.gm6.GetAccCommand;
import client.command.commands.gm6.HeapDumpCommand;
import client.command.commands.gm6.HealAgentCommand;
import client.command.commands.gm6.JourneyCommand;
import client.command.commands.gm6.MapPlayersCommand;
import client.command.commands.gm6.SaveAllCommand;
import client.command.commands.gm6.ServerAddChannelCommand;
import client.command.commands.gm6.ServerAddWorldCommand;
import client.command.commands.gm6.ServerHealthCommand;
import client.command.commands.gm6.SecurityEventsCommand;
import client.command.commands.gm6.ServerRemoveChannelCommand;
import client.command.commands.gm6.ServerRemoveWorldCommand;
import client.command.commands.gm6.SetGmLevelCommand;
import client.command.commands.gm6.ShutdownCommand;
import client.command.commands.gm6.SpawnAllPNpcsCommand;
import client.command.commands.gm6.SupplyRateCouponCommand;
import client.command.commands.gm6.TownLifeCommand;
import client.command.commands.gm6.WarpWorldCommand;
import constants.id.MapId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class CommandsExecutor {
    private static final Logger log = LoggerFactory.getLogger(CommandsExecutor.class);
    private static final CommandsExecutor instance = new CommandsExecutor();
    private static final char USER_HEADING = '@';
    private static final char GM_HEADING = '!';

    private final HashMap<String, Command> registeredCommands = new HashMap<>();
    private final List<Pair<List<String>, List<String>>> commandsNameDesc = new ArrayList<>();
    private Pair<List<String>, List<String>> levelCommandsCursor;

    public static CommandsExecutor getInstance() {
        return instance;
    }

    public static boolean isCommand(Client client, String content) {
        char heading = content.charAt(0);
        if (client.getPlayer().isGM()) {
            return heading == USER_HEADING || heading == GM_HEADING;
        }
        return heading == USER_HEADING;
    }

    private CommandsExecutor() {
        registerLv0Commands();
        registerLv1Commands();
        registerLv2Commands();
        registerLv3Commands();
        registerLv4Commands();
        registerLv5Commands();
        registerLv6Commands();
    }

    public List<Pair<List<String>, List<String>>> getGmCommands() {
        return commandsNameDesc;
    }

    public void handle(Client client, String message) {
        if (client.tryacquireClient()) {
            try {
                handleInternal(client, message);
            } finally {
                client.releaseClient();
            }
        } else {
            client.getPlayer().dropMessage(5, "Try again in a while... Latest commands are currently being processed.");
        }
    }

    private void handleInternal(Client client, String message) {
        if (client.getPlayer().getMapId() == MapId.JAIL) {
            client.getPlayer().yellowMessage("You do not have permission to use commands while in jail.");
            return;
        }
        final String splitRegex = "[ ]";
        String[] splitedMessage = message.substring(1).split(splitRegex, 2);
        if (splitedMessage.length < 2) {
            splitedMessage = new String[]{splitedMessage[0], ""};
        }

        client.getPlayer().setLastCommandMessage(splitedMessage[1]);    // thanks Tochi & Nulliphite for noticing string messages being marshalled lowercase
        final String commandName = splitedMessage[0].toLowerCase();
        final String[] lowercaseParams = splitedMessage[1].toLowerCase().split(splitRegex);

        final Command command = registeredCommands.get(commandName);
        if (command == null) {
            client.getPlayer().yellowMessage("Command '" + commandName + "' is not available. See @commands for a list of available commands.");
            return;
        }
        if (client.getPlayer().gmLevel() < command.getRank()) {
            client.getPlayer().yellowMessage("You do not have permission to use this command.");
            return;
        }
        String[] params;
        if (lowercaseParams.length > 0 && !lowercaseParams[0].isEmpty()) {
            params = Arrays.copyOfRange(lowercaseParams, 0, lowercaseParams.length);
        } else {
            params = new String[]{};
        }

        command.execute(client, params);
        log.info("Chr {} used command {}", client.getPlayer().getName(), command.getClass().getSimpleName());
    }

    private void addCommandInfo(String name, Class<? extends Command> commandClass) {
        try {
            levelCommandsCursor.getRight().add(commandClass.getDeclaredConstructor().newInstance().getDescription());
            levelCommandsCursor.getLeft().add(name);
        } catch (Exception e) {
            monitoring.RuntimeFailureLogger.log(e);
        }
    }

    private void addCommand(String[] syntaxs, Class<? extends Command> commandClass) {
        for (String syntax : syntaxs) {
            addCommand(syntax, 0, commandClass);
        }
    }

    private void addCommand(String syntax, Class<? extends Command> commandClass) {
        //for (String syntax : syntaxs){
        addCommand(syntax, 0, commandClass);
        //}
    }

    private void addCommand(String[] surtaxes, int rank, Class<? extends Command> commandClass) {
        for (String syntax : surtaxes) {
            addCommand(syntax, rank, commandClass);
        }
    }

    private void addCommand(String syntax, int rank, Class<? extends Command> commandClass) {
        if (registeredCommands.containsKey(syntax.toLowerCase())) {
            log.warn("Error on register command with name: {}. Already exists.", syntax);
            return;
        }

        String commandName = syntax.toLowerCase();
        addCommandInfo(commandName, commandClass);

        try {
            Command commandInstance = commandClass.getDeclaredConstructor().newInstance();     // thanks Halcyon for noticing commands getting reinstanced every call
            commandInstance.setRank(rank);

            registeredCommands.put(commandName, commandInstance);
        } catch (Exception e) {
            log.warn("Failed to create command instance", e);
        }
    }

    private void registerLv0Commands() {
        levelCommandsCursor = new Pair<>(new ArrayList<String>(), new ArrayList<String>());

        addCommand(new String[]{"help", "commands"}, HelpCommand.class);
        addCommand("droplimit", DropLimitCommand.class);
        addCommand("time", TimeCommand.class);
        addCommand("credits", StaffCommand.class);
        addCommand("uptime", UptimeCommand.class);
        addCommand("gacha", GachaCommand.class);
        addCommand("dispose", DisposeCommand.class);
        addCommand("language", ChangeLanguageCommand.class);
        addCommand("equiplv", EquipLvCommand.class);
        addCommand("rates", RatesCommand.class);
        addCommand("online", OnlineCommand.class);
        addCommand("gm", GmCommand.class);
        addCommand("reportbug", ReportBugCommand.class);
        addCommand("points", ReadPointsCommand.class);
        addCommand("joinevent", JoinEventCommand.class);
        addCommand("leaveevent", LeaveEventCommand.class);
        addCommand("ranks", RanksCommand.class);
        addCommand("enableauth", EnableAuthCommand.class);
        addCommand("mapowner", MapOwnerCommand.class);
        addCommand("bosshp", BossHpCommand.class);
        addCommand("mobhp", MobHpCommand.class);
        addCommand("mobdrops", WhatDropsFromCommand.class);
        addCommand("whodrops", WhoDropsCommand.class);
        addCommand("search", SearchCommand.class);
        addCommand("id", IdCommand.class);
        addCommand("unbug", UnBugCommand.class);

        commandsNameDesc.add(levelCommandsCursor);
    }


    private void registerLv1Commands() {
        levelCommandsCursor = new Pair<>(new ArrayList<String>(), new ArrayList<String>());

        addCommand("str", 1, StatStrCommand.class);
        addCommand("dex", 1, StatDexCommand.class);
        addCommand("int", 1, StatIntCommand.class);
        addCommand("luk", 1, StatLukCommand.class);
        addCommand("dress", 1, DressingRoomCommand.class);
        addCommand("dresscash", 1, DressingRoomCashCommand.class);
        addCommand("loot", 1, LootCommand.class);
        addCommand("goto", 1, GotoCommand.class);
        addCommand("resetap", 1, ResetApCommand.class);
        addCommand("resetsp", 1, ResetSpCommand.class);

        commandsNameDesc.add(levelCommandsCursor);
    }


    private void registerLv2Commands() {
        levelCommandsCursor = new Pair<>(new ArrayList<String>(), new ArrayList<String>());

        addCommand("toggleexp", 2, ToggleExpCommand.class);
        addCommand("mobskill", 2, MobSkillCommand.class);
        addCommand("buffme", 2, BuffMeCommand.class);
        addCommand("recharge", 2, RechargeCommand.class);
        addCommand("whereami", 2, WhereaMiCommand.class);
        addCommand("hide", 2, HideCommand.class);
        addCommand("unhide", 2, UnHideCommand.class);
        addCommand("sp", 2, SpCommand.class);
        addCommand("ap", 2, ApCommand.class);
        addCommand("empowerme", 2, EmpowerMeCommand.class);
        addCommand("buffmap", 2, BuffMapCommand.class);
        addCommand("buff", 2, BuffCommand.class);
        addCommand("cleardrops", 2, ClearDropsCommand.class);
        addCommand("clearslot", 2, ClearSlotCommand.class);
        addCommand("clearsavelocs", 2, ClearSavedLocationsCommand.class);
        addCommand("warp", 2, WarpCommand.class);
        addCommand("heal", 2, HealCommand.class);
        addCommand("item", 2, ItemCommand.class);
        addCommand("dupe", 2, DupeCommand.class);
        addCommand("drop", 2, ItemDropCommand.class);
        addCommand("level", 2, LevelCommand.class);
        addCommand("levelpro", 2, LevelProCommand.class);
        addCommand("setslot", 2, SetSlotCommand.class);
        addCommand("setstat", 2, SetStatCommand.class);
        addCommand("maxstat", 2, MaxStatCommand.class);
        addCommand("maxskill", 2, MaxSkillCommand.class);
        addCommand("resetskill", 2, ResetSkillCommand.class);
        addCommand("job", 2, JobCommand.class);
        addCommand("fly", 2, FlyCommand.class);
        addCommand("pos", 2, PosCommand.class);
        addCommand("maxenergy", 2, MaxEnergyCommand.class);
        addCommand("startquest", 2, QuestStartCommand.class);
        addCommand("completequest", 2, QuestCompleteCommand.class);
        addCommand("resetquest", 2, QuestResetCommand.class);
        addCommand("meso", 2, MesoCommand.class);

        commandsNameDesc.add(levelCommandsCursor);
    }

    private void registerLv3Commands() {
        levelCommandsCursor = new Pair<>(new ArrayList<String>(), new ArrayList<String>());

        addCommand("dc", 3, DcCommand.class);
        addCommand(new String[]{"warphere", "summon"}, 3, SummonCommand.class);
        addCommand(new String[]{"warpto", "follow"}, 3, ReachCommand.class);
        addCommand("gmshop", 3, GmShopCommand.class);
        addCommand("jail", 3, JailCommand.class);
        addCommand("unjail", 3, UnJailCommand.class);
        addCommand("debuff", 3, DebuffCommand.class);
        addCommand("spawn", 3, SpawnCommand.class);
        addCommand("mutemap", 3, MuteMapCommand.class);
        addCommand("checkdmg", 3, CheckDmgCommand.class);
        addCommand("inmap", 3, InMapCommand.class);
        addCommand("music", 3, MusicCommand.class);
        addCommand("togglewhitechat", 3, ChatCommand.class);
        addCommand("expeditions", 3, ExpedsCommand.class);
        addCommand("seed", 3, SeedCommand.class);
        addCommand("killall", 3, KillAllCommand.class);
        addCommand("notice", 3, NoticeCommand.class);
        addCommand("rip", 3, RipCommand.class);
        addCommand("openportal", 3, OpenPortalCommand.class);
        addCommand("closeportal", 3, ClosePortalCommand.class);
        addCommand("startevent", 3, StartEventCommand.class);
        addCommand("endevent", 3, EndEventCommand.class);
        addCommand("startmapevent", 3, StartMapEventCommand.class);
        addCommand("stopmapevent", 3, StopMapEventCommand.class);
        addCommand("ban", 3, BanCommand.class);
        addCommand("healmap", 3, HealMapCommand.class);
        addCommand("healplayer", 3, HealPersonCommand.class);
        addCommand("killmap", 3, KillMapCommand.class);
        addCommand("night", 3, NightCommand.class);
        addCommand("npc", 3, NpcCommand.class);
        addCommand("timer", 3, TimerCommand.class);
        addCommand("timermap", 3, TimerMapCommand.class);
        addCommand("warpmap", 3, WarpMapCommand.class);
        addCommand("warparea", 3, WarpAreaCommand.class);
        addCommand("zakum", 3, ZakumCommand.class);
        addCommand("horntail", 3, HorntailCommand.class);
        addCommand("pinkbean", 3, PinkbeanCommand.class);
        addCommand("papulatus", 3, PapCommand.class);
        addCommand("pianus", 3, PianusCommand.class);
        addCommand("cake", 3, CakeCommand.class);
        addCommand("mapplayers", 3, MapPlayersCommand.class);

        commandsNameDesc.add(levelCommandsCursor);
    }

    private void registerLv4Commands() {
        levelCommandsCursor = new Pair<>(new ArrayList<String>(), new ArrayList<String>());

        addCommand("bomb", 4, BombCommand.class);
        addCommand("hpmp", 4, HpMpCommand.class);
        addCommand("maxhpmp", 4, MaxHpMpCommand.class);
        addCommand("ignore", 4, IgnoreCommand.class);
        addCommand("ignored", 4, IgnoredCommand.class);
        addCommand("fame", 4, FameCommand.class);
        addCommand("givenx", 4, GiveNxCommand.class);
        addCommand("givevp", 4, GiveVpCommand.class);
        addCommand("giverp", 4, GiveRpCommand.class);
        addCommand("givemeso", 4, GiveMesoCommand.class);
        addCommand("giveitem", 4, GiveItemCommand.class);
        addCommand("kill", 4, KillCommand.class);
        addCommand("unban", 4, UnBanCommand.class);
        addCommand("hurt", 4, HurtCommand.class);
        addCommand("face", 4, FaceCommand.class);
        addCommand("hair", 4, HairCommand.class);
        addCommand("timerall", 4, TimerAllCommand.class);
        addCommand("itemvac", 4, ItemVacCommand.class);

        commandsNameDesc.add(levelCommandsCursor);
    }

    private void registerLv5Commands() {
        levelCommandsCursor = new Pair<>(new ArrayList<String>(), new ArrayList<String>());

        addCommand("reloadevents", 5, ReloadEventsCommand.class);
        addCommand("reloaddrops", 5, ReloadDropsCommand.class);
        addCommand("reloadportals", 5, ReloadPortalsCommand.class);
        addCommand("reloadmap", 5, ReloadMapCommand.class);
        addCommand("reloadshops", 5, ReloadShopsCommand.class);
        addCommand("monitor", 5, MonitorCommand.class);
        addCommand("monitors", 5, MonitorsCommand.class);
        addCommand("togglecoupon", 5, ToggleCouponCommand.class);
        addCommand("pe", 5, PeCommand.class);
        addCommand("servermessage", 5, ServerMessageCommand.class);
        addCommand("proitem", 5, ProItemCommand.class);
        addCommand("seteqstat", 5, SetEqStatCommand.class);
        addCommand("exprate", 5, ExpRateCommand.class);
        addCommand("mesorate", 5, MesoRateCommand.class);
        addCommand("droprate", 5, DropRateCommand.class);
        addCommand("bossdroprate", 5, BossDropRateCommand.class);
        addCommand("questrate", 5, QuestRateCommand.class);
        addCommand("travelrate", 5, TravelRateCommand.class);
        addCommand("fishrate", 5, FishingRateCommand.class);
        addCommand("forcevac", 5, ForceVacCommand.class);
        addCommand("playernpc", 5, PlayerNpcCommand.class);
        addCommand("playernpcremove", 5, PlayerNpcRemoveCommand.class);
        addCommand("pnpc", 5, PnpcCommand.class);
        addCommand("pnpcremove", 5, PnpcRemoveCommand.class);
        addCommand("pmob", 5, PmobCommand.class);
        addCommand("pmobremove", 5, PmobRemoveCommand.class);
        addCommand("debug", 5, DebugCommand.class);
        addCommand("climbcapture", 5, ClimbCaptureCommand.class);
        addCommand("mobcapture", 5, MobCaptureCommand.class);
        addCommand("set", 5, SetCommand.class);
        addCommand("showpackets", 5, ShowPacketsCommand.class);
        addCommand("showmovelife", 5, ShowMoveLifeCommand.class);
        addCommand("showsessions", 5, ShowSessionsCommand.class);
        addCommand("iplist", 5, IpListCommand.class);
        addCommand("warpworld", 5, WarpWorldCommand.class);
        addCommand("saveall", 5, SaveAllCommand.class);
        addCommand("getacc", 5, GetAccCommand.class);
        addCommand("clearquestcache", 5, ClearQuestCacheCommand.class);
        addCommand("clearquest", 5, ClearQuestCommand.class);
        addCommand("supplyratecoupon", 5, SupplyRateCouponCommand.class);
        addCommand("serverhealth", 5, ServerHealthCommand.class);
        addCommand("heapdump", 5, HeapDumpCommand.class);
        addCommand("spawnallpnpcs", 5, SpawnAllPNpcsCommand.class);
        addCommand("devtest", 5, DevtestCommand.class);
        addCommand("securityevents", 5, SecurityEventsCommand.class);
        addCommand("fmspots", 5, FreeMarketSpotsCommand.class);

        commandsNameDesc.add(levelCommandsCursor);
    }

    private void registerLv6Commands() {
        levelCommandsCursor = new Pair<>(new ArrayList<String>(), new ArrayList<String>());

        addCommand("adopttestagent", 6, AdoptTestAgentCommand.class);
        addCommand("healagent", 6, HealAgentCommand.class);
        addCommand("deleteagent", 6, DeleteAgentCommand.class);
        addCommand("spawnbot", 6, SpawnBotCommand.class);
        addCommand("botcfg", 6, BotCfgCommand.class);
        addCommand("airshow", 6, AirshowCommand.class);
        addCommand("regennav", 6, RegenNavCommand.class);
        addCommand("setgmlevel", 6, SetGmLevelCommand.class);
        addCommand("dcall", 6, DCAllCommand.class);
        addCommand("shutdown", 6, ShutdownCommand.class);
        addCommand("eraseallpnpcs", 6, EraseAllPNpcsCommand.class);
        addCommand("addchannel", 6, ServerAddChannelCommand.class);
        addCommand("addworld", 6, ServerAddWorldCommand.class);
        addCommand("removechannel", 6, ServerRemoveChannelCommand.class);
        addCommand("removeworld", 6, ServerRemoveWorldCommand.class);
        addCommand("agentpop", 6, AgentPopCommand.class);
        addCommand("agentscheduler", 6, AgentSchedulerCommand.class);
        addCommand("agentworld", 6, AgentWorldCommand.class);
        addCommand("kpqtest", 6, KpqTestCommand.class);
        addCommand("hpqtest", 6, HpqTestCommand.class);
        addCommand("lpqtest", 6, LpqTestCommand.class);
        addCommand("lmpqtest", 6, LmpqTestCommand.class);
        addCommand("opqtest", 6, OpqTestCommand.class);
        addCommand("ppqtest", 6, PpqTestCommand.class);
        addCommand("epqtest", 6, EpqTestCommand.class);
        addCommand("warplpq", 6, WarpLpqCommand.class);
        addCommand("mushroomtest", 6, MushroomTestCommand.class);
        addCommand("balrogtest", 6, BalrogTestCommand.class);
        addCommand("secondjobtest", 6, SecondJobTestCommand.class);
        addCommand("agentfield", 6, AgentFieldCommand.class);
        addCommand("townlife", 6, TownLifeCommand.class);
        addCommand("amherst", 6, AmherstCommand.class);
        addCommand("victoria", 6, VictoriaCommand.class);
        addCommand("journey", 6, JourneyCommand.class);
        addCommand("mapleisland", 6, MapleIslandCommand.class);
        addCommand("observer", 6, ObserverCommand.class);
        addCommand("southperry", 6, SouthperryCommand.class);
        addCommand("economy", 6, EconomyCommand.class);

        commandsNameDesc.add(levelCommandsCursor);
    }

}
