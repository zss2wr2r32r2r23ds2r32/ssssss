package com.sharded.core.modules.chatcolor;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.Text;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** EZColor chat colour equip menu — requires ezcolor.color.* permissions. */
public final class ChatColorModule extends Module implements CommandExecutor {

    private static final String MENU_TITLE = "Chat Colours";
    private final Map<String, ColorOption> colors = new LinkedHashMap<>();

    private static final class MenuHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public ChatColorModule(ShardedCore plugin) {
        super(plugin, "chatcolor");
    }

    @Override
    protected void onEnable() {
        loadColors();
        registerCommand("chatcolor", this);
        registerCommand("chatcolors", this);
    }

    @Override
    protected void onDisable() {
        // Never reset player chat colours on reload.
    }

    private void loadColors() {
        colors.clear();
        var section = config.getConfigurationSection("colors");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            var color = section.getConfigurationSection(id);
            if (color == null) continue;
            colors.put(id, new ColorOption(
                    id,
                    color.getInt("slot", 0),
                    color.getString("permission", "ezcolor.color." + id),
                    color.getString("command", "ezcolor #" + id),
                    color.getString("material", "RED_DYE"),
                    color.getString("display-name", id),
                    color.getStringList("lore")
            ));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.chatcolor.use")) {
            send(player, "no-permission");
            return true;
        }
        openMenu(player);
        return true;
    }

    public void openMenu(Player player) {
        int rows = config.getInt("menu-rows", 4);
        Inventory inventory = plugin.getServer().createInventory(new MenuHolder(), rows * 9, Text.c(MENU_TITLE));
        Material fillerMat = Material.matchMaterial(config.getString("filler-material", "BLACK_STAINED_GLASS_PANE"));
        if (fillerMat == null) fillerMat = Material.BLACK_STAINED_GLASS_PANE;
        ItemStack filler = new ItemBuilder(fillerMat).name(" ").hideAll().build();
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler.clone());

        Map<String, String> ph = equipPlaceholders(player);
        for (ColorOption color : colors.values()) {
            Material mat = Material.matchMaterial(color.material());
            if (mat == null) mat = Material.RED_DYE;
            inventory.setItem(color.slot(), new ItemBuilder(mat)
                    .name(apply(color.displayName(), ph))
                    .lore(apply(color.lore(), ph))
                    .hideAll()
                    .build());
        }

        int removeSlot = config.getInt("remove.slot", 4);
        inventory.setItem(removeSlot, new ItemBuilder(Material.BARRIER)
                .name(config.getString("remove.display-name", "&c&lREMOVE CHAT COLOUR"))
                .lore(apply(config.getStringList("remove.lore"), ph))
                .hideAll()
                .build());

        player.openInventory(inventory);
    }

    public Map<String, String> equipPlaceholders(Player player) {
        Map<String, String> map = new LinkedHashMap<>();
        String yes = config.getString("placeholders.owned-yes", "&#9FFF00&nYes");
        String no = config.getString("placeholders.owned-no", "&#FF2727&nNo");
        for (ColorOption color : colors.values()) {
            map.put("chatcolor_owned_" + color.id(), player.hasPermission(color.permission()) ? yes : no);
        }
        return map;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        if (event.getSlot() == config.getInt("remove.slot", 4)) {
            player.closeInventory();
            run(player, config.getString("remove.command", "ezcolors reset"));
            send(player, "removed");
            return;
        }

        for (ColorOption color : colors.values()) {
            if (color.slot() != event.getSlot()) continue;
            player.closeInventory();
            if (!player.hasPermission(color.permission())) {
                send(player, "not-owned", "%color%", color.displayName());
                return;
            }
            run(player, color.command());
            send(player, "applied", "%color%", color.displayName());
            return;
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) event.setCancelled(true);
    }

    private void run(Player player, String command) {
        if (command.startsWith("/")) command = command.substring(1);
        player.performCommand(command);
    }

    private List<String> apply(List<String> lines, Map<String, String> ph) {
        List<String> out = new ArrayList<>();
        for (String line : lines) out.add(apply(line, ph));
        return out;
    }

    private String apply(String line, Map<String, String> ph) {
        String out = line;
        for (Map.Entry<String, String> e : ph.entrySet()) out = out.replace("%" + e.getKey() + "%", e.getValue());
        return out;
    }

    private record ColorOption(String id, int slot, String permission, String command,
                               String material, String displayName, List<String> lore) {
    }
}
