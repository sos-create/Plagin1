# Astral Nexus RPG — Paper Edition

A server-side RPG plugin for **Paper 1.21.4**. This is an independent Paper implementation inspired by the feature themes of Astral Nexus RPG; it is not the Forge mod and does not require clients to install a mod.

## Design: vanilla survival comes first

- RPG mode is **opt-in per player**: players are untouched until they run `/astral start`.
- Players who never start RPG keep ordinary vanilla survival.
- The plugin does not replace vanilla recipes, tools, blocks, dimensions, hunger, mining, or world progression.
- RPG rewards are stored separately from vanilla experience levels.
- `/astral stop` hides/disables the RPG system for that player.

## Included in the first playable build

- Four archetypes: Mage, Warrior, Ranger, Guardian.
- Separate Astral level, experience, skill points, mana, and stamina.
- One active class ability per archetype, with mana cost and cooldown.
- Four upgradeable skills: power (ability damage), wisdom (maximum and regeneration of mana), vitality (Guardian healing and shield), and focus (shorter ability cooldown).
- RPG profile shows each skill level and the separate mana/stamina pools.
- Passive mana/stamina regeneration while RPG mode is active.
- Astral experience from hostile mob kills and ore mining (only for opted-in players).
- Repeatable-style starter hunt objective: defeat five hostile monsters for Astral XP.
- Player data is stored in Minecraft's persistent player data container.

## Commands

| Command | Description |
|---|---|
| `/astral help` | Show help |
| `/astral start` | Opt in to RPG |
| `/astral choose <mage|warrior|ranger|guardian>` | Choose an archetype |
| `/astral profile` | View RPG progress |
| `/astral cast` | Use your class ability |
| `/astral skill <power|wisdom|vitality|focus>` | Spend a skill point on a skill |
| `/astral quest accept hunt` | Start the five-monster hunt |
| `/astral quest info` | View quest progress |
| `/astral stop` | Disable RPG mode |

## Build

Requires JDK 21 and Gradle 8.10.2 or newer.

```bash
gradle clean build
```

The plugin JAR is created in `build/libs/`. Put it in the server's `plugins` folder and restart Paper 1.21.4.

## Roadmap

The first build is a stable foundation, not a one-to-one port of every Forge feature. Planned expansions include expanded skill trees, artifacts/equipment, quest chains and factions, custom encounters/bosses, optional world events, and admin configuration. New world content will be disabled by default or opt-in so it cannot disrupt ordinary survival.
