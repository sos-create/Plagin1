# Astral Nexus RPG — Paper 1.21.4

A server-side RPG plugin inspired by the Astral Nexus RPG mod. It keeps normal Minecraft survival intact: RPG progression is optional and starts only when a player opts in.

## Interface

Use `/astral` or `/astral menu` to open the RPG interface. The inventory GUI uses a dark astral theme with purple accents, gold highlights, and separate sections for:

- Character profile, level, Astral XP, mana, stamina, and attributes
- Four archetypes: Mage, Warrior, Ranger, Guardian
- Skill upgrades: Power, Wisdom, Vitality, Focus
- Quest journal and monster-hunt progress
- Class abilities and mana/cooldown information

The Paper plugin uses Minecraft's inventory interface. It reproduces the visual direction and navigation of the reference mod within the server-side plugin API; it does not load custom client textures or replace the Minecraft main menu.

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

The plugin JAR is created in `build/libs/`. Put it in the `plugins` folder of an existing Paper 1.21.4 server.
