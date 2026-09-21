package com.sharded.core.modules.tokens;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.Numbers;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import com.sharded.core.util.TrackedInventories;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

public final class TokensModule extends Module implements CommandExecutor, TabCompleter {
   private TokenDatabase database;
   private TokenService service;
   private BukkitTask playtimeTask;
   private final Map<UUID, Long> onlineSince = new ConcurrentHashMap<>();
   private TokenMethodsGuiHandler tokenMethodsGui;
   private static final String PLAYTIME_LAST_GRANT = "tokens-hourly-last-grant";

   public TokensModule(ShardedCore plugin) {
      super(plugin, "tokens");
   }

   public TokenService service() {
      return this.service;
   }

   public TokenDatabase database() {
      return this.database;
   }

   private void migrateMethods() {
      if ("GRAY_SHULKER_BOX".equalsIgnoreCase(this.config.getString("gui.methods.killstreaks.material", ""))
         && "MACE".equalsIgnoreCase(this.config.getString("gui.methods.kill_rewards.material", ""))
         && "SOUL_TORCH".equalsIgnoreCase(this.config.getString("gui.methods.playtime.material", ""))
         && this.config.getInt("config-version", 0) >= 10) {
         return;
      }
      try (InputStream input = this.plugin.getResource(this.jarResourcePath("config.yml"))) {
         if (input == null) {
            return;
         }
         YamlConfiguration bundled = YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
         ConfigurationSection methods = bundled.getConfigurationSection("gui.methods");
         this.config.set("gui.methods", null);
         if (methods != null) {
            for (String key : methods.getKeys(true)) {
               if (!methods.isConfigurationSection(key)) {
                  this.config.set("gui.methods." + key, methods.get(key));
               }
            }
         }
         if (bundled.contains("gui.filler-material")) {
            this.config.set("gui.filler-material", bundled.getString("gui.filler-material"));
         }
         this.config.set("gui.size", bundled.getInt("gui.size", this.config.getInt("gui.size", 27)));
         this.config.set("config-version", 10);
         this.config.save(new File(this.moduleFolder(), "config.yml"));
      } catch (Exception exception) {
         this.plugin.getLogger().warning("[tokens] Could not update token methods: " + exception.getMessage());
      }
   }

   String configString(String path, String def) {
      return this.config.getString(path, def);
   }

   int configInt(String path, int def) {
      return this.config.getInt(path, def);
   }

   ConfigurationSection configSection(String path) {
      return this.config.getConfigurationSection(path);
   }

   public String tokenPrefix() {
      return this.messagePrefix();
   }

   @Override
   protected void onEnable() {
      this.migrateMethods();
      try {
         this.database = new TokenDatabase(this.plugin, this.moduleFolder());
         this.service = new TokenService(this.database);
      } catch (Exception exception) {
         throw new IllegalStateException("Could not open token database", exception);
      }

      this.plugin.gui().setNoPermissionMessage(this.raw("no-permission", new String[0]));
      File file1 = this.syncMenusFolder();
      this.plugin.gui().loadFolder(file1, "");
      this.registerCommand("bal", this);
      this.registerCommand("tokens", this);
      this.registerCommand("tokenshop", this);
      this.registerCommand("tokenmethods", this);
      this.registerCommand("hourly", this);
      this.tokenMethodsGui = new TokenMethodsGuiHandler(this);
      this.startPlaytimeRewards();
   }

   private void startPlaytimeRewards() {
      if (this.config.getBoolean("playtime-reward.enabled", true)) {
         long i = Math.max(20L, this.config.getLong("playtime-reward.check-interval-seconds", 60L) * 20L);
         this.playtimeTask = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, this::tickPlaytimeRewards, i, i);
      }
   }

   private void tickPlaytimeRewards() {
      if (this.service != null) {
         long i = this.config.getLong("playtime-reward.amount", 50L);
         long j = this.config.getLong("playtime-reward.interval-minutes", 60L) * 60000L;
         if (i > 0L && j > 0L) {
            long k = System.currentTimeMillis();

            for (Player player : this.plugin.getServer().getOnlinePlayers()) {
               UUID uuid = player.getUniqueId();
               long l = this.plugin.stateStore().getLong(uuid, "tokens-hourly-last-grant", this.onlineSince.getOrDefault(uuid, k));
               if (k - l >= j) {
                  this.service.give(uuid, i);
                  this.plugin.stateStore().setLong(uuid, "tokens-hourly-last-grant", k);
                  this.send(player, "playtime-reward", new String[]{"%amount%", String.valueOf(i)});
               }
            }
         }
      }
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent event) {
      if (this.config.getBoolean("playtime-reward.enabled", true)) {
         UUID uuid = event.getPlayer().getUniqueId();
         long i = System.currentTimeMillis();
         this.onlineSince.put(uuid, i);
         if (this.plugin.stateStore().getLong(uuid, "tokens-hourly-last-grant", 0L) == 0L) {
            this.plugin.stateStore().setLong(uuid, "tokens-hourly-last-grant", i);
         }
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.onlineSince.remove(event.getPlayer().getUniqueId());
   }

   private File syncMenusFolder() {
      File file1 = new File(this.moduleFolder(), "menus");
      file1.mkdirs();

      for (String s : List.of("mainmenu", "glow", "keys", "cosmetics", "gradients", "chatcolors", "tags", "backpack")) {
         this.syncJarResource("menus/" + s + ".yml");
      }

      return file1;
   }

   @Override
   protected void onDisable() {
      if (this.playtimeTask != null) {
         this.playtimeTask.cancel();
      }

      this.playtimeTask = null;
      this.onlineSince.clear();
      if (this.database != null) {
         this.database.close();
      }

      this.database = null;
      this.service = null;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      String s = command.getName().toLowerCase(Locale.ROOT);
      switch (s) {
         case "bal":
            if (args.length == 0) {
               if (sender instanceof Player player3) {
                  long j = this.service.getBalance(player3.getUniqueId());
                  this.send(player3, "balance-self", new String[]{"%amount%", String.valueOf(j), "%formatted%", Numbers.format(j)});
                  return true;
               }

               this.send(sender, "players-only", new String[0]);
               return true;
            }

            OfflinePlayer offlineplayer = this.service.resolve(args[0]);
            long i = this.service.getBalance(offlineplayer.getUniqueId());
            this.send(
               sender, "balance-other", new String[]{"%player%", this.name(offlineplayer), "%amount%", String.valueOf(i), "%formatted%", Numbers.format(i)}
            );
            return true;
         case "tokenshop":
            if (!(sender instanceof Player player2)) {
               this.send(sender, "players-only", new String[0]);
               return true;
            }

            if (!player2.hasPermission("sharded.tokenshop.use")) {
               this.send(player2, "no-permission", new String[0]);
               return true;
            }

            this.plugin.gui().open(player2, this.config.getString("main-menu", "mainmenu"));
            break;
         case "tokenmethods":
            if (sender instanceof Player player1) {
               this.tokenMethodsGui.open(player1);
               return true;
            }

            this.send(sender, "players-only", new String[0]);
            return true;
         case "hourly":
            if (sender instanceof Player player) {
               this.handleHourly(player);
               return true;
            }

            this.send(sender, "players-only", new String[0]);
            return true;
         case "tokens":
            this.handleTokensAdmin(sender, args);
      }

      return true;
   }

   private void handleTokensAdmin(CommandSender sender, String[] args) {
      if (args.length == 0) {
         this.send(sender, "tokens-usage", new String[0]);
      } else {
         String s = args[0].toLowerCase(Locale.ROOT);
         switch (s) {
            case "give":
               if (!sender.hasPermission("sharded.tokens.admin")) {
                  this.send(sender, "no-permission", new String[0]);
                  return;
               }

               if (args.length < 3) {
                  this.send(sender, "give-usage", new String[0]);
                  return;
               }

               OfflinePlayer offlineplayer3 = this.service.resolve(args[1]);
               long i1 = this.parseAmount(args[2]);
               this.service.give(offlineplayer3.getUniqueId(), i1);
               this.send(sender, "given", new String[]{"%player%", this.name(offlineplayer3), "%amount%", String.valueOf(i1)});
               break;
            case "set":
               if (!sender.hasPermission("sharded.tokens.admin")) {
                  this.send(sender, "no-permission", new String[0]);
                  return;
               }

               if (args.length < 3) {
                  this.send(sender, "set-usage", new String[0]);
                  return;
               }

               OfflinePlayer offlineplayer2 = this.service.resolve(args[1]);
               long l = this.parseAmount(args[2]);
               this.service.setBalance(offlineplayer2.getUniqueId(), l);
               this.send(sender, "set", new String[]{"%player%", this.name(offlineplayer2), "%amount%", String.valueOf(l)});
               break;
            case "remove":
            case "take":
               if (!sender.hasPermission("sharded.tokens.admin")) {
                  this.send(sender, "no-permission", new String[0]);
                  return;
               }

               if (args.length < 3) {
                  this.send(sender, "remove-usage", new String[0]);
                  return;
               }

               OfflinePlayer offlineplayer1 = this.service.resolve(args[1]);
               long j = this.parseAmount(args[2]);
               this.service.take(offlineplayer1.getUniqueId(), j);
               this.send(sender, "removed", new String[]{"%player%", this.name(offlineplayer1), "%amount%", String.valueOf(j)});
               break;
            case "reset":
               if (!sender.hasPermission("sharded.tokens.admin")) {
                  this.send(sender, "no-permission", new String[0]);
                  return;
               }

               if (args.length < 2) {
                  this.send(sender, "reset-usage", new String[0]);
                  return;
               }

               OfflinePlayer offlineplayer = this.service.resolve(args[1]);
               this.service.reset(offlineplayer.getUniqueId());
               this.send(sender, "reset", new String[]{"%player%", this.name(offlineplayer)});
               break;
            case "giveall":
               if (!sender.hasPermission("sharded.tokens.admin")) {
                  this.send(sender, "no-permission", new String[0]);
                  return;
               }

               if (args.length < 2) {
                  this.send(sender, "giveall-usage", new String[0]);
                  return;
               }

               long i = this.parseAmount(args[1]);
               int k = 0;

               for (Player player : Bukkit.getOnlinePlayers()) {
                  this.service.give(player.getUniqueId(), i);
                  k++;
               }

               this.send(sender, "giveall", new String[]{"%count%", String.valueOf(k), "%amount%", String.valueOf(i)});
               break;
            default:
               this.send(sender, "tokens-usage", new String[0]);
         }
      }
   }

   private long parseAmount(String raw) {
      return Numbers.parseAmount(raw);
   }

   private String name(OfflinePlayer player) {
      return player.getName() == null ? player.getUniqueId().toString().substring(0, 8) : player.getName();
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      String s = command.getName().toLowerCase(Locale.ROOT);
      if (s.equals("bal")) {
         return args.length == 1 ? TabCompleteHelper.onlinePlayers(args[0]) : List.of();
      } else if (s.equals("tokens") && sender.hasPermission("sharded.tokens.admin")) {
         if (args.length == 1) {
            return TabCompleteHelper.filter(args[0], "give", "set", "remove", "take", "reset", "giveall");
         } else if (args.length == 2 && !args[0].equalsIgnoreCase("giveall")) {
            return TabCompleteHelper.knownPlayers(args[1]);
         } else if (args.length == 3 && !args[0].equalsIgnoreCase("reset") && !args[0].equalsIgnoreCase("giveall")) {
            return TabCompleteHelper.filter(args[2], "1", "100", "1k", "10k", "100k", "1m", "10m");
         } else {
            return args.length == 2 && args[0].equalsIgnoreCase("giveall") ? TabCompleteHelper.filter(args[1], "1k", "10k", "100k", "1m", "10m") : List.of();
         }
      } else {
         return List.of();
      }
   }

   private void handleHourly(Player player) {
      if (!this.config.getBoolean("playtime-reward.enabled", true)) {
         this.send(player, "hourly-disabled", new String[0]);
      } else {
         long i = this.config.getLong("playtime-reward.amount", 50L);
         long j = this.config.getLong("playtime-reward.interval-minutes", 60L) * 60000L;
         long k = this.plugin.stateStore().getLong(player.getUniqueId(), "tokens-hourly-last-grant", 0L);
         long l = System.currentTimeMillis();
         long i1 = k + j - l;
         if (i1 <= 0L) {
            this.send(player, "hourly-ready", new String[]{"%amount%", String.valueOf(i)});
         } else {
            this.send(player, "hourly-wait", new String[]{"%time%", Text.time(i1 / 1000L), "%amount%", String.valueOf(i)});
         }
      }
   }

   @EventHandler
   public void onTokenMethodsClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player player) {
         if (TrackedInventories.lookup(event.getView().getTopInventory(), TokenMethodsGuiHandler.Holder.class) != null) {
            event.setCancelled(true);
            if (event.getClickedInventory() == event.getView().getTopInventory()) {
               this.tokenMethodsGui.handleClick(player, event.getSlot());
            }
         }
      }
   }
}
