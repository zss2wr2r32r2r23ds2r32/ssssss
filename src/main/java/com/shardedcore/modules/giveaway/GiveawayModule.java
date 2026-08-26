package com.shardedcore.modules.giveaway;

import com.shardedcore.ShardedCore;
import com.shardedcore.database.Sqlite;
import com.shardedcore.gui.Menus;
import com.shardedcore.module.Module;
import com.shardedcore.util.Amounts;
import com.shardedcore.util.ColorUtil;
import com.shardedcore.util.Configs;
import com.shardedcore.util.Items;
import com.shardedcore.util.Slots;
import com.shardedcore.util.Sounds;
import com.shardedcore.util.Tabs;
import com.shardedcore.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public final class GiveawayModule extends Module implements CommandExecutor, TabCompleter, Listener {

    private Sqlite sqlite;
    private FileConfiguration menu;
    private BukkitTask tick;
    private final Map<UUID, Integer> durationIndex = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldown = new ConcurrentHashMap<>();
    private final Set<Integer> warned = ConcurrentHashMap.newKeySet();

    public GiveawayModule(ShardedCore plugin) {
        super(plugin, "giveaway");
    }

    @Override
    protected void extraFiles() {
        extraFile("menu.yml");
    }

    @Override
    public void enable() {
        menu = Configs.load(new File(folder, "menu.yml"));
        sqlite = plugin.toggles().sqlite();
        try {
            sqlite.run("""
                    CREATE TABLE IF NOT EXISTS giveaways (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        owner TEXT NOT NULL,
                        owner_name TEXT NOT NULL,
                        ends_at INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                    """);
            sqlite.run("""
                    CREATE TABLE IF NOT EXISTS giveaway_items (
                        giveaway_id INTEGER NOT NULL,
                        slot INTEGER NOT NULL,
                        item TEXT NOT NULL
                    )
                    """);
            sqlite.run("""
                    CREATE TABLE IF NOT EXISTS giveaway_entries (
                        giveaway_id INTEGER NOT NULL,
                        uuid TEXT NOT NULL,
                        PRIMARY KEY (giveaway_id, uuid)
                    )
                    """);
            sqlite.run("""
                    CREATE TABLE IF NOT EXISTS giveaway_wins (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid TEXT NOT NULL,
                        owner_name TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        claimed INTEGER NOT NULL DEFAULT 0
                    )
                    """);
            sqlite.run("""
                    CREATE TABLE IF NOT EXISTS giveaway_win_items (
                        win_id INTEGER NOT NULL,
                        slot INTEGER NOT NULL,
                        item TEXT NOT NULL
                    )
                    """);
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create giveaway tables", ex);
        }
        registerCommand("giveaway", this);
        registerListener(this);
        long period = Math.max(1, config.getInt("tick-seconds", 10)) * 20L;
        tick = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, period, period);
    }

    @Override
    public void disable() {
        if (tick != null) tick.cancel();
        cleanup();
    }

    @Override
    public void reload() {
        super.reload();
        menu = Configs.load(new File(folder, "menu.yml"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("cancel")) {
            if (!player.hasPermission("shardedcore.giveaway.admin")) {
                send(player, "no-permission");
                return true;
            }
            if (args.length < 2) {
                send(player, "usage-cancel");
                return true;
            }
            try {
                cancel(player, Integer.parseInt(args[1]));
            } catch (NumberFormatException ex) {
                send(player, "usage-cancel");
            }
            return true;
        }
        openMain(player, 0);
        return true;
    }

    private void openMain(Player player, int page) {
        List<Running> running = running();
        List<Integer> area = Slots.of(menu, "area");
        int per = Math.max(1, area.size());
        int pages = Math.max(1, (running.size() + per - 1) / per);
        int current = Math.max(0, Math.min(page, pages - 1));
        Menus.Menu gui = plugin.menus().create(player, menu.getString("title", "&8Public Giveaways"), 6);
        if (running.isEmpty()) {
            ConfigurationSection empty = menu.getConfigurationSection("main.empty");
            if (empty != null) gui.set(empty.getInt("slot", 22), Items.fromSection(empty, player));
        } else {
            int start = current * per;
            for (int i = 0; i < per && start + i < running.size(); i++) {
                Running entry = running.get(start + i);
                gui.set(area.get(i), entryIcon(player, entry), event -> {
                    event.setCancelled(true);
                    if (event.isRightClick()) openView(player, entry);
                    else enter(player, entry);
                });
            }
        }
        button(gui, "main.your-items", player,
                "wins", String.valueOf(winCount(player.getUniqueId())),
                event -> openWins(player, 0));
        button(gui, "main.refresh", player, event -> openMain(player, current));
        button(gui, "main.create", player,
                "running", String.valueOf(runningCount(player.getUniqueId())),
                "max", String.valueOf(config.getInt("rules.max-running", 1)),
                event -> openCreate(player));
        if (current > 0) button(gui, "main.previous", player, event -> openMain(player, current - 1));
        if (current + 1 < pages) button(gui, "main.next", player, event -> openMain(player, current + 1));
        fill(gui);
        plugin.menus().open(player, gui);
        sound(player, "sounds.open");
    }

    private ItemStack entryIcon(Player player, Running entry) {
        ConfigurationSection section = menu.getConfigurationSection("main.entry");
        List<ItemStack> prizes = items(entry.id);
        String marker = section == null ? "LEFT-CLICK" : section.getString("state-marker", "LEFT-CLICK");
        List<String> lore = section == null ? new ArrayList<>() : new ArrayList<>(section.getStringList("lore"));
        if (entry.owner.equals(player.getUniqueId())) {
            replaceMarker(lore, marker, section == null ? "" : section.getString("state-own", ""));
        } else if (entered(entry.id, player.getUniqueId())) {
            replaceMarker(lore, marker, section == null ? "" : section.getString("state-entered", ""));
        }
        List<String> expanded = new ArrayList<>();
        for (String line : lore) {
            if (line.contains("%items%")) {
                for (String prize : prizeText(prizes).split("\n")) expanded.add(prize);
            } else {
                expanded.add(line);
            }
        }
        String name = section == null ? "%player%'s giveaway" : section.getString("name", "%player%'S GIVEAWAY");
        return Items.named(
                Sounds.material(section == null ? "CHEST_MINECART" : section.getString("material", "CHEST_MINECART"), Material.CHEST_MINECART),
                Text.apply(name, "player", entry.ownerName, "items", String.valueOf(prizes.size()),
                        "entries", String.valueOf(entryCount(entry.id)), "time", left(entry.endsAt)),
                Text.applyList(expanded, "player", entry.ownerName, "items", String.valueOf(prizes.size()),
                        "entries", String.valueOf(entryCount(entry.id)), "time", left(entry.endsAt))
        );
    }

    private void replaceMarker(List<String> lore, String marker, String replacement) {
        for (int i = 0; i < lore.size(); i++) {
            if (lore.get(i).contains(marker)) lore.set(i, replacement);
        }
    }

    private void openView(Player player, Running entry) {
        List<Integer> area = Slots.of(menu, "area");
        Menus.Menu gui = plugin.menus().create(player, menu.getString("title", "&8Public Giveaways"), 6);
        List<ItemStack> prizes = items(entry.id);
        for (int i = 0; i < area.size() && i < prizes.size(); i++) gui.set(area.get(i), prizes.get(i));
        button(gui, "view.return", player, "player", entry.ownerName, event -> openMain(player, 0));
        fill(gui);
        plugin.menus().open(player, gui);
        sound(player, "sounds.click");
    }

    private void openWins(Player player, int page) {
        List<Win> wins = wins(player.getUniqueId());
        List<Integer> area = Slots.of(menu, "area");
        int per = Math.max(1, area.size());
        int pages = Math.max(1, (wins.size() + per - 1) / per);
        int current = Math.max(0, Math.min(page, pages - 1));
        Menus.Menu gui = plugin.menus().create(player, menu.getString("title", "&8Public Giveaways"), 6);
        if (wins.isEmpty()) {
            ConfigurationSection empty = menu.getConfigurationSection("wins.empty");
            if (empty != null) gui.set(empty.getInt("slot", 22), Items.fromSection(empty, player));
        } else {
            int start = current * per;
            for (int i = 0; i < per && start + i < wins.size(); i++) {
                Win win = wins.get(start + i);
                String path = win.kind.equals("returned") ? "wins.returned" : "wins.entry";
                List<ItemStack> prizes = winItems(win.id);
                gui.set(area.get(i), Items.fromSection(menu.getConfigurationSection(path), player,
                        "amount", String.valueOf(count(prizes)),
                        "player", win.ownerName,
                        "items", String.valueOf(prizes.size()),
                        "time", formatTime(win.createdAt)), event -> {
                    event.setCancelled(true);
                    claim(player, win);
                });
            }
        }
        button(gui, "wins.refresh", player, event -> openWins(player, current));
        button(gui, "wins.return", player, event -> openMain(player, 0));
        if (current > 0) button(gui, "wins.previous", player, event -> openWins(player, current - 1));
        if (current + 1 < pages) button(gui, "wins.next", player, event -> openWins(player, current + 1));
        fill(gui);
        plugin.menus().open(player, gui);
    }

    private void openCreate(Player player) {
        if (runningCount(player.getUniqueId()) >= config.getInt("rules.max-running", 1)) {
            send(player, "already-running");
            sound(player, "sounds.error");
            return;
        }
        long wait = config.getLong("rules.cooldown-seconds", 0) * 1000L;
        Long last = cooldown.get(player.getUniqueId());
        if (wait > 0 && last != null && System.currentTimeMillis() - last < wait) {
            send(player, "cooldown", "time", Amounts.duration(wait - (System.currentTimeMillis() - last), "d", "h", "m", "s", 2));
            return;
        }
        List<Integer> area = Slots.of(menu, "area");
        Menus.Menu gui = plugin.menus().create(player, menu.getString("title", "&8Public Giveaways"), 6)
                .editableSlots(area);
        renderCreateButtons(player, gui, area);
        fill(gui, area);
        gui.onClose(closed -> {
            if (closed.hasMetadata("shardedcore-giveaway-starting")) {
                closed.removeMetadata("shardedcore-giveaway-starting", plugin);
                return;
            }
            returnItems(closed, gui.inventory().getContents(), area);
        });
        plugin.menus().open(player, gui);
        sound(player, "sounds.open");
    }

    private void renderCreateButtons(Player player, Menus.Menu gui, List<Integer> area) {
        int max = config.getInt("rules.max-items", 36);
        int count = count(contents(gui, area));
        List<String> durations = config.getStringList("durations");
        if (durations.isEmpty()) durations = List.of("1h");
        final List<String> times = durations;
        int index = durationIndex.getOrDefault(player.getUniqueId(), 0) % times.size();
        String duration = times.get(index);
        button(gui, "create.cancel", player, event -> {
            event.setCancelled(true);
            player.closeInventory();
        });
        button(gui, "create.preview", player, "items", String.valueOf(count), "max", String.valueOf(max), event -> {
            event.setCancelled(true);
            renderCreateButtons(player, gui, area);
        });
        int next = (index + 1) % times.size();
        button(gui, "create.start", player, "items", String.valueOf(count), "duration", duration, event -> {
            event.setCancelled(true);
            if (event.isRightClick()) {
                durationIndex.put(player.getUniqueId(), next);
                renderCreateButtons(player, gui, area);
                sound(player, "sounds.click");
                return;
            }
            start(player, gui, area, duration);
        });
    }

    private void start(Player player, Menus.Menu gui, List<Integer> area, String duration) {
        List<ItemStack> prizes = contents(gui, area);
        int min = config.getInt("rules.min-items", 1);
        int max = config.getInt("rules.max-items", 36);
        if (prizes.size() < min) {
            send(player, "too-few-items", "min", String.valueOf(min));
            sound(player, "sounds.error");
            return;
        }
        if (prizes.size() > max) {
            send(player, "too-many-items", "max", String.valueOf(max));
            return;
        }
        for (ItemStack item : prizes) {
            if (blocked(item.getType())) {
                send(player, "blocked-item");
                return;
            }
        }
        long ends = System.currentTimeMillis() + Math.max(1000L, Amounts.durationMillis(duration));
        try {
            sqlite.execute("INSERT INTO giveaways (owner, owner_name, ends_at, status, created_at) VALUES (?, ?, ?, 'running', ?)",
                    player.getUniqueId().toString(), player.getName(), ends, System.currentTimeMillis());
            Integer id = sqlite.query("SELECT last_insert_rowid() AS id", rs -> {
                try {
                    return rs.next() ? rs.getInt("id") : null;
                } catch (SQLException ex) {
                    return null;
                }
            });
            if (id == null) throw new SQLException("no id");
            for (int i = 0; i < prizes.size(); i++) {
                sqlite.execute("INSERT INTO giveaway_items (giveaway_id, slot, item) VALUES (?, ?, ?)",
                        id, i, Items.serialize(prizes.get(i)));
            }
            player.setMetadata("shardedcore-giveaway-starting", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
            cooldown.put(player.getUniqueId(), System.currentTimeMillis());
            player.closeInventory();
            sound(player, "sounds.start");
            broadcastStart(player.getName(), prizes);
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to start giveaway", ex);
            send(player, "start-failed");
            returnItems(player, prizes.toArray(ItemStack[]::new), null);
        }
    }

    private void enter(Player player, Running entry) {
        if (entry.owner.equals(player.getUniqueId()) && !config.getBoolean("rules.owner-can-enter", false)) {
            send(player, "own-giveaway", "player", entry.ownerName);
            sound(player, "sounds.error");
            return;
        }
        if (entered(entry.id, player.getUniqueId())) {
            send(player, "already-entered", "player", entry.ownerName);
            return;
        }
        try {
            sqlite.execute("INSERT OR IGNORE INTO giveaway_entries (giveaway_id, uuid) VALUES (?, ?)",
                    entry.id, player.getUniqueId().toString());
            send(player, "entered", "player", entry.ownerName);
            sound(player, "sounds.enter");
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to enter giveaway", ex);
        }
    }

    private void claim(Player player, Win win) {
        List<ItemStack> prizes = winItems(win.id);
        int need = prizes.size();
        int empty = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType().isAir()) empty++;
        }
        if (empty < need) {
            send(player, "no-space", "slots", String.valueOf(need - empty));
            return;
        }
        try {
            sqlite.execute("UPDATE giveaway_wins SET claimed = 1 WHERE id = ?", win.id);
        } catch (SQLException ex) {
            return;
        }
        for (ItemStack item : prizes) give(player, item);
        send(player, "claimed", "amount", String.valueOf(prizes.size()));
        sound(player, "sounds.claim");
        openWins(player, 0);
    }

    private void cancel(Player player, int id) {
        Running running = running().stream().filter(entry -> entry.id == id).findFirst().orElse(null);
        if (running == null) {
            send(player, "not-found");
            return;
        }
        finish(running, true);
        send(player, "cancelled", "id", String.valueOf(id), "player", running.ownerName);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        long warn = config.getLong("warn-seconds", 300) * 1000L;
        for (Running entry : running()) {
            if (warn > 0 && entry.endsAt - now <= warn && entry.endsAt > now && warned.add(entry.id)) {
                broadcast(cfg("broadcast.ending", ""), "player", entry.ownerName, "time", left(entry.endsAt));
            }
            if (entry.endsAt <= now) finish(entry, false);
        }
        long keep = config.getLong("keep-days", 30) * 86_400_000L;
        if (keep > 0) {
            long cut = now - keep;
            try {
                sqlite.execute("DELETE FROM giveaway_win_items WHERE win_id IN (SELECT id FROM giveaway_wins WHERE claimed = 1 AND created_at < ?)", cut);
                sqlite.execute("DELETE FROM giveaway_wins WHERE claimed = 1 AND created_at < ?", cut);
                sqlite.execute("DELETE FROM giveaway_items WHERE giveaway_id IN (SELECT id FROM giveaways WHERE status != 'running' AND created_at < ?)", cut);
                sqlite.execute("DELETE FROM giveaway_entries WHERE giveaway_id IN (SELECT id FROM giveaways WHERE status != 'running' AND created_at < ?)", cut);
                sqlite.execute("DELETE FROM giveaways WHERE status != 'running' AND created_at < ?", cut);
            } catch (SQLException ignored) {
            }
        }
    }

    private void finish(Running entry, boolean cancelled) {
        List<String> entries = entryIds(entry.id);
        List<ItemStack> prizes = items(entry.id);
        try {
            sqlite.execute("UPDATE giveaways SET status = ? WHERE id = ?", cancelled ? "cancelled" : "ended", entry.id);
        } catch (SQLException ex) {
            return;
        }
        if (entries.isEmpty()) {
            storeWin(entry.owner, entry.ownerName, "returned", prizes);
            Player owner = Bukkit.getPlayer(entry.owner);
            if (owner != null) send(owner, "returned");
            return;
        }
        String winnerId = entries.get(ThreadLocalRandom.current().nextInt(entries.size()));
        UUID winner = UUID.fromString(winnerId);
        storeWin(winner, entry.ownerName, "won", prizes);
        Player online = Bukkit.getPlayer(winner);
        String winnerName = online != null ? online.getName() : winnerId.substring(0, 8);
        broadcast(cfg("broadcast.winner", ""),
                "winner", winnerName, "player", entry.ownerName, "entries", String.valueOf(entries.size()));
    }

    private void storeWin(UUID uuid, String ownerName, String kind, List<ItemStack> prizes) {
        try {
            sqlite.execute("INSERT INTO giveaway_wins (uuid, owner_name, kind, created_at, claimed) VALUES (?, ?, ?, ?, 0)",
                    uuid.toString(), ownerName, kind, System.currentTimeMillis());
            Integer id = sqlite.query("SELECT last_insert_rowid() AS id", rs -> {
                try {
                    return rs.next() ? rs.getInt("id") : null;
                } catch (SQLException ex) {
                    return null;
                }
            });
            if (id == null) return;
            for (int i = 0; i < prizes.size(); i++) {
                sqlite.execute("INSERT INTO giveaway_win_items (win_id, slot, item) VALUES (?, ?, ?)",
                        id, i, Items.serialize(prizes.get(i)));
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to store giveaway win", ex);
        }
    }

    private void broadcastStart(String player, List<ItemStack> prizes) {
        String line = Text.apply(cfg("broadcast.started", ""), "player", player, "items", String.valueOf(prizes.size()));
        Component hover = ColorUtil.parse(prizeText(prizes));
        Component message = ColorUtil.parse(line)
                .hoverEvent(HoverEvent.showText(hover))
                .clickEvent(ClickEvent.runCommand("/giveaway"));
        Bukkit.getOnlinePlayers().forEach(online -> online.sendMessage(message));
    }

    private void broadcast(String line, String... pairs) {
        if (line == null || line.isBlank()) return;
        Component message = ColorUtil.parse(Text.apply(line, pairs));
        Bukkit.getOnlinePlayers().forEach(online -> online.sendMessage(message));
    }

    private String prizeText(List<ItemStack> prizes) {
        int limit = menu.getInt("prize-lines", 10);
        String format = menu.getString("prize-format", "%amount%x %item%");
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < prizes.size() && i < limit; i++) {
            ItemStack item = prizes.get(i);
            lines.add(Text.apply(format, "amount", String.valueOf(item.getAmount()), "item", itemName(item)));
        }
        if (prizes.size() > limit) {
            lines.add(Text.apply(menu.getString("prize-more", ""), "rest", String.valueOf(prizes.size() - limit)));
        }
        return String.join("\n", lines);
    }

    private List<Running> running() {
        List<Running> list = new ArrayList<>();
        try {
            sqlite.query("SELECT id, owner, owner_name, ends_at FROM giveaways WHERE status = 'running' ORDER BY ends_at ASC", rs -> {
                try {
                    while (rs.next()) {
                        list.add(new Running(rs.getInt("id"), UUID.fromString(rs.getString("owner")),
                                rs.getString("owner_name"), rs.getLong("ends_at")));
                    }
                } catch (SQLException ex) {
                    throw new IllegalStateException(ex);
                }
                return list;
            });
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to list giveaways", ex);
        }
        return list;
    }

    private List<ItemStack> items(int id) {
        return loadItems("SELECT slot, item FROM giveaway_items WHERE giveaway_id = ? ORDER BY slot", id);
    }

    private List<ItemStack> winItems(int id) {
        return loadItems("SELECT slot, item FROM giveaway_win_items WHERE win_id = ? ORDER BY slot", id);
    }

    private List<ItemStack> loadItems(String sql, int id) {
        List<ItemStack> list = new ArrayList<>();
        try {
            sqlite.query(sql, rs -> {
                try {
                    while (rs.next()) {
                        ItemStack item = Items.deserialize(rs.getString("item"));
                        if (item != null) list.add(item);
                    }
                } catch (SQLException ex) {
                    throw new IllegalStateException(ex);
                }
                return list;
            }, id);
        } catch (SQLException ignored) {
        }
        return list;
    }

    private List<Win> wins(UUID uuid) {
        List<Win> list = new ArrayList<>();
        try {
            sqlite.query("SELECT id, owner_name, kind, created_at FROM giveaway_wins WHERE uuid = ? AND claimed = 0 ORDER BY created_at DESC", rs -> {
                try {
                    while (rs.next()) {
                        list.add(new Win(rs.getInt("id"), rs.getString("owner_name"), rs.getString("kind"), rs.getLong("created_at")));
                    }
                } catch (SQLException ex) {
                    throw new IllegalStateException(ex);
                }
                return list;
            }, uuid.toString());
        } catch (SQLException ignored) {
        }
        return list;
    }

    private int winCount(UUID uuid) {
        try {
            Integer value = sqlite.query("SELECT COUNT(*) AS n FROM giveaway_wins WHERE uuid = ? AND claimed = 0", rs -> {
                try {
                    return rs.next() ? rs.getInt("n") : 0;
                } catch (SQLException ex) {
                    return 0;
                }
            }, uuid.toString());
            return value == null ? 0 : value;
        } catch (SQLException ex) {
            return 0;
        }
    }

    private int runningCount(UUID uuid) {
        try {
            Integer value = sqlite.query("SELECT COUNT(*) AS n FROM giveaways WHERE owner = ? AND status = 'running'", rs -> {
                try {
                    return rs.next() ? rs.getInt("n") : 0;
                } catch (SQLException ex) {
                    return 0;
                }
            }, uuid.toString());
            return value == null ? 0 : value;
        } catch (SQLException ex) {
            return 0;
        }
    }

    private int entryCount(int id) {
        try {
            Integer value = sqlite.query("SELECT COUNT(*) AS n FROM giveaway_entries WHERE giveaway_id = ?", rs -> {
                try {
                    return rs.next() ? rs.getInt("n") : 0;
                } catch (SQLException ex) {
                    return 0;
                }
            }, id);
            return value == null ? 0 : value;
        } catch (SQLException ex) {
            return 0;
        }
    }

    private boolean entered(int id, UUID uuid) {
        try {
            Boolean value = sqlite.query("SELECT 1 FROM giveaway_entries WHERE giveaway_id = ? AND uuid = ?", rs -> {
                try {
                    return rs.next();
                } catch (SQLException ex) {
                    return false;
                }
            }, id, uuid.toString());
            return Boolean.TRUE.equals(value);
        } catch (SQLException ex) {
            return false;
        }
    }

    private List<String> entryIds(int id) {
        List<String> list = new ArrayList<>();
        try {
            sqlite.query("SELECT uuid FROM giveaway_entries WHERE giveaway_id = ?", rs -> {
                try {
                    while (rs.next()) list.add(rs.getString("uuid"));
                } catch (SQLException ex) {
                    throw new IllegalStateException(ex);
                }
                return list;
            }, id);
        } catch (SQLException ignored) {
        }
        return list;
    }

    private void button(Menus.Menu gui, String path, Player player, java.util.function.Consumer<org.bukkit.event.inventory.InventoryClickEvent> click, String... pairs) {
        ConfigurationSection section = menu.getConfigurationSection(path);
        if (section == null) return;
        gui.set(section.getInt("slot", 0), Items.fromSection(section, player, pairs), event -> {
            event.setCancelled(true);
            click.accept(event);
        });
    }

    private void button(Menus.Menu gui, String path, Player player, String k1, String v1, java.util.function.Consumer<org.bukkit.event.inventory.InventoryClickEvent> click) {
        button(gui, path, player, click, k1, v1);
    }

    private void button(Menus.Menu gui, String path, Player player, String k1, String v1, String k2, String v2,
                        java.util.function.Consumer<org.bukkit.event.inventory.InventoryClickEvent> click) {
        button(gui, path, player, click, k1, v1, k2, v2);
    }

    private void button(Menus.Menu gui, String path, Player player, java.util.function.Consumer<org.bukkit.event.inventory.InventoryClickEvent> click) {
        button(gui, path, player, click, new String[0]);
    }

    private void fill(Menus.Menu gui) {
        fill(gui, List.of());
    }

    private void fill(Menus.Menu gui, List<Integer> skip) {
        gui.fillExcept(Items.fromSection(menu.getConfigurationSection("filler"), null), skip);
    }

    private boolean filler(ItemStack item) {
        if (item == null || item.getType().isAir()) return true;
        Material material = Sounds.material(menu.getString("filler.material", "BLACK_STAINED_GLASS_PANE"),
                Material.BLACK_STAINED_GLASS_PANE);
        return item.getType() == material;
    }

    private List<ItemStack> contents(Menus.Menu gui, List<Integer> area) {
        List<ItemStack> list = new ArrayList<>();
        ItemStack[] contents = gui.inventory().getContents();
        for (int slot : area) {
            if (slot < 0 || slot >= contents.length) continue;
            ItemStack item = contents[slot];
            if (item != null && !filler(item)) list.add(item.clone());
        }
        return list;
    }

    private void returnItems(Player player, ItemStack[] contents, List<Integer> area) {
        if (contents == null) return;
        Set<Integer> limit = area == null ? null : new HashSet<>(area);
        for (int i = 0; i < contents.length; i++) {
            if (limit != null && !limit.contains(i)) continue;
            if (filler(contents[i])) continue;
            give(player, contents[i]);
        }
    }

    private void give(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        player.getInventory().addItem(item.clone()).values()
                .forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
    }

    private boolean blocked(Material material) {
        for (String name : config.getStringList("rules.blocked-materials")) {
            if (material.name().equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private int count(List<ItemStack> items) {
        int total = 0;
        for (ItemStack item : items) total += item.getAmount();
        return total;
    }

    private String left(long endsAt) {
        return Amounts.duration(Math.max(0, endsAt - System.currentTimeMillis()), "d", "h", "m", "s", 2);
    }

    private String formatTime(long at) {
        try {
            return new SimpleDateFormat(menu.getString("time-format", "dd.MM.yyyy HH:mm")).format(new Date(at));
        } catch (Exception ex) {
            return String.valueOf(at);
        }
    }

    private String itemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName() && item.getItemMeta().displayName() != null) {
            String plain = PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
            if (!plain.isBlank()) return plain;
        }
        return Text.pretty(item.getType().name());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("shardedcore.giveaway.admin")) {
            return Tabs.filter(List.of("cancel"), args[0]);
        }
        return List.of();
    }

    private record Running(int id, UUID owner, String ownerName, long endsAt) {
    }

    private record Win(int id, String ownerName, String kind, long createdAt) {
    }
}
