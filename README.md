# Cosmic Agents

Cosmic Agents is an experimental Global MapleStory v83 server project built on
top of [P0nk/Cosmic](https://github.com/P0nk/Cosmic). It keeps Cosmic's server
foundation while reconstructing the AI companion/bot work from
[nutnnut/Cosmic](https://github.com/nutnnut/Cosmic) into a cleaner, modular,
server-validated Agent platform.

The long-term goal is a living server population: Agents that can navigate,
fight, loot, quest, shop, trade, build personal profiles, participate in the
economy, and eventually accept LLM-directed high-level goals while still obeying
the same server-side validation rules as normal gameplay.

> Status: active reconstruction. This branch is for architecture, tooling, and
> research toward autonomous Agents. Treat it as experimental rather than a
> production-ready server release.

## Credits And Upstream Lineage

- [P0nk/Cosmic](https://github.com/P0nk/Cosmic) is the upstream v83 server base.
  Cosmic was created and maintained by Ponk with contributions from the wider
  Cosmic community.
- [nutnnut/Cosmic](https://github.com/nutnnut/Cosmic) is the source of the AI
  companion/bot baseline being reconstructed here.
- Cosmic itself descends from the OdinMS and HeavenMS emulator lineage.

This repository is not the official Cosmic project. It is a research fork for
Agent architecture, tooling, cataloging, and future autonomy work.

## Reconstruction Baseline

The Agent reconstruction started from nutnnut's `source/master` branch:

- nutnnut/Cosmic baseline: `5f0447172e501d85bc406be2599f0a010a82a2d2`
- P0nk/Cosmic comparison baseline: `fec53bc7714dc0f1ae3f50b2986cdf2727e0912a`
- Reconstruction branch: `reconstruction/source-master-agent-base`

That nutnnut `source/master` baseline already included the merge commit
`8987c5762 Merge branch 'experimental'`. For this project, that merged master is
treated as the legacy behavior baseline to preserve first, then reshape.

Not every feature from every nutnnut branch is imported here. Branches such as
`source/dev`, `source/fix-queststatus-notstarted-bloat`,
`source/fix-monster-crystal-level`, and `source/mob-rate` are review sources,
not automatically merged behavior. They should be evaluated and ported
selectively after the Agent reconstruction boundary is stable.

WZ asset differences from nutnnut are also not adopted wholesale. The intended
base is Cosmic-compatible server/client data, with targeted asset changes only
when catalog, foothold, navigation, or gameplay review shows they are needed.

## Project Vision

The reconstruction takes a useful but tightly-coupled companion/bot system and
turns it into an Agent engine with clearer ownership:

```text
Old shape:
  one large bot system with many behaviors tied together

Target shape:
  modular Agent runtime
  + capabilities
  + server adapters
  + policies
  + events
  + catalogs
  + profiles
  + future LLM control
```

The first win is structure, not flashy new gameplay. The current philosophy is:

```text
Preserve behavior.
Then improve structure.
Then prove core capabilities.
Then optimize scale.
Then add deeper autonomy.
```

## What This Repository Contains

### Agent Engine Reconstruction

The old bot runtime is being moved into `server.agents` with separated modules
for runtime state, commands, dialogue, integration, movement, combat, loot, and
future capabilities.

The intended design uses:

- capability modules for focused behavior such as navigation, combat, loot,
  inventory, NPC interaction, questing, shops, and trade.
- adapter boundaries so server-specific code stays isolated from portable Agent
  logic.
- event-driven updates so profiles, telemetry, and future learning systems can
  react without living inside the core tick loop.
- policy and validator layers so Agents still respect range, level,
  prerequisites, map state, item state, and server rules.
- plan/objective cards so Agents can pursue structured goals such as Amherst or
  Maple Island questline milestones.

### Catalog Platform

The catalog work is the knowledge layer for Agents and future LLM tooling. It is
intended to build fast lookup data from WZ/XML, SQL tables, scripts, and
overrides without taxing the live game server.

Catalogs are planned to answer questions such as:

- where an NPC exists and what actions it supports.
- which quests can start or complete at an NPC.
- what map, mob, drop, shop, reward, reactor, or portal data exists.
- where an Agent can stand near an NPC for more natural interaction.
- what dialogue length or interaction delay should be simulated.
- how a future LLM can query game knowledge safely through a middle layer.

### Database Console

This branch also contains the Database Console work: a web-based admin interface
for inspecting and editing game data through an API instead of directly editing
tables by hand.

Its phase-one direction includes:

- accounts, characters, stats, AP/SP, location, inventory, storage, equipment,
  appearance, quests, and monster book data.
- mobs, item catalogs, drop tables, NPC shops, and gachapon pools.
- right-dock detail views, MapleStory icons, autocomplete for game IDs, and
  safer UI workflows for common edits.
- future database-backed overrides for selected hardcoded or script-backed
  reward systems.

Do not expose the Database Console publicly without adding proper authentication
and network hardening. It is powerful admin tooling.

### Future Platforms

Several systems are documented for later implementation after the Agent runtime
is stable:

- Agent profile system for personalities, builds, preferences, relationship
  memory, decision journals, and self-adaptation.
- economy engine for dynamic demand, prices, item valuation, taxes, inflation,
  market manipulation resistance, and Agent market behavior.
- server console for runtime configuration, map overrides, spawn policy, travel
  rules, analytics, and operational controls.
- LLM control layer where an LLM assigns high-level tasks while the Agent engine
  handles validated low-level execution.
- scaling runtime for thousands of Agents through visibility-aware simulation,
  background shortcuts, route ETA, batched persistence, and load shedding.

## Current Roadmap

1. Finish reconstructing nutnnut's original bot behavior into the Agent runtime.
2. Prove a capability-first baseline with Amherst MVP, then Maple Island
   questline MVP.
3. Decide which legacy nutnnut behavior remains enabled and which becomes
   legacy/off-by-default.
4. Optimize the Agent engine toward the target of roughly 2,000 concurrent
   Agents while keeping player responsiveness stable.
5. Expand profiles, economy, plan cards, catalogs, and LLM integration once the
   base engine is proven.

## Documentation Map

Useful project documents:

- [Agent Engine Vision And Reconstruction Overview](docs/agents/AGENT_ENGINE_VISION_AND_RECONSTRUCTION_OVERVIEW.md)
- [Post-Reconstruction Agent Platform Specification](docs/agents/POST_RECONSTRUCTION_AGENT_PLATFORM_SPECIFICATION.md)
- [Agent Reconstruction Baseline](docs/agents/RECONSTRUCTION_BASELINE.md)
- [Agent Fix TODO](docs/agents/AGENT_FIX_TODO.md)
- [Agent Engine Scaling Track](docs/agents/AGENT_ENGINE_SCALING_TRACK.md)
- [Catalog Platform Architecture](docs/agents/catalog-platform/CATALOG_PLATFORM_ARCHITECTURE.md)
- [Database Console UI Design](docs/consoles/DATABASE_CONSOLE_UI_DESIGN.md)
- [Server Console Scope](docs/consoles/SERVER_CONSOLE_SCOPE.md)
- [nutnnut Over Cosmic Review](docs/NUTNNUT_OVER_COSMIC_REVIEW.md)

## Original Cosmic Setup Notes

The setup notes below are inherited from P0nk/Cosmic and remain useful for
running the base server locally.

## Getting started
Follow along as I go through the steps to play the game on your local computer from start to finish. I won't go into extreme detail, so if you don't have prior experience with Java or git, you might struggle.

We will set up the following:
- Database - the database is used by the server to store game data such as accounts, characters and inventory items.
- Server - the server is the "brain" and routes network traffic between the clients.
- Client - the client is the application used to _play the game_, i.e. MapleStory.exe.

### 1 - Database 
You will start by installing the database server and database client. Then you will connect to the server with the client to create a new database schema.

#### Steps

1. Download and install [MySQL Community Server 8+](https://dev.mysql.com/downloads/mysql/). You will have to set a root password. Make sure you don't lose it because you will need it later.
2. Download and install [HeidiSQL](https://www.heidisql.com/download.php).
3. Connect to the database: 
   1. Open HeidiSQL
   2. Create a new Session: "New" -> fill in your password -> "Save"
   3. Connect to the database: click on your saved session -> "Open"
4. Create a new database schema:
   1. In the opened session, right-click on the session name in the menu on the left
   2. "Create new" -> "Database" -> database name should be "cosmic" -> "OK"
5. Done. The database is now ready. Once the Cosmic server starts, it will create tables and populate some of them with initial data.

### 2 - Server
You will start by cloning the repository, then configure the database properties and lastly start the server.

#### Prerequisites
* Java 21 (I recommend [Amazon Corretto](https://aws.amazon.com/corretto))
* IDE (I recommend [IntelliJ IDEA](https://www.jetbrains.com/idea/))

#### Steps

1. Clone Cosmic into a new project. In IntelliJ, you would create a new project from version control.
2. Open _config.yaml_. Find "DB_PASS" and set it to your database root user password.
3. Start the server. The main method is located in `net.server.Server`.
4. If you see "Cosmic is now online" in the console, it means the server is online and ready to serve traffic. Yay!

Below, I list other ways of running the server which are completely optional.

#### Docker
Support for Docker is also provided out of the box, as an alternative to running straight in the IDE. If you have [Docker Desktop](https://www.docker.com/products/docker-desktop/) installed it's as easy as running `docker compose up`.

Making changes becomes a bit more tedious though as you have to rebuild the server image via `docker compose up --build`.

#### Jar
Another option is to start the server from a terminal by running a jar file. You first need to build the jar file from source which requires [Maven](https://maven.apache.org/). Fortunately, [Maven Wrapper](https://maven.apache.org/wrapper/) is provided so you don't have to install Maven separately.

Building the jar file is as easy as running ``./mvnw.cmd clean package``. The project is configured to produce a "fat" jar which contains all dependencies (by utilizing the _maven-assembly-plugin_). Note that the WZ XML files are __not__ included in the jar.

To run the jar, a ``launch.bat`` file is provided for convenience. Simply double-click it and the server will start in a new terminal window. 

Alternatively, run the jar file from the terminal. Just remember to provide the `wz-path` system property pointing to your wz directory.

### 3 - Client
The client files are located in a separate repository: https://github.com/P0nk/Cosmic-client

Follow the installation guide in the README.

### 4 - Getting into the game
You have successfully started the client, and you're looking at the login screen. 

#### Logging in
At this point, you can log in to the admin account using the following credentials:
* Username: "admin"
* Password: "admin"
* Pin: "0000"
* Pic: "000000"

You can also create a new regular account by typing in your desired username & password and attempting to log in. This "automatic registration" feature lets you create new accounts to play around with. It is enabled by default (see _config.yaml_).

#### Entering the game
Create a new character as you normally would, and then select it to enter the game. Hooray, finally we're in!

If you log in to the "Admin" character, you'll notice that the character looks almost invisible. This is hide mode, which is enabled by default when you log in to a GM character. You won't be visible to normal players and no mobs will move if you're alone on the map. Toggle hide mode on or off by typing "@hide" in the in-game chat.

Hide is one of many commands available to players, type "@commands" to see the full list. Higher ranked GMs have access to more powerful commands.

That's it, have fun playing around in game! 

## Advanced concepts
Some slightly more advanced concepts that might be useful once you're up and running.

### Host on remote server
You don't have to host the server on your local machine to play. It's possible to host on a remote server such as a VPS or a dedicated server.

I leave it to you to figure out the server hosting part, but once you have that running you'll need to edit the client ip to point to your remote server ip.

### WZ files
WZ files are the asset/data files required by the client and server. Typically, the [HaRepacker-resurrected](https://github.com/lastbattle/Harepacker-resurrected) tool is used to manage (view, edit, export) the .wz files.

The client can read the .wz files directly, but the server requires them to be in XML format. The server does not make use of the sprites, which is the motivation for different kinds of exporting. 
HaRepacker allows you to export to "Private server", which is the .img files packaged in the .wz stripped of sprites and converted to XML. This takes much less disk space.

This server requires custom .wz files (unfortunately), as you may have noted during installation of the client. The intention is for these to be removed eventually and to solely run on vanilla .wz files.

#### WZ editing
* Use the HaRepacker-resurrected editor, encryption "GMS (old)".
* Open the desired .wz for editing and use the node hierarchy to make the desired changes (copy/pasting nodes may be unreliable in rare scenarios).
* Save the changed .wz, overwriting the original content at the client folder.
* Finally, re-export (using the "Private Server" exporting option) the changed XMLs into the server's .wz XML files (found in the "wz" directory), overwriting the old contents.

Make sure to always export from the client .wz files to the server XML, and not the other way around. 

Editing the client .wz without exporting to the server may lead to strange behavior.
