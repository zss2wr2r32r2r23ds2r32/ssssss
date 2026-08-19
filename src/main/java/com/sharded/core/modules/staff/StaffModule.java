package com.sharded.core.modules.staff;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.DiscordWebhook;
import com.sharded.core.util.OfflinePlayers;
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
import java.util.logging.Level;

/** Staff tools: staffmode, punishments, audit logging, vanish, freeze, wipe, alts. */
public final class StaffModule extends Module implements CommandExecutor, TabCompleter {

    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private StaffDatabase database;
    private StaffModeManager staffMode;
    private PunishmentManager punishments;

    private File auditLogFile;
    private Set<String> auditCommands;
    private Set<String> auditSubcommands;
    private Set<String> auditPermissions;

    public StaffModule(ShardedCore plugin) {
        super(plugin, "staff");
    }

    public YamlConfiguration config() {
        return config;
    }

    public PunishmentManager punishments() {
        return punishments;
    }

    public StaffModeManager staffMode() {
        return staffMode;
    }

    @Override
    protected void onEnable() {
        try {
            database = new StaffDatabase(plugin, moduleFolder());
        } catch (Exception e) {
            throw new IllegalStateException("Could not open staff database", e);
        }
        staffMode = new StaffModeManager(plugin, this);
        punishments = new PunishmentManager(plugin, this, database);
        registerListener(staffMode);
        registerListener(punishments);

        auditLogFile = new File(moduleFolder(), config.getString("audit-log-file", "audit.log"));
        reloadAuditLists();

        registerCommand("gmc", this);
        registerCommand("gms", this);
        registerCommand("gmsp", this);
        registerCommand("staffmode", this);
        registerCommand("sfmode", this);
        registerCommand("vanish", this);
        registerCommand("freeze", this);
        registerCommand("punish", this);
        registerCommand("ban", this);
        registerCommand("banip", this);
        registerCommand("offend", this);
        registerCommand("unban", this);
        registerCommand("unmute", this);
        registerCommand("pardon", this);
        registerCommand("wipe", this);
        registerCommand("alts", this);
        registerCommand("stafflist", this);
        registerCommand("randomtp", this);
    }

    @Override
    protected void onDisable() {
        if (database != null) database.close();
        database = null;
        staffMode = null;
        punishments = null;
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
            case "punish" -> handlePunish(sender, args);
            case "ban" -> handleBan(sender, args);
            case "banip" -> handleBanIp(sender, args);
            case "offend" -> handleOffend(sender, args);
            case "unban" -> handleSimpleUnpunish(sender, args, "unban");
            case "unmute" -> handleSimpleUnpunish(sender, args, "unmute");
            case "pardon" -> handleSimpleUnpunish(sender, args, "pardon");
            case "wipe" -> handleWipe(sender, args);
            case "alts" -> handleAlts(sender, args);
            case "stafflist" -> handleStaffList(sender);
            case "randomtp" -> handleRandomTp(sender);
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
        GameMode mode = switch (cmd) {
            case "gmc" -> GameMode.CREATIVE;
            case "gms" -> GameMode.SURVIVAL;
            default -> GameMode.SPECTATOR;
        };
        player.setGameMode(mode);
        send(player, "gamemode-set", "%mode%", mode.name().toLowerCase(Locale.ROOT));
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

    private boolean handlePunish(CommandSender sender, String[] args) {
        if (!(sender instanceof Player staff)) {
            send(sender, "players-only");
            return true;
        }
        if (args.length == 0) {
            send(staff, "punish-usage");
            return true;
        }
        OfflinePlayer target = OfflinePlayers.resolve(args[0]);
        if (target == null || (target.getName() == null && !target.hasPlayedBefore())) {
            send(staff, "player-not-found");
            return true;
        }
        punishments.openPunishMenu(staff, target);
        return true;
    }

    private boolean handleBan(CommandSender sender, String[] args) {
        if (args.length == 0) {
            send(sender, "ban-usage");
            return true;
        }
        OfflinePlayer target = OfflinePlayers.resolve(args[0]);
        if (target == null) {
            send(sender, "player-not-found");
            return true;
        }
        String reason = args.length >= 2 ? args[1] : config.getString("ban.default-reason", "No reason specified");
        String duration = args.length >= 3 ? args[2] : defaultDuration("reasons", reason);
        punishments.ban(sender, target, reason, duration);
        return true;
    }

    private boolean handleBanIp(CommandSender sender, String[] args) {
        if (args.length == 0) {
            send(sender, "banip-usage");
            return true;
        }
        OfflinePlayer target = OfflinePlayers.resolve(args[0]);
        if (target == null) {
            send(sender, "player-not-found");
            return true;
        }
        String reason = args.length >= 2 ? args[1] : config.getString("ban.default-reason", "No reason specified");
        String duration = args.length >= 3 ? args[2] : defaultDuration("reasons", reason);
        punishments.banIp(sender, target, reason, duration);
        return true;
    }

    private boolean handleOffend(CommandSender sender, String[] args) {
        if (args.length == 0) {
            send(sender, "offend-usage");
            return true;
        }
        OfflinePlayer target = OfflinePlayers.resolve(args[0]);
        if (target == null) {
            send(sender, "player-not-found");
            return true;
        }
        punishments.showOffenses(sender, target);
        return true;
    }

    private boolean handleSimpleUnpunish(CommandSender sender, String[] args, String type) {
        if (args.length == 0) {
            send(sender, type + "-usage");
            return true;
        }
        OfflinePlayer target = OfflinePlayers.resolve(args[0]);
        if (target == null) {
            send(sender, "player-not-found");
            return true;
        }
        switch (type) {
            case "unban" -> punishments.unban(sender, target);
            case "unmute" -> punishments.unmute(sender, target);
            case "pardon" -> punishments.pardon(sender, target);
        }
        return true;
    }

    private boolean handleWipe(CommandSender sender, String[] args) {
        if (!(sender instanceof Player staff)) {
            send(sender, "players-only");
            return true;
        }
        if (args.length == 0) {
            send(staff, "wipe-usage");
            return true;
        }
        OfflinePlayer target = OfflinePlayers.resolve(args[0]);
        if (target == null) {
            send(staff, "player-not-found");
            return true;
        }
        String reason = args.length >= 2 ? args[1] : "default";
        if (config.getBoolean("wipe.confirm-gui", true)) {
            punishments.openWipeConfirm(staff, target, reason);
        } else {
            punishments.wipePlayer(staff, target.getUniqueId(), OfflinePlayers.name(target.getUniqueId()), reason);
        }
        return true;
    }

    private boolean handleAlts(CommandSender sender, String[] args) {
        OfflinePlayer target;
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                send(sender, "alts-usage");
                return true;
            }
            target = player;
        } else {
            target = OfflinePlayers.resolve(args[0]);
        }
        if (target == null) {
            send(sender, "player-not-found");
            return true;
        }
        punishments.showAlts(sender, target);
        return true;
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

    private String defaultDuration(String sectionPath, String reason) {
        ConfigurationSection section = config.getConfigurationSection(sectionPath);
        if (section == null || !section.contains(reason)) return "permanent";
        Object raw = section.get(reason);
        if (raw instanceof List<?> list && !list.isEmpty()) return String.valueOf(list.get(0));
        return raw == null ? "permanent" : String.valueOf(raw);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            return switch (name) {
                case "freeze", "punish", "ban", "banip", "offend", "unban", "unmute", "pardon", "wipe", "alts" ->
                        TabCompleteHelper.knownPlayers(args[0]);
                default -> List.of();
            };
        }
        if (args.length == 2) {
            return switch (name) {
                case "ban", "banip" -> TabCompleteHelper.configKeys(args[1], punishments.banReasons());
                case "wipe" -> TabCompleteHelper.configKeys(args[1], punishments.wipeReasons());
                default -> List.of();
            };
        }
        if (args.length == 3 && (name.equals("ban") || name.equals("banip"))) {
            ConfigurationSection section = config.getConfigurationSection("reasons");
            if (section == null || !section.contains(args[1])) return List.of();
            Object raw = section.get(args[1]);
            if (raw instanceof List<?> list) {
                List<String> durations = new ArrayList<>();
                for (Object entry : list) durations.add(String.valueOf(entry));
                return TabCompleteHelper.filter(args[2], durations);
            }
            return TabCompleteHelper.filter(args[2], String.valueOf(raw));
        }
        return List.of();
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
