# Astral Nexus RPG — Paper 1.21.4

Astral Nexus is an optional dungeon adventure for Paper 1.21.4 (Java 21). Normal worlds remain vanilla: RPG menus, progression, rewards, and special items are restricted to the separate dungeon world.

## Portal

Build a vertical portal frame:
- Outer frame: iron blocks, 4 blocks wide and 5 blocks high.
- Opening: 2 blocks wide and 3 blocks high, filled with white stained-glass panes.
- Right-click one of the white panes to enter after the frame is complete.

The portal is a server-side effect with particles and teleportation. It does not require Immersive Portals or a client-side mod.

## Dungeon run

- The plugin creates a separate world named `AstralNexusDungeon` and builds a four-room dungeon.
- Entering starts a run and grants the Astral Guide and Talent Crystal.
- Defeat three enemy waves and the Rift Warden.
- Room clears grant an Astral Essence Flask; the final boss grants a Rift Blade and Warden Heart.
- The player is returned to the exact portal-side location after victory.
- RPG progression and combat bonuses only operate in the dungeon world. Special access items are removed when leaving and are not handed out on ordinary-world login.

## Resource pack

The plugin embeds its custom 96×96 icon textures and resource pack in the JAR. It asks the player to accept the pack when entering the dungeon. It does not replace any `assets/minecraft/` textures.

## Installation

1. Use Paper 1.21.4 with Java 21.
2. Stop the server.
3. Put `AstralNexusRPG-1.20.0.jar` in `plugins/`.
4. Start the server.
5. Build the iron-and-white-glass portal in the normal world and right-click an inner pane.

## Notes

The dungeon world is currently a shared server world. The challenge waves are tracked per player, but this version does not create a private world instance for each player. A server-side plugin cannot reproduce Immersive Portals' true seamless cross-dimensional rendering; that visual requires client support.

## Build

Requires JDK 21 and Gradle 8.11.

```bash
gradle clean build
```

The build generates the resource pack and embeds it into the plugin JAR.
