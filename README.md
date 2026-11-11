# Astral Nexus RPG — Paper 1.21.4

Astral Nexus is an optional fantasy-RPG dungeon adventure for Paper 1.21.4 (Java 21). The ordinary world remains vanilla; the RPG system is activated only in the separate `AstralNexusDungeon` world.

## Portal

Build a vertical portal frame:
- Outer frame: iron blocks, 4 blocks wide and 5 blocks high.
- Opening: 2 blocks wide and 3 blocks high, filled with white stained-glass panes.
- Right-click one of the white panes to enter after the frame is complete.

The portal, menus, and combat effects are server-side. A client-side mod is not required.

## Dungeons

The plugin creates a separate world named `AstralNexusDungeon` and procedurally builds one of three large cave layouts for each run:

- **Зал сокровищ** — a glass dome, central overlook into the lower vault, golden inlays, galleries, a grand switchback staircase and treasure rooms.
- **Кристальная пещера** — a broad cavern, crystal lake, suspended bridges and large faceted amethyst formations.
- **Затонувшие катакомбы** — prismarine canals, a cathedral-like altar, pillars and Gothic ceiling ribs.

Defeat three enemy waves and the Rift Warden. The dungeon preserves inventory and experience on death, shows **ПОТРАЧЕНО**, then returns the player to the saved entry point. Victory also returns the player automatically.

The dungeon world is shared by players; encounters are tracked per player, but this is not a separate private world instance for every player.

## Skills and menus

Four archetypes have their own active abilities. The separate **Кристалл боевых навыков** opens a list of currently unlocked abilities with mana cost, cooldown and ready status. Skill effects use distinct particle shapes and sounds. Talent ranks affect damage, mana cost, cooldown and defense.

## Resource pack and consent

The plugin embeds its custom textures and asks the player to accept the pack when entering the dungeon. It does not override vanilla `assets/minecraft/` textures.

The default URL `http://127.0.0.1:8123/astral-pack.zip` works only when the Minecraft client and Paper server run on the same computer. For players connecting from another device:
1. Set `resource-pack.public-url` in `plugins/AstralNexusRPG/config.yml` to the server's reachable public IP or hostname, for example `http://your-domain.example:8123/astral-pack.zip`.
2. Forward TCP port `8123` through the router/firewall to the Paper server.
3. Restart the server.

A failed remote download now produces a hint when the URL still points to localhost.

## Installation

1. Use Paper 1.21.4 with Java 21.
2. Stop the server and back up the world and current plugin.
3. Replace the old JAR in `plugins/` with `AstralNexusRPG-1.22.1.jar`.
4. Start the server and enter through the iron-and-white-glass portal.

Do not delete `AstralNexusDungeon`. The new `dungeon_built_map_v4_` markers add the latest visual details when each map is next prepared.

## Build

Requires JDK 21 and Gradle 8.11.

```bash
gradle clean build
```

The build generates the embedded resource pack and packages it into the plugin JAR.
