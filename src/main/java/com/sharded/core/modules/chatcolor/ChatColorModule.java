package com.sharded.core.modules.chatcolor;

import com.sharded.core.ShardedCore;
import com.sharded.core.cosmetics.CosmeticService;
import com.sharded.core.module.Module;
import com.sharded.core.util.ColorConfigUtil;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Chat colour equip — sharded.chatcolor.* permissions. */
public final class ChatColorModule extends Module implements CommandExecutor, TabCompleter {

    private static final Pattern GRADIENT_INPUT = Pattern.compile(
            "^#?[0-9A-Fa-f]{6}\\s+#?[0-9A-Fa-f]{6}$");

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

    private void loadColors() {
        colors.clear();
        ConfigurationSection section = config.getConfigurationSection("colors");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ConfigurationSection color = section.getConfigurationSection(id);
            if (color == null) continue;
            colors.put(id, new ColorOption(
                    id,
                    color.getInt("slot", 0),
                    ColorConfigUtil.resolvePermission(id, color, "sharded.chatcolor."),
                    ColorConfigUtil.resolveValue(color, "&f"),
                    color.getString("material", "RED_DYE"),
                    color.getString("display-name", id),
                    color.getStringList("lore")
            ));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
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

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("create") && args.length >= 3) {
            if (!sender.hasPermission("sharded.chatcolor.admin")) {
                send(sender, "no-permission");
                return true;
            }
            createColor(sender, args[1].toLowerCase(Locale.ROOT),
                    String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)));
            return true;
        }
        if (sub.equals("delete") && args.length >= 2) {
            if (!sender.hasPermission("sharded.chatcolor.admin")) {
                send(sender, "no-permission");
                return true;
            }
            deleteColor(sender, args[1].toLowerCase(Locale.ROOT));
            return true;
        }
        if (sub.equals("reset")) {
            if (!(sender instanceof Player player)) {
                send(sender, "players-only");
                return true;
            }
            resetColor(player);
            return true;
        }
        if (sub.equals("gradient") && args.length >= 3) {
            if (!(sender instanceof Player player)) {
                send(sender, "players-only");
                return true;
            }
            if (args.length >= 4 && sender.hasPermission("sharded.chatcolor.admin")) {
                createColor(sender, args[1].toLowerCase(Locale.ROOT), args[2] + " " + args[3]);
                return true;
            }
            String gradient = normalizeGradient(args[1] + " " + args[2]);
            if (gradient == null) {
                send(player, "gradient-invalid");
                return true;
            }
            CosmeticService cosmetics = plugin.cosmetics();
            if (cosmetics != null) cosmetics.setChatColor(player, gradient);
            send(player, "applied", "%color%", "Gradient");
            return true;
        }
        if (sub.equals("set") && args.length >= 2) {
            if (!(sender instanceof Player player)) {
                send(sender, "players-only");
                return true;
            }
            applyColorById(player, args[1].toLowerCase(Locale.ROOT));
            return true;
        }

        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.chatcolor.use")) {
            send(player, "no-permission");
            return true;
        }
        applyColorById(player, sub);
        return true;
    }

    private void applyColorById(Player player, String id) {
        ColorOption color = colors.get(id);
        if (color == null) {
            send(player, "color-not-found", "%color%", id);
            return;
        }
        if (!player.hasPermission(color.permission())) {
            send(player, "not-owned", "%color%", color.displayName());
            return;
        }
        CosmeticService cosmetics = plugin.cosmetics();
        if (cosmetics != null) {
            cosmetics.setChatColor(player, CosmeticService.normalizeColorSpec(color.value()));
        }
        send(player, "applied", "%color%", color.displayName());
    }

    private void resetColor(Player player) {
        CosmeticService cosmetics = plugin.cosmetics();
        if (cosmetics != null) cosmetics.clearChatColor(player);
        send(player, "removed");
    }

    private void createColor(CommandSender sender, String id, String value) {
        int rows = config.getInt("menu-rows", 6);
        int slot = nextFreeSlot(rows * 9);
        if (slot < 0) {
            send(sender, "menu-full");
            return;
        }
        config.set("colors." + id + ".slot", slot);
        config.set("colors." + id + ".permission", "sharded.chatcolor." + id);
        config.set("colors." + id + ".value", CosmeticService.normalizeColorSpec(value));
        config.set("colors." + id + ".material", "PAPER");
        config.set("colors." + id + ".display-name", "&f" + id);
        config.set("colors." + id + ".lore", List.of(
                "&8Descriptions", "", "&#9FFF00Information:",
                "&#9FFF00| &fEquip this chat colour.", "", "%click%to apply"));
        saveConfig();
        loadColors();
        send(sender, "color-created", "%color%", id);
    }

    private void deleteColor(CommandSender sender, String id) {
        if (config.getConfigurationSection("colors." + id) == null) {
            send(sender, "color-not-found", "%color%", id);
            return;
        }
        config.set("colors." + id, null);
        saveConfig();
        loadColors();
        send(sender, "color-deleted", "%color%", id);
    }

    private int nextFreeSlot(int size) {
        Set<Integer> used = colors.values().stream().map(ColorOption::slot).collect(Collectors.toSet());
        used.add(config.getInt("remove.slot", 4));
        for (int i = 0; i < size; i++) {
            if (!used.contains(i)) return i;
        }
        return -1;
    }

    private void saveConfig() {
        try {
            config.save(new File(moduleFolder(), "config.yml"));
        } catch (Exception e) {
            plugin.getLogger().warning("[chatcolor] Could not save config: " + e.getMessage());
        }
    }

    public void openMenu(Player player) {
        int rows = config.getInt("menu-rows", 6);
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
                .name(config.getString("remove.display-name", "&c&lRemove chat colour"))
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
            resetColor(player);
            return;
        }

        for (ColorOption color : colors.values()) {
            if (color.slot() != event.getSlot()) continue;
            player.closeInventory();
            applyColorById(player, color.id());
            return;
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) event.setCancelled(true);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("set", "reset", "gradient"));
            if (sender.hasPermission("sharded.chatcolor.admin")) subs.addAll(List.of("create", "delete"));
            subs.addAll(colors.keySet());
            return TabCompleteHelper.filter(args[0], subs);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("gradient")
                && sender.hasPermission("sharded.chatcolor.admin")) {
            return TabCompleteHelper.filter(args[1], colors.keySet());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("delete"))) {
            return TabCompleteHelper.filter(args[1], colors.keySet());
        }
        return List.of();
    }

    private String normalizeGradient(String raw) {
        if (raw == null || !GRADIENT_INPUT.matcher(raw.trim()).matches()) return null;
        String[] parts = raw.trim().split("\\s+");
        return ensureHash(parts[0]) + " " + ensureHash(parts[1]);
    }

    private String ensureHash(String hex) {
        return hex.startsWith("#") ? hex.toUpperCase(Locale.ROOT) : "#" + hex.toUpperCase(Locale.ROOT);
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

    private record ColorOption(String id, int slot, String permission, String value,
                               String material, String displayName, List<String> lore) {
    }
}
