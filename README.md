# Astral Nexus RPG 1.25.0 — Paper 1.21.4

Astral Nexus is an optional fantasy-RPG dungeon adventure for Paper 1.21.4 and Java 21. Normal survival worlds are not modified. Character levels, coins, talents, ability ranks and player inventories are stored independently from the regenerated dungeon world.

## Two access items — no duplicates

Inside the dungeon the player receives two marked access items:

- **✦ Путеводитель Астрала** — opens the main RPG menu.
- **✧ Кристалл боевых навыков** — opens the class ability menu.

Before issuing either item, the plugin searches the player's storage inventory and offhand for its persistent marker. If the item already exists, it is refreshed in place and is not issued again. Extra copies left by older versions are reduced to one. Only a missing item is granted. The check runs once when the adventure starts; normal-world logins no longer delete tokens from someone who is already inside the dungeon.

On leaving the dungeon, these two dungeon-only items are removed as before, so ordinary survival remains clean.

## Three long, dry cave routes

The old compact rooms and flooded areas are replaced by three carefully themed underground journeys. Each route runs for roughly 160 blocks before opening into a large final cavern, with three combat checkpoints, side grottoes, natural stone arches, hanging formations and layered lighting.

- **Сумрачные галереи** — an abandoned royal mine in deep slate and tuff, with gilded blackstone seams, massive stone ribs, hanging chains, warm lantern pools and forgotten side shrines.
- **Аметистовый разлом** — a geode-like cavern of calcite and smooth basalt. Amethyst spires grow from the walls, purple crystal details catch the light, and soul lanterns and sea lanterns give the passage a cool magical glow.
- **Изумрудные недра** — a huge underground root forest with moss-covered rock, ancient roots, spore blossoms, luminous fungi and overgrown side chambers.

## Walls and scenery

The main corridors now have continuous, two-block-thick side walls, not just a loose edge of rough terrain. Their materials match each route: polished blackstone with gilded accents, basalt/purple crystal trim, or mossy stone with glowing fungi. Regular buttresses, inset light blocks, chain details and decorative caps break up the walls along the full path.

Side grottoes now have closed rocky perimeter walls and small light insets. The final boss cavern is enclosed by a large perimeter wall and monumental piers, while its entrance from the main tunnel stays open. The walkable floor and headroom remain clear.

There is **no water** in any of the generated cave routes.

## Clean rebuild for every new trial

Before a new attempt, the plugin clears the complete selected map footprint from floor to ceiling. It removes broken blocks, old decorations, containers, dropped items, projectiles and leftover mobs, then builds a fresh variant of that cave. This prevents changes made during a previous run from leaking into the next one.

A route is reserved while a player is using it. A second player is sent to another free route rather than having the first player's cave erased underneath them. If all three routes are busy, the portal reports that and does not start a conflicting rebuild.

On the first start of 1.24.0, the plugin removed the previous dedicated `AstralNexusDungeon` world once. The 1.25.0 update preserves the currently generated world and applies the new wall design the next time a route is rebuilt for a new trial. If players are still inside a route during a rebuild, the route is not overwritten. Normal worlds, player inventories, levels, coins, passive talents and ability ranks are not deleted.

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
3. Replace the old JAR with `AstralNexusRPG-1.25.0.jar`.
4. Start the server. The new walls and grotto boundaries appear when each route is generated for the next trial. No full dungeon-world deletion is required for 1.25.0.

## Build

Requires JDK 21 and Gradle 8.11.

```bash
gradle clean build
```

The build generates and embeds the resource pack.
