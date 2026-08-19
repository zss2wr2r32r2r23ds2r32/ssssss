package com.sharded.core.modules.eglow;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.ColorUtil;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.Text;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import org.bukkit.inventory.InventoryHolder;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** EGlow equip menu — requires eglow.color.* permissions from token shop. */
public final class EGlowModule extends Module implements CommandExecutor {

    private static final String MENU_TITLE = "🔥 Glows";
    private static final class GlowMenuHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private final Map<String, GlowOption> glows = new LinkedHashMap<>();

    public EGlowModule(ShardedCore plugin) {
        super(plugin, "eglow");
    }

    @Override
    protected void onEnable() {
        loadGlowOptions();
        plugin.gui().registerMenuExtras("glow", this::shopPlaceholders);
        registerCommand("eglow", this);
        registerCommand("glows", this);
        registerCommand("glowing", this);
    }

    private void loadGlowOptions() {
        glows.clear();
        var section = config.getConfigurationSection("glows");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            var glow = section.getConfigurationSection(id);
            if (glow == null) continue;
            glows.put(id, new GlowOption(
                    id,
                    glow.getInt("slot", 0),
                    glow.getString("permission", "eglow.color." + id),
                    glow.getString("command", "eglow " + id),
                    glow.getString("display-name", "&f" + id),
                    glow.getStringList("lore"),
                    parseColor(glow.getString("color", "#FFFFFF"))
            ));
        }
    }

    private Color parseColor(String raw) {
        String hex = ColorUtil.normalize(raw).replace("§x", "").replace("&x", "")
                .replace("&", "").replace("§", "");
        hex = hex.replaceAll("[^0-9A-Fa-f]", "");
        if (hex.length() >= 6) {
            try {
                return Color.fromRGB(
                        Integer.parseInt(hex.substring(0, 2), 16),
                        Integer.parseInt(hex.substring(2, 4), 16),
                        Integer.parseInt(hex.substring(4, 6), 16));
            } catch (NumberFormatException ignored) {
            }
        }
        return Color.WHITE;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("eglow.use")) {
            send(player, "no-permission");
            return true;
        }
        if (args.length == 0) {
            openMenu(player);
            return true;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("off")) {
            runGlowCommand(player, config.getString("disable-command", "eglow off"));
            send(player, "disabled");
            return true;
        }
        GlowOption glow = glows.get(sub);
        if (glow == null) {
            openMenu(player);
            return true;
        }
        applyGlow(player, glow);
        return true;
    }

    public void openMenu(Player player) {
        int rows = config.getInt("menu-rows", 4);
        Inventory inventory = plugin.getServer().createInventory(new GlowMenuHolder(), rows * 9, Text.c(MENU_TITLE));

        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").hideAll().build();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler.clone());
        }

        String ownedYes = config.getString("owned.yes", "&#9FFF00Yes");
        String ownedNo = config.getString("owned.no", "&#FF2727No");
        String ownedLine = config.getString("owned.lore-line", "%color%⚓ &fOwned: %owned%");

        for (GlowOption glow : glows.values()) {
            boolean owned = player.hasPermission(glow.permission());
            List<String> lore = new ArrayList<>();
            for (String line : glow.lore()) {
                lore.add(line.replace("%owned%", owned ? ownedYes : ownedNo));
            }
            String ownedFormatted = ownedLine
                    .replace("%owned%", owned ? ownedYes : ownedNo)
                    .replace("%color%", glow.displayName().substring(0, Math.min(20, glow.displayName().length())));
            if (config.getBoolean("owned.show-in-lore", true)) {
                lore.add("");
                lore.add(ownedFormatted);
            }
            ItemStack item = leatherChestplate(glow.color(), glow.displayName(), lore);
            inventory.setItem(glow.slot(), item);
        }

        List<String> disableLore = config.getStringList("disable.lore");
        inventory.setItem(config.getInt("disable.slot", 4),
                new ItemBuilder(Material.BARRIER)
                        .name(config.getString("disable.display-name", "&x&F&F&0&0&0&0&lDISABLE GLOW"))
                        .lore(disableLore)
                        .hideAll()
                        .build());

        player.openInventory(inventory);
    }

    private ItemStack leatherChestplate(Color color, String name, List<String> lore) {
        ItemStack stack = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta meta = (LeatherArmorMeta) stack.getItemMeta();
        if (meta != null) {
            meta.setColor(color);
            stack.setItemMeta(meta);
        }
        return new ItemBuilder(stack).name(name).lore(lore).hideAll().build();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof GlowMenuHolder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        int slot = event.getSlot();
        if (slot == config.getInt("disable.slot", 4)) {
            player.closeInventory();
            runGlowCommand(player, config.getString("disable-command", "eglow off"));
            send(player, "disabled");
            return;
        }

        for (GlowOption glow : glows.values()) {
            if (glow.slot() != slot) continue;
            player.closeInventory();
            applyGlow(player, glow);
            return;
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof GlowMenuHolder) {
            event.setCancelled(true);
        }
    }

    private void applyGlow(Player player, GlowOption glow) {
        if (!player.hasPermission(glow.permission())) {
            send(player, "no-color-permission", "%color%", glow.id());
            return;
        }
        runGlowCommand(player, glow.command());
        send(player, "applied", "%color%", glow.displayName());
    }

    private void runGlowCommand(Player player, String command) {
        if (command.startsWith("/")) command = command.substring(1);
        player.performCommand(command);
    }

    public Map<String, String> shopPlaceholders(Player player) {
        Map<String, String> map = new HashMap<>();
        String ownedYes = config.getString("owned.yes", "&#9FFF00Yes");
        String ownedNo = config.getString("owned.no", "&#FF2727No");
        for (GlowOption glow : glows.values()) {
            map.put("glow_owned_" + glow.id(), player.hasPermission(glow.permission()) ? ownedYes : ownedNo);
        }
        return map;
    }

    private record GlowOption(String id, int slot, String permission, String command,
                              String displayName, List<String> lore, Color color) {
    }
}
