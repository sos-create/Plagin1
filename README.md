# Astral Nexus RPG 1.32.0 — Paper 1.21.4

Astral Nexus is an optional fantasy-RPG dungeon adventure for **Paper 1.21.4** and **Java 21**. It preserves ordinary survival outside the dungeon and includes six generated cave maps.

## Six cave maps

1. **Сумрачные галереи** — a cramped deepslate mine with broken braces, warm recessed lamps, cracked masonry and gravel shelves.
2. **Аметистовый разлом** — a narrow calcite-and-basalt fissure with amethyst accents and cool crystal light.
3. **Изумрудные недра** — a root-choked cave with moss, glow lichen, shroomlight and irregular stone ledges.
4. **Костяная бездна** — a larger fossil cave with bone ribs, basalt ridges and sculk growths.
5. **Пепельный лабиринт** — a blackstone-and-basalt cave with magma seams and ochre froglight accents.
6. **Кристальный провал** — a pale calcite and prismarine cavern with amethyst features and sea-lantern illumination.

## Cave safety

- Main passages, side grottos and boss chambers have continuous solid perimeter walls and a second outer stone layer at curved grotto edges to close diagonal corner gaps.
- Every generated map receives a hidden, unbreakable barrier safety floor below the playable area. It is invisible during normal play and catches accidental gaps.
- If a player nevertheless falls below the safe height in the dungeon, the plugin returns them to the nearest Z-section of the main cave route and clears fall damage.
- Dungeon regeneration also clears the previous safety floor before rebuilding, so old geometry cannot leave stray barriers behind.
- Breaking and placing blocks, buckets and block-damaging explosions remain blocked in the dungeon.

## Online resource-pack delivery — no IP or extra port

- The resource pack remains embedded in the plugin JAR and is also published as a versioned ZIP over HTTPS from this repository.
- The plugin automatically sends that HTTPS URL and the matching SHA-1 hash to players when they enter a dungeon. They only need to accept the Minecraft resource-pack prompt.
- No public IP, DNS record, reverse proxy, or extra TCP port is required for texture downloads.
- The Minecraft server's connection port (for example, **25635**) remains the game port; the plugin does not try to use it as a web server.
- The default `resource-pack.public-url` is empty. An administrator may optionally set a custom public HTTP(S) URL; otherwise the built-in GitHub HTTPS URL is used. Old configurations containing the previous `YOUR_SERVER_IP` placeholder automatically fall back to the built-in URL.

## Menu button compatibility

The Paper plugin registers the `astralnexus:open_menu` incoming plugin-message channel used by the optional Fabric 1.21.4 inventory-button client addon. The server still validates permissions and only opens the adventure menu while the player is inside the Astral Nexus dungeon. The server gameplay, dungeon items and command fallback remain server-side.

## Protected dungeon and balanced boss

- The six cave routes stay dry and have their own materials, lighting, grottos and final chambers.
- The boss has 160 health, 8 base attack damage, 6 armor, 0.27 movement speed and 0.25 knockback resistance. Phase attacks are capped at 10/12 damage; the pulse deals 2/3/4 damage every 12/10/8 seconds, and the final phase no longer applies Wither.

## RPG progression

- Four classes: mage, warrior, ranger and guardian, each with its own 12-item shop.
- Skills, mana, stamina, quests, coins and class-specific abilities remain available.
- Each monster killed in an active dungeon run grants exactly **20 astral coins**. Kill XP and kill-quest progress remain limited to the active dungeon; quest completion rewards are unchanged.
- Every map uses five escalating enemy waves followed by the multi-phase boss **Страж Разлома**.
- Two or three players activating the same portal within the joining window share one run, map, wave progress and objectives.
- A cave is reserved for the whole party while the run is active, and consecutive identical maps are avoided when another route is available.

Build target: Paper 1.21.4, Java 21.
