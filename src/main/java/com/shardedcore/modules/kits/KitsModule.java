package com.shardedcore.modules.kits;

import com.shardedcore.ShardedCore;
import com.shardedcore.gui.Menus;
import com.shardedcore.module.Module;
import com.shardedcore.util.Configs;
import com.shardedcore.util.Items;
import com.shardedcore.util.Players;
import com.shardedcore.util.Sounds;
import com.shardedcore.util.Tabs;
import com.shardedcore.util.Text;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class KitsModule extends Module implements CommandExecutor, TabCompleter {

    private static final String ADMIN = "shardedcore.kits.admin";

    private File kitsFolder;
    private File layoutsFolder;

    public KitsModule(ShardedCore plugin) {
        super(plugin, "kits");
    }

    @Override
    public void enable() {
        kitsFolder = new File(folder, "kits");
        layoutsFolder = new File(folder, "layouts");
        if (!kitsFolder.exists() && !kitsFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create kits folder");
        }
        if (!layoutsFolder.exists() && !layoutsFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create kit layouts folder");
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
        if (command.getName().equalsIgnoreCase("kits")) {
            if (!(sender instanceof Player player)) {
                send(sender, "players-only");
                return true;
            }
            openGui(player, 0);
            return true;
        }
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                send(sender, "players-only");
                return true;
            }
            openGui(player, 0);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> create(sender, args);
            case "delete" -> delete(sender, args);
            case "give" -> giveCommand(sender, args);
            case "claim" -> claimCommand(sender, args);
            default -> {
                if (sender instanceof Player player && kit(args[0]) != null) {
                    startClaim(player, args[0]);
                } else {
                    send(sender, "usage");
                }
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
        PlayerInventory inventory = player.getInventory();
        Map<Integer, ItemStack> items = new HashMap<>();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (isAir(item)) continue;
            items.put(slot, item.clone());
        }
        if (items.isEmpty()) {
            send(player, "empty-inventory");
            return true;
        }
        File kitFolder = kitFolder(id);
        if (!kitFolder.exists() && !kitFolder.mkdirs()) {
            send(player, "invalid-name", "kit", args[1]);
            return true;
        }
        YamlConfiguration kitYaml = new YamlConfiguration();
        kitYaml.set("name", args[1]);
        for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
            kitYaml.set("items." + entry.getKey(), entry.getValue());
        }
        Configs.save(kitYaml, new File(kitFolder, "kit.yml"));

        File guiFile = new File(kitFolder, "gui.yml");
        if (!guiFile.exists()) {
            YamlConfiguration gui = new YamlConfiguration();
            gui.set("material", cfg("defaults.material", "CHEST"));
            gui.set("name", Text.apply(cfg("defaults.name", "&f%kit%"), "kit", args[1]));
            gui.set("lore", config.getStringList("defaults.lore"));
            gui.set("slot", -1);
            gui.set("permission", "");
            Configs.save(gui, guiFile);
        }
        send(player, "created", "kit", args[1]);
        sound(player, "sounds.create");
        return true;
    }

    private boolean delete(CommandSender sender, String[] args) {
        if (!admin(sender)) return true;
        if (args.length < 2) {
            send(sender, "usage-delete");
            return true;
        }
        Kit kit = kit(args[1]);
        if (kit == null) {
            send(sender, "missing", "kit", args[1]);
            return true;
        }
        File folder = kitFolder(kit.id);
        deleteTree(folder);
        send(sender, "deleted", "kit", kit.displayName);
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
            send(sender, "player-offline");
            return true;
        }
        Kit kit = kit(args[2]);
        if (kit == null) {
            send(sender, "missing", "kit", args[2]);
            return true;
        }
        for (ItemStack item : kit.items()) give(target, item);
        send(sender, "gave", "player", target.getName(), "kit", kit.displayName);
        send(target, "received", "kit", kit.displayName);
        sound(target, "sounds.claim");
        return true;
    }

    private boolean claimCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (args.length < 2) {
            send(player, "usage-claim");
            return true;
        }
        startClaim(player, args[1]);
        return true;
    }

    private void startClaim(Player player, String name) {
        Kit kit = kit(name);
        if (kit == null) {
            send(player, "missing", "kit", name);
            return;
        }
        if (!canUse(player, kit)) {
            send(player, "no-kit-permission", "kit", kit.displayName);
            return;
        }
        List<ItemStack> items = kit.items();
        if (items.isEmpty()) {
            send(player, "empty-kit", "kit", kit.displayName);
            return;
        }
        List<ItemStack> layout = loadLayout(player.getUniqueId(), kit.id);
        if (layout == null || !sameItems(layout, items)) layout = items;
        openLayout(player, kit, layout);
    }

    private void openLayout(Player player, Kit kit, List<ItemStack> items) {
        int rows = Math.max(1, Math.min(6, (items.size() + 8) / 9));
        Menus.Menu menu = plugin.menus().create(player, Text.apply(cfg("layout-title", "&8%kit%"), "kit", kit.displayName), rows);
        int size = rows * 9;
        for (int i = 0; i < items.size() && i < size; i++) {
            menu.set(i, items.get(i).clone());
        }
        menu.onAny(event -> {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= size) return;
            ItemStack cursor = event.getCursor();
            ItemStack current = event.getCurrentItem();
            ItemStack cursorCopy = isAir(cursor) ? null : cursor.clone();
            ItemStack currentCopy = isAir(current) ? null : current.clone();
            event.getView().setCursor(currentCopy);
            event.getInventory().setItem(slot, cursorCopy);
        });
        menu.onClose(closed -> {
            List<ItemStack> arranged = new ArrayList<>();
            ItemStack cursor = closed.getItemOnCursor();
            if (!isAir(cursor)) {
                arranged.add(cursor.clone());
                closed.setItemOnCursor(null);
            }
            for (ItemStack item : menu.inventory().getContents()) {
                if (!isAir(item)) arranged.add(item.clone());
            }
            saveLayout(closed.getUniqueId(), kit.id, arranged);
            for (ItemStack item : arranged) give(closed, item);
            send(closed, "claimed", "kit", kit.displayName);
            sound(closed, "sounds.claim");
        });
        plugin.menus().open(player, menu);
        send(player, "layout-hint", "kit", kit.displayName);
        sound(player, "sounds.open");
    }

    private void openGui(Player player, int page) {
        List<Kit> kits = visible(player);
        if (kits.isEmpty()) {
            send(player, "none");
            return;
        }
        int rows = Math.max(3, Math.min(6, config.getInt("gui.rows", 4)));
        List<Integer> content = contentSlots(rows);
        int per = Math.max(1, content.size());
        int pages = Math.max(1, (kits.size() + per - 1) / per);
        int current = Math.max(0, Math.min(page, pages - 1));
        Menus.Menu menu = plugin.menus().create(player, cfg("gui.title", "&8Kits"), rows);
        int start = current * per;
        for (int i = 0; i < per && start + i < kits.size(); i++) {
            Kit kit = kits.get(start + i);
            int slot = kit.slot >= 0 && kit.slot < rows * 9 && current == 0 ? kit.slot : content.get(i);
            List<String> lore = kit.lore.isEmpty() ? config.getStringList("defaults.lore") : kit.lore;
            lore = Text.applyList(lore, "kit", kit.displayName);
            menu.set(slot, Items.named(kit.material, kit.iconName, lore), event -> {
                event.setCancelled(true);
                player.closeInventory();
                startClaim(player, kit.id);
            });
        }
        if (pages > 1) {
            menu.set(config.getInt("gui.previous.slot", rows * 9 - 9), Items.named(
                    Sounds.material(cfg("gui.previous.material", "RED_STAINED_GLASS_PANE"), Material.RED_STAINED_GLASS_PANE),
                    cfg("gui.previous.name", "&#FF0000&lPREVIOUS PAGE"),
                    List.of("&7Page " + current)
            ), event -> {
                event.setCancelled(true);
                if (current > 0) openGui(player, current - 1);
            });
            menu.set(config.getInt("gui.next.slot", rows * 9 - 1), Items.named(
                    Sounds.material(cfg("gui.next.material", "LIME_STAINED_GLASS_PANE"), Material.LIME_STAINED_GLASS_PANE),
                    cfg("gui.next.name", "&#80ee0b&lNEXT PAGE"),
                    List.of("&7Page " + (current + 2))
            ), event -> {
                event.setCancelled(true);
                if (current + 1 < pages) openGui(player, current + 1);
            });
        }
        menu.fill(Items.named(
                Sounds.material(cfg("gui.filler.material", "BLACK_STAINED_GLASS_PANE"), Material.BLACK_STAINED_GLASS_PANE),
                cfg("gui.filler.name", " "),
                List.of()
        ));
        plugin.menus().open(player, menu);
        sound(player, "sounds.open");
    }

    private List<Kit> visible(Player player) {
        List<Kit> kits = new ArrayList<>();
        File[] folders = kitsFolder.listFiles(File::isDirectory);
        if (folders == null) return kits;
        for (File folder : folders) {
            Kit kit = loadKit(folder);
            if (kit != null && canUse(player, kit)) kits.add(kit);
        }
        kits.sort(Comparator.comparingInt((Kit kit) -> kit.slot < 0 ? Integer.MAX_VALUE : kit.slot)
                .thenComparing(kit -> kit.displayName, String.CASE_INSENSITIVE_ORDER));
        return kits;
    }

    private Kit kit(String name) {
        if (name == null) return null;
        File exact = kitFolder(sanitize(name));
        if (exact.isDirectory()) return loadKit(exact);
        File[] folders = kitsFolder.listFiles(File::isDirectory);
        if (folders == null) return null;
        for (File folder : folders) {
            if (folder.getName().equalsIgnoreCase(name)) return loadKit(folder);
            Kit kit = loadKit(folder);
            if (kit != null && kit.displayName.equalsIgnoreCase(name)) return kit;
        }
        return null;
    }

    private Kit loadKit(File folder) {
        File kitFile = new File(folder, "kit.yml");
        if (!kitFile.exists()) return null;
        FileConfiguration yaml = Configs.load(kitFile);
        String id = folder.getName().toLowerCase(Locale.ROOT);
        String display = yaml.getString("name", folder.getName());
        Map<Integer, ItemStack> items = new HashMap<>();
        ConfigurationSection section = yaml.getConfigurationSection("items");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(key);
                    ItemStack item = section.getItemStack(key);
                    if (!isAir(item)) items.put(slot, item.clone());
                } catch (NumberFormatException ignored) {
                }
            }
        } else {
            List<?> list = yaml.getList("items");
            if (list != null) {
                int slot = 0;
                for (Object object : list) {
                    if (object instanceof ItemStack item && !isAir(item)) items.put(slot, item.clone());
                    slot++;
                }
            }
        }
        File guiFile = new File(folder, "gui.yml");
        Material material = Sounds.material(cfg("defaults.material", "CHEST"), Material.CHEST);
        String iconName = Text.apply(cfg("defaults.name", "&f%kit%"), "kit", display);
        List<String> lore = new ArrayList<>(config.getStringList("defaults.lore"));
        int slot = -1;
        String permission = "";
        if (guiFile.exists()) {
            FileConfiguration gui = Configs.load(guiFile);
            material = Sounds.material(gui.getString("material", material.name()), material);
            iconName = gui.getString("name", iconName);
            if (gui.isList("lore") && !gui.getStringList("lore").isEmpty()) lore = gui.getStringList("lore");
            slot = gui.getInt("slot", -1);
            permission = gui.getString("permission", "");
        }
        return new Kit(id, display, items, material, iconName, lore, slot, permission);
    }

    private boolean canUse(Player player, Kit kit) {
        if (player.hasPermission(ADMIN)) return true;
        if (kit.permission != null && !kit.permission.isBlank()) return player.hasPermission(kit.permission);
        return player.hasPermission("shardedcore.kit." + kit.id) || player.hasPermission("shardedcore.kits");
    }

    private List<ItemStack> loadLayout(UUID uuid, String kitId) {
        File file = new File(layoutsFolder, uuid + ".yml");
        if (!file.exists()) return null;
        FileConfiguration yaml = Configs.load(file);
        ConfigurationSection section = yaml.getConfigurationSection(kitId);
        if (section == null) section = yaml.getConfigurationSection("kits." + kitId);
        if (section == null) return null;
        ConfigurationSection items = section.getConfigurationSection("items");
        List<ItemStack> list = new ArrayList<>();
        if (items != null) {
            List<Integer> slots = new ArrayList<>();
            for (String key : items.getKeys(false)) {
                try {
                    slots.add(Integer.parseInt(key));
                } catch (NumberFormatException ignored) {
                }
            }
            slots.sort(Integer::compareTo);
            for (int slot : slots) {
                ItemStack item = items.getItemStack(String.valueOf(slot));
                if (!isAir(item)) list.add(item.clone());
            }
            return list;
        }
        List<?> raw = section.getList("items");
        if (raw == null) return null;
        for (Object object : raw) {
            if (object instanceof ItemStack item && !isAir(item)) list.add(item.clone());
        }
        return list;
    }

    private void saveLayout(UUID uuid, String kitId, List<ItemStack> items) {
        File file = new File(layoutsFolder, uuid + ".yml");
        YamlConfiguration yaml = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        yaml.set(kitId, null);
        yaml.set("kits." + kitId, null);
        int slot = 0;
        for (ItemStack item : items) {
            if (isAir(item)) continue;
            yaml.set(kitId + ".items." + slot, item);
            slot++;
        }
        Configs.save(yaml, file);
    }

    private boolean sameItems(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) return false;
        List<ItemStack> remaining = new ArrayList<>();
        for (ItemStack item : b) remaining.add(item.clone());
        for (ItemStack item : a) {
            boolean found = false;
            Iterator<ItemStack> iterator = remaining.iterator();
            while (iterator.hasNext()) {
                ItemStack other = iterator.next();
                if (item.isSimilar(other) && item.getAmount() == other.getAmount()) {
                    iterator.remove();
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return remaining.isEmpty();
    }

    private void give(Player player, ItemStack item) {
        if (isAir(item)) return;
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
        leftover.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission(ADMIN)) return true;
        send(sender, "no-permission");
        return false;
    }

    private File kitFolder(String id) {
        return new File(kitsFolder, id);
    }

    private static String sanitize(String name) {
        if (name == null) return "";
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
    }

    private static boolean isAir(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    private static List<Integer> contentSlots(int rows) {
        List<Integer> slots = new ArrayList<>();
        int last = Math.max(0, rows - 1);
        for (int row = 0; row < rows; row++) {
            if (rows >= 3 && (row == 0 || row == last)) continue;
            for (int col = 1; col < 8; col++) slots.add(row * 9 + col);
        }
        if (slots.isEmpty()) {
            for (int i = 0; i < rows * 9; i++) slots.add(i);
        }
        return slots;
    }

    private static void deleteTree(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteTree(child);
            }
        }
        file.delete();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("kits")) return List.of();
        List<String> names = kitNames();
        if (args.length == 1) {
            List<String> options = new ArrayList<>(names);
            if (sender.hasPermission(ADMIN)) options.addAll(0, List.of("create", "delete", "give"));
            options.add("claim");
            return Tabs.filter(options, args[0]);
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "delete", "claim" -> Tabs.filter(names, args[1]);
                case "give" -> Tabs.players(args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) return Tabs.filter(names, args[2]);
        return List.of();
    }

    private List<String> kitNames() {
        List<String> names = new ArrayList<>();
        File[] folders = kitsFolder == null ? null : kitsFolder.listFiles(File::isDirectory);
        if (folders == null) return names;
        for (File folder : folders) names.add(folder.getName());
        return names;
    }

    private record Kit(
            String id,
            String displayName,
            Map<Integer, ItemStack> slots,
            Material material,
            String iconName,
            List<String> lore,
            int slot,
            String permission
    ) {
        private List<ItemStack> items() {
            List<Integer> order = new ArrayList<>(slots.keySet());
            order.sort(Integer::compareTo);
            List<ItemStack> list = new ArrayList<>();
            for (int index : order) {
                ItemStack item = slots.get(index);
                if (!isAir(item)) list.add(item.clone());
            }
            return list;
        }
    }
}
