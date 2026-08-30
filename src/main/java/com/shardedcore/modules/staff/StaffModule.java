package com.shardedcore.modules.staff;

import com.shardedcore.ShardedCore;
import com.shardedcore.database.Sqlite;
import com.shardedcore.gui.GuiButtons;
import com.shardedcore.gui.Menus;
import com.shardedcore.module.Module;
import com.shardedcore.util.Amounts;
import com.shardedcore.util.ColorUtil;
import com.shardedcore.util.Items;
import com.shardedcore.util.Players;
import com.shardedcore.util.Sounds;
import com.shardedcore.util.Tabs;
import com.shardedcore.util.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public final class StaffModule extends Module implements CommandExecutor, TabCompleter, Listener {

    private static final List<String> COMMANDS = List.of(
            "staff", "staffmode", "vanish", "freeze", "unfreeze", "stafflist", "randomtp", "staffchat",
            "gmc", "gms", "gmsp", "gma", "punish", "ban", "mute", "kick", "offend", "banip",
            "unban", "unbanip", "unmute", "pardon", "alts", "screenshare", "invrollback",
            "revokepunishment", "requeststaff", "staffgoto"
    );

    private Sqlite sqlite;
    private boolean remoteSql;
    private NamespacedKey toolKey;
    private final Set<UUID> staffMode = ConcurrentHashMap.newKeySet();
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();
    private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();
    private final Set<UUID> staffChat = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> screenshare = new ConcurrentHashMap<>();
    private final Map<UUID, Long> requests = new ConcurrentHashMap<>();
    private final Map<UUID, Long> toolClicks = new ConcurrentHashMap<>();
    private BukkitTask snapshotTask;

    public StaffModule(ShardedCore plugin) {
        super(plugin, "staff");
    }

    @Override
    public void enable() {
        sqlite = plugin.toggles().sqlite();
        Sqlite remote = com.shardedcore.database.Databases.open(plugin, config.getConfigurationSection("database"), sqlite, "Staff");
        if (remote != sqlite) {
            sqlite = remote;
            remoteSql = true;
        }
        toolKey = new NamespacedKey(plugin, "staff_tool");
        try {
            boolean mysql = sqlite.mysql();
            String id = mysql ? "INT NOT NULL AUTO_INCREMENT PRIMARY KEY" : "INTEGER PRIMARY KEY AUTOINCREMENT";
            String text = mysql ? "VARCHAR(255)" : "TEXT";
            String longText = mysql ? "LONGTEXT" : "TEXT";
            String integer = mysql ? "BIGINT" : "INTEGER";
            sqlite.run("CREATE TABLE IF NOT EXISTS staff_punishments ("
                    + "id " + id + ", uuid " + text + " NOT NULL, name " + text + " NOT NULL, type " + text + " NOT NULL, "
                    + "reason " + text + " NOT NULL, staff " + text + " NOT NULL, created " + integer + " NOT NULL, "
                    + "expires " + integer + " NOT NULL, ip " + text + ", active " + integer + " NOT NULL DEFAULT 1)");
            sqlite.run("CREATE TABLE IF NOT EXISTS staff_alts ("
                    + "uuid " + text + " NOT NULL, name " + text + " NOT NULL, ip " + text + " NOT NULL, "
                    + "last_seen " + integer + " NOT NULL, PRIMARY KEY (uuid, ip))");
            sqlite.run("CREATE TABLE IF NOT EXISTS staff_snapshots ("
                    + "id " + id + ", uuid " + text + " NOT NULL, category " + text + " NOT NULL, "
                    + "created " + integer + " NOT NULL, contents " + longText + " NOT NULL)");
            sqlite.run("CREATE TABLE IF NOT EXISTS staff_state ("
                    + "uuid " + text + " PRIMARY KEY, vanished " + integer + " NOT NULL DEFAULT 0, "
                    + "frozen " + integer + " NOT NULL DEFAULT 0)");
            sqlite.run("CREATE TABLE IF NOT EXISTS staff_audit ("
                    + "id " + id + ", uuid " + text + " NOT NULL, name " + text + " NOT NULL, "
                    + "command " + longText + " NOT NULL, created " + integer + " NOT NULL)");
            sqlite.run("CREATE TABLE IF NOT EXISTS staff_mode ("
                    + "uuid " + text + " PRIMARY KEY, contents " + longText + " NOT NULL, armor " + longText + " NOT NULL, "
                    + "extra " + longText + " NOT NULL, gamemode " + text + " NOT NULL, xp DOUBLE NOT NULL, "
                    + "level " + integer + " NOT NULL)");
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create staff tables", ex);
        }
        for (String name : COMMANDS) registerCommand(name, this);
        registerListener(this);
        int minutes = Math.max(1, config.getInt("snapshot-interval-minutes", 5));
        snapshotTask = Bukkit.getScheduler().runTaskTimer(plugin, this::autoSnapshot, minutes * 1200L, minutes * 1200L);
        for (Player player : Bukkit.getOnlinePlayers()) hideVanished(player);
    }

    @Override
    public void disable() {
        if (snapshotTask != null) snapshotTask.cancel();
        for (UUID uuid : new ArrayList<>(staffMode)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) exitStaff(player, false);
        }
        staffMode.clear();
        vanished.clear();
        frozen.clear();
        staffChat.clear();
        screenshare.clear();
        if (remoteSql && sqlite != null) sqlite.close();
        cleanup();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        return switch (name) {
            case "staff" -> help(sender);
            case "staffmode" -> staffMode(sender);
            case "vanish" -> vanish(sender);
            case "freeze" -> freeze(sender, args);
            case "unfreeze" -> unfreeze(sender, args);
            case "stafflist" -> staffList(sender);
            case "randomtp" -> randomTp(sender);
            case "staffchat" -> staffChat(sender);
            case "gmc" -> gamemode(sender, GameMode.CREATIVE, args);
            case "gms" -> gamemode(sender, GameMode.SURVIVAL, args);
            case "gmsp" -> gamemode(sender, GameMode.SPECTATOR, args);
            case "gma" -> gamemode(sender, GameMode.ADVENTURE, args);
            case "punish" -> punish(sender, args);
            case "ban" -> punishCmd(sender, args, "ban", false);
            case "mute" -> punishCmd(sender, args, "mute", false);
            case "kick" -> kick(sender, args);
            case "offend" -> offend(sender, args);
            case "banip" -> punishCmd(sender, args, "banip", true);
            case "unban" -> lift(sender, args, "ban");
            case "unbanip" -> unbanIp(sender, args);
            case "unmute" -> lift(sender, args, "mute");
            case "pardon" -> pardon(sender, args);
            case "alts" -> alts(sender, args);
            case "screenshare" -> screenshare(sender, args);
            case "invrollback" -> rollback(sender, args);
            case "revokepunishment" -> revoke(sender);
            case "requeststaff" -> request(sender);
            case "staffgoto" -> staffGoto(sender, args);
            default -> true;
        };
    }

    private boolean help(CommandSender sender) {
        if (!staff(sender, "shardedcore.staff")) return true;
        send(sender, "staff-header");
        for (String line : List.of(
                "&f- /staffmode (/sfmode) &7– Toggle staff mode",
                "&f- /vanish &7– Toggle vanish",
                "&f- /freeze <player> &7– Freeze a player",
                "&f- /unfreeze <player> &7– Unfreeze a player",
                "&f- /stafflist &7– List online staff",
                "&f- /randomtp &7– Teleport to random player",
                "&f- /staffchat (/sc) &7– Toggle staff chat",
                "&f- /gmc /gms /gmsp /gma [player] &7– Change gamemode",
                "&f- /punish <player> &7– Open punish menu",
                "&f- /ban <player> [reason] [duration] &7– Ban a player",
                "&f- /mute <player> [reason] [duration] &7– Mute a player",
                "&f- /kick <player> [reason] &7– Kick a player",
                "&f- /offend <player> &7– Ban repeat offender",
                "&f- /banip <player> [reason] &7– IP ban a player",
                "&f- /unban <player> &7– Unban a player",
                "&f- /unbanip <ip|player|list> &7– Remove an IP ban",
                "&f- /unmute <player> &7– Unmute a player",
                "&f- /pardon <player> &7– Unban + unmute",
                "&f- /alts [player] &7– Show linked alts",
                "&f- /screenshare (/ss) <player> &7– Screenshare a player",
                "&f- /invrollback <player> &7– Inventory rollback",
                "&f- /requeststaff &7– Request staff",
                "&f- /revokepunishment &7– Mass revoke punishments"
        )) {
            sender.sendMessage(ColorUtil.parse(line));
        }
        return true;
    }

    public boolean inStaffMode(Player player) {
        return player != null && staffMode.contains(player.getUniqueId());
    }

    public boolean vanished(Player player) {
        return player != null && vanished.contains(player.getUniqueId());
    }

    private boolean staffMode(CommandSender sender) {
        if (!(sender instanceof Player player) || !staff(player, "shardedcore.staff.mode")) return true;
        if (staffMode.remove(player.getUniqueId())) {
            exitStaff(player, true);
            send(player, "staffmode-off");
            return true;
        }
        enterStaff(player);
        send(player, "staffmode-on");
        return true;
    }

    private void enterStaff(Player player) {
        saveMode(player);
        staffMode.add(player.getUniqueId());
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        player.setGameMode(GameMode.CREATIVE);
        player.setAllowFlight(true);
        player.setFlying(true);
        if (config.getBoolean("staffmode.vanish-on-enter", true)) setVanish(player, true);
        else hideFromPlayers(player);
        String disable = cfg("staffmode.disable-eglow-command", "eglow:eglow disable");
        if (disable != null && !disable.isBlank()) player.performCommand(disable.startsWith("/") ? disable.substring(1) : disable);
        giveStaffItems(player);
    }

    private void giveStaffItems(Player player) {
        PlayerInventory inventory = player.getInventory();
        boolean hidden = vanished.contains(player.getUniqueId());
        setStaffItem(inventory, "vanish", hidden);
        setStaffItem(inventory, "freeze", false);
        setStaffItem(inventory, "punish", false);
        setStaffItem(inventory, "randomtp", false);
        setStaffItem(inventory, "exit", false);
    }

    private void setStaffItem(PlayerInventory inventory, String id, boolean vanishedState) {
        int slot = staffItemSlot(id);
        if (slot < 0 || slot > 8) return;
        inventory.setItem(slot, staffItem(id, vanishedState));
    }

    private int staffItemSlot(String id) {
        ConfigurationSection section = config.getConfigurationSection("staffmode.items." + id);
        if (section != null) return section.getInt("slot", fallbackSlot(id));
        return config.getInt("staffmode.items." + id + "-slot", fallbackSlot(id));
    }

    private static int fallbackSlot(String id) {
        return switch (id) {
            case "vanish" -> 0;
            case "freeze" -> 1;
            case "punish" -> 2;
            case "randomtp" -> 3;
            case "exit" -> 8;
            default -> 4;
        };
    }

    private ItemStack staffItem(String id, boolean vanishedState) {
        ConfigurationSection section = config.getConfigurationSection("staffmode.items." + id);
        String namePath = vanishedState && "vanish".equals(id) ? "vanished-name" : "name";
        String lorePath = vanishedState && "vanish".equals(id) ? "vanished-lore" : "lore";
        String materialPath = vanishedState && "vanish".equals(id) ? "vanished-material" : "material";
        String rawMaterial = section == null ? defaultMaterial(id, vanishedState)
                : section.getString(materialPath, section.getString("material", defaultMaterial(id, vanishedState)));
        Material fallback = Material.matchMaterial(defaultMaterial(id, vanishedState));
        Material material = Sounds.material(rawMaterial, fallback == null ? Material.STONE : fallback);
        String name = section == null ? defaultName(id, vanishedState)
                : section.getString(namePath, section.getString("name", defaultName(id, vanishedState)));
        List<String> lore = section == null || section.getStringList(lorePath).isEmpty()
                ? (section == null ? defaultLore(id) : (section.getStringList("lore").isEmpty() ? defaultLore(id) : section.getStringList("lore")))
                : section.getStringList(lorePath);
        ItemStack item = Items.named(material == null ? Material.STONE : material, name, lore);
        item.editMeta(meta -> meta.getPersistentDataContainer().set(toolKey, PersistentDataType.STRING, id));
        return item;
    }

    private static String defaultMaterial(String id, boolean vanishedState) {
        return switch (id) {
            case "vanish" -> vanishedState ? "GRAY_DYE" : "LIME_DYE";
            case "freeze" -> "PACKED_ICE";
            case "punish" -> "NETHERITE_AXE";
            case "randomtp" -> "COMPASS";
            case "exit" -> "BARRIER";
            default -> "STONE";
        };
    }

    private static String defaultName(String id, boolean vanishedState) {
        return switch (id) {
            case "vanish" -> vanishedState ? "&#8B8B8B&lVANISHED" : "&#FF8300&lVANISH";
            case "freeze" -> "&#00C1FF&lFREEZE";
            case "punish" -> "&#FF0000&lPUNISH";
            case "randomtp" -> "&#00A2FF&lRANDOM TP";
            case "exit" -> "&#FF0000&lEXIT STAFF";
            default -> "&f&lITEM";
        };
    }

    private static List<String> defaultLore(String id) {
        return switch (id) {
            case "vanish" -> List.of("&8Description", "", "&#FF8300Information:", "&#FF8300| &fToggle vanish", "", GuiButtons.clickFooter("To Toggle"));
            case "freeze" -> List.of("&8Description", "", "&#00C1FFInformation:", "&#00C1FF| &fRight click a player", "&#00C1FF| &fto freeze them", "", GuiButtons.clickFooter("To Use"));
            case "punish" -> List.of("&8Description", "", "&#FF0000Information:", "&#FF0000| &fHit a player", "&#FF0000| &fto open punish", "", GuiButtons.clickFooter("To Use"));
            case "randomtp" -> List.of("&8Description", "", "&#00A2FFInformation:", "&#00A2FF| &fTeleport to a", "&#00A2FF| &frandom player", "", GuiButtons.clickFooter("To Teleport"));
            case "exit" -> List.of("&8Description", "", "&#FF0000Information:", "&#FF0000| &fLeave staff mode", "", GuiButtons.clickFooter("To Exit"));
            default -> List.of();
        };
    }

    private String toolId(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return "";
        String id = item.getItemMeta().getPersistentDataContainer().get(toolKey, PersistentDataType.STRING);
        return id == null ? "" : id;
    }

    private boolean tooSoon(Player player) {
        long now = System.currentTimeMillis();
        Long last = toolClicks.put(player.getUniqueId(), now);
        return last != null && now - last < 250L;
    }

    private void exitStaff(Player player, boolean restore) {
        staffMode.remove(player.getUniqueId());
        setVanish(player, false);
        if (restore) restoreMode(player);
    }

    private void saveMode(Player player) {
        PlayerInventory inventory = player.getInventory();
        try {
            sqlite.execute("INSERT INTO staff_mode (uuid, contents, armor, extra, gamemode, xp, level) VALUES (?, ?, ?, ?, ?, ?, ?)"
                    + (sqlite.mysql()
                    ? " ON DUPLICATE KEY UPDATE contents = VALUES(contents), armor = VALUES(armor), extra = VALUES(extra), gamemode = VALUES(gamemode), xp = VALUES(xp), level = VALUES(level)"
                    : " ON CONFLICT(uuid) DO UPDATE SET contents = excluded.contents, armor = excluded.armor, extra = excluded.extra, gamemode = excluded.gamemode, xp = excluded.xp, level = excluded.level"),
                    player.getUniqueId().toString(), serializeInv(inventory.getStorageContents()),
                    serializeInv(inventory.getArmorContents()),
                    serializeInv(new ItemStack[]{inventory.getItemInOffHand()}),
                    player.getGameMode().name(), (double) player.getExp(), player.getLevel());
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to save staff inventory", ex);
        }
    }

    private void restoreMode(Player player) {
        try {
            sqlite.query("SELECT * FROM staff_mode WHERE uuid = ?", rs -> {
                try {
                    if (!rs.next()) return null;
                    PlayerInventory inventory = player.getInventory();
                    inventory.clear();
                    unpack(inventory, rs.getString("contents"));
                    ItemStack[] armor = deserializeInv(rs.getString("armor"));
                    if (armor.length >= 4) inventory.setArmorContents(new ItemStack[]{armor[0], armor[1], armor[2], armor[3]});
                    ItemStack[] extra = deserializeInv(rs.getString("extra"));
                    if (extra.length > 0) inventory.setItemInOffHand(extra[0]);
                    try {
                        player.setGameMode(GameMode.valueOf(rs.getString("gamemode")));
                    } catch (IllegalArgumentException ignored) {
                    }
                    player.setExp((float) rs.getDouble("xp"));
                    player.setLevel(rs.getInt("level"));
                } catch (SQLException ignored) {
                }
                return null;
            }, player.getUniqueId().toString());
            sqlite.execute("DELETE FROM staff_mode WHERE uuid = ?", player.getUniqueId().toString());
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to restore staff inventory", ex);
        }
    }

    private boolean vanish(CommandSender sender) {
        if (!(sender instanceof Player player) || !staff(player, "shardedcore.staff.vanish")) return true;
        setVanish(player, !vanished.contains(player.getUniqueId()));
        send(player, vanished.contains(player.getUniqueId()) ? "vanish-on" : "vanish-off");
        return true;
    }

    private void setVanish(Player player, boolean on) {
        if (on) {
            vanished.add(player.getUniqueId());
            player.setInvisible(true);
            player.setCollidable(false);
            try {
                player.setVisibleByDefault(false);
            } catch (Throwable ignored) {
            }
            if (config.getBoolean("vanish.fly", true) && player.getGameMode() != GameMode.SPECTATOR) {
                player.setAllowFlight(true);
                player.setFlying(true);
            }
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other.equals(player)) continue;
                if (other.hasPermission(cfg("see-vanished", "shardedcore.staff.seevanished"))) {
                    other.showPlayer(plugin, player);
                } else {
                    other.hidePlayer(plugin, player);
                }
            }
        } else {
            vanished.remove(player.getUniqueId());
            player.setInvisible(false);
            player.setCollidable(true);
            try {
                player.setVisibleByDefault(true);
            } catch (Throwable ignored) {
            }
            for (Player other : Bukkit.getOnlinePlayers()) other.showPlayer(plugin, player);
            if (staffMode.contains(player.getUniqueId())) hideFromPlayers(player);
        }
        saveState(player.getUniqueId(), "vanished", on);
        if (staffMode.contains(player.getUniqueId())) {
            setStaffItem(player.getInventory(), "vanish", on);
        }
    }

    private void saveState(UUID uuid, String column, boolean value) {
        try {
            sqlite.execute("INSERT INTO staff_state (uuid, vanished, frozen) VALUES (?, ?, ?) "
                            + (sqlite.mysql()
                            ? "ON DUPLICATE KEY UPDATE " + column + " = VALUES(" + column + ")"
                            : "ON CONFLICT(uuid) DO UPDATE SET " + column + " = excluded." + column),
                    uuid.toString(), "vanished".equals(column) && value ? 1 : 0, "frozen".equals(column) && value ? 1 : 0);
            sqlite.execute("UPDATE staff_state SET " + column + " = ? WHERE uuid = ?", value ? 1 : 0, uuid.toString());
        } catch (SQLException ignored) {
        }
    }

    private boolean freeze(CommandSender sender, String[] args) {
        if (!staff(sender, "shardedcore.staff.freeze")) return true;
        Player target = player(sender, args, 0);
        if (target == null) return true;
        if (frozen.contains(target.getUniqueId())) {
            send(sender, "freeze-on", "player", target.getName());
            return true;
        }
        frozen.add(target.getUniqueId());
        saveState(target.getUniqueId(), "frozen", true);
        send(sender, "freeze-on", "player", target.getName());
        send(target, "frozen");
        return true;
    }

    private boolean unfreeze(CommandSender sender, String[] args) {
        if (!staff(sender, "shardedcore.staff.freeze")) return true;
        Player target = player(sender, args, 0);
        if (target == null) return true;
        frozen.remove(target.getUniqueId());
        saveState(target.getUniqueId(), "frozen", false);
        send(sender, "freeze-off", "player", target.getName());
        send(target, "unfrozen");
        return true;
    }

    private boolean staffList(CommandSender sender) {
        if (!staff(sender, "shardedcore.staff.list")) return true;
        List<Player> list = new ArrayList<>();
        String perm = cfg("stafflist-permission", "shardedcore.staff.mode");
        boolean see = sender.hasPermission(cfg("see-vanished", "shardedcore.staff.seevanished"));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission(perm) || !player.hasPermission("shardedcore.staff.mode")) continue;
            if (vanished.contains(player.getUniqueId()) && !see) continue;
            list.add(player);
        }
        if (list.isEmpty()) {
            send(sender, "stafflist-empty");
            return true;
        }
        send(sender, "stafflist-header", "count", String.valueOf(list.size()));
        for (Player player : list) {
            send(sender, "stafflist-entry", "player", player.getName(),
                    "status", vanished.contains(player.getUniqueId()) ? "vanished" : "visible");
        }
        return true;
    }

    private boolean randomTp(CommandSender sender) {
        if (!(sender instanceof Player player) || !staff(player, "shardedcore.staff.randomtp")) return true;
        List<Player> pool = new ArrayList<>();
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player) || vanished.contains(other.getUniqueId())) continue;
            pool.add(other);
        }
        if (pool.isEmpty()) {
            send(player, "randomtp-none");
            return true;
        }
        Player target = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        player.teleportAsync(target.getLocation());
        send(player, "randomtp", "player", target.getName());
        return true;
    }

    private boolean staffChat(CommandSender sender) {
        if (!(sender instanceof Player player) || !staff(player, "shardedcore.staff.chat")) return true;
        if (staffChat.remove(player.getUniqueId())) send(player, "staffchat-off");
        else {
            staffChat.add(player.getUniqueId());
            send(player, "staffchat-on");
        }
        return true;
    }

    private boolean gamemode(CommandSender sender, GameMode mode, String[] args) {
        if (!staff(sender, "shardedcore.staff.gamemode")) return true;
        Player target = args.length >= 1 ? Bukkit.getPlayerExact(args[0]) : (sender instanceof Player player ? player : null);
        if (target == null) {
            send(sender, "player-not-found");
            return true;
        }
        target.setGameMode(mode);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (target.isOnline() && target.getGameMode() != mode) target.setGameMode(mode);
        }, 1L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (target.isOnline() && target.getGameMode() != mode) target.setGameMode(mode);
        }, 3L);
        send(sender, "gamemode", "mode", mode.name().toLowerCase(Locale.ROOT));
        return true;
    }

    private boolean punish(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) || !staff(player, "shardedcore.staff.punish")) return true;
        Player target = player(sender, args, 0);
        if (target == null) return true;
        openPunish(player, target);
        return true;
    }

    private void openPunish(Player staff, Player target) {
        ConfigurationSection punish = config.getConfigurationSection("punish");
        int rows = punish == null ? 3 : punish.getInt("rows", 3);
        Menus.Menu menu = plugin.menus().create(staff,
                punish == null ? cfg("messages.punish-title", "Punish") : punish.getString("title", cfg("messages.punish-title", "Punish")),
                rows);
        placePunishButton(menu, staff, target, "ban", 11, Material.IRON_BARS, "&#FF0000&lBAN");
        placePunishButton(menu, staff, target, "mute", 13, Material.BOOK, "&#FFBA00&lMUTE");
        placePunishButton(menu, staff, target, "kick", 15, Material.LEATHER_BOOTS, "&#FF8300&lKICK");
        int closeSlot = punish == null ? Math.max(0, menu.inventory().getSize() - 1)
                : punish.getInt("close.slot", Math.max(0, menu.inventory().getSize() - 1));
        if (closeSlot >= 0 && closeSlot < menu.inventory().getSize()) {
            menu.set(closeSlot, GuiButtons.close(staff), event -> {
                event.setCancelled(true);
                GuiButtons.play(staff, "click");
                staff.closeInventory();
            });
        }
        GuiButtons.border(menu);
        plugin.menus().open(staff, menu);
        GuiButtons.play(staff, "open");
    }

    private void placePunishButton(Menus.Menu menu, Player staff, Player target, String type, int fallbackSlot,
                                   Material fallbackMaterial, String fallbackName) {
        ConfigurationSection section = config.getConfigurationSection("punish." + type);
        int slot = section == null ? fallbackSlot : section.getInt("slot", fallbackSlot);
        ItemStack item = section == null
                ? Items.named(fallbackMaterial, fallbackName, List.of("&8Description", "",
                "&7Click to choose a " + type + " reason for &f" + target.getName()))
                : Items.fromSection(section, staff, "player", target.getName());
        menu.set(slot, item, event -> {
            event.setCancelled(true);
            GuiButtons.play(staff, "click");
            openReasons(staff, target, type);
        });
    }

    private void openReasons(Player staff, Player target, String type) {
        String path = type.equals("mute") ? "mute-reasons" : type.equals("kick") ? "kick-reasons" : "reasons";
        List<ReasonChoice> choices = loadReasons(path, type);
        if (choices.isEmpty() && type.equals("ban")) choices = loadReasons("ban-reasons", type);
        if (choices.isEmpty()) {
            choices.add(new ReasonChoice("Unfair Modifications", List.of(type.equals("kick") ? "kick" : "7d"), null));
        }
        int rows = Math.max(3, Math.min(6, (choices.size() + 16) / 7 + 2));
        Menus.Menu menu = plugin.menus().create(staff, Text.apply(cfg("messages.reason-title", "%type% Reasons"),
                "type", type.substring(0, 1).toUpperCase(Locale.ROOT) + type.substring(1), "player", target.getName()), rows);
        int[] slots = GuiButtons.inner(rows);
        int index = 0;
        for (ReasonChoice choice : choices) {
            if (index >= slots.length) break;
            String durationLabel = choice.durations.size() == 1 ? choice.durations.get(0) : choice.durations.size() + " options";
            ItemStack item;
            if (choice.section != null && !choice.section.getStringList("lore").isEmpty()) {
                item = Items.fromSection(choice.section, staff, "player", target.getName(), "reason", choice.reason,
                        "duration", durationLabel, "type", type);
            } else {
                String color = type.equals("mute") ? "&#FFBA00" : type.equals("kick") ? "&#FF8300" : "&#FF0000";
                Material material = type.equals("mute") ? Material.PAPER : type.equals("kick") ? Material.LEATHER_BOOTS : Material.IRON_BARS;
                if (choice.section != null) {
                    material = Sounds.material(choice.section.getString("material", material.name()), material);
                }
                item = Items.named(material, color + "&l" + choice.reason,
                        List.of("&8Description", "",
                                color + "Information:",
                                color + "| &fPlayer: &f" + target.getName(),
                                color + "| &fDuration: &f" + durationLabel,
                                "",
                                GuiButtons.clickFooter(choice.durations.size() > 1 ? "To Open"
                                        : "To " + type.substring(0, 1).toUpperCase(Locale.ROOT) + type.substring(1))));
            }
            menu.set(slots[index++], item, event -> {
                event.setCancelled(true);
                GuiButtons.play(staff, "click");
                if (choice.durations.size() > 1) {
                    openDurations(staff, target, type, choice);
                    return;
                }
                applyReason(staff, target, type, choice.reason, choice.durations.get(0));
            });
        }
        GuiButtons.placeBack(menu, staff, Math.max(0, menu.inventory().getSize() - 1),
                () -> openPunish(staff, target));
        GuiButtons.border(menu);
        plugin.menus().open(staff, menu);
    }

    private void openDurations(Player staff, Player target, String type, ReasonChoice choice) {
        int rows = Math.max(3, Math.min(6, (choice.durations.size() + 16) / 7 + 2));
        Menus.Menu menu = plugin.menus().create(staff, choice.reason, rows);
        int[] slots = GuiButtons.inner(rows);
        int index = 0;
        String color = type.equals("mute") ? "&#FFBA00" : "&#FF0000";
        for (String duration : choice.durations) {
            if (index >= slots.length) break;
            menu.set(slots[index++], Items.named(Material.CLOCK, color + "&l" + duration,
                    List.of("&8Description", "",
                            color + "Information:",
                            color + "| &f" + choice.reason,
                            color + "| &fPlayer: " + target.getName(),
                            "",
                            GuiButtons.clickFooter("To Apply"))), event -> {
                event.setCancelled(true);
                GuiButtons.play(staff, "click");
                applyReason(staff, target, type, choice.reason, duration);
            });
        }
        GuiButtons.placeBack(menu, staff, Math.max(0, menu.inventory().getSize() - 1),
                () -> openReasons(staff, target, type));
        GuiButtons.border(menu);
        plugin.menus().open(staff, menu);
    }

    private void applyReason(Player staff, Player target, String type, String reason, String duration) {
        if (type.equals("kick")) {
            Player live = target.getPlayer();
            if (live != null) live.kick(ColorUtil.parse("&c" + reason));
            send(staff, "kicked", "player", target.getName(), "reason", reason);
            broadcast("Kick", target.getName(), staff.getName(), reason);
        } else {
            apply(staff, target, type, reason, duration, false);
        }
        staff.closeInventory();
    }

    private List<ReasonChoice> loadReasons(String path, String type) {
        List<ReasonChoice> list = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) return list;
        for (String key : section.getKeys(false)) {
            ConfigurationSection nested = section.getConfigurationSection(key);
            if (nested != null) {
                String reason = nested.getString("reason", nested.getString("name", key));
                List<String> durations = nested.getStringList("durations");
                if (durations.isEmpty() && nested.isList("duration")) durations = nested.getStringList("duration");
                if (durations.isEmpty()) {
                    String one = nested.getString("duration", type.equals("kick") ? "kick" : "7d");
                    durations = List.of(one);
                }
                list.add(new ReasonChoice(ColorUtil.strip(reason), durations, nested));
                continue;
            }
            if (section.isList(key)) {
                list.add(new ReasonChoice(key, section.getStringList(key), null));
            } else {
                list.add(new ReasonChoice(key, List.of(section.getString(key, type.equals("kick") ? "kick" : "7d")), null));
            }
        }
        return list;
    }

    private boolean punishCmd(CommandSender sender, String[] args, String type, boolean ip) {
        if (!staff(sender, "shardedcore.staff." + type)) return true;
        if (args.length < 1) {
            send(sender, "usage-player", "command", type);
            return true;
        }
        OfflinePlayer target = Players.offline(args[0]);
        if (target == null) {
            send(sender, "player-not-found");
            return true;
        }
        String reason = args.length >= 2 ? args[1] : cfg("default-reason", "Unfair Modifications");
        String duration = args.length >= 3 ? args[2] : (ip ? "permanent" : "7d");
        if (args.length == 2 && looksDuration(args[1])) {
            duration = args[1];
            reason = cfg("default-reason", "Unfair Modifications");
        }
        apply(sender, target, type, reason, duration, ip);
        return true;
    }

    private boolean kick(CommandSender sender, String[] args) {
        if (!staff(sender, "shardedcore.staff.kick")) return true;
        Player target = player(sender, args, 0);
        if (target == null) return true;
        String reason = args.length >= 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "Kicked";
        target.kick(ColorUtil.parse("&c" + reason));
        send(sender, "kicked", "player", target.getName(), "reason", reason);
        broadcast("Kick", target.getName(), sender.getName(), reason);
        return true;
    }

    private boolean offend(CommandSender sender, String[] args) {
        if (!staff(sender, "shardedcore.staff.offend")) return true;
        if (args.length < 1) {
            send(sender, "usage-player", "command", "offend");
            return true;
        }
        OfflinePlayer target = Players.offline(args[0]);
        if (target == null) {
            send(sender, "player-not-found");
            return true;
        }
        apply(sender, target, "ban", cfg("offend-reason", "Ban-Evasion"), cfg("offend-duration", "permanent"), false);
        return true;
    }

    private void apply(CommandSender staff, OfflinePlayer target, String type, String reason, String duration, boolean ip) {
        long expires = expires(duration);
        String address = ip(target);
        try {
            sqlite.execute("""
                    INSERT INTO staff_punishments (uuid, name, type, reason, staff, created, expires, ip, active)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1)
                    """, target.getUniqueId().toString(), Players.name(target), type, reason, staff.getName(),
                    System.currentTimeMillis(), expires, address);
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to save punishment", ex);
        }
        String time = expires <= 0 ? "permanent" : Amounts.duration(expires - System.currentTimeMillis(), "d", "h", "m", "s", 2);
        send(staff, type.equals("mute") ? "muted" : (ip ? "ip-banned" : "banned"),
                "player", Players.name(target), "reason", reason, "time", time);
        broadcast(type.substring(0, 1).toUpperCase(Locale.ROOT) + type.substring(1), Players.name(target), staff.getName(), reason);
        Player online = target.getPlayer();
        if (online != null && (type.equals("ban") || type.equals("banip"))) {
            online.kick(ColorUtil.parse(String.join("\n", Text.applyList(new ArrayList<>(config.getStringList("ban-screen")),
                    "staff", staff.getName(), "reason", reason, "time", time, "discord", cfg("discord", "discord.gg/shardedmc")))));
        }
        if (online != null && type.equals("mute")) send(online, "muted-chat", "reason", reason);
    }

    private boolean lift(CommandSender sender, String[] args, String type) {
        if (!staff(sender, "shardedcore.staff." + (type.equals("ban") ? "unban" : "unmute"))) return true;
        if (args.length < 1) {
            send(sender, "usage-player", "command", type.equals("ban") ? "unban" : "unmute");
            return true;
        }
        OfflinePlayer target = Players.offline(args[0]);
        if (target == null) {
            send(sender, "player-not-found");
            return true;
        }
        deactivate(target.getUniqueId(), type);
        send(sender, type.equals("ban") ? "unbanned" : "unmuted", "player", Players.name(target));
        return true;
    }

    private boolean pardon(CommandSender sender, String[] args) {
        if (!staff(sender, "shardedcore.staff.pardon")) return true;
        if (args.length < 1) {
            send(sender, "usage-player", "command", "pardon");
            return true;
        }
        OfflinePlayer target = Players.offline(args[0]);
        if (target == null) {
            send(sender, "player-not-found");
            return true;
        }
        deactivate(target.getUniqueId(), "ban");
        deactivate(target.getUniqueId(), "mute");
        deactivate(target.getUniqueId(), "banip");
        send(sender, "pardoned", "player", Players.name(target));
        return true;
    }

    private boolean unbanIp(CommandSender sender, String[] args) {
        if (!staff(sender, "shardedcore.staff.unbanip")) return true;
        if (args.length < 1) {
            send(sender, "usage-unbanip");
            return true;
        }
        if (args[0].equalsIgnoreCase("list")) {
            List<String> ips = activeIps();
            send(sender, "unbanip-list", "count", String.valueOf(ips.size()));
            for (String ip : ips) sender.sendMessage(ColorUtil.parse("&7- &f" + ip));
            return true;
        }
        String token = args[0];
        OfflinePlayer player = Players.offline(token);
        String ip = token.contains(".") ? token : (player == null ? token : ip(player));
        try {
            int changed = sqlite.query("SELECT changes() FROM (UPDATE staff_punishments SET active = 0 WHERE active = 1 AND type = 'banip' AND (ip = ? OR name = ? OR uuid = ?))",
                    rs -> 1, ip, token, player == null ? token : player.getUniqueId().toString());
            sqlite.execute("UPDATE staff_punishments SET active = 0 WHERE active = 1 AND type = 'banip' AND (ip = ? OR name = ?)", ip, token);
            send(sender, "unbanip-done", "ip", ip);
        } catch (SQLException ex) {
            send(sender, "unbanip-missing", "ip", ip);
        }
        return true;
    }

    private boolean alts(CommandSender sender, String[] args) {
        if (!staff(sender, "shardedcore.staff.alts")) return true;
        if (!(sender instanceof Player) && args.length < 1) {
            send(sender, "usage-player", "command", "alts");
            return true;
        }
        OfflinePlayer target = args.length >= 1 ? Players.offline(args[0]) : (sender instanceof Player player ? player : null);
        if (target == null) {
            send(sender, "player-not-found");
            return true;
        }
        String address = ip(target);
        List<String> names = new ArrayList<>();
        try {
            sqlite.query("SELECT DISTINCT name, uuid FROM staff_alts WHERE ip = ?", rs -> {
                try {
                    while (rs.next()) names.add(rs.getString("name") + ":" + rs.getString("uuid"));
                } catch (SQLException ignored) {
                }
                return null;
            }, address);
        } catch (SQLException ignored) {
        }
        if (names.isEmpty()) {
            send(sender, "alts-none");
            return true;
        }
        if (sender instanceof Player viewer) {
            openAlts(viewer, target, names);
            return true;
        }
        send(sender, "alts-header", "player", Players.name(target));
        for (String row : names) {
            String[] parts = row.split(":", 2);
            UUID uuid = parts.length == 2 ? UUID.fromString(parts[1]) : target.getUniqueId();
            send(sender, "alts-entry", "name", parts[0], "status", Bukkit.getPlayer(uuid) != null ? "online" : "offline");
        }
        return true;
    }

    private void openAlts(Player staff, OfflinePlayer target, List<String> names) {
        Menus.Menu menu = plugin.menus().create(staff, Text.apply(cfg("messages.alts-title", "%players% Alts"),
                "players", String.valueOf(names.size()), "player", Players.name(target)), 4);
        int slot = 10;
        for (String row : names) {
            if (slot % 9 == 8) slot += 2;
            if (slot >= 35) break;
            String[] parts = row.split(":", 2);
            String name = parts[0];
            UUID uuid = parts.length == 2 ? UUID.fromString(parts[1]) : target.getUniqueId();
            Player online = Bukkit.getPlayer(uuid);
            ItemStack head = online != null
                    ? Items.head(online, "&#00A2FF&l" + name.toUpperCase(Locale.ROOT),
                    List.of("&8Description", "", "&7Left click to teleport", "&7Right click to punish"))
                    : Items.named(Material.PLAYER_HEAD, "&#00A2FF&l" + name.toUpperCase(Locale.ROOT),
                    List.of("&7Offline"));
            menu.set(slot, head, event -> {
                event.setCancelled(true);
                Player live = Bukkit.getPlayer(uuid);
                if (event.isRightClick()) {
                    if (live != null) openPunish(staff, live);
                    return;
                }
                if (live != null) {
                    staff.teleport(live);
                    send(staff, "randomtp", "player", live.getName());
                } else {
                    send(staff, "player-not-found");
                }
            });
            slot++;
        }
        GuiButtons.border(menu);
        plugin.menus().open(staff, menu);
    }

    private boolean screenshare(CommandSender sender, String[] args) {
        if (!staff(sender, "shardedcore.staff.screenshare")) return true;
        Player target = player(sender, args, 0);
        if (target == null) return true;
        if (screenshare.remove(target.getUniqueId()) != null) {
            frozen.remove(target.getUniqueId());
            send(sender, "screenshare-done", "player", target.getName());
            return true;
        }
        long until = System.currentTimeMillis() + config.getInt("screenshare-seconds", 120) * 1000L;
        screenshare.put(target.getUniqueId(), until);
        frozen.add(target.getUniqueId());
        send(sender, "screenshare-start", "player", target.getName());
        send(target, "screenshare-target");
        target.showTitle(Title.title(
                ColorUtil.parse(cfg("screenshare.title", "&#FF0000&lSCREENSHARE")),
                ColorUtil.parse(cfg("screenshare.subtitle", "&fPlease Do not log out")),
                Title.Times.times(java.time.Duration.ofMillis(200), java.time.Duration.ofSeconds(4), java.time.Duration.ofMillis(200))));
        target.sendActionBar(ColorUtil.parse(cfg("screenshare.actionbar", "&fJoin #Screenshare @ discord.gg/shardedmc")));
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!target.isOnline() || !screenshare.containsKey(target.getUniqueId())) {
                task.cancel();
                return;
            }
            target.sendActionBar(ColorUtil.parse(cfg("screenshare.actionbar", "&fJoin #Screenshare @ discord.gg/shardedmc")));
        }, 20L, 40L);
        return true;
    }

    private boolean rollback(CommandSender sender, String[] args) {
        if (!(sender instanceof Player staff) || !staff(staff, "shardedcore.staff.invrollback")) return true;
        if (args.length < 1) {
            send(staff, "rollback-usage");
            return true;
        }
        OfflinePlayer target = Players.offline(args[0]);
        if (target == null) {
            send(staff, "player-not-found");
            return true;
        }
        List<Snap> snaps = snapshots(target.getUniqueId());
        if (snaps.isEmpty()) {
            send(staff, "rollback-empty", "player", Players.name(target));
            return true;
        }
        Menus.Menu menu = plugin.menus().create(staff, Text.apply(cfg("messages.rollback-title", "☀ Staff ☀ Previewing | Rollback"),
                "player", Players.name(target)), 4);
        int slot = 10;
        for (Snap snap : snaps) {
            if (slot % 9 == 8) slot += 2;
            if (slot >= 35) break;
            menu.set(slot, Items.named(Material.CHEST, "&#00A2FF&l" + snap.category.toUpperCase(Locale.ROOT),
                    List.of("&7" + Amounts.duration(System.currentTimeMillis() - snap.created, "d", "h", "m", "s", 2) + " ago",
                            "&8Click to restore")), event -> {
                event.setCancelled(true);
                Player online = target.getPlayer();
                if (online == null) {
                    send(staff, "player-not-found");
                    return;
                }
                applySnap(online, snap.contents);
                send(staff, "rollback-restored", "player", online.getName());
                staff.closeInventory();
            });
            slot++;
        }
        menu.set(GuiButtons.slot("close", 31), GuiButtons.close(staff), event -> {
            event.setCancelled(true);
            staff.closeInventory();
        });
        GuiButtons.border(menu);
        plugin.menus().open(staff, menu);
        return true;
    }

    private boolean revoke(CommandSender sender) {
        if (!(sender instanceof Player player) || !staff(player, "shardedcore.staff.revoke")) return true;
        Menus.Menu menu = plugin.menus().create(player, cfg("messages.revoke-title", "Revoke Punishments"), 4);
        int slot = 10;
        List<String> reasons = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection("reasons");
        if (section != null) reasons.addAll(section.getKeys(false));
        if (reasons.stream().noneMatch(reason -> reason.equalsIgnoreCase("doxxing"))) reasons.add("Doxxing");
        reasons.add("All");
        for (String reason : reasons) {
            if (slot % 9 == 8) slot += 2;
            if (slot >= 35) break;
            menu.set(slot, Items.named(Material.PAPER, "&#FF0000&l" + reason.toUpperCase(Locale.ROOT),
                    List.of("&7Revoke matching punishments")), event -> {
                event.setCancelled(true);
                int count = revokeReason(reason);
                send(player, "revoke-done", "count", String.valueOf(count));
                player.closeInventory();
            });
            slot++;
        }
        GuiButtons.border(menu);
        plugin.menus().open(player, menu);
        return true;
    }

    private int revokeReason(String reason) {
        try {
            if (reason.equalsIgnoreCase("all")) {
                sqlite.execute("UPDATE staff_punishments SET active = 0 WHERE active = 1");
                return 1;
            }
            sqlite.execute("UPDATE staff_punishments SET active = 0 WHERE active = 1 AND lower(reason) LIKE ?",
                    "%" + reason.toLowerCase(Locale.ROOT) + "%");
            return 1;
        } catch (SQLException ex) {
            return 0;
        }
    }

    private boolean request(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        long wait = config.getLong("request-cooldown-seconds", 30) * 1000L;
        Long last = requests.get(player.getUniqueId());
        if (last != null && System.currentTimeMillis() - last < wait) {
            send(player, "request-cooldown");
            return true;
        }
        requests.put(player.getUniqueId(), System.currentTimeMillis());
        send(player, "request-sent");
        String name = player.getName();
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (!staff.hasPermission("shardedcore.staff")) continue;
            staff.sendMessage(ColorUtil.parse(staffMessage("request-staff", "player", name))
                    .clickEvent(ClickEvent.runCommand("/staffgoto " + name))
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                            ColorUtil.parse(cfg("messages.request-hover", "&7Click to teleport to %player%")
                                    .replace("%player%", name)))));
        }
        return true;
    }

    private boolean staffGoto(CommandSender sender, String[] args) {
        if (!(sender instanceof Player staff) || !staff(staff, "shardedcore.staff")) return true;
        if (args.length < 1) {
            send(staff, "usage-player", "command", "staffgoto");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            send(staff, "player-not-found");
            return true;
        }
        staff.teleportAsync(target.getLocation());
        send(staff, "goto", "player", target.getName());
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        rememberAlt(player);
        hideVanished(player);
        snapshot(player, "join");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            hideVanished(player);
            restoreVanish(player);
        }, 10L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        snapshot(player, "quit");
        if (screenshare.remove(player.getUniqueId()) != null) {
            apply(Bukkit.getConsoleSender(), player, "ban",
                    cfg("screenshare-ban-reason", "Refusing to SS"),
                    cfg("screenshare-ban-duration", "7d"), false);
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("shardedcore.staff")) send(staff, "screenshare-refuse", "player", player.getName());
            }
        }
        if (staffMode.contains(player.getUniqueId())) exitStaff(player, true);
        // vanish/frozen persist in sqlite
        staffChat.remove(player.getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        snapshot(event.getEntity(), "death");
    }

    @EventHandler
    public void onLogin(AsyncPlayerPreLoginEvent event) {
        Punishment ban = active(event.getUniqueId(), "ban");
        if (ban == null) ban = activeIp(event.getAddress() == null ? "" : event.getAddress().getHostAddress());
        if (ban == null) return;
        String time = ban.expires <= 0 ? "permanent" : Amounts.duration(Math.max(0, ban.expires - System.currentTimeMillis()), "d", "h", "m", "s", 2);
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, ColorUtil.parse(String.join("\n",
                Text.applyList(new ArrayList<>(config.getStringList("ban-screen")),
                        "staff", ban.staff, "reason", ban.reason, "time", time, "discord", cfg("discord", "discord.gg/shardedmc")))));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (staffChat.contains(player.getUniqueId())) {
            event.setCancelled(true);
            String message = PlainTextComponentSerializer.plainText().serialize(event.message());
            String format = Text.apply(cfg("staff-chat-format", "&#AD4EFF&lSTAFF &8▷ &f%player%&7: &f%message%"),
                    "player", player.getName(), "message", message);
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("shardedcore.staff.chat") || staff.hasPermission("shardedcore.staff")) {
                    staff.sendMessage(ColorUtil.parse(format));
                }
            }
            return;
        }
        Punishment mute = active(player.getUniqueId(), "mute");
        if (mute != null) {
            event.setCancelled(true);
            send(player, "muted-chat", "reason", mute.reason);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGamemodeCommand(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage().substring(1).trim();
        if (raw.isEmpty()) return;
        String[] parts = raw.split("\\s+");
        String command = parts[0].toLowerCase(Locale.ROOT);
        if (command.contains(":")) command = command.substring(command.indexOf(':') + 1);
        GameMode mode = switch (command) {
            case "gmsp", "gm3" -> GameMode.SPECTATOR;
            case "gmc", "gm1", "gmcs" -> GameMode.CREATIVE;
            case "gms", "gm0" -> GameMode.SURVIVAL;
            case "gma", "gm2" -> GameMode.ADVENTURE;
            default -> null;
        };
        if (mode == null) return;
        Player player = event.getPlayer();
        if (!player.hasPermission("shardedcore.staff.gamemode")) return;
        event.setCancelled(true);
        if (parts.length >= 2) {
            gamemode(player, mode, new String[]{parts[1]});
        } else {
            gamemode(player, mode, new String[0]);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAudit(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!config.getBoolean("audit.enabled", true)) return;
        if (config.getBoolean("audit.staff-only", true)
                && !player.hasPermission(cfg("staff-permission", "shardedcore.staff.mode"))) {
            return;
        }
        String command = event.getMessage();
        String name = command.substring(1).trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
        if (name.contains(":")) name = name.substring(name.indexOf(':') + 1);
        List<String> watched = config.getStringList("audit.commands");
        if (!watched.isEmpty()) {
            boolean match = false;
            for (String allowed : watched) {
                if (allowed.equalsIgnoreCase(name)) {
                    match = true;
                    break;
                }
            }
            if (!match) return;
        }
        String line = Text.apply(cfg("messages.audit", "%prefix%&#FF8300%player% &fused &f%command%")
                        .replace("%prefix%", cfg("prefix", "")),
                "player", player.getName(), "command", command);
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.equals(player)) continue;
            if (staff.hasPermission(cfg("audit-permission", "shardedcore.staff.audit"))) {
                staff.sendMessage(ColorUtil.parse(line));
            }
        }
        try {
            sqlite.execute("INSERT INTO staff_audit (uuid, name, command, created) VALUES (?, ?, ?, ?)",
                    player.getUniqueId().toString(), player.getName(), command, System.currentTimeMillis());
        } catch (SQLException ignored) {
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Punishment mute = active(event.getPlayer().getUniqueId(), "mute");
        if (mute == null) return;
        String command = event.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        if (config.getStringList("mute-commands").contains(command)) {
            event.setCancelled(true);
            send(event.getPlayer(), "muted-chat", "reason", mute.reason);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!frozen.contains(event.getPlayer().getUniqueId()) || event.getTo() == null) return;
        if (event.getFrom().getX() != event.getTo().getX() || event.getFrom().getZ() != event.getTo().getZ()) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (staffMode.contains(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (staffMode.contains(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (staffMode.contains(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (!staffMode.contains(event.getPlayer().getUniqueId())) return;
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) return;
        if (event.getPlayer().getGameMode() == GameMode.SPECTATOR) return;
        event.setCancelled(true);
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK
                && event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        String id = toolId(event.getItem());
        if (id.isEmpty() || id.equals("freeze") || id.equals("punish")) return;
        if (tooSoon(player)) return;
        switch (id) {
            case "vanish" -> player.performCommand("vanish");
            case "randomtp" -> player.performCommand("randomtp");
            case "exit" -> player.performCommand("staffmode");
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntity(PlayerInteractEntityEvent event) {
        if (!staffMode.contains(event.getPlayer().getUniqueId())) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getPlayer().getGameMode() == GameMode.SPECTATOR) return;
        event.setCancelled(true);
        if (!(event.getRightClicked() instanceof Player target)) return;
        Player player = event.getPlayer();
        String id = toolId(player.getInventory().getItemInMainHand());
        if (id.isEmpty() || tooSoon(player)) return;
        if (id.equals("freeze")) player.performCommand("freeze " + target.getName());
        else if (id.equals("punish")) openPunish(player, target);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!staffMode.contains(player.getUniqueId())) return;
        event.setCancelled(true);
        if (player.getGameMode() == GameMode.SPECTATOR) return;
        if (!(event.getEntity() instanceof Player target)) return;
        String id = toolId(player.getInventory().getItemInMainHand());
        if (!id.equals("punish") || tooSoon(player)) return;
        if (player.hasPermission("shardedcore.staff.punish")) openPunish(player, target);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player player && staffMode.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (vanished.contains(player.getUniqueId()) && config.getBoolean("vanish.disable-pickup", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onStaffClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (staffMode.contains(player.getUniqueId()) && event.getClickedInventory() == player.getInventory()) {
            event.setCancelled(true);
        }
    }

    private void restoreVanish(Player player) {
        try {
            Integer stored = sqlite.query("SELECT vanished FROM staff_state WHERE uuid = ?", rs -> {
                try {
                    return rs.next() ? rs.getInt("vanished") : 0;
                } catch (SQLException ex) {
                    return 0;
                }
            }, player.getUniqueId().toString());
            if (stored != null && stored == 1) setVanish(player, true);
        } catch (SQLException ignored) {
        }
    }

    private void hideFromPlayers(Player player) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) continue;
            if (other.hasPermission(cfg("staff-permission", "shardedcore.staff.mode"))) {
                other.showPlayer(plugin, player);
            } else {
                other.hidePlayer(plugin, player);
            }
        }
    }

    private void hideVanished(Player viewer) {
        boolean staff = viewer.hasPermission(cfg("staff-permission", "shardedcore.staff.mode"));
        boolean see = viewer.hasPermission(cfg("see-vanished", "shardedcore.staff.seevanished"));
        for (UUID uuid : vanished) {
            Player other = Bukkit.getPlayer(uuid);
            if (other == null || other.equals(viewer)) continue;
            if (see) viewer.showPlayer(plugin, other);
            else viewer.hidePlayer(plugin, other);
        }
        for (UUID uuid : staffMode) {
            if (vanished.contains(uuid)) continue;
            Player other = Bukkit.getPlayer(uuid);
            if (other == null || other.equals(viewer)) continue;
            if (staff) viewer.showPlayer(plugin, other);
            else viewer.hidePlayer(plugin, other);
        }
        if (vanished.contains(viewer.getUniqueId())) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.hasPermission(cfg("see-vanished", "shardedcore.staff.seevanished"))) {
                    other.hidePlayer(plugin, viewer);
                }
            }
        } else if (staffMode.contains(viewer.getUniqueId())) {
            hideFromPlayers(viewer);
        }
    }

    private void rememberAlt(Player player) {
        String ip = player.getAddress() == null ? "" : player.getAddress().getAddress().getHostAddress();
        try {
            sqlite.execute("INSERT INTO staff_alts (uuid, name, ip, last_seen) VALUES (?, ?, ?, ?)"
                    + (sqlite.mysql()
                    ? " ON DUPLICATE KEY UPDATE name = VALUES(name), last_seen = VALUES(last_seen)"
                    : " ON CONFLICT(uuid, ip) DO UPDATE SET name = excluded.name, last_seen = excluded.last_seen"),
                    player.getUniqueId().toString(), player.getName(), ip, System.currentTimeMillis());
        } catch (SQLException ignored) {
        }
    }

    private void autoSnapshot() {
        int max = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            snapshot(player, "auto");
            if (++max >= 8) break;
        }
    }

    private void snapshot(Player player, String category) {
        try {
            sqlite.execute("INSERT INTO staff_snapshots (uuid, category, created, contents) VALUES (?, ?, ?, ?)",
                    player.getUniqueId().toString(), category, System.currentTimeMillis(),
                    serializeInv(player.getInventory().getContents()));
            sqlite.execute("DELETE FROM staff_snapshots WHERE uuid = ? AND category = ? AND created < ? AND id NOT IN (SELECT id FROM staff_snapshots WHERE uuid = ? AND category = ? ORDER BY created DESC LIMIT ?)",
                    player.getUniqueId().toString(), category, 0L, player.getUniqueId().toString(), category,
                    config.getInt("max-snapshots", 20));
        } catch (SQLException ignored) {
        }
    }

    private List<Snap> snapshots(UUID uuid) {
        List<Snap> list = new ArrayList<>();
        try {
            sqlite.query("SELECT id, category, created, contents FROM staff_snapshots WHERE uuid = ? ORDER BY created DESC LIMIT 21", rs -> {
                try {
                    while (rs.next()) {
                        list.add(new Snap(rs.getInt("id"), rs.getString("category"), rs.getLong("created"), rs.getString("contents")));
                    }
                } catch (SQLException ignored) {
                }
                return null;
            }, uuid.toString());
        } catch (SQLException ignored) {
        }
        return list;
    }

    private void applySnap(Player player, String contents) {
        ItemStack[] items = deserializeInv(contents);
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        for (int i = 0; i < items.length && i < inventory.getSize(); i++) {
            inventory.setItem(i, items[i]);
        }
    }

    private Punishment active(UUID uuid, String type) {
        try {
            return sqlite.query("SELECT reason, staff, expires FROM staff_punishments WHERE uuid = ? AND type = ? AND active = 1 AND (expires = 0 OR expires > ?) ORDER BY created DESC LIMIT 1",
                    rs -> {
                        try {
                            if (!rs.next()) return null;
                            return new Punishment(rs.getString("reason"), rs.getString("staff"), rs.getLong("expires"));
                        } catch (SQLException ex) {
                            return null;
                        }
                    }, uuid.toString(), type, System.currentTimeMillis());
        } catch (SQLException ex) {
            return null;
        }
    }

    private Punishment activeIp(String ip) {
        if (ip == null || ip.isBlank()) return null;
        try {
            return sqlite.query("SELECT reason, staff, expires FROM staff_punishments WHERE ip = ? AND type = 'banip' AND active = 1 AND (expires = 0 OR expires > ?) ORDER BY created DESC LIMIT 1",
                    rs -> {
                        try {
                            if (!rs.next()) return null;
                            return new Punishment(rs.getString("reason"), rs.getString("staff"), rs.getLong("expires"));
                        } catch (SQLException ex) {
                            return null;
                        }
                    }, ip, System.currentTimeMillis());
        } catch (SQLException ex) {
            return null;
        }
    }

    private void deactivate(UUID uuid, String type) {
        try {
            sqlite.execute("UPDATE staff_punishments SET active = 0 WHERE uuid = ? AND type = ? AND active = 1", uuid.toString(), type);
        } catch (SQLException ignored) {
        }
    }

    private List<String> activeIps() {
        List<String> ips = new ArrayList<>();
        try {
            sqlite.query("SELECT DISTINCT ip FROM staff_punishments WHERE type = 'banip' AND active = 1 AND ip IS NOT NULL", rs -> {
                try {
                    while (rs.next()) ips.add(rs.getString("ip"));
                } catch (SQLException ignored) {
                }
                return null;
            });
        } catch (SQLException ignored) {
        }
        return ips;
    }

    private String ip(OfflinePlayer player) {
        Player online = player.getPlayer();
        if (online != null && online.getAddress() != null) return online.getAddress().getAddress().getHostAddress();
        try {
            return sqlite.query("SELECT ip FROM staff_alts WHERE uuid = ? ORDER BY last_seen DESC LIMIT 1", rs -> {
                try {
                    return rs.next() ? rs.getString("ip") : "";
                } catch (SQLException ex) {
                    return "";
                }
            }, player.getUniqueId().toString());
        } catch (SQLException ex) {
            return "";
        }
    }

    private long expires(String duration) {
        if (duration == null || duration.isBlank() || duration.equalsIgnoreCase("permanent") || duration.equalsIgnoreCase("perm")) {
            return 0L;
        }
        long millis = Amounts.durationMillis(duration);
        return millis <= 0 ? 0L : System.currentTimeMillis() + millis;
    }

    private boolean looksDuration(String raw) {
        return raw != null && raw.matches("(?i)\\d+[smhdw]|permanent|perm");
    }

    private void broadcast(String action, String player, String staff, String reason) {
        String text = staffMessage("broadcast", "action", action, "player", player, "staff", staff, "reason", reason);
        if (text == null || text.isBlank()) return;
        Bukkit.getOnlinePlayers().forEach(online -> online.sendMessage(ColorUtil.parse(text)));
    }

    private boolean staff(CommandSender sender, String permission) {
        if (sender.hasPermission(permission) || sender.hasPermission("shardedcore.staff")) return true;
        send(sender, "no-permission");
        return false;
    }

    private Player player(CommandSender sender, String[] args, int index) {
        if (args.length <= index) {
            send(sender, "usage-player", "command", "command");
            return null;
        }
        Player target = Bukkit.getPlayerExact(args[index]);
        if (target == null) send(sender, "player-not-found");
        return target;
    }

    private String staffMessage(String path, String... pairs) {
        String prefix = cfg("prefix", plugin.prefix());
        return Text.apply(cfg("messages." + path, "").replace("%prefix%", prefix), pairs);
    }

    @Override
    protected void send(CommandSender to, String path, String... pairs) {
        String text = staffMessage(path, pairs);
        if (text == null || text.isEmpty()) return;
        to.sendMessage(ColorUtil.parse(text));
    }

    private void unpack(PlayerInventory inventory, String contents) {
        ItemStack[] items = deserializeInv(contents);
        for (int i = 0; i < items.length && i < inventory.getStorageContents().length; i++) {
            inventory.setItem(i, items[i]);
        }
    }

    private static String serializeInv(ItemStack[] contents) {
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set("n", contents.length);
        for (int i = 0; i < contents.length; i++) yaml.set("s." + i, contents[i]);
        return yaml.saveToString();
    }

    private static ItemStack[] deserializeInv(String raw) {
        if (raw == null || raw.isBlank()) return new ItemStack[0];
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        try {
            yaml.loadFromString(raw);
        } catch (Exception ex) {
            return new ItemStack[0];
        }
        int n = yaml.getInt("n", 0);
        ItemStack[] items = new ItemStack[n];
        for (int i = 0; i < n; i++) items[i] = yaml.getItemStack("s." + i);
        return items;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            return switch (name) {
                case "freeze", "unfreeze", "punish", "ban", "mute", "kick", "offend", "banip", "unmute",
                     "alts", "screenshare", "invrollback", "gmc", "gms", "gmsp", "gma", "staffgoto" -> Tabs.players(args[0]);
                case "unban", "pardon" -> bannedNames(args[0]);
                case "unbanip" -> {
                    List<String> options = new ArrayList<>(List.of("list"));
                    options.addAll(Tabs.players(args[0]));
                    yield Tabs.filter(options, args[0]);
                }
                default -> List.of();
            };
        }
        if (args.length == 2 && (name.equals("ban") || name.equals("mute") || name.equals("banip") || name.equals("kick"))) {
            String path = name.equals("mute") ? "mute-reasons" : name.equals("kick") ? "kick-reasons" : "reasons";
            ConfigurationSection section = config.getConfigurationSection(path);
            return section == null ? List.of() : Tabs.filter(new ArrayList<>(section.getKeys(false)), args[1]);
        }
        if (args.length == 3 && (name.equals("ban") || name.equals("mute") || name.equals("banip"))) {
            return Tabs.filter(List.of("1h", "6h", "1d", "7d", "30d", "permanent"), args[2]);
        }
        return List.of();
    }

    private List<String> bannedNames(String prefix) {
        List<String> names = new ArrayList<>();
        try {
            sqlite.query("SELECT DISTINCT name FROM staff_punishments WHERE active = 1 AND type IN ('ban','banip')", rs -> {
                try {
                    while (rs.next()) names.add(rs.getString("name"));
                } catch (SQLException ignored) {
                }
                return null;
            });
        } catch (SQLException ignored) {
        }
        return Tabs.filter(names, prefix);
    }

    private record Punishment(String reason, String staff, long expires) {
    }

    private record Snap(int id, String category, long created, String contents) {
    }

    private record ReasonChoice(String reason, List<String> durations, ConfigurationSection section) {
    }
}
