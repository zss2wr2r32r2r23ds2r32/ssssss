package com.sharded.core.modules.namecolor;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Name colour / gradient equip menu — requires namecolor.set.color.* permissions. */
public final class NameColorModule extends Module implements CommandExecutor {

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
        var section = config.getConfigurationSection("colors");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            var color = section.getConfigurationSection(id);
            if (color == null) continue;
            colors.put(id, new ColorOption(
                    id,
                    color.getInt("slot", 0),
                    color.getString("permission", "namecolor.set.color." + id),
                    color.getString("command", "namecolor:color " + id),
                    color.getString("material", "RED_DYE"),
                    color.getString("display-name", id),
                    color.getStringList("lore"),
                    color.getBoolean("custom-input", false)
            ));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.namecolor.use")) {
            send(player, "no-permission");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("custom")) {
            if (!player.hasPermission("namecolor.set.color.custom")) {
                send(player, "not-owned", "%color%", "Custom");
                return true;
            }
            awaitingGradient.put(player.getUniqueId(), true);
            send(player, "custom-prompt");
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
        for (ColorOption color : colors.values()) {
            if (color.slot() != event.getSlot()) continue;
            player.closeInventory();
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
            run(player, color.command());
            send(player, "applied", "%color%", color.displayName());
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
        if (!awaitingGradient.remove(player.getUniqueId())) return;
        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> handleGradientInput(player, input));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        awaitingGradient.remove(event.getPlayer().getUniqueId());
    }

    private void handleGradientInput(Player player, String raw) {
        if (!player.hasPermission("namecolor.set.color.custom")) {
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
        String cmd = config.getString("custom-apply-command", "namecolor:gradient %gradient%")
                .replace("%player%", player.getName())
                .replace("%player_name%", player.getName())
                .replace("%gradient%", gradient);
        run(player, cmd);
        if (database != null) database.saveLastGradient(player.getUniqueId(), gradient);
        send(player, fromChat ? "custom-set" : "custom-reapplied", "%gradient%", gradient);
    }

    private String normalizeGradientInput(String raw) {
        if (raw == null || !GRADIENT_INPUT.matcher(raw.trim()).matches()) return null;
        String[] parts = raw.trim().split("\\s+");
        return ensureHash(parts[0]) + " " + ensureHash(parts[1]);
    }

    private String ensureHash(String hex) {
        return hex.startsWith("#") ? hex.toUpperCase() : "#" + hex.toUpperCase();
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
                               String material, String displayName, List<String> lore,
                               boolean customInput) {
    }
}
