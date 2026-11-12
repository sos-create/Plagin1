package ru.soscreate.astralnexus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.UUID;

import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class AstralNexusPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private NamespacedKey activeKey, classKey, levelKey, xpKey, skillPointsKey;
    private NamespacedKey manaKey, staminaKey, powerKey, wisdomKey, vitalityKey, focusKey, questKey, questKillsKey;
    private NamespacedKey menuItemKey, quickSkillsItemKey, coinsKey, dungeonRewardKey;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Map<String, Long>> abilityCooldowns = new HashMap<>();
    private final Set<UUID> texturedPlayers = new HashSet<>();
    private HttpServer resourcePackServer;
    private File resourcePackFile;
    private String resourcePackSha1;
    private DungeonManager dungeonManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        prepareResourcePack();
        resetDungeonWorldOnce();
        startResourcePackServer();
        activeKey = key("active");
        classKey = key("class");
        levelKey = key("level");
        xpKey = key("xp");
        skillPointsKey = key("skill_points");
        manaKey = key("mana");
        staminaKey = key("stamina");
        powerKey = key("power");
        wisdomKey = key("wisdom");
        vitalityKey = key("vitality");
        focusKey = key("focus");
        questKey = key("quest");
        questKillsKey = key("quest_kills");
        menuItemKey = key("menu_token");
        quickSkillsItemKey = key("quick_skills_token");
        coinsKey = key("coins");
        dungeonRewardKey = key("dungeon_reward");
        dungeonManager = new DungeonManager();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(dungeonManager, this);
        if (getCommand("astral") != null) getCommand("astral").setExecutor(this);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (dungeonManager.isDungeon(online)) {
                online.getPersistentDataContainer().set(activeKey, PersistentDataType.BYTE, (byte) 1);
                ensureEconomy(online);
                Bukkit.getScheduler().runTask(this, () -> dungeonManager.startAdventure(online));
            } else {
                dungeonManager.removeSpecialItems(online);
                online.getPersistentDataContainer().set(activeKey, PersistentDataType.BYTE, (byte) 0);
            }
        }

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!active(player)) continue;
                int mana = Math.min(maxMana(player), get(player, manaKey, 0) + 2 + get(player, wisdomKey, 0));
                int stamina = Math.min(100, get(player, staminaKey, 100) + 3);
                set(player, manaKey, mana);
                set(player, staminaKey, stamina);
                player.sendActionBar(Component.text(ChatColor.LIGHT_PURPLE + "✦ " + mana + "/" + maxMana(player)
                        + ChatColor.GRAY + " маны   " + ChatColor.GREEN + "◆ " + stamina + "/100 выносливости"));
            }
        }, 20L, 40L);

        getLogger().info("Astral Nexus RPG enabled for Paper 1.21.4. RPG mode is opt-in.");
    }

    private void resetDungeonWorldOnce() {
        File marker = new File(getDataFolder(), "dungeon-world-reset-1.24.done");
        if (marker.isFile()) return;
        org.bukkit.World oldWorld = Bukkit.getWorld("AstralNexusDungeon");
        if (oldWorld != null) {
            if (!oldWorld.getPlayers().isEmpty()) {
                getLogger().warning("Dungeon reset postponed: players are still inside AstralNexusDungeon. Restart with the dungeon empty to remove old maps.");
                return;
            }
            if (!Bukkit.unloadWorld(oldWorld, true)) {
                getLogger().severe("Could not unload AstralNexusDungeon; old maps were not deleted.");
                return;
            }
        }
        java.nio.file.Path worldFolder = Bukkit.getWorldContainer().toPath().resolve("AstralNexusDungeon");
        try {
            if (java.nio.file.Files.exists(worldFolder)) {
                try (var paths = java.nio.file.Files.walk(worldFolder)) {
                    for (java.nio.file.Path entry : paths.sorted(java.util.Comparator.reverseOrder()).toList())
                        java.nio.file.Files.deleteIfExists(entry);
                }
            }
            if (!getDataFolder().exists() && !getDataFolder().mkdirs())
                throw new IOException("Cannot create plugin data folder for reset marker");
            java.nio.file.Files.writeString(marker.toPath(),
                    "Astral Nexus 1.24.0: legacy rooms removed; long dry cave routes are generated on demand.\n");
            getLogger().info("Removed the legacy AstralNexusDungeon world. Three new long cave routes will be generated on demand.");
        } catch (IOException ex) {
            getLogger().severe("Could not remove old AstralNexusDungeon world: " + ex.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (dungeonManager != null) dungeonManager.shutdown();
        cooldowns.clear();
        abilityCooldowns.clear();
        texturedPlayers.clear();
        if (resourcePackServer != null) {
            resourcePackServer.stop(0);
            resourcePackServer = null;
        }
    }

    private void sendResourcePack(Player player) {
        String url = getConfig().getString("resource-pack.public-url", "").trim();
        if (url.isEmpty()) url = "http://127.0.0.1:8123/astral-pack.zip";
        if (resourcePackSha1 == null || url.isEmpty()) {
            message(player, ChatColor.RED + "Ресурс-пак не подготовлен. Проверь resource-pack.zip внутри JAR.");
            return;
        }
        try {
            player.setResourcePack(url, resourcePackSha1, false,
                    Component.text("Astral Nexus: принять загрузку собственных текстур меню?"));
            message(player, ChatColor.LIGHT_PURPLE + "Отправил запрос на загрузку текстур. Подтверди его в окне Minecraft.");
        } catch (IllegalArgumentException ex) {
            getLogger().warning("Invalid resource-pack URL: " + ex.getMessage());
            message(player, ChatColor.RED + "Неверный адрес ресурспака: " + url);
        }
    }

    private void prepareResourcePack() {
        try (var input = getResource("resource-pack.zip")) {
            if (input == null) {
                getLogger().severe("Bundled resource-pack.zip is missing from the plugin JAR.");
                return;
            }
            byte[] zip = input.readAllBytes();
            if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
                throw new IOException("Cannot create plugin data folder");
            }
            if (zip.length < 4 || zip[0] != 'P' || zip[1] != 'K' || zip[2] != 3 || zip[3] != 4) {
                throw new IOException("Embedded resource pack is not a valid ZIP archive");
            }
            resourcePackFile = new File(getDataFolder(), "resource-pack.zip");
            Files.write(resourcePackFile.toPath(), zip);
            resourcePackSha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(zip));
            getLogger().info("Embedded Astral Nexus resource pack extracted (" + zip.length
                    + " bytes, SHA-1: " + resourcePackSha1 + ").");
        } catch (IOException | NoSuchAlgorithmException ex) {
            getLogger().severe("Could not prepare embedded resource pack: " + ex.getMessage());
            resourcePackFile = null;
            resourcePackSha1 = null;
        }
    }

    private void startResourcePackServer() {
        if (resourcePackFile == null || !resourcePackFile.isFile()) return;
        int port = getConfig().getInt("resource-pack.server-port", 8123);
        try {
            resourcePackServer = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
            resourcePackServer.createContext("/astral-pack.zip", exchange -> {
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1);
                    exchange.close();
                    return;
                }
                exchange.getResponseHeaders().set("Content-Type", "application/zip");
                exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=astral-pack.zip");
                exchange.getResponseHeaders().set("Cache-Control", "public, max-age=3600");
                exchange.sendResponseHeaders(200, resourcePackFile.length());
                try (var output = exchange.getResponseBody()) {
                    Files.copy(resourcePackFile.toPath(), output);
                }
            });
            resourcePackServer.start();
            getLogger().info("Resource pack HTTP endpoint listening on port " + port + ". Forward this port so players can download the pack.");
        } catch (IOException ex) {
            getLogger().severe("Could not start resource-pack HTTP server on port " + port + ": " + ex.getMessage());
            resourcePackServer = null;
        }
    }

    private NamespacedKey key(String name) { return new NamespacedKey(this, name); }
    private int get(Player p, NamespacedKey key, int fallback) {
        return p.getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, fallback);
    }
    private void set(Player p, NamespacedKey key, int value) {
        p.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, value);
    }
    private String getString(Player p, NamespacedKey key, String fallback) {
        return p.getPersistentDataContainer().getOrDefault(key, PersistentDataType.STRING, fallback);
    }
    private void setString(Player p, NamespacedKey key, String value) {
        p.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
    }
    private boolean active(Player p) {
        return p.getWorld().getName().equals(DungeonManager.WORLD_NAME)
                && p.getPersistentDataContainer().getOrDefault(activeKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
    }
    private void activate(Player p) {
        p.getPersistentDataContainer().set(activeKey, PersistentDataType.BYTE, (byte) 1);
        if (get(p, levelKey, 0) == 0) {
            set(p, levelKey, 1); set(p, xpKey, 0); set(p, skillPointsKey, 0);
            set(p, manaKey, 50); set(p, staminaKey, 100); set(p, powerKey, 0);
            set(p, wisdomKey, 0); set(p, vitalityKey, 0); set(p, focusKey, 0);
        }
    }
    private int maxMana(Player p) { return 50 + get(p, levelKey, 1) * 5 + get(p, wisdomKey, 0) * 10; }

    private NamespacedKey abilityRankKey(String id) { return key("ability_rank_" + id); }

    private int abilityRank(Player player, String id) {
        return Math.max(0, Math.min(5, get(player, abilityRankKey(id), 0)));
    }

    private String pathRequirement(String id) {
        return switch (id) {
            case "flow" -> "Мудрость III";
            case "seal" -> "Стойкость III";
            case "resonance" -> "Сила III";
            case "tempo" -> "Концентрация III";
            default -> "нет требований";
        };
    }

    private void upgradeAbility(Player player, AbilityDefinition ability) {
        if (!active(player) || getString(player, classKey, "").isEmpty()) {
            message(player, "Сначала начни RPG и выбери архетип.");
            return;
        }
        if (!pathUnlocked(player, ability.id())) {
            message(player, "Для открытия «" + ability.title() + "» требуется " + pathRequirement(ability.id()) + ".");
            return;
        }
        int rank = abilityRank(player, ability.id());
        if (rank >= 5) {
            message(player, "Умение «" + ability.title() + "» уже достигло максимального ранга V.");
            return;
        }
        int points = get(player, skillPointsKey, 0);
        if (points < 1) {
            message(player, "Недостаточно очков талантов. Получай опыт за приключения.");
            return;
        }
        set(player, skillPointsKey, points - 1);
        set(player, abilityRankKey(ability.id()), rank + 1);
        message(player, ChatColor.GREEN + "Умение «" + ability.title() + "» улучшено до ранга " + (rank + 1)
                + "/5. Осталось очков: " + (points - 1) + ".");
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.9f, 1.15f + rank * 0.08f);
    }

    private void message(CommandSender sender, String text) {
        sender.sendMessage(ChatColor.DARK_PURPLE + "[Astral] " + ChatColor.RESET + text);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            message(sender, "Эта команда доступна только игроку.");
            return true;
        }
        if (!p.hasPermission("astralnexus.use")) {
            message(p, ChatColor.RED + "У тебя нет прав на использование RPG.");
            return true;
        }
        if (args.length == 0) { if (!dungeonManager.isDungeon(p)) { message(p, ChatColor.YELLOW + "Меню Astral Nexus открывается только в подземелье."); return true; } openMenu(p, Page.HOME); return true; }
        if (args[0].equalsIgnoreCase("help")) { help(p); return true; }

        if (!args[0].equalsIgnoreCase("help") && !args[0].equalsIgnoreCase("pack") && !dungeonManager.isDungeon(p)) {
            message(p, ChatColor.YELLOW + "Сначала войди в подземелье через портал Astral Nexus."); return true;
        }
        switch (args[0].toLowerCase()) {
            case "menu" -> openMenu(p, Page.HOME);
            case "economy" -> openMenu(p, Page.ECONOMY);
            case "shop" -> openMenu(p, Page.SHOP);
            case "pack" -> sendResourcePack(p);
            case "start" -> {
                if (!dungeonManager.isDungeon(p)) { message(p, ChatColor.YELLOW + "Astral Nexus доступен только внутри подземелья. Построй портал из железных блоков и белых стеклянных панелей."); return true; }
                if (active(p)) { message(p, "Режим RPG уже включён. Обычное выживание продолжает работать как обычно."); return true; }
                activate(p);
                message(p, ChatColor.GREEN + "Ты добровольно начал путь Astral Nexus! Выбери архетип: /astral choose <mage|warrior|ranger|guardian>");
                message(p, "До активации RPG плагин не меняет твои характеристики и игровой процесс.");
            }
            case "profile" -> openMenu(p, Page.PROFILE);
            case "choose" -> chooseClass(p, args);
            case "skill" -> upgradeSkill(p, args);
            case "cast" -> cast(p);
            case "quest" -> quest(p, args);
            case "stop" -> {
                if (!active(p)) { message(p, "RPG уже выключена."); return true; }
                p.getPersistentDataContainer().set(activeKey, PersistentDataType.BYTE, (byte) 0);
                message(p, "RPG-панель и награды отключены. Обычное выживание не затронуто.");
            }
            default -> help(p);
        }
        return true;
    }

    private void chooseClass(Player p, String[] args) {
        if (!active(p)) { message(p, "Сначала напиши /astral start."); return; }
        if (args.length < 2) { message(p, "Выбор: mage, warrior, ranger, guardian."); return; }
        String type = args[1].toLowerCase();
        if (!type.matches("mage|warrior|ranger|guardian")) {
            message(p, ChatColor.RED + "Неизвестный архетип. Доступны: mage, warrior, ranger, guardian."); return;
        }
        if (!getString(p, classKey, "").isEmpty()) {
            message(p, "Архетип уже выбран. Смена класса будет добавлена через отдельный игровой предмет."); return;
        }
        setString(p, classKey, type);
        set(p, manaKey, maxMana(p));
        message(p, ChatColor.GREEN + "Твой архетип: " + prettyClass(type) + ". Способность: /astral cast");
    }

    private void upgradeSkill(Player p, String[] args) {
        if (!active(p) || getString(p, classKey, "").isEmpty()) {
            message(p, "Сначала начни RPG и выбери архетип."); return;
        }
        if (args.length < 2) {
            message(p, "Пассивные таланты: power, wisdom, vitality, focus. Активные умения улучшаются по ID из меню способностей.");
            return;
        }
        String skill = args[1].toLowerCase(java.util.Locale.ROOT);
        NamespacedKey target = switch (skill) {
            case "power" -> powerKey;
            case "wisdom" -> wisdomKey;
            case "vitality" -> vitalityKey;
            case "focus" -> focusKey;
            default -> null;
        };
        if (target == null) {
            AbilityDefinition ability = allAbilities(p).stream()
                    .filter(candidate -> candidate.id().equals(skill)).findFirst().orElse(null);
            if (ability == null) {
                message(p, ChatColor.RED + "Неизвестный талант или умение: " + skill + ".");
                return;
            }
            upgradeAbility(p, ability);
            return;
        }
        int points = get(p, skillPointsKey, 0);
        int rank = get(p, target, 0);
        if (points < 1) { message(p, "Нет очков талантов. Получай опыт за приключения."); return; }
        if (rank >= 10) { message(p, "Талант «" + skillName(getString(p, classKey, ""), skill) + "» уже достиг максимума X."); return; }
        set(p, skillPointsKey, points - 1);
        set(p, target, rank + 1);
        if (skill.equals("wisdom")) set(p, manaKey, maxMana(p));
        message(p, ChatColor.GREEN + "Талант «" + skillName(getString(p, classKey, ""), skill)
                + "» повышен до ранга " + (rank + 1) + "/10. Осталось очков: " + (points - 1) + ".");
        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.25f);
    }

    private void cast(Player p) {
        if (!active(p) || getString(p, classKey, "").isEmpty()) {
            message(p, "Сначала начни RPG и выбери архетип.");
            return;
        }
        List<AbilityDefinition> classAbilities = abilitiesFor(getString(p, classKey, ""));
        if (classAbilities.isEmpty()) {
            message(p, "Для выбранного архетипа пока нет боевых умений.");
            return;
        }
        // The command and both GUIs use the same spell rank, mana cost, cooldown and VFX.
        castAbility(p, classAbilities.get(0));
    }

    private int damageNearby(Player p, double radius, double damage) {
        int count = 0;
        for (Entity entity : p.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Monster monster && monster.isValid() && !monster.isDead()) {
                monster.damage(damage, p);
                count++;
            }
        }
        return count;
    }

    // Every temporary buff or debuff applied by a player skill is capped at 15 seconds (300 ticks).
    private int skillEffectTicks(int baseTicks, int rankBonusTicks, int rank) {
        return Math.max(1, Math.min(300, baseTicks + rankBonusTicks * rank));
    }

    private record QuestDefinition(String id, String title, Material icon, String description,
                                   String eventType, int target, int coins, int xp) {}
    private record ShopProduct(Material material, String title, String description, int price, int amount) {}
    private record AbilityDefinition(String id, String title, String description,
                                     Material icon, int manaCost, int cooldownSeconds) {}

    private List<QuestDefinition> questDefinitions() {
        return List.of(
                new QuestDefinition("hunt", "Охотник на монстров", Material.PAPER, "Победи 5 враждебных существ", "kill", 5, 120, 90),
                new QuestDefinition("slayer", "Истребитель", Material.IRON_SWORD, "Победи 20 враждебных существ", "kill", 20, 360, 240),
                new QuestDefinition("miner", "Сердце шахты", Material.DIAMOND_PICKAXE, "Добудь 12 рудных блоков", "mine", 12, 220, 150),
                new QuestDefinition("lumber", "Лесной сборщик", Material.IRON_AXE, "Сруби 16 древесных блоков", "wood", 16, 150, 110),
                new QuestDefinition("farmer", "Хозяин полей", Material.GOLDEN_HOE, "Собери 20 зрелых культур", "farm", 20, 190, 130),
                new QuestDefinition("fisher", "Зов глубин", Material.FISHING_ROD, "Поймай 5 предметов на удочку", "fish", 5, 260, 180)
        );
    }

    private NamespacedKey questProgressKey(String id) { return key("quest_" + id + "_progress"); }

    private int questProgress(Player player, QuestDefinition quest) {
        return get(player, questProgressKey(quest.id()), -1);
    }

    private void progressQuests(Player player, String eventType, int amount) {
        if (!active(player) || amount <= 0) return;
        for (QuestDefinition quest : questDefinitions()) {
            if (!quest.eventType().equals(eventType)) continue;
            int progress = questProgress(player, quest);
            if (progress < 0 || progress >= quest.target()) continue;
            int updated = Math.min(quest.target(), progress + amount);
            set(player, questProgressKey(quest.id()), updated);
            if (updated >= quest.target()) {
                message(player, ChatColor.GOLD + "Задание «" + quest.title() + "» выполнено! Забери награду в журнале.");
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.1f);
            }
        }
    }

    private int coins(Player player) { return get(player, coinsKey, 250); }
    private void ensureEconomy(Player player) {
        if (!player.getPersistentDataContainer().has(coinsKey, PersistentDataType.INTEGER)) set(player, coinsKey, 250);
    }

    private void addCoins(Player player, int amount) {
        if (amount <= 0) return;
        ensureEconomy(player);
        set(player, coinsKey, Math.min(2_000_000_000, coins(player) + amount));
        message(player, ChatColor.GOLD + "＋" + amount + " астральных монет. Баланс: " + coins(player));
    }

    private List<ShopProduct> shopProducts(String archetype) {
        return switch (archetype) {
            case "mage" -> List.of(
                    new ShopProduct(Material.AMETHYST_SHARD, "Осколки фокуса", "12 кристаллов для магического фокуса", 75, 12),
                    new ShopProduct(Material.BLAZE_ROD, "Жезлы пламени", "4 жезла для усиления стихий", 120, 4),
                    new ShopProduct(Material.GHAST_TEAR, "Слеза эфира", "Редкий реагент восстановления", 190, 2),
                    new ShopProduct(Material.ENDER_PEARL, "Сфера скачка", "4 жемчужины для смены позиции", 110, 4),
                    new ShopProduct(Material.ENDER_EYE, "Око поиска", "2 ока для поиска астральных путей", 165, 2),
                    new ShopProduct(Material.GLOWSTONE_DUST, "Световая пыль", "16 порций пыли для магических зарядов", 95, 16),
                    new ShopProduct(Material.LAPIS_LAZULI, "Лазурный катализатор", "32 лазурита для зачарования", 90, 32),
                    new ShopProduct(Material.FIRE_CHARGE, "Сгустки огня", "8 зарядов дальней стихийной атаки", 125, 8),
                    new ShopProduct(Material.EXPERIENCE_BOTTLE, "Флаконы познания", "12 бутылочек опыта", 170, 12),
                    new ShopProduct(Material.GOLDEN_CARROT, "Морковь маны", "8 золотых морковок для восстановления", 95, 8),
                    new ShopProduct(Material.GOLDEN_APPLE, "Яблоко защиты", "Золотое яблоко на случай прорыва", 180, 1),
                    new ShopProduct(Material.TOTEM_OF_UNDYING, "Тотем последнего импульса", "Страховка мага в бою с боссом", 1800, 1));
            case "warrior" -> List.of(
                    new ShopProduct(Material.IRON_SWORD, "Клинок авангарда", "Железный меч для ближнего боя", 260, 1),
                    new ShopProduct(Material.IRON_AXE, "Тяжёлая секира", "Мощное оружие для пробития защиты", 285, 1),
                    new ShopProduct(Material.SHIELD, "Щит бастиона", "Блокируй удары и переживай натиск", 230, 1),
                    new ShopProduct(Material.IRON_CHESTPLATE, "Кираса авангарда", "Надёжная броня передней линии", 360, 1),
                    new ShopProduct(Material.IRON_HELMET, "Шлем бойца", "Защита головы в ближнем бою", 190, 1),
                    new ShopProduct(Material.IRON_LEGGINGS, "Набедренники бойца", "Защита ног в сражении", 300, 1),
                    new ShopProduct(Material.IRON_BOOTS, "Сапоги натиска", "Броня для тяжёлого похода", 170, 1),
                    new ShopProduct(Material.COOKED_BEEF, "Рацион берсерка", "24 приготовленных стейка", 78, 24),
                    new ShopProduct(Material.GOLDEN_APPLE, "Боевой стимулятор", "Золотое яблоко для решающего боя", 180, 1),
                    new ShopProduct(Material.GOLDEN_CARROT, "Плотный паёк", "8 золотых морковок", 95, 8),
                    new ShopProduct(Material.DIAMOND, "Заточка клинка", "Редкий алмаз для улучшения снаряжения", 420, 1),
                    new ShopProduct(Material.TOTEM_OF_UNDYING, "Тотем несокрушимости", "Страховка в бою с боссом", 1800, 1));
            case "ranger" -> List.of(
                    new ShopProduct(Material.BOW, "Лук дальнего дозора", "Лук для точных дальних атак", 260, 1),
                    new ShopProduct(Material.CROSSBOW, "Арбалет следопыта", "Арбалет для подготовленного залпа", 310, 1),
                    new ShopProduct(Material.ARROW, "Колчан стрел", "32 обычные стрелы", 82, 32),
                    new ShopProduct(Material.SPECTRAL_ARROW, "Световые стрелы", "16 стрел для подсветки целей", 135, 16),
                    new ShopProduct(Material.TIPPED_ARROW, "Полевые наконечники", "8 особых наконечников", 165, 8),
                    new ShopProduct(Material.STRING, "Тетивы", "24 нити для обслуживания лука", 65, 24),
                    new ShopProduct(Material.FEATHER, "Оперение стрел", "16 перьев для боеприпасов", 55, 16),
                    new ShopProduct(Material.SPYGLASS, "Прицел разведчика", "Подзорная труба для поиска целей", 180, 1),
                    new ShopProduct(Material.FIREWORK_ROCKET, "Ракеты залпа", "12 ракет для арбалета и манёвра", 145, 12),
                    new ShopProduct(Material.COOKED_RABBIT, "Паёк разведчика", "16 порций лёгкой провизии", 64, 16),
                    new ShopProduct(Material.GOLDEN_CARROT, "Рацион дозора", "8 золотых морковок", 95, 8),
                    new ShopProduct(Material.TOTEM_OF_UNDYING, "Тотем последнего выстрела", "Страховка при отходе", 1800, 1));
            case "guardian" -> List.of(
                    new ShopProduct(Material.SHIELD, "Эгида стража", "Щит для защиты союзной линии", 230, 1),
                    new ShopProduct(Material.IRON_CHESTPLATE, "Панцирь хранителя", "Крепкая броня корпуса", 360, 1),
                    new ShopProduct(Material.IRON_HELMET, "Шлем хранителя", "Защита головы", 190, 1),
                    new ShopProduct(Material.IRON_LEGGINGS, "Набедренники бастиона", "Защита ног", 300, 1),
                    new ShopProduct(Material.IRON_BOOTS, "Сапоги бастиона", "Устойчивость в тяжёлом бою", 170, 1),
                    new ShopProduct(Material.TURTLE_HELMET, "Шлем глубин", "Дополнительная выживаемость под водой", 290, 1),
                    new ShopProduct(Material.HONEY_BOTTLE, "Медовый эликсир", "6 бутылочек для восстановления", 90, 6),
                    new ShopProduct(Material.GOLDEN_APPLE, "Яблоко исцеления", "Золотое яблоко для экстренного лечения", 180, 1),
                    new ShopProduct(Material.ENCHANTED_GOLDEN_APPLE, "Сердце бастиона", "Редкая мощная защита", 2400, 1),
                    new ShopProduct(Material.IRON_INGOT, "Ремкомплект брони", "16 железных слитков", 130, 16),
                    new ShopProduct(Material.COOKED_BEEF, "Паёк защитника", "24 приготовленных стейка", 78, 24),
                    new ShopProduct(Material.TOTEM_OF_UNDYING, "Тотем последнего рубежа", "Страховка хранителя", 1800, 1));
            default -> List.of();
        };
    }

    private ItemStack shopProductIcon(ShopProduct product, Player player) {
        ItemStack item = new ItemStack(product.material());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6" + product.title());
        meta.setLore(List.of("§7" + product.description(),
                "§7Количество: §f" + product.amount(),
                "§7Цена: §e" + product.price() + " монет",
                coins(player) >= product.price() ? "§aНажми, чтобы купить" : "§cНедостаточно монет"));
        item.setItemMeta(meta);
        return item;
    }

    private void buyProduct(Player player, ShopProduct product) {
        ensureEconomy(player);
        if (coins(player) < product.price()) {
            message(player, ChatColor.RED + "Не хватает монет. Нужно " + product.price() + ", у тебя " + coins(player) + ".");
            return;
        }
        set(player, coinsKey, coins(player) - product.price());
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(new ItemStack(product.material(), product.amount()));
        for (ItemStack item : overflow.values()) player.getWorld().dropItemNaturally(player.getLocation(), item);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.3f);
        message(player, ChatColor.GREEN + "Покупка: " + product.title() + ". Остаток: " + coins(player) + " монет.");
    }

    private List<AbilityDefinition> abilitiesFor(String archetype) {
        return switch (archetype) {
            case "mage" -> List.of(
                    new AbilityDefinition("nova", "Астральная нова", "Взрыв энергии вокруг мага", Material.NETHER_STAR, 18, 9),
                    new AbilityDefinition("ward", "Призма защиты", "Сопротивление и поглощение урона", Material.AMETHYST_SHARD, 14, 14),
                    new AbilityDefinition("blink", "Скачок эфира", "Короткое перемещение вперёд", Material.ENDER_PEARL, 12, 8),
                    new AbilityDefinition("surge", "Прилив маны", "Восстановить до 25 единиц маны", Material.HEART_OF_THE_SEA, 0, 25));
            case "warrior" -> List.of(
                    new AbilityDefinition("spin", "Вихрь клинка", "Удар по врагам вокруг", Material.IRON_SWORD, 16, 8),
                    new AbilityDefinition("charge", "Рывок берсерка", "Стремительный бросок вперёд", Material.SHIELD, 12, 10),
                    new AbilityDefinition("roar", "Боевой рёв", "Ослабить ближайших противников", Material.GOAT_HORN, 18, 18),
                    new AbilityDefinition("guard", "Железная стойка", "Временная защита и поглощение", Material.IRON_CHESTPLATE, 14, 16));
            case "ranger" -> List.of(
                    new AbilityDefinition("volley", "Звёздный залп", "Мощная критическая стрела", Material.BOW, 15, 7),
                    new AbilityDefinition("snare", "Ловушка следопыта", "Замедлить врагов вокруг", Material.COBWEB, 13, 13),
                    new AbilityDefinition("dash", "Теневой шаг", "Ускорение и рывок", Material.FEATHER, 12, 11),
                    new AbilityDefinition("mark", "Метка добычи", "Подсветить ближайших врагов", Material.SPYGLASS, 10, 16));
            case "guardian" -> List.of(
                    new AbilityDefinition("heal", "Астральное исцеление", "Восстановить здоровье и получить щит", Material.GOLDEN_APPLE, 18, 12),
                    new AbilityDefinition("aegis", "Эгида хранителя", "Сильное поглощение урона", Material.SHIELD, 16, 15),
                    new AbilityDefinition("quake", "Землетрясение", "Ударить ближайших врагов", Material.MACE, 22, 18),
                    new AbilityDefinition("rally", "Знамя стойкости", "Усилить себя на короткое время", Material.TOTEM_OF_UNDYING, 14, 20));
            default -> List.of();
        };
    }

    private void playAbilityVfx(Player player, String abilityId) {
        org.bukkit.World world = player.getWorld();
        org.bukkit.Color tint = switch (abilityId) {
            case "nova", "resonance" -> org.bukkit.Color.fromRGB(92, 190, 255);
            case "ward", "guard", "aegis", "seal" -> org.bukkit.Color.fromRGB(93, 225, 211);
            case "blink", "charge", "dash", "tempo" -> org.bukkit.Color.fromRGB(181, 126, 255);
            case "volley", "mark" -> org.bukkit.Color.fromRGB(255, 207, 104);
            case "snare", "roar", "quake", "spin" -> org.bukkit.Color.fromRGB(255, 125, 105);
            case "heal", "rally", "surge", "flow" -> org.bukkit.Color.fromRGB(137, 242, 174);
            default -> org.bukkit.Color.fromRGB(204, 155, 255);
        };
        Particle.DustOptions dust = new Particle.DustOptions(tint, 1.25f);
        final int[] frame = {0};
        Bukkit.getScheduler().runTaskTimer(this, task -> {
            if (!player.isOnline() || !player.isValid() || player.getWorld() != world || frame[0] >= 6) {
                task.cancel();
                return;
            }
            org.bukkit.Location center = player.getLocation().clone().add(0, 0.12, 0);
            org.bukkit.util.Vector forward = player.getLocation().getDirection().setY(0);
            if (forward.lengthSquared() < 0.001) forward.setZ(1);
            forward.normalize();
            org.bukkit.util.Vector side = new org.bukkit.util.Vector(-forward.getZ(), 0, forward.getX());
            double phase = frame[0] * 0.28;

            switch (abilityId) {
                case "nova" -> {
                    double radius = 0.55 + frame[0] * 0.43;
                    for (int point = 0; point < 36; point++) {
                        double angle = Math.PI * 2.0 * point / 36.0 + phase;
                        org.bukkit.Location at = center.clone().add(Math.cos(angle) * radius, 0.2 + frame[0] * 0.035,
                                Math.sin(angle) * radius);
                        world.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, dust);
                    }
                    if (frame[0] == 0 || frame[0] == 3)
                        world.spawnParticle(Particle.END_ROD, center.clone().add(0, 1.2, 0), 10, 0.35, 0.45, 0.35, 0.025);
                }
                case "resonance" -> {
                    for (int ring = 0; ring < 2; ring++) {
                        double radius = 0.7 + frame[0] * 0.38 + ring * 0.42;
                        for (int point = 0; point < 28; point++) {
                            double angle = Math.PI * 2.0 * point / 28.0 - phase * (ring == 0 ? 1 : 0.7);
                            world.spawnParticle(Particle.DUST,
                                    center.clone().add(Math.cos(angle) * radius, 0.25 + ring * 0.32, Math.sin(angle) * radius),
                                    1, 0, 0, 0, 0, dust);
                        }
                    }
                    if (frame[0] == 2) world.spawnParticle(Particle.END_ROD, center.clone().add(0, 1.4, 0),
                            24, 0.8, 0.65, 0.8, 0.035);
                }
                case "spin", "quake" -> {
                    double radius = 0.8 + frame[0] * 0.48;
                    for (int point = 0; point < 36; point++) {
                        double angle = Math.PI * 2.0 * point / 36.0 + phase;
                        org.bukkit.Location at = center.clone().add(Math.cos(angle) * radius, 0.08, Math.sin(angle) * radius);
                        world.spawnParticle(abilityId.equals("spin") ? Particle.SWEEP_ATTACK : Particle.CRIT,
                                at, 1, 0, 0, 0, 0);
                        if (point % 3 == 0) world.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, dust);
                    }
                }
                case "ward", "guard", "aegis", "seal" -> {
                    for (int point = 0; point < 24; point++) {
                        double angle = Math.PI * 2.0 * point / 24.0 + phase * (point % 2 == 0 ? 1 : -1);
                        double y = 0.25 + ((point + frame[0] * 3) % 18) * 0.105;
                        double radius = 0.72 + 0.08 * Math.sin(phase * 2 + point);
                        world.spawnParticle(Particle.DUST,
                                center.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius),
                                1, 0, 0, 0, 0, dust);
                    }
                    if (frame[0] == 0 || frame[0] == 4)
                        world.spawnParticle(Particle.WAX_ON, center.clone().add(0, 1.0, 0), 12, 0.55, 0.8, 0.55, 0.02);
                }
                case "blink", "charge", "dash" -> {
                    org.bukkit.Location trail = center.clone().subtract(forward.clone().multiply(0.45 + frame[0] * 0.3));
                    for (int point = -2; point <= 2; point++) {
                        org.bukkit.Location at = trail.clone().add(side.clone().multiply(point * 0.18))
                                .add(0, 0.25 + Math.abs(point) * 0.09, 0);
                        world.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, dust);
                    }
                    world.spawnParticle(Particle.PORTAL, trail, 8, 0.22, 0.25, 0.22, 0.08);
                }
                case "tempo" -> {
                    for (int point = 0; point < 20; point++) {
                        double angle = Math.PI * 2.0 * point / 20.0 + phase * 2.5;
                        double y = 0.2 + ((point + frame[0] * 4) % 16) * 0.12;
                        world.spawnParticle(Particle.DUST,
                                center.clone().add(Math.cos(angle) * 0.65, y, Math.sin(angle) * 0.65),
                                1, 0, 0, 0, 0, dust);
                    }
                    if (frame[0] % 2 == 0)
                        world.spawnParticle(Particle.ENCHANT, center.clone().add(0, 1.0, 0), 8, 0.45, 0.6, 0.45, 0.2);
                }
                case "volley" -> {
                    org.bukkit.Location eye = player.getEyeLocation();
                    org.bukkit.util.Vector direction = eye.getDirection().normalize();
                    for (int point = 1; point <= 12; point++) {
                        org.bukkit.Location at = eye.clone().add(direction.clone().multiply(point * 0.45));
                        world.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, dust);
                        if (point % 3 == 0) world.spawnParticle(Particle.CRIT, at, 2, 0.025, 0.025, 0.025, 0.01);
                    }
                }
                case "snare" -> {
                    double radius = 0.55 + frame[0] * 0.18;
                    for (int point = 0; point < 32; point++) {
                        double angle = Math.PI * 2.0 * point / 32.0;
                        org.bukkit.Location at = center.clone().add(Math.cos(angle) * radius, 0.06, Math.sin(angle) * radius);
                        world.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, dust);
                    }
                    if (frame[0] == 0) world.spawnParticle(Particle.CLOUD, center, 18, 0.65, 0.05, 0.65, 0.015);
                }
                case "mark" -> {
                    org.bukkit.Location eye = player.getEyeLocation().add(player.getEyeLocation().getDirection().normalize().multiply(2.2));
                    for (int arm = -2; arm <= 2; arm++) {
                        world.spawnParticle(Particle.ELECTRIC_SPARK, eye.clone().add(arm * 0.16, 0, 0), 1, 0, 0, 0, 0);
                        world.spawnParticle(Particle.ELECTRIC_SPARK, eye.clone().add(0, arm * 0.16, 0), 1, 0, 0, 0, 0);
                    }
                    if (frame[0] == 1)
                        for (Entity entity : player.getNearbyEntities(12, 8, 12))
                            if (entity instanceof Monster monster && !monster.isDead())
                                world.spawnParticle(Particle.END_ROD, monster.getLocation().add(0, 1.8, 0), 5, 0.15, 0.2, 0.15, 0.01);
                }
                case "roar" -> {
                    if (frame[0] == 0) world.spawnParticle(Particle.SONIC_BOOM, center.clone().add(0, 1, 0), 1);
                    double radius = 0.8 + frame[0] * 0.62;
                    for (int point = 0; point < 32; point++) {
                        double angle = Math.PI * 2.0 * point / 32.0;
                        world.spawnParticle(Particle.DUST,
                                center.clone().add(Math.cos(angle) * radius, 0.25, Math.sin(angle) * radius),
                                1, 0, 0, 0, 0, dust);
                    }
                }
                case "heal" -> {
                    for (int point = 0; point < 18; point++) {
                        double angle = Math.PI * 2.0 * point / 18.0 + phase;
                        double y = 0.18 + ((point + frame[0] * 3) % 16) * 0.12;
                        world.spawnParticle(Particle.DUST,
                                center.clone().add(Math.cos(angle) * 0.58, y, Math.sin(angle) * 0.58),
                                1, 0, 0, 0, 0, dust);
                    }
                    if (frame[0] == 0 || frame[0] == 3)
                        world.spawnParticle(Particle.HEART, center.clone().add(0, 1.6, 0), 4, 0.35, 0.2, 0.35, 0.01);
                }
                case "rally" -> {
                    for (int y = 0; y < 6; y++) {
                        world.spawnParticle(Particle.DUST, center.clone().add(0, 0.3 + y * 0.28, 0),
                                2, 0.08, 0.035, 0.08, 0, dust);
                    }
                    if (frame[0] == 0 || frame[0] == 4)
                        world.spawnParticle(Particle.TOTEM_OF_UNDYING, center.clone().add(0, 1.1, 0),
                                10, 0.35, 0.65, 0.35, 0.03);
                }
                case "surge", "flow" -> {
                    for (int point = 0; point < 22; point++) {
                        double angle = Math.PI * 2.0 * point / 22.0 + phase * 1.7;
                        double y = 0.18 + ((point + frame[0] * 2) % 18) * 0.105;
                        world.spawnParticle(Particle.DUST,
                                center.clone().add(Math.cos(angle) * 0.55, y, Math.sin(angle) * 0.55),
                                1, 0, 0, 0, 0, dust);
                    }
                    if (frame[0] % 2 == 0)
                        world.spawnParticle(Particle.ENCHANT, center.clone().add(0, 1.1, 0), 9, 0.4, 0.55, 0.4, 0.18);
                }
                default -> {
                    double radius = 0.65 + frame[0] * 0.3;
                    for (int point = 0; point < 24; point++) {
                        double angle = Math.PI * 2.0 * point / 24.0;
                        world.spawnParticle(Particle.DUST,
                                center.clone().add(Math.cos(angle) * radius, 0.2, Math.sin(angle) * radius),
                                1, 0, 0, 0, 0, dust);
                    }
                }
            }
            frame[0]++;
        }, 0L, 2L);

        Sound castSound = switch (abilityId) {
            case "nova", "resonance" -> Sound.ENTITY_PLAYER_LEVELUP;
            case "ward", "guard", "aegis", "seal", "rally" -> Sound.BLOCK_BEACON_ACTIVATE;
            case "blink", "charge", "dash", "tempo" -> Sound.ITEM_TRIDENT_RIPTIDE_1;
            case "volley", "mark" -> Sound.ENTITY_ARROW_SHOOT;
            case "roar", "quake" -> Sound.ENTITY_WARDEN_SONIC_BOOM;
            case "heal" -> Sound.BLOCK_AMETHYST_BLOCK_CHIME;
            case "surge", "flow" -> Sound.BLOCK_RESPAWN_ANCHOR_CHARGE;
            case "spin" -> Sound.ENTITY_PLAYER_ATTACK_SWEEP;
            case "snare" -> Sound.BLOCK_CHAIN_PLACE;
            default -> Sound.BLOCK_BEACON_POWER_SELECT;
        };
        float pitch = abilityId.equals("nova") || abilityId.equals("resonance") ? 1.55f
                : abilityId.equals("roar") || abilityId.equals("quake") ? 0.8f : 1.15f;
        player.playSound(player.getLocation(), castSound, 0.78f, pitch);
    }

    private void castAbility(Player player, AbilityDefinition ability) {
        if (!active(player) || getString(player, classKey, "").isEmpty()) {
            message(player, "Сначала начни RPG и выбери архетип.");
            return;
        }
        Map<String, Long> cds = abilityCooldowns.computeIfAbsent(player.getUniqueId(), id -> new HashMap<>());
        long now = System.currentTimeMillis();
        long readyAt = cds.getOrDefault(ability.id(), 0L);
        if (now < readyAt) {
            message(player, "Умение перезаряжается ещё " + ((readyAt - now + 999) / 1000) + " сек.");
            return;
        }
        int rank = abilityRank(player, ability.id());
        double rankScale = 1.0 + rank * 0.12;
        int effectiveManaCost = Math.max(0, ability.manaCost() - get(player, wisdomKey, 0) - rank / 2);
        if (ability.id().equals("surge") || ability.id().equals("flow")) {
            // These abilities restore mana instead of consuming it.
            if (ability.id().equals("surge"))
                set(player, manaKey, Math.min(maxMana(player), get(player, manaKey, 0) + 25 + rank * 5));
        } else {
            if (get(player, manaKey, 0) < effectiveManaCost) {
                message(player, "Недостаточно маны. Требуется " + effectiveManaCost + ".");
                return;
            }
            set(player, manaKey, get(player, manaKey, 0) - effectiveManaCost);
        }
        int power = get(player, powerKey, 0);
        switch (ability.id()) {
            case "nova", "spin", "quake" -> {
                int hits = damageNearby(player, (ability.id().equals("nova") ? 7.0 : 5.0) + rank * 0.18,
                        (5.0 + power * 1.4) * rankScale);
                player.getWorld().spawnParticle(ability.id().equals("spin") ? Particle.SWEEP_ATTACK : Particle.END_ROD,
                        player.getLocation().add(0, 1, 0), 35, 1.4, 0.8, 1.4, 0.04);
                message(player, ability.title() + "! Поражено целей: " + hits);
            }
            case "ward", "guard", "aegis" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, skillEffectTicks(160, 24, rank), 0));
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, skillEffectTicks(220, 24, rank), rank >= 4 ? 2 : 1));
                player.getWorld().spawnParticle(Particle.WAX_ON, player.getLocation().add(0, 1, 0), 24, 0.8, 0.8, 0.8, 0.02);
                message(player, ability.title() + ": защита активирована.");
            }
            case "blink", "charge", "dash" -> {
                if (ability.id().equals("blink")) {
                    org.bukkit.Location target = player.getLocation().clone()
                            .add(player.getLocation().getDirection().normalize().multiply(4.0 + rank * 0.45));
                    if (target.getBlock().isPassable() && target.clone().add(0, 1, 0).getBlock().isPassable()) {
                        player.teleport(target);
                    } else {
                        player.setVelocity(player.getLocation().getDirection().normalize().multiply(1.2).setY(0.25));
                    }
                } else {
                    player.setVelocity(player.getLocation().getDirection().normalize().multiply(1.5 + rank * 0.08).setY(0.35 + rank * 0.015));
                    if (ability.id().equals("dash")) player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, skillEffectTicks(100, 20, rank), 1));
                }
                player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation(), 28, 0.6, 0.8, 0.6, 0.2);
                message(player, ability.title() + "!");
            }
            case "surge" -> message(player, "Восстановлено до 25 единиц маны.");
            case "roar", "snare" -> {
                for (Entity entity : player.getNearbyEntities(7, 4, 7)) {
                    if (entity instanceof Monster monster && !monster.isDead()) {
                        monster.addPotionEffect(new PotionEffect(
                                ability.id().equals("roar") ? PotionEffectType.WEAKNESS : PotionEffectType.SLOWNESS,
                                skillEffectTicks(100, 24, rank), rank >= 3 ? 2 : 1));
                    }
                }
                player.getWorld().spawnParticle(Particle.SONIC_BOOM, player.getLocation().add(0, 1, 0), 1);
                message(player, ability.title() + "!");
            }
            case "volley" -> {
                Arrow arrow = player.launchProjectile(Arrow.class);
                arrow.setDamage((8.0 + power * 1.5) * rankScale);
                arrow.setCritical(true);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 1.25f);
                message(player, "Звёздный залп выпущен!");
            }
            case "mark" -> {
                int marked = 0;
                for (Entity entity : player.getNearbyEntities(12, 8, 12)) {
                    if (entity instanceof Monster monster && !monster.isDead()) {
                        monster.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, skillEffectTicks(160, 24, rank), 0));
                        marked++;
                    }
                }
                message(player, "Целей отмечено: " + marked);
            }
            case "heal" -> {
                double max = player.getAttribute(Attribute.MAX_HEALTH) == null ? 20.0
                        : player.getAttribute(Attribute.MAX_HEALTH).getValue();
                player.setHealth(Math.min(max, player.getHealth() + 8.0 + get(player, vitalityKey, 0) + rank * 1.5));
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, skillEffectTicks(160, 20, rank), rank >= 4 ? 1 : 0));
                message(player, "Астральное исцеление восстановило здоровье.");
            }
            case "rally" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, skillEffectTicks(180, 24, rank), rank >= 4 ? 1 : 0));
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, skillEffectTicks(100, 20, rank), 0));
                message(player, "Знамя стойкости поднято.");
            }
            case "flow" -> {
                int restored = Math.min(40 + rank * 5, maxMana(player) - get(player, manaKey, 0));
                set(player, manaKey, get(player, manaKey, 0) + restored);
                player.getWorld().spawnParticle(Particle.SPLASH, player.getLocation().add(0, 1, 0), 28, 0.7, 0.8, 0.7, 0.15);
                message(player, "Поток энергии восстановил " + restored + " маны.");
            }
            case "seal" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, skillEffectTicks(240, 30, rank), 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, skillEffectTicks(240, 30, rank), rank >= 4 ? 2 : 1));
                player.getWorld().spawnParticle(Particle.WAX_ON, player.getLocation().add(0, 1, 0), 42, 1.0, 1.0, 1.0, 0.03);
                message(player, "Защитная печать активирована.");
            }
            case "resonance" -> {
                int hits = damageNearby(player, 8.0 + rank * 0.2, (9.0 + power * 1.8) * rankScale);
                player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0), 50, 1.8, 1.0, 1.8, 0.08);
                message(player, "Астральный резонанс поразил целей: " + hits);
            }
            case "tempo" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, skillEffectTicks(240, 30, rank), rank >= 4 ? 2 : 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, skillEffectTicks(180, 24, rank), 1));
                player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation(), 44, 0.8, 1.0, 0.8, 0.2);
                message(player, "Темп времени ускорил твои движения.");
            }
            default -> { message(player, "Это умение пока недоступно."); return; }
        }
        playAbilityVfx(player, ability.id());
        int effectiveCooldown = Math.max(3, ability.cooldownSeconds() - get(player, focusKey, 0) / 2);
        effectiveCooldown = Math.max(3, effectiveCooldown - rank / 2);
        cds.put(ability.id(), now + effectiveCooldown * 1000L);
    }

    private void quest(Player p, String[] args) {
        if (!active(p)) { message(p, "Сначала напиши /astral start."); return; }
        if (args.length == 1 || args[1].equalsIgnoreCase("info")) {
            String current = getString(p, questKey, "");
            if (current.isEmpty()) message(p, "Активного задания нет. Доступно: /astral quest accept hunt");
            else message(p, "Задание «Охота на монстров»: " + get(p, questKillsKey, 0) + "/5. Убивай враждебных мобов.");
            return;
        }
        if (args[1].equalsIgnoreCase("accept") && args.length >= 3 && args[2].equalsIgnoreCase("hunt")) {
            if (!getString(p, questKey, "").isEmpty()) { message(p, "У тебя уже есть активное задание."); return; }
            setString(p, questKey, "hunt"); set(p, questKillsKey, 0);
            message(p, ChatColor.GREEN + "Задание принято: победи 5 враждебных существ. Награда: 120 астрального опыта.");
            return;
        }
        message(p, "Использование: /astral quest [info|accept hunt]");
    }

    private void addXp(Player p, int amount) {
        int xp = get(p, xpKey, 0) + amount;
        int level = get(p, levelKey, 1);
        int points = get(p, skillPointsKey, 0);
        while (xp >= 100 + level * 50) {
            xp -= 100 + level * 50;
            level++;
            points++;
            message(p, ChatColor.GOLD + "Новый уровень Astral Nexus: " + level + "! Получено очко навыка.");
        }
        set(p, xpKey, xp); set(p, levelKey, level); set(p, skillPointsKey, points);
        set(p, manaKey, Math.min(maxMana(p), get(p, manaKey, 0) + 5));
    }

    @EventHandler
    public void onMobKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || !active(killer) || !(event.getEntity() instanceof Monster)) return;
        addXp(killer, 10);
        addCoins(killer, 10 + Math.max(0, get(killer, levelKey, 1) - 1) * 2);
        progressQuests(killer, "kill", 1);
        if (getString(killer, questKey, "").equals("hunt")) {
            int kills = get(killer, questKillsKey, 0) + 1;
            set(killer, questKillsKey, kills);
            if (kills >= 5) {
                setString(killer, questKey, "");
                set(killer, questKillsKey, 0);
                addXp(killer, 120);
                message(killer, ChatColor.GOLD + "Задание выполнено! Получено 120 астрального опыта.");
            } else {
                message(killer, "Прогресс охоты: " + kills + "/5.");
            }
        }
    }    @EventHandler
    public void onRpgDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !active(player)) return;
        int rank = Math.min(10, get(player, vitalityKey, 0));
        if (rank <= 0) return;
        double reduction = Math.min(0.25, rank * 0.025);
        event.setDamage(event.getDamage() * (1.0 - reduction));
    }

    @EventHandler
    public void onOreBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        if (!active(p)) return;
        Material type = event.getBlock().getType();
        if (type.name().endsWith("_ORE") || type == Material.ANCIENT_DEBRIS) {
            addXp(p, 3);
            int reward = type == Material.DIAMOND_ORE || type == Material.DEEPSLATE_DIAMOND_ORE
                    || type == Material.ANCIENT_DEBRIS ? 14 : 5;
            addCoins(p, reward);
            progressQuests(p, "mine", 1);
        } else if (type.name().endsWith("_LOG") || type.name().endsWith("_WOOD")) {
            addCoins(p, 2);
            progressQuests(p, "wood", 1);
        } else if (type.name().contains("WHEAT") || type.name().contains("CARROTS")
                || type.name().contains("POTATOES") || type.name().contains("BEETROOTS")) {
            if (event.getBlock().getBlockData() instanceof org.bukkit.block.data.Ageable ageable
                    && ageable.getAge() >= ageable.getMaximumAge()) {
                addCoins(p, 2);
                progressQuests(p, "farm", 1);
            }
        }
    }

    @EventHandler
    public void onFish(org.bukkit.event.player.PlayerFishEvent event) {
        if (event.getState() == org.bukkit.event.player.PlayerFishEvent.State.CAUGHT_FISH
                && event.getCaught() != null && active(event.getPlayer())) {
            progressQuests(event.getPlayer(), "fish", 1);
            addCoins(event.getPlayer(), 8);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        p.getPersistentDataContainer().set(activeKey, PersistentDataType.BYTE, (byte) 0);

        // If 1.23.0 removed the old dungeon folder while this player was offline,
        // Minecraft can place them in the normal world. Restore their saved portal-side
        // location instead of leaving them stranded at the fallback spawn.
        if (!dungeonManager.isDungeon(p)) {
            dungeonManager.removeSpecialItems(p);
            org.bukkit.Location pendingReturn = dungeonManager.readReturnLocation(p);
            if (pendingReturn != null) {
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    if (!p.isOnline() || dungeonManager.isDungeon(p)) return;
                    org.bukkit.Location destination = dungeonManager.readReturnLocation(p);
                    if (destination == null || destination.getWorld() == null) return;
                    p.teleportAsync(destination).whenComplete((moved, error) ->
                            Bukkit.getScheduler().runTask(this, () -> {
                                if (!p.isOnline()) return;
                                if (error == null && Boolean.TRUE.equals(moved)) {
                                    dungeonManager.clearReturnLocation(p);
                                    message(p, ChatColor.GREEN + "Тебя вернуло к месту входа после обновления подземелья.");
                                } else {
                                    getLogger().warning("Could not restore saved dungeon return location for " + p.getName()
                                            + (error == null ? "" : ": " + error.getMessage()));
                                }
                            }));
                }, 2L);
            }
        }

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline() || !p.hasPermission("astralnexus.use")) return;
            if (dungeonManager.isDungeon(p)) dungeonManager.startAdventure(p);
        }, 40L);
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        texturedPlayers.remove(event.getPlayer().getUniqueId());
        if (dungeonManager != null && dungeonManager.isDungeon(event.getPlayer())) {
            UUID quittingId = event.getPlayer().getUniqueId();
            dungeonManager.removePartyMember(quittingId);
            dungeonManager.releaseMap(quittingId);
        }
        cooldowns.remove(event.getPlayer().getUniqueId());
        abilityCooldowns.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onAstralDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !active(player)) return;
        int vitality = get(player, vitalityKey, 0);
        if (vitality <= 0) return;
        double reduction = Math.min(0.25, vitality * 0.025);
        event.setDamage(event.getDamage() * (1.0 - reduction));
    }

    @EventHandler
    public void onResourcePackStatus(org.bukkit.event.player.PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        Object openHolder = player.getOpenInventory().getTopInventory().getHolder();
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> {
                texturedPlayers.add(player.getUniqueId());
                message(player, ChatColor.GREEN + "Текстуры Astral Nexus загружены и применены.");
                if (openHolder instanceof MenuHolder holder) {
                    openMenu(player, holder.page);
                } else if (openHolder instanceof QuickSkillsHolder) {
                    openQuickSkills(player);
                }
            }
            case DECLINED, FAILED_DOWNLOAD, FAILED_RELOAD, DISCARDED -> {
                texturedPlayers.remove(player.getUniqueId());
                message(player, ChatColor.YELLOW + "Кастомные текстуры не активны. Используются стандартные иконки.");
                if (event.getStatus() == org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD
                        || event.getStatus() == org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.FAILED_RELOAD) {
                    String packUrl = getConfig().getString("resource-pack.public-url", "").toLowerCase(java.util.Locale.ROOT);
                    if (packUrl.contains("127.0.0.1") || packUrl.contains("localhost")) {
                        message(player, ChatColor.RED + "Адрес 127.0.0.1 доступен только на компьютере сервера.");
                        message(player, ChatColor.YELLOW + "Для игроков с других устройств укажи публичный IP/домен в plugins/AstralNexusRPG/config.yml и открой TCP-порт 8123.");
                    } else {
                        message(player, ChatColor.YELLOW + "Проверь доступность resource-pack.public-url, TCP-порта 8123 и соответствие версии ресурспака Minecraft 1.21.4.");
                    }
                }
                if (openHolder instanceof MenuHolder holder) {
                    openMenu(player, holder.page);
                } else if (openHolder instanceof QuickSkillsHolder) {
                    openQuickSkills(player);
                }
            }
            default -> { }
        }
    }

    private String prettyClass(String type) {
        return switch (type) {
            case "mage" -> "Маг";
            case "warrior" -> "Воин";
            case "ranger" -> "Следопыт";
            case "guardian" -> "Страж";
            default -> type;
        };
    }


    private String skillName(String type, String skill) {
        return switch (skill) {
            case "power" -> "Сила";
            case "wisdom" -> "Мудрость";
            case "vitality" -> "Стойкость";
            default -> "Концентрация";
        };
    }

    private String skillDescription(String type, String skill) {
        return switch (skill) {
            case "power" -> "Увеличивает урон всех классовых атак и боевых умений";
            case "wisdom" -> "Увеличивает максимум и регенерацию маны, снижает стоимость умений";
            case "vitality" -> "Уменьшает входящий урон; усиливает лечение Стража";
            default -> "Сокращает перезарядку всех активных умений";
        };
    }

    private enum Page { HOME, PROFILE, CLASS, SKILLS, QUESTS, ABILITIES, ECONOMY, SHOP }
    private static final class MenuHolder implements InventoryHolder {
        final Page page; Inventory inventory;
        MenuHolder(Page page) { this.page = page; }
        @Override public Inventory getInventory() { return inventory; }
    }
    private String iconName(int model) {
        return switch (model) {
            case 1301 -> "home";
            case 1302 -> "profile";
            case 1303 -> "class";
            case 1304 -> "skills";
            case 1305 -> "quests";
            case 1306 -> "abilities";
            case 1307 -> "menu_token";
            case 1308 -> "quick_item";
            case 1309 -> "economy";
            case 1310 -> "shop";
            case 1311 -> "mage";
            case 1312 -> "warrior";
            case 1313 -> "ranger";
            case 1314 -> "guardian";
            case 1315 -> "locked";
            case 1316 -> "experience";
            case 1321 -> "quest_hunt";
            case 1322 -> "quest_slayer";
            case 1323 -> "quest_miner";
            case 1324 -> "quest_lumber";
            case 1325 -> "quest_farmer";
            case 1326 -> "quest_fisher";
            case 1331 -> "ability_arcane";
            case 1332 -> "ability_blade";
            case 1333 -> "ability_arrow";
            case 1334 -> "ability_aegis";
            case 1335 -> "path_flow";
            case 1336 -> "path_seal";
            case 1337 -> "path_resonance";
            case 1338 -> "path_tempo";
            case 1340 -> "close";
            case 1341 -> "mana";
            case 1401 -> "shop_food";
            case 1402 -> "shop_arrows";
            case 1403 -> "shop_pearl";
            case 1404 -> "shop_carrot";
            case 1405 -> "shop_apple";
            case 1406 -> "shop_xp";
            case 1407 -> "shop_blade";
            case 1408 -> "shop_shield";
            case 1409 -> "shop_diamond";
            case 1410 -> "shop_fish";
            case 1411 -> "shop_rocket";
            case 1412 -> "shop_totem";
            case 1420 -> "dungeon_key";
            case 1421 -> "void_blade";
            case 1422 -> "astral_flask";
            case 1423 -> "boss_relic";
            case 1430 -> "ability_nova";
            case 1431 -> "ability_prism";
            case 1432 -> "ability_blink";
            case 1433 -> "ability_mana";
            case 1434 -> "ability_spin";
            case 1435 -> "ability_charge";
            case 1436 -> "ability_roar";
            case 1437 -> "ability_guard";
            case 1438 -> "ability_volley";
            case 1439 -> "ability_snare";
            case 1440 -> "ability_dash";
            case 1441 -> "ability_mark";
            case 1442 -> "ability_heal";
            case 1443 -> "ability_aegis";
            case 1444 -> "ability_quake";
            case 1445 -> "ability_rally";
            default -> "nexus";
        };
    }

    private NamespacedKey iconItemModel(int model) {
        return new NamespacedKey("astral", "icons/" + iconName(model));
    }

    private ItemStack uiItem(Material type, String name, String... lore) {
        ItemStack item = new ItemStack(type);
        ItemMeta meta = item.getItemMeta();
        var serializer = LegacyComponentSerializer.legacySection();
        meta.displayName(serializer.deserialize(name));
        if (lore.length > 0) meta.lore(java.util.Arrays.stream(lore).map(serializer::deserialize).toList());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack customIcon(int model, String title, String... lore) {
        // A custom model is attached only to this GUI carrier stack; vanilla item models are untouched.
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(iconItemModel(model));
        var serializer = LegacyComponentSerializer.legacySection();
        meta.displayName(serializer.deserialize(title));
        if (lore.length > 0) meta.lore(java.util.Arrays.stream(lore).map(serializer::deserialize).toList());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack dungeonReward(int model, String title, String... lore) {
        ItemStack item = customIcon(model, title, lore);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(dungeonRewardKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private void fillNavigation(Inventory inv) {
        inv.setItem(0, customIcon(1301, "§6Главная", "§7Экран персонажа"));
        inv.setItem(1, customIcon(1309, "§6Экономика", "§7Кошелёк и награды"));
        inv.setItem(2, customIcon(1302, "§bПерсонаж", "§7Профиль и параметры"));
        inv.setItem(3, customIcon(1303, "§dАрхетипы", "§7Выбор класса"));
        inv.setItem(4, customIcon(1310, "§eМагазин", "§7Предметы и припасы"));
        inv.setItem(5, customIcon(1304, "§5Навыки", "§7Древо развития"));
        inv.setItem(6, customIcon(1305, "§eЖурнал", "§7Задания и награды"));
        inv.setItem(7, customIcon(1306, "§cСпособности", "§7Активные умения"));
        inv.setItem(8, customIcon(1340, "§cЗакрыть", "§7Вернуться в мир"));
    }

    private int abilityIconId(String id) {
        return switch (id) {
            case "nova" -> 1430;
            case "ward" -> 1431;
            case "blink" -> 1432;
            case "surge" -> 1433;
            case "spin" -> 1434;
            case "charge" -> 1435;
            case "roar" -> 1436;
            case "guard" -> 1437;
            case "volley" -> 1438;
            case "snare" -> 1439;
            case "dash" -> 1440;
            case "mark" -> 1441;
            case "heal" -> 1442;
            case "aegis" -> 1443;
            case "quake" -> 1444;
            case "rally" -> 1445;
            case "flow" -> 1335;
            case "seal" -> 1336;
            case "resonance" -> 1337;
            case "tempo" -> 1338;
            default -> 1306;
        };
    }

    private boolean pathUnlocked(Player player, String id) {
        return switch (id) {
            case "flow" -> get(player, wisdomKey, 0) >= 3;
            case "seal" -> get(player, vitalityKey, 0) >= 3;
            case "resonance" -> get(player, powerKey, 0) >= 3;
            case "tempo" -> get(player, focusKey, 0) >= 3;
            default -> true;
        };
    }

    private List<AbilityDefinition> pathAbilities() {
        return List.of(
                new AbilityDefinition("flow", "Поток энергии", "Открой при мудрости III: восстановление маны", Material.HEART_OF_THE_SEA, 0, 22),
                new AbilityDefinition("seal", "Защитная печать", "Открой при стойкости III: щит и сопротивление", Material.SHIELD, 15, 18),
                new AbilityDefinition("resonance", "Астральный резонанс", "Открой при силе III: волна урона", Material.AMETHYST_SHARD, 17, 16),
                new AbilityDefinition("tempo", "Темп времени", "Открой при концентрации III: ускорение", Material.CLOCK, 12, 20)
        );
    }

    private List<AbilityDefinition> allAbilities(Player player) {
        List<AbilityDefinition> all = new java.util.ArrayList<>(abilitiesFor(getString(player, classKey, "")));
        all.addAll(pathAbilities());
        return all;
    }

    private ItemStack specialItem(int iconModel, NamespacedKey marker, String title, String... lore) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(iconItemModel(iconModel));
        meta.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
        var serializer = LegacyComponentSerializer.legacySection();
        meta.displayName(serializer.deserialize(title));
        if (lore.length > 0) meta.lore(java.util.Arrays.stream(lore).map(serializer::deserialize).toList());
        item.setItemMeta(meta);
        return item;
    }

    private boolean upgradeOrCreateMarkedItem(Player player, NamespacedKey marker,
                                               int iconModel, String title, String... lore) {
        var inventory = player.getInventory();
        boolean found = false;

        // Search the whole usable inventory and offhand before issuing anything.
        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack existing = contents[slot];
            if (existing == null || existing.getType().isAir() || !isMarkedItem(existing, marker)) continue;
            if (!found) {
                ItemStack replacement = specialItem(iconModel, marker, title, lore);
                replacement.setAmount(1);
                inventory.setItem(slot, replacement);
                found = true;
            } else {
                // Older versions could leave duplicates; keep one and remove extras.
                inventory.setItem(slot, null);
            }
        }

        ItemStack offhand = inventory.getItemInOffHand();
        if (isMarkedItem(offhand, marker)) {
            if (!found) {
                ItemStack replacement = specialItem(iconModel, marker, title, lore);
                replacement.setAmount(1);
                inventory.setItemInOffHand(replacement);
                found = true;
            } else {
                inventory.setItemInOffHand(null);
            }
        }

        if (found) return true;
        giveMarkedItem(player, specialItem(iconModel, marker, title, lore));
        return false;
    }

    private void giveMarkedItem(Player player, ItemStack item) {
        var leftovers = player.getInventory().addItem(item);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private void ensureSpecialItems(Player player) {
        if (!player.hasPermission("astralnexus.use")) return;
        // Replace old Nether Star / Recovery Compass tokens with safe namespaced GUI carriers.
        upgradeOrCreateMarkedItem(player, menuItemKey, 1420,
                "§6✦ Путеводитель Астрала",
                "§7ПКМ — открыть главное RPG-меню",
                "§8Персонаж • архетипы • журнал • способности");
        upgradeOrCreateMarkedItem(player, quickSkillsItemKey, 1308,
                "§d✧ Кристалл боевых навыков",
                "§7ПКМ — открыть отдельное меню способностей",
                "§8Здесь отображаются только уже доступные умения");
    }

    private boolean isMarkedItem(ItemStack item, NamespacedKey marker) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(marker, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onSpecialItemUse(PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (!event.getPlayer().hasPermission("astralnexus.use")) return;
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        ItemStack held = event.getItem();
        if (isMarkedItem(held, menuItemKey)) {
            event.setCancelled(true);
            openMenu(player, Page.HOME);
        } else if (isMarkedItem(held, quickSkillsItemKey)) {
            event.setCancelled(true);
            openQuickSkills(player);
        }
    }

    private static final class QuickSkillsHolder implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    private List<AbilityDefinition> availableAbilities(Player player) {
        List<AbilityDefinition> available = new java.util.ArrayList<>(
                abilitiesFor(getString(player, classKey, "")));
        for (AbilityDefinition path : pathAbilities()) {
            if (pathUnlocked(player, path.id())) available.add(path);
        }
        return available;
    }

    private void renderAbilitySlots(Player player, Inventory inventory) {
        int[] slots = {10, 12, 14, 16, 28, 30, 32, 34};
        List<AbilityDefinition> available = availableAbilities(player);
        for (int i = 0; i < slots.length; i++) {
            if (i >= available.size()) {
                inventory.setItem(slots[i], new ItemStack(Material.BLACK_STAINED_GLASS_PANE));
                continue;
            }
            AbilityDefinition ability = available.get(i);
            long readyAt = abilityCooldowns.getOrDefault(player.getUniqueId(), Map.of())
                    .getOrDefault(ability.id(), 0L);
            int remaining = (int) Math.max(0, (readyAt - System.currentTimeMillis() + 999) / 1000);
            int effectiveCost = Math.max(0, ability.manaCost() - get(player, wisdomKey, 0));
            int effectiveCooldown = Math.max(3, ability.cooldownSeconds()
                    - get(player, focusKey, 0) / 2 - abilityRank(player, ability.id()) / 2);
            boolean enoughMana = get(player, manaKey, 0) >= effectiveCost
                    || ability.id().equals("surge") || ability.id().equals("flow");
            inventory.setItem(slots[i], customIcon(abilityIconId(ability.id()),
                    "§d✦ " + ability.title(),
                    "§7" + ability.description(),
                    "§7Стоимость: §b" + effectiveCost + " маны",
                    "§7Перезарядка: §f" + effectiveCooldown + " сек.",
                    "§7Ранг: §f" + abilityRank(player, ability.id()) + "/5",
                    remaining > 0 ? "§cОткат: " + remaining + " сек."
                            : enoughMana ? "§aГотово к применению" : "§cНедостаточно маны",
                    "§eЛКМ: применить",
                    "§dShift+ЛКМ: улучшить за 1 очко"));
        }
        inventory.setItem(22, customIcon(1306, "§dДОСТУПНЫЕ СПОСОБНОСТИ",
                "§7Архетип: §f" + (getString(player, classKey, "").isEmpty()
                        ? "не выбран" : prettyClass(getString(player, classKey, ""))),
                "§7Открыто умений: §f" + available.size(),
                "§8Запечатанные умения здесь не отображаются"));
        inventory.setItem(40, customIcon(1341, "§bАстральная мана",
                "§f" + get(player, manaKey, 0) + " §8/ §f" + maxMana(player),
                "§7Текущий запас энергии"));
    }

    private void openQuickSkills(Player player) {
        QuickSkillsHolder holder = new QuickSkillsHolder();
        Component title = texturedPlayers.contains(player.getUniqueId())
                ? Component.text("\uF808\uE005").font(net.kyori.adventure.key.Key.key("astral", "gui"))
                : Component.text("✦ БОЕВЫЕ СПОСОБНОСТИ ✦")
                    .color(net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE)
                    .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD);
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.inventory = inv;
        fillNavigation(inv);
        renderAbilitySlots(player, inv);
        decorateMenu(inv, Page.ABILITIES, player);

        player.openInventory(inv);
    }

    private String rankBar(int rank) {
        StringBuilder bar = new StringBuilder("§8[");
        for (int i = 0; i < 10; i++) bar.append(i < rank ? "§6▰" : "§7▱");
        return bar.append("§8]").toString();
    }

    private void decorateMenu(Inventory inventory, Page page, Player player) {
        for (int row = 1; row <= 4; row++) for (int slot : new int[]{row * 9, row * 9 + 8}) {
            if (inventory.getItem(slot) == null)
                inventory.setItem(slot, uiItem(row % 2 == 0 ? Material.PURPLE_STAINED_GLASS_PANE
                        : Material.BLACK_STAINED_GLASS_PANE, "§0 "));
        }
        for (int slot = 45; slot <= 53; slot++) {
            if (slot == 49 || inventory.getItem(slot) != null) continue;
            Material frame = slot % 2 == 0 ? Material.PURPLE_STAINED_GLASS_PANE
                    : Material.BLACK_STAINED_GLASS_PANE;
            inventory.setItem(slot, uiItem(frame, "§0 "));
        }
        if (inventory.getItem(49) == null)
            inventory.setItem(49, customIcon(1341, "§d✦ АСТРАЛЬНОЕ ЯДРО",
                    "§7" + prettyClass(getString(player, classKey, "не выбран")),
                    "§bМана: §f" + get(player, manaKey, 0) + "/" + maxMana(player),
                    "§6Монеты: §f" + coins(player), "§8Astral Nexus RPG • " + page.name()));
    }

    private void openMenu(Player p, Page page) {
        MenuHolder holder = new MenuHolder(page);
        String section = switch (page) {
            case HOME -> "ГЛАВНАЯ";
            case PROFILE -> "ПЕРСОНАЖ";
            case CLASS -> "АРХЕТИПЫ";
            case SKILLS -> "НАВЫКИ";
            case QUESTS -> "ЖУРНАЛ";
            case ABILITIES -> "СПОСОБНОСТИ";
            case ECONOMY -> "ЭКОНОМИКА";
            case SHOP -> "МАГАЗИН";
        };
        Component menuTitle;
        if (texturedPlayers.contains(p.getUniqueId())) {
            String art = switch (page) {
                case HOME -> "\uE000";
                case SKILLS -> "\uE001";
                case PROFILE -> "\uE002";
                case CLASS -> "\uE003";
                case QUESTS -> "\uE004";
                case ABILITIES -> "\uE005";
                case ECONOMY -> "\uE006";
                case SHOP -> "\uE007";
            };
            menuTitle = Component.text("\uF808" + art).font(net.kyori.adventure.key.Key.key("astral", "gui"));
        } else {
            menuTitle = Component.text("✦ ASTRAL NEXUS • " + section + " ✦")
                    .color(net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE)
                    .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD);
        }
        Inventory inv = Bukkit.createInventory(holder, 54, menuTitle);
        holder.inventory = inv;
        fillNavigation(inv);

        switch (page) {
            case HOME -> {
                // Only real equipped gear is shown. Empty equipment slots remain genuinely empty.
                ItemStack[] armor = p.getInventory().getArmorContents();
                int[] armorSlots = {37, 28, 19, 10};
                for (int i = 0; i < armorSlots.length; i++) {
                    ItemStack equipped = armor[i];
                    if (equipped != null && !equipped.getType().isAir()) inv.setItem(armorSlots[i], equipped.clone());
                }
                ItemStack offhand = p.getInventory().getItemInOffHand();
                if (offhand != null && !offhand.getType().isAir()) inv.setItem(16, offhand.clone());
                ItemStack mainhand = p.getInventory().getItemInMainHand();
                if (mainhand != null && !mainhand.getType().isAir()) inv.setItem(25, mainhand.clone());

                inv.setItem(13, customIcon(1302, "§d" + p.getName(),
                        "§7Уровень: §f" + get(p, levelKey, 1),
                        "§7Архетип: §f" + (getString(p, classKey, "").isEmpty()
                                ? "Не выбран" : prettyClass(getString(p, classKey, ""))),
                        "§7Кошелёк: §6" + coins(p) + " монет",
                        "§7Очки талантов: §e" + get(p, skillPointsKey, 0)));
                int level = get(p, levelKey, 1);
                int xp = get(p, xpKey, 0);
                int requiredXp = 100 + level * 50;
                int progress = Math.max(0, Math.min(20, xp * 20 / requiredXp));
                StringBuilder xpBar = new StringBuilder("§8[");
                for (int i = 0; i < 20; i++) xpBar.append(i < progress ? "§d▰" : "§7▱");
                xpBar.append("§8]");
                inv.setItem(49, customIcon(1316,
                        active(p) ? "§6Уровень " + level : "§6✦ НАЧАТЬ ПУТЬ ✦",
                        active(p) ? "§7Опыт: §f" + xp + "§8/§f" + requiredXp : "§7Нажми, чтобы активировать RPG",
                        active(p) ? xpBar.toString() : "§8Обычное выживание не изменится"));
            }
            case PROFILE -> {
                inv.setItem(10, customIcon(1302, "§bПрофиль героя",
                        "§7Игрок: §f" + p.getName(),
                        "§7Архетип: §f" + (getString(p, classKey, "").isEmpty()
                                ? "Не выбран" : prettyClass(getString(p, classKey, "")))));
                inv.setItem(12, customIcon(1316, "§6Уровень " + get(p, levelKey, 1),
                        "§7Опыт: §f" + get(p, xpKey, 0),
                        "§7До следующего уровня: §e" + Math.max(0, 100 + get(p, levelKey, 1) * 50 - get(p, xpKey, 0))));
                inv.setItem(14, customIcon(1335, "§bАстральная энергия",
                        "§7Мана: §f" + get(p, manaKey, 0) + "/" + maxMana(p),
                        "§7Выносливость: §f" + get(p, staminaKey, 100) + "/100"));
                inv.setItem(16, customIcon(1332, "§cБоевые параметры",
                        "§7Сила: §f" + get(p, powerKey, 0),
                        "§7Мудрость: §f" + get(p, wisdomKey, 0),
                        "§7Стойкость: §f" + get(p, vitalityKey, 0),
                        "§7Концентрация: §f" + get(p, focusKey, 0)));
                inv.setItem(28, customIcon(1309, "§6Кошелёк",
                        "§e" + coins(p) + " §7астральных монет",
                        "§8Единственная валюта Astral Nexus"));
                inv.setItem(30, customIcon(1304, "§5Таланты",
                        "§7Очки для прокачки: §e" + get(p, skillPointsKey, 0)));
                inv.setItem(32, customIcon(1337, "§dПути открыты",
                        "§7Открыто ветвей: §e" + (int) pathAbilities().stream().filter(x -> pathUnlocked(p, x.id())).count(),
                        "§8Открывай ветви до ранга III"));
                inv.setItem(34, customIcon(1334, "§bЗащита",
                        "§7Снижение получаемого урона: §f" + (get(p, vitalityKey, 0) * 2.5) + "%",
                        "§7Бонус за стойкость"));
            }
            case CLASS -> {
                String chosen = getString(p, classKey, "");
                inv.setItem(10, customIcon(1311, "§bМаг",
                        "§7Астральные импульсы и контроль",
                        chosen.equals("mage") ? "§aВыбран" : "§eНажми, чтобы выбрать"));
                inv.setItem(12, customIcon(1312, "§cВоин",
                        "§7Ближний бой и разрушительные удары",
                        chosen.equals("warrior") ? "§aВыбран" : "§eНажми, чтобы выбрать"));
                inv.setItem(14, customIcon(1313, "§aСледопыт",
                        "§7Точность, ловушки и мобильность",
                        chosen.equals("ranger") ? "§aВыбран" : "§eНажми, чтобы выбрать"));
                inv.setItem(16, customIcon(1314, "§bСтраж",
                        "§7Защита, исцеление и стойкость",
                        chosen.equals("guardian") ? "§aВыбран" : "§eНажми, чтобы выбрать"));
            }
            case SKILLS -> {
                String type = getString(p, classKey, "");
                int points = get(p, skillPointsKey, 0);
                inv.setItem(22, customIcon(1304, "§6✦ ДРЕВО ТАЛАНТОВ ✦",
                        "§7Архетип: §f" + (type.isEmpty() ? "не выбран" : prettyClass(type)),
                        "§7Свободные очки: §e" + points,
                        "§8ЛКМ по узлу — вложить одно очко",
                        "§8Пассивные таланты и активные заклинания прокачиваются отдельно"));

                String[] passives = {"power","wisdom","vitality","focus"};
                int[] passiveSlots = {10,16,28,34};
                int[] passiveModels = {1332,1335,1334,1338};
                for (int i = 0; i < passives.length; i++) {
                    String id = passives[i];
                    NamespacedKey stat = switch (id) {
                        case "power" -> powerKey;
                        case "wisdom" -> wisdomKey;
                        case "vitality" -> vitalityKey;
                        default -> focusKey;
                    };
                    int rank = get(p, stat, 0);
                    inv.setItem(passiveSlots[i], customIcon(passiveModels[i],
                            (rank >= 10 ? "§6✦ " : "§b") + skillName(type, id),
                            "§7" + skillDescription(type, id),
                            "§7Ранг: §f" + rank + "/10", rankBar(rank),
                            points > 0 && rank < 10 ? "§eЛКМ: улучшить за 1 очко" : "§8Нет очков или достигнут максимум"));
                }

                String[] pathIds = {"flow","seal","resonance","tempo"};
                int[] pathSlots = {12,14,30,32};
                for (int i = 0; i < pathIds.length; i++) {
                    String id = pathIds[i];
                    AbilityDefinition ability = pathAbilities().stream()
                            .filter(x -> x.id().equals(id)).findFirst().orElseThrow();
                    int rank = abilityRank(p, id);
                    boolean unlocked = pathUnlocked(p, id);
                    inv.setItem(pathSlots[i], customIcon(unlocked ? abilityIconId(id) : 1315,
                            (unlocked ? "§d✦ " : "§8") + ability.title(),
                            "§7" + ability.description(),
                            unlocked ? "§7Ранг умения: §f" + rank + "/5" : "§7Требуется: §e" + pathRequirement(id),
                            unlocked ? rankBar(rank * 2) : "§8Повышай связанный пассивный талант",
                            unlocked && points > 0 && rank < 5 ? "§eЛКМ: улучшить за 1 очко" :
                                    unlocked ? "§8Максимальный ранг или нет очков" : "§8Ветка пока закрыта"));
                }

                List<AbilityDefinition> classAbilities = abilitiesFor(type);
                int[] classSlots = {37,39,41,43};
                for (int i = 0; i < classSlots.length && i < classAbilities.size(); i++) {
                    AbilityDefinition ability = classAbilities.get(i);
                    int rank = abilityRank(p, ability.id());
                    inv.setItem(classSlots[i], customIcon(abilityIconId(ability.id()),
                            "§d" + ability.title(),
                            "§7" + ability.description(),
                            "§7Ранг умения: §f" + rank + "/5", rankBar(rank * 2),
                            "§7Мана: §b" + ability.manaCost() + " §8• §7Откат: §f" + ability.cooldownSeconds() + " сек.",
                            points > 0 && rank < 5 ? "§eЛКМ: улучшить за 1 очко" : "§8Нет очков или достигнут максимум"));
                }
                inv.setItem(40, customIcon(1341, "§6ОЧКИ ТАЛАНТОВ",
                        "§e" + points + " §7свободных очков",
                        "§7Каждый новый уровень даёт одно очко",
                        "§8Пассивы: ранг X • Умения: ранг V"));
            }
            case QUESTS -> {
                int[] questSlots = {10, 12, 14, 16, 28, 30};
                int[] questModels = {1321, 1322, 1323, 1324, 1325, 1326};
                List<QuestDefinition> quests = questDefinitions();
                for (int i = 0; i < quests.size(); i++) {
                    QuestDefinition quest = quests.get(i);
                    int progress = questProgress(p, quest);
                    String state = progress < 0 ? "§eНажми, чтобы принять"
                            : progress >= quest.target() ? "§aЗавершено — нажми забрать награду" : "§bВыполняется";
                    inv.setItem(questSlots[i], customIcon(questModels[i],
                            (progress >= quest.target() ? "§a✦ " : "§6") + quest.title(),
                            "§7" + quest.description(),
                            "§7Прогресс: §f" + Math.max(0, progress) + "/" + quest.target(),
                            "§7Награда: §6" + quest.coins() + " монет §8+ §d" + quest.xp() + " XP",
                            state));
                }
            }
            case ABILITIES -> renderAbilitySlots(p, inv);
            case ECONOMY -> inv.setItem(22, customIcon(1309, "§6Астральный кошелёк",
                    "§e" + coins(p) + " §7монет",
                    "§8Единый баланс: покупки и награды",
                    "§7Убийства, добыча, урожай и задания приносят монеты"));
            case SHOP -> {
                int[] productSlots = {10, 12, 14, 16, 19, 21, 23, 25, 28, 30, 32, 34};
                String archetype = getString(p, classKey, "");
                List<ShopProduct> products = shopProducts(archetype);
                if (products.isEmpty()) {
                    inv.setItem(22, customIcon(1401, "§dСначала выбери архетип",
                            "§7У каждого класса свой ассортимент.",
                            "§8Открой вкладку «Класс» и выбери героя."));
                } else {
                    for (int i = 0; i < products.size(); i++)
                        inv.setItem(productSlots[i], shopProductIcon(products.get(i), p));
                }
            }
        }
        decorateMenu(inv, page, p);
        p.openInventory(inv);
    }

    private boolean handleNavigation(Player player, int slot) {
        switch (slot) {
            case 0 -> openMenu(player, Page.HOME);
            case 1 -> openMenu(player, Page.ECONOMY);
            case 2 -> openMenu(player, Page.PROFILE);
            case 3 -> openMenu(player, Page.CLASS);
            case 4 -> openMenu(player, Page.SHOP);            case 5 -> openMenu(player, Page.SKILLS);
            case 6 -> openMenu(player, Page.QUESTS);            case 7 -> openMenu(player, Page.ABILITIES);
            case 8 -> player.closeInventory();
            default -> { return false; }
        }
        return true;
    }

    private void clickAbilitySlot(Player player, int slot, boolean upgrade) {
        int[] slots = {10, 12, 14, 16, 28, 30, 32, 34};
        for (int i = 0; i < slots.length; i++) {
            if (slot != slots[i]) continue;
            List<AbilityDefinition> available = availableAbilities(player);
            if (i >= available.size()) return;
            if (upgrade) {
                upgradeAbility(player, available.get(i));
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof QuickSkillsHolder)
                    openQuickSkills(player);
                else
                    openMenu(player, Page.ABILITIES);
            } else {
                player.closeInventory();
                castAbility(player, available.get(i));
            }
            return;
        }
    }

    @EventHandler public void onUiClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Object holder = e.getView().getTopInventory().getHolder();
        if (!(holder instanceof MenuHolder) && !(holder instanceof QuickSkillsHolder)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        int slot = e.getRawSlot();

        if (slot >= 0 && slot < 9) {
            handleNavigation(p, slot);
            return;
        }

        if (holder instanceof QuickSkillsHolder) {
            clickAbilitySlot(p, slot, e.isShiftClick());
            return;
        }

        MenuHolder menu = (MenuHolder) holder;
        switch (menu.page) {
            case HOME -> {
                if (slot == 49 && !active(p)) {
                    activate(p);
                    message(p, ChatColor.GREEN + "Путь Astral Nexus начат! Теперь выбери архетип.");
                    openMenu(p, Page.CLASS);
                }
            }
            case CLASS -> {
                if (!getString(p, classKey, "").isEmpty()) {
                    message(p, "Архетип уже выбран. Он сохраняется за персонажем.");
                    return;
                }
                String selected = switch (slot) {
                    case 10 -> "mage";
                    case 12 -> "warrior";
                    case 14 -> "ranger";
                    case 16 -> "guardian";
                    default -> "";
                };
                if (!selected.isEmpty()) {
                    if (!active(p)) activate(p);
                    setString(p, classKey, selected);
                    set(p, manaKey, maxMana(p));
                    p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.4f);
                    message(p, ChatColor.GREEN + "Выбран архетип: " + prettyClass(selected));
                    openMenu(p, Page.PROFILE);
                }
            }
            case SKILLS -> {
                if (getString(p, classKey, "").isEmpty()) {
                    message(p, "Сначала выбери архетип.");
                    openMenu(p, Page.CLASS);
                    return;
                }
                String passive = switch (slot) {
                    case 10 -> "power";
                    case 16 -> "wisdom";
                    case 28 -> "vitality";
                    case 34 -> "focus";
                    default -> "";
                };
                if (!passive.isEmpty()) {
                    upgradeSkill(p, new String[]{"skill", passive});
                    openMenu(p, Page.SKILLS);
                    return;
                }
                String pathId = switch (slot) {
                    case 12 -> "flow";
                    case 14 -> "seal";
                    case 30 -> "resonance";
                    case 32 -> "tempo";
                    default -> "";
                };
                if (!pathId.isEmpty()) {
                    AbilityDefinition ability = pathAbilities().stream()
                            .filter(x -> x.id().equals(pathId)).findFirst().orElse(null);
                    if (ability != null) upgradeAbility(p, ability);
                    openMenu(p, Page.SKILLS);
                    return;
                }
                int[] classSlots = {37,39,41,43};
                List<AbilityDefinition> classAbilities = abilitiesFor(getString(p, classKey, ""));
                for (int i = 0; i < classSlots.length && i < classAbilities.size(); i++) {
                    if (slot != classSlots[i]) continue;
                    upgradeAbility(p, classAbilities.get(i));
                    openMenu(p, Page.SKILLS);
                    return;
                }
            }
            case QUESTS -> {
                int[] questSlots = {10, 12, 14, 16, 28, 30};
                List<QuestDefinition> quests = questDefinitions();
                for (int i = 0; i < quests.size(); i++) {
                    if (slot != questSlots[i]) continue;
                    QuestDefinition quest = quests.get(i);
                    int progress = questProgress(p, quest);
                    if (progress < 0) {
                        set(p, questProgressKey(quest.id()), 0);
                        message(p, ChatColor.GREEN + "Поручение принято: " + quest.title());
                    } else if (progress >= quest.target()) {
                        set(p, questProgressKey(quest.id()), -1);
                        addCoins(p, quest.coins());
                        addXp(p, quest.xp());
                        message(p, ChatColor.GOLD + "Награда за «" + quest.title() + "» получена!");
                    } else {
                        message(p, "Прогресс «" + quest.title() + "»: " + progress + "/" + quest.target());
                    }
                    openMenu(p, Page.QUESTS);
                    return;
                }
            }
            case ABILITIES -> clickAbilitySlot(p, slot, e.isShiftClick());
            case ECONOMY -> { /* wallet is display-only; earnings and spending update it automatically */ }
            case SHOP -> {
                int[] productSlots = {10, 12, 14, 16, 19, 21, 23, 25, 28, 30, 32, 34};
                List<ShopProduct> products = shopProducts(getString(p, classKey, ""));
                for (int i = 0; i < products.size(); i++) {
                    if (slot == productSlots[i]) {
                        buyProduct(p, products.get(i));
                        openMenu(p, Page.SHOP);
                        return;
                    }
                }
            }
        }
    }

    @EventHandler
    public void onUiDrag(InventoryDragEvent event) {
        Object holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof MenuHolder || holder instanceof QuickSkillsHolder) {
            event.setCancelled(true);
        }
    }

    private void help(Player p) {
        message(p, ChatColor.LIGHT_PURPLE + "Astral Nexus RPG — /astral menu открывает меню");
        message(p, "/astral pack — повторно предложить загрузку текстур");
        message(p, "/astral menu — открыть игровое меню");
        message(p, "/astral economy — баланс астральных монет");
        message(p, "/astral shop — магазин предметов");
        message(p, "/astral start — добровольно включить RPG");
        message(p, "/astral choose <mage|warrior|ranger|guardian> — выбрать архетип");
        message(p, "/astral profile — персонаж и прогресс");
        message(p, "/astral cast — применить способность (8 сек. перезарядка)");
        message(p, "/astral skill <power|wisdom|vitality|focus|ability_id> — улучшить талант за очко");
        message(p, "/astral quest accept hunt — начать охоту на 5 монстров");
        message(p, "/astral quest info — прогресс задания | /astral stop — отключить RPG");
    }

    /**
     * Isolated Astral Nexus dungeon. No RPG items or progression are active in normal worlds.
     * Portal: 4x5 iron-block frame with a 2x3 white stained-glass-pane opening.
     */
    private final class DungeonManager implements Listener {
        static final String WORLD_NAME = "AstralNexusDungeon";
        private final NamespacedKey mobOwnerKey = new NamespacedKey(AstralNexusPlugin.this, "dungeon_mob_owner");
        private final NamespacedKey dungeonMobKey = new NamespacedKey(AstralNexusPlugin.this, "dungeon_mob");
        private final Map<UUID, org.bukkit.Location> returnLocations = new HashMap<>();
        private final Map<UUID, Integer> waves = new HashMap<>();
        private final Map<UUID, UUID> partyLeaderByPlayer = new HashMap<>();
        private final Map<UUID, Set<UUID>> partyMembers = new HashMap<>();
        private final Map<String, UUID> pendingPortalLeaders = new HashMap<>();
        private final Set<Integer> readyMaps = new HashSet<>();
        private final Map<UUID, Integer> remaining = new HashMap<>();
        private final Map<UUID, Integer> playerMaps = new HashMap<>();
        private final Map<Integer, UUID> activeMapOwners = new HashMap<>();
        private final Set<UUID> completionScheduled = new HashSet<>();
        private final Set<String> announcedPortals = new HashSet<>();
        private final Set<Integer> buildingMaps = new HashSet<>();
        private final Map<Integer, List<Runnable>> dungeonReadyCallbacks = new HashMap<>();
        private final NamespacedKey returnWorldKey = new NamespacedKey(AstralNexusPlugin.this, "return_world");
        private final NamespacedKey returnXKey = new NamespacedKey(AstralNexusPlugin.this, "return_x");
        private final NamespacedKey returnYKey = new NamespacedKey(AstralNexusPlugin.this, "return_y");
        private final NamespacedKey returnZKey = new NamespacedKey(AstralNexusPlugin.this, "return_z");
        private final NamespacedKey returnYawKey = new NamespacedKey(AstralNexusPlugin.this, "return_yaw");
        private final NamespacedKey returnPitchKey = new NamespacedKey(AstralNexusPlugin.this, "return_pitch");
        private final NamespacedKey mapKey = new NamespacedKey(AstralNexusPlugin.this, "dungeon_map");
        private final NamespacedKey lastMapKey = new NamespacedKey(AstralNexusPlugin.this, "last_dungeon_map");
        private final NamespacedKey bossKey = new NamespacedKey(AstralNexusPlugin.this, "dungeon_boss");
        private final NamespacedKey bossPhaseKey = new NamespacedKey(AstralNexusPlugin.this, "dungeon_boss_phase");
        private final Map<UUID, Integer> bossPulseTasks = new HashMap<>();
        private org.bukkit.World dungeonWorld;
        private static final int[] MAP_CENTERS = {0, 160, 320, 480, 640, 800};
        private static final String[] MAP_NAMES = {"Сумрачные галереи", "Аметистовый разлом", "Изумрудные недра", "Костяная бездна", "Пепельный лабиринт", "Кристальный провал"};
        private static final int ROUTE_ENTRY_Z = 23;
        private static final int ROUTE_END_Z = -134;
        private static final int[] ENCOUNTER_Z = {-20, -43, -66, -89, -111};
        private static final int BOSS_Z = -127;
        private static final int ROUTE_BASE_Y = 69;

        boolean isDungeon(Player player) {
            return player.getWorld().getName().equals(WORLD_NAME);
        }

        private record Portal(int x, int y, int z, boolean alongX) {}

        private int reserveRandomMap(UUID playerId) {
            List<Integer> available = new java.util.ArrayList<>();
            for (int i = 0; i < MAP_CENTERS.length; i++) {
                if (!activeMapOwners.containsKey(i) && !buildingMaps.contains(i)) available.add(i);
            }
            if (available.isEmpty()) return -1;
            Player player = Bukkit.getPlayer(playerId);
            Integer previousMap = player == null ? null
                    : player.getPersistentDataContainer().get(lastMapKey, PersistentDataType.INTEGER);
            if (previousMap != null && available.size() > 1) available.remove(previousMap);
            else if (previousMap != null && available.size() == 1 && available.get(0).equals(previousMap)) return -1;
            java.util.Collections.shuffle(available);
            int selected = available.get(0);
            if (player != null) player.getPersistentDataContainer().set(lastMapKey, PersistentDataType.INTEGER, selected);
            activeMapOwners.put(selected, playerId);
            readyMaps.remove(selected);
            partyLeaderByPlayer.put(playerId, playerId);
            partyMembers.put(playerId, new java.util.LinkedHashSet<>(Set.of(playerId)));
            playerMaps.put(playerId, selected);
            return selected;
        }

        private boolean claimExistingMap(Player player, int mapIndex) {
            UUID id = player.getUniqueId();
            UUID leader = partyLeaderByPlayer.getOrDefault(id, id);
            UUID owner = activeMapOwners.get(mapIndex);
            if (owner != null && !owner.equals(leader)) return false;
            activeMapOwners.putIfAbsent(mapIndex, leader);
            playerMaps.put(id, mapIndex);
            return true;
        }

        private void releaseMap(UUID playerId) {
            Integer mapIndex = playerMaps.get(playerId);
            if (mapIndex == null) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) mapIndex = player.getPersistentDataContainer()
                        .get(mapKey, PersistentDataType.INTEGER);
            }
            if (mapIndex != null) activeMapOwners.remove(mapIndex, playerId);
            playerMaps.remove(playerId);
        }

        @EventHandler
        public void onPortalFrameBuilt(org.bukkit.event.block.BlockPlaceEvent event) {
            Material type = event.getBlockPlaced().getType();
            if (type != Material.IRON_BLOCK && type != Material.WHITE_STAINED_GLASS_PANE) return;
            Bukkit.getScheduler().runTask(AstralNexusPlugin.this, () -> {
                Portal portal = findPortal(event.getBlockPlaced());
                if (portal == null) return;
                String id = event.getBlockPlaced().getWorld().getName() + ":" + portal.x + ":" + portal.y + ":" + portal.z + ":" + portal.alongX;
                if (announcedPortals.add(id)) {
                    Player player = event.getPlayer();
                    player.sendActionBar(Component.text("✦ Портал Astral Nexus пробуждён")
                            .color(net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE));
                    for (int i = 0; i < 18; i++) {
                        double dx = portal.alongX ? (i % 3) - 1 : 0;
                        double dz = portal.alongX ? 0 : (i % 3) - 1;
                        event.getBlockPlaced().getWorld().spawnParticle(Particle.PORTAL,
                                new org.bukkit.Location(event.getBlockPlaced().getWorld(),
                                        portal.x + dx + 0.5, portal.y + 1 + (i / 3) * 0.45,
                                        portal.z + dz + 0.5), 5, 0.12, 0.12, 0.12, 0.02);
                    }
                    player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.5f);
                }
            });
        }

        @EventHandler
        public void onPortalUse(PlayerInteractEvent event) {
            if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND
                    || event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
                    || event.getClickedBlock() == null) return;
            if (event.getClickedBlock().getType() != Material.WHITE_STAINED_GLASS_PANE) return;
            Portal portal = findPortal(event.getClickedBlock());
            if (portal == null) return;
            event.setCancelled(true);
            Player player = event.getPlayer();
            if (isDungeon(player)) {
                leaveDungeon(player, false);
                return;
            }
            String portalId = portalKey(portal, event.getClickedBlock().getWorld());
            enterDungeon(player, portalId);
        }

        private String portalKey(Portal portal, org.bukkit.World world) {
            return world.getName() + ":" + portal.x + ":" + portal.y + ":" + portal.z + ":" + portal.alongX;
        }

        private Portal findPortal(org.bukkit.block.Block clicked) {
            org.bukkit.World world = clicked.getWorld();
            int bx = clicked.getX(), by = clicked.getY(), bz = clicked.getZ();
            for (boolean alongX : new boolean[]{true, false}) {
                for (int ox = 0; ox <= 1; ox++) {
                    for (int oy = 0; oy <= 2; oy++) {
                        int x = bx - (alongX ? ox : 0);
                        int z = bz - (alongX ? 0 : ox);
                        int y = by - oy;
                        boolean valid = true;
                        for (int w = -1; w <= 2 && valid; w++) {
                            for (int h = -1; h <= 3; h++) {
                                int px = x + (alongX ? w : 0);
                                int pz = z + (alongX ? 0 : w);
                                Material expected = (w == -1 || w == 2 || h == -1 || h == 3)
                                        ? Material.IRON_BLOCK : Material.WHITE_STAINED_GLASS_PANE;
                                if (world.getBlockAt(px, y + h, pz).getType() != expected) {
                                    valid = false;
                                    break;
                                }
                            }
                        }
                        if (valid) return new Portal(x, y, z, alongX);
                    }
                }
            }
            return null;
        }

        private final Set<UUID> transitioning = new HashSet<>();

        private org.bukkit.World getDungeonWorld() {
            if (dungeonWorld != null && Bukkit.getWorld(WORLD_NAME) != null) return dungeonWorld;
            dungeonWorld = Bukkit.getWorld(WORLD_NAME);
            if (dungeonWorld == null) {
                dungeonWorld = new org.bukkit.WorldCreator(WORLD_NAME)
                        .environment(org.bukkit.World.Environment.NORMAL)
                        .type(org.bukkit.WorldType.FLAT)
                        .generateStructures(false).createWorld();
            }
            return dungeonWorld;
        }

        private void prepareDungeon(org.bukkit.World world, int mapIndex, Runnable ready) {
            if (readyMaps.contains(mapIndex)) {
                ready.run();
                return;
            }
            dungeonReadyCallbacks.computeIfAbsent(mapIndex, ignored -> new java.util.ArrayList<>()).add(ready);
            if (!buildingMaps.add(mapIndex)) return;
            world.setDifficulty(org.bukkit.Difficulty.HARD);
            world.setTime(18000L);
            world.setStorm(false);
            world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
            world.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, true);

            int centerChunk = MAP_CENTERS[mapIndex] >> 4;
            List<java.util.concurrent.CompletableFuture<org.bukkit.Chunk>> chunks = new java.util.ArrayList<>();
            for (int cx = centerChunk - 4; cx <= centerChunk + 4; cx++) {
                for (int cz = -10; cz <= 2; cz++) chunks.add(world.getChunkAtAsync(cx, cz, true, true));
            }
            java.util.concurrent.CompletableFuture.allOf(chunks.toArray(java.util.concurrent.CompletableFuture[]::new))
                    .whenComplete((ignored, error) -> Bukkit.getScheduler().runTask(AstralNexusPlugin.this, () -> {
                        if (error != null || Bukkit.getWorld(WORLD_NAME) == null) {
                            failMapPreparation(mapIndex, error == null ? "world unavailable" : error.getMessage());
                            return;
                        }
                        clearDungeonRegionInBatches(world, mapIndex);
                    }));
        }

        private void failMapPreparation(int mapIndex, String reason) {
            buildingMaps.remove(mapIndex);
            dungeonReadyCallbacks.remove(mapIndex);
            UUID owner = activeMapOwners.get(mapIndex);
            for (Player waiting : Bukkit.getOnlinePlayers()) {
                UUID id = waiting.getUniqueId();
                if (!id.equals(owner) && playerMaps.getOrDefault(id, -1) != mapIndex) continue;
                transitioning.remove(id);
                returnLocations.remove(id);
                removePartyMember(id);
                releaseMap(id);
                clearReturnLocation(waiting);
                waiting.sendActionBar(Component.text("Не удалось подготовить пещеру. Проверь журнал сервера.")
                        .color(net.kyori.adventure.text.format.NamedTextColor.RED));
            }
            if (owner != null) releaseMap(owner);
            getLogger().severe("Could not prepare cave route " + MAP_NAMES[mapIndex] + ": " + reason);
        }

        private void clearDungeonRegionInBatches(org.bukkit.World world, int mapIndex) {
            int centerX = MAP_CENTERS[mapIndex];
            final int minX = centerX - 52, maxX = centerX + 52;
            final int minZ = -150, maxZ = 40;
            final int minY = 37, maxY = 112;
            // Never regenerate a route under another player who was already left inside the world.
            for (Player occupant : world.getPlayers()) {
                int x = occupant.getLocation().getBlockX();
                int z = occupant.getLocation().getBlockZ();
                if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
                    failMapPreparation(mapIndex, "a player is still inside the selected route");
                    return;
                }
            }
            // This dedicated world contains only the RPG trial, so remove old mobs, drops and projectiles too.
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Player) continue;
                org.bukkit.Location at = entity.getLocation();
                if (at.getBlockX() >= minX && at.getBlockX() <= maxX
                        && at.getBlockZ() >= minZ && at.getBlockZ() <= maxZ) entity.remove();
            }

            int width = maxX - minX + 1;
            int depth = maxZ - minZ + 1;
            int height = maxY - minY + 1;
            long total = (long) width * depth * height;
            long[] cursor = {0};
            Bukkit.getScheduler().runTaskTimer(AstralNexusPlugin.this, task -> {
                int budget = 12000;
                while (budget-- > 0 && cursor[0] < total) {
                    long n = cursor[0]++;
                    int y = minY + (int) (n % height);
                    long column = n / height;
                    int z = minZ + (int) (column % depth);
                    int x = minX + (int) (column / depth);
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
                if (cursor[0] >= total) {
                    task.cancel();
                    buildDungeonInBatches(world, mapIndex);
                }
            }, 1L, 1L);
        }

        private record BlockPlacement(int x, int y, int z, Material material) {}

        private void put(List<BlockPlacement> blocks, int x, int y, int z, Material material) {
            blocks.add(new BlockPlacement(x, y, z, material));
        }

        private int cavePathOffset(int mapIndex, int z) {
            double t = (ROUTE_ENTRY_Z - z) / 20.0;
            return switch (mapIndex) {
                case 0 -> (int) Math.round(Math.sin(t * 0.68) * 8.0 + Math.sin(t * 1.55) * 2.0);
                case 1 -> (int) Math.round((Math.sin(t * 0.55 + 0.45) - Math.sin(0.45)) * 10.0
                        + Math.sin(t * 1.35) * 2.5);
                case 2 -> (int) Math.round((Math.sin(t * 0.72 + 0.9) - Math.sin(0.9)) * 8.5
                        + Math.sin(t * 1.8) * 2.2);
                default -> (int) Math.round(Math.sin(t * 0.43 + 0.7) * 13.0
                        + Math.sin(t * 1.12) * 3.2 - Math.sin(0.7) * 13.0);
            };
        }

        private double smoothCaveProfile(int mapIndex, double t) {
            double envelope = Math.sin(Math.PI * t);
            double basin = envelope * envelope;
            return switch (mapIndex) {
                // The first passage descends into one deep basin and climbs out in a long curve.
                case 0 -> -13.0 * basin + 1.45 * Math.sin(4.0 * Math.PI * t) * envelope;
                // The geode route has two broad undulations over its deeper central descent.
                case 1 -> -15.5 * basin + 1.65 * Math.sin(6.0 * Math.PI * t) * envelope;
                // The root cavern has several rolling shelves, but the overall descent stays continuous.
                case 2 -> -14.0 * basin + 1.80 * Math.sin(8.0 * Math.PI * t) * envelope;
                default -> -18.0 * basin + 2.25 * Math.sin(10.0 * Math.PI * t) * envelope;
            };
        }

        private int caveFloorY(int mapIndex, int z) {
            double t = (ROUTE_ENTRY_Z - z) / (double) (ROUTE_ENTRY_Z - ROUTE_END_Z);
            t = Math.max(0.0, Math.min(1.0, t));
            int base = ROUTE_BASE_Y + (int) Math.round(smoothCaveProfile(mapIndex, t));
            if (t > 0.08 && t < 0.92) {
                int roughPhase = Math.floorMod(z * 37 + mapIndex * 11, 19);
                if (roughPhase == 0) base--;
                else if (roughPhase == 2) base++;
            }
            return base;
        }

        private int chamberExpansion(int z) {
            int bonus = 0;
            for (int chamber : new int[]{-28, -67, -104, BOSS_Z}) {
                int distance = Math.abs(z - chamber);
                if (distance < 12) bonus = Math.max(bonus, 12 - distance);
            }
            return bonus;
        }

        private int caveHalfWidth(int mapIndex, int z, long seed) {
            if (mapIndex <= 2) {
                // Narrow passages are the main visual identity; encounter pockets widen only a little.
                int base = mapIndex == 0 ? 4 : mapIndex == 1 ? 4 : 5;
                int rockVariation = Math.floorMod((int) (z * 31L + seed), 3);
                int throatPhase = Math.floorMod(z + (int) (seed >> 11) + mapIndex * 17, 47);
                int squeeze = throatPhase < 9 ? 1 : 0;
                int encounterRoom = Math.min(2, chamberExpansion(z) / 6);
                return Math.max(3, base + rockVariation + encounterRoom - squeeze);
            }
            int base = 13;
            int noise = Math.floorMod((int) (z * 31L + seed), 4);
            return base + noise + chamberExpansion(z) / 2;
        }

        private int caveRoofY(int mapIndex, int z, int dx, int width, long seed) {
            double ratio = Math.min(1.0, dx * (double) dx / Math.max(1.0, width * (double) width));
            double arch = Math.sqrt(Math.max(0.0, 1.0 - ratio));
            int floorY = caveFloorY(mapIndex, z);
            if (mapIndex <= 2) {
                // Low, broken ceilings and periodically pinched throats keep the player inside the rock.
                int height = mapIndex == 1 ? 6 : 5;
                int noise = Math.floorMod((int) (z * 17L + dx * 13L + seed), 2);
                double chamberLift = Math.min(1.5, chamberExpansion(z) / 8.0);
                int throatPhase = Math.floorMod(z + (int) (seed >> 8) + mapIndex * 19, 67);
                double throat = Math.max(0.0, 1.0 - Math.abs(throatPhase - 33) / 9.0);
                int squeeze = (int) Math.round(throat * 2.0);
                return Math.max(floorY + 5,
                        floorY + 3 + (int) Math.round(arch * (height + chamberLift)) + noise - squeeze);
            }
            int height = 21;
            int noise = Math.floorMod((int) (z * 17L + dx * 13L + seed), 3);
            return floorY + 10 + (int) Math.round(arch * (height + chamberExpansion(z) / 2.0)) + noise;
        }

        private Material caveRock(int mapIndex, int x, int y, int z, long seed) {
            int v = Math.floorMod((int) (x * 17L + y * 7L + z * 29L + seed), 9);
            return switch (mapIndex) {
                case 0 -> switch (v) {
                    case 0 -> Material.CRACKED_DEEPSLATE_BRICKS;
                    case 1, 2 -> Material.TUFF;
                    case 3 -> Material.COBBLED_DEEPSLATE;
                    case 4 -> Material.GILDED_BLACKSTONE;
                    default -> Material.DEEPSLATE_BRICKS;
                };
                case 1 -> switch (v) {
                    case 0, 1 -> Material.CALCITE;
                    case 2 -> Material.SMOOTH_BASALT;
                    case 3 -> Material.POLISHED_BASALT;
                    case 4 -> Material.AMETHYST_BLOCK;
                    default -> Material.TUFF_BRICKS;
                };
                case 2 -> switch (v) {
                    case 0, 1 -> Material.MOSSY_STONE_BRICKS;
                    case 2 -> Material.MOSSY_COBBLESTONE;
                    case 3 -> Material.ROOTED_DIRT;
                    case 4 -> Material.MOSS_BLOCK;
                    default -> Material.TUFF_BRICKS;
                };
                case 4 -> switch (v) {
                    case 0, 1 -> Material.MAGMA_BLOCK;
                    case 2 -> Material.POLISHED_BLACKSTONE;
                    case 3 -> Material.BASALT;
                    case 4 -> Material.BLACKSTONE;
                    default -> Material.POLISHED_BLACKSTONE_BRICKS;
                };
                case 5 -> switch (v) {
                    case 0, 1 -> Material.AMETHYST_BLOCK;
                    case 2 -> Material.CALCITE;
                    case 3 -> Material.PRISMARINE_BRICKS;
                    case 4 -> Material.DARK_PRISMARINE;
                    default -> Material.TUFF;
                };
                default -> switch (v) {
                    case 0, 1 -> Material.BONE_BLOCK;
                    case 2 -> Material.POLISHED_BASALT;
                    case 3 -> Material.COBBLED_DEEPSLATE;
                    case 4 -> Material.SCULK;
                    case 5 -> Material.TUFF_BRICKS;
                    case 6 -> Material.BLACKSTONE;
                    default -> Material.DEEPSLATE_BRICKS;
                };
            };
        }

        private Material caveFloor(int mapIndex, int dx, int z, long seed) {
            int v = Math.floorMod((int) (dx * 11L + z * 23L + seed), 9);
            return switch (mapIndex) {
                case 0 -> v == 0 ? Material.CRACKED_DEEPSLATE_TILES
                        : v < 3 ? Material.POLISHED_DEEPSLATE : Material.DEEPSLATE_TILES;
                case 1 -> v == 0 ? Material.POLISHED_BASALT
                        : v < 3 ? Material.SMOOTH_BASALT : Material.CALCITE;
                case 2 -> v == 0 ? Material.MOSSY_COBBLESTONE
                        : v < 4 ? Material.MOSSY_STONE_BRICKS : Material.TUFF_BRICKS;
                case 4 -> v == 0 ? Material.MAGMA_BLOCK
                        : v < 4 ? Material.POLISHED_BLACKSTONE : Material.BASALT;
                case 5 -> v == 0 ? Material.AMETHYST_BLOCK
                        : v < 4 ? Material.CALCITE : Material.PRISMARINE_BRICKS;
                default -> v == 0 ? Material.POLISHED_BLACKSTONE
                        : v < 4 ? Material.COBBLED_DEEPSLATE : Material.BASALT;
            };
        }

        private Material caveUnderfloor(int mapIndex) {
            return switch (mapIndex) {
                case 0 -> Material.DEEPSLATE;
                case 1 -> Material.TUFF;
                case 2 -> Material.ROOTED_DIRT;
                case 3 -> Material.BLACKSTONE;
                case 4 -> Material.BASALT;
                default -> Material.CALCITE;
            };
        }

        private void addEntryGate(List<BlockPlacement> blocks, int centerX, int mapIndex) {
            Material floor = mapIndex == 0 ? Material.DEEPSLATE_BRICKS
                    : mapIndex == 1 ? Material.CALCITE : mapIndex == 2 ? Material.MOSSY_STONE_BRICKS : Material.POLISHED_BLACKSTONE;
            for (int x = -7; x <= 7; x++) {
                for (int z = ROUTE_ENTRY_Z - 5; z <= ROUTE_ENTRY_Z + 7; z++) {
                    put(blocks, centerX + x, 68, z, Material.DEEPSLATE);
                    put(blocks, centerX + x, 69, z, floor);
                    for (int y = 70; y <= 78; y++) put(blocks, centerX + x, y, z, Material.AIR);
                }
            }
            Material frame = mapIndex == 0 ? Material.GILDED_BLACKSTONE
                    : mapIndex == 1 ? Material.PURPUR_BLOCK : mapIndex == 2 ? Material.MOSSY_STONE_BRICKS : Material.BONE_BLOCK;
            for (int sign : new int[]{-1, 1}) {
                for (int y = 70; y <= 80; y++) put(blocks, centerX + sign * 7, y, ROUTE_ENTRY_Z + 6, frame);
                put(blocks, centerX + sign * 7, 81, ROUTE_ENTRY_Z + 6, Material.LANTERN);
            }
            for (int x = -7; x <= 7; x++) put(blocks, centerX + x, 80, ROUTE_ENTRY_Z + 6, frame);
            put(blocks, centerX, 79, ROUTE_ENTRY_Z + 6,
                    mapIndex == 1 ? Material.SEA_LANTERN : mapIndex == 2 ? Material.SHROOMLIGHT : Material.SOUL_LANTERN);
        }

        private Material caveWallTrim(int mapIndex) {
            return switch (mapIndex) {
                case 0 -> Material.POLISHED_BLACKSTONE_BRICKS;
                case 1 -> Material.POLISHED_BASALT;
                case 2 -> Material.MOSSY_STONE_BRICKS;
                case 3 -> Material.BONE_BLOCK;
                case 4 -> Material.POLISHED_BLACKSTONE_BRICKS;
                default -> Material.PRISMARINE_BRICKS;
            };
        }

        private Material caveWallAccent(int mapIndex) {
            return switch (mapIndex) {
                case 0 -> Material.GILDED_BLACKSTONE;
                case 1 -> Material.AMETHYST_BLOCK;
                case 2 -> Material.MOSS_BLOCK;
                case 3 -> Material.SCULK_CATALYST;
                case 4 -> Material.MAGMA_BLOCK;
                default -> Material.AMETHYST_BLOCK;
            };
        }

        private Material caveWallLight(int mapIndex) {
            return switch (mapIndex) {
                case 0 -> Material.SOUL_LANTERN;
                case 1 -> Material.SEA_LANTERN;
                case 2 -> Material.SHROOMLIGHT;
                case 3 -> Material.SOUL_LANTERN;
                case 4 -> Material.OCHRE_FROGLIGHT;
                default -> Material.SEA_LANTERN;
            };
        }

        private void addDecoratedCaveEdgeWalls(List<BlockPlacement> blocks, int mapIndex,
                                               int z, int routeX, int width, long seed) {
            Material trim = caveWallTrim(mapIndex);
            Material accent = caveWallAccent(mapIndex);
            Material lamp = caveWallLight(mapIndex);
            int floorY = caveFloorY(mapIndex, z);
            if (mapIndex <= 2) {
                int wallTop = caveRoofY(mapIndex, z, width, width, seed) + 3;
                for (int sign : new int[]{-1, 1}) {
                    int faceX = routeX + sign * (width + 1);
                    int backingX = routeX + sign * (width + 2);
                    for (int y = floorY - 1; y <= wallTop; y++) {
                        put(blocks, faceX, y, z, caveRock(mapIndex, faceX, y, z, seed));
                        put(blocks, backingX, y, z, caveRock(mapIndex, backingX, y, z, seed + 7));
                    }
                    put(blocks, faceX, floorY, z, trim);
                    if (Math.floorMod(z + sign * 7 + (int) (seed >> 5), 13) == 0) {
                        put(blocks, faceX, floorY + 1, z, accent);
                        put(blocks, faceX, floorY + 2, z, caveRock(mapIndex, faceX, floorY + 2, z, seed));
                    }
                    // Decorative wall sconces: warm/cool light is recessed into a framed stone niche.
                    int lampPhase = Math.floorMod(z + sign * 11 + (int) seed, 17);
                    if (lampPhase == 0 || lampPhase == 1) {
                        int lampY = Math.min(wallTop - 1, floorY + 4);
                        put(blocks, faceX, lampY - 1, z, trim);
                        put(blocks, faceX, lampY, z, lamp);
                        put(blocks, backingX, lampY, z, trim);
                        put(blocks, faceX, lampY + 1, z, accent);
                        if (mapIndex == 0) {
                            put(blocks, faceX, lampY + 2, z, Material.CHAIN);
                            put(blocks, backingX, lampY + 2, z, Material.CHAIN);
                        } else if (mapIndex == 1) {
                            put(blocks, faceX, lampY + 2, z, Material.AMETHYST_CLUSTER);
                        } else {
                            put(blocks, faceX, lampY + 2, z, Material.GLOW_LICHEN);
                        }
                    }
                }
                return;
            }
            int wallTop = caveRoofY(mapIndex, z, width, width, seed) + 5;
            for (int sign : new int[]{-1, 1}) {
                int faceX = routeX + sign * (width + 1);
                int backingX = routeX + sign * (width + 2);
                for (int y = floorY - 1; y <= wallTop; y++) {
                    put(blocks, faceX, y, z, caveRock(mapIndex, faceX, y, z, seed));
                    put(blocks, backingX, y, z, caveRock(mapIndex, backingX, y, z, seed + 7));
                }
                put(blocks, faceX, floorY, z, trim);
                put(blocks, backingX, floorY, z, trim);
                if (Math.floorMod(z + (int) seed, 4) == 0) {
                    put(blocks, faceX, floorY + 3, z, accent);
                    put(blocks, faceX, wallTop - 1, z, trim);
                }
                if (Math.floorMod(z + (int) seed, 12) == 0) {
                    for (int y = floorY + 1; y <= wallTop + 1; y++) put(blocks, faceX, y, z, trim);
                    put(blocks, faceX, wallTop + 2, z, accent);
                    put(blocks, faceX, floorY + 6, z, lamp);
                    put(blocks, faceX, floorY + 5, z - 1, accent);
                    put(blocks, faceX, floorY + 5, z + 1, accent);
                    for (int y = floorY + 8; y <= Math.min(wallTop + 3, floorY + 15); y++)
                        put(blocks, faceX, y, z, Material.CHAIN);
                    put(blocks, faceX, Math.min(wallTop + 4, floorY + 16), z, accent);
                }
            }
        }

        private void addClaustrophobicTunnelDecor(List<BlockPlacement> blocks, int mapIndex,
                                                   int z, int routeX, int width, int floorY, long seed) {
            if (mapIndex > 2) return;
            Material rubble = mapIndex == 0 ? Material.COBBLED_DEEPSLATE
                    : mapIndex == 1 ? Material.TUFF : Material.ROOTED_DIRT;
            for (int sign : new int[]{-1, 1}) {
                int sideX = routeX + sign * Math.max(2, width - 1);
                int ceilingX = routeX + sign * Math.max(1, width - 2);
                int roof = caveRoofY(mapIndex, z, Math.abs(ceilingX - routeX), width, seed);

                // Rough shelves and fallen fragments cling to the lower side walls.
                int wallNoise = Math.floorMod((int) (z * 19L + sign * 23L + seed), 9);
                if (wallNoise <= 4) {
                    put(blocks, sideX, floorY + 1, z, rubble);
                    if (wallNoise == 0 || wallNoise == 3)
                        put(blocks, sideX, floorY + 2, z, caveRock(mapIndex, sideX, floorY + 2, z, seed));
                }

                // Stalactites hang close to the walls, leaving the centre line passable.
                if (Math.floorMod(z + sign * 5 + (int) seed, 13) == 0) {
                    int available = Math.max(1, roof - floorY - 4);
                    int length = Math.min(3, 1 + Math.floorMod(z + sign * 11 + (int) seed, 3));
                    length = Math.min(length, available);
                    for (int k = 1; k <= length; k++)
                        put(blocks, ceilingX, roof - k, z, Material.POINTED_DRIPSTONE);
                }

                // Heavy floor teeth and rubble make the margins feel dangerous, not the walking line.
                if (Math.floorMod(z + sign * 17 + (int) (seed >> 3), 23) == 0) {
                    put(blocks, sideX, floorY + 1, z, Material.DRIPSTONE_BLOCK);
                    if (width >= 4) put(blocks, sideX, floorY + 2, z, Material.POINTED_DRIPSTONE);
                }

                if (mapIndex == 0 && Math.floorMod(z + sign * 7 + (int) seed, 29) == 0) {
                    put(blocks, routeX + sign * (width + 1), floorY + 3, z, Material.CRACKED_DEEPSLATE_BRICKS);
                    put(blocks, routeX + sign * (width + 1), floorY + 4, z, Material.GILDED_BLACKSTONE);
                } else if (mapIndex == 1 && Math.floorMod(z + sign * 13 + (int) seed, 31) == 0) {
                    put(blocks, routeX + sign * (width + 1), floorY + 3, z, Material.CALCITE);
                    put(blocks, routeX + sign * (width + 1), floorY + 4, z, Material.AMETHYST_BLOCK);
                } else if (mapIndex == 2 && Math.floorMod(z + sign * 19 + (int) seed, 27) == 0) {
                    put(blocks, routeX + sign * (width + 1), roof - 2, z, Material.HANGING_ROOTS);
                    put(blocks, routeX + sign * (width + 1), floorY + 2, z, Material.MOSS_BLOCK);
                }
            }
        }

        private void addLongCaveRoute(List<BlockPlacement> blocks, int centerX, int mapIndex, long seed) {
            Material underfloor = caveUnderfloor(mapIndex);
            for (int z = ROUTE_ENTRY_Z + 7; z >= ROUTE_END_Z; z--) {
                int routeX = centerX + cavePathOffset(mapIndex, z);
                int width = caveHalfWidth(mapIndex, z, seed);
                int floorY = caveFloorY(mapIndex, z);
                int edgeRoof = caveRoofY(mapIndex, z, width, width, seed);

                for (int dx = -width - 2; dx <= width + 2; dx++) {
                    int x = routeX + dx;
                    if (Math.abs(dx) <= width) {
                        int roofY = caveRoofY(mapIndex, z, Math.abs(dx), width, seed);
                        // Solid earth beneath the sloping path prevents gaps beneath ramps and landings.
                        for (int y = 37; y < floorY; y++) put(blocks, x, y, z, underfloor);
                        put(blocks, x, floorY, z, caveFloor(mapIndex, dx, z, seed));
                        for (int y = floorY + 1; y < roofY; y++) put(blocks, x, y, z, Material.AIR);
                        put(blocks, x, roofY, z, caveRock(mapIndex, x, roofY, z, seed));
                    } else {
                        int wallTop = edgeRoof + 4;
                        // Fill the earth below the outer wall as well; the boundary never floats over a void.
                        for (int y = 37; y < floorY - 1; y++) put(blocks, x, y, z, underfloor);
                        for (int y = floorY - 1; y <= wallTop; y++)
                            put(blocks, x, y, z, caveRock(mapIndex, x, y, z, seed));
                    }
                }

                addDecoratedCaveEdgeWalls(blocks, mapIndex, z, routeX, width, seed);
                addClaustrophobicTunnelDecor(blocks, mapIndex, z, routeX, width, floorY, seed);

                // Rough rock shelves and fractured patches break the otherwise flat walking surface.
                if (mapIndex <= 2 && Math.floorMod(z * 7 + (int) seed, 11) == 0) {
                    int sign = Math.floorMod(z + (int) (seed >> 6), 2) == 0 ? -1 : 1;
                    int edgeX = routeX + sign * Math.max(2, width - 1);
                    put(blocks, edgeX, floorY, z, mapIndex == 0 ? Material.GRAVEL
                            : mapIndex == 1 ? Material.TUFF : Material.MOSSY_COBBLESTONE);
                    if (Math.floorMod(z + (int) seed, 3) == 0)
                        put(blocks, edgeX - sign, floorY, z, caveFloor(mapIndex, -sign, z, seed));
                }

                // Low ribbed supports follow the narrow cave ceiling.
                if (Math.floorMod(z + (int) seed, 19) == 0) {
                    Material beam = mapIndex == 0 ? Material.DEEPSLATE_BRICKS
                            : mapIndex == 1 ? Material.POLISHED_BASALT
                            : mapIndex == 2 ? Material.MOSSY_STONE_BRICKS : Material.BONE_BLOCK;
                    if (mapIndex <= 2) {
                        int side = Math.max(2, width - 1);
                        for (int sign : new int[]{-1, 1}) {
                            int sx = routeX + sign * side;
                            int postTop = caveRoofY(mapIndex, z, side, width, seed);
                            for (int y = floorY + 1; y < postTop; y++) put(blocks, sx, y, z, beam);
                            put(blocks, sx, postTop, z, caveWallTrim(mapIndex));
                        }
                        for (int dx = -side; dx <= side; dx++) {
                            int archY = caveRoofY(mapIndex, z, Math.abs(dx), width, seed);
                            put(blocks, routeX + dx, archY, z, beam);
                        }
                    } else {
                        int side = Math.max(7, width - 2);
                        for (int sign : new int[]{-1, 1}) {
                            int sx = routeX + sign * side;
                            for (int y = floorY + 1; y <= floorY + 15; y++) put(blocks, sx, y, z, beam);
                            put(blocks, sx + sign, floorY + 15, z, Material.CHAIN);
                        }
                        for (int dx = -side; dx <= side; dx++) {
                            int archY = floorY + 15 + (int) Math.round(
                                    Math.sqrt(Math.max(0, side * side - dx * dx)) * 0.27);
                            if (archY < floorY + 20) put(blocks, routeX + dx, archY, z, beam);
                        }
                    }
                }

                // A few hanging light clusters illuminate the floor without flattening the cave mood.
                if (mapIndex <= 2 && Math.floorMod(z + (int) seed, 29) == 0) {
                    int sign = Math.floorMod(z + (int) (seed >> 4), 2) == 0 ? -1 : 1;
                    int wallX = routeX + sign * (width + 1);
                    int lampY = floorY + 5;
                    put(blocks, wallX, lampY, z, caveWallLight(mapIndex));
                    put(blocks, wallX, lampY + 1, z, caveWallAccent(mapIndex));
                    put(blocks, wallX, lampY - 1, z, caveWallTrim(mapIndex));
                    if (mapIndex == 2) {
                        put(blocks, wallX - sign, lampY + 1, z, Material.GLOW_LICHEN);
                        put(blocks, wallX + sign, lampY + 1, z, Material.GLOW_LICHEN);
                    }
                } else if (mapIndex > 2 && Math.floorMod(z + (int) seed, 11) == 0) {
                    int roof = caveRoofY(mapIndex, z, 0, width, seed);
                    for (int chainY = roof - 2; chainY >= roof - 5; chainY--)
                        put(blocks, routeX, chainY, z, Material.CHAIN);
                    put(blocks, routeX, roof - 6, z, Material.SOUL_LANTERN);
                }

                // The fourth route keeps its distinct ceiling formations.
                if (mapIndex == 3 && Math.floorMod(z + (int) seed, 19) == 0) {
                    for (int sign : new int[]{-1, 1}) {
                        int dx = sign * Math.max(5, width - 4);
                        int roof = caveRoofY(mapIndex, z, Math.abs(dx), width, seed);
                        int length = 3 + Math.floorMod(z + sign * 7 + (int) seed, 5);
                        for (int k = 0; k < length; k++)
                            put(blocks, routeX + dx, roof - 1 - k, z, Material.POINTED_DRIPSTONE);
                    }
                }

                if (mapIndex == 0 && Math.floorMod(z + (int) seed, 27) == 0) {
                    // Broken mineshaft braces remain attached to the side walls.
                    for (int sign : new int[]{-1, 1}) {
                        int sx = routeX + sign * (width + 1);
                        put(blocks, sx, floorY + 2, z, Material.POLISHED_BLACKSTONE_BRICKS);
                        put(blocks, sx, floorY + 3, z, Material.CRACKED_DEEPSLATE_BRICKS);
                        put(blocks, sx, floorY + 4, z, Material.GILDED_BLACKSTONE);
                    }
                } else if (mapIndex == 1 && Math.floorMod(z + (int) seed, 24) == 0) {
                    // Small amethyst seams are embedded in the wall, not grown into the walking path.
                    for (int sign : new int[]{-1, 1}) {
                        int sx = routeX + sign * (width + 1);
                        put(blocks, sx, floorY + 2, z, Material.CALCITE);
                        put(blocks, sx, floorY + 3, z, Material.AMETHYST_BLOCK);
                    }
                } else if (mapIndex == 2 && Math.floorMod(z + (int) seed, 21) == 0) {
                    // Roots and moss cling to the margins; the middle of the passage remains clear.
                    int roof = caveRoofY(mapIndex, z, Math.max(1, width - 2), width, seed);
                    for (int sign : new int[]{-1, 1}) {
                        int sx = routeX + sign * (width + 1);
                        put(blocks, sx, Math.min(roof - 1, floorY + 5), z, Material.HANGING_ROOTS);
                        put(blocks, sx, floorY + 2, z, Material.MOSS_BLOCK);
                    }
                } else if (mapIndex == 3 && Math.floorMod(z + (int) seed, 18) == 0) {
                    // Bone ribs, hanging dripstone and sculk make this route visibly different.
                    int roof = caveRoofY(mapIndex, z, 0, width, seed);
                    for (int sign : new int[]{-1, 1}) {
                        int sx = routeX + sign * Math.max(6, width - 3);
                        int ribHeight = 7 + Math.floorMod(z + sign * 11 + (int) seed, 6);
                        for (int k = 0; k < ribHeight; k++)
                            put(blocks, sx, floorY + 1 + k, z, k % 3 == 0 ? Material.BONE_BLOCK : Material.POLISHED_BASALT);
                        put(blocks, sx, floorY + ribHeight + 1, z, Material.SCULK_CATALYST);
                        for (int k = 0; k < 5; k++)
                            put(blocks, sx, roof - 1 - k, z, Material.POINTED_DRIPSTONE);
                    }
                    put(blocks, routeX, roof - 2, z, Material.SOUL_LANTERN);
                    put(blocks, routeX - 3, floorY + 1, z, Material.SCULK);
                    put(blocks, routeX + 3, floorY + 1, z, Material.SCULK);
                }
            }

            addEntryGate(blocks, centerX, mapIndex);
            addSideGrotto(blocks, centerX, mapIndex, -42, -1, seed);
            addSideGrotto(blocks, centerX, mapIndex, -82, 1, seed + 131);
            addSideGrotto(blocks, centerX, mapIndex, -112, -1, seed + 271);
            addFinalSanctum(blocks, centerX, mapIndex, seed);
        }

        private void addSideGrotto(List<BlockPlacement> blocks, int centerX, int mapIndex, int z, int side, long seed) {
            int routeX = centerX + cavePathOffset(mapIndex, z);
            int width = caveHalfWidth(mapIndex, z, seed);
            int startX = routeX + side * (width - 1);
            Material floor = mapIndex == 0 ? Material.POLISHED_DEEPSLATE
                    : mapIndex == 1 ? Material.CALCITE : mapIndex == 2 ? Material.MOSSY_STONE_BRICKS : Material.POLISHED_BLACKSTONE;
            int grottoReach = mapIndex <= 2 ? 8 : 15;
            int grottoHalfSpan = mapIndex <= 2 ? 5 : 8;
            for (int outward = 0; outward <= grottoReach; outward++) {
                for (int dz = -grottoHalfSpan; dz <= grottoHalfSpan; dz++) {
                    double ellipse = (outward / (double) grottoReach) * (outward / (double) grottoReach)
                            + (dz / (double) grottoHalfSpan) * (dz / (double) grottoHalfSpan);
                    if (ellipse > 1.0) continue;
                    int x = startX + side * outward;
                    int localZ = z + dz;
                    int floorY = caveFloorY(mapIndex, localZ);
                    int roof = mapIndex <= 2
                            ? floorY + 6 + (int) Math.round(Math.sqrt(1.0 - ellipse) * 7.0)
                            : floorY + 10 + (int) Math.round(Math.sqrt(1.0 - ellipse) * 15.0);
                    for (int y = 37; y < floorY; y++)
                        put(blocks, x, y, localZ, caveUnderfloor(mapIndex));
                    put(blocks, x, floorY, localZ, floor);
                    if (ellipse >= 0.82) {
                        // Closed, solid perimeter walls follow the grotto's descending and rising floor.
                        for (int y = floorY + 1; y <= roof; y++)
                            put(blocks, x, y, localZ, caveRock(mapIndex, x, y, localZ, seed));
                        if (Math.floorMod(outward + dz + (int) seed, mapIndex <= 2 ? 17 : 7) == 0) {
                            put(blocks, x, floorY + 4, localZ,
                                    mapIndex <= 2 ? Material.SOUL_LANTERN : caveWallLight(mapIndex));
                            put(blocks, x, floorY + 3, localZ, caveWallTrim(mapIndex));
                            put(blocks, x, floorY + 5, localZ, caveWallTrim(mapIndex));
                        }
                    } else {
                        for (int y = floorY + 1; y < roof; y++) put(blocks, x, y, localZ, Material.AIR);
                        put(blocks, x, roof, localZ, caveRock(mapIndex, x, roof, localZ, seed));
                    }
                }
            }

            int relicX = startX + side * (mapIndex <= 2 ? 5 : 11);
            int relicY = caveFloorY(mapIndex, z);
            if (mapIndex == 0) {
                for (int dx = -3; dx <= 3; dx++) put(blocks, relicX + dx, relicY + 1, z, Material.CHISELED_DEEPSLATE);
                put(blocks, relicX, relicY + 2, z, Material.GOLD_BLOCK);
                put(blocks, relicX, relicY + 3, z, Material.LANTERN);
                for (int y = relicY + 4; y <= relicY + 9; y++) put(blocks, relicX, y, z, Material.CHAIN);
            } else if (mapIndex == 1) {
                for (int y = relicY + 1; y <= relicY + 4; y++) put(blocks, relicX, y, z, Material.AMETHYST_BLOCK);
                put(blocks, relicX - 1, relicY + 2, z, Material.CALCITE);
                put(blocks, relicX + 1, relicY + 2, z, Material.CALCITE);
            } else if (mapIndex == 2) {
                put(blocks, relicX, relicY + 1, z, Material.MOSS_BLOCK);
                put(blocks, relicX - 2, relicY + 1, z, Material.FLOWERING_AZALEA_LEAVES);
                put(blocks, relicX + 2, relicY + 1, z, Material.FLOWERING_AZALEA_LEAVES);
                put(blocks, relicX, relicY + 5, z, Material.SHROOMLIGHT);
                put(blocks, relicX, relicY + 6, z, Material.HANGING_ROOTS);
            } else {
                for (int dx = -3; dx <= 3; dx += 2) {
                    int ribs = 7 + Math.floorMod(dx + (int) seed, 4);
                    for (int y = relicY + 1; y <= relicY + ribs; y++)
                        put(blocks, relicX + dx, y, z, y % 2 == 0 ? Material.BONE_BLOCK : Material.POLISHED_BASALT);
                }
                put(blocks, relicX, relicY + 8, z, Material.SCULK_CATALYST);
                put(blocks, relicX, relicY + 9, z, Material.SOUL_LANTERN);
                for (int y = relicY + 10; y <= relicY + 14; y++)
                    put(blocks, relicX, y, z + 2, Material.CHAIN);
            }
        }

        private void addFinalSanctum(List<BlockPlacement> blocks, int centerX, int mapIndex, long seed) {
            int z = BOSS_Z;
            int routeX = centerX + cavePathOffset(mapIndex, z);
            Material trim = mapIndex == 0 ? Material.GILDED_BLACKSTONE
                    : mapIndex == 1 ? Material.AMETHYST_BLOCK : mapIndex == 2 ? Material.MOSSY_STONE_BRICKS : Material.BONE_BLOCK;
            Material light = Material.SOUL_LANTERN;
            int radiusX = mapIndex <= 2 ? 15 : 20;
            int radiusZ = mapIndex <= 2 ? 13 : 18;
            int roomRoofLift = mapIndex <= 2 ? 13 : 22;
            int roomArchLift = mapIndex <= 2 ? 8 : 13;

            // The first three routes end in a compact pressure chamber, not a huge open hall.
            for (int dx = -radiusX; dx <= radiusX; dx++) {
                for (int dz = -radiusZ; dz <= radiusZ; dz++) {
                    double ellipse = dx * dx / (double) (radiusX * radiusX)
                            + dz * dz / (double) (radiusZ * radiusZ);
                    if (ellipse > 1.0) continue;
                    int x = routeX + dx;
                    int localZ = z + dz;
                    int floorY = caveFloorY(mapIndex, localZ);
                    int roof = floorY + roomRoofLift
                            + (int) Math.round(Math.sqrt(1.0 - ellipse) * roomArchLift);
                    for (int y = 37; y < floorY; y++)
                        put(blocks, x, y, localZ, caveUnderfloor(mapIndex));
                    put(blocks, x, floorY, localZ, caveFloor(mapIndex, dx, localZ, seed));
                    for (int y = floorY + 1; y < roof; y++) put(blocks, x, y, localZ, Material.AIR);
                    put(blocks, x, roof, localZ, caveRock(mapIndex, x, roof, localZ, seed));
                }
            }

            // A closed, decorated boundary wall follows the local terrain around the whole final cavern.
            for (int dx = -(radiusX + 2); dx <= radiusX + 2; dx++) {
                for (int dz = -(radiusZ + 2); dz <= radiusZ + 2; dz++) {
                    double edge = dx * dx / (double) (radiusX * radiusX)
                            + dz * dz / (double) (radiusZ * radiusZ);
                    if (edge < 0.82 || edge > 1.18) continue;
                    if (dz >= radiusZ - 3 && Math.abs(dx) <= radiusX - 3) continue;
                    int x = routeX + dx;
                    int wallZ = z + dz;
                    int localFloor = caveFloorY(mapIndex, wallZ);
                    int wallTop = localFloor + (mapIndex <= 2 ? 14 : 22);
                    for (int y = 37; y < localFloor; y++)
                        put(blocks, x, y, wallZ, caveUnderfloor(mapIndex));
                    put(blocks, x, localFloor, wallZ, caveFloor(mapIndex, dx, wallZ, seed));
                    for (int y = localFloor + 1; y <= wallTop; y++) {
                        Material wall = (y == localFloor + 1 || y == localFloor + 2
                                || y == wallTop - 1 || y == wallTop)
                                ? trim : caveRock(mapIndex, x, y, wallZ, seed);
                        put(blocks, x, y, wallZ, wall);
                    }
                    if (Math.floorMod(dx + dz, mapIndex <= 2 ? 19 : 7) == 0) {
                        put(blocks, x, localFloor + (mapIndex <= 2 ? 5 : 10), wallZ, light);
                        put(blocks, x, localFloor + (mapIndex <= 2 ? 4 : 9), wallZ, trim);
                        put(blocks, x, localFloor + (mapIndex <= 2 ? 6 : 11), wallZ, trim);
                    }
                }
            }

            // Short supports are cut into the walls, preserving a tight but usable boss arena.
            int pierOffsetX = mapIndex <= 2 ? radiusX - 3 : 17;
            int pierOffsetZ = mapIndex <= 2 ? radiusZ - 3 : 10;
            int pierHeight = mapIndex <= 2 ? 11 : 19;
            for (int sx : new int[]{-pierOffsetX, pierOffsetX}) {
                for (int sign : new int[]{-1, 1}) {
                    int pierX = routeX + sx;
                    int pierZ = z + sign * pierOffsetZ;
                    int pierFloor = caveFloorY(mapIndex, pierZ);
                    for (int y = pierFloor + 1; y <= pierFloor + pierHeight; y++)
                        put(blocks, pierX, y, pierZ, trim);
                    put(blocks, pierX, pierFloor + pierHeight + 1, pierZ, caveWallAccent(mapIndex));
                }
            }

            int daisZ = z - (mapIndex <= 2 ? 7 : 14);
            int daisFloor = caveFloorY(mapIndex, daisZ);
            for (int dx = -5; dx <= 5; dx++) {
                for (int dz = -5; dz <= 5; dz++) {
                    if (Math.abs(dx) == 5 || Math.abs(dz) == 5)
                        put(blocks, routeX + dx, daisFloor + 1, daisZ + dz, trim);
                    else if (Math.abs(dx) <= 2 && Math.abs(dz) <= 2)
                        put(blocks, routeX + dx, daisFloor + 1, daisZ + dz, light);
                }
            }
            for (int y = daisFloor + 2; y <= daisFloor + (mapIndex <= 2 ? 6 : 11); y++)
                put(blocks, routeX, y, daisZ, Material.CHAIN);
            put(blocks, routeX, daisFloor + (mapIndex <= 2 ? 7 : 12), daisZ, light);
        }

                private void buildDungeonInBatches(org.bukkit.World world, int mapIndex) {
            List<BlockPlacement> blocks = new java.util.ArrayList<>(150000);
            int centerX = MAP_CENTERS[mapIndex];
            long seed = java.util.concurrent.ThreadLocalRandom.current().nextLong();
            addLongCaveRoute(blocks, centerX, mapIndex, seed);
            final int[] cursor = {0};
            Bukkit.getScheduler().runTaskTimer(AstralNexusPlugin.this, task -> {
                int limit = Math.min(cursor[0] + 4200, blocks.size());
                while (cursor[0] < limit) {
                    BlockPlacement block = blocks.get(cursor[0]++);
                    world.getBlockAt(block.x(), block.y(), block.z()).setType(block.material(), false);
                }
                if (cursor[0] >= blocks.size()) {
                    task.cancel();
                    world.setSpawnLocation(centerX, caveFloorY(mapIndex, ROUTE_ENTRY_Z) + 1, ROUTE_ENTRY_Z);
                    buildingMaps.remove(mapIndex);
                    readyMaps.add(mapIndex);
                    List<Runnable> callbacks = dungeonReadyCallbacks.remove(mapIndex);
                    if (callbacks != null) callbacks.forEach(Runnable::run);
                    getLogger().info("Regenerated " + MAP_NAMES[mapIndex] + " as a clean long cave route ("
                            + blocks.size() + " block placements).");
                }
            }, 1L, 1L);
        }

        private void populateTreasureChests(org.bukkit.World world, int centerX) {
            for (int[] pos : new int[][]{
                    {0,0},{-7,-7},{7,-7},{-7,7},{7,7},{-7,0},{7,0},{0,-7},{0,7},
                    {-9,-11},{9,-11},{-9,11},{9,11}
            }) {
                int chestY = pos[0] == 0 && pos[1] == 0 ? 41 : 40;
                org.bukkit.block.Block block = world.getBlockAt(centerX + pos[0], chestY, pos[1]);
                if (!(block.getState() instanceof org.bukkit.block.Chest chest)) continue;
                chest.getBlockInventory().addItem(new ItemStack(Material.GOLD_INGOT, 8 + Math.floorMod(pos[0] + pos[1], 9)));
                chest.getBlockInventory().addItem(new ItemStack(Material.EMERALD, 3 + Math.floorMod(pos[0] * pos[1], 5)));
                if (pos[0] == 0 && pos[1] == 0)
                    chest.getBlockInventory().addItem(new ItemStack(Material.DIAMOND, 5));
                chest.update(true, false);
            }
        }

        private void saveReturnLocation(Player player, org.bukkit.Location location) {
            var data = player.getPersistentDataContainer();
            data.set(returnWorldKey, PersistentDataType.STRING, location.getWorld().getName());
            data.set(returnXKey, PersistentDataType.DOUBLE, location.getX());
            data.set(returnYKey, PersistentDataType.DOUBLE, location.getY());
            data.set(returnZKey, PersistentDataType.DOUBLE, location.getZ());
            data.set(returnYawKey, PersistentDataType.FLOAT, location.getYaw());
            data.set(returnPitchKey, PersistentDataType.FLOAT, location.getPitch());
        }

        private org.bukkit.Location readReturnLocation(Player player) {
            var data = player.getPersistentDataContainer();
            String worldName = data.get(returnWorldKey, PersistentDataType.STRING);
            if (worldName == null) return null;
            org.bukkit.World world = Bukkit.getWorld(worldName);
            if (world == null) return null;
            Double x = data.get(returnXKey, PersistentDataType.DOUBLE);
            Double y = data.get(returnYKey, PersistentDataType.DOUBLE);
            Double z = data.get(returnZKey, PersistentDataType.DOUBLE);
            if (x == null || y == null || z == null) return null;
            float yaw = data.getOrDefault(returnYawKey, PersistentDataType.FLOAT, 0f);
            float pitch = data.getOrDefault(returnPitchKey, PersistentDataType.FLOAT, 0f);
            return new org.bukkit.Location(world, x, y, z, yaw, pitch);
        }

        private void clearReturnLocation(Player player) {
            var data = player.getPersistentDataContainer();
            data.remove(returnWorldKey); data.remove(returnXKey); data.remove(returnYKey);
            data.remove(returnZKey); data.remove(returnYawKey); data.remove(returnPitchKey);
            data.remove(mapKey);
        }

        private void enterDungeon(Player player, String portalId) {
            UUID id = player.getUniqueId();
            if (transitioning.contains(id) || isDungeon(player)) return;
            UUID pendingLeader = pendingPortalLeaders.get(portalId);
            if (pendingLeader != null && partyMembers.containsKey(pendingLeader)
                    && partyMembers.get(pendingLeader).size() < 3
                    && playerMaps.containsKey(pendingLeader)) {
                joinDungeonParty(player, pendingLeader);
                return;
            }

            if (!transitioning.add(id)) return;
            org.bukkit.World world = getDungeonWorld();
            if (world == null) {
                transitioning.remove(id);
                message(player, ChatColor.RED + "Не удалось создать подземелье. Проверь журнал сервера.");
                return;
            }
            int mapIndex = reserveRandomMap(id);
            if (mapIndex < 0) {
                transitioning.remove(id);
                player.sendActionBar(Component.text("Все маршруты сейчас заняты. Попробуй чуть позже.")
                        .color(net.kyori.adventure.text.format.NamedTextColor.YELLOW));
                return;
            }
            pendingPortalLeaders.put(portalId, id);
            Bukkit.getScheduler().runTaskLater(AstralNexusPlugin.this, () -> {
                if (pendingPortalLeaders.get(portalId) == id) pendingPortalLeaders.remove(portalId);
            }, 80L);
            teleportPartyMember(player, id, mapIndex, world, false);
        }

        private void joinDungeonParty(Player player, UUID leader) {
            UUID id = player.getUniqueId();
            if (!transitioning.add(id)) return;
            Set<UUID> members = partyMembers.get(leader);
            Integer mapIndex = playerMaps.get(leader);
            org.bukkit.World world = getDungeonWorld();
            if (members == null || members.size() >= 3 || mapIndex == null || world == null) {
                transitioning.remove(id);
                player.sendActionBar(Component.text("Группа уже заполнена или подземелье недоступно.")
                        .color(net.kyori.adventure.text.format.NamedTextColor.YELLOW));
                return;
            }
            members.add(id);
            partyLeaderByPlayer.put(id, leader);
            playerMaps.put(id, mapIndex);
            player.getPersistentDataContainer().set(mapKey, PersistentDataType.INTEGER, mapIndex);
            teleportPartyMember(player, leader, mapIndex, world, true);
        }

        private void teleportPartyMember(Player player, UUID leader, int mapIndex,
                                         org.bukkit.World world, boolean joined) {
            UUID id = player.getUniqueId();
            org.bukkit.Location returnPoint = player.getLocation().clone();
            returnLocations.put(id, returnPoint);
            saveReturnLocation(player, returnPoint);
            player.getPersistentDataContainer().set(mapKey, PersistentDataType.INTEGER, mapIndex);
            player.closeInventory();
            player.sendActionBar(Component.text(joined ? "Присоединяю к группе..." : "Подготавливаю свежую пещеру...")
                    .color(net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE));
            prepareDungeon(world, mapIndex, () -> {
                if (!player.isOnline() || !transitioning.contains(id)) {
                    if (!player.isOnline()) releaseMap(id);
                    return;
                }
                int centerX = MAP_CENTERS[mapIndex];
                org.bukkit.Location destination = new org.bukkit.Location(world, centerX + 0.5,
                        caveFloorY(mapIndex, ROUTE_ENTRY_Z) + 1.1, ROUTE_ENTRY_Z + 0.5);
                player.teleportAsync(destination).whenComplete((success, error) ->
                        Bukkit.getScheduler().runTask(AstralNexusPlugin.this, () -> {
                            transitioning.remove(id);
                            if (!player.isOnline()) {
                                releaseMap(id);
                                return;
                            }
                            if (error != null || !Boolean.TRUE.equals(success)) {
                                returnLocations.remove(id);
                                removePartyMember(id);
                                releaseMap(id);
                                clearReturnLocation(player);
                                getLogger().warning("Async dungeon teleport failed for " + player.getName()
                                        + (error == null ? "" : ": " + error.getMessage()));
                                message(player, ChatColor.RED + "Не удалось перенести тебя в подземелье. Попробуй ещё раз.");
                                return;
                            }
                            playerMaps.put(id, mapIndex);
                            player.getPersistentDataContainer().set(activeKey, PersistentDataType.BYTE, (byte) 1);
                            if (get(player, levelKey, 0) == 0) {
                                set(player, levelKey, 1); set(player, xpKey, 0); set(player, skillPointsKey, 0);
                                set(player, manaKey, 50); set(player, staminaKey, 100);
                                set(player, powerKey, 0); set(player, wisdomKey, 0); set(player, vitalityKey, 0); set(player, focusKey, 0);
                            }
                            ensureEconomy(player);
                            player.sendActionBar(Component.text(MAP_NAMES[mapIndex] + " • " + (joined ? "группа вошла" : "испытание началось"))
                                    .color(net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE));
                            if (resourcePackSha1 != null) sendResourcePack(player);
                            startAdventure(player);
                        }));
            });
        }

        private void removePartyMember(UUID id) {
            UUID leader = partyLeaderByPlayer.getOrDefault(id, id);
            Set<UUID> members = partyMembers.get(leader);
            partyLeaderByPlayer.remove(id);
            if (members == null) return;
            members.remove(id);
            if (members.isEmpty()) {
                partyMembers.remove(leader);
                partyLeaderByPlayer.remove(leader);
                return;
            }
            if (!leader.equals(id)) return;

            UUID nextLeader = members.iterator().next();
            partyMembers.remove(leader);
            partyMembers.put(nextLeader, members);
            for (UUID member : members) partyLeaderByPlayer.put(member, nextLeader);
            Integer mapIndex = playerMaps.get(nextLeader);
            if (mapIndex != null) activeMapOwners.put(mapIndex, nextLeader);
            if (waves.containsKey(leader)) waves.put(nextLeader, waves.get(leader));
            if (remaining.containsKey(leader)) remaining.put(nextLeader, remaining.get(leader));
            if (completionScheduled.remove(leader)) completionScheduled.add(nextLeader);
            boolean encounterNeedsStart = !completionScheduled.contains(nextLeader)
                    && remaining.getOrDefault(leader, 0) <= 0;
            waves.remove(leader);
            remaining.remove(leader);
            for (Entity entity : dungeonWorld == null ? List.<Entity>of() : dungeonWorld.getEntities()) {
                String owner = entity.getPersistentDataContainer().get(mobOwnerKey, PersistentDataType.STRING);
                if (id.toString().equals(owner)) entity.getPersistentDataContainer()
                        .set(mobOwnerKey, PersistentDataType.STRING, nextLeader.toString());
            }
            cancelBossPulse(leader);
            if (encounterNeedsStart) {
                Bukkit.getScheduler().runTask(AstralNexusPlugin.this, () -> {
                    Player promoted = Bukkit.getPlayer(nextLeader);
                    if (promoted != null && promoted.isOnline() && isDungeon(promoted)) spawnWave(promoted);
                });
            }
            for (Entity entity : dungeonWorld == null ? List.<Entity>of() : dungeonWorld.getEntities()) {
                if (entity instanceof LivingEntity living
                        && living.getPersistentDataContainer().getOrDefault(bossKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1) {
                    Player newOwner = Bukkit.getPlayer(nextLeader);
                    if (newOwner != null) startBossPulse(newOwner, living);
                    break;
                }
            }
        }

        void startAdventure(Player player) {
            if (!isDungeon(player)) return;
            UUID id = player.getUniqueId();
            player.getPersistentDataContainer().set(activeKey, PersistentDataType.BYTE, (byte) 1);
            ensureSpecialItems(player);
            ensureEconomy(player);
            org.bukkit.Location savedReturn = readReturnLocation(player);
            if (savedReturn != null) returnLocations.putIfAbsent(id, savedReturn);
            else returnLocations.putIfAbsent(id, Bukkit.getWorlds().get(0).getSpawnLocation());
            int mapIndex = player.getPersistentDataContainer().getOrDefault(mapKey, PersistentDataType.INTEGER, 0);
            mapIndex = Math.max(0, Math.min(MAP_CENTERS.length - 1, mapIndex));
            if (!claimExistingMap(player, mapIndex)) {
                player.sendActionBar(Component.text("Этот маршрут уже занят. Возвращаю тебя к порталу.")
                        .color(net.kyori.adventure.text.format.NamedTextColor.YELLOW));
                leaveDungeon(player, false);
                return;
            }
            UUID leader = partyLeaderByPlayer.getOrDefault(id, id);
            if (!leader.equals(id)) {
                player.sendActionBar(Component.text("Ты проходишь подземелье в группе.")
                        .color(net.kyori.adventure.text.format.NamedTextColor.GREEN));
                return;
            }
            waves.put(id, 0);
            remaining.remove(id);
            completionScheduled.remove(id);
            spawnWave(player);
        }

        private void spawnWave(Player player) {
            UUID id = player.getUniqueId();
            int mapIndex = playerMaps.getOrDefault(id,
                    player.getPersistentDataContainer().getOrDefault(mapKey, PersistentDataType.INTEGER, 0));
            mapIndex = Math.max(0, Math.min(MAP_CENTERS.length - 1, mapIndex));
            final int selectedMapIndex = mapIndex;
            int centerX = MAP_CENTERS[selectedMapIndex];
            int wave = waves.getOrDefault(id, 0);
            Set<UUID> party = partyMembers.getOrDefault(id, Set.of(id));

            if (wave >= 5) {
                for (UUID memberId : party) {
                    Player member = Bukkit.getPlayer(memberId);
                    if (member == null || !member.isOnline() || !isDungeon(member)) continue;
                    member.sendTitle("§4§lБОСС", "§5Страж Разлома выходит из глубины", 10, 70, 20);
                    member.playSound(member.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.72f);
                }
                Bukkit.getScheduler().runTaskLater(AstralNexusPlugin.this, () -> {
                    Player leader = Bukkit.getPlayer(id);
                    if (leader == null || !leader.isOnline() || !isDungeon(leader) || waves.getOrDefault(id, 0) < 5) return;
                    double arenaZ = BOSS_Z;
                    double bossX = centerX + cavePathOffset(selectedMapIndex, BOSS_Z) + 0.5;
                    spawnDungeonMob(leader, org.bukkit.entity.EntityType.WITHER_SKELETON, bossX,
                            caveFloorY(selectedMapIndex, BOSS_Z) + 1, arenaZ + 0.5, true);
                    for (UUID memberId : partyMembers.getOrDefault(id, Set.of(id))) {
                        Player member = Bukkit.getPlayer(memberId);
                        if (member != null && member.isOnline())
                            member.sendActionBar(Component.text("Страж Разлома пробудился")
                                    .color(net.kyori.adventure.text.format.NamedTextColor.DARK_PURPLE));
                    }
                    remaining.put(id, 1);
                }, 40L);
                return;
            }

            String[] waveSubtitles = {
                    "§7В глубине шевелятся первые твари",
                    "§7Из боковых тоннелей идёт подкрепление",
                    "§cТвари перекрывают узкий проход",
                    "§4Из темноты выходит элитная стража",
                    "§4Последняя оборона перед Стражем"
            };
            for (UUID memberId : party) {
                Player member = Bukkit.getPlayer(memberId);
                if (member == null || !member.isOnline() || !isDungeon(member)) continue;
                member.sendTitle("§6§lВОЛНА " + (wave + 1), waveSubtitles[wave], 10, 58, 16);
                member.playSound(member.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.72f, 1.25f);
            }
            final int currentWave = wave;
            Bukkit.getScheduler().runTaskLater(AstralNexusPlugin.this, () -> {
                Player leader = Bukkit.getPlayer(id);
                if (leader == null || !leader.isOnline() || !isDungeon(leader)
                        || waves.getOrDefault(id, -1) != currentWave) return;
                double arenaZ = ENCOUNTER_Z[currentWave];
                double waveX = centerX + cavePathOffset(selectedMapIndex, (int) arenaZ);
                org.bukkit.entity.EntityType[] types = switch (currentWave) {
                    case 0 -> new org.bukkit.entity.EntityType[]{
                            org.bukkit.entity.EntityType.ZOMBIE, org.bukkit.entity.EntityType.SKELETON,
                            org.bukkit.entity.EntityType.CAVE_SPIDER, org.bukkit.entity.EntityType.HUSK};
                    case 1 -> new org.bukkit.entity.EntityType[]{
                            org.bukkit.entity.EntityType.HUSK, org.bukkit.entity.EntityType.STRAY,
                            org.bukkit.entity.EntityType.VINDICATOR, org.bukkit.entity.EntityType.CAVE_SPIDER,
                            org.bukkit.entity.EntityType.WITCH};
                    case 2 -> new org.bukkit.entity.EntityType[]{
                            org.bukkit.entity.EntityType.PILLAGER, org.bukkit.entity.EntityType.VINDICATOR,
                            org.bukkit.entity.EntityType.EVOKER, org.bukkit.entity.EntityType.WITCH,
                            org.bukkit.entity.EntityType.STRAY, org.bukkit.entity.EntityType.CAVE_SPIDER};
                    case 3 -> new org.bukkit.entity.EntityType[]{
                            org.bukkit.entity.EntityType.PIGLIN_BRUTE, org.bukkit.entity.EntityType.BLAZE,
                            org.bukkit.entity.EntityType.WITHER_SKELETON, org.bukkit.entity.EntityType.VINDICATOR,
                            org.bukkit.entity.EntityType.WITCH, org.bukkit.entity.EntityType.STRAY};
                    default -> new org.bukkit.entity.EntityType[]{
                            org.bukkit.entity.EntityType.EVOKER, org.bukkit.entity.EntityType.PIGLIN_BRUTE,
                            org.bukkit.entity.EntityType.BLAZE, org.bukkit.entity.EntityType.WITHER_SKELETON,
                            org.bukkit.entity.EntityType.VINDICATOR, org.bukkit.entity.EntityType.CAVE_SPIDER,
                            org.bukkit.entity.EntityType.WITCH};
                };
                remaining.put(id, types.length);
                for (int i = 0; i < types.length; i++) {
                    double x = waveX + (i - (types.length - 1) / 2.0) * 2.5 + 0.5;
                    spawnDungeonMob(leader, types[i], x, caveFloorY(selectedMapIndex, (int) arenaZ) + 1,
                            arenaZ + 0.5, false);
                }
                for (UUID memberId : partyMembers.getOrDefault(id, Set.of(id))) {
                    Player member = Bukkit.getPlayer(memberId);
                    if (member != null && member.isOnline())
                        member.sendActionBar(Component.text(MAP_NAMES[selectedMapIndex] + " • волна " + (currentWave + 1) + "/5")
                                .color(net.kyori.adventure.text.format.NamedTextColor.GOLD));
                }
            }, 40L);
        }

                private void spawnDungeonMob(Player owner, org.bukkit.entity.EntityType type,
                                     double x, double y, double z, boolean boss) {
            org.bukkit.entity.Entity entity = dungeonWorld.spawnEntity(new org.bukkit.Location(dungeonWorld, x, y, z), type);
            if (entity instanceof LivingEntity living) {
                living.getPersistentDataContainer().set(mobOwnerKey, PersistentDataType.STRING, owner.getUniqueId().toString());
                living.getPersistentDataContainer().set(dungeonMobKey, PersistentDataType.BYTE, (byte) 1);
                living.setCustomName(boss ? "§5✦ Страж Разлома" : "§cПорождение Астрала");
                living.setCustomNameVisible(true);
                if (boss) {
                    if (living.getAttribute(Attribute.MAX_HEALTH) != null) {
                        living.getAttribute(Attribute.MAX_HEALTH).setBaseValue(280.0);
                        living.setHealth(280.0);
                    }
                    if (living.getAttribute(Attribute.ATTACK_DAMAGE) != null)
                        living.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(18.0);
                    if (living.getAttribute(Attribute.ARMOR) != null)
                        living.getAttribute(Attribute.ARMOR).setBaseValue(14.0);
                    if (living.getAttribute(Attribute.MOVEMENT_SPEED) != null)
                        living.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.34);
                    if (living.getAttribute(Attribute.KNOCKBACK_RESISTANCE) != null)
                        living.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(0.65);
                    living.getPersistentDataContainer().set(bossKey, PersistentDataType.BYTE, (byte) 1);
                    living.getPersistentDataContainer().set(bossPhaseKey, PersistentDataType.INTEGER, 1);
                    living.setGlowing(true);
                    if (living.getEquipment() != null) {
                        var gear = living.getEquipment();
                        gear.setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
                        gear.setHelmet(new ItemStack(Material.NETHERITE_HELMET));
                        gear.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
                        gear.setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
                        gear.setBoots(new ItemStack(Material.NETHERITE_BOOTS));
                        gear.setItemInMainHandDropChance(0.0f);
                        gear.setHelmetDropChance(0.0f);
                        gear.setChestplateDropChance(0.0f);
                        gear.setLeggingsDropChance(0.0f);
                        gear.setBootsDropChance(0.0f);
                    }
                    startBossPulse(owner, living);
                } else {
                    int wave = waves.getOrDefault(owner.getUniqueId(), 0);
                    double health = 26.0 + wave * 11.0;
                    if (living.getAttribute(Attribute.MAX_HEALTH) != null) {
                        living.getAttribute(Attribute.MAX_HEALTH).setBaseValue(health);
                        living.setHealth(health);
                    }
                    if (living.getAttribute(Attribute.ATTACK_DAMAGE) != null)
                        living.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(5.0 + wave * 2.5);
                    if (living.getAttribute(Attribute.MOVEMENT_SPEED) != null)
                        living.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.25 + wave * 0.025);
                }
            }
        }

        private void startBossPulse(Player owner, LivingEntity boss) {
            UUID ownerId = owner.getUniqueId();
            final long[] nextPulseAt = {System.currentTimeMillis() + 5_000L};
            org.bukkit.scheduler.BukkitTask pulseTask = new org.bukkit.scheduler.BukkitRunnable() {
                @Override
                public void run() {
                    if (!owner.isOnline() || !isDungeon(owner) || !boss.isValid() || boss.isDead()) {
                        cancel();
                        bossPulseTasks.remove(ownerId);
                        return;
                    }
                    int phase = boss.getPersistentDataContainer().getOrDefault(bossPhaseKey, PersistentDataType.INTEGER, 1);
                    long now = System.currentTimeMillis();
                    long interval = phase >= 3 ? 5_500L : phase == 2 ? 7_500L : 10_000L;
                    if (now < nextPulseAt[0] || owner.getLocation().distanceSquared(boss.getLocation()) > 144.0) return;
                    nextPulseAt[0] = now + interval;
                    boss.getWorld().spawnParticle(Particle.SONIC_BOOM, boss.getLocation().add(0, 1, 0), 1);
                    boss.getWorld().spawnParticle(Particle.SOUL, boss.getLocation(), 26, 1.8, 0.4, 1.8, 0.03);
                    owner.playSound(owner.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.9f, phase >= 3 ? 0.72f : 0.88f);
                    owner.damage(phase >= 3 ? 9.0 : phase == 2 ? 7.0 : 5.0, boss);
                    owner.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, phase >= 3 ? 1 : 0));
                    if (phase >= 3) owner.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 40, 0));
                }
            }.runTaskTimer(AstralNexusPlugin.this, 40L, 20L);
            bossPulseTasks.put(ownerId, pulseTask.getTaskId());
        }

        private void cancelBossPulse(UUID ownerId) {
            Integer taskId = bossPulseTasks.remove(ownerId);
            if (taskId != null) Bukkit.getScheduler().cancelTask(taskId);
        }

        @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
        public void onDungeonBossPhaseDamage(org.bukkit.event.entity.EntityDamageEvent event) {
            if (!(event.getEntity() instanceof LivingEntity boss)
                    || boss.getPersistentDataContainer().getOrDefault(bossKey, PersistentDataType.BYTE, (byte) 0) != (byte) 1)
                return;
            String ownerText = boss.getPersistentDataContainer().get(mobOwnerKey, PersistentDataType.STRING);
            if (ownerText == null) return;
            UUID ownerId;
            try { ownerId = UUID.fromString(ownerText); } catch (IllegalArgumentException ignored) { return; }
            Player owner = Bukkit.getPlayer(ownerId);
            if (owner == null || !owner.isOnline() || !isDungeon(owner)) return;
            double maxHealth = boss.getAttribute(Attribute.MAX_HEALTH) == null ? 280.0
                    : boss.getAttribute(Attribute.MAX_HEALTH).getValue();
            double healthAfterHit = Math.max(0.0, boss.getHealth() - event.getFinalDamage());
            int phase = boss.getPersistentDataContainer().getOrDefault(bossPhaseKey, PersistentDataType.INTEGER, 1);
            int nextPhase = healthAfterHit <= maxHealth * 0.28 ? 3 : healthAfterHit <= maxHealth * 0.65 ? 2 : 1;
            if (nextPhase <= phase) return;
            boss.getPersistentDataContainer().set(bossPhaseKey, PersistentDataType.INTEGER, nextPhase);
            Bukkit.getScheduler().runTask(AstralNexusPlugin.this, () -> {
                if (!boss.isValid() || boss.isDead() || !owner.isOnline()) return;
                if (boss.getAttribute(Attribute.ATTACK_DAMAGE) != null)
                    boss.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(nextPhase == 3 ? 25.0 : 21.0);
                if (boss.getAttribute(Attribute.MOVEMENT_SPEED) != null)
                    boss.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(nextPhase == 3 ? 0.43 : 0.39);
                if (nextPhase == 2) {
                    boss.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 240, 0));
                    owner.sendTitle("§c§lФАЗА II", "§7Страж ускоряется и наносит больше урона", 5, 50, 15);
                } else {
                    boss.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 300, 1));
                    boss.setGlowing(true);
                    owner.sendTitle("§4§lФАЗА III", "§5Разлом поглощает Стража — уклоняйся от импульса", 5, 60, 15);
                }
                boss.getWorld().spawnParticle(Particle.EXPLOSION, boss.getLocation().add(0, 1, 0), 2);
                boss.getWorld().spawnParticle(Particle.SOUL, boss.getLocation(), 36, 1.5, 0.8, 1.5, 0.05);
                owner.playSound(owner.getLocation(), Sound.ENTITY_WARDEN_ROAR, 1.0f, nextPhase == 3 ? 0.65f : 0.9f);
            });
        }

        @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
        public void onDungeonBossStrike(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
            if (!(event.getEntity() instanceof Player target)
                    || !(event.getDamager() instanceof LivingEntity boss)
                    || boss.getPersistentDataContainer().getOrDefault(bossKey, PersistentDataType.BYTE, (byte) 0) != (byte) 1)
                return;
            String owner = boss.getPersistentDataContainer().get(mobOwnerKey, PersistentDataType.STRING);
            if (!target.getUniqueId().toString().equals(owner) || !isDungeon(target)) return;
            int phase = boss.getPersistentDataContainer().getOrDefault(bossPhaseKey, PersistentDataType.INTEGER, 1);
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, phase >= 3 ? 1 : 0));
            if (phase >= 3) target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 40, 0));
        }

        @EventHandler
        public void onDungeonMobDeath(EntityDeathEvent event) {
            LivingEntity mob = event.getEntity();
            String ownerText = mob.getPersistentDataContainer().get(mobOwnerKey, PersistentDataType.STRING);
            if (ownerText == null) return;
            event.getDrops().clear();
            event.setDroppedExp(0);
            UUID id;
            try { id = UUID.fromString(ownerText); } catch (IllegalArgumentException ignored) { return; }
            Player adventurer = Bukkit.getPlayer(id);
            if (adventurer == null || !adventurer.isOnline() || !isDungeon(adventurer)) return;
            // Progress is tied to the assigned adventurer, not only getKiller(); environmental
            // damage or another player's final hit must not soft-lock the dungeon objective.
            if (mob.getPersistentDataContainer().getOrDefault(bossKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1)
                cancelBossPulse(id);
            int left = Math.max(0, remaining.getOrDefault(id, 1) - 1);
            remaining.put(id, left);
            if (mob.getKiller() != null && mob.getKiller().isOnline())
                addXp(mob.getKiller(), mob.getPersistentDataContainer().has(dungeonMobKey, PersistentDataType.BYTE) ? 35 : 10);
            if (left > 0) return;

            int wave = waves.getOrDefault(id, 0);
            Set<UUID> party = new java.util.LinkedHashSet<>(partyMembers.getOrDefault(id, Set.of(id)));
            if (wave >= 5) {
                if (!completionScheduled.add(id)) return;
                for (UUID memberId : party) {
                    Player member = Bukkit.getPlayer(memberId);
                    if (member == null || !member.isOnline() || !isDungeon(member)) continue;
                    member.getInventory().addItem(dungeonReward(1423, "§5Сердце Стража", "§7Трофей за победу над Стражем Разлома", "§dРедкая астральная реликвия"));
                    member.getInventory().addItem(dungeonReward(1421, "§bКлинок Разлома", "§7Оружие, выкованное в подземелье", "§8Трофей финальной комнаты"));
                    member.sendActionBar(Component.text("✦ Страж Разлома повержен")
                        .color(net.kyori.adventure.text.format.NamedTextColor.GOLD));
                    member.playSound(member.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                }
                Bukkit.getScheduler().runTaskLater(AstralNexusPlugin.this, () -> {
                    for (UUID memberId : party) {
                        Player member = Bukkit.getPlayer(memberId);
                        if (member != null && member.isOnline() && isDungeon(member)) leaveDungeon(member, true);
                    }
                }, 60L);
            } else {
                for (UUID memberId : party) {
                    Player member = Bukkit.getPlayer(memberId);
                    if (member != null && member.isOnline())
                        member.getInventory().addItem(dungeonReward(1422, "§bФлакон астральной эссенции", "§7Награда за волну " + (wave + 1), "§8Трофей подземелья"));
                }
                waves.put(id, wave + 1);
                Bukkit.getScheduler().runTaskLater(AstralNexusPlugin.this, () -> {
                    Player leader = Bukkit.getPlayer(id);
                    if (leader != null && leader.isOnline() && isDungeon(leader)) spawnWave(leader);
                }, 60L);
            }
        }

        @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
        public void onDungeonPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
            Player player = event.getEntity();
            if (!isDungeon(player)) return;

            // Dungeon death is a fail-state, not a vanilla item-loss event.
            // Preserve inventory/XP and route the respawn directly to the saved entry point.
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.getDrops().clear();
            event.setDroppedExp(0);

            UUID id = player.getUniqueId();
            removePartyMember(id);
            waves.remove(id);
            remaining.remove(id);
            completionScheduled.remove(id);
            cancelBossPulse(id);
            transitioning.remove(id);
            player.getPersistentDataContainer().set(activeKey, PersistentDataType.BYTE, (byte) 0);
            removeSpecialItems(player);

            // Remove only this adventurer's tagged encounter mobs; other players' runs stay intact.
            for (Entity entity : dungeonWorld == null ? List.<Entity>of() : dungeonWorld.getEntities()) {
                String owner = entity.getPersistentDataContainer().get(mobOwnerKey, PersistentDataType.STRING);
                if (player.getUniqueId().toString().equals(owner)) entity.remove();
            }
        }

        @EventHandler
        public void onDungeonPlayerRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
            Player player = event.getPlayer();
            if (!isDungeon(player)) return;

            org.bukkit.Location destination = readReturnLocation(player);
            if (destination == null || destination.getWorld() == null) {
                org.bukkit.World fallback = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
                if (fallback == null) {
                    getLogger().severe("Cannot return " + player.getName() + " after dungeon death: no fallback world exists.");
                    return;
                }
                destination = fallback.getSpawnLocation();
            }

            event.setRespawnLocation(destination);
            Bukkit.getScheduler().runTask(AstralNexusPlugin.this, () -> {
                if (!player.isOnline()) return;
                player.getPersistentDataContainer().set(activeKey, PersistentDataType.BYTE, (byte) 0);
                removeSpecialItems(player);
                returnLocations.remove(player.getUniqueId());
                releaseMap(player.getUniqueId());
                clearReturnLocation(player);
                player.sendTitle("§c§lПОТРАЧЕНО", "§7Подземелье оказалось сильнее тебя", 5, 55, 15);
                player.playSound(player.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.65f, 0.8f);
                player.sendActionBar(Component.text("Ты пал в подземелье. Инвентарь сохранён.")
                        .color(net.kyori.adventure.text.format.NamedTextColor.RED));
            });
        }

        private void leaveDungeon(Player player, boolean success) {
            UUID id = player.getUniqueId();
            if (!transitioning.add(id)) return;
            org.bukkit.Location back = returnLocations.get(id);
            if (back == null) back = readReturnLocation(player);
            if (back == null || back.getWorld() == null) back = Bukkit.getWorlds().get(0).getSpawnLocation();
            waves.remove(id);
            remaining.remove(id);
            completionScheduled.remove(id);
            cancelBossPulse(id);
            player.closeInventory();
            removeSpecialItems(player);
            player.getPersistentDataContainer().set(activeKey, PersistentDataType.BYTE, (byte) 0);
            final org.bukkit.Location destination = back.clone();
            player.teleportAsync(destination).whenComplete((moved, error) ->
                    Bukkit.getScheduler().runTask(AstralNexusPlugin.this, () -> {
                        transitioning.remove(id);
                        if (!player.isOnline()) return;
                        if (error != null || !Boolean.TRUE.equals(moved)) {
                            getLogger().warning("Async return teleport failed for " + player.getName()
                                    + (error == null ? "" : ": " + error.getMessage()));
                            message(player, ChatColor.RED + "Не удалось вернуться. Плагин повторит попытку автоматически.");
                            Bukkit.getScheduler().runTaskLater(AstralNexusPlugin.this, () -> {
                                if (player.isOnline() && isDungeon(player)) leaveDungeon(player, success);
                            }, 40L);
                            return;
                        }
                        returnLocations.remove(id);
                        removePartyMember(id);
                        releaseMap(id);
                        clearReturnLocation(player);
                        player.sendActionBar(Component.text(success
                                ? "Подземелье пройдено — ты вернулся к порталу."
                                : "Ты вернулся к порталу.")
                                .color(success ? net.kyori.adventure.text.format.NamedTextColor.GREEN
                                        : net.kyori.adventure.text.format.NamedTextColor.YELLOW));
                    }));
        }

        private boolean isDungeonReward(ItemStack item) {
            return item != null && item.hasItemMeta()
                    && item.getItemMeta().getPersistentDataContainer().has(dungeonRewardKey, PersistentDataType.BYTE);
        }

        private void removeSpecialItems(Player player) {
            for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
                ItemStack item = player.getInventory().getItem(slot);
                if (isMarkedItem(item, menuItemKey) || isMarkedItem(item, quickSkillsItemKey) || isDungeonReward(item)) {
                    player.getInventory().setItem(slot, null);
                }
            }
            ItemStack offhand = player.getInventory().getItemInOffHand();
            if (isMarkedItem(offhand, menuItemKey) || isMarkedItem(offhand, quickSkillsItemKey) || isDungeonReward(offhand)) {
                player.getInventory().setItemInOffHand(null);
            }
        }

        @EventHandler
        public void onDungeonQuit(org.bukkit.event.player.PlayerQuitEvent event) {
            Player player = event.getPlayer();
            if (!isDungeon(player)) return;
            UUID id = player.getUniqueId();
            // Never teleport from PlayerQuitEvent: the connection is already being torn down.
            removeSpecialItems(player);
            player.getPersistentDataContainer().set(activeKey, PersistentDataType.BYTE, (byte) 0);
            returnLocations.remove(id);
            waves.remove(id);
            remaining.remove(id);
            cancelBossPulse(id);
            releaseMap(id);
            completionScheduled.remove(id);
            transitioning.remove(id);
        }

        @EventHandler
        public void onDungeonRejoin(PlayerJoinEvent event) {
            Player player = event.getPlayer();
            if (!isDungeon(player)) return;
            Bukkit.getScheduler().runTaskLater(AstralNexusPlugin.this, () -> {
                if (player.isOnline() && isDungeon(player)) {
                    player.sendActionBar(Component.text("Незавершённая попытка закрыта.")
                            .color(net.kyori.adventure.text.format.NamedTextColor.YELLOW));
                    leaveDungeon(player, false);
                }
            }, 2L);
        }

        void shutdown() {
            // Do not teleport during onDisable/reload; Bukkit may already be shutting down.
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (isDungeon(player)) {
                    removeSpecialItems(player);
                    player.getPersistentDataContainer().set(activeKey, PersistentDataType.BYTE, (byte) 0);
                }
            }
            for (UUID id : new java.util.ArrayList<>(bossPulseTasks.keySet())) cancelBossPulse(id);
            returnLocations.clear();
            waves.clear();
            remaining.clear();
            transitioning.clear();
            dungeonReadyCallbacks.clear();
            buildingMaps.clear();
            playerMaps.clear();
            activeMapOwners.clear();
            partyLeaderByPlayer.clear();
            partyMembers.clear();
            pendingPortalLeaders.clear();
            readyMaps.clear();
            completionScheduled.clear();
        }
    }

}