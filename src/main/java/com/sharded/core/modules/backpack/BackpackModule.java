package com.sharded.core.modules.backpack;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.tokens.TokenService;
import com.sharded.core.util.Numbers;
import com.sharded.core.util.OfflinePlayers;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import org.bukkit.Sound;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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

import java.util.UUID;

/**
 * /backpack [player] - extra storage slots (SQLite).
 * Default: 1 slot. Permissions sharded.backpack.slots.N unlock more (up to 9).
 * Admins with sharded.backpack.admin can view and take items from any backpack.
 */
public final class BackpackModule extends Module implements CommandExecutor, TabCompleter {

    private static final class BackpackHolder implements InventoryHolder {
        private final UUID owner;
        private final boolean readOnly;
        private final int[] slots;
        private Inventory inventory;

        private BackpackHolder(UUID owner, boolean readOnly, int[] slots) {
            this.owner = owner;
            this.readOnly = readOnly;
            this.slots = slots;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        private boolean isStorageSlot(int slot) {
            for (int s : slots) if (s == slot) return true;
            return false;
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

    private int maxSlots() {
        return Math.min(9, Math.max(1, config.getInt("max-slots", 9)));
    }

    private int allowedSlots(Player player) {
        if (player == null) return Math.max(1, config.getInt("default-slots", 1));
        int max = maxSlots();
        int slots = Math.min(max, Math.max(1, config.getInt("default-slots", 1)));
        for (int i = max; i > slots; i--) {
            if (player.hasPermission("sharded.backpack.slots." + i)) return i;
        }
        return slots;
    }

    /** Called from token shop [backpack_buy] slotCount tokens */
    public boolean tryPurchaseSlot(Player player, int slotCount, long cost) {
        if (!player.hasPermission("sharded.tokenshop.use")) {
            send(player, "no-permission");
            return false;
        }
        if (slotCount < 2 || slotCount > maxSlots()) return false;
        String perm = "sharded.backpack.slots." + slotCount;
        if (player.hasPermission(perm)) {
            send(player, "slot-owned", "%slots%", String.valueOf(slotCount));
            return false;
        }
        if (slotCount > 2 && !player.hasPermission("sharded.backpack.slots." + (slotCount - 1))) {
            send(player, "slot-needs-previous", "%slots%", String.valueOf(slotCount - 1));
            return false;
        }

        TokenService tokens = plugin.modules().tokens();
        if (tokens == null) return false;
        long balance = tokens.getBalance(player.getUniqueId());
        if (balance < cost) {
            send(player, "not-enough-tokens", "%missing%", Numbers.format(cost - balance));
            return false;
        }
        if (!tokens.take(player.getUniqueId(), cost)) {
            send(player, "not-enough-tokens", "%missing%", Numbers.format(cost - balance));
            return false;
        }

        if (!plugin.luckPerms().isAvailable()) {
            tokens.give(player.getUniqueId(), cost);
            send(player, "lp-missing");
            return false;
        }
        plugin.luckPerms().runConsole("lp user " + player.getName() + " permission set " + perm + " true");
        send(player, "slot-purchased", "%slots%", String.valueOf(slotCount));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        return true;
    }

    private int[] storageSlots(int count) {
        int start = (9 - count) / 2;
        int[] slots = new int[count];
        for (int i = 0; i < count; i++) slots[i] = start + i;
        return slots;
    }

    private int displaySlotCount(Player owner, ItemStack[] stored) {
        return allowedSlots(owner);
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
        boolean viewingOther = false;

        if (args.length >= 1) {
            boolean canView = viewer.hasPermission("sharded.backpack.view.others")
                    || viewer.hasPermission("sharded.backpack.admin");
            if (!canView) {
                send(viewer, "no-permission-others");
                return true;
            }
            OfflinePlayer target = OfflinePlayers.resolve(args[0]);
            targetId = target.getUniqueId();
            targetName = target.getName() == null ? args[0] : target.getName();
            viewingOther = !viewer.getUniqueId().equals(targetId);
        }

        Player targetOnline = Bukkit.getPlayer(targetId);
        ItemStack[] stored = database.load(targetId);
        int slotCount = displaySlotCount(targetOnline, stored);
        boolean adminEdit = viewingOther && viewer.hasPermission("sharded.backpack.admin");
        boolean readOnly = viewingOther && !adminEdit;
        open(viewer, targetId, targetName, readOnly, slotCount, stored, adminEdit);
        return true;
    }

    private void open(Player viewer, UUID ownerId, String ownerName, boolean readOnly, int slotCount,
                      ItemStack[] stored, boolean adminView) {
        int[] slots = storageSlots(slotCount);
        BackpackHolder holder = new BackpackHolder(ownerId, readOnly, slots);
        String titleKey = adminView ? "title-admin" : "title";
        String title = Text.apply(config.getString(titleKey, config.getString("title", "&5Backpack &8- &7%player%")),
                "%player%", ownerName);
        Inventory inventory = Bukkit.createInventory(holder, 9, Text.c(title));
        holder.inventory = inventory;

        Material fillerMat = Material.BLACK_STAINED_GLASS_PANE;
        String fillerName = " ";
        if (config.isConfigurationSection("filler")) {
            fillerMat = Material.matchMaterial(config.getString("filler.material", "BLACK_STAINED_GLASS_PANE").toUpperCase(java.util.Locale.ROOT));
            if (fillerMat == null) fillerMat = Material.BLACK_STAINED_GLASS_PANE;
            fillerName = config.getString("filler.name", " ");
        }
        ItemStack filler = new ItemStack(fillerMat);
        var fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.displayName(Text.c(fillerName));
            filler.setItemMeta(fillerMeta);
        }
        for (int i = 0; i < 9; i++) inventory.setItem(i, filler);

        for (int i = 0; i < slots.length; i++) {
            if (i < stored.length && stored[i] != null && !stored[i].getType().isAir()) {
                inventory.setItem(slots[i], stored[i]);
            } else {
                inventory.setItem(slots[i], null);
            }
        }

        viewer.openInventory(inventory);
        if (config.getBoolean("play-sound", true)) {
            viewer.playSound(viewer.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.6f, 1.3f);
        }
        if (adminView) send(viewer, "admin-view", "%player%", ownerName);
        else if (readOnly) send(viewer, "viewing-other", "%player%", ownerName);
        else send(viewer, "opened");
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BackpackHolder holder)) return;
        if (holder.readOnly) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) send(player, "read-only");
            return;
        }
        if (event.getClickedInventory() == event.getView().getTopInventory() && !holder.isStorageSlot(event.getSlot())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof BackpackHolder holder)) return;
        if (holder.readOnly) return;
        ItemStack[] data = new ItemStack[holder.slots.length];
        for (int i = 0; i < holder.slots.length; i++) {
            ItemStack item = event.getInventory().getItem(holder.slots[i]);
            data[i] = item == null || item.getType().isAir() ? null : item.clone();
        }
        Player ownerOnline = Bukkit.getPlayer(holder.owner);
        int allowed = ownerOnline == null ? data.length : allowedSlots(ownerOnline);
        if (data.length > allowed) {
            ItemStack[] trimmed = new ItemStack[allowed];
            System.arraycopy(data, 0, trimmed, 0, allowed);
            data = trimmed;
        }
        final ItemStack[] saveData = data;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            if (database != null) database.save(holder.owner, saveData);
        });
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && (sender.hasPermission("sharded.backpack.view.others")
                || sender.hasPermission("sharded.backpack.admin"))) {
            return TabCompleteHelper.onlinePlayers(args[0]);
        }
        return java.util.List.of();
    }
}
