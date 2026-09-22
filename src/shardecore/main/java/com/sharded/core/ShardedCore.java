package com.sharded.core;

import com.sharded.core.data.PlayerDataManager;
import com.sharded.core.api.leaderboard.LeaderboardManager;
import com.sharded.core.cosmetics.CosmeticService;
import com.sharded.core.gui.GuiListener;
import com.sharded.core.gui.GuiManager;
import com.sharded.core.gui.GuiNavigation;
import com.sharded.core.hook.DuelsHook;
import com.sharded.core.hook.LuckPermsHook;
import com.sharded.core.hook.PlaceholderHook;
import com.sharded.core.module.Module;
import com.sharded.core.module.ModuleManager;
import com.sharded.core.modules.arenasync.ArenaSyncModule;
import com.sharded.core.modules.chatfilter.ChatFilterModule;
import com.sharded.core.modules.commandwhitelist.CommandWhitelistModule;
import com.sharded.core.modules.cosmeticschat.CosmeticsChatModule;
import com.sharded.core.modules.dropfix.DropfixModule;
import com.sharded.core.modules.exploitfixer.ExploitFixerModule;
import com.sharded.core.modules.itemedit.ItemEditModule;
import com.sharded.core.modules.killrewards.KillRewardsModule;
import com.sharded.core.modules.leaderboardboards.LeaderboardBoardsModule;
import com.sharded.core.modules.live.LiveModule;
import com.sharded.core.modules.namegradients.NameGradientsModule;
import com.sharded.core.modules.nametags.NametagsModule;
import com.sharded.core.modules.playtimerewards.PlaytimeRewardsModule;
import com.sharded.core.modules.ranksinfo.RanksInfoModule;
import com.sharded.core.modules.serverlinks.ServerLinksModule;
import com.sharded.core.modules.staffpromote.StaffPromoteModule;
import com.sharded.core.modules.welcome.WelcomeModule;
import com.sharded.core.net.SpawnPacketFix;
import com.sharded.core.util.CommandHelp;
import com.sharded.core.util.ConfigSync;
import com.sharded.core.util.CoreTabComplete;
import com.sharded.core.util.EventLocatorBar;
import com.sharded.core.util.GuiSounds;
import com.sharded.core.util.MessageUtil;
import com.sharded.core.util.PlayerStateStore;
import com.sharded.core.util.TabCompleteHelper;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class ShardedCore extends JavaPlugin implements TabCompleter {
   private static ShardedCore instance;
   private LuckPermsHook luckPerms;
   private DuelsHook duelsHook;
   private PlaceholderHook placeholderHook;
   private PlayerStateStore stateStore;
   private GuiManager guiManager;
   private GuiNavigation guiNavigation;
   private GuiSounds guiSounds;
   private ModuleManager moduleManager;
   private CoreTabComplete coreTabComplete;
   private CosmeticService cosmeticService;
   private PlayerDataManager playerData;

   public void onEnable() {
      instance = this;
      if (!this.getDataFolder().exists()) {
         this.getDataFolder().mkdirs();
      }

      this.saveDefaultConfig();
      ConfigSync.deleteBackups(this);
      ConfigSync.syncMainConfig(this);
      this.disableRemovedModules();
      this.rewriteGuideMenu();
      this.rewriteStaleYaml("modules/tokens/tokens/menus/gradients.yml", 9);
      this.rewriteStaleYaml("modules/tokens/tokens/menus/keys.yml", 7);
      this.rewriteStaleYaml("modules/tokens/wardrobe/messages.yml", 6);
      this.rewriteStaleYaml("modules/wardrobe/messages.yml", 6);
      this.rewriteStaleYaml("modules/tokens/tokens/menus/cosmetics.yml", 9);
      this.unstickHatshopFromTokenCosmeticsMenu();
      this.rewriteStaleYaml("modules/tokens/tokens/menus/mainmenu.yml", 8);
      this.rewriteStaleYaml("modules/tokens/menus/mainmenu.yml", 8);
      this.rewriteStaleYaml("modules/ranksinfo/config.yml", 12);
      this.fixRewardWonPlaceholders("modules/weeklyrewards/messages.yml");
      this.fixRewardWonPlaceholders("modules/dailyrewards/messages.yml");
      this.luckPerms = new LuckPermsHook(this);
      this.duelsHook = new DuelsHook(this);
      this.placeholderHook = new PlaceholderHook(this);
      this.getServer().getPluginManager().registerEvents(this.placeholderHook, this);
      this.stateStore = new PlayerStateStore(this);
      this.guiNavigation = new GuiNavigation(this);
      this.guiNavigation.reload(this);
      this.guiSounds = new GuiSounds(this);
      this.guiManager = new GuiManager(this);
      this.coreTabComplete = new CoreTabComplete(this);
      this.getServer().getPluginManager().registerEvents(new GuiListener(this.guiManager), this);
      this.cosmeticService = new CosmeticService(this);
      this.cosmeticService.enable();
      this.playerData = new PlayerDataManager(this);
      this.playerData.load();
      this.moduleManager = new ModuleManager(this);
      this.registerOverlayModule(new ArenaSyncModule(this));
      this.registerOverlayModule(new WelcomeModule(this));
      this.registerOverlayModule(new LiveModule(this));
      this.registerOverlayModule(new DropfixModule(this));
      this.registerOverlayModule(new ExploitFixerModule(this));
      this.registerOverlayModule(new NametagsModule(this));
      this.registerOverlayModule(new KillRewardsModule(this));
      this.registerOverlayModule(new PlaytimeRewardsModule(this));
      this.registerOverlayModule(new ChatFilterModule(this));
      this.registerOverlayModule(new ServerLinksModule(this));
      this.registerOverlayModule(new CommandWhitelistModule(this));
      this.registerOverlayModule(new StaffPromoteModule(this));
      this.registerOverlayModule(new NameGradientsModule(this));
      this.registerOverlayModule(new RanksInfoModule(this));
      this.registerOverlayModule(new CosmeticsChatModule(this));
      this.registerOverlayModule(new ItemEditModule(this));
      this.registerOverlayModule(new LeaderboardBoardsModule(this));
      this.moduleManager.enableModules();
      SpawnPacketFix.install(this);
      EventLocatorBar.purgeLeftovers(this);
      EventLocatorBar.install(this);
      this.getServer().getScheduler().runTaskLater(this, this::removeLeftoverNametags, 20L);
      this.placeholderHook.tryRegister();
      PluginCommand plugincommand = this.getCommand("shardedcore");
      if (plugincommand != null) {
         plugincommand.setTabCompleter(this);
      }

      this.getLogger().info("ShardedCore enabled with " + this.moduleManager.enabledCount() + " modules.");
   }

   public void onDisable() {
      EventLocatorBar.shutdownActive();
      SpawnPacketFix.uninstall();
      if (this.moduleManager != null) {
         this.moduleManager.disableModules();
      }

      if (this.cosmeticService != null) {
         this.cosmeticService.disable();
      }

      if (this.stateStore != null) {
         this.stateStore.saveNow();
         this.stateStore.close();
      }

      if (this.playerData != null) {
         this.playerData.save();
      }

      instance = null;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (!command.getName().equalsIgnoreCase("shardedcore")) {
         return false;
      } else if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
         if (!sender.hasPermission("sharded.admin")) {
            MessageUtil.deliver(sender, this.coreMessage("no-permission", "&cYou don't have permission."), this.globalDelivery());
            return true;
         } else {
            ConfigSync.syncMainConfig(this);
            if (this.guiNavigation != null) {
               this.guiNavigation.reload(this);
            }

            if (this.guiSounds != null) {
               this.guiSounds.reload();
            }

            this.moduleManager.reload();
            this.placeholderHook.tryRegister();
            if (this.duelsHook != null) {
               this.duelsHook.refreshCommands();
            }

            this.stateStore.saveNow();
            MessageUtil.deliver(
               sender,
               this.coreMessage("reloaded", "&aConfiguration reloaded. &7(%modules% modules enabled)")
                  .replace("%modules%", String.valueOf(this.moduleManager.enabledCount())),
               this.globalDelivery()
            );
            return true;
         }
      } else if (args.length > 0 && args[0].equalsIgnoreCase("resetconfigs")) {
         if (!sender.hasPermission("sharded.admin")) {
            MessageUtil.deliver(sender, this.coreMessage("no-permission", "&cYou don't have permission."), this.globalDelivery());
            return true;
         } else {
            int i = ConfigSync.resetAll(this);
            this.reloadConfig();
            this.moduleManager.reload();
            this.placeholderHook.tryRegister();
            MessageUtil.deliver(
               sender,
               this.coreMessage("reset-configs", "&aReset &f%count% &aconfig files from plugin defaults.")
                  .replace("%count%", String.valueOf(i)),
               this.globalDelivery()
            );
            return true;
         }
      } else if (args.length > 0 && args[0].equalsIgnoreCase("staff")) {
         CommandHelp.sendStaff(sender, this.getConfig().getString("prefix", "&8[&bSharded&8] &r"));
         return true;
      } else {
         CommandHelp.send(sender, this.getConfig().getString("prefix", "&8[&bSharded&8] &r"), this.moduleManager);
         return true;
      }
   }

   private String coreMessage(String key, String def) {
      String prefix = this.getConfig().getString("prefix", "&8[&bSharded&8] &r");
      String body = this.getConfig().getString("messages." + key, def);
      return prefix + body;
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (!command.getName().equalsIgnoreCase("shardedcore")) {
         return List.of();
      } else {
         return args.length == 1 ? TabCompleteHelper.filter(args[0], "reload", "resetconfigs", "staff", "help") : List.of();
      }
   }

   public static ShardedCore get() {
      return instance;
   }

   public LuckPermsHook luckPerms() {
      return this.luckPerms;
   }

   public DuelsHook duelsHook() {
      return this.duelsHook;
   }

   public PlayerStateStore stateStore() {
      return this.stateStore;
   }

   public GuiManager gui() {
      return this.guiManager;
   }

   public GuiNavigation guiNavigation() {
      return this.guiNavigation;
   }

   public GuiSounds guiSounds() {
      return this.guiSounds;
   }

   public ModuleManager modules() {
      return this.moduleManager;
   }

   public CosmeticService cosmetics() {
      return this.cosmeticService;
   }

   public PlayerDataManager playerData() {
      return this.playerData;
   }

   public PlayerDataManager getPlayerDataManager() {
      return this.playerData;
   }

   public String errorColor() {
      return this.getConfig().getString("error", "&#FF2121");
   }

   public String successColor() {
      return this.getConfig().getString("success", "&#9FFF00");
   }

   public LeaderboardManager leaderboardManager() {
      return this.moduleManager == null ? null : this.moduleManager.get(LeaderboardBoardsModule.class);
   }

   public CoreTabComplete coreTabComplete() {
      return this.coreTabComplete;
   }

   private void disableRemovedModules() {
      this.getConfig().set("modules.leaderboards", true);
      this.getConfig().set("modules.leaderboardboards", true);
      this.getConfig().set("modules.leaderboardtopper", false);
      this.getConfig().set("modules.multiverse", false);
      this.getConfig().set("modules.cold", false);
      this.getConfig().set("modules.chatformat", true);
      this.getConfig().set("modules.rules", true);
      this.getConfig().set("modules.graves", true);
      this.getConfig().set("modules.hide", false);
      this.getConfig().set("modules.itemedit", true);
      this.getConfig().set("modules.crates", true);
      this.getConfig().set("modules.stats", true);
      this.getConfig().set("modules.homes", true);
      this.getConfig().set("modules.kits", true);
      if (this.getConfig().getInt("config-version", 0) < 26) {
         this.getConfig().set("config-version", 26);
      }

      this.saveConfig();
   }

   private void rewriteGuideMenu() {
      File file1 = new File(this.getDataFolder(), "modules/guide/config.yml");
      YamlConfiguration yamlconfiguration = file1.isFile() ? YamlConfiguration.loadConfiguration(file1) : new YamlConfiguration();
      boolean flag = yamlconfiguration.getInt("config-version", 0) < 12;
      if (flag) {
         try {
            try (InputStream inputstream = this.getResource("modules/guide/config.yml")) {
               if (inputstream != null) {
                  if (file1.getParentFile() != null) {
                     file1.getParentFile().mkdirs();
                  }
                  Files.copy(inputstream, file1.toPath(), StandardCopyOption.REPLACE_EXISTING);
               }
            }
         } catch (Exception exception) {
         }
      }
   }

   private void fixRewardWonPlaceholders(String resourcePath) {
      File file1 = new File(this.getDataFolder(), resourcePath);
      if (file1.isFile()) {
         YamlConfiguration yamlconfiguration = YamlConfiguration.loadConfiguration(file1);
         boolean flag = false;

         for (String s : List.of("won", "spin-format")) {
            String s1 = yamlconfiguration.getString(s);
            if (s1 != null && s1.contains("%RARITY")) {
               yamlconfiguration.set(
                  s,
                  s1.replace("%RARITY_COLOR%", "%rarity_color%")
                     .replace("%RARITY_COLORED%", "%rarity_colored%")
                     .replace("%RARITY%", "%rarity%")
                     .replace("%PERCENT%", "%percent%")
                     .replace("%REWARD%", "%reward%")
               );
               flag = true;
            }
         }

         if (flag) {
            try {
               yamlconfiguration.save(file1);
            } catch (Exception exception) {
            }
         }
      }
   }

   private void unstickHatshopFromTokenCosmeticsMenu() {
      File file1 = new File(this.getDataFolder(), "modules/tokens/tokens/menus/cosmetics.yml");
      if (file1.isFile()) {
         YamlConfiguration yamlconfiguration = YamlConfiguration.loadConfiguration(file1);
         String s = yamlconfiguration.getString("open_command", "");
         if ("hatshop".equalsIgnoreCase(s) || "wardrobe".equalsIgnoreCase(s)) {
            yamlconfiguration.set("open_command", null);
            yamlconfiguration.set("config-version", Math.max(9, yamlconfiguration.getInt("config-version", 0)));

            try {
               yamlconfiguration.save(file1);
            } catch (Exception exception) {
            }
         }
      }
   }

   private void rewriteStaleYaml(String resourcePath, int minVersion) {
      File file1 = new File(this.getDataFolder(), resourcePath);
      YamlConfiguration yamlconfiguration = file1.isFile() ? YamlConfiguration.loadConfiguration(file1) : new YamlConfiguration();
      if (yamlconfiguration.getInt("config-version", 0) < minVersion) {
         try {
            try (InputStream inputstream = this.getResource(resourcePath)) {
               if (inputstream != null) {
                  if (file1.getParentFile() != null) {
                     file1.getParentFile().mkdirs();
                  }

                  Files.copy(inputstream, file1.toPath(), StandardCopyOption.REPLACE_EXISTING);
                  return;
               }
            }
         } catch (Exception exception) {
         }
      }
   }

   private void registerOverlayModule(Module module) {
      this.moduleManager.register(module);
   }

   private void removeLeftoverNametags() {
      NamespacedKey namespacedkey = new NamespacedKey(this, "stats_nametag");
      int i = 0;

      for (World world : this.getServer().getWorlds()) {
         for (TextDisplay textdisplay : world.getEntitiesByClass(TextDisplay.class)) {
            if (textdisplay.getPersistentDataContainer().has(namespacedkey, PersistentDataType.STRING)) {
               textdisplay.remove();
               i++;
            }
         }
      }

      if (i > 0) {
         this.getLogger().info("Removed " + i + " leftover nametag hologram(s).");
      }
   }

   public MessageUtil.Delivery globalDelivery() {
      MessageUtil.Delivery messageutil$delivery = MessageUtil.Delivery.parse(this.getConfig().getString("message-mode", "chat"));
      return messageutil$delivery != null ? messageutil$delivery : MessageUtil.Delivery.CHAT;
   }
}
