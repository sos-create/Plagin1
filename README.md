# Astral Nexus RPG 1.31.0 — Paper 1.21.4

Astral Nexus is an optional fantasy-RPG dungeon adventure for Paper 1.21.4 and Java 21. The dungeon pool now contains **six distinct caves**, each with a long route, themed rock formations and its own lighting identity.

## Six cave maps

1. **Сумрачные галереи** — a cramped deepslate mine with broken braces, warm recessed lamps, cracked masonry and rough gravel shelves.
2. **Аметистовый разлом** — a narrow calcite-and-basalt fissure with amethyst accents and cool crystal light.
3. **Изумрудные недра** — a root-choked cave with moss, glow lichen, shroomlight and irregular stone ledges.
4. **Костяная бездна** — a larger fossil cave with bone ribs, basalt ridges and sculk growths.
5. **Пепельный лабиринт** — a blackstone-and-basalt cave with magma seams and ochre froglight accents.
6. **Кристальный провал** — a pale calcite and prismarine cavern with amethyst features and sea-lantern illumination.

## Cave atmosphere and traversal

- The first three routes remain deliberately narrow and claustrophobic; the fourth through sixth have their own broader silhouettes and material palettes.
- Themed hanging chandeliers now combine chains, lanterns, sea lanterns, amethyst clusters, froglights and cave-specific accents. Short wall sconces and recessed lights fill the edges without turning the tunnels into bright halls.
- Wall edges have layered rock, trim, crystal or moss details. The central path remains passable.
- The floor profile includes subtle irregular rises and dips, gravel patches and broken ledges instead of a perfectly smooth corridor. The solid underfloor and terrain-aware spawn heights are retained.
- Side grottos and final chambers remain attached to their respective cave routes. All routes stay dry.

## Protected dungeon and balanced boss

- Players cannot break or place blocks in the dungeon world. Bucket use and block-damaging explosions are also blocked, preserving generated cave routes and encounter arenas.
- The boss is easier: 160 health, 8 base attack damage, 6 armor, 0.27 movement speed and 0.25 knockback resistance. Phase attacks are capped at 10/12 damage; the pulse deals 2/3/4 damage every 12/10/8 seconds, and the final phase no longer applies Wither.

## Online server and resource-pack delivery

- The plugin contains the complete resource pack and serves it over HTTP on `0.0.0.0:8123`; players do not need a client-side mod to receive the pack.
- For a public server, set `resource-pack.public-url` in `plugins/AstralNexusRPG/config.yml` to `http://YOUR_PUBLIC_IP_OR_DOMAIN:8123/astral-pack.zip` (or your HTTPS reverse-proxy URL). Open/forward TCP port `8123` to the Paper server. The plugin warns if the URL is still a localhost address or the example placeholder.
- `127.0.0.1` and `localhost` only work when the Minecraft client is on the same computer as the server; they are not valid public download addresses.
- This build remains a Paper 1.21.4 plugin and does not change the server software, authentication mode, dungeon flow, or co-op system.

## Monster rewards and kill tracking

- Each monster killed in an active dungeon run grants exactly **20 astral coins**, regardless of player level.
- Monster-kill progress is recorded only while the player is active inside `AstralNexusDungeon`; kills elsewhere do not grant RPG kill coins, XP or advance kill quests.
- Rewards for completing quests remain unchanged.

## Five waves and boss

Every map uses the same complete encounter sequence: **five escalating enemy waves**, followed by the multi-phase boss **Страж Разлома**. Each wave has its own spawn set and announcement. The boss appears after wave five, not after the third wave.

## Co-op portal parties

Two or three players who activate the **same portal within a short joining window** are placed in one shared run and one cave. They share wave progress, enemy objectives and dungeon rewards. The party is limited to three players; a departing or disconnected leader is replaced by another active party member.

## Random map selection

A cave is reserved for the whole party while the run is active, so it cannot be regenerated underneath another group. Consecutive identical maps are avoided when another route is available.

Build target: Paper 1.21.4, Java 21.