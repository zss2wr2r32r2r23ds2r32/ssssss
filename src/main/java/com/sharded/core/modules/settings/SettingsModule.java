package com.sharded.core.modules.settings;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.chat.ChatToggleModule;
import com.sharded.core.modules.nightvision.NightVisionModule;
import com.sharded.core.modules.privatemessages.PrivateMessagesModule;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * /settings - a personal settings GUI with toggles for public chat,
 * private messages and night vision. Slots and icons are configurable.
 */
public final class SettingsModule extends Module implements CommandExecutor {

    private static final class SettingsHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public SettingsModule(ShardedCore plugin) {
        super(plugin, "settings");
    }

    @Override
    protected void onEnable() {
        registerCommand("settings", this);
    }

    @Override
    protected void onDisable() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof SettingsHolder) {
                player.closeInventory();
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.settings.use")) {
            send(player, "no-permission");
            return true;
        }
        open(player);
        return true;
    }

    private void open(Player player) {
        SettingsHolder holder = new SettingsHolder();
        Inventory inventory = Bukkit.createInventory(holder, 27, Text.c(config.getString("title", "&8Settings")));
        holder.inventory = inventory;
        render(player, inventory);
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
    }

    private void render(Player player, Inventory inventory) {
        var filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name("&r").build();
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);

        ChatToggleModule chat = plugin.modules().get(ChatToggleModule.class);
        PrivateMessagesModule pms = plugin.modules().get(PrivateMessagesModule.class);
        NightVisionModule nv = plugin.modules().get(NightVisionModule.class);

        if (chat != null && chat.isEnabled()) {
            boolean on = chat.isChatEnabled(player);
            inventory.setItem(config.getInt("slots.chat", 11), toggleItem(
                    Material.valueOf(config.getString("icons.chat", "OAK_SIGN")),
                    raw("item-chat"), on));
        }
        if (pms != null && pms.isEnabled()) {
            boolean on = pms.isMsgEnabled(player);
            inventory.setItem(config.getInt("slots.msg", 13), toggleItem(
                    Material.valueOf(config.getString("icons.msg", "WRITABLE_BOOK")),
                    raw("item-msg"), on));
        }
        if (nv != null && nv.isEnabled()) {
            boolean on = nv.isNightVisionEnabled(player);
            inventory.setItem(config.getInt("slots.nightvision", 15), toggleItem(
                    Material.valueOf(config.getString("icons.nightvision", "ENDER_EYE")),
                    raw("item-nightvision"), on));
        }
    }

    private org.bukkit.inventory.ItemStack toggleItem(Material icon, String name, boolean on) {
        return new ItemBuilder(icon)
                .name(name)
                .lore(on ? raw("state-on") : raw("state-off"), raw("click-to-toggle"))
                .glow(on)
                .hideAll()
                .build();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SettingsHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != holder.inventory) return;

        int slot = event.getSlot();
        ChatToggleModule chat = plugin.modules().get(ChatToggleModule.class);
        PrivateMessagesModule pms = plugin.modules().get(PrivateMessagesModule.class);
        NightVisionModule nv = plugin.modules().get(NightVisionModule.class);

        if (chat != null && chat.isEnabled() && slot == config.getInt("slots.chat", 11)) {
            chat.setChatEnabled(player, !chat.isChatEnabled(player));
        } else if (pms != null && pms.isEnabled() && slot == config.getInt("slots.msg", 13)) {
            pms.setMsgEnabled(player, !pms.isMsgEnabled(player));
        } else if (nv != null && nv.isEnabled() && slot == config.getInt("slots.nightvision", 15)) {
            if (!player.hasPermission("sharded.nightvision.use")) {
                send(player, "no-permission");
                return;
            }
            nv.setNightVision(player, !nv.isNightVisionEnabled(player));
        } else {
            return;
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.8f);
        render(player, holder.inventory);
    }
}
