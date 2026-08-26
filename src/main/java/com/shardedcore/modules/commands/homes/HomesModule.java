package com.shardedcore.modules.commands.homes;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import com.shardedcore.util.MessageUtil;
import com.shardedcore.util.TabCompleteHelper;
import com.shardedcore.util.TeleportHelper;
import com.shardedcore.util.Text;
import com.shardedcore.util.TrackedInventories;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HomesModule extends Module implements CommandExecutor, TabCompleter, Listener {

    private HomesDatabase database;
    private HomesGuiHandler guiHandler;
    private TeleportHelper teleportHelper;
    private final Map<Integer, Integer> slotToGui = new HashMap<>();
    private final Map<Integer, Integer> guiToSlot = new HashMap<>();

    public HomesModule(ShardedCore plugin) {
        super(plugin, "homes");
    }

    HomesDatabase database() {
        return database;
    }

    @Override
    public void enable() {
        try {
            database = new HomesDatabase(plugin, moduleFolder);
        } catch (Exception e) {
            throw new IllegalStateException("Could not open homes database", e);
        }
        guiHandler = new HomesGuiHandler(this);
        teleportHelper = new TeleportHelper(plugin);
        teleportHelper.start();
        loadSlotMappings();
        registerListener(this);
        registerCommand("homes", this);
        registerCommand("sethome", this);
        registerCommand("delhome", this);
        registerCommand("home", this);
    }

    @Override
    public void disable() {
        if (teleportHelper != null) teleportHelper.shutdown();
        if (database != null) database.close();
        slotToGui.clear();
        guiToSlot.clear();
        cleanup();
    }

    private void loadSlotMappings() {
        slotToGui.clear();
        guiToSlot.clear();
        ConfigurationSection slots = config.getConfigurationSection("gui.slots");
        if (slots != null) {
            for (String key : slots.getKeys(false)) {
                try {
                    int homeSlot = Integer.parseInt(key);
                    int guiSlot = slots.getInt(key);
                    slotToGui.put(homeSlot, guiSlot);
                    guiToSlot.put(guiSlot, homeSlot);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (slotToGui.isEmpty()) {
            int[] defaults = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
            for (int i = 0; i < defaults.length; i++) {
                slotToGui.put(i + 1, defaults[i]);
                guiToSlot.put(defaults[i], i + 1);
            }
        }
    }

    int maxSlotCount() {
        return config.getInt("max-slots", 14);
    }

    Integer guiSlotFor(int homeSlot) {
        return slotToGui.get(homeSlot);
    }

    Integer homeSlotForGuiSlot(int guiSlot) {
        return guiToSlot.get(guiSlot);
    }

    int maxHomes(Player player) {
        int highest = config.getInt("default-max-homes", 3);
        for (int i = 1; i <= maxSlotCount(); i++) {
            if (player.hasPermission("shardedcore.homes." + i)) highest = i;
        }
        return Math.min(highest, maxSlotCount());
    }

    String guiRaw(String key, String... replacements) {
        return Text.apply(config.getString("gui." + key, ""), replacements);
    }

    List<String> guiRawList(String key, String... replacements) {
        List<String> lines = new ArrayList<>(config.getStringList("gui." + key));
        List<String> out = new ArrayList<>();
        for (String line : lines) out.add(Text.apply(line, replacements));
        return out;
    }

    void teleportToHome(Player player, HomesDatabase.Home home) {
        Location target = HomesGuiHandler.toLocation(home);
        if (target == null) {
            send(player, "world-not-found", "world", home.world());
            return;
        }
        for (String disabled : config.getStringList("disabled-worlds")) {
            if (disabled.equalsIgnoreCase(target.getWorld().getName())) {
                send(player, "world-disabled", "world", target.getWorld().getName());
                return;
            }
        }
        int delay = config.getInt("teleport.delay-seconds", 5);
        String countdown = config.getString("teleport.countdown-actionbar",
                "&#FF005D&lHOME &8▷ &fTeleporting in &#FF005D&n{seconds}s");
        String cancelled = config.getString("teleport.cancelled-actionbar",
                "&#FF005D&lHOME &8▷ &fYou moved &8— &7teleport cancelled.");
        player.closeInventory();
        teleportHelper.teleportDelayed(player, target, delay, countdown, p -> send(p, "teleported",
                "slot", String.valueOf(home.slot()), "world", home.world(),
                "x", String.valueOf((int) home.x()), "y", String.valueOf((int) home.y()), "z", String.valueOf((int) home.z())),
                () -> MessageUtil.sendActionBar(player, plugin, cancelled));
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        HomesGuiHandler.HomesGuiHolder holder = TrackedInventories.lookup(
                event.getView().getTopInventory(), HomesGuiHandler.HomesGuiHolder.class);
        if (holder == null) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        guiHandler.handleClick(player, holder, event.getSlot(), event.getClick());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (teleportHelper != null) teleportHelper.cancel(event.getPlayer().getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("shardedcore.command." + command.getName().toLowerCase(Locale.ROOT))) {
            send(player, "no-permission");
            return true;
        }
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "homes" -> { guiHandler.openMain(player); yield true; }
            case "sethome" -> { handleSetHome(player, args); yield true; }
            case "delhome" -> { handleDelHome(player, args); yield true; }
            case "home" -> { handleHome(player, args); yield true; }
            default -> false;
        };
    }

    private void handleSetHome(Player player, String[] args) {
        if (args.length == 0) { send(player, "sethome-usage"); return; }
        Integer slot = parseSlot(args[0]);
        if (slot == null || slot < 1 || slot > maxSlotCount()) { send(player, "invalid-slot"); return; }
        if (slot > maxHomes(player)) {
            send(player, "slot-locked", "slot", String.valueOf(slot), "max", String.valueOf(maxHomes(player)));
            return;
        }
        Location loc = player.getLocation();
        database.setHome(player.getUniqueId(), slot, loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
        send(player, "set", "slot", String.valueOf(slot));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
    }

    private void handleDelHome(Player player, String[] args) {
        if (args.length == 0) { send(player, "delhome-usage"); return; }
        Integer slot = parseSlot(args[0]);
        if (slot == null || slot < 1 || slot > maxSlotCount()) { send(player, "invalid-slot"); return; }
        if (!database.hasHome(player.getUniqueId(), slot)) {
            send(player, "not-set", "slot", String.valueOf(slot));
            return;
        }
        guiHandler.openDeleteConfirm(player, slot);
    }

    private void handleHome(Player player, String[] args) {
        if (args.length == 0) { send(player, "home-usage"); return; }
        Integer slot = parseSlot(args[0]);
        if (slot == null || slot < 1 || slot > maxSlotCount()) { send(player, "invalid-slot"); return; }
        HomesDatabase.Home home = database.getHome(player.getUniqueId(), slot);
        if (home == null) { send(player, "not-set", "slot", String.valueOf(slot)); return; }
        teleportToHome(player, home);
    }

    private Integer parseSlot(String raw) {
        try { return Integer.parseInt(raw.trim()); } catch (NumberFormatException e) { return null; }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || args.length != 1) return List.of();
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (!cmd.equals("sethome") && !cmd.equals("delhome") && !cmd.equals("home")) return List.of();
        List<String> slots = new ArrayList<>();
        for (int i = 1; i <= maxHomes(player); i++) slots.add(String.valueOf(i));
        return TabCompleteHelper.filter(slots, args[0]);
    }
}
