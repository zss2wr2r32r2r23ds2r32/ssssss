package com.sharded.core.modules.backpack;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.OfflinePlayers;
import com.sharded.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * /backpack [player] - single-slot extra storage (SQLite).
 * View/edit your own backpack, or view others (including offline) with permission.
 */
public final class BackpackModule extends Module implements CommandExecutor, TabCompleter {

    private static final int SLOT = 13;

    private static final class BackpackHolder implements InventoryHolder {
        private final UUID owner;
        private final boolean readOnly;
        private Inventory inventory;

        private BackpackHolder(UUID owner, boolean readOnly) {
            this.owner = owner;
            this.readOnly = readOnly;
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
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof BackpackHolder) {
                player.closeInventory();
            }
        }
        if (database != null) database.close();
        database = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player viewer)) {
            send(sender, "players-only");
            return true;
        }
        if (!viewer.hasPermission("sharded.backpack.use")) {
            send(viewer, "no-permission");
            return true;
        }

        UUID targetId = viewer.getUniqueId();
        String targetName = viewer.getName();
        boolean readOnly = false;

        if (args.length >= 1) {
            if (!viewer.hasPermission("sharded.backpack.view.others")) {
                send(viewer, "no-permission-others");
                return true;
            }
            OfflinePlayer target = OfflinePlayers.resolve(args[0]);
            targetId = target.getUniqueId();
            targetName = target.getName() == null ? args[0] : target.getName();
            readOnly = !viewer.getUniqueId().equals(targetId);
        }

        open(viewer, targetId, targetName, readOnly);
        return true;
    }

    private void open(Player viewer, UUID ownerId, String ownerName, boolean readOnly) {
        BackpackHolder holder = new BackpackHolder(ownerId, readOnly);
        String title = Text.apply(config.getString("title", "&5Backpack &8- &7%player%"),
                "%player%", ownerName);
        Inventory inventory = Bukkit.createInventory(holder, 27, Text.c(title));
        holder.inventory = inventory;

        ItemStack[] stored = database.load(ownerId);
        if (stored.length > 0 && stored[0] != null) {
            inventory.setItem(SLOT, stored[0]);
        }

        viewer.openInventory(inventory);
        if (config.getBoolean("play-sound", true)) {
            viewer.playSound(viewer.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.6f, 1.3f);
        }
        if (readOnly) {
            send(viewer, "viewing-other", "%player%", ownerName);
        } else {
            send(viewer, "opened");
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BackpackHolder holder)) return;
        if (holder.readOnly) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) send(player, "read-only");
        } else if (event.getClickedInventory() == event.getView().getTopInventory() && event.getSlot() != SLOT) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof BackpackHolder holder)) return;
        if (holder.readOnly) return;
        ItemStack item = event.getInventory().getItem(SLOT);
        ItemStack[] data = new ItemStack[]{item == null ? null : item.clone()};
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            if (database != null) database.save(holder.owner, data);
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("sharded.backpack.view.others")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) names.add(p.getName());
            }
            return names;
        }
        return List.of();
    }
}
