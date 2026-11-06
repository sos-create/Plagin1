package ru.soscreate.astralnexus;

import net.kyori.adventure.text.Component;
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
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class AstralNexusPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private NamespacedKey activeKey, classKey, levelKey, xpKey, skillPointsKey;
    private NamespacedKey manaKey, staminaKey, powerKey, wisdomKey, vitalityKey, focusKey, questKey, questKillsKey;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    @Override
    public void onEnable() {
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
        if (points < 1) { message(p, "Нет очков навыков. Получай астральный опыт за приключения."); return; }
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
    public void onOreBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        if (!active(p)) return;
        Material type = event.getBlock().getType();
        if (type.name().endsWith("_ORE") || type == Material.ANCIENT_DEBRIS) addXp(p, 3);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (active(p)) Bukkit.getScheduler().runTask(this, () ->
                message(p, "Твой путь Astral Nexus активен. /astral help — список команд."));
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


    private enum Page { HOME, PROFILE, CLASS, SKILLS, QUESTS, ABILITIES }
    private static final class MenuHolder implements InventoryHolder {
        final Page page; Inventory inventory;
        MenuHolder(Page page) { this.page = page; }
        @Override public Inventory getInventory() { return inventory; }
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
    private void openMenu(Player p, Page page) {
        MenuHolder holder = new MenuHolder(page);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("✦ ASTRAL NEXUS ✦"));
        holder.inventory = inv;
        ItemStack filler = uiItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);
        for (int i = 0; i < 9; i++) inv.setItem(i, uiItem(Material.PURPLE_STAINED_GLASS_PANE, " "));
        inv.setItem(4, uiItem(Material.AMETHYST_SHARD, "§6✦ ASTRAL NEXUS RPG", "§8Путь начинается с тебя"));
        inv.setItem(0, uiItem(Material.NETHER_STAR, "§6Главная"));
        inv.setItem(9, uiItem(Material.PLAYER_HEAD, "§bПерсонаж"));
        inv.setItem(18, uiItem(Material.COMPASS, "§dАрхетип"));
        inv.setItem(27, uiItem(Material.ENCHANTED_BOOK, "§5Навыки"));
        inv.setItem(36, uiItem(Material.WRITABLE_BOOK, "§eЖурнал"));
        inv.setItem(45, uiItem(Material.BLAZE_ROD, "§cСпособности"));
        inv.setItem(49, uiItem(Material.BARRIER, "§cЗакрыть"));
        inv.setItem(53, uiItem(Material.GLOWSTONE_DUST, "§dРесурсы",
                "§7Мана: §f" + get(p,manaKey,0) + "/" + maxMana(p),
                "§7Выносливость: §f" + get(p,staminaKey,100) + "/100"));
        switch (page) {
            case HOME -> {
                inv.setItem(20, uiItem(Material.PLAYER_HEAD,"§bПрофиль персонажа","§7Уровень, опыт и характеристики","§eОткрыть"));
                inv.setItem(22, uiItem(Material.NETHER_STAR,"§dВыбор пути","§7Маг • Воин • Следопыт • Страж","§eВыбрать архетип"));
                inv.setItem(24, uiItem(Material.ENCHANTED_BOOK,"§5Древо навыков","§7Сила • Мудрость • Стойкость • Фокус","§eРазвивать персонажа"));
                inv.setItem(30, uiItem(Material.WRITABLE_BOOK,"§eЖурнал заданий","§7Цели, прогресс и награды"));
                inv.setItem(32, uiItem(Material.BLAZE_ROD,"§cБоевые способности","§7Умения выбранного архетипа"));
                inv.setItem(40, uiItem(Material.AMETHYST_SHARD,"§6Начать приключение",
                        active(p) ? "§aRPG уже активирована" : "§eНажми, чтобы включить RPG"));
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
                inv.setItem(20,uiItem(Material.IRON_SWORD,"§cСила","§7Урон способностей","§7Уровень: "+get(p,powerKey,0),"§eНажми для улучшения"));
                inv.setItem(22,uiItem(Material.LAPIS_LAZULI,"§9Мудрость","§7Максимум и регенерация маны","§7Уровень: "+get(p,wisdomKey,0),"§eНажми для улучшения"));
                inv.setItem(24,uiItem(Material.SHIELD,"§bСтойкость","§7Защита и лечение Стража","§7Уровень: "+get(p,vitalityKey,0),"§eНажми для улучшения"));
                inv.setItem(31,uiItem(Material.CLOCK,"§dКонцентрация","§7Сокращает перезарядку","§7Уровень: "+get(p,focusKey,0),"§eНажми для улучшения"));
                inv.setItem(40,uiItem(Material.EXPERIENCE_BOTTLE,"§6Очки: "+get(p,skillPointsKey,0)));
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
                inv.setItem(22,uiItem(Material.BLAZE_ROD,"§d"+ability,"§7Стоимость: 15 маны","§7Перезарядка: 8 секунд","§7Мана: "+get(p,manaKey,0)+"/"+maxMana(p),"§eНажми для применения"));
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
        if(s==49){p.closeInventory();return;}
        if(s==0){openMenu(p,Page.HOME);return;} if(s==9){openMenu(p,Page.PROFILE);return;}
        if(s==18){openMenu(p,Page.CLASS);return;} if(s==27){openMenu(p,Page.SKILLS);return;}
        if(s==36){openMenu(p,Page.QUESTS);return;} if(s==45){openMenu(p,Page.ABILITIES);return;}
        if(!active(p)&&h.page!=Page.HOME){message(p,"Сначала начни RPG: /astral start.");openMenu(p,Page.HOME);return;}
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
                if(!t.isEmpty()){setString(p,classKey,t);set(p,manaKey,maxMana(p));p.playSound(p.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,0.8f,1.4f);message(p,"Выбран архетип: "+prettyClass(t));openMenu(p,Page.PROFILE);}
            }
            case SKILLS -> {
                String skill=switch(s){case 20->"power";case 22->"wisdom";case 24->"vitality";case 31->"focus";default->"";};
                if(!skill.isEmpty()){
                    if(getString(p,classKey,"").isEmpty()){message(p,"Сначала выбери архетип.");openMenu(p,Page.CLASS);return;}
                    NamespacedKey k=switch(skill){case "power"->powerKey;case "wisdom"->wisdomKey;case "vitality"->vitalityKey;default->focusKey;};
                    int pts=get(p,skillPointsKey,0);
                    if(pts<1)message(p,"Недостаточно очков навыков.");
                    else{set(p,skillPointsKey,pts-1);set(p,k,get(p,k,0)+1);if(skill.equals("wisdom"))set(p,manaKey,maxMana(p));message(p,"Навык улучшен: "+skill);}
                    openMenu(p,Page.SKILLS);
                }
            }
            case QUESTS -> {if(s==22){if(getString(p,questKey,"").isEmpty()){setString(p,questKey,"hunt");set(p,questKillsKey,0);message(p,"Задание принято: победи 5 монстров.");}openMenu(p,Page.QUESTS);}}
            case ABILITIES -> {if(s==22){p.closeInventory();cast(p);}}
            default -> {}
        }
    }
    @EventHandler public void onUiDrag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof MenuHolder)e.setCancelled(true);}

    private void help(Player p) {
        message(p, ChatColor.LIGHT_PURPLE + "Astral Nexus RPG — /astral menu открывает меню");
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
