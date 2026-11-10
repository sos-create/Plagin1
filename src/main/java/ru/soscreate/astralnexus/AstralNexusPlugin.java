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
    private NamespacedKey menuItemKey, quickSkillsItemKey, coinsKey;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Map<String, Long>> abilityCooldowns = new HashMap<>();
    private final Set<UUID> texturedPlayers = new HashSet<>();
    private HttpServer resourcePackServer;
    private File resourcePackFile;
    private String resourcePackSha1;

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

        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("astral") != null) getCommand("astral").setExecutor(this);
        for (Player online : Bukkit.getOnlinePlayers()) { ensureSpecialItems(online); ensureEconomy(online); }

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
        return p.getPersistentDataContainer().getOrDefault(activeKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
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
        if (args.length == 0) { openMenu(p, Page.HOME); return true; }
        if (args[0].equalsIgnoreCase("help")) { help(p); return true; }

        switch (args[0].toLowerCase()) {
            case "menu" -> openMenu(p, Page.HOME);
            case "economy" -> openMenu(p, Page.ECONOMY);
            case "shop" -> openMenu(p, Page.SHOP);
            case "pack" -> sendResourcePack(p);
            case "start" -> {
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
                new QuestDefinition("hunt", "Охотник на монстров", Material.ZOMBIE_HEAD, "Победи 5 враждебных существ", "kill", 5, 120, 90),
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
    }

    @EventHandler
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
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline() || !p.hasPermission("astralnexus.use")) return;
            Component open = Component.text("✦ ОТКРЫТЬ ASTRAL NEXUS ✦")
                    .color(net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE)
                    .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD)
                    .clickEvent(ClickEvent.runCommand("/astral menu"))
                    .hoverEvent(HoverEvent.showText(Component.text("Нажми, чтобы открыть меню Astral Nexus")));
            p.sendMessage(Component.text("╔══ ").color(net.kyori.adventure.text.format.NamedTextColor.DARK_PURPLE)
                    .append(Component.text("АСТРАЛЬНЫЙ НЕКСУС").color(net.kyori.adventure.text.format.NamedTextColor.GOLD))
                    .append(Component.text(" ══╗").color(net.kyori.adventure.text.format.NamedTextColor.DARK_PURPLE)));
            p.sendMessage(Component.text("Нажми на кнопку ниже — команда не требуется:"));
            p.sendMessage(open);

            ensureSpecialItems(p);
            ensureEconomy(p);
            message(p, ChatColor.LIGHT_PURPLE + "Выданы два предмета: Путеводитель Астрала и Кристалл талантов. Используй ПКМ.");
            if (resourcePackSha1 != null) sendResourcePack(p);
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
    private ItemStack uiItem(Material type, String name, String... lore) {
        ItemStack item = new ItemStack(type);
        ItemMeta meta = item.getItemMeta();
        int customModel = switch (type) {
            case NETHER_STAR -> 1001;
            case IRON_SWORD -> 1002;
            case LAPIS_LAZULI -> 1003;
            case HEART_OF_THE_SEA -> 1101;
            case RABBIT_FOOT -> 1102;
            case NAUTILUS_SHELL -> 1103;
            case EMERALD -> 1104;
            case PHANTOM_MEMBRANE -> 1105;
            case ECHO_SHARD -> 1106;
            case TOTEM_OF_UNDYING -> 1107;
            case PRISMARINE_SHARD -> 1108;
            case BLAZE_POWDER -> 1109;
            case AMETHYST_SHARD -> 1110;
            case CLOCK -> 1111;
            case FEATHER -> 1112;
            case COMPASS -> 1301;
            case PAPER -> 1302;
            case ENDER_EYE -> 1303;
            case ENCHANTED_BOOK -> 1304;
            case WRITABLE_BOOK -> 1305;
            case BLAZE_ROD -> 1306;
            case GOLD_INGOT -> 1309;
            case CHEST -> 1310;
            default -> 0;
        };
        if (customModel > 0) meta.setCustomModelData(customModel);
        var serializer = LegacyComponentSerializer.legacySection();
        meta.displayName(serializer.deserialize(name));
        if (lore.length > 0) meta.lore(java.util.Arrays.stream(lore).map(serializer::deserialize).toList());
        item.setItemMeta(meta);
        return item;
    }
    private ItemStack customIcon(int model, String title, String... lore) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setCustomModelData(model);
        var serializer = LegacyComponentSerializer.legacySection();
        meta.displayName(serializer.deserialize(title));
        if (lore.length > 0) meta.lore(java.util.Arrays.stream(lore).map(serializer::deserialize).toList());
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
        inv.setItem(8, uiItem(Material.BARRIER, "§cЗакрыть", "§7Вернуться в мир"));
    }

    private int abilityIconId(String id) {
        return switch (id) {
            case "nova", "spin", "quake" -> 1331;
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

    private ItemStack specialItem(Material material, int customModel, NamespacedKey marker,
                                  String title, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setCustomModelData(customModel);
        meta.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
        var serializer = LegacyComponentSerializer.legacySection();
        meta.displayName(serializer.deserialize(title));
        if (lore.length > 0) meta.lore(java.util.Arrays.stream(lore).map(serializer::deserialize).toList());
        item.setItemMeta(meta);
        return item;
    }

    private boolean hasMarkedItem(Player player, NamespacedKey marker) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType().isAir() || !item.hasItemMeta()) continue;
            if (item.getItemMeta().getPersistentDataContainer().has(marker, PersistentDataType.BYTE)) return true;
        }
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
        if (!hasMarkedItem(player, menuItemKey)) {
            giveMarkedItem(player, specialItem(Material.NETHER_STAR, 1307, menuItemKey,
                    "§6✦ Путеводитель Астрала",
                    "§7ПКМ — открыть главное RPG-меню",
                    "§8Персонаж • архетипы • журнал • способности"));
        }
        if (!hasMarkedItem(player, quickSkillsItemKey)) {
            giveMarkedItem(player, specialItem(Material.RECOVERY_COMPASS, 1308, quickSkillsItemKey,
                    "§d✧ Кристалл талантов",
                    "§7ПКМ — быстрый выбор навыка",
                    "§8Повышение талантов и применение способности"));
        }
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
            inventory.setItem(slots[i], customIcon(abilityIconId(ability.id()),
                    "§d✦ " + ability.title(),
                    "§7" + ability.description(),
                    "§7Стоимость: §b" + ability.manaCost() + " маны",
                    "§7Перезарядка: §f" + ability.cooldownSeconds() + " сек.",
                    "§eНажми, чтобы применить"));
        }

        for (int i = 0; i < paths.size(); i++) {
            AbilityDefinition ability = paths.get(i);
            if (pathUnlocked(player, ability.id())) {
                inventory.setItem(slots[i + 4], customIcon(abilityIconId(ability.id()),
                        "§d✦ " + ability.title(),
                        "§7" + ability.description(),
                        "§7Стоимость: §b" + ability.manaCost() + " маны",
                        "§7Перезарядка: §f" + ability.cooldownSeconds() + " сек.",
                        "§aПуть открыт — навык доступен",
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
                    inv.setItem(productSlots[i], uiItem(product.material(), "§6" + product.title(),
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
            case 4 -> openMenu(player, Page.SHOP);
            case 5 -> openMenu(player, Page.SKILLS);
            case 6 -> openMenu(player, Page.QUESTS);
            case 7 -> openMenu(player, Page.ABILITIES);
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
}
