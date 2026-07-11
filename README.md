# Cosmic Agents

Cosmic Agents is a Global MapleStory v83 server project built on top of
[P0nk/Cosmic](https://github.com/P0nk/Cosmic). It extends Cosmic with a modular,
server-validated Agent platform for autonomous characters that remain normal
Cosmic characters and obey server-side gameplay rules.

The behavior-preserving Agent reconstruction and 12-phase runtime hardening are
complete on `master`. Production and test `server.bots` packages are absent;
Agent behavior is owned by modules under `server.agents`.

## Credits And Upstream Lineage

- [P0nk/Cosmic](https://github.com/P0nk/Cosmic) is the upstream v83 server base.
  Cosmic was created and maintained by Ponk with contributions from the wider
  Cosmic community.
- [nutnnut/Cosmic](https://github.com/nutnnut/Cosmic) provided the companion
  behavior from which the reconstructed Agent runtime was derived.
- Cosmic itself descends from the OdinMS and HeavenMS emulator lineage.

This repository is not the official Cosmic project.

## Current State

The Agent implementation is divided into explicit ownership areas:

- `server.agents.runtime` owns live sessions, lifecycle, state, tick
  orchestration, scheduling, and registries.
- `server.agents.capabilities` owns movement, navigation, combat, loot,
  supplies, inventory, equipment, trade, shops, builds, dialogue, social,
  quest, NPC, and reactor behavior.
- `server.agents.commands` owns command parsing and coordination.
- `server.agents.plans` contains plan models and retained script execution.
- `server.agents.integration` defines server-facing gateways.
- `server.agents.integration.cosmic` contains Cosmic-specific adapters.
- `server.agents.monitoring` exposes runtime, scheduler, parity, and queue
  diagnostics.

Agent runtime hardening includes bounded asynchronous queues, session-scoped
callbacks, lifecycle cleanup, safe navigation-cache persistence, weighted graph
cache eviction, concurrent combat-profile caching, secure backing-account
provisioning, per-Agent mailbox support, and a central scheduler implementation.
The mailbox and central scheduler remain disabled by default; the established
compatibility paths remain active.

The repository also contains read-only game-data catalog tooling and a Database
Console for administrative game-data access. The Database Console should not be
exposed publicly without authentication and network hardening.

## Validation Status

- Full Maven suite: 4,392 tests passed with no failures, errors, or skips.
- Maven package build: passed.
- Production and test `server.bots` directories: absent.
- Production `server.bots` imports and runtime dependencies: absent.
- Automated scheduler parity and deterministic 500-session scheduler soak:
  passed.
- Live-client parity and long-duration production soak: not yet recorded.

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
