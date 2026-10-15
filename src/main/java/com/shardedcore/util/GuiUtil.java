package com.shardedcore.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class GuiUtil {

    private GuiUtil() {
    }

    public static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        if (name != null) meta.displayName(ColorUtil.parse(name));
        if (lore != null && !lore.isEmpty()) {
            List<Component> lines = new ArrayList<>();
            for (String line : lore) lines.add(ColorUtil.parse(line));
            meta.lore(lines);
        }
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
        stack.setItemMeta(meta);
        return stack;
    }

    public static ItemStack filler(ConfigurationSection section) {
        Material material = Material.matchMaterial(section == null ? "BLACK_STAINED_GLASS_PANE"
                : section.getString("material", "BLACK_STAINED_GLASS_PANE"));
        if (material == null) material = Material.BLACK_STAINED_GLASS_PANE;
        String name = section == null ? " " : section.getString("name", " ");
        return item(material, name, List.of());
    }

    public static Location readLocation(ConfigurationSection section) {
        if (section == null || !section.isString("world")) return null;
        World world = Bukkit.getWorld(section.getString("world", ""));
        if (world == null) return null;
        return new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
    }

    public static void writeLocation(ConfigurationSection section, Location location) {
        if (section == null || location == null || location.getWorld() == null) return;
        section.set("world", location.getWorld().getName());
        section.set("x", location.getX());
        section.set("y", location.getY());
        section.set("z", location.getZ());
        section.set("yaw", location.getYaw());
        section.set("pitch", location.getPitch());
    }
}
