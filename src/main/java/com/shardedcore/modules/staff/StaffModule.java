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
import com.shardedcore.util.Tabs;
import com.shardedcore.util.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
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
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
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
            "staff", "staffmode", "vanish", "freeze", "stafflist", "randomtp", "staffchat",
            "gmc", "gms", "gmsp", "gma", "punish", "ban", "mute", "kick", "offend", "banip",
            "unban", "unbanip", "unmute", "pardon", "alts", "screenshare", "invrollback",
            "revokepunishment", "requeststaff"
    );

    private Sqlite sqlite;
    private final Set<UUID> staffMode = ConcurrentHashMap.newKeySet();
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();
    private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();
    private final Set<UUID> staffChat = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> screenshare = new ConcurrentHashMap<>();
    private final Map<UUID, Long> requests = new ConcurrentHashMap<>();
    private BukkitTask snapshotTask;

    public StaffModule(ShardedCore plugin) {
        super(plugin, "staff");
    }

    @Override
    public void enable() {
        sqlite = plugin.toggles().sqlite();
        try {
            sqlite.run("""
                    CREATE TABLE IF NOT EXISTS staff_punishments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid TEXT NOT NULL,
                        name TEXT NOT NULL,
                        type TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        staff TEXT NOT NULL,
                        created INTEGER NOT NULL,
                        expires INTEGER NOT NULL,
                        ip TEXT,
                        active INTEGER NOT NULL DEFAULT 1
                    )
                    """);
            sqlite.run("""
                    CREATE TABLE IF NOT EXISTS staff_alts (
                        uuid TEXT NOT NULL,
                        name TEXT NOT NULL,
                        ip TEXT NOT NULL,
                        last_seen INTEGER NOT NULL,
                        PRIMARY KEY (uuid, ip)
                    )
                    """);
            sqlite.run("""
                    CREATE TABLE IF NOT EXISTS staff_snapshots (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid TEXT NOT NULL,
                        category TEXT NOT NULL,
                        created INTEGER NOT NULL,
                        contents TEXT NOT NULL
                    )
                    """);
            sqlite.run("""
                    CREATE TABLE IF NOT EXISTS staff_mode (
                        uuid TEXT PRIMARY KEY,
                        contents TEXT NOT NULL,
                        armor TEXT NOT NULL,
                        extra TEXT NOT NULL,
                        gamemode TEXT NOT NULL,
                        xp REAL NOT NULL,
                        level INTEGER NOT NULL
                    )
                    """);
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
        if (config.getBoolean("staffmode.vanish-on-enter", true)) setVanish(player, true);
        String disable = cfg("staffmode.disable-eglow-command", "eglow:eglow disable");
        if (disable != null && !disable.isBlank()) player.performCommand(disable.startsWith("/") ? disable.substring(1) : disable);
        inventory.setItem(0, Items.named(Material.LIME_DYE, "&#FF8300&lVANISH", List.of("&7Toggle vanish")));
        inventory.setItem(1, Items.named(Material.PACKED_ICE, "&#00C1FF&lFREEZE", List.of("&7Freeze a player")));
        inventory.setItem(2, Items.named(Material.NETHERITE_AXE, "&#FF0000&lPUNISH", List.of("&7Open punish menu")));
        inventory.setItem(8, Items.named(Material.BARRIER, "&#FF0000&lEXIT STAFF", List.of("&7Leave staff mode")));
    }

    private void exitStaff(Player player, boolean restore) {
        staffMode.remove(player.getUniqueId());
        setVanish(player, false);
        if (restore) restoreMode(player);
    }

    private void saveMode(Player player) {
        PlayerInventory inventory = player.getInventory();
        try {
                    sqlite.execute("""
                    INSERT INTO staff_mode (uuid, contents, armor, extra, gamemode, xp, level)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(uuid) DO UPDATE SET contents = excluded.contents, armor = excluded.armor,
                    extra = excluded.extra, gamemode = excluded.gamemode, xp = excluded.xp, level = excluded.level
                    """, player.getUniqueId().toString(), serializeInv(inventory.getStorageContents()),
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
            if (config.getBoolean("vanish.fly", true)) {
                player.setAllowFlight(true);
                player.setFlying(true);
            }
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.hasPermission(cfg("see-vanished", "shardedcore.staff.seevanished"))) {
                    other.hidePlayer(plugin, player);
                }
            }
        } else {
            vanished.remove(player.getUniqueId());
            for (Player other : Bukkit.getOnlinePlayers()) other.showPlayer(plugin, player);
        }
    }

    private boolean freeze(CommandSender sender, String[] args) {
        if (!staff(sender, "shardedcore.staff.freeze")) return true;
        Player target = player(sender, args, 0);
        if (target == null) return true;
        if (frozen.remove(target.getUniqueId())) {
            send(sender, "freeze-off", "player", target.getName());
            send(target, "unfrozen");
            return true;
        }
        frozen.add(target.getUniqueId());
        send(sender, "freeze-on", "player", target.getName());
        send(target, "frozen");
        return true;
    }

    private boolean staffList(CommandSender sender) {
        if (!staff(sender, "shardedcore.staff.list")) return true;
        List<Player> list = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(cfg("staff-permission", "shardedcore.staff"))) list.add(player);
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
        Menus.Menu menu = plugin.menus().create(staff, cfg("messages.punish-title", "☀ Staff ☀ Previewing | Punish"), 3);
        menu.set(11, Items.named(Material.IRON_BARS, "&#FF0000&lBAN", List.of("&7Ban " + target.getName())), event -> {
            event.setCancelled(true);
            apply(staff, target, "ban", cfg("default-reason", "Unfair Modifications"), "7d", false);
            staff.closeInventory();
        });
        menu.set(13, Items.named(Material.BOOK, "&#FFBA00&lMUTE", List.of("&7Mute " + target.getName())), event -> {
            event.setCancelled(true);
            apply(staff, target, "mute", "Chat-Behavior", "1d", false);
            staff.closeInventory();
        });
        menu.set(15, Items.named(Material.LEATHER_BOOTS, "&#FF8300&lKICK", List.of("&7Kick " + target.getName())), event -> {
            event.setCancelled(true);
            target.kick(ColorUtil.parse("&cKicked by staff."));
            send(staff, "kicked", "player", target.getName(), "reason", "Kicked");
            staff.closeInventory();
        });
        menu.set(GuiButtons.slot("close", 22), GuiButtons.close(staff), event -> {
            event.setCancelled(true);
            staff.closeInventory();
        });
        GuiButtons.border(menu);
        plugin.menus().open(staff, menu);
        GuiButtons.play(staff, "open");
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
        send(sender, "alts-header", "player", Players.name(target));
        for (String row : names) {
            String[] parts = row.split(":", 2);
            UUID uuid = parts.length == 2 ? UUID.fromString(parts[1]) : target.getUniqueId();
            send(sender, "alts-entry", "name", parts[0], "status", Bukkit.getPlayer(uuid) != null ? "online" : "offline");
        }
        return true;
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
        if (!staff(sender, "shardedcore.staff.revoke")) return true;
        try {
            sqlite.execute("UPDATE staff_punishments SET active = 0 WHERE active = 1");
        } catch (SQLException ignored) {
        }
        send(sender, "revoke-done", "count", "all");
        return true;
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
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (!staff.hasPermission("shardedcore.staff")) continue;
            staff.sendMessage(ColorUtil.parse(Text.apply(cfg("messages.request-staff", ""),
                            "player", player.getName()))
                    .clickEvent(ClickEvent.runCommand("/tp " + player.getName())));
        }
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        rememberAlt(player);
        hideVanished(player);
        snapshot(player, "join");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) hideVanished(player);
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
        vanished.remove(player.getUniqueId());
        frozen.remove(player.getUniqueId());
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

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!staffMode.contains(event.getPlayer().getUniqueId())) return;
        ItemStack item = event.getItem();
        if (item == null) {
            event.setCancelled(true);
            return;
        }
        if (item.getType() == Material.BARRIER) {
            event.setCancelled(true);
            event.getPlayer().performCommand("staffmode");
            return;
        }
        if (item.getType() == Material.LIME_DYE || item.getType() == Material.GRAY_DYE) {
            event.setCancelled(true);
            event.getPlayer().performCommand("vanish");
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

    private void hideVanished(Player viewer) {
        boolean see = viewer.hasPermission(cfg("see-vanished", "shardedcore.staff.seevanished"));
        for (UUID uuid : vanished) {
            Player other = Bukkit.getPlayer(uuid);
            if (other == null || other.equals(viewer)) continue;
            if (see) viewer.showPlayer(plugin, other);
            else viewer.hidePlayer(plugin, other);
        }
        if (vanished.contains(viewer.getUniqueId())) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.hasPermission(cfg("see-vanished", "shardedcore.staff.seevanished"))) {
                    other.hidePlayer(plugin, viewer);
                }
            }
        }
    }

    private void rememberAlt(Player player) {
        String ip = player.getAddress() == null ? "" : player.getAddress().getAddress().getHostAddress();
        try {
            sqlite.execute("""
                    INSERT INTO staff_alts (uuid, name, ip, last_seen) VALUES (?, ?, ?, ?)
                    ON CONFLICT(uuid, ip) DO UPDATE SET name = excluded.name, last_seen = excluded.last_seen
                    """, player.getUniqueId().toString(), player.getName(), ip, System.currentTimeMillis());
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
        String text = Text.apply(cfg("messages.broadcast", ""), "action", action, "player", player, "staff", staff, "reason", reason);
        if (text.isBlank()) return;
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
                case "freeze", "punish", "ban", "mute", "kick", "offend", "banip", "unban", "unmute",
                     "pardon", "alts", "screenshare", "invrollback", "gmc", "gms", "gmsp", "gma" -> Tabs.players(args[0]);
                case "unbanip" -> {
                    List<String> options = new ArrayList<>(List.of("list"));
                    options.addAll(Tabs.players(args[0]));
                    yield Tabs.filter(options, args[0]);
                }
                default -> List.of();
            };
        }
        if (args.length == 2 && (name.equals("ban") || name.equals("mute") || name.equals("banip") || name.equals("kick"))) {
            ConfigurationSection section = config.getConfigurationSection(name.equals("mute") ? "mute-reasons" : "reasons");
            return section == null ? List.of() : Tabs.filter(new ArrayList<>(section.getKeys(false)), args[1]);
        }
        if (args.length == 3 && (name.equals("ban") || name.equals("mute") || name.equals("banip"))) {
            return Tabs.filter(List.of("1h", "6h", "1d", "7d", "30d", "permanent"), args[2]);
        }
        return List.of();
    }

    private record Punishment(String reason, String staff, long expires) {
    }

    private record Snap(int id, String category, long created, String contents) {
    }
}
