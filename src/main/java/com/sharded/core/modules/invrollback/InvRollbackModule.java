package com.sharded.core.modules.invrollback;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.OfflinePlayers;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import com.sharded.core.util.TrackedInventories;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.sql.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Inventory snapshots and rollback GUI. */
public final class InvRollbackModule extends Module implements CommandExecutor, TabCompleter {

    public enum SnapshotReason { AUTO, JOIN, QUIT, DEATH }

    public record Snapshot(long id, UUID uuid, SnapshotReason reason, long createdAt, ItemStack[] contents,
                           ItemStack[] armor, ItemStack[] offhand) {
    }

    private Connection connection;
    private BukkitTask snapshotTask;
    private final Map<UUID, GuiContext> guiContexts = new ConcurrentHashMap<>();

    private record GuiContext(UUID target, SnapshotReason category, int page) {
    }

    public InvRollbackModule(ShardedCore plugin) {
        super(plugin, "invrollback");
    }

    @Override
    protected void onEnable() {
        try {
            File dbFile = new File(moduleFolder(), "invrollback.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement st = connection.createStatement()) {
                st.execute("""
                        CREATE TABLE IF NOT EXISTS snapshots (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            uuid TEXT NOT NULL,
                            reason TEXT NOT NULL,
                            created_at INTEGER NOT NULL,
                            contents BLOB NOT NULL,
                            armor BLOB,
                            offhand BLOB
                        )
                        """);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not open invrollback database", e);
        }
        registerListener(this);
        registerCommand("invrollback", this);
        long interval = Math.max(60L, config.getLong("snapshot-interval-minutes", 5L) * 60L * 20L);
        snapshotTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::snapshotOnline, interval, interval);
    }

    @Override
    protected void onDisable() {
        if (snapshotTask != null) snapshotTask.cancel();
        if (connection != null) {
            try { connection.close(); } catch (SQLException ignored) {}
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("sharded.invrollback.use")) {
            send(sender, "no-permission");
            return true;
        }
        if (!(sender instanceof Player staff)) {
            send(sender, "players-only");
            return true;
        }
        if (args.length == 0) {
            send(staff, "usage");
            return true;
        }
        OfflinePlayer target = OfflinePlayers.resolve(args[0]);
        if (target == null || !target.hasPlayedBefore()) {
            send(staff, "never-joined", "%player%", args[0]);
            return true;
        }
        openCategories(staff, target.getUniqueId(), OfflinePlayers.name(target.getUniqueId()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return TabCompleteHelper.knownPlayers(args[0]);
        return List.of();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (config.getBoolean("snapshot-on.join", true)) save(event.getPlayer(), SnapshotReason.JOIN);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (config.getBoolean("snapshot-on.quit", true)) save(event.getPlayer(), SnapshotReason.QUIT);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (config.getBoolean("snapshot-on.death", true)) save(event.getEntity(), SnapshotReason.DEATH);
    }

    private void snapshotOnline() {
        int perTick = config.getInt("snapshots-per-tick", 5);
        int i = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (i++ >= perTick) break;
            save(player, SnapshotReason.AUTO);
        }
    }

    private void save(Player player, SnapshotReason reason) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO snapshots (uuid, reason, created_at, contents, armor, offhand) VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, player.getUniqueId().toString());
            ps.setString(2, reason.name());
            ps.setLong(3, System.currentTimeMillis());
            ps.setBytes(4, serialize(player.getInventory().getContents()));
            ps.setBytes(5, serialize(player.getInventory().getArmorContents()));
            ps.setBytes(6, serialize(new ItemStack[]{player.getInventory().getItemInOffHand()}));
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[invrollback] save failed: " + e.getMessage());
        }
        trim(player.getUniqueId());
    }

    private void trim(UUID uuid) {
        int max = config.getInt("max-snapshots", 20);
        try (PreparedStatement ps = connection.prepareStatement("""
                DELETE FROM snapshots WHERE id NOT IN (
                    SELECT id FROM snapshots WHERE uuid = ? ORDER BY created_at DESC LIMIT ?
                ) AND uuid = ?
                """)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, max);
            ps.setString(3, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    private void openCategories(Player staff, UUID targetId, String targetName) {
        int size = config.getInt("categories.size", 27);
        Holder holder = new Holder(Holder.Type.CATEGORIES, targetId, null, 0);
        Inventory inv = Bukkit.createInventory(holder, size, Text.c(config.getString("categories.title", "Rollback | %player%").replace("%player%", targetName)));
        TrackedInventories.track(inv, holder);
        putCategory(inv, "auto", SnapshotReason.AUTO, targetId);
        putCategory(inv, "join", SnapshotReason.JOIN, targetId);
        putCategory(inv, "quit", SnapshotReason.QUIT, targetId);
        putCategory(inv, "death", SnapshotReason.DEATH, targetId);
        staff.openInventory(inv);
    }

    private void putCategory(Inventory inv, String key, SnapshotReason reason, UUID uuid) {
        var section = config.getConfigurationSection("categories.items." + key);
        if (section == null) return;
        int count = count(uuid, reason);
        Material mat = Material.matchMaterial(section.getString("material", "CHEST"));
        if (mat == null) mat = Material.CHEST;
        List<String> lore = new ArrayList<>();
        for (String line : section.getStringList("lore")) {
            lore.add(line.replace("%count%", String.valueOf(count)));
        }
        inv.setItem(section.getInt("slot", 0), new ItemBuilder(mat)
                .name(section.getString("display_name", key).replace("%count%", String.valueOf(count)))
                .lore(lore).hideAll().build());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player staff)) return;
        Holder holder = TrackedInventories.lookup(event.getView().getTopInventory(), Holder.class);
        if (holder == null) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        if (holder.type() == Holder.Type.CATEGORIES) {
            int slot = event.getSlot();
            SnapshotReason reason = categoryFromSlot(slot);
            if (reason == null) return;
            openSnapshotList(staff, holder.targetId(), reason, 0);
            return;
        }
        if (holder.type() == Holder.Type.LIST) {
            ItemStack item = event.getCurrentItem();
            if (item == null || !item.hasItemMeta()) return;
            long id = item.getItemMeta().getPersistentDataContainer()
                    .getOrDefault(new org.bukkit.NamespacedKey(plugin, "snapshot_id"),
                            org.bukkit.persistence.PersistentDataType.LONG, -1L);
            if (id < 0) return;
            Snapshot snap = load(id);
            if (snap == null) {
                send(staff, "empty-backup");
                return;
            }
            restore(staff, snap);
        }
    }

    private SnapshotReason categoryFromSlot(int slot) {
        for (String key : List.of("auto", "join", "quit", "death")) {
            var section = config.getConfigurationSection("categories.items." + key);
            if (section != null && section.getInt("slot") == slot) {
                return SnapshotReason.valueOf(key.toUpperCase(Locale.ROOT));
            }
        }
        return null;
    }

    private void openSnapshotList(Player staff, UUID targetId, SnapshotReason reason, int page) {
        List<Snapshot> list = list(targetId, reason);
        if (list.isEmpty()) {
            send(staff, "none", "%player%", OfflinePlayers.name(targetId));
            return;
        }
        Holder holder = new Holder(Holder.Type.LIST, targetId, reason, page);
        Inventory inv = Bukkit.createInventory(holder, 54, Text.c("&8Rollback | " + reason.name()));
        TrackedInventories.track(inv, holder);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
        int slot = 0;
        for (Snapshot snap : list) {
            if (slot >= 45) break;
            String date = fmt.format(Instant.ofEpochMilli(snap.createdAt()));
            ItemStack icon = new ItemBuilder(Material.CHEST)
                    .name(config.getString("item.display_name", "%date%").replace("%date%", date))
                    .lore(config.getStringList("item.lore").stream()
                            .map(l -> l.replace("%date%", date).replace("%reason%", reason.name())
                                    .replace("%items%", String.valueOf(countItems(snap))))
                            .toList())
                    .hideAll().build();
            icon.editMeta(meta -> meta.getPersistentDataContainer().set(
                    new org.bukkit.NamespacedKey(plugin, "snapshot_id"),
                    org.bukkit.persistence.PersistentDataType.LONG, snap.id()));
            inv.setItem(slot++, icon);
        }
        staff.openInventory(inv);
    }

    private void restore(Player staff, Snapshot snap) {
        Player target = Bukkit.getPlayer(snap.uuid());
        if (target == null) {
            send(staff, "offline", "%player%", OfflinePlayers.name(snap.uuid()));
            return;
        }
        target.getInventory().setContents(clone(snap.contents()));
        target.getInventory().setArmorContents(clone(snap.armor()));
        if (snap.offhand() != null && snap.offhand().length > 0) {
            target.getInventory().setItemInOffHand(snap.offhand()[0]);
        }
        target.updateInventory();
        send(staff, "restored", "%player%", target.getName());
    }

    private int countItems(Snapshot snap) {
        int n = 0;
        for (ItemStack item : snap.contents()) if (item != null && !item.getType().isAir()) n++;
        return n;
    }

    private int count(UUID uuid, SnapshotReason reason) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM snapshots WHERE uuid = ? AND reason = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, reason.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    private List<Snapshot> list(UUID uuid, SnapshotReason reason) {
        List<Snapshot> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM snapshots WHERE uuid = ? AND reason = ? ORDER BY created_at DESC LIMIT 45")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, reason.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(read(rs));
            }
        } catch (SQLException ignored) {
        }
        return out;
    }

    private Snapshot load(long id) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM snapshots WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? read(rs) : null;
            }
        } catch (SQLException e) {
            return null;
        }
    }

    private Snapshot read(ResultSet rs) throws SQLException {
        return new Snapshot(
                rs.getLong("id"),
                UUID.fromString(rs.getString("uuid")),
                SnapshotReason.valueOf(rs.getString("reason")),
                rs.getLong("created_at"),
                deserialize(rs.getBytes("contents")),
                deserialize(rs.getBytes("armor")),
                deserialize(rs.getBytes("offhand"))
        );
    }

    private byte[] serialize(ItemStack[] items) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
            out.writeInt(items.length);
            for (ItemStack item : items) out.writeObject(item);
            return bytes.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private ItemStack[] deserialize(byte[] data) {
        if (data == null || data.length == 0) return new ItemStack[0];
        try (BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(data))) {
            int len = in.readInt();
            ItemStack[] items = new ItemStack[len];
            for (int i = 0; i < len; i++) items[i] = (ItemStack) in.readObject();
            return items;
        } catch (Exception e) {
            return new ItemStack[0];
        }
    }

    private ItemStack[] clone(ItemStack[] items) {
        ItemStack[] copy = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) copy[i] = items[i] == null ? null : items[i].clone();
        return copy;
    }

    private static final class Holder implements InventoryHolder {
        enum Type { CATEGORIES, LIST }

        private final Type type;
        private final UUID targetId;
        private final SnapshotReason reason;
        private final int page;

        Holder(Type type, UUID targetId, SnapshotReason reason, int page) {
            this.type = type;
            this.targetId = targetId;
            this.reason = reason;
            this.page = page;
        }

        Type type() { return type; }
        UUID targetId() { return targetId; }

        @Override
        public Inventory getInventory() { return null; }
    }
}
