# Astral Nexus RPG 1.24.0 — Paper 1.21.4

Astral Nexus is an optional fantasy-RPG dungeon adventure for Paper 1.21.4 and Java 21. Normal survival worlds are not modified. Character levels, coins, talents, ability ranks and player inventories are stored independently from the regenerated dungeon world.

## Three long, dry cave routes

The old compact rooms, flooded catacombs and crystal lake are removed. There are now three carefully themed underground journeys. Each route runs for roughly 160 blocks before opening into a large final cavern, with three combat checkpoints, side grottoes, natural stone arches, hanging formations and layered lighting.

- **Сумрачные галереи** — an abandoned royal mine in deep slate and tuff, with gilded blackstone seams, massive stone ribs, hanging chains, warm lantern pools and forgotten side shrines.
- **Аметистовый разлом** — a geode-like cavern of calcite and smooth basalt. Amethyst spires grow from the walls, purple crystal details catch the light, and soul lanterns and sea lanterns give the passage a cool magical glow.
- **Изумрудные недра** — a huge underground root forest with moss-covered rock, ancient roots, spore blossoms, luminous fungi and overgrown side chambers.

There is **no water** in any of the generated cave routes. The main passages have continuous floors and open headroom. The final sanctuary is wider than the passages and has a separate boss approach.

## Clean rebuild for every new trial

Before a new attempt, the plugin clears the complete selected map footprint from floor to ceiling. It removes broken blocks, old decorations, containers, dropped items, projectiles and leftover mobs, then builds a fresh variant of that cave. This prevents changes made during a previous run from leaking into the next one.

A route is reserved while a player is using it. A second player is sent to another free route rather than having the first player's cave erased underneath them. If all three routes are busy, the portal reports that and does not start a conflicting rebuild.

On the first start of 1.24.0, the plugin removes the previous dedicated `AstralNexusDungeon` world once so that the old small rooms are discarded. If players are still inside that world, the reset is postponed rather than ejecting them. The reset only affects this plugin's dedicated dungeon world; normal worlds, player inventories, levels, coins, passive talents and ability ranks are not deleted.

## Chat and feedback

Automatic dungeon announcements and the recurring trial instructions no longer flood the chat. Short status messages such as route preparation, wave progress, boss arrival and return feedback appear in the action bar instead. The automatic welcome message on every join has been removed.

## Talent tree

The talent menu has three development layers:

1. **Four passive talents**, each rank I–X: Strength (damage), Wisdom (mana pool/regeneration/cost), Vitality (damage reduction and Guardian healing), and Focus (cooldowns).
2. **Four specialization branches**, each rank I–V: Flow, Seal, Resonance and Tempo. They unlock when their linked passive stat reaches rank III.
3. **Four class abilities**, each rank I–V, individually saved for the selected archetype.

Every visible node in the talent tree is upgradeable. Investing a point changes the real spell/stat calculations. The active abilities panel remains separate: ordinary click casts an unlocked spell; Shift+click spends one point to upgrade its rank.

One character level grants one talent point. Existing player levels, passive ranks and currencies are preserved; new ability ranks start at 0 and can be levelled with unspent points.

## Controls

- Use the Astral Guide to open the main RPG menu.
- Use the Talent Crystal to open the class ability panel.
- In the Talent Tree, left-click a node to invest one talent point.
- In the combat ability panel, left-click to cast or Shift+left-click to upgrade the selected ability.
- The command `/astral skill <power|wisdom|vitality|focus|ability_id>` can also upgrade a passive stat or an ability.

## Resource pack

The JAR embeds the menu and item textures. The plugin asks each player to accept the resource pack before applying it. The default URL `http://127.0.0.1:8123/astral-pack.zip` is for a client running on the same computer as the Paper server. Remote players need a public hostname/IP in `plugins/AstralNexusRPG/config.yml` and TCP port 8123 forwarded to the server.

## Installation

1. Use Paper 1.21.4 with Java 21.
2. Stop the server and back up the normal world, player data, plugin folder and existing JAR.
3. Replace the old JAR with `AstralNexusRPG-1.24.0.jar`.
4. Start the server. The old dedicated dungeon world is deleted once and regenerated as players start new trials. Normal worlds and character progression remain intact.

## Build

Requires JDK 21 and Gradle 8.11.

```bash
gradle clean build
```

The build generates and embeds the resource pack.
