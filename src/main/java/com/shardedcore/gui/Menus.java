package com.shardedcore.gui;

import com.shardedcore.ShardedCore;
import com.shardedcore.util.ColorUtil;
import com.shardedcore.util.Items;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Lightweight click GUI. All work stays off extra scheduler tasks. */
public final class Menus implements Listener {

    private final ShardedCore plugin;
    private final Map<UUID, Menu> open = new HashMap<>();

    public Menus(ShardedCore plugin) {
        this.plugin = plugin;
    }

    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public Menu create(Player player, String title, int rows) {
        return new Menu(player, title, rows);
    }

    public void open(Player player, Menu menu) {
        open.put(player.getUniqueId(), menu);
        player.openInventory(menu.inventory);
    }

    public void close(Player player) {
        open.remove(player.getUniqueId());
        player.closeInventory();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Menu menu = open.get(player.getUniqueId());
        if (menu == null || event.getInventory() != menu.inventory) return;
        if (menu.locked) event.setCancelled(true);
        if (event.getClickedInventory() != menu.inventory) {
            if (menu.locked) event.setCancelled(true);
            if (menu.bottomClick != null && event.getClickedInventory() == player.getInventory()) {
                menu.bottomClick.accept(event);
            }
            return;
        }
        Consumer<InventoryClickEvent> handler = menu.clicks.get(event.getRawSlot());
        if (handler != null) handler.accept(event);
        if (menu.anyClick != null) menu.anyClick.accept(event);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Menu menu = open.get(player.getUniqueId());
        if (menu == null || event.getInventory() != menu.inventory) return;
        if (menu.locked) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Menu menu = open.get(player.getUniqueId());
        if (menu == null || event.getInventory() != menu.inventory) return;
        open.remove(player.getUniqueId());
        if (menu.close != null) menu.close.accept(player);
    }

    public static final class Menu implements InventoryHolder {
        private final Inventory inventory;
        private final Map<Integer, Consumer<InventoryClickEvent>> clicks = new HashMap<>();
        private boolean locked = true;
        private Consumer<InventoryClickEvent> anyClick;
        private Consumer<InventoryClickEvent> bottomClick;
        private Consumer<Player> close;

        private Menu(Player player, String title, int rows) {
            this.inventory = Bukkit.createInventory(this, Math.max(1, Math.min(6, rows)) * 9, ColorUtil.parse(title));
        }

        public Inventory inventory() {
            return inventory;
        }

        public Menu unlocked() {
            this.locked = false;
            return this;
        }

        public Menu set(int slot, ItemStack item) {
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, item == null ? null : Items.hideBundleBits(item.clone()));
            }
            return this;
        }

        public Menu set(int slot, ItemStack item, Consumer<InventoryClickEvent> click) {
            set(slot, item);
            if (click != null) clicks.put(slot, click);
            return this;
        }

        public Menu fill(ItemStack item) {
            ItemStack filler = item == null ? null : Items.hideBundleBits(item.clone());
            for (int i = 0; i < inventory.getSize(); i++) {
                if (inventory.getItem(i) == null) inventory.setItem(i, filler);
            }
            return this;
        }

        public Menu onAny(Consumer<InventoryClickEvent> click) {
            this.anyClick = click;
            return this;
        }

        public Menu onBottom(Consumer<InventoryClickEvent> click) {
            this.bottomClick = click;
            return this;
        }

        public Menu onClose(Consumer<Player> close) {
            this.close = close;
            return this;
        }

        public Menu border(ItemStack item, BiConsumer<Integer, ItemStack> ignored) {
            int size = inventory.getSize();
            int rows = size / 9;
            for (int i = 0; i < size; i++) {
                int row = i / 9;
                int col = i % 9;
                if (row == 0 || row == rows - 1 || col == 0 || col == 8) {
                    inventory.setItem(i, item);
                }
            }
            return this;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
