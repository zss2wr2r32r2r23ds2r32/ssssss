package com.sharded.core.modules.staff;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.DiscordWebhook;
import com.sharded.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;

/** Staff gamemode shortcuts, command audit log, and Discord webhook alerts. */
public final class StaffModule extends Module implements CommandExecutor {

    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private File auditLogFile;
    private Set<String> auditCommands;
    private Set<String> auditSubcommands;
    private Set<String> auditPermissions;

    public StaffModule(ShardedCore plugin) {
        super(plugin, "staff");
    }

    @Override
    protected void onEnable() {
        auditLogFile = new File(moduleFolder(), config.getString("audit-log-file", "audit.log"));
        reloadAuditLists();
        registerCommand("gmc", this);
        registerCommand("gms", this);
        registerCommand("gmsp", this);
    }

    private void reloadAuditLists() {
        auditCommands = loadLowerSet("audit-commands");
        auditSubcommands = loadLowerSet("audit-subcommands");
        auditPermissions = loadLowerSet("audit-permissions");
    }

    private Set<String> loadLowerSet(String path) {
        Set<String> set = new HashSet<>();
        for (String entry : config.getStringList(path)) {
            if (entry != null && !entry.isBlank()) {
                set.add(entry.toLowerCase(Locale.ROOT));
            }
        }
        return set;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.staff.gamemode")) {
            send(player, "no-permission");
            return true;
        }
        GameMode mode = switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "gmc" -> GameMode.CREATIVE;
            case "gms" -> GameMode.SURVIVAL;
            case "gmsp" -> GameMode.SPECTATOR;
            default -> null;
        };
        if (mode == null) return true;
        player.setGameMode(mode);
        send(player, "gamemode-set", "%mode%", mode.name().toLowerCase(Locale.ROOT));
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
        if (mappedPerm != null && player.hasPermission(mappedPerm) && isAuditedPermission(mappedPerm)) {
            return true;
        }

        String commandPerm = commandPermission(label);
        if (commandPerm != null && player.hasPermission(commandPerm) && isAuditedPermission(commandPerm)) {
            return true;
        }

        return commandPermissionDefaultOp(label);
    }

    private String mappedPermission(String label, String[] args) {
        ConfigurationSection section = config.getConfigurationSection("audit-command-permissions." + label);
        if (section == null || args.length == 0) return null;
        return section.getString(args[0].toLowerCase(Locale.ROOT));
    }

    private boolean isAuditedPermission(String permission) {
        if (permission == null || permission.isBlank()) return false;
        if (auditPermissions.contains("*") || auditPermissions.contains(permission.toLowerCase(Locale.ROOT))) {
            return true;
        }
        Permission node = Bukkit.getPluginManager().getPermission(permission);
        return node != null && node.getDefault() != PermissionDefault.TRUE;
    }

    private String commandPermission(String label) {
        Command command = Bukkit.getCommandMap().getCommand(label);
        if (command == null) return null;
        String perm = command.getPermission();
        if (perm != null && !perm.isBlank()) return perm;
        PluginCommand pluginCommand = plugin.getCommand(label);
        return pluginCommand == null ? null : pluginCommand.getPermission();
    }

    private boolean commandPermissionDefaultOp(String label) {
        String perm = commandPermission(label);
        if (perm == null || perm.isBlank()) {
            return config.getBoolean("audit-null-permission-commands", false);
        }
        Permission permission = Bukkit.getPluginManager().getPermission(perm);
        return permission != null && permission.getDefault() == PermissionDefault.OP;
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
            if (online.hasPermission(notifyPerm)) {
                online.sendMessage(formatted);
            }
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
