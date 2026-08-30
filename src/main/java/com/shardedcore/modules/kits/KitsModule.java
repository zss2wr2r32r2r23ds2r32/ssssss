package com.shardedcore.modules.kits;

import com.shardedcore.ShardedCore;
import com.shardedcore.database.Sqlite;
import com.shardedcore.gui.Menus;
import com.shardedcore.module.Module;
import com.shardedcore.util.Amounts;
import com.shardedcore.util.Configs;
import com.shardedcore.util.Items;
import com.shardedcore.util.Players;
import com.shardedcore.util.Sounds;
import com.shardedcore.util.Tabs;
import com.shardedcore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class KitsModule extends Module implements CommandExecutor, TabCompleter {

    private static final String ADMIN = "shardedcore.kits.admin";
    private static final int[] GUI_TO_PLAYER = new int[54];

    static {
        for (int i = 0; i < 54; i++) GUI_TO_PLAYER[i] = -1;
        for (int i = 0; i < 27; i++) GUI_TO_PLAYER[i] = 9 + i;
        for (int i = 0; i < 9; i++) GUI_TO_PLAYER[27 + i] = i;
        GUI_TO_PLAYER[36] = 39;
        GUI_TO_PLAYER[37] = 38;
        GUI_TO_PLAYER[38] = 37;
        GUI_TO_PLAYER[39] = 36;
        GUI_TO_PLAYER[40] = 40;
    }

    private File kitsFolder;
    private File layoutsFolder;
    private Sqlite sqlite;
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    public KitsModule(ShardedCore plugin) {
        super(plugin, "kits");
    }

    @Override
    public void enable() {
        kitsFolder = new File(folder, "kits");
        layoutsFolder = new File(folder, "layouts");
        kitsFolder.mkdirs();
        layoutsFolder.mkdirs();
        sqlite = plugin.toggles().sqlite();
        try {
            sqlite.run("""
                    CREATE TABLE IF NOT EXISTS kit_cooldowns (
                        uuid TEXT NOT NULL,
                        kit TEXT NOT NULL,
                        until INTEGER NOT NULL,
                        PRIMARY KEY (uuid, kit)
                    )
                    """);
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create kit_cooldowns", ex);
        }
        registerCommand("kit", this);
        registerCommand("kits", this);
    }

    @Override
    public void disable() {
        cleanup();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || command.getName().equalsIgnoreCase("kits")) {
            if (command.getName().equalsIgnoreCase("kits")) {
                if (!(sender instanceof Player player)) {
                    send(sender, "players-only");
                    return true;
                }
                openGui(player);
                return true;
            }
            send(sender, "usage-claim");
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "claim" -> {
                if (!(sender instanceof Player player)) {
                    send(sender, "players-only");
                    yield true;
                }
                if (args.length < 2) {
                    send(player, "usage-claim");
                    yield true;
                }
                claim(player, args[1]);
                yield true;
            }
            case "create" -> create(sender, args);
            case "delete" -> delete(sender, args);
            case "give" -> giveCommand(sender, args);
            case "list" -> list(sender);
            case "help" -> {
                sendLines(sender, config.getStringList("messages.help"), "");
                yield true;
            }
            default -> {
                send(sender, "usage-claim");
                yield true;
            }
        };
    }

    private boolean create(CommandSender sender, String[] args) {
        if (!admin(sender)) return true;
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (args.length < 2) {
            send(player, "usage-create");
            return true;
        }
        String id = sanitize(args[1]);
        if (id.isEmpty()) {
            send(player, "invalid-name", "kit", args[1]);
            return true;
        }
        Map<Integer, ItemStack> items = snapshot(player.getInventory());
        if (items.isEmpty()) {
            send(player, "empty-inventory");
            return true;
        }
        File kitFolder = new File(kitsFolder, id);
        kitFolder.mkdirs();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", args[1]);
        for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
            yaml.set("items." + entry.getKey(), entry.getValue());
        }
        Configs.save(yaml, new File(kitFolder, "kit.yml"));
        send(player, "created", "kit", args[1], "items", String.valueOf(items.size()));
        sound(player, "sounds.create");
        return true;
    }

    private boolean delete(CommandSender sender, String[] args) {
        if (!admin(sender)) return true;
        if (args.length < 2) {
            send(sender, "usage-delete");
            return true;
        }
        File folder = new File(kitsFolder, sanitize(args[1]));
        if (!folder.isDirectory()) {
            send(sender, "unknown", "kit", args[1]);
            return true;
        }
        deleteTree(folder);
        send(sender, "deleted", "kit", args[1]);
        return true;
    }

    private boolean giveCommand(CommandSender sender, String[] args) {
        if (!admin(sender)) return true;
        if (args.length < 3) {
            send(sender, "usage-give");
            return true;
        }
        Player target = Players.online(args[1]);
        if (target == null) {
            send(sender, "unknown-player", "player", args[1]);
            return true;
        }
        Map<Integer, ItemStack> items = loadItems(sanitize(args[2]));
        if (items.isEmpty()) {
            send(sender, "unknown", "kit", args[2]);
            return true;
        }
        giveMapped(target, items, loadLayout(target.getUniqueId(), sanitize(args[2])));
        send(sender, "gave", "player", target.getName(), "kit", args[2]);
        send(target, "received", "kit", args[2]);
        sound(target, "sounds.claim");
        return true;
    }

    private boolean list(CommandSender sender) {
        List<String> names = kitIds();
        if (names.isEmpty()) {
            send(sender, "list-empty");
            return true;
        }
        send(sender, "list", "amount", String.valueOf(names.size()), "kits", String.join(", ", names));
        return true;
    }

    private void openGui(Player player) {
        int size = Math.max(9, config.getInt("menu.size", 27));
        int rows = Math.max(1, size / 9);
        Menus.Menu menu = plugin.menus().create(player, cfg("menu.title", "&8Kits"), rows);
        ConfigurationSection kits = config.getConfigurationSection("kits");
        if (kits != null) {
            for (String id : kits.getKeys(false)) {
                ConfigurationSection section = kits.getConfigurationSection(id);
                if (section == null) continue;
                boolean unlocked = canUse(player, id, section.getString("permission", "shardedcore.kit." + id));
                ConfigurationSection icon = section.getConfigurationSection(unlocked ? "item" : "locked");
                if (icon == null) icon = section.getConfigurationSection("item");
                if (icon == null) continue;
                ConfigurationSection shown = icon;
                boolean canClaim = unlocked;
                String kitId = id;
                long left = remaining(player.getUniqueId(), id);
                String cooldown = section.getString("cooldown", cfg("no-cooldown", "None"));
                String time = left <= 0 ? cfg("ready", "Ready") : Amounts.duration(left, "d", "h", "m", "s", 2);
                ItemStack stack = Items.fromSection(shown, player, "cooldown", cooldown, "time", time, "kit", kitId);
                int slot = section.getInt("slot", 0);
                menu.set(slot, stack, event -> {
                    event.setCancelled(true);
                    if (!canClaim) {
                        send(player, "no-kit-permission", "kit", shown.getString("name", kitId));
                        sound(player, "sounds.denied");
                        return;
                    }
                    if (event.getClick() == ClickType.RIGHT || event.isRightClick()) {
                        player.closeInventory();
                        openArrange(player, kitId);
                    } else {
                        player.closeInventory();
                        claim(player, kitId);
                    }
                });
            }
        }
        menu.fill(Items.fromSection(config.getConfigurationSection("menu.filler"), player));
        plugin.menus().open(player, menu);
        sound(player, "sounds.open");
    }

    private void claim(Player player, String name) {
        String id = resolveKitId(name);
        ConfigurationSection section = config.getConfigurationSection("kits." + id);
        String permission = section == null ? "shardedcore.kit." + id : section.getString("permission", "shardedcore.kit." + id);
        if (!canUse(player, id, permission)) {
            send(player, "no-kit-permission", "kit", name);
            sound(player, "sounds.denied");
            return;
        }
        long left = remaining(player.getUniqueId(), id);
        if (left > 0) {
            send(player, "cooldown", "kit", name, "time", Amounts.duration(left, "d", "h", "m", "s", 2));
            sound(player, "sounds.denied");
            return;
        }
        Map<Integer, ItemStack> items = loadItems(name);
        if (items.isEmpty()) {
            send(player, "empty-kit", "kit", name);
            return;
        }
        Map<Integer, ItemStack> layout = loadLayout(player.getUniqueId(), id);
        Map<Integer, ItemStack> use = layout != null && sameItems(flat(layout), flat(items)) ? layout : items;
        if (!com.shardedcore.util.Inventories.hasSpace(player, use)) {
            send(player, "no-space", "kit", name);
            sound(player, "sounds.denied");
            return;
        }
        giveMapped(player, items, layout);
        long wait = Amounts.durationMillis(section == null ? "1h" : section.getString("cooldown", "1h"));
        setCooldown(player.getUniqueId(), id, System.currentTimeMillis() + wait);
        send(player, "claimed", "kit", name);
        sound(player, "sounds.claim");
    }

    private void openArrange(Player player, String raw) {
        String id = resolveKitId(raw);
        ConfigurationSection section = config.getConfigurationSection("kits." + id);
        String permission = section == null ? "shardedcore.kit." + id : section.getString("permission", "shardedcore.kit." + id);
        if (!canUse(player, id, permission)) {
            send(player, "no-kit-permission", "kit", id);
            return;
        }
        Map<Integer, ItemStack> items = loadItems(id);
        if (items.isEmpty()) {
            send(player, "empty-kit", "kit", id);
            return;
        }
        int rows = Math.max(6, config.getInt("layout.rows", 6));
        ConfigurationSection back = config.getConfigurationSection("layout.back");
        int backSlot = back == null ? 45 : back.getInt("slot", 45);
        java.util.Set<Integer> editable = new java.util.HashSet<>();
        for (int slot = 0; slot < GUI_TO_PLAYER.length; slot++) {
            if (GUI_TO_PLAYER[slot] >= 0 && slot != backSlot) editable.add(slot);
        }
        Menus.Menu menu = plugin.menus().create(player, cfg("layout.title", "&8Kits | Preview"), rows)
                .editableSlots(editable);
        Map<Integer, ItemStack> layout = loadLayout(player.getUniqueId(), id);
        Map<Integer, ItemStack> placed = layout == null || !sameItems(flat(layout), flat(items)) ? items : layout;
        for (Map.Entry<Integer, ItemStack> entry : placed.entrySet()) {
            int gui = playerToGui(entry.getKey());
            if (gui >= 0 && gui != backSlot) menu.inventory().setItem(gui, entry.getValue().clone());
        }
        menu.set(backSlot, Items.fromSection(back, player), event -> {
            event.setCancelled(true);
            player.closeInventory();
            openGui(player);
        });
        menu.fillExcept(Items.fromSection(config.getConfigurationSection("layout.filler"), player), editable);
        menu.onBottom(event -> event.setCancelled(true));
        menu.onClose(closed -> saveArrange(closed, id, menu, items, backSlot));
        plugin.menus().open(player, menu);
    }

    private void saveArrange(Player player, String id, Menus.Menu menu, Map<Integer, ItemStack> original, int backSlot) {
        ItemStack cursor = player.getItemOnCursor();
        if (!isAir(cursor)) {
            ItemStack[] contents = menu.inventory().getContents();
            boolean stored = false;
            for (int slot = 0; slot < contents.length; slot++) {
                if (slot == backSlot) continue;
                if (slot >= GUI_TO_PLAYER.length || GUI_TO_PLAYER[slot] < 0) continue;
                if (isAir(contents[slot])) {
                    menu.inventory().setItem(slot, cursor.clone());
                    stored = true;
                    break;
                }
            }
            player.setItemOnCursor(null);
            if (!stored) {
                send(player, "layout-refused", "kit", id);
                return;
            }
        }
        Map<Integer, ItemStack> arranged = new HashMap<>();
        ItemStack filler = Items.fromSection(config.getConfigurationSection("layout.filler"), player);
        ItemStack[] contents = menu.inventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (slot == backSlot) continue;
            ItemStack item = contents[slot];
            if (isAir(item) || fillerLike(item, filler)) continue;
            int playerSlot = slot < GUI_TO_PLAYER.length ? GUI_TO_PLAYER[slot] : -1;
            if (playerSlot < 0) continue;
            arranged.put(playerSlot, item.clone());
        }
        if (!sameItems(flat(arranged), flat(original))) {
            send(player, "layout-refused", "kit", id);
            return;
        }
        saveLayout(player.getUniqueId(), id, arranged);
        send(player, "layout-saved", "kit", id);
    }

    private void giveMapped(Player player, Map<Integer, ItemStack> items, Map<Integer, ItemStack> layout) {
        Map<Integer, ItemStack> use = layout != null && sameItems(flat(layout), flat(items)) ? layout : items;
        PlayerInventory inventory = player.getInventory();
        for (Map.Entry<Integer, ItemStack> entry : use.entrySet()) {
            int slot = entry.getKey();
            ItemStack item = entry.getValue().clone();
            if (slot >= 0 && slot < inventory.getSize() && isAir(inventory.getItem(slot))) {
                inventory.setItem(slot, item);
            } else {
                HashMap<Integer, ItemStack> leftover = inventory.addItem(item);
                leftover.values().forEach(stack -> inventory.addItem(stack));
            }
        }
    }

    private Map<Integer, ItemStack> loadItems(String id) {
        for (String folder : itemFolders(id)) {
            Map<Integer, ItemStack> items = readKitFile(new File(new File(kitsFolder, folder), "kit.yml"));
            if (!items.isEmpty()) return items;
        }
        return Map.of();
    }

    private Map<Integer, ItemStack> readKitFile(File file) {
        if (!file.exists()) return Map.of();
        FileConfiguration yaml = Configs.load(file);
        Map<Integer, ItemStack> items = new HashMap<>();
        ConfigurationSection section = yaml.getConfigurationSection("items");
        if (section == null) return items;
        for (String key : section.getKeys(false)) {
            try {
                ItemStack item = section.getItemStack(key);
                if (!isAir(item)) items.put(Integer.parseInt(key), item.clone());
            } catch (NumberFormatException ignored) {
            }
        }
        return items;
    }

    private Map<Integer, ItemStack> loadLayout(UUID uuid, String kitId) {
        File file = new File(layoutsFolder, uuid + ".yml");
        if (!file.exists()) return null;
        FileConfiguration yaml = Configs.load(file);
        ConfigurationSection section = yaml.getConfigurationSection(kitId + ".items");
        if (section == null) return null;
        Map<Integer, ItemStack> items = new HashMap<>();
        for (String key : section.getKeys(false)) {
            try {
                ItemStack item = section.getItemStack(key);
                if (!isAir(item)) items.put(Integer.parseInt(key), item.clone());
            } catch (NumberFormatException ignored) {
            }
        }
        return items;
    }

    private void saveLayout(UUID uuid, String kitId, Map<Integer, ItemStack> items) {
        File file = new File(layoutsFolder, uuid + ".yml");
        YamlConfiguration yaml = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        yaml.set(kitId, null);
        for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
            yaml.set(kitId + ".items." + entry.getKey(), entry.getValue());
        }
        Configs.save(yaml, file);
    }

    private long remaining(UUID uuid, String kit) {
        long until = cooldowns.computeIfAbsent(uuid, this::loadCooldowns).getOrDefault(kit, 0L);
        return Math.max(0L, until - System.currentTimeMillis());
    }

    private void setCooldown(UUID uuid, String kit, long until) {
        cooldowns.computeIfAbsent(uuid, ignored -> new ConcurrentHashMap<>()).put(kit, until);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                sqlite.execute("""
                        INSERT INTO kit_cooldowns (uuid, kit, until) VALUES (?, ?, ?)
                        ON CONFLICT(uuid, kit) DO UPDATE SET until = excluded.until
                        """, uuid.toString(), kit, until);
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save kit cooldown", ex);
            }
        });
    }

    private Map<String, Long> loadCooldowns(UUID uuid) {
        Map<String, Long> map = new ConcurrentHashMap<>();
        try {
            sqlite.query("SELECT kit, until FROM kit_cooldowns WHERE uuid = ?", rs -> {
                try {
                    while (rs.next()) map.put(rs.getString("kit"), rs.getLong("until"));
                } catch (SQLException ex) {
                    throw new IllegalStateException(ex);
                }
                return map;
            }, uuid.toString());
        } catch (SQLException ignored) {
        }
        return map;
    }

    private boolean canUse(Player player, String id, String permission) {
        if (player.hasPermission(ADMIN) || player.hasPermission("shardedcore.kits.all")) return true;
        if (permission != null && !permission.isBlank()) return player.hasPermission(permission);
        return player.hasPermission("shardedcore.kit." + id);
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission(ADMIN)) return true;
        send(sender, "no-permission");
        return false;
    }

    private List<String> kitIds() {
        List<String> ids = new ArrayList<>();
        ConfigurationSection kits = config.getConfigurationSection("kits");
        if (kits != null) ids.addAll(kits.getKeys(false));
        return ids;
    }

    private static Map<Integer, ItemStack> snapshot(PlayerInventory inventory) {
        Map<Integer, ItemStack> items = new HashMap<>();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (!isAir(contents[slot])) items.put(slot, contents[slot].clone());
        }
        return items;
    }

    private static List<ItemStack> flat(Map<Integer, ItemStack> items) {
        List<ItemStack> list = new ArrayList<>();
        items.values().forEach(item -> {
            if (!isAir(item)) list.add(item.clone());
        });
        return list;
    }

    private String resolveKitId(String raw) {
        String id = sanitize(raw);
        if (config.isConfigurationSection("kits." + id)) return id;
        ConfigurationSection aliases = config.getConfigurationSection("aliases");
        if (aliases != null) {
            String mapped = aliases.getString(id, "");
            if (mapped != null && !mapped.isBlank() && config.isConfigurationSection("kits." + sanitize(mapped))) {
                return sanitize(mapped);
            }
        }
        return switch (id) {
            case "default" -> config.isConfigurationSection("kits.member") ? "member" : id;
            case "vip" -> "prism";
            case "mvp" -> "crystal";
            case "elite" -> "amethyst";
            case "divine" -> "sharded";
            case "legend", "sharded_" -> "shardedplus";
            case "immortal" -> "patron";
            default -> id;
        };
    }

    private List<String> itemFolders(String raw) {
        String typed = sanitize(raw);
        String resolved = resolveKitId(raw);
        List<String> folders = new ArrayList<>();
        if (!typed.isEmpty()) folders.add(typed);
        if (!resolved.isEmpty() && !folders.contains(resolved)) folders.add(resolved);
        ConfigurationSection section = config.getConfigurationSection("kits." + resolved);
        if (section != null) {
            String contents = section.getString("contents", section.getString("file", ""));
            if (contents != null && !contents.isBlank()) {
                String folder = sanitize(contents);
                if (!folders.contains(folder)) folders.add(folder);
            }
        }
        String legacy = switch (resolved) {
            case "shardedplus" -> "sharded_";
            default -> "";
        };
        if (!legacy.isEmpty() && !folders.contains(legacy)) folders.add(legacy);
        return folders;
    }

    private static boolean fillerLike(ItemStack item, ItemStack filler) {
        if (isAir(item) || isAir(filler)) return false;
        return item.getType() == filler.getType() && item.getType().name().endsWith("GLASS_PANE");
    }

    private static boolean sameItems(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) return false;
        List<ItemStack> remaining = new ArrayList<>();
        for (ItemStack item : b) remaining.add(normalize(item));
        for (ItemStack item : a) {
            ItemStack compare = normalize(item);
            boolean found = false;
            Iterator<ItemStack> iterator = remaining.iterator();
            while (iterator.hasNext()) {
                ItemStack other = iterator.next();
                if (compare.isSimilar(other) && compare.getAmount() == other.getAmount()) {
                    iterator.remove();
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return remaining.isEmpty();
    }

    private static ItemStack normalize(ItemStack item) {
        ItemStack clone = item.clone();
        Items.hideBundleBits(clone);
        return clone;
    }

    private static int playerToGui(int playerSlot) {
        for (int i = 0; i < GUI_TO_PLAYER.length; i++) {
            if (GUI_TO_PLAYER[i] == playerSlot) return i;
        }
        return -1;
    }

    private static String sanitize(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
    }

    private static boolean isAir(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteTree(child);
        }
        file.delete();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("kits")) return List.of();
        List<String> names = kitIds();
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("claim"));
            if (sender.hasPermission(ADMIN)) options.addAll(0, List.of("create", "delete", "give", "list", "help"));
            return Tabs.filter(options, args[0]);
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "claim", "delete" -> Tabs.filter(names, args[1]);
                case "give" -> Tabs.players(args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) return Tabs.filter(names, args[2]);
        return List.of();
    }
}
