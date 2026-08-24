package com.sharded.core.modules.rewards;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.Text;
import com.sharded.core.util.TrackedInventories;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/** /rewards hub — daily and weekly reward menus. */
public final class RewardsModule extends Module implements CommandExecutor {

    static final class Holder implements InventoryHolder {
        Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public RewardsModule(ShardedCore plugin) {
        super(plugin, "rewards");
    }

    @Override
    protected void onEnable() {
        registerCommand("rewards", this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        openHub(player);
        return true;
    }

    void openHub(Player player) {
        String title = config.getString("gui.title", "&8Rewards");
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 27, Text.c(title));
        holder.inventory = inv;
        TrackedInventories.track(inv, holder);

        ItemStack pane = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);

        inv.setItem(11, hubItem("daily", Material.SUNFLOWER, 11));
        inv.setItem(15, hubItem("weekly", Material.NETHER_STAR, 15));
        player.openInventory(inv);
    }

    private ItemStack hubItem(String key, Material mat, int slot) {
        String color = config.getString("gui.colors." + key, "&e");
        return new ItemBuilder(mat)
                .name(config.getString("gui.items." + key + ".name", color + "&l" + key))
                .lore(config.getStringList("gui.items." + key + ".lore"))
                .build();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (TrackedInventories.lookup(event.getView().getTopInventory(), Holder.class) == null) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (event.getSlot() == 11) {
            player.closeInventory();
            player.performCommand("dailyrewards");
        } else if (event.getSlot() == 15) {
            player.closeInventory();
            player.performCommand("weeklyrewards");
        }
    }
}
