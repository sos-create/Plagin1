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
import java.util.UUID;

import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

public final class AstralNexusPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private NamespacedKey activeKey, classKey, levelKey, xpKey, skillPointsKey;
    private NamespacedKey manaKey, staminaKey, powerKey, wisdomKey, vitalityKey, focusKey, questKey, questKillsKey;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
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

        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("astral") != null) getCommand("astral").setExecutor(this);

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
            message(player, ChatColor.RED + "Ресурс-пак не подготовлен. Проверь resource-pack.zip.b64 внутри JAR.");
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
        try (var input = getResource("resource-pack.zip.b64")) {
            if (input == null) {
                getLogger().warning("Bundled resource-pack.zip.b64 is missing; custom textures are disabled.");
                return;
            }
            byte[] zip = Base64.getMimeDecoder().decode(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
                throw new IOException("Cannot create plugin data folder");
            }
            if (zip.length < 4 || zip[0] != 'P' || zip[1] != 'K' || zip[2] != 3 || zip[3] != 4) {
                throw new IOException("Embedded resource pack is not a valid ZIP archive");
            }
            resourcePackFile = new File(getDataFolder(), "resource-pack.zip");
            Files.write(resourcePackFile.toPath(), zip);
            resourcePackSha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(zip));
            getLogger().info("Bundled Astral Nexus resource pack extracted (SHA-1: " + resourcePackSha1 + ").");
        } catch (IOException | NoSuchAlgorithmException | IllegalArgumentException ex) {
            getLogger().severe("Could not prepare bundled resource pack: " + ex.getMessage());
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
        if (type.name().endsWith("_ORE") || type == Material.ANCIENT_DEBRIS) addXp(p, 3);
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

            if (resourcePackSha1 != null) sendResourcePack(p);
        }, 40L);
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

    private enum Page { HOME, PROFILE, CLASS, SKILLS, QUESTS, ABILITIES }
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
            default -> 0;
        };
        if (customModel > 0) meta.setCustomModelData(customModel);
        var serializer = LegacyComponentSerializer.legacySection();
        meta.displayName(serializer.deserialize(name));
        if (lore.length > 0) meta.lore(java.util.Arrays.stream(lore).map(serializer::deserialize).toList());
        item.setItemMeta(meta);
        return item;
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
        };
        Component menuTitle;
        if (texturedPlayers.contains(p.getUniqueId()) && page == Page.HOME) {
            menuTitle = Component.text("\uE000").font(net.kyori.adventure.key.Key.key("astral", "gui"));
        } else if (texturedPlayers.contains(p.getUniqueId()) && page == Page.SKILLS) {
            menuTitle = Component.text("\uE001").font(net.kyori.adventure.key.Key.key("astral", "gui"));
        } else {
            menuTitle = Component.text("✦ ").color(net.kyori.adventure.text.format.NamedTextColor.DARK_PURPLE)
                    .append(Component.text("ASTRAL NEXUS").color(net.kyori.adventure.text.format.NamedTextColor.GOLD).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD))
                    .append(Component.text(" • " + section + " ✦").color(net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE));
        }
        Inventory inv = Bukkit.createInventory(holder, 54, menuTitle);
        holder.inventory = inv;

        ItemStack voidPane = uiItem(Material.BLACK_STAINED_GLASS_PANE, "§0");
        ItemStack borderPane = uiItem(Material.PURPLE_STAINED_GLASS_PANE, "§5✦");
        ItemStack accentPane = uiItem(Material.MAGENTA_STAINED_GLASS_PANE, "§d✧");
        ItemStack starPane = uiItem(Material.AMETHYST_CLUSTER, "§d✦");
        for (int i = 0; i < 54; i++) inv.setItem(i, voidPane);
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, (i % 2 == 0) ? borderPane : accentPane);
            inv.setItem(45 + i, (i % 2 == 0) ? borderPane : accentPane);
        }
        for (int row = 1; row < 5; row++) {
            inv.setItem(row * 9, borderPane);
            inv.setItem(row * 9 + 8, borderPane);
        }
        for (int slot : new int[]{10, 12, 14, 16, 28, 29, 33, 34, 38, 39, 41, 42}) inv.setItem(slot, starPane);
        inv.setItem(1, accentPane);
        inv.setItem(7, accentPane);
        inv.setItem(46, accentPane);
        inv.setItem(52, accentPane);

        inv.setItem(4, uiItem(Material.NETHER_STAR, "§6✦ ASTRAL NEXUS ✦",
                "§8━━━━━━━━━━━━━━━━━━━━", "§7Твой путь среди звёзд начинается здесь",
                "§d✧ §fВыбери раздел, чтобы открыть его", "§8━━━━━━━━━━━━━━━━━━━━"));
        inv.setItem(0, uiItem(Material.NETHER_STAR, "§6Главная", "§8Вернуться к центру меню"));
        inv.setItem(9, uiItem(Material.PLAYER_HEAD, "§bПерсонаж", "§8Уровень, опыт и параметры"));
        inv.setItem(18, uiItem(Material.ENDER_EYE, "§dАрхетипы", "§8Выбери свой путь"));
        inv.setItem(27, uiItem(Material.ENCHANTED_BOOK, "§5Навыки", "§8Развивай таланты героя"));
        inv.setItem(36, uiItem(Material.WRITABLE_BOOK, "§eЖурнал", "§8Задания и награды"));
        inv.setItem(45, uiItem(Material.BLAZE_ROD, "§cСпособности", "§8Боевые умения архетипа"));
        inv.setItem(49, uiItem(Material.BARRIER, "§cЗакрыть меню", "§7Вернуться в мир"));
        inv.setItem(53, uiItem(Material.LAPIS_LAZULI, "§b✧ Энергия героя",
                "§7Мана: §f" + get(p,manaKey,0) + "/" + maxMana(p),
                "§7Выносливость: §f" + get(p,staminaKey,100) + "/100",
                "§8Регенерация происходит автоматически",
                "§8Твои силы всегда под контролем"));
        switch (page) {
            case HOME -> {
                // The custom font glyph supplies the full-screen artwork; keep only interactive equipment slots.
                for (int slot = 0; slot < 54; slot++) inv.clear(slot);
                inv.setItem(4, uiItem(Material.NETHER_STAR, "§d✦ ASTRAL NEXUS ✦", "§7Экипировка и развитие героя"));
                inv.setItem(0, uiItem(Material.COMPASS, "§6Обзор", "§7Главный экран персонажа"));
                inv.setItem(2, uiItem(Material.PLAYER_HEAD, "§bПрофиль", "§7Характеристики героя"));
                inv.setItem(3, uiItem(Material.ENDER_EYE, "§dАрхетипы", "§7Выбери класс персонажа"));
                inv.setItem(5, uiItem(Material.ENCHANTED_BOOK, "§5Навыки", "§7Открой древо талантов"));
                inv.setItem(6, uiItem(Material.WRITABLE_BOOK, "§eЖурнал", "§7Задания и награды"));
                inv.setItem(7, uiItem(Material.BLAZE_ROD, "§cСпособности", "§7Боевые умения"));
                inv.setItem(8, uiItem(Material.BARRIER, "§cЗакрыть", "§7Вернуться в мир"));
                ItemStack[] armor = p.getInventory().getArmorContents();
                int[] armorSlots = {37, 28, 19, 10};
                Material[] armorFallback = {Material.IRON_BOOTS, Material.IRON_LEGGINGS, Material.IRON_CHESTPLATE, Material.IRON_HELMET};
                String[] armorNames = {"§7Сапоги", "§7Поножи", "§7Нагрудник", "§7Шлем"};
                for (int n = 0; n < armorSlots.length; n++) {
                    ItemStack equipped = armor[n];
                    inv.setItem(armorSlots[n], equipped == null || equipped.getType().isAir()
                            ? uiItem(armorFallback[n], armorNames[n], "§8Слот экипировки пуст") : equipped.clone());
                }
                ItemStack offhand = p.getInventory().getItemInOffHand();
                inv.setItem(16, offhand == null || offhand.getType().isAir()
                        ? uiItem(Material.SHIELD, "§bЛевая рука", "§8Щит или предмет во второй руке") : offhand.clone());
                ItemStack mainhand = p.getInventory().getItemInMainHand();
                inv.setItem(25, mainhand == null || mainhand.getType().isAir()
                        ? uiItem(Material.IRON_SWORD, "§cОружие", "§8Возьми оружие в основную руку") : mainhand.clone());
                inv.setItem(34, uiItem(Material.BOW, "§dСлот аксессуара", "§8Ячейка для будущих аксессуаров"));
                inv.setItem(43, uiItem(Material.AMETHYST_SHARD, "§5Астральная реликвия", "§8Ячейка для редкого артефакта"));
                ItemStack avatar = uiItem(Material.PLAYER_HEAD, "§d" + p.getName(),
                        "§7Уровень §f" + get(p, levelKey, 1),
                        "§7Архетип §f" + (getString(p, classKey, "").isEmpty() ? "Не выбран" : prettyClass(getString(p, classKey, ""))),
                        "§8Твой персонаж");
                if (avatar.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta skull) {
                    skull.setOwningPlayer(p);
                    avatar.setItemMeta(skull);
                }
                inv.setItem(22, avatar);
                inv.setItem(31, uiItem(Material.NETHER_STAR, "§d✧ Снаряжение героя ✧",
                        "§7Слева — броня, справа — оружие и реликвии",
                        "§8Предметы показываются из твоего инвентаря"));
                inv.setItem(11, uiItem(Material.GOLD_INGOT, "§6Золото", "§f0 §7монет", "§8Баланс экономики"));
                inv.setItem(20, uiItem(Material.AMETHYST_SHARD, "§dАстральные осколки", "§f0 §7осколков", "§8Редкий ресурс"));
                int level = get(p, levelKey, 1);
                int xp = get(p, xpKey, 0);
                int requiredXp = Math.max(100, level * 100);
                int progress = Math.max(0, Math.min(20, xp * 20 / requiredXp));
                StringBuilder xpBar = new StringBuilder("§8[");
                for (int n = 0; n < 20; n++) xpBar.append(n < progress ? "§d▰" : "§7▱");
                xpBar.append("§8]");
                inv.setItem(40, uiItem(Material.EXPERIENCE_BOTTLE, "§6Уровень " + level,
                        "§7Опыт: §f" + xp + " §8/ §f" + requiredXp, xpBar.toString(),
                        "§7Очки талантов: §e" + get(p, skillPointsKey, 0)));
                for (int slot : new int[]{46, 47, 48, 50, 51, 52}) {
                    inv.setItem(slot, uiItem(Material.GRAY_STAINED_GLASS_PANE, "§8§lЗАБЛОКИРОВАНО",
                            "§7Дополнительная ячейка экипировки", "§8Откроется в будущих обновлениях"));
                }
            }
            case PROFILE -> {
                inv.setItem(20, uiItem(Material.PLAYER_HEAD,"§bПерсонаж",
                        "§7Архетип: §f"+(getString(p,classKey,"").isEmpty()?"Не выбран":prettyClass(getString(p,classKey,""))),
                        "§7Уровень: §f"+get(p,levelKey,1),"§7Опыт: §f"+get(p,xpKey,0),
                        "§7Очки навыков: §f"+get(p,skillPointsKey,0)));
                inv.setItem(22, uiItem(Material.LAPIS_LAZULI,"§9Мана и выносливость",
                        "§7Мана: §f"+get(p,manaKey,0)+"/"+maxMana(p),
                        "§7Выносливость: §f"+get(p,staminaKey,100)+"/100"));
                inv.setItem(24, uiItem(Material.IRON_SWORD,"§6Характеристики",
                        "§7Сила: §f"+get(p,powerKey,0),"§7Мудрость: §f"+get(p,wisdomKey,0),
                        "§7Стойкость: §f"+get(p,vitalityKey,0),"§7Концентрация: §f"+get(p,focusKey,0)));
            }
            case CLASS -> {
                inv.setItem(20,uiItem(Material.BLAZE_ROD,"§dМаг","§7Астральная магия и импульсы","§eВыбрать"));
                inv.setItem(22,uiItem(Material.IRON_SWORD,"§cВоин","§7Мощные удары ближнего боя","§eВыбрать"));
                inv.setItem(24,uiItem(Material.BOW,"§aСледопыт","§7Точность и звёздные стрелы","§eВыбрать"));
                inv.setItem(31,uiItem(Material.SHIELD,"§bСтраж","§7Защита и восстановление","§eВыбрать"));
                String chosen=getString(p,classKey,"");
                inv.setItem(40,uiItem(Material.PAPER,"§6Твой путь",chosen.isEmpty()?"§7Ещё не выбран":"§f"+prettyClass(chosen)));
            }
            case SKILLS -> {
                // Remove decorative glass filler so the constellation artwork is visible between real skill nodes.
                for (int slot : new int[]{10, 11, 12, 13, 14, 15, 16, 28, 29, 30, 31, 32, 33, 34, 38, 39, 41, 42}) inv.clear(slot);
                String type = getString(p, classKey, "");
                int points = get(p, skillPointsKey, 0);
                // Branches of the talent constellation. Gold links lead from the core to four skill nodes.
                Material link = Material.YELLOW_STAINED_GLASS_PANE;
                for (int slot : new int[]{13, 12, 14, 11, 15, 31, 30, 32, 29, 33}) {
                    inv.setItem(slot, uiItem(link, "§6✦"));
                }
                inv.setItem(22, uiItem(Material.NETHER_STAR, "§6✦ ЯДРО АСТРАЛА ✦",
                        "§7Архетип: §f" + (type.isEmpty() ? "не выбран" : prettyClass(type)),
                        "§7Уровень: §e" + get(p, levelKey, 1),
                        "§7Очки талантов: §e" + points,
                        "§8От ядра расходятся четыре пути развития"));
                if (type.isEmpty()) {
                    inv.setItem(13, uiItem(Material.ENDER_EYE, "§dПробуждение древа",
                            "§7Сначала выбери архетип героя.",
                            "§eОткрой раздел «Архетипы»."));
                } else {
                    int power = get(p, powerKey, 0);
                    int wisdom = get(p, wisdomKey, 0);
                    int vitality = get(p, vitalityKey, 0);
                    int focus = get(p, focusKey, 0);
                    inv.setItem(10, uiItem(Material.IRON_SWORD, (power >= 10 ? "§6✦ " : "§c✧ ") + skillName(type, "power"),
                            "§7" + skillDescription(type, "power"),
                            "§7Ранг: §f" + power + " §8/ §f10",
                            "§8" + rankBar(power),
                            power >= 10 ? "§aМаксимальный ранг" : "§eНажми, чтобы вложить 1 очко"));
                    inv.setItem(16, uiItem(Material.ENCHANTED_BOOK, (wisdom >= 10 ? "§6✦ " : "§9✧ ") + skillName(type, "wisdom"),
                            "§7" + skillDescription(type, "wisdom"),
                            "§7Ранг: §f" + wisdom + " §8/ §f10",
                            "§8" + rankBar(wisdom),
                            wisdom >= 10 ? "§aМаксимальный ранг" : "§eНажми, чтобы вложить 1 очко"));
                    inv.setItem(28, uiItem(Material.SHIELD, (vitality >= 10 ? "§6✦ " : "§b✧ ") + skillName(type, "vitality"),
                            "§7" + skillDescription(type, "vitality"),
                            "§7Ранг: §f" + vitality + " §8/ §f10",
                            "§8" + rankBar(vitality),
                            vitality >= 10 ? "§aМаксимальный ранг" : "§eНажми, чтобы вложить 1 очко"));
                    inv.setItem(34, uiItem(Material.CLOCK, (focus >= 10 ? "§6✦ " : "§d✧ ") + skillName(type, "focus"),
                            "§7" + skillDescription(type, "focus"),
                            "§7Ранг: §f" + focus + " §8/ §f10",
                            "§8" + rankBar(focus),
                            focus >= 10 ? "§aМаксимальный ранг" : "§eНажми, чтобы вложить 1 очко"));
                }
                inv.setItem(40, uiItem(Material.EXPERIENCE_BOTTLE, "§6Очки талантов: §e" + points,
                        "§7Получай одно очко за каждый новый уровень.",
                        "§8Каждый ранг стоит 1 очко."));
                inv.setItem(4, uiItem(Material.NETHER_STAR, "§6ДРЕВО ТАЛАНТОВ",
                        "§7Астральная карта развития героя",
                        "§7Класс: §f" + (type.isEmpty() ? "не выбран" : prettyClass(type))));
            }
            case QUESTS -> {
                boolean has=!getString(p,questKey,"").isEmpty();
                inv.setItem(22,uiItem(Material.ZOMBIE_HEAD,has?"§aОхота на монстров — активна":"§eОхота на монстров",
                        "§7Победи 5 враждебных существ","§7Прогресс: "+get(p,questKillsKey,0)+"/5",
                        "§7Награда: 120 астрального опыта",has?"§aЗадание принято":"§eНажми, чтобы принять"));
            }
            case ABILITIES -> {
                String type=getString(p,classKey,"");
                String ability=switch(type){case "mage"->"Астральный импульс";case "warrior"->"Вихрь клинка";case "ranger"->"Звёздная стрела";case "guardian"->"Астральная защита";default->"Сначала выбери архетип";};
                String detail=switch(type){case "mage"->"Волна энергии поражает врагов вокруг";case "warrior"->"Круговой удар по ближайшим монстрам";case "ranger"->"Мощная критическая звёздная стрела";case "guardian"->"Лечение и временный защитный щит";default->"Выбери класс, чтобы открыть умение";};
                int cost=Math.max(5,15-get(p,wisdomKey,0));
                inv.setItem(22,uiItem(Material.BLAZE_ROD,"§d✦ "+ability,
                        "§7"+detail,"§7Стоимость: §b"+cost+" маны",
                        "§7Перезарядка: §f"+Math.max(3,8-get(p,focusKey,0)*0.5)+" сек.",
                        "§7Мана: §f"+get(p,manaKey,0)+"/"+maxMana(p),
                        "§eНажми, чтобы применить способность"));
            }
        }
        p.openInventory(inv);
    }
    @EventHandler public void onUiClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!(e.getView().getTopInventory().getHolder() instanceof MenuHolder h)) return;
        e.setCancelled(true);
        if (e.getClickedInventory()!=e.getView().getTopInventory()) return;
        int s=e.getRawSlot();
        if((h.page==Page.HOME && s==8) || (h.page!=Page.HOME && s==49)){p.closeInventory();return;}
        if(h.page==Page.HOME){
            if(s==0){openMenu(p,Page.HOME);return;}
            if(s==2){openMenu(p,Page.PROFILE);return;}
            if(s==3){openMenu(p,Page.CLASS);return;}
            if(s==5){openMenu(p,Page.SKILLS);return;}
            if(s==6){openMenu(p,Page.QUESTS);return;}
            if(s==7){openMenu(p,Page.ABILITIES);return;}
            if(s==31 && !active(p)){activate(p);message(p,ChatColor.GREEN + "Путь Astral Nexus начат! Теперь выбери архетип.");openMenu(p,Page.CLASS);return;}
            return;
        }
        if(s==0){openMenu(p,Page.HOME);return;}
        if(s==9){openMenu(p,Page.PROFILE);return;}
        if(s==18){openMenu(p,Page.CLASS);return;} if(s==27){openMenu(p,Page.SKILLS);return;}
        if(s==36){openMenu(p,Page.QUESTS);return;} if(s==45){openMenu(p,Page.ABILITIES);return;}
        switch(h.page){
            case HOME -> {
                if(s==20)openMenu(p,Page.PROFILE); else if(s==22)openMenu(p,Page.CLASS);
                else if(s==24)openMenu(p,Page.SKILLS); else if(s==30)openMenu(p,Page.QUESTS);
                else if(s==32)openMenu(p,Page.ABILITIES);
                else if(s==40&&!active(p)){activate(p);message(p,"Ты начал путь Astral Nexus! Выбери архетип.");openMenu(p,Page.CLASS);}
            }
            case CLASS -> {
                if(!getString(p,classKey,"").isEmpty()){message(p,"Архетип уже выбран.");return;}
                String t=switch(s){case 20->"mage";case 22->"warrior";case 24->"ranger";case 31->"guardian";default->"";};
                if(!t.isEmpty()){
                    if(!active(p)) activate(p);
                    setString(p,classKey,t);
                    set(p,manaKey,maxMana(p));
                    p.playSound(p.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,0.8f,1.4f);
                    message(p,ChatColor.GREEN + "Выбран архетип: " + prettyClass(t));
                    openMenu(p,Page.PROFILE);
                }
            }
            case SKILLS -> {
                String skill=switch(s){case 10->"power";case 16->"wisdom";case 28->"vitality";case 34->"focus";default->"";};
                if(!skill.isEmpty()){
                    if(getString(p,classKey,"").isEmpty()){message(p,"Сначала выбери архетип.");openMenu(p,Page.CLASS);return;}
                    NamespacedKey k=switch(skill){case "power"->powerKey;case "wisdom"->wisdomKey;case "vitality"->vitalityKey;default->focusKey;};
                    int pts=get(p,skillPointsKey,0);
                    int rank=get(p,k,0);
                    if(pts<1)message(p,"Недостаточно очков талантов.");
                    else if(rank>=10)message(p,"Этот навык уже достиг максимального ранга.");
                    else {
                        set(p,skillPointsKey,pts-1);
                        set(p,k,rank+1);
                        if(skill.equals("wisdom"))set(p,manaKey,maxMana(p));
                        message(p,ChatColor.GREEN+"Изучен талант «"+skillName(getString(p,classKey,""),skill)+"» — ранг "+(rank+1)+"/10.");
                        p.playSound(p.getLocation(),Sound.BLOCK_ENCHANTMENT_TABLE_USE,0.8f,1.25f);
                    }
                    openMenu(p,Page.SKILLS);
                }
            }
            case QUESTS -> {if(s==22){if(getString(p,questKey,"").isEmpty()){setString(p,questKey,"hunt");set(p,questKillsKey,0);message(p,"Задание принято: победи 5 монстров.");}openMenu(p,Page.QUESTS);}}
            case ABILITIES -> {if(s==22){p.closeInventory();cast(p);}}
            default -> {}
        }
    }
    @EventHandler
    public void onUiDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    private void help(Player p) {
        message(p, ChatColor.LIGHT_PURPLE + "Astral Nexus RPG — /astral menu открывает меню");
        message(p, "/astral pack — повторно предложить загрузку текстур");
        message(p, "/astral menu — открыть игровое меню");
        message(p, "/astral start — добровольно включить RPG");
        message(p, "/astral choose <mage|warrior|ranger|guardian> — выбрать архетип");
        message(p, "/astral profile — персонаж и прогресс");
        message(p, "/astral cast — применить способность (8 сек. перезарядка)");
        message(p, "/astral skill <power|wisdom|vitality|focus> — развить навык за очко");
        message(p, "/astral quest accept hunt — начать охоту на 5 монстров");
        message(p, "/astral quest info — прогресс задания | /astral stop — отключить RPG");
    }
}
