# Astral Nexus RPG 1.23.0 — Paper 1.21.4

Astral Nexus is an optional fantasy-RPG dungeon adventure for Paper 1.21.4 (Java 21). Normal worlds are not modified by the dungeon generator. Character progression is stored in player data and remains intact when the isolated dungeon world is rebuilt.

## Three regenerated dungeons

On the first boot of 1.23.0 the plugin removes the old, dedicated `AstralNexusDungeon` world and creates a clean one. This is a one-time migration. It does not delete the normal world, player inventories, character levels, coins, passive talents, or ability ranks.

- **Зал сокровищ** — a large underground treasure hall with a glass dome, protected central viewing shaft, upper galleries, a lower vault, an opened switchback stair entrance, a dry side gallery and a separate combat arena.
- **Кристальная пещера** — a calcite cavern, large amethyst formations, a crystal lake with a clear shore, suspended bridges, dry entrance path and a flat combat platform.
- **Затонувшие катакомбы** — a prismarine cathedral with canals, a central altar, Gothic arches, luminous pillars and a railed causeway that crosses the water channels.

Map generation includes a final safety pass that creates solid arena floors, clears player/mob headroom, and protects the treasure shaft. The old map world is dedicated to this plugin: if another plugin has players inside it during startup, the reset is postponed rather than ejecting them. Restart when it is empty to complete the one-time reset.

## Talent tree

The talent menu is a large connected constellation with three levels of development:

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
2. Stop the server.
3. Back up the normal world, player data, plugin folder and existing JAR.
4. Replace the old plugin with `AstralNexusRPG-1.23.0.jar`.
5. Start the server. The old isolated dungeon world is removed once and regenerated; player progression remains.

## Build

Requires JDK 21 and Gradle 8.11.

```bash
gradle clean build
```

The build generates and embeds the resource pack.
