# Astral Nexus RPG 1.27.0 — Paper 1.21.4

Astral Nexus is an optional fantasy-RPG dungeon adventure for Paper 1.21.4 and Java 21. The dungeon pool contains **exactly four distinct cave maps**. A player's latest route is remembered, so the next attempt cannot select the same map twice in a row.

## Four fully decorated cave adventures

Each route is a long underground cave with a continuous floor, a real descent and a gradual climb through uneven natural shelves. Every route has its own ceiling shape, rock palette, side chambers, wall structures and final sanctum.

1. **Сумрачные галереи** — forgotten royal mine in deepslate and tuff, with gilded seams, heavy supports, chains and deep soul-lit galleries.
2. **Аметистовый разлом** — a huge geode cave in calcite and basalt, with crystal spires, amethyst growths and cool light embedded in the rock.
3. **Изумрудные недра** — a lush underground root cavern with moss shelves, hanging roots, spore blossoms, glowing fungi and rolling terraces.
4. **Костяная бездна** — a separate, darker fossil cave with a deeper winding descent, bone ribs, basalt ridges, hanging dripstone, sculk growths and soul lanterns.

The first three themes are retained and expanded; the fourth is a separate route rather than a recolour of an existing map. All four caves are generated as solid enclosed spaces.

## Continuous terrain and cave decoration

- The floor is generated at the actual local height for every corridor section.
- Rock is filled underneath ramps and landings, preventing hollow undersides and gaps.
- Two-block cave walls follow the route's height profile and are decorated with buttresses, inset lights, carved bands, ribs and hanging details.
- The roof is arched and follows the same local height curve; stalactites, crystals, chains and plants attach to the roof and nearby walls.
- Side grottos and the final boss chamber have solid floors, enclosed perimeters and themed shrines.
- The routes stay dry; the walkable center is kept clear of decorative stalagmites and wall pieces.
- The three wave checkpoints and boss spawn at the exact local floor height.

Each new attempt clears and regenerates only its reserved cave region. A route is not rebuilt beneath another player. If the only free route is the same one the player just completed, entry waits rather than repeating it immediately.

## Three waves and a multi-phase boss

- Before each encounter, only the dungeon player sees a full-screen **ВОЛНА 1**, **ВОЛНА 2** or **ВОЛНА 3** title. Mobs appear after the announcement.
- Wave 1 has four enemies; wave 2 has five stronger enemies; wave 3 has six advanced enemies, including witches and evokers.
- The final **БОСС** title is shown only to that same dungeon player.
- **Страж Разлома** has 280 health, netherite equipment, high armor, stronger attacks and knockback resistance.
- The boss changes behavior at 65% and 28% health. Later phases increase movement speed and damage; the final phase adds a more frequent sonic pulse with short wither and slow effects.
- Dungeon mobs do not drop ordinary random equipment; encounter rewards are granted through the RPG reward system.

## Skills and class-specific shop

Temporary effects applied by player skills are capped at **15 seconds (300 ticks)**, including rank scaling. Higher skill ranks can still improve strength or cooldown values, but never extend these effects past the cap.

The shop now has an individual 12-item catalogue for each archetype:

- **Маг** — crystals, blaze rods, ghast tears, teleport orbs, magic reagents and emergency protection.
- **Воин** — swords, axes, shield, iron armor pieces, food and combat supplies.
- **Следопыт** — bows, crossbows, arrows, spectral arrows, strings, feathers, spyglass and rockets.
- **Страж** — defensive armor, shields, healing supplies, turtle helmet, iron repair materials and rare protection.

The shop uses the selected class's list and displays the actual Minecraft item icon for each product. A class must be selected before the class catalogue becomes available.

## Two access items — no duplicates

Inside the dungeon the player receives two marked access items:

- **✦ Путеводитель Астрала** — opens the main RPG menu.
- **✧ Кристалл боевых навыков** — opens the class ability menu.

Before granting either item, the plugin checks the player's storage inventory and offhand for its persistent marker. Existing items are refreshed in place; extra copies are reduced to one.

## Isolated world and regeneration

The maps are located in the separate `AstralNexusDungeon` world. Normal worlds, player inventories, levels, coins, passive talents and ability ranks are not intentionally reset by map generation. A route is reserved while it is in use, and its region is cleared only before the next run.

## Installation

1. Use Paper 1.21.4 with Java 21.
2. Stop the server and back up the normal world, player data, plugin folder and existing JAR.
3. Replace the old JAR with `AstralNexusRPG-1.27.0.jar`.
4. Start the server. Each cave is regenerated when selected; normal worlds and character progression are kept.

## Resource pack

The JAR embeds the menu and item textures. The plugin asks each player to accept the resource pack before applying it. The default URL `http://127.0.0.1:8123/astral-pack.zip` is for a client running on the same computer as the Paper server. Remote players need a public hostname/IP in `plugins/AstralNexusRPG/config.yml` and TCP port 8123 forwarded to the server.

## Build

Requires JDK 21 and Gradle 8.11.

```bash
gradle clean build
```

The build generates and embeds the resource pack and runs cave, GUI, class-shop, skill-duration and encounter validators in GitHub Actions.
