package com.sharded.core.modules.modulesadmin;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.module.ModuleManager;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-game module enable/disable GUI. */
public final class ModulesAdminModule extends Module implements CommandExecutor {

    private static final class AdminHolder implements InventoryHolder {
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    public ModulesAdminModule(ShardedCore plugin) {
        super(plugin, "modulesadmin");
    }

    @Override
    protected void onEnable() {
        registerCommand("module", this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.modules.admin")) {
            send(player, "no-permission");
            return true;
        }
        openGui(player);
        return true;
    }

    private void openGui(Player player) {
        Map<String, List<String>> grouped = groupedModules();
        int rows = Math.min(6, Math.max(3, (grouped.size() + 8) / 9));
        Inventory inv = Bukkit.createInventory(new AdminHolder(), rows * 9, Text.c(raw("gui-title")));
        ((AdminHolder) inv.getHolder()).inventory = inv;

        int slot = 0;
        for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
            if (slot >= inv.getSize()) break;
            inv.setItem(slot++, new ItemBuilder(Material.BOOK)
                    .name("&f" + capitalize(entry.getKey()))
                    .lore(List.of("&7Category folder", "&7" + entry.getValue().size() + " modules"))
                    .build());
        }

        ModuleManager manager = plugin.modules();
        for (com.sharded.core.module.Module module : manager.allModules()) {
            if (slot >= inv.getSize()) break;
            boolean enabled = plugin.getConfig().getBoolean("modules." + module.id(), true);
            Material mat = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
            inv.setItem(slot++, new ItemBuilder(mat)
                    .name((enabled ? "&a" : "&c") + module.id())
                    .lore(List.of("&7Click to toggle", "&7Category: &f" + module.categoryLabel()))
                    .build());
        }
        player.openInventory(inv);
    }

    private Map<String, List<String>> groupedModules() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (com.sharded.core.module.Module module : plugin.modules().allModules()) {
            String cat = module.categoryLabel();
            map.computeIfAbsent(cat.isBlank() ? "core" : cat, k -> new ArrayList<>()).add(module.id());
        }
        return map;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof AdminHolder)) return;
        event.setCancelled(true);
        if (event.getCurrentItem() == null || !event.getCurrentItem().hasItemMeta()) return;
        String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(event.getCurrentItem().getItemMeta().displayName());
        if (name == null || name.isBlank()) return;
        String id = name.toLowerCase().replace(" ", "");
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.isConfigurationSection("modules") || !cfg.getConfigurationSection("modules").contains(id)) return;
        boolean now = !cfg.getBoolean("modules." + id, true);
        cfg.set("modules." + id, now);
        plugin.saveConfig();
        send(player, now ? "enabled" : "disabled", "%module%", id);
        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.modules().reload(), 5L);
        openGui(player);
    }

    private static String capitalize(String s) {
        if (s == null || s.isBlank()) return "Core";
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
