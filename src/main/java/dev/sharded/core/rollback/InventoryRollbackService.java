package dev.sharded.core.rollback;

import dev.sharded.core.ShardedCore;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class InventoryRollbackService implements Listener, CommandExecutor, TabCompleter {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final ShardedCore plugin;
    private File root;

    public InventoryRollbackService(ShardedCore plugin) {
        this.plugin = plugin;
    }

    public void load() {
        root = new File(plugin.getDataFolder(), "rollback");
        if (!root.exists()) {
            root.mkdirs();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.getConfig().getBoolean("rollback.save-on-join", true)) {
            save(event.getPlayer(), "join");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.getConfig().getBoolean("rollback.save-on-quit", true)) {
            save(event.getPlayer(), "quit");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (plugin.getConfig().getBoolean("rollback.save-on-death", true)) {
            save(event.getEntity(), "death");
        }
    }

    public void save(Player player, String reason) {
        File folder = playerFolder(player.getUniqueId());
        folder.mkdirs();
        long now = System.currentTimeMillis();
        File file = new File(folder, now + ".yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", player.getName());
        yaml.set("uuid", player.getUniqueId().toString());
        yaml.set("reason", reason);
        yaml.set("when", Instant.ofEpochMilli(now).toString());
        yaml.set("level", player.getLevel());
        yaml.set("exp", (double) player.getExp());
        yaml.set("total-exp", player.getTotalExperience());
        yaml.set("contents", Arrays.asList(player.getInventory().getContents()));
        yaml.set("extra", Arrays.asList(player.getInventory().getExtraContents()));
        try {
            yaml.save(file);
        } catch (IOException ignored) {
        }
        prune(folder);
    }

    private void prune(File folder) {
        int keep = Math.max(1, plugin.getConfig().getInt("rollback.keep-per-player", 20));
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length <= keep) {
            return;
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        for (int i = keep; i < files.length; i++) {
            files[i].delete();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("shardedcore.invrollback")) {
            sender.sendMessage(plugin.messages().get("core.no-permission"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(plugin.messages().get("rollback.usage"));
            return true;
        }
        UUID uuid = plugin.players().lookup(args[0]);
        if (uuid == null) {
            sender.sendMessage(plugin.messages().get("rollback.unknown-player", "%player%", args[0]));
            return true;
        }
        String name = plugin.players().name(uuid);
        if (name == null) {
            name = args[0];
        }
        List<File> snapshots = snapshots(uuid);
        if (snapshots.isEmpty()) {
            sender.sendMessage(plugin.messages().get("rollback.none", "%player%", name));
            return true;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("list")) {
            sender.sendMessage(plugin.messages().get("rollback.list-header", "%player%", name));
            for (int i = 0; i < snapshots.size(); i++) {
                SnapshotMeta meta = readMeta(snapshots.get(i));
                sender.sendMessage(plugin.messages().get(
                        "rollback.list-line",
                        "%index%", String.valueOf(i + 1),
                        "%when%", meta.when,
                        "%reason%", meta.reason
                ));
            }
            return true;
        }
        int index = 1;
        if (args.length >= 2 && !args[1].equalsIgnoreCase("latest")) {
            try {
                index = Integer.parseInt(args[1]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(plugin.messages().get("rollback.usage"));
                return true;
            }
        }
        if (index < 1 || index > snapshots.size()) {
            sender.sendMessage(plugin.messages().get("rollback.none", "%player%", name));
            return true;
        }
        File file = snapshots.get(index - 1);
        Player target = Bukkit.getPlayer(uuid);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(plugin.messages().get("rollback.unknown-player", "%player%", name));
            return true;
        }
        restore(target, file);
        SnapshotMeta meta = readMeta(file);
        sender.sendMessage(plugin.messages().get("rollback.restored", "%player%", name, "%when%", meta.when));
        if (!sender.equals(target)) {
            target.sendMessage(plugin.messages().get("rollback.restored", "%player%", name, "%when%", meta.when));
        }
        return true;
    }

    private void restore(Player player, File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<ItemStack> contents = (List<ItemStack>) yaml.getList("contents", List.of());
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        ItemStack[] array = contents.toArray(ItemStack[]::new);
        for (int i = 0; i < Math.min(array.length, inventory.getSize()); i++) {
            inventory.setItem(i, array[i]);
        }
        player.setLevel(yaml.getInt("level"));
        player.setExp((float) yaml.getDouble("exp"));
        player.setTotalExperience(yaml.getInt("total-exp"));
        player.updateInventory();
    }

    private List<File> snapshots(UUID uuid) {
        File folder = playerFolder(uuid);
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return List.of();
        }
        List<File> list = new ArrayList<>(Arrays.asList(files));
        list.sort(Comparator.comparingLong(File::lastModified).reversed());
        return list;
    }

    private SnapshotMeta readMeta(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String when = yaml.getString("when", TIME.format(Instant.ofEpochMilli(file.lastModified())));
        try {
            when = TIME.format(Instant.parse(when));
        } catch (RuntimeException ignored) {
        }
        return new SnapshotMeta(when, yaml.getString("reason", "unknown"));
    }

    private File playerFolder(UUID uuid) {
        return new File(root, uuid.toString());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("shardedcore.invrollback")) {
            return List.of();
        }
        if (args.length == 1) {
            return plugin.players().tabNames(args[0]);
        }
        if (args.length == 2) {
            String needle = args[1].toLowerCase(Locale.ROOT);
            List<String> options = new ArrayList<>(List.of("latest", "list"));
            options.removeIf(s -> !s.startsWith(needle));
            return options;
        }
        return List.of();
    }

    private record SnapshotMeta(String when, String reason) {
    }
}
