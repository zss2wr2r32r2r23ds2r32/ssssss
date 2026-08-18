package com.sharded.core.modules.backpack;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * /backpack (/bp) - per-player extra storage backed by a SQLite database.
 * Size is permission based:
 *   base rows (config) + N extra rows from sharded.backpack.level.N
 * e.g. sharded.backpack.level.2 = +2 rows. The highest level the player has wins.
 */
public final class BackpackModule extends Module implements CommandExecutor {

    private static final class BackpackHolder implements InventoryHolder {
        private final UUID owner;
        private Inventory inventory;

        private BackpackHolder(UUID owner) {
            this.owner = owner;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private BackpackDatabase database;

    public BackpackModule(ShardedCore plugin) {
        super(plugin, "backpack");
    }

    @Override
    protected void onEnable() {
        try {
            database = new BackpackDatabase(plugin, moduleFolder());
        } catch (Exception e) {
            throw new IllegalStateException("Could not open backpack database", e);
        }
        registerCommand("backpack", this);
    }

    @Override
    protected void onDisable() {
        // Force-close any open backpacks so their contents get saved.
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof BackpackHolder) {
                player.closeInventory();
            }
        }
        if (database != null) database.close();
        database = null;
    }

    /** Backpack size in slots for the player's permissions. */
    public int sizeFor(Player player) {
        int baseRows = Math.max(1, Math.min(6, config.getInt("base-rows", 1)));
        int maxExtra = Math.max(0, config.getInt("max-extra-rows", 5));
        int extra = 0;
        for (int level = maxExtra; level >= 1; level--) {
            if (player.hasPermission("sharded.backpack.level." + level)) {
                extra = level;
                break;
            }
        }
        return Math.min(6, baseRows + extra) * 9;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.backpack.use")) {
            send(player, "no-permission");
            return true;
        }

        int size = sizeFor(player);
        BackpackHolder holder = new BackpackHolder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, size,
                Text.c(Text.apply(config.getString("title", "&5Backpack &8- &7%player%"), "%player%", player.getName())));
        holder.inventory = inventory;

        ItemStack[] stored = database.load(player.getUniqueId());
        for (int i = 0; i < stored.length && i < size; i++) {
            inventory.setItem(i, stored[i]);
        }
        // If the backpack shrank (lost permissions), give overflow back to the player.
        for (int i = size; i < stored.length; i++) {
            if (stored[i] != null) {
                player.getInventory().addItem(stored[i]).values()
                        .forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            }
        }

        player.openInventory(inventory);
        if (config.getBoolean("play-sound", true)) {
            player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.6f, 1.3f);
        }
        send(player, "opened", "%rows%", String.valueOf(size / 9));
        return true;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof BackpackHolder holder)) return;
        ItemStack[] contents = event.getInventory().getContents();
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] == null ? null : contents[i].clone();
        }
        // Save off the main thread - SQLite writes shouldn't lag the server.
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            if (database != null) database.save(holder.owner, copy);
        });
    }
}
