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

    public StaffModule(ShardedCore plugin) {
        super(plugin, "staff");
    }

    @Override
    protected void onEnable() {
        auditLogFile = new File(moduleFolder(), config.getString("audit-log-file", "audit.log"));
        reloadAuditCommands();
        registerCommand("gmc", this);
        registerCommand("gms", this);
        registerCommand("gmsp", this);
    }

    private void reloadAuditCommands() {
        auditCommands = new HashSet<>();
        for (String entry : config.getStringList("audit-commands")) {
            if (entry != null && !entry.isBlank()) {
                auditCommands.add(entry.toLowerCase(Locale.ROOT));
            }
        }
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
        recordAudit(player, "/" + label);
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!config.getBoolean("audit-enabled", true)) return;
        Player player = event.getPlayer();
        if (!player.hasPermission("sharded.staff")) return;

        String message = event.getMessage().trim();
        if (message.isEmpty() || message.charAt(0) != '/') return;

        String[] parts = message.substring(1).split("\\s+");
        if (parts.length == 0) return;
        String label = parts[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) label = label.substring(colon + 1);

        if (!shouldAudit(label)) return;
        recordAudit(player, message);
    }

    private boolean shouldAudit(String label) {
        if (auditCommands.contains(label) || auditCommands.contains("*")) return true;
        if (commandPermissionDefaultOp(label)) return true;
        return false;
    }

    private boolean commandPermissionDefaultOp(String label) {
        Command command = Bukkit.getCommandMap().getCommand(label);
        if (command == null) return false;
        String perm = command.getPermission();
        if (perm == null || perm.isBlank()) {
            return config.getBoolean("audit-null-permission-commands", false);
        }
        Permission permission = Bukkit.getPluginManager().getPermission(perm);
        if (permission == null) {
            PluginCommand pluginCommand = plugin.getCommand(label);
            if (pluginCommand != null && pluginCommand.getPermission() != null) {
                permission = Bukkit.getPluginManager().getPermission(pluginCommand.getPermission());
            }
        }
        return permission != null && permission.getDefault() == PermissionDefault.OP;
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
        String formatted = raw("audit-staff", "%player%", actor.getName(), "%command%", commandLine);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(actor)) continue;
            if (online.hasPermission(notifyPerm)) {
                online.sendMessage(Text.c(formatted));
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
