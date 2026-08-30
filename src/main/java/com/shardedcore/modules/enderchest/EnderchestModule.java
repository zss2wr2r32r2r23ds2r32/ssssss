package com.shardedcore.modules.enderchest;

import com.shardedcore.data.TimedPerks;
import com.shardedcore.ShardedCore;
import com.shardedcore.database.Sqlite;
import com.shardedcore.gui.Menus;
import com.shardedcore.module.Module;
import com.shardedcore.modules.combat.CombatModule;
import com.shardedcore.util.Items;
import com.shardedcore.util.Perms;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class EnderchestModule extends Module implements CommandExecutor {

    private Sqlite sqlite;

    public EnderchestModule(ShardedCore plugin) {
        super(plugin, "enderchest");
    }

    @Override
    public void enable() {
        sqlite = plugin.toggles().sqlite();
        try {
            sqlite.run("""
                    CREATE TABLE IF NOT EXISTS enderchest_items (
                        uuid TEXT NOT NULL,
                        slot INTEGER NOT NULL,
                        item TEXT NOT NULL,
                        PRIMARY KEY (uuid, slot)
                    )
                    """);
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create enderchest table", ex);
        }
        registerCommand("ec", this);
    }

    @Override
    public void disable() {
        cleanup();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        CombatModule combat = plugin.modules().get(CombatModule.class);
        if (combat != null && combat.tagged(player) && !config.getBoolean("allow-in-combat", false)) {
            send(player, "in-combat");
            return true;
        }
        if (!player.hasPermission("shardedcore.ec") && !TimedPerks.has(player.getUniqueId(), "ec")) {
            send(player, "no-permission");
            return true;
        }
        open(player);
        return true;
    }

    private void open(Player player) {
        int rows = Perms.highest(player, "shardedcore.ec.", 1, 6, config.getInt("default-rows", 3));
        Map<Integer, ItemStack> stored = load(player.getUniqueId());
        if (stored.isEmpty()) {
            ItemStack[] vanilla = player.getEnderChest().getContents();
            for (int i = 0; i < vanilla.length; i++) {
                if (vanilla[i] != null && !vanilla[i].getType().isAir()) stored.put(i, vanilla[i]);
            }
        }
        Menus.Menu menu = plugin.menus().create(player, cfg("title", "&8Ender Chest"), rows).unlocked();
        int size = rows * 9;
        for (Map.Entry<Integer, ItemStack> entry : stored.entrySet()) {
            if (entry.getKey() >= 0 && entry.getKey() < size) menu.set(entry.getKey(), entry.getValue());
        }
        menu.onClose(closed -> save(closed, menu.inventory(), size));
        plugin.menus().open(player, menu);
    }

    public void wipe(UUID uuid) {
        try {
            sqlite.execute("DELETE FROM enderchest_items WHERE uuid = ?", uuid.toString());
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to wipe enderchest", ex);
        }
        Player player = org.bukkit.Bukkit.getPlayer(uuid);
        if (player != null) player.getEnderChest().clear();
    }

    private Map<Integer, ItemStack> load(UUID uuid) {
        Map<Integer, ItemStack> items = new HashMap<>();
        try {
            sqlite.query("SELECT slot, item FROM enderchest_items WHERE uuid = ?", rs -> {
                try {
                    while (rs.next()) {
                        ItemStack item = Items.deserialize(rs.getString("item"));
                        if (item != null) items.put(rs.getInt("slot"), item);
                    }
                } catch (SQLException ex) {
                    throw new IllegalStateException(ex);
                }
                return items;
            }, uuid.toString());
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to load enderchest", ex);
        }
        return items;
    }

    private void save(Player player, Inventory top, int size) {
        UUID uuid = player.getUniqueId();
        try {
            sqlite.execute("DELETE FROM enderchest_items WHERE uuid = ?", uuid.toString());
            for (int slot = 0; slot < Math.min(size, top.getSize()); slot++) {
                ItemStack item = top.getItem(slot);
                if (item == null || item.getType().isAir()) continue;
                sqlite.execute("INSERT INTO enderchest_items (uuid, slot, item) VALUES (?, ?, ?)",
                        uuid.toString(), slot, Items.serialize(item));
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save enderchest", ex);
        }
        Inventory vanilla = player.getEnderChest();
        vanilla.clear();
        for (int slot = 0; slot < Math.min(27, Math.min(size, top.getSize())); slot++) {
            ItemStack item = top.getItem(slot);
            if (item != null) vanilla.setItem(slot, item.clone());
        }
    }
}
