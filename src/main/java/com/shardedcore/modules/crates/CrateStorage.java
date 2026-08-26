package com.shardedcore.modules.crates;

import com.shardedcore.ShardedCore;
import com.shardedcore.util.Configs;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/** YAML crate definitions under modules/crates/crates/&lt;id&gt;.yml. */
public final class CrateStorage {

    private final ShardedCore plugin;
    private final File folder;
    private final Map<String, Crate> crates = new LinkedHashMap<>();

    public CrateStorage(ShardedCore plugin, File folder) {
        this.plugin = plugin;
        this.folder = folder;
    }

    public void load() {
        crates.clear();
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create crates folder");
            return;
        }
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;
        for (File file : files) {
            try {
                Crate crate = read(file);
                if (crate != null) crates.put(crate.id, crate);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to load crate " + file.getName(), ex);
            }
        }
    }

    public Collection<Crate> all() {
        return crates.values();
    }

    public List<String> ids() {
        return new ArrayList<>(crates.keySet());
    }

    public Crate get(String id) {
        if (id == null) return null;
        Crate exact = crates.get(id.toLowerCase(Locale.ROOT));
        if (exact != null) return exact;
        for (Crate crate : crates.values()) {
            if (crate.id.equalsIgnoreCase(id) || crate.displayName.equalsIgnoreCase(id)) return crate;
        }
        return null;
    }

    public boolean exists(String id) {
        return get(id) != null;
    }

    public Crate create(String rawName) {
        String id = sanitize(rawName);
        if (id.isEmpty() || crates.containsKey(id)) return null;
        Crate crate = new Crate(id, rawName, 3);
        crates.put(id, crate);
        save(crate);
        return crate;
    }

    public boolean delete(String id) {
        Crate crate = get(id);
        if (crate == null) return false;
        crates.remove(crate.id);
        File file = file(crate.id);
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("Could not delete crate file " + file.getName());
        }
        return true;
    }

    public void save(Crate crate) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("display-name", crate.displayName);
        yaml.set("rows", crate.rows);
        for (Map.Entry<Integer, ItemStack> entry : crate.rewards.entrySet()) {
            if (isAir(entry.getValue())) continue;
            yaml.set("rewards." + entry.getKey(), entry.getValue());
        }
        List<Map<String, Object>> locations = new ArrayList<>();
        for (BlockLoc loc : crate.locations) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("world", loc.world);
            map.put("x", loc.x);
            map.put("y", loc.y);
            map.put("z", loc.z);
            locations.add(map);
        }
        yaml.set("locations", locations);
        Configs.save(yaml, file(crate.id));
    }

    public Crate at(Block block) {
        if (block == null) return null;
        return at(block.getLocation());
    }

    public Crate at(Location location) {
        if (location == null || location.getWorld() == null) return null;
        BlockLoc target = BlockLoc.of(location);
        for (Crate crate : crates.values()) {
            for (BlockLoc loc : crate.locations) {
                if (loc.equals(target)) return crate;
            }
        }
        return null;
    }

    public static String sanitize(String name) {
        if (name == null) return "";
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
    }

    public static boolean isAir(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    public static boolean isKeyall(Crate crate) {
        return crate != null && (crate.id.equalsIgnoreCase("keyall") || crate.displayName.equalsIgnoreCase("Keyall"));
    }

    private Crate read(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String id = file.getName().substring(0, file.getName().length() - 4).toLowerCase(Locale.ROOT);
        String display = yaml.getString("display-name", id);
        int rows = Math.max(1, Math.min(6, yaml.getInt("rows", 3)));
        Crate crate = new Crate(id, display, rows);
        ConfigurationSection rewards = yaml.getConfigurationSection("rewards");
        if (rewards != null) {
            for (String key : rewards.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(key);
                    ItemStack item = rewards.getItemStack(key);
                    if (!isAir(item)) crate.rewards.put(slot, item.clone());
                } catch (NumberFormatException ignored) {
                }
            }
        } else {
            List<?> list = yaml.getList("rewards");
            if (list != null) {
                int slot = 0;
                for (Object object : list) {
                    if (object instanceof ItemStack item && !isAir(item)) {
                        crate.rewards.put(slot, item.clone());
                    }
                    slot++;
                }
            }
        }
        List<Map<?, ?>> locations = yaml.getMapList("locations");
        for (Map<?, ?> map : locations) {
            Object world = map.get("world");
            if (world == null) continue;
            crate.locations.add(new BlockLoc(
                    String.valueOf(world),
                    asInt(map.get("x")),
                    asInt(map.get("y")),
                    asInt(map.get("z"))
            ));
        }
        return crate;
    }

    private File file(String id) {
        return new File(folder, id + ".yml");
    }

    private static int asInt(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (value == null) return 0;
        try {
            return (int) Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    public static final class Crate {
        public final String id;
        public String displayName;
        public int rows;
        public final Map<Integer, ItemStack> rewards = new LinkedHashMap<>();
        public final List<BlockLoc> locations = new ArrayList<>();

        public Crate(String id, String displayName, int rows) {
            this.id = id;
            this.displayName = displayName;
            this.rows = Math.max(1, Math.min(6, rows));
        }

        public int size() {
            return rows * 9;
        }

        public List<ItemStack> rewardList() {
            List<ItemStack> list = new ArrayList<>();
            for (ItemStack item : rewards.values()) {
                if (!isAir(item)) list.add(item.clone());
            }
            return list;
        }
    }

    public record BlockLoc(String world, int x, int y, int z) {
        public static BlockLoc of(Location location) {
            World w = location.getWorld();
            return new BlockLoc(w == null ? "world" : w.getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }

        public Location location() {
            World w = Bukkit.getWorld(world);
            if (w == null) return null;
            return new Location(w, x, y, z);
        }
    }
}
