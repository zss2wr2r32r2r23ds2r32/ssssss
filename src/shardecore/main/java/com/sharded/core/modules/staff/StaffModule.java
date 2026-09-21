package com.sharded.core.modules.staff;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.punishments.PunishmentsModule;
import com.sharded.core.util.ColorUtil;
import com.sharded.core.util.DiscordWebhook;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
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
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

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
      return this.config;
   }

   public PunishmentsModule punishments() {
      return this.plugin.modules().get(PunishmentsModule.class);
   }

   public StaffModeManager staffMode() {
      return this.staffMode;
   }

   @Override
   protected void onEnable() {
      this.staffMode = new StaffModeManager(this.plugin, this);
      this.registerListener(this.staffMode);
      this.auditLogFile = new File(this.moduleFolder(), this.config.getString("audit-log-file", "audit.log"));
      this.reloadAuditLists();
      this.registerCommand("gmc", this);
      this.registerCommand("gms", this);
      this.registerCommand("gmsp", this);
      this.registerCommand("staffmode", this);
      this.registerCommand("sfmode", this);
      this.registerCommand("vanish", this);
      this.registerCommand("freeze", this);
      this.registerCommand("stafflist", this);
      this.registerCommand("randomtp", this);
      this.registerCommand("gtp", this);
      this.registerCommand("gotoplayer", this);
   }

   @Override
   protected void onDisable() {
      this.staffMode = null;
   }

   private void reloadAuditLists() {
      this.auditCommands = this.loadLowerSet("audit-commands");
      this.auditSubcommands = this.loadLowerSet("audit-subcommands");
      this.auditPermissions = this.loadLowerSet("audit-permissions");
   }

   private Set<String> loadLowerSet(String path) {
      Set<String> set = new HashSet<>();

      for (String s : this.config.getStringList(path)) {
         if (s != null && !s.isBlank()) {
            set.add(s.toLowerCase(Locale.ROOT));
         }
      }

      return set;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      String s = command.getName().toLowerCase(Locale.ROOT);

      return switch (s) {
         case "gmc", "gms", "gmsp" -> this.handleGamemode(sender, s);
         case "staffmode", "sfmode" -> this.handleStaffMode(sender);
         case "vanish" -> this.handleVanish(sender);
         case "freeze" -> this.handleFreeze(sender, args);
         case "stafflist" -> this.handleStaffList(sender);
         case "randomtp" -> this.handleRandomTp(sender);
         case "gtp", "gotoplayer" -> this.handleGoToPlayer(sender, args);
         default -> false;
      };
   }

   private boolean handleGamemode(CommandSender sender, String cmd) {
      if (sender instanceof Player player) {
         if (!player.hasPermission("sharded.staff.gamemode")) {
            this.send(sender, "no-permission", new String[0]);
            return true;
         } else if ("gmsp".equals(cmd)) {
            return this.handleSpectatorToggle(player);
         } else {
            GameMode gamemode = switch (cmd) {
               case "gmc" -> GameMode.CREATIVE;
               case "gms" -> GameMode.SURVIVAL;
               default -> GameMode.SPECTATOR;
            };
            player.setGameMode(gamemode);
            this.send(player, "gamemode-set", new String[]{"%mode%", gamemode.name().toLowerCase(Locale.ROOT)});
            return true;
         }
      } else {
         this.send(sender, "players-only", new String[0]);
         return true;
      }
   }

   private boolean handleSpectatorToggle(Player player) {
      UUID uuid = player.getUniqueId();
      if (player.getGameMode() == GameMode.SPECTATOR) {
         String s = this.plugin.stateStore().getString(uuid, "gmsp-previous-mode", GameMode.SURVIVAL.name());

         GameMode gamemode;
         try {
            gamemode = GameMode.valueOf(s.toUpperCase(Locale.ROOT));
         } catch (IllegalArgumentException illegalargumentexception) {
            gamemode = GameMode.SURVIVAL;
         }

         player.setGameMode(gamemode);
         this.plugin.stateStore().setString(uuid, "gmsp-previous-mode", "");
         this.send(player, "gamemode-set", new String[]{"%mode%", gamemode.name().toLowerCase(Locale.ROOT)});
         return true;
      } else {
         this.plugin.stateStore().setString(uuid, "gmsp-previous-mode", player.getGameMode().name());
         player.setGameMode(GameMode.SPECTATOR);
         this.send(player, "gamemode-set", new String[]{"%mode%", "spectator"});
         return true;
      }
   }

   private boolean handleGoToPlayer(CommandSender sender, String[] args) {
      if (sender instanceof Player player) {
         if (!player.hasPermission("sharded.staff.teleport")) {
            this.send(sender, "no-permission", new String[0]);
            return true;
         } else if (args.length == 0) {
            this.send(sender, "gtp-usage", new String[0]);
            return true;
         } else {
            Player player1 = Bukkit.getPlayerExact(args[0]);
            if (player1 == null) {
               this.send(sender, "player-not-found", new String[0]);
               return true;
            } else {
               player.teleportAsync(player1.getLocation()).thenAccept(success -> {
                  if (success && player.isOnline()) {
                     this.send(player, "teleported-to", new String[]{"%player%", player1.getName()});
                  }
               });
               return true;
            }
         }
      } else {
         this.send(sender, "players-only", new String[0]);
         return true;
      }
   }

   private boolean handleStaffMode(CommandSender sender) {
      if (sender instanceof Player player) {
         if (!player.hasPermission("sharded.staff.mode")) {
            this.send(sender, "no-permission", new String[0]);
            return true;
         } else {
            this.staffMode.toggleStaffMode(player);
            return true;
         }
      } else {
         this.send(sender, "players-only", new String[0]);
         return true;
      }
   }

   private boolean handleVanish(CommandSender sender) {
      if (sender instanceof Player player) {
         if (!player.hasPermission("sharded.staff.vanish")) {
            this.send(sender, "no-permission", new String[0]);
            return true;
         } else {
            this.staffMode.toggleVanish(player);
            this.staffMode.refreshVanishItem(player);
            return true;
         }
      } else {
         this.send(sender, "players-only", new String[0]);
         return true;
      }
   }

   private boolean handleFreeze(CommandSender sender, String[] args) {
      if (!sender.hasPermission("sharded.staff.freeze")) {
         this.send(sender, "no-permission", new String[0]);
         return true;
      } else if (args.length == 0) {
         this.send(sender, "freeze-usage", new String[0]);
         return true;
      } else {
         Player player = Bukkit.getPlayerExact(args[0]);
         if (player == null) {
            this.send(sender, "player-not-found", new String[0]);
            return true;
         } else {
            this.staffMode.toggleFreeze(sender instanceof Player player1 ? player1 : null, player);
            return true;
         }
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      String s = command.getName().toLowerCase(Locale.ROOT);
      if (args.length == 1 && s.equals("freeze")) {
         return TabCompleteHelper.knownPlayers(args[0]);
      } else {
         return args.length == 1 && (s.equals("gtp") || s.equals("gotoplayer")) && sender.hasPermission("sharded.staff.teleport")
            ? TabCompleteHelper.onlinePlayers(args[0])
            : List.of();
      }
   }

   private boolean handleStaffList(CommandSender sender) {
      if (!sender.hasPermission("sharded.staff.list")) {
         this.send(sender, "no-permission", new String[0]);
         return true;
      } else {
         List<Player> list = this.staffMode.onlineStaff();
         if (list.isEmpty()) {
            this.send(sender, "stafflist-empty", new String[0]);
            return true;
         } else {
            this.send(sender, "stafflist-header", new String[]{"%count%", String.valueOf(list.size())});

            for (Player player : list) {
               String s = this.staffMode.isVanished(player.getUniqueId())
                  ? this.raw("stafflist-vanished", new String[0])
                  : this.raw("stafflist-visible", new String[0]);
               this.send(sender, "stafflist-entry", new String[]{"%player%", player.getName(), "%status%", s});
            }

            return true;
         }
      }
   }

   private boolean handleRandomTp(CommandSender sender) {
      if (sender instanceof Player player) {
         if (!player.hasPermission("sharded.staff.randomtp")) {
            this.send(sender, "no-permission", new String[0]);
            return true;
         } else {
            this.staffMode.teleportToRandomPlayer(player);
            return true;
         }
      } else {
         this.send(sender, "players-only", new String[0]);
         return true;
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onCommand(PlayerCommandPreprocessEvent event) {
      if (this.config.getBoolean("audit-enabled", true)) {
         Player player = event.getPlayer();
         if (player.hasPermission("sharded.staff")) {
            String s = event.getMessage().trim();
            if (!s.isEmpty() && s.charAt(0) == '/') {
               String s1 = s.substring(1).trim();
               String[] astring = s1.split("\\s+");
               if (astring.length != 0) {
                  String s2 = this.normalizeLabel(astring[0]);
                  String[] astring1 = new String[astring.length - 1];
                  System.arraycopy(astring, 1, astring1, 0, astring1.length);
                  if (this.shouldAudit(player, s2, astring1, s1)) {
                     this.recordAudit(player, s);
                  }
               }
            }
         }
      }
   }

   private String normalizeLabel(String raw) {
      String s = raw.toLowerCase(Locale.ROOT);
      int i = s.indexOf(58);
      if (i >= 0) {
         s = s.substring(i + 1);
      }

      return s;
   }

   private boolean shouldAudit(Player player, String label, String[] args, String body) {
      String s = body.toLowerCase(Locale.ROOT);

      for (String s1 : this.auditSubcommands) {
         if (s.equals(s1) || s.startsWith(s1 + " ")) {
            return true;
         }
      }

      if (!this.auditCommands.contains(label) && !this.auditCommands.contains("*")) {
         String s2 = this.mappedPermission(label, args);
         if (s2 != null && player.hasPermission(s2) && this.isAuditedPermission(s2)) {
            return true;
         } else {
            String s3 = this.commandPermission(label);
            return s3 != null && player.hasPermission(s3) && this.isAuditedPermission(s3) ? true : this.commandPermissionDefaultOp(label);
         }
      } else {
         return true;
      }
   }

   private String mappedPermission(String label, String[] args) {
      ConfigurationSection configurationsection = this.config.getConfigurationSection("audit-command-permissions." + label);
      return configurationsection != null && args.length != 0 ? configurationsection.getString(args[0].toLowerCase(Locale.ROOT)) : null;
   }

   private boolean isAuditedPermission(String permission) {
      if (permission != null && !permission.isBlank()) {
         if (!this.auditPermissions.contains("*") && !this.auditPermissions.contains(permission.toLowerCase(Locale.ROOT))) {
            Permission permissionx = Bukkit.getPluginManager().getPermission(permission);
            return permissionx != null && permissionx.getDefault() != PermissionDefault.TRUE;
         } else {
            return true;
         }
      } else {
         return false;
      }
   }

   private String commandPermission(String label) {
      Command command = Bukkit.getCommandMap().getCommand(label);
      if (command == null) {
         return null;
      } else {
         String s = command.getPermission();
         if (s != null && !s.isBlank()) {
            return s;
         } else {
            PluginCommand plugincommand = this.plugin.getCommand(label);
            return plugincommand == null ? null : plugincommand.getPermission();
         }
      }
   }

   private boolean commandPermissionDefaultOp(String label) {
      String s = this.commandPermission(label);
      if (s != null && !s.isBlank()) {
         Permission permission = Bukkit.getPluginManager().getPermission(s);
         return permission != null && permission.getDefault() == PermissionDefault.OP;
      } else {
         return this.config.getBoolean("audit-null-permission-commands", false);
      }
   }

   private String auditPrefix() {
      return ColorUtil.normalize(this.config.getString("audit-prefix", "&#AD4EFF&lAUDIT LOGS &8> &r"));
   }

   private void recordAudit(Player player, String commandLine) {
      String s = "[" + LOG_TIME.format(LocalDateTime.now()) + "] " + player.getName() + " (" + player.getUniqueId() + "): " + commandLine;
      this.appendLog(s);
      this.notifyStaff(player, commandLine);
      this.sendWebhook(player, commandLine);
   }

   private void appendLog(String line) {
      if (this.config.getBoolean("audit-log-to-file", true)) {
         try {
            File file1 = this.auditLogFile.getParentFile();
            if (file1 != null && !file1.exists()) {
               file1.mkdirs();
            }

            try (PrintWriter printwriter = new PrintWriter(new FileWriter(this.auditLogFile, true))) {
               printwriter.println(line);
            }
         } catch (IOException ioexception) {
            this.plugin.getLogger().log(Level.WARNING, "[staff] Could not write audit log: " + ioexception.getMessage());
         }
      }
   }

   private void notifyStaff(Player actor, String commandLine) {
      if (this.config.getBoolean("audit-notify-staff", true)) {
         String s = this.config.getString("audit-notify-permission", "sharded.staff.notify");
         String s1 = Text.apply(this.messages.getString("audit-staff", "&f%player% &7used &f%command%"), "%player%", actor.getName(), "%command%", commandLine);
         Component component = Text.c(this.auditPrefix() + s1);

         for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.equals(actor) && player.hasPermission(s)) {
               player.sendMessage(component);
            }
         }
      }
   }

   private void sendWebhook(Player player, String commandLine) {
      if (this.config.getBoolean("discord-webhook.enabled", false)) {
         String s = this.config.getString("discord-webhook.url", "");
         if (!s.isBlank()) {
            String s1 = this.config.getString("discord-webhook.title", "Staff Command Audit");
            String s2 = this.config
               .getString("discord-webhook.description", "**%player%** ran `%command%`")
               .replace("%player%", player.getName())
               .replace("%command%", commandLine)
               .replace("%uuid%", player.getUniqueId().toString())
               .replace("%world%", player.getWorld().getName());
            int i = (int)this.config.getLong("discord-webhook.color", 11357951L);
            DiscordWebhook.sendAsync(this.plugin.getLogger(), s, s1, s2, i);
         }
      }
   }
}
