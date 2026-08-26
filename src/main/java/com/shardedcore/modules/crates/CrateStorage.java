package com.shardedcore.modules.crates;

import com.shardedcore.ShardedCore;
import com.shardedcore.util.EventRewards;
import com.shardedcore.util.GuiUtil;
import com.shardedcore.util.ItemBuilder;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.*;
import java.util.*;

public final class CrateStorage {

    private final ShardedCore plugin;
    private final File root;
    private final NamespacedKey keyType;
    private final Map<String, YamlConfiguration> crates = new LinkedHashMap<>();
    private final Map<String, Location> placed = new HashMap<>();

    CrateStorage(ShardedCore plugin, File root) {
        this.plugin = plugin;
        this.root = root;
        this.keyType = new NamespacedKey(plugin, "crate-key");
    }

    void loadAll() {
        crates.clear(); placed.clear();
        File[] dirs = root.listFiles(File::isDirectory);
        if (dirs == null) return;
        for (File dir : dirs) {
            File cfg = new File(dir, "crate.yml");
            if (!cfg.exists()) continue;
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(cfg);
            crates.put(dir.getName(), yaml);
            Location loc = GuiUtil.readLocation(yaml.getConfigurationSection("location"));
            if (loc != null) placed.put(dir.getName(), loc);
        }
    }

    List<String> listIds() { return new ArrayList<>(crates.keySet()); }
    boolean exists(String id) { return crates.containsKey(id); }
    YamlConfiguration get(String id) { return crates.get(id); }

    void create(String id) {
        new File(root, id).mkdirs();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("display-name", id);
        yaml.set("key.material", "TRIPWIRE_HOOK");
        yaml.set("key.name", "&e" + id + " Key");
        yaml.set("rewards", List.of(Map.of("name", "Sample Reward", "material", "DIAMOND", "money", 100)));
        save(id, yaml);
    }

    void delete(String id) { crates.remove(id); placed.remove(id); deleteDir(new File(root, id)); }

    void save(String id, YamlConfiguration yaml) {
        try { yaml.save(new File(new File(root, id), "crate.yml")); } catch (IOException e) { plugin.getLogger().warning(e.getMessage()); }
        crates.put(id, yaml);
    }

    void place(String id, Location loc) {
        YamlConfiguration yaml = crates.get(id); if (yaml == null) return;
        GuiUtil.writeLocation(yaml.createSection("location"), loc);
        save(id, yaml); placed.put(id, loc.clone());
    }

    void unplace(String id) { YamlConfiguration yaml = crates.get(id); if (yaml == null) return; yaml.set("location", null); save(id, yaml); placed.remove(id); }

    String crateAt(Location loc) {
        for (var e : placed.entrySet()) {
            Location l = e.getValue();
            if (l.getWorld()!=null && loc.getWorld()!=null && l.getWorld().equals(loc.getWorld())
                    && l.getBlockX()==loc.getBlockX() && l.getBlockY()==loc.getBlockY() && l.getBlockZ()==loc.getBlockZ()) return e.getKey();
        }
        return null;
    }

    boolean hasKey(Player player, String crateId) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || !stack.hasItemMeta()) continue;
            String t = stack.getItemMeta().getPersistentDataContainer().get(keyType, PersistentDataType.STRING);
            if (crateId.equals(t)) return true;
        }
        return false;
    }

    void consumeKey(Player player, String crateId) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack == null || !stack.hasItemMeta()) continue;
            if (!crateId.equals(stack.getItemMeta().getPersistentDataContainer().get(keyType, PersistentDataType.STRING))) continue;
            stack.setAmount(stack.getAmount() - 1); return;
        }
    }

    ItemStack createKey(String crateId, int amount) {
        YamlConfiguration yaml = crates.get(crateId);
        Material mat = Material.TRIPWIRE_HOOK;
        String name = "&e" + crateId + " Key";
        if (yaml != null && yaml.isConfigurationSection("key")) {
            Material m = Material.matchMaterial(yaml.getString("key.material", "TRIPWIRE_HOOK"));
            if (m != null) mat = m;
            name = yaml.getString("key.name", name);
        }
        ItemStack key = new ItemBuilder(mat).name(name).build();
        key.setAmount(amount);
        ItemMeta meta = key.getItemMeta();
        if (meta != null) { meta.getPersistentDataContainer().set(keyType, PersistentDataType.STRING, crateId); key.setItemMeta(meta); }
        return key;
    }

    void grantReward(Player player, String crateId) {
        YamlConfiguration yaml = crates.get(crateId);
        if (yaml == null) return;
        List<Map<?, ?>> rewards = yaml.getMapList("rewards");
        if (rewards.isEmpty()) return;
        Map<?, ?> picked = rewards.get(new Random().nextInt(rewards.size()));
        YamlConfiguration section = new YamlConfiguration();
        for (var e : picked.entrySet()) section.set(String.valueOf(e.getKey()), e.getValue());
        EventRewards.grant(plugin, player.getUniqueId(), section);
    }

    private void deleteDir(File dir) {
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) { if (f.isDirectory()) deleteDir(f); else f.delete(); }
        dir.delete();
    }
}
