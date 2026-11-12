# Astral Nexus RPG 1.26.0 — Paper 1.21.4

Astral Nexus is an optional fantasy-RPG dungeon adventure for Paper 1.21.4 and Java 21. The dungeon pool contains **exactly three maps**. Older room/cavern variants are not selectable; these three routes are the only generated trials.

## Three long caves with elevation changes

All three routes are long underground caves with a natural descent into lower rock and a gradual climb back out. The route elevation is generated as a smooth profile: the entry and exit return to the original level, while the middle sections descend by approximately 13–16 blocks and pass through broad secondary rises and dips. The three caves use different elevation curves so their paths do not all feel alike.

1. **Сумрачные галереи** — a forgotten royal mine in deep slate and tuff. The route descends into deep galleries, passes gilded seams and old stone supports, then climbs into the final sanctuary.
2. **Аметистовый разлом** — a long geode passage in calcite and basalt, with amethyst spires growing from the walls and cool crystal light along a more pronounced series of descents and rises.
3. **Изумрудные недра** — a winding underground root cavern with moss shelves, hanging roots, spore blossoms and glowing fungi, following several rolling terraces.

## Continuous terrain and decorations

- The floor is generated at the actual local height for every corridor section.
- The ground underneath each ramp is filled with solid rock, so the sloping path has no hollow underside or gaps.
- Side walls begin below the local floor and follow the changing floor and roof levels.
- Cave arches, columns, crystals, chains, stalactites and lanterns are attached to the local height profile instead of floating at a fixed Y level.
- Side grotto floors and ceilings follow the same vertical curve as the main passage.
- The final boss cavern also follows the slope and has a closed decorative perimeter, with a clear entrance from the main tunnel.
- No water is generated.

The entry pad remains level for safe arrival. The three combat checkpoints and the boss now spawn at the corresponding local floor height, not at the old fixed Y coordinate.

## Two access items — no duplicates

Inside the dungeon the player receives two marked access items:

- **✦ Путеводитель Астрала** — opens the main RPG menu.
- **✧ Кристалл боевых навыков** — opens the class ability menu.

Before granting either item, the plugin checks the player's storage inventory and offhand for its persistent marker. If the item already exists, it is refreshed in place and not granted again. Extra copies from older builds are reduced to one. Only a missing item is issued.

## Clean rebuild for every new trial

Before a new attempt, the plugin clears the complete selected map footprint from floor to ceiling. It removes broken blocks, old decorations, containers, dropped items, projectiles and leftover mobs, then builds a fresh variant of that cave. This prevents changes made during a previous run from leaking into the next one.

A route is reserved while a player is using it. A second player is sent to another free route rather than having the first player's cave erased underneath them. If all three routes are busy, the portal reports that and does not start a conflicting rebuild.

The maps are located in the separate `AstralNexusDungeon` world. Normal worlds, player inventories, levels, coins, passive talents and ability ranks are not deleted.

## Chat and feedback

Automatic dungeon announcements and recurring trial instructions use the action bar instead of flooding the chat. The automatic welcome message on every join is removed.

## Talent tree

The talent menu has three development layers:

1. **Four passive talents**, each rank I–X: Strength (damage), Wisdom (mana pool/regeneration/cost), Vitality (damage reduction and Guardian healing), and Focus (cooldowns).
2. **Four specialization branches**, each rank I–V: Flow, Seal, Resonance and Tempo. They unlock when their linked passive stat reaches rank III.
3. **Four class abilities**, each rank I–V, individually saved for the selected archetype.

Every visible node in the talent tree is upgradeable. The active abilities panel remains separate: ordinary click casts an unlocked spell; Shift+click spends one point to upgrade its rank.

## Installation

1. Use Paper 1.21.4 with Java 21.
2. Stop the server and back up the normal world, player data, plugin folder and existing JAR.
3. Replace the old JAR with `AstralNexusRPG-1.26.0.jar`.
4. Start the server. The new elevation and decoration layout is regenerated automatically when each route is next selected. Normal worlds and character progression remain intact.

## Resource pack

The JAR embeds the menu and item textures. The plugin asks each player to accept the resource pack before applying it. The default URL `http://127.0.0.1:8123/astral-pack.zip` is for a client running on the same computer as the Paper server. Remote players need a public hostname/IP in `plugins/AstralNexusRPG/config.yml` and TCP port 8123 forwarded to the server.

## Build

Requires JDK 21 and Gradle 8.11.

```bash
gradle clean build
```

The build generates and embeds the resource pack.
