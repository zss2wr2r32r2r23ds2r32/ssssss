package com.sharded.core.modules.namecolor;

import com.sharded.core.ShardedCore;
import com.sharded.core.cosmetics.CosmeticService;
import com.sharded.core.module.Module;
import com.sharded.core.util.ColorConfigUtil;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Name colour / gradient equip — sharded.namecolor.* permissions. */
public final class NameColorModule extends Module implements CommandExecutor, TabCompleter {

    private static final Pattern GRADIENT_INPUT = Pattern.compile(
            "^#?[0-9A-Fa-f]{6}\\s+#?[0-9A-Fa-f]{6}$");

    private static final String MENU_TITLE = "Name Colours";
    private final Map<String, ColorOption> colors = new LinkedHashMap<>();
    private final Map<UUID, Boolean> awaitingGradient = new ConcurrentHashMap<>();

    private NameColorDatabase database;

    private static final class MenuHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public NameColorModule(ShardedCore plugin) {
        super(plugin, "namecolor");
    }

    @Override
    protected void onEnable() {
        try {
            database = new NameColorDatabase(plugin, moduleFolder());
        } catch (Exception e) {
            throw new IllegalStateException("Could not open namecolor database", e);
        }
        loadColors();
        registerCommand("namecolor", this);
        registerCommand("namecolors", this);
    }

    @Override
    protected void onDisable() {
        awaitingGradient.clear();
        if (database != null) database.close();
        database = null;
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
                    ColorConfigUtil.resolvePermission(id, color, "sharded.namecolor."),
                    ColorConfigUtil.resolveValue(color, "&f"),
                    color.getString("material", "RED_DYE"),
                    color.getString("display-name", id),
                    color.getStringList("lore"),
                    color.getBoolean("custom-input", false)
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
            if (!player.hasPermission("sharded.namecolor.use")) {
                send(player, "no-permission");
                return true;
            }
            openMenu(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("create") && args.length >= 3) {
            if (!sender.hasPermission("sharded.namecolor.admin")) {
                send(sender, "no-permission");
                return true;
            }
            createColor(sender, args[1].toLowerCase(Locale.ROOT), String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)));
            return true;
        }
        if (sub.equals("delete") && args.length >= 2) {
            if (!sender.hasPermission("sharded.namecolor.admin")) {
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
        if (sub.equals("set") && args.length >= 2) {
            if (!(sender instanceof Player player)) {
                send(sender, "players-only");
                return true;
            }
            applyColorById(player, args[1].toLowerCase(Locale.ROOT));
            return true;
        }
        if (sub.equals("custom")) {
            if (!(sender instanceof Player player)) {
                send(sender, "players-only");
                return true;
            }
            if (!player.hasPermission("sharded.namecolor.custom")) {
                send(player, "not-owned", "%color%", "Custom");
                return true;
            }
            awaitingGradient.put(player.getUniqueId(), true);
            send(player, "custom-prompt");
            return true;
        }
        if (sub.equals("gradient")) {
            if (args.length >= 4 && sender.hasPermission("sharded.namecolor.admin")) {
                createColor(sender, args[1].toLowerCase(Locale.ROOT), args[2] + " " + args[3]);
                return true;
            }
            if (args.length >= 3 && sender instanceof Player player) {
                String gradient = normalizeGradientInput(args[1] + " " + args[2]);
                if (gradient == null) {
                    send(player, "custom-invalid");
                    return true;
                }
                applyGradient(player, gradient, false);
                return true;
            }
            send(sender, "gradient-usage");
            return true;
        }

        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.namecolor.use")) {
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
        if (color.customInput()) {
            String last = database == null ? null : database.getLastGradient(player.getUniqueId());
            if (last != null && !last.isBlank()) {
                applyGradient(player, last, false);
                return;
            }
            awaitingGradient.put(player.getUniqueId(), true);
            send(player, "custom-prompt");
            return;
        }
        applyColorValue(player, color.value(), color.displayName());
    }

    private void applyColorValue(Player player, String value, String label) {
        CosmeticService cosmetics = plugin.cosmetics();
        if (cosmetics != null) {
            cosmetics.setNameColor(player, CosmeticService.normalizeColorSpec(value));
        }
        send(player, "applied", "%color%", label);
    }

    private void resetColor(Player player) {
        CosmeticService cosmetics = plugin.cosmetics();
        if (cosmetics != null) cosmetics.clearNameColor(player);
        send(player, "reset");
    }

    private void createColor(CommandSender sender, String id, String value) {
        int rows = config.getInt("menu-rows", 6);
        int slot = nextFreeSlot(rows * 9);
        if (slot < 0) {
            send(sender, "menu-full");
            return;
        }
        String normalized = CosmeticService.normalizeColorSpec(value);
        config.set("colors." + id + ".slot", slot);
        config.set("colors." + id + ".permission", "sharded.namecolor." + id);
        config.set("colors." + id + ".value", normalized);
        config.set("colors." + id + ".material", "NAME_TAG");
        config.set("colors." + id + ".display-name", "&f" + id);
        config.set("colors." + id + ".lore", List.of(
                "&8Descriptions", "", "&#9FFF00Information:",
                "&#9FFF00| &fEquip this name colour.", "", "%click%to apply"));
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
        int resetSlot = config.getInt("reset.slot", 4);
        used.add(resetSlot);
        for (int i = 0; i < size; i++) {
            if (!used.contains(i)) return i;
        }
        return -1;
    }

    private void saveConfig() {
        try {
            config.save(new File(moduleFolder(), "config.yml"));
        } catch (Exception e) {
            plugin.getLogger().warning("[namecolor] Could not save config: " + e.getMessage());
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

        int resetSlot = config.getInt("reset.slot", 4);
        inventory.setItem(resetSlot, new ItemBuilder(Material.BARRIER)
                .name(config.getString("reset.display-name", "&c&lReset name colour"))
                .lore(apply(config.getStringList("reset.lore"), ph))
                .hideAll()
                .build());

        player.openInventory(inventory);
    }

    public Map<String, String> equipPlaceholders(Player player) {
        Map<String, String> map = new LinkedHashMap<>();
        String yes = config.getString("placeholders.owned-yes", "&#9FFF00&nYes");
        String no = config.getString("placeholders.owned-no", "&#FF2727&nNo");
        String none = config.getString("placeholders.none", "&7None");
        String last = database == null ? null : database.getLastGradient(player.getUniqueId());
        map.put("last_gradient", last == null || last.isBlank() ? none : last);
        for (ColorOption color : colors.values()) {
            map.put("namecolor_owned_" + color.id(), player.hasPermission(color.permission()) ? yes : no);
        }
        return map;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        if (event.getSlot() == config.getInt("reset.slot", 4)) {
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

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!Boolean.TRUE.equals(awaitingGradient.remove(player.getUniqueId()))) return;
        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> handleGradientInput(player, input));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        awaitingGradient.remove(event.getPlayer().getUniqueId());
    }

    private void handleGradientInput(Player player, String raw) {
        if (!player.hasPermission("sharded.namecolor.custom")) {
            send(player, "not-owned", "%color%", "Custom");
            return;
        }
        String normalized = normalizeGradientInput(raw);
        if (normalized == null) {
            send(player, "custom-invalid");
            return;
        }
        applyGradient(player, normalized, true);
    }

    private void applyGradient(Player player, String gradient, boolean fromChat) {
        CosmeticService cosmetics = plugin.cosmetics();
        if (cosmetics != null) cosmetics.setNameColor(player, gradient);
        if (database != null) database.saveLastGradient(player.getUniqueId(), gradient);
        send(player, fromChat ? "custom-set" : "custom-reapplied", "%gradient%", gradient);
    }

    private String normalizeGradientInput(String raw) {
        if (raw == null || !GRADIENT_INPUT.matcher(raw.trim()).matches()) return null;
        String[] parts = raw.trim().split("\\s+");
        return ensureHash(parts[0]) + " " + ensureHash(parts[1]);
    }

    private String ensureHash(String hex) {
        return hex.startsWith("#") ? hex.toUpperCase(Locale.ROOT) : "#" + hex.toUpperCase(Locale.ROOT);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("set", "reset", "gradient", "custom"));
            if (sender.hasPermission("sharded.namecolor.admin")) subs.addAll(List.of("create", "delete"));
            subs.addAll(colors.keySet());
            return TabCompleteHelper.filter(args[0], subs);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("gradient")) {
            if (sender.hasPermission("sharded.namecolor.admin")) {
                return TabCompleteHelper.filter(args[1], colors.keySet());
            }
            return List.of();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("delete"))) {
            return TabCompleteHelper.filter(args[1], colors.keySet());
        }
        return List.of();
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
                               String material, String displayName, List<String> lore,
                               boolean customInput) {
    }
}
