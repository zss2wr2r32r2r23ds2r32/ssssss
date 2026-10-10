package com.sharded.core.modules.staff;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.DiscordWebhook;
import com.sharded.core.modules.punishments.PunishmentsModule;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** Staff tools: staffmode, punishments, audit logging, vanish, freeze, wipe, alts. */
public final class StaffModule extends Module implements CommandExecutor, TabCompleter {

    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String GMSP_PREVIOUS = "gmsp-previous-mode";

    private StaffModeManager staffMode;

    private File auditLogFile;
    private Set<String> auditCommands;
    private Set<String> auditSubcommands;
    private Set<String> auditPermissions;

    public StaffModule(ShardedCore plugin) {
        super(plugin, "core", "staff");
    }

    public YamlConfiguration config() {
        return config;
    }

    public PunishmentsModule punishments() {
        return plugin.modules().get(PunishmentsModule.class);
    }

    public StaffModeManager staffMode() {
        return staffMode;
    }

    @Override
    protected void onEnable() {
        staffMode = new StaffModeManager(plugin, this);
        registerListener(staffMode);

        auditLogFile = new File(moduleFolder(), config.getString("audit-log-file", "audit.log"));
        reloadAuditLists();

        registerCommand("gmc", this);
        registerCommand("gms", this);
        registerCommand("gmsp", this);
        registerCommand("staffmode", this);
        registerCommand("sfmode", this);
        registerCommand("vanish", this);
        registerCommand("freeze", this);
        registerCommand("stafflist", this);
        registerCommand("randomtp", this);
        registerCommand("gtp", this);
        registerCommand("gotoplayer", this);
    }

    @Override
    protected void onDisable() {
        staffMode = null;
    }

    private void reloadAuditLists() {
        auditCommands = loadLowerSet("audit-commands");
        auditSubcommands = loadLowerSet("audit-subcommands");
        auditPermissions = loadLowerSet("audit-permissions");
    }

    private Set<String> loadLowerSet(String path) {
        Set<String> set = new HashSet<>();
        for (String entry : config.getStringList(path)) {
            if (entry != null && !entry.isBlank()) set.add(entry.toLowerCase(Locale.ROOT));
        }
        return set;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        return switch (name) {
            case "gmc", "gms", "gmsp" -> handleGamemode(sender, name);
            case "staffmode", "sfmode" -> handleStaffMode(sender);
            case "vanish" -> handleVanish(sender);
            case "freeze" -> handleFreeze(sender, args);
            case "stafflist" -> handleStaffList(sender);
            case "randomtp" -> handleRandomTp(sender);
            case "gtp", "gotoplayer" -> handleGoToPlayer(sender, args);
            default -> false;
        };
    }

    private boolean handleGamemode(CommandSender sender, String cmd) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.staff.gamemode")) {
            send(sender, "no-permission");
            return true;
        }
        if ("gmsp".equals(cmd)) {
            return handleSpectatorToggle(player);
        }
        GameMode mode = switch (cmd) {
            case "gmc" -> GameMode.CREATIVE;
            case "gms" -> GameMode.SURVIVAL;
            default -> GameMode.SPECTATOR;
        };
        player.setGameMode(mode);
        send(player, "gamemode-set", "%mode%", mode.name().toLowerCase(Locale.ROOT));
        return true;
    }

    private boolean handleSpectatorToggle(Player player) {
        UUID uuid = player.getUniqueId();
        if (player.getGameMode() == GameMode.SPECTATOR) {
            String saved = plugin.stateStore().getString(uuid, GMSP_PREVIOUS, GameMode.SURVIVAL.name());
            GameMode restore;
            try {
                restore = GameMode.valueOf(saved.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                restore = GameMode.SURVIVAL;
            }
            player.setGameMode(restore);
            plugin.stateStore().setString(uuid, GMSP_PREVIOUS, "");
            send(player, "gamemode-set", "%mode%", restore.name().toLowerCase(Locale.ROOT));
            return true;
        }
        plugin.stateStore().setString(uuid, GMSP_PREVIOUS, player.getGameMode().name());
        player.setGameMode(GameMode.SPECTATOR);
        send(player, "gamemode-set", "%mode%", "spectator");
        return true;
    }

    private boolean handleGoToPlayer(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.staff.teleport")) {
            send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            send(sender, "gtp-usage");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            send(sender, "player-not-found");
            return true;
        }
        player.teleportAsync(target.getLocation()).thenAccept(success -> {
            if (success && player.isOnline()) {
                send(player, "teleported-to", "%player%", target.getName());
            }
        });
        return true;
    }

    private boolean handleStaffMode(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.staff.mode")) {
            send(sender, "no-permission");
            return true;
        }
        staffMode.toggleStaffMode(player);
        return true;
    }

    private boolean handleVanish(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.staff.vanish")) {
            send(sender, "no-permission");
            return true;
        }
        staffMode.toggleVanish(player);
        staffMode.refreshVanishItem(player);
        return true;
    }

    private boolean handleFreeze(CommandSender sender, String[] args) {
        if (!sender.hasPermission("sharded.staff.freeze")) {
            send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            send(sender, "freeze-usage");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            send(sender, "player-not-found");
            return true;
        }
        staffMode.toggleFreeze(sender instanceof Player p ? p : null, target);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (args.length == 1 && name.equals("freeze")) {
            return TabCompleteHelper.knownPlayers(args[0]);
        }
        if (args.length == 1 && (name.equals("gtp") || name.equals("gotoplayer"))
                && sender.hasPermission("sharded.staff.teleport")) {
            return TabCompleteHelper.onlinePlayers(args[0]);
        }
        return List.of();
    }

    private boolean handleStaffList(CommandSender sender) {
        if (!sender.hasPermission("sharded.staff.list")) {
            send(sender, "no-permission");
            return true;
        }
        List<Player> staff = staffMode.onlineStaff();
        if (staff.isEmpty()) {
            send(sender, "stafflist-empty");
            return true;
        }
        send(sender, "stafflist-header", "%count%", String.valueOf(staff.size()));
        for (Player member : staff) {
            String status = staffMode.isVanished(member.getUniqueId())
                    ? raw("stafflist-vanished") : raw("stafflist-visible");
            send(sender, "stafflist-entry", "%player%", member.getName(), "%status%", status);
        }
        return true;
    }

    private boolean handleRandomTp(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.staff.randomtp")) {
            send(sender, "no-permission");
            return true;
        }
        staffMode.teleportToRandomPlayer(player);
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!config.getBoolean("audit-enabled", true)) return;
        Player player = event.getPlayer();
        if (!player.hasPermission("sharded.staff")) return;

        String message = event.getMessage().trim();
        if (message.isEmpty() || message.charAt(0) != '/') return;

        String body = message.substring(1).trim();
        String[] parts = body.split("\\s+");
        if (parts.length == 0) return;

        String label = normalizeLabel(parts[0]);
        String[] args = new String[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);

        if (!shouldAudit(player, label, args, body)) return;
        recordAudit(player, message);
    }

    private String normalizeLabel(String raw) {
        String label = raw.toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) label = label.substring(colon + 1);
        return label;
    }

    private boolean shouldAudit(Player player, String label, String[] args, String body) {
        String lowerBody = body.toLowerCase(Locale.ROOT);
        for (String pattern : auditSubcommands) {
            if (lowerBody.equals(pattern) || lowerBody.startsWith(pattern + " ")) return true;
        }
        if (auditCommands.contains(label) || auditCommands.contains("*")) return true;

        String mappedPerm = mappedPermission(label, args);
        if (mappedPerm != null && player.hasPermission(mappedPerm) && isAuditedPermission(mappedPerm)) return true;

        String commandPerm = commandPermission(label);
        if (commandPerm != null && player.hasPermission(commandPerm) && isAuditedPermission(commandPerm)) return true;

        return commandPermissionDefaultOp(label);
    }

    private String mappedPermission(String label, String[] args) {
        ConfigurationSection section = config.getConfigurationSection("audit-command-permissions." + label);
        if (section == null || args.length == 0) return null;
        return section.getString(args[0].toLowerCase(Locale.ROOT));
    }

    private boolean isAuditedPermission(String permission) {
        if (permission == null || permission.isBlank()) return false;
        if (auditPermissions.contains("*") || auditPermissions.contains(permission.toLowerCase(Locale.ROOT))) return true;
        var node = Bukkit.getPluginManager().getPermission(permission);
        return node != null && node.getDefault() != org.bukkit.permissions.PermissionDefault.TRUE;
    }

    private String commandPermission(String label) {
        Command cmd = Bukkit.getCommandMap().getCommand(label);
        if (cmd == null) return null;
        String perm = cmd.getPermission();
        if (perm != null && !perm.isBlank()) return perm;
        PluginCommand pluginCommand = plugin.getCommand(label);
        return pluginCommand == null ? null : pluginCommand.getPermission();
    }

    private boolean commandPermissionDefaultOp(String label) {
        String perm = commandPermission(label);
        if (perm == null || perm.isBlank()) return config.getBoolean("audit-null-permission-commands", false);
        var permission = Bukkit.getPluginManager().getPermission(perm);
        return permission != null && permission.getDefault() == org.bukkit.permissions.PermissionDefault.OP;
    }

    private String auditPrefix() {
        return com.sharded.core.util.ColorUtil.normalize(
                config.getString("audit-prefix", "&#AD4EFF&lAUDIT LOGS &8> &r"));
    }

    private void recordAudit(Player player, String commandLine) {
        String line = "[" + LOG_TIME.format(LocalDateTime.now()) + "] "
                + player.getName() + " (" + player.getUniqueId() + "): " + commandLine;
        appendLog(line);
        notifyStaff(player, commandLine);
        sendWebhook(player, commandLine);
    }

    private void appendLog(String line) {
        if (!config.getBoolean("audit-log-to-file", true)) return;
        try {
            File parent = auditLogFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (PrintWriter writer = new PrintWriter(new FileWriter(auditLogFile, true))) {
                writer.println(line);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[staff] Could not write audit log: " + e.getMessage());
        }
    }

    private void notifyStaff(Player actor, String commandLine) {
        if (!config.getBoolean("audit-notify-staff", true)) return;
        String notifyPerm = config.getString("audit-notify-permission", "sharded.staff.notify");
        String body = Text.apply(
                messages.getString("audit-staff", "&f%player% &7used &f%command%"),
                "%player%", actor.getName(),
                "%command%", commandLine);
        var formatted = Text.c(auditPrefix() + body);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(actor)) continue;
            if (online.hasPermission(notifyPerm)) online.sendMessage(formatted);
        }
    }

    private void sendWebhook(Player player, String commandLine) {
        if (!config.getBoolean("discord-webhook.enabled", false)) return;
        String url = config.getString("discord-webhook.url", "");
        if (url.isBlank()) return;
        String title = config.getString("discord-webhook.title", "Staff Command Audit");
        String description = config.getString("discord-webhook.description",
                        "**%player%** ran `%command%`")
                .replace("%player%", player.getName())
                .replace("%command%", commandLine)
                .replace("%uuid%", player.getUniqueId().toString())
                .replace("%world%", player.getWorld().getName());
        int color = (int) config.getLong("discord-webhook.color", 0xAD4EFF);
        DiscordWebhook.sendAsync(plugin.getLogger(), url, title, description, color);
    }
}
