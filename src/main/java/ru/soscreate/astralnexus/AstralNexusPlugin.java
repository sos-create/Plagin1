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
                ensureSpecialItems(online);
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
            message(p, "Навыки: power (урон), wisdom (максимум и регенерация маны), vitality (защита Стража), focus (перезарядка)."); return;
        }
        String skill = args[1].toLowerCase();
        NamespacedKey target = switch (skill) {
            case "power" -> powerKey;
            case "wisdom" -> wisdomKey;
            case "vitality" -> vitalityKey;
            case "focus" -> focusKey;
            default -> null;
        };
        if (target == null) { message(p, ChatColor.RED + "Неизвестный навык. Доступны: power, wisdom, vitality, focus."); return; }
        int points = get(p, skillPointsKey, 0);
        if (points < 1) { message(p, "Нет очков талантов. Получай астральный опыт за приключения."); return; }
        if (get(p, target, 0) >= 10) { message(p, "Этот навык уже достиг максимального ранга 10."); return; }
        set(p, skillPointsKey, points - 1);
        set(p, target, get(p, target, 0) + 1);
        if (skill.equals("wisdom")) set(p, manaKey, maxMana(p));
        message(p, ChatColor.GREEN + "Навык " + skill + " повышен до " + get(p, target, 0) + ". Осталось очков: " + (points - 1) + ".");
    }

    private void cast(Player p) {
        if (!active(p) || getString(p, classKey, "").isEmpty()) {
            message(p, "Сначала начни RPG и выбери архетип."); return;
        }
        long now = System.currentTimeMillis();
        long readyAt = cooldowns.getOrDefault(p.getUniqueId(), 0L);
        if (now < readyAt) {
            message(p, "Способность перезаряжается: " + ((readyAt - now + 999) / 1000) + " сек."); return;
        }
        int cost = Math.max(5, 15 - get(p, wisdomKey, 0));
        if (get(p, manaKey, 0) < cost) { message(p, "Недостаточно маны. Она восстанавливается автоматически."); return; }
        set(p, manaKey, get(p, manaKey, 0) - cost);
        int power = get(p, powerKey, 0);
        String type = getString(p, classKey, "");
        switch (type) {
            case "mage" -> {
                int hits = damageNearby(p, 7.0, 5.0 + power * 1.5);
                p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 35, 1.5, 1, 1.5, 0.03);
                message(p, "Астральный импульс! Поражено целей: " + hits);
            }
            case "warrior" -> {
                int hits = damageNearby(p, 4.5, 7.0 + power * 2.0);
                p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, p.getLocation().add(0, 1, 0), 12, 1.2, 0.5, 1.2, 0);
                message(p, "Вихрь клинка! Поражено целей: " + hits);
            }
            case "ranger" -> {
                Arrow arrow = p.launchProjectile(Arrow.class);
                arrow.setDamage(5.0 + power * 1.5);
                arrow.setCritical(true);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 1.2f);
                message(p, "Звёздная стрела выпущена!");
            }
            case "guardian" -> {
                double max = p.getAttribute(Attribute.MAX_HEALTH) == null ? 20.0 : p.getAttribute(Attribute.MAX_HEALTH).getValue();
                p.setHealth(Math.min(max, p.getHealth() + 5.0 + power + get(p, vitalityKey, 0) * 1.5));
                p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100 + get(p, vitalityKey, 0) * 20, 0));
                p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 20, 1, 1, 1, 0.1);
                message(p, "Астральная защита восстановила здоровье.");
            }
            default -> { message(p, "Сначала выбери архетип."); return; }
        }
        long cooldownMillis = Math.max(3000L, 8000L - get(p, focusKey, 0) * 500L);
        cooldowns.put(p.getUniqueId(), now + cooldownMillis);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.4f);
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

    private List<ShopProduct> shopProducts() {
        return List.of(
                new ShopProduct(Material.COOKED_BEEF, "Провиант путешественника", "16 приготовленных стейков", 55, 16),
                new ShopProduct(Material.ARROW, "Колчан стрел", "16 стрел для следопыта", 48, 16),
                new ShopProduct(Material.ENDER_PEARL, "Жемчуг странника", "4 жемчужины Эндера", 110, 4),
                new ShopProduct(Material.GOLDEN_CARROT, "Золотая морковь", "8 золотых морковок", 95, 8),
                new ShopProduct(Material.GOLDEN_APPLE, "Золотое яблоко", "Пища с защитным эффектом", 180, 1),
                new ShopProduct(Material.EXPERIENCE_BOTTLE, "Флаконы опыта", "8 бутылочек опыта", 150, 8),
                new ShopProduct(Material.IRON_SWORD, "Клинок авантюриста", "Прочный железный меч", 260, 1),
                new ShopProduct(Material.SHIELD, "Щит странника", "Щит для второй руки", 230, 1),
                new ShopProduct(Material.DIAMOND, "Астральный алмаз", "Редкий материал для обмена", 420, 1),
                new ShopProduct(Material.COOKED_COD, "Запас провизии", "24 порции приготовленной рыбы", 62, 24),
                new ShopProduct(Material.FIREWORK_ROCKET, "Ракеты следопыта", "12 ракет для полёта", 125, 12),
                new ShopProduct(Material.TOTEM_OF_UNDYING, "Тотем хранителя", "Редкая страховка в опасном походе", 1800, 1)
        );
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
        if (ability.id().equals("surge")) {
            set(player, manaKey, Math.min(maxMana(player), get(player, manaKey, 0) + 25));
        } else {
            if (get(player, manaKey, 0) < ability.manaCost()) {
                message(player, "Недостаточно маны. Требуется " + ability.manaCost() + ".");
                return;
            }
            set(player, manaKey, get(player, manaKey, 0) - ability.manaCost());
        }
        int power = get(player, powerKey, 0);
        switch (ability.id()) {
            case "nova", "spin", "quake" -> {
                int hits = damageNearby(player, ability.id().equals("nova") ? 7.0 : 5.0, 5.0 + power * 1.4);
                player.getWorld().spawnParticle(ability.id().equals("spin") ? Particle.SWEEP_ATTACK : Particle.END_ROD,
                        player.getLocation().add(0, 1, 0), 35, 1.4, 0.8, 1.4, 0.04);
                message(player, ability.title() + "! Поражено целей: " + hits);
            }
            case "ward", "guard", "aegis" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 160, 0));
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 220, 1));
                player.getWorld().spawnParticle(Particle.WAX_ON, player.getLocation().add(0, 1, 0), 24, 0.8, 0.8, 0.8, 0.02);
                message(player, ability.title() + ": защита активирована.");
            }
            case "blink", "charge", "dash" -> {
                if (ability.id().equals("blink")) {
                    org.bukkit.Location target = player.getLocation().clone()
                            .add(player.getLocation().getDirection().normalize().multiply(4));
                    if (target.getBlock().isPassable() && target.clone().add(0, 1, 0).getBlock().isPassable()) {
                        player.teleport(target);
                    } else {
                        player.setVelocity(player.getLocation().getDirection().normalize().multiply(1.2).setY(0.25));
                    }
                } else {
                    player.setVelocity(player.getLocation().getDirection().normalize().multiply(1.5).setY(0.35));
                    if (ability.id().equals("dash")) player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1));
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
                                100, 1));
                    }
                }
                player.getWorld().spawnParticle(Particle.SONIC_BOOM, player.getLocation().add(0, 1, 0), 1);
                message(player, ability.title() + "!");
            }
            case "volley" -> {
                Arrow arrow = player.launchProjectile(Arrow.class);
                arrow.setDamage(8.0 + power * 1.5);
                arrow.setCritical(true);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 1.25f);
                message(player, "Звёздный залп выпущен!");
            }
            case "mark" -> {
                int marked = 0;
                for (Entity entity : player.getNearbyEntities(12, 8, 12)) {
                    if (entity instanceof Monster monster && !monster.isDead()) {
                        monster.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 160, 0));
                        marked++;
                    }
                }
                message(player, "Целей отмечено: " + marked);
            }
            case "heal" -> {
                double max = player.getAttribute(Attribute.MAX_HEALTH) == null ? 20.0
                        : player.getAttribute(Attribute.MAX_HEALTH).getValue();
                player.setHealth(Math.min(max, player.getHealth() + 8.0 + get(player, vitalityKey, 0)));
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 160, 0));
                message(player, "Астральное исцеление восстановило здоровье.");
            }
            case "rally" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 180, 0));
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 0));
                message(player, "Знамя стойкости поднято.");
            }
            case "flow" -> {
                int restored = Math.min(40, maxMana(player) - get(player, manaKey, 0));
                set(player, manaKey, get(player, manaKey, 0) + restored);
                player.getWorld().spawnParticle(Particle.SPLASH, player.getLocation().add(0, 1, 0), 28, 0.7, 0.8, 0.7, 0.15);
                message(player, "Поток энергии восстановил " + restored + " маны.");
            }
            case "seal" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 240, 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 240, 1));
                player.getWorld().spawnParticle(Particle.WAX_ON, player.getLocation().add(0, 1, 0), 42, 1.0, 1.0, 1.0, 0.03);
                message(player, "Защитная печать активирована.");
            }
            case "resonance" -> {
                int hits = damageNearby(player, 8.0, 9.0 + power * 1.8);
                player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0), 50, 1.8, 1.0, 1.8, 0.08);
                message(player, "Астральный резонанс поразил целей: " + hits);
            }
            case "tempo" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 240, 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 180, 1));
                player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation(), 44, 0.8, 1.0, 0.8, 0.2);
                message(player, "Темп времени ускорил твои движения.");
            }
            default -> { message(player, "Это умение пока недоступно."); return; }
        }
        cds.put(ability.id(), now + ability.cooldownSeconds() * 1000L);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.25f);
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
        dungeonManager.removeSpecialItems(p);
        p.getPersistentDataContainer().set(activeKey, PersistentDataType.BYTE, (byte) 0);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline() || !p.hasPermission("astralnexus.use")) return;
            message(p, ChatColor.LIGHT_PURPLE + "Astral Nexus находится отдельно от обычного мира. Построй портал из железных блоков с белыми стеклянными панелями внутри.");
            if (dungeonManager.isDungeon(p)) dungeonManager.startAdventure(p);
        }, 40L);
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        texturedPlayers.remove(event.getPlayer().getUniqueId());
        cooldowns.remove(event.getPlayer().getUniqueId());
        abilityCooldowns.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onResourcePackStatus(org.bukkit.event.player.PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> {
                texturedPlayers.add(player.getUniqueId());
                message(player, ChatColor.GREEN + "Текстуры Astral Nexus загружены. Открой меню ещё раз.");
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder holder) {
                    openMenu(player, holder.page);
                }
            }
            case DECLINED, FAILED_DOWNLOAD, FAILED_RELOAD, DISCARDED -> {
                texturedPlayers.remove(player.getUniqueId());
                message(player, ChatColor.YELLOW + "Кастомные текстуры не активны. Используются стандартные иконки.");
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder holder) {
                    openMenu(player, holder.page);
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
        return switch (type) {
            case "mage" -> switch (skill) {
                case "power" -> "Мощь астрального импульса";
                case "wisdom" -> "Глубина маны";
                case "vitality" -> "Защитный контур";
                default -> "Быстрое плетение";
            };
            case "warrior" -> switch (skill) {
                case "power" -> "Сокрушительный вихрь";
                case "wisdom" -> "Боевой дух";
                case "vitality" -> "Стальная закалка";
                default -> "Ритм боя";
            };
            case "ranger" -> switch (skill) {
                case "power" -> "Звёздное оперение";
                case "wisdom" -> "Запас стрелка";
                case "vitality" -> "Ловкость следопыта";
                default -> "Прицельная концентрация";
            };
            case "guardian" -> switch (skill) {
                case "power" -> "Ответный удар";
                case "wisdom" -> "Источник хранителя";
                case "vitality" -> "Несокрушимый щит";
                default -> "Непоколебимость";
            };
            default -> skill;
        };
    }

    private String skillDescription(String type, String skill) {
        return switch (skill) {
            case "power" -> switch (type) {
                case "mage" -> "Увеличивает урон астральной магии";
                case "warrior" -> "Усиливает урон вихря клинка";
                case "ranger" -> "Увеличивает урон звёздной стрелы";
                default -> "Усиливает восстановление при защите";
            };
            case "wisdom" -> "Увеличивает запас и скорость восстановления маны";
            case "vitality" -> type.equals("guardian")
                    ? "Усиливает лечение и поглощение урона"
                    : "Снижает получаемый урон персонажем";
            default -> "Сокращает перезарядку классовой способности";
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
            case "nova" -> 1331;
            case "spin", "quake" -> 1332;
            case "ward", "guard", "aegis", "heal" -> 1334;
            case "blink", "charge", "dash" -> 1338;
            case "surge", "rally" -> 1335;
            case "roar", "mark" -> 1337;
            case "volley" -> 1333;
            case "snare" -> 1336;
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
        boolean found = false;
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack existing = contents[slot];
            if (existing == null || existing.getType().isAir() || !isMarkedItem(existing, marker)) continue;
            ItemStack replacement = specialItem(iconModel, marker, title, lore);
            replacement.setAmount(Math.max(1, existing.getAmount()));
            player.getInventory().setItem(slot, replacement);
            found = true;
        }
        if (!found) giveMarkedItem(player, specialItem(iconModel, marker, title, lore));
        return true;
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
        upgradeOrCreateMarkedItem(player, quickSkillsItemKey, 1422,
                "§d✧ Кристалл талантов",
                "§7ПКМ — открыть панель быстрого вызова",
                "§8Нажимай на иконки умений, чтобы применить их");
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

    private void renderAbilitySlots(Player player, Inventory inventory) {
        int[] slots = {10, 12, 14, 16, 28, 30, 32, 34};
        List<AbilityDefinition> base = abilitiesFor(getString(player, classKey, ""));
        List<AbilityDefinition> paths = pathAbilities();

        for (int i = 0; i < 4; i++) {
            if (i >= base.size()) {
                inventory.setItem(slots[i], customIcon(1315, "§8Архетип не выбран",
                        "§7Сначала выбери класс персонажа"));
                continue;
            }
            AbilityDefinition ability = base.get(i);
            long readyAt = abilityCooldowns.getOrDefault(player.getUniqueId(), Map.of())
                    .getOrDefault(ability.id(), 0L);
            int remaining = (int) Math.max(0, (readyAt - System.currentTimeMillis() + 999) / 1000);
            inventory.setItem(slots[i], customIcon(abilityIconId(ability.id()),
                    "§d✦ " + ability.title(),
                    "§7" + ability.description(),
                    "§7Стоимость: §b" + ability.manaCost() + " маны",
                    "§7Перезарядка: §f" + ability.cooldownSeconds() + " сек.",
                    remaining > 0 ? "§cОткат: " + remaining + " сек." : "§aГотово к применению",
                    "§eНажми, чтобы применить"));
        }

        inventory.setItem(40, customIcon(1341, "§bАстральная мана",
                "§f" + get(player, manaKey, 0) + " §8/ §f" + maxMana(player),
                "§7Текущий запас энергии"));
        for (int i = 0; i < paths.size(); i++) {
            AbilityDefinition ability = paths.get(i);
            if (pathUnlocked(player, ability.id())) {
                long readyAt = abilityCooldowns.getOrDefault(player.getUniqueId(), Map.of())
                        .getOrDefault(ability.id(), 0L);
                int remaining = (int) Math.max(0, (readyAt - System.currentTimeMillis() + 999) / 1000);
                inventory.setItem(slots[i + 4], customIcon(abilityIconId(ability.id()),
                        "§d✦ " + ability.title(),
                        "§7" + ability.description(),
                        "§7Стоимость: §b" + ability.manaCost() + " маны",
                        "§7Перезарядка: §f" + ability.cooldownSeconds() + " сек.",
                        "§aПуть открыт — навык доступен",
                        remaining > 0 ? "§cОткат: " + remaining + " сек." : "§aГотово к применению",
                        "§eНажми, чтобы применить"));
            } else {
                String requirement = switch (ability.id()) {
                    case "flow" -> "Мудрость III";
                    case "seal" -> "Стойкость III";
                    case "resonance" -> "Сила III";
                    default -> "Концентрация III";
                };
                inventory.setItem(slots[i + 4], customIcon(1315,
                        "§8§lПуть запечатан",
                        "§7" + ability.title(),
                        "§7Требуется: §e" + requirement,
                        "§8Путь появится в быстром доступе после открытия"));
            }
        }
    }

    private void openQuickSkills(Player player) {
        QuickSkillsHolder holder = new QuickSkillsHolder();
        Component title = texturedPlayers.contains(player.getUniqueId())
                ? Component.text("\uF808\uE005").font(net.kyori.adventure.key.Key.key("astral", "gui"))
                : Component.text("✦ БЫСТРЫЙ ДОСТУП К НАВЫКАМ ✦")
                    .color(net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE)
                    .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD);
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.inventory = inv;
        fillNavigation(inv);
        renderAbilitySlots(player, inv);

        player.openInventory(inv);
    }

    private String rankBar(int rank) {
        StringBuilder bar = new StringBuilder("§8[");
        for (int i = 0; i < 10; i++) bar.append(i < rank ? "§6▰" : "§7▱");
        return bar.append("§8]").toString();
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
                int power = get(p, powerKey, 0);
                int wisdom = get(p, wisdomKey, 0);
                int vitality = get(p, vitalityKey, 0);
                int focus = get(p, focusKey, 0);

                inv.setItem(22, customIcon(1304, "§6ЯДРО АСТРАЛА",
                        "§7Архетип: §f" + (type.isEmpty() ? "не выбран" : prettyClass(type)),
                        "§7Очки талантов: §e" + points,
                        "§8Каждый новый ранг открывает новые возможности"));
                inv.setItem(10, customIcon(1332, (power >= 10 ? "§6✦ " : "§c") + skillName(type, "power"),
                        "§7" + skillDescription(type, "power"),
                        "§7Ранг: §f" + power + "/10", rankBar(power),
                        points > 0 && power < 10 ? "§eНажми, чтобы вложить очко" : "§8Нужно очко талантов"));
                inv.setItem(16, customIcon(1335, (wisdom >= 10 ? "§6✦ " : "§b") + skillName(type, "wisdom"),
                        "§7" + skillDescription(type, "wisdom"),
                        "§7Ранг: §f" + wisdom + "/10", rankBar(wisdom),
                        points > 0 && wisdom < 10 ? "§eНажми, чтобы вложить очко" : "§8Нужно очко талантов"));
                inv.setItem(28, customIcon(1334, (vitality >= 10 ? "§6✦ " : "§b") + skillName(type, "vitality"),
                        "§7" + skillDescription(type, "vitality"),
                        "§7Ранг: §f" + vitality + "/10", rankBar(vitality),
                        points > 0 && vitality < 10 ? "§eНажми, чтобы вложить очко" : "§8Нужно очко талантов"));
                inv.setItem(34, customIcon(1338, (focus >= 10 ? "§6✦ " : "§d") + skillName(type, "focus"),
                        "§7" + skillDescription(type, "focus"),
                        "§7Ранг: §f" + focus + "/10", rankBar(focus),
                        points > 0 && focus < 10 ? "§eНажми, чтобы вложить очко" : "§8Нужно очко талантов"));

                for (AbilityDefinition path : pathAbilities()) {
                    int slot = switch (path.id()) {
                        case "flow" -> 12;
                        case "seal" -> 14;
                        case "resonance" -> 30;
                        default -> 32;
                    };
                    String requirement = switch (path.id()) {
                        case "flow" -> "Мудрость III";
                        case "seal" -> "Стойкость III";
                        case "resonance" -> "Сила III";
                        default -> "Концентрация III";
                    };
                    boolean unlocked = pathUnlocked(p, path.id());
                    inv.setItem(slot, unlocked
                            ? customIcon(abilityIconId(path.id()), "§d✦ " + path.title(),
                                "§7Путь открыт", "§eНажми, чтобы применить умение")
                            : customIcon(1315, "§8Путь запечатан",
                                "§7" + path.title(), "§7Требуется: §e" + requirement,
                                "§8Открой до ранга III"));
                }
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
                List<ShopProduct> products = shopProducts();
                for (int i = 0; i < products.size(); i++) {
                    ShopProduct product = products.get(i);
                    int[] productIcons = {1401, 1402, 1403, 1404, 1405, 1406,
                            1407, 1408, 1409, 1410, 1411, 1412};
                    inv.setItem(productSlots[i], customIcon(productIcons[i], "§6" + product.title(),
                            "§7" + product.description(),
                            "§7Количество: §f" + product.amount(),
                            "§7Цена: §e" + product.price() + " монет",
                            coins(p) >= product.price() ? "§aНажми, чтобы купить" : "§cНедостаточно монет"));
                }
            }
        }
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

    private void clickAbilitySlot(Player player, int slot) {
        int[] slots = {10, 12, 14, 16, 28, 30, 32, 34};
        List<AbilityDefinition> base = abilitiesFor(getString(player, classKey, ""));
        List<AbilityDefinition> paths = pathAbilities();
        for (int i = 0; i < slots.length; i++) {
            if (slot != slots[i]) continue;
            if (i < 4) {
                if (i >= base.size()) {
                    message(player, "Сначала выбери архетип в разделе «Архетипы».");
                    openMenu(player, Page.CLASS);
                    return;
                }
                player.closeInventory();
                castAbility(player, base.get(i));
                return;
            }
            AbilityDefinition path = paths.get(i - 4);
            if (!pathUnlocked(player, path.id())) {
                String requirement = switch (path.id()) {
                    case "flow" -> "мудрость III";
                    case "seal" -> "стойкость III";
                    case "resonance" -> "сила III";
                    default -> "концентрация III";
                };
                message(player, "Путь «" + path.title() + "» запечатан. Требуется " + requirement + ".");
                return;
            }
            player.closeInventory();
            castAbility(player, path);
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
            clickAbilitySlot(p, slot);
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
                String skill = switch (slot) {
                    case 10 -> "power";
                    case 16 -> "wisdom";
                    case 28 -> "vitality";
                    case 34 -> "focus";
                    default -> "";
                };
                if (!skill.isEmpty()) {
                    if (getString(p, classKey, "").isEmpty()) {
                        message(p, "Сначала выбери архетип.");
                        openMenu(p, Page.CLASS);
                        return;
                    }
                    NamespacedKey k = switch (skill) {
                        case "power" -> powerKey;
                        case "wisdom" -> wisdomKey;
                        case "vitality" -> vitalityKey;
                        default -> focusKey;
                    };
                    int points = get(p, skillPointsKey, 0);
                    int rank = get(p, k, 0);
                    if (points < 1) message(p, "Недостаточно очков талантов.");
                    else if (rank >= 10) message(p, "Этот навык уже достиг максимального ранга.");
                    else {
                        set(p, skillPointsKey, points - 1);
                        set(p, k, rank + 1);
                        if (skill.equals("wisdom")) set(p, manaKey, maxMana(p));
                        message(p, ChatColor.GREEN + "Изучен талант «"
                                + skillName(getString(p, classKey, ""), skill) + "» — ранг " + (rank + 1) + "/10.");
                        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.25f);
                    }
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
                    AbilityDefinition path = pathAbilities().stream().filter(x -> x.id().equals(pathId)).findFirst().orElse(null);
                    if (path != null && pathUnlocked(p, pathId)) {
                        p.closeInventory();
                        castAbility(p, path);
                    } else {
                        String requirement = switch (pathId) {
                            case "flow" -> "мудрость III";
                            case "seal" -> "стойкость III";
                            case "resonance" -> "сила III";
                            default -> "концентрация III";
                        };
                        message(p, "Для открытия этой ветви требуется " + requirement + ".");
                    }
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
            case ABILITIES -> clickAbilitySlot(p, slot);
            case ECONOMY -> { /* wallet is display-only; earnings and spending update it automatically */ }
            case SHOP -> {
                int[] productSlots = {10, 12, 14, 16, 19, 21, 23, 25, 28, 30, 32, 34};
                List<ShopProduct> products = shopProducts();
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
        message(p, "/astral skill <power|wisdom|vitality|focus> — развить навык за очко");
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
        private final Map<UUID, Integer> remaining = new HashMap<>();
        private final Map<UUID, Integer> playerMaps = new HashMap<>();
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
        private org.bukkit.World dungeonWorld;
        private static final int[] MAP_CENTERS = {0, 100, 200};
        private static final String[] MAP_NAMES = {"Зал сокровищ", "Кристальная пещера", "Затонувшие катакомбы"};

        boolean isDungeon(Player player) {
            return player.getWorld().getName().equals(WORLD_NAME);
        }

        private record Portal(int x, int y, int z, boolean alongX) {}

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
                    message(player, ChatColor.LIGHT_PURPLE + "✦ Портал Astral Nexus пробуждён! Щёлкни ПКМ по белой стеклянной панели, чтобы войти.");
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
                leaveDungeon(player, true);
                return;
            }
            enterDungeon(player);
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

        private record BlockPlacement(int x, int y, int z, Material material) {}
        private final Set<UUID> transitioning = new HashSet<>();
        private final List<Runnable> dungeonReadyCallbacks = new java.util.ArrayList<>();
        private boolean dungeonBuilding;

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
            NamespacedKey builtKey = new NamespacedKey(AstralNexusPlugin.this, "dungeon_built_map_" + mapIndex);
            if (world.getPersistentDataContainer().has(builtKey, PersistentDataType.BYTE)) {
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
            java.util.List<java.util.concurrent.CompletableFuture<org.bukkit.Chunk>> chunks = new java.util.ArrayList<>();
            for (int cx = centerChunk - 2; cx <= centerChunk + 2; cx++)
                for (int cz = -2; cz <= 2; cz++)
                    chunks.add(world.getChunkAtAsync(cx, cz, true, true));
            java.util.concurrent.CompletableFuture.allOf(chunks.toArray(java.util.concurrent.CompletableFuture[]::new))
                    .whenComplete((ignored, error) -> Bukkit.getScheduler().runTask(AstralNexusPlugin.this, () -> {
                        if (error != null || Bukkit.getWorld(WORLD_NAME) == null) {
                            buildingMaps.remove(mapIndex);
                            dungeonReadyCallbacks.remove(mapIndex);
                            getLogger().severe("Could not prepare dungeon map " + mapIndex + ": "
                                    + (error == null ? "world unavailable" : error.getMessage()));
                            for (Player waiting : Bukkit.getOnlinePlayers()) {
                                if (transitioning.remove(waiting.getUniqueId())) {
                                    returnLocations.remove(waiting.getUniqueId());
                                    message(waiting, ChatColor.RED + "Не удалось подготовить подземелье. Проверь журнал сервера.");
                                }
                            }
                            return;
                        }
                        buildDungeonInBatches(world, builtKey, mapIndex);
                    }));
        }

        private record BlockPlacement(int x, int y, int z, Material material) {}

        private void put(List<BlockPlacement> blocks, int x, int y, int z, Material material) {
            blocks.add(new BlockPlacement(x, y, z, material));
        }

        private void addCaveShell(List<BlockPlacement> blocks, int centerX, int mapIndex,
                                  Material floor, Material wall, Material roof) {
            int seed = 37 + mapIndex * 101;
            for (int x = -17; x <= 17; x++) {
                for (int z = -17; z <= 17; z++) {
                    double distance = Math.sqrt(x * x + z * z);
                    if (distance > 17.5) continue;
                    int wx = centerX + x;
                    int noise = Math.floorMod(x * 19 + z * 31 + seed, 5);
                    Material floorBlock = noise == 0 ? Material.POLISHED_DEEPSLATE : floor;
                    put(blocks, wx, 69, z, floorBlock);
                    if (distance > 15.5) {
                        int wallHeight = 77 + Math.floorMod(x * 7 + z * 11 + seed, 5);
                        for (int y = 70; y <= wallHeight; y++) {
                            Material texture = (y % 4 == 0 && noise == 1) ? Material.COBBLED_DEEPSLATE : wall;
                            put(blocks, wx, y, z, texture);
                        }
                    }
                    // Irregular natural ceiling. The middle is left open for each map's signature skylight.
                    if (distance > 7.0) {
                        int ceilingY = 83 - (int)(distance * 0.22) + noise / 2;
                        put(blocks, wx, ceilingY, z, roof);
                        if (noise == 0 || noise == 3) put(blocks, wx, ceilingY - 1, z, wall);
                    }
                }
            }
            // Jagged cave ribs, pillars and hanging stalactites.
            for (int x : new int[]{-13, -7, 7, 13}) {
                for (int z : new int[]{-13, 13}) {
                    for (int y = 70; y <= 77 + Math.floorMod(x + z + seed, 3); y++)
                        put(blocks, centerX + x, y, z, wall);
                    put(blocks, centerX + x, 78, z, Material.SOUL_LANTERN);
                }
            }
            for (int[] stalactite : new int[][]{{-10,-5},{10,-6},{-8,10},{11,8},{0,-15},{-15,1},{15,-1}}) {
                int sx = centerX + stalactite[0], sz = stalactite[1];
                int length = 2 + Math.floorMod(sx + sz + seed, 4);
                for (int i = 0; i < length; i++)
                    put(blocks, sx, 81 - i, sz, Material.POINTED_DRIPSTONE);
            }
        }

        private void buildTreasureCavern(List<BlockPlacement> blocks, int centerX) {
            addCaveShell(blocks, centerX, 0, Material.DEEPSLATE_TILES,
                    Material.DEEPSLATE_BRICKS, Material.TUFF);
            // Blue-tinted glass dome set into the cave ceiling.
            for (int x = -7; x <= 7; x++) for (int z = -7; z <= 7; z++) {
                int d2 = x*x + z*z;
                if (d2 > 49) continue;
                int y = 82 + (int)Math.round(Math.sqrt(49 - d2) * 0.55);
                put(blocks, centerX + x, y, z, (d2 > 38) ? Material.LIGHT_BLUE_STAINED_GLASS : Material.TINTED_GLASS);
            }
            // The central viewing well opens into a compact treasure vault below.
            for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
                if (Math.abs(x) <= 3 && Math.abs(z) <= 3) continue;
                put(blocks, centerX + x, 69, z, Material.POLISHED_BLACKSTONE_BRICKS);
                put(blocks, centerX + x, 70, z, Material.GLASS);
            }
            for (int x = -7; x <= 7; x++) for (int z = -7; z <= 7; z++) {
                if (Math.abs(x) == 7 || Math.abs(z) == 7) {
                    for (int y = 59; y <= 65; y++)
                        put(blocks, centerX + x, y, z, Material.DEEPSLATE_BRICKS);
                }
                if (yIsFloor(x,z)) put(blocks, centerX + x, 58, z, Material.POLISHED_BLACKSTONE_BRICKS);
            }
            // A descending side stair connects the upper hall to the vault.
            for (int step = 0; step < 9; step++) {
                int z = 5 + step;
                int y = 69 - step;
                put(blocks, centerX + 8, y, z, Material.POLISHED_BLACKSTONE_STAIRS);
                put(blocks, centerX + 8, y - 1, z, Material.POLISHED_BLACKSTONE_BRICKS);
            }
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
                if (Math.abs(x) == 3 || Math.abs(z) == 3) put(blocks, centerX + x, 59, z, Material.GOLD_BLOCK);
            }
            put(blocks, centerX, 59, 0, Material.DIAMOND_BLOCK);
            put(blocks, centerX, 60, 0, Material.CHEST);
            for (int[] pos : new int[][]{{-5,-5},{5,-5},{-5,5},{5,5},{0,-6},{0,6}}) {
                put(blocks, centerX + pos[0], 60, pos[1], Material.CHEST);
                put(blocks, centerX + pos[0], 61, pos[1], Material.CHAIN);
            }
            // Storage alcoves and decorative treasure chests in the main chamber.
            for (int x : new int[]{-13, 13}) for (int z : new int[]{-8, 0, 8}) {
                put(blocks, centerX + x, 70, z, Material.BARREL);
                put(blocks, centerX + x, 71, z, Material.LANTERN);
            }
            for (int x : new int[]{-12, 12}) for (int y = 70; y <= 76; y++)
                put(blocks, centerX + x, y, 0, Material.DEEPSLATE_BRICK_WALL);
        }

        private boolean yIsFloor(int x, int z) {
            return true;
        }

        private void buildCrystalCavern(List<BlockPlacement> blocks, int centerX) {
            addCaveShell(blocks, centerX, 1, Material.CALCITE, Material.TUFF_BRICKS, Material.TUFF);
            for (int[] crystal : new int[][]{{-11,-8},{-10,7},{10,-9},{12,7},{0,14},{-14,0},{14,0}}) {
                int height = 3 + Math.floorMod(crystal[0] * 3 + crystal[1], 5);
                for (int y = 70; y < 70 + height; y++)
                    put(blocks, centerX + crystal[0], y, crystal[1], Material.AMETHYST_BLOCK);
                put(blocks, centerX + crystal[0], 70 + height, crystal[1], Material.AMETHYST_CLUSTER);
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                    if (Math.abs(dx) + Math.abs(dz) == 1)
                        put(blocks, centerX + crystal[0] + dx, 70, crystal[1] + dz, Material.BUDDING_AMETHYST);
            }
            for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++)
                if (x*x + z*z <= 16) put(blocks, centerX + x, 69, z, Material.WATER);
            for (int x : new int[]{-15,15}) for (int z : new int[]{-5,5})
                for (int y = 70; y <= 78; y++) put(blocks, centerX + x, y, z, Material.AMETHYST_BLOCK);
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++)
                if (Math.abs(x) == 2 || Math.abs(z) == 2) put(blocks, centerX + x, 70, z, Material.SEA_LANTERN);
        }

        private void buildSunkenCatacombs(List<BlockPlacement> blocks, int centerX) {
            addCaveShell(blocks, centerX, 2, Material.PRISMARINE_BRICKS,
                    Material.DARK_PRISMARINE, Material.PRISMARINE);
            for (int x = -15; x <= 15; x++) {
                put(blocks, centerX + x, 69, -5, Material.WATER);
                put(blocks, centerX + x, 69, 5, Material.WATER);
            }
            for (int x : new int[]{-13, -7, 7, 13}) for (int z : new int[]{-12, 12}) {
                for (int y = 70; y <= 77; y++) put(blocks, centerX + x, y, z, Material.PRISMARINE_BRICKS);
                put(blocks, centerX + x, 78, z, Material.SEA_LANTERN);
            }
            for (int[] coral : new int[][]{{-12,-8},{-10,9},{11,-9},{13,8},{0,14}}) {
                put(blocks, centerX + coral[0], 70, coral[1], Material.MAGMA_BLOCK);
                put(blocks, centerX + coral[0], 71, coral[1], Material.BRAIN_CORAL);
                put(blocks, centerX + coral[0] + 1, 71, coral[1], Material.TUBE_CORAL_FAN);
            }
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++)
                if (Math.abs(x) == 3 || Math.abs(z) == 3)
                    put(blocks, centerX + x, 70, z, Material.DARK_PRISMARINE);
            put(blocks, centerX, 70, 0, Material.SEA_LANTERN);
        }

        private void buildDungeonInBatches(org.bukkit.World world, NamespacedKey builtKey, int mapIndex) {
            List<BlockPlacement> blocks = new java.util.ArrayList<>(6500);
            int centerX = MAP_CENTERS[mapIndex];
            switch (mapIndex) {
                case 0 -> buildTreasureCavern(blocks, centerX);
                case 1 -> buildCrystalCavern(blocks, centerX);
                default -> buildSunkenCatacombs(blocks, centerX);
            }
            final int[] cursor = {0};
            Bukkit.getScheduler().runTaskTimer(AstralNexusPlugin.this, task -> {
                int limit = Math.min(cursor[0] + 220, blocks.size());
                while (cursor[0] < limit) {
                    BlockPlacement b = blocks.get(cursor[0]++);
                    world.getBlockAt(b.x(), b.y(), b.z()).setType(b.material(), false);
                }
                if (cursor[0] >= blocks.size()) {
                    task.cancel();
                    world.setSpawnLocation(centerX, 70, 12);
                    world.getPersistentDataContainer().set(builtKey, PersistentDataType.BYTE, (byte) 1);
                    buildingMaps.remove(mapIndex);
                    List<Runnable> callbacks = dungeonReadyCallbacks.remove(mapIndex);
                    if (callbacks != null) callbacks.forEach(Runnable::run);
                    if (mapIndex == 0) populateTreasureChests(world, centerX);
                    getLogger().info("Astral Nexus cave map " + MAP_NAMES[mapIndex] + " prepared in tick batches.");
                }
            }, 1L, 1L);
        }

        private void populateTreasureChests(org.bukkit.World world, int centerX) {
            for (int[] pos : new int[][]{{0,0},{-5,-5},{5,-5},{-5,5},{5,5},{0,-6},{0,6}}) {
                org.bukkit.block.Block block = world.getBlockAt(centerX + pos[0], 60, pos[1]);
                if (!(block.getState() instanceof org.bukkit.block.Chest chest)) continue;
                chest.getBlockInventory().addItem(new ItemStack(Material.GOLD_INGOT, 8 + Math.floorMod(pos[0] + pos[1], 9)));
                chest.getBlockInventory().addItem(new ItemStack(Material.EMERALD, 3 + Math.floorMod(pos[0] * pos[1], 5)));
                if (pos[0] == 0 && pos[1] == 0)
                    chest.getBlockInventory().addItem(new ItemStack(Material.DIAMOND, 5));
                chest.update(true, false);
            }
        }

        private void enterDungeon(Player player) {
            UUID id = player.getUniqueId();
            if (!transitioning.add(id)) return;
            org.bukkit.World world = getDungeonWorld();
            if (world == null) {
                transitioning.remove(id);
                message(player, ChatColor.RED + "Не удалось создать подземелье. Проверь журнал сервера.");
                return;
            }
            returnLocations.put(id, player.getLocation().clone());
            player.closeInventory();
            message(player, ChatColor.YELLOW + "Подготавливаю подземелье и безопасно загружаю чанки...");
            prepareDungeon(world, () -> {
                if (!player.isOnline() || !transitioning.contains(id)) return;
                org.bukkit.Location destination = new org.bukkit.Location(world, 0.5, 70.1, 0.5);
                player.teleportAsync(destination).whenComplete((success, error) ->
                        Bukkit.getScheduler().runTask(AstralNexusPlugin.this, () -> {
                            transitioning.remove(id);
                            if (!player.isOnline()) return;
                            if (error != null || !Boolean.TRUE.equals(success)) {
                                returnLocations.remove(id);
                                getLogger().warning("Async dungeon teleport failed for " + player.getName()
                                        + (error == null ? "" : ": " + error.getMessage()));
                                message(player, ChatColor.RED + "Не удалось перенести тебя в подземелье. Попробуй ещё раз.");
                                return;
                            }
                            player.getPersistentDataContainer().set(activeKey, PersistentDataType.BYTE, (byte) 1);
                            if (get(player, levelKey, 0) == 0) {
                                set(player, levelKey, 1); set(player, xpKey, 0); set(player, skillPointsKey, 0);
                                set(player, manaKey, 50); set(player, staminaKey, 100);
                                set(player, powerKey, 0); set(player, wisdomKey, 0); set(player, vitalityKey, 0); set(player, focusKey, 0);
                            }
                            ensureSpecialItems(player);
                            ensureEconomy(player);
                            message(player, ChatColor.LIGHT_PURPLE + "Ты вошёл в Подземелье Астрала. Здесь начинаются твои приключения и прокачка.");
                            message(player, ChatColor.GRAY + "Путеводитель и Кристалл талантов выданы. Победи три волны и Стража Разлома.");
                            if (resourcePackSha1 != null) sendResourcePack(player);
                            startAdventure(player);
                        }));
            });
        }

        void startAdventure(Player player) {
            if (!isDungeon(player)) return;
            player.getPersistentDataContainer().set(activeKey, PersistentDataType.BYTE, (byte) 1);
            ensureSpecialItems(player);
            ensureEconomy(player);
            returnLocations.putIfAbsent(player.getUniqueId(), Bukkit.getWorlds().get(0).getSpawnLocation());
            waves.put(player.getUniqueId(), 0);
            spawnWave(player);
        }

        private void spawnWave(Player player) {
            UUID id = player.getUniqueId();
            int wave = waves.getOrDefault(id, 0);
            if (wave >= 3) {
                spawnDungeonMob(player, org.bukkit.entity.EntityType.WITHER_SKELETON, 66.5, 70, 0.5, true);
                message(player, ChatColor.DARK_PURPLE + "Финальная комната: Страж Разлома пробудился!");
                remaining.put(id, 1);
                return;
            }
            double centerX = wave * 22.0;
            org.bukkit.entity.EntityType[] types = switch (wave) {
                case 0 -> new org.bukkit.entity.EntityType[]{org.bukkit.entity.EntityType.ZOMBIE, org.bukkit.entity.EntityType.SKELETON, org.bukkit.entity.EntityType.ZOMBIE};
                case 1 -> new org.bukkit.entity.EntityType[]{org.bukkit.entity.EntityType.HUSK, org.bukkit.entity.EntityType.STRAY, org.bukkit.entity.EntityType.HUSK};
                default -> new org.bukkit.entity.EntityType[]{org.bukkit.entity.EntityType.PILLAGER, org.bukkit.entity.EntityType.VINDICATOR, org.bukkit.entity.EntityType.EVOKER};
            };
            remaining.put(id, types.length);
            for (int i = 0; i < types.length; i++) {
                double z = (i - 1) * 3.0;
                spawnDungeonMob(player, types[i], centerX + 0.5, 70, z + 0.5, false);
            }
            message(player, ChatColor.GOLD + "Комната " + (wave + 1) + "/3: уничтожь всех противников.");
        }

        private void spawnDungeonMob(Player owner, org.bukkit.entity.EntityType type,
                                     double x, double y, double z, boolean boss) {
            org.bukkit.entity.Entity entity = dungeonWorld.spawnEntity(new org.bukkit.Location(dungeonWorld, x, y, z), type);
            if (entity instanceof LivingEntity living) {
                living.getPersistentDataContainer().set(mobOwnerKey, PersistentDataType.STRING, owner.getUniqueId().toString());
                living.getPersistentDataContainer().set(dungeonMobKey, PersistentDataType.BYTE, (byte) 1);
                living.setCustomName(boss ? "§5✦ Страж Разлома" : "§cПорождение Астрала");
                living.setCustomNameVisible(true);
                if (boss && living.getAttribute(Attribute.MAX_HEALTH) != null) {
                    living.getAttribute(Attribute.MAX_HEALTH).setBaseValue(100.0);
                    living.setHealth(100.0);
                    living.setGlowing(true);
                }
            }
        }

        @EventHandler
        public void onDungeonMobDeath(EntityDeathEvent event) {
            LivingEntity mob = event.getEntity();
            String ownerText = mob.getPersistentDataContainer().get(mobOwnerKey, PersistentDataType.STRING);
            if (ownerText == null || mob.getKiller() == null) return;
            Player killer = mob.getKiller();
            if (!killer.getUniqueId().toString().equals(ownerText) || !isDungeon(killer)) return;
            UUID id = killer.getUniqueId();
            int left = remaining.getOrDefault(id, 0) - 1;
            remaining.put(id, left);
            addXp(killer, mob.getPersistentDataContainer().has(dungeonMobKey, PersistentDataType.BYTE) ? 35 : 10);
            if (left <= 0) {
                int wave = waves.getOrDefault(id, 0);
                if (wave >= 3) {
                    killer.getInventory().addItem(dungeonReward(1423, "§5Сердце Стража", "§7Трофей за победу над Стражем Разлома", "§dРедкая астральная реликвия"));
                    killer.getInventory().addItem(dungeonReward(1421, "§bКлинок Разлома", "§7Оружие, выкованное в подземелье", "§8Трофей финальной комнаты"));
                    message(killer, ChatColor.GOLD + "✦ Страж Разлома повержен! Подземелье пройдено.");
                    killer.playSound(killer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                    Bukkit.getScheduler().runTaskLater(AstralNexusPlugin.this, () -> {
                        if (killer.isOnline() && isDungeon(killer)) leaveDungeon(killer, true);
                    }, 80L);
                } else {
                    killer.getInventory().addItem(dungeonReward(1422, "§bФлакон астральной эссенции", "§7Редкая награда за зачистку комнаты", "§8Трофей подземелья"));
                    waves.put(id, wave + 1);
                    Bukkit.getScheduler().runTaskLater(AstralNexusPlugin.this, () -> {
                        if (killer.isOnline() && isDungeon(killer)) spawnWave(killer);
                    }, 60L);
                }
            }
        }

        private void leaveDungeon(Player player, boolean success) {
            UUID id = player.getUniqueId();
            if (!transitioning.add(id)) return;
            org.bukkit.Location back = returnLocations.remove(id);
            if (back == null || back.getWorld() == null) back = Bukkit.getWorlds().get(0).getSpawnLocation();
            waves.remove(id);
            remaining.remove(id);
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
                            message(player, ChatColor.RED + "Не удалось вернуться в обычный мир. Повтори выход через портал.");
                            return;
                        }
                        message(player, success
                                ? ChatColor.GREEN + "Ты вернулся в обычный мир после завершения подземелья. Прогресс Astral Nexus сохранён."
                                : ChatColor.YELLOW + "Ты покинул подземелье. Обычный мир снова работает без RPG-механик.");
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
            transitioning.remove(id);
        }

        @EventHandler
        public void onDungeonRejoin(PlayerJoinEvent event) {
            Player player = event.getPlayer();
            if (!isDungeon(player)) return;
            Bukkit.getScheduler().runTaskLater(AstralNexusPlugin.this, () -> {
                if (player.isOnline() && isDungeon(player)) {
                    message(player, ChatColor.YELLOW + "Сессия подземелья была прервана выходом. Возвращаю тебя в обычный мир.");
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
            returnLocations.clear();
            waves.clear();
            remaining.clear();
            transitioning.clear();
            dungeonReadyCallbacks.clear();
        }
    }

}