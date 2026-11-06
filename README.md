# Astral Nexus RPG — Paper 1.21.4

A server-side RPG plugin inspired by the Astral Nexus RPG mod. It keeps normal Minecraft survival intact: RPG progression is optional and starts only when a player opts in.

## Interface

The player receives **no extra item**. Install the companion Fabric client mod and a separate **✦ Дополнение** button appears above the player's standard inventory. Clicking it sends a small custom plugin message to the Paper server, which opens the RPG menu. `/astral` and `/astral menu` remain optional fallbacks. The 54-slot inventory GUI uses a dark fantasy/astral theme, purple and magenta borders, gold highlights, custom item names and lore, and separate sections for:

- Character profile, level, Astral XP, mana, stamina, and attributes
- Four archetypes: Mage, Warrior, Ranger, Guardian
- Skill upgrades: Power, Wisdom, Vitality, Focus
- Quest journal and monster-hunt progress
- Class abilities and mana/cooldown information

The added button is rendered by the Fabric client mod; the actual RPG menu remains server-controlled by the Paper plugin. The client mod and server plugin communicate over the `astralnexus:open_menu` plugin channel. The GUI itself uses Minecraft inventory widgets, item icons, titles and lore.

## Installation

1. Put `AstralNexusRPG-1.2.0.jar` in the Paper 1.21.4 server's `plugins` folder.
2. On every player's Minecraft 1.21.4 client, install Fabric Loader, Fabric API, and `AstralNexusInventoryButton-1.0.0.jar` in the client's `mods` folder.
3. Restart the Paper server and the Minecraft client. Open the normal player inventory and click **✦ Дополнение** above it.

The inventory button requires the Fabric client mod. The Paper plugin alone cannot insert a clickable control into the vanilla client inventory. The client component is for Fabric, not Forge or NeoForge.

## Gameplay

- RPG mode is opt-in per player with `/astral start`.
- Players who do not activate RPG keep ordinary vanilla survival.
- Separate Astral level, experience, skill points, mana, and stamina.
- One class ability per archetype.
- Astral XP from hostile mob kills and ore mining for opted-in players.
- Starter hunt: defeat five hostile monsters for 120 Astral XP.
- Player progress is stored in persistent player data.

## Commands

| Command | Description |
|---|---|
| `/astral` or `/astral menu` | Open the RPG menu |
| `/astral start` | Activate RPG |
| `/astral choose <mage|warrior|ranger|guardian>` | Choose an archetype |
| `/astral profile` | Open character profile |
| `/astral cast` | Use class ability |
| `/astral skill <power|wisdom|vitality|focus>` | Upgrade a skill |
| `/astral quest accept hunt` | Accept the monster hunt |
| `/astral quest info` | View quest progress |
| `/astral stop` | Disable RPG mode |

## Build

Requires JDK 21 and Gradle 8.10.2 or newer.

```bash
gradle clean build
```

The Paper plugin JAR is created in `build/libs/`. Build the Fabric client component separately with `gradle -p client-mod clean build`; its JAR is created in `client-mod/build/libs/`.
