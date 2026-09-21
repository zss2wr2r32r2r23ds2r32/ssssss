package com.sharded.core.module;

import com.sharded.core.ShardedCore;
import com.sharded.core.util.ColorUtil;
import com.sharded.core.util.ConfigSync;
import com.sharded.core.util.MessageUtil;
import com.sharded.core.util.Prefix;
import com.sharded.core.util.Text;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

public abstract class Module implements Listener {
   protected final ShardedCore plugin;
   private final String category;
   private final String id;
   private final List<String> commands = new ArrayList<>();
   private final List<Listener> listeners = new ArrayList<>();
   protected YamlConfiguration config;
   protected YamlConfiguration messages;
   private boolean enabled;

   protected Module(ShardedCore plugin, String id) {
      this.plugin = plugin;
      this.category = ModuleCategories.categoryOf(id);
      this.id = id;
   }

   protected Module(ShardedCore plugin, String category, String id) {
      this.plugin = plugin;
      this.category = category == null ? ModuleCategories.categoryOf(id) : category;
      this.id = id;
   }

   public final String categoryLabel() {
      return this.category;
   }

   public final String id() {
      return this.id;
   }

   public final boolean isEnabled() {
      return this.enabled;
   }

   public final void enable() {
      this.loadConfigs();
      this.onEnable();
      this.registerListener(this);
      this.enabled = true;
   }

   public final void disable() {
      if (!this.enabled) {
         return;
      }
      this.enabled = false;
      try {
         this.onDisable();
      } catch (Exception exception) {
         this.plugin.getLogger().warning("[" + this.id + "] onDisable: " + exception.getMessage());
      }
      List<Listener> copy = new ArrayList<>(this.listeners);
      this.listeners.clear();
      for (Listener listener : copy) {
         try {
            HandlerList.unregisterAll(listener);
         } catch (Exception ignored) {
         }
      }
      for (String s : new ArrayList<>(this.commands)) {
         PluginCommand plugincommand = this.plugin.getCommand(s);
         if (plugincommand != null) {
            plugincommand.setExecutor(this.plugin);
            plugincommand.setTabCompleter(this.plugin);
         }
      }
      this.commands.clear();
   }

   protected abstract void onEnable();

   protected void onDisable() {
   }

   public final void loadConfigs() {
      this.config = this.loadYaml("config.yml");
      this.messages = this.loadYaml("messages.yml");
   }

   private YamlConfiguration loadYaml(String fileName) {
      this.migrateLegacyFolder();
      File file1 = this.moduleFolder();
      File file2 = new File(file1, fileName);
      String s = this.resolveResourcePath(fileName);
      return ConfigSync.load(this.plugin, file2, s);
   }

   protected final String jarResourcePath(String fileName) {
      String s = ModulePaths.resourcePath(this.id, fileName);
      if (this.plugin.getResource(s) != null) {
         return s;
      } else {
         String s1 = "modules/" + this.id + "/" + fileName;
         return this.plugin.getResource(s1) != null ? s1 : s;
      }
   }

   protected final File syncJarResource(String fileName) {
      File file1 = new File(this.moduleFolder(), fileName);
      ConfigSync.sync(this.plugin, file1, this.jarResourcePath(fileName));
      return file1;
   }

   private String resolveResourcePath(String fileName) {
      return this.jarResourcePath(fileName);
   }

   private void migrateLegacyFolder() {
      if (!"core".equals(this.category)) {
         File file1 = new File(this.plugin.getDataFolder(), "modules/" + this.id);
         File file2 = this.moduleFolder();
         if (file1.exists() && !file1.getAbsolutePath().equals(file2.getAbsolutePath())) {
            if (!file2.exists() || file2.list() == null || file2.list().length <= 0) {
               try {
                  Path path = file2.toPath().toAbsolutePath().normalize();
                  Files.walk(file1.toPath()).forEach(pathx -> {
                     try {
                        Path path1 = pathx.toAbsolutePath().normalize();
                        if (path1.startsWith(path)) {
                           return;
                        }

                        Path path2 = file2.toPath().resolve(file1.toPath().relativize(pathx));
                        if (Files.isDirectory(pathx)) {
                           Files.createDirectories(path2);
                        } else {
                           Files.createDirectories(path2.getParent());
                           Files.copy(pathx, path2, StandardCopyOption.REPLACE_EXISTING);
                        }
                     } catch (Exception exception1) {
                     }
                  });
               } catch (Exception exception) {
                  this.plugin.getLogger().warning("Could not migrate module folder for " + this.id + ": " + exception.getMessage());
               }
            }
         }
      }
   }

   public void reload() {
      this.loadConfigs();
   }

   public final File moduleFolder() {
      File file1 = ModulePaths.moduleFolder(this.plugin, this.id);
      if (!file1.exists()) {
         file1.mkdirs();
      }

      return file1;
   }

   protected final void registerListener(Listener listener) {
      if (listener != null && !this.listeners.contains(listener)) {
         this.plugin.getServer().getPluginManager().registerEvents(listener, this.plugin);
         this.listeners.add(listener);
      }
   }

   protected final void registerCommand(String name, CommandExecutor executor) {
      PluginCommand plugincommand = this.plugin.getCommand(name);
      if (plugincommand == null) {
         this.plugin.getLogger().warning("[" + this.id + "] Command '" + name + "' is missing from plugin.yml!");
      } else {
         plugincommand.setExecutor(executor);
         if (executor instanceof TabCompleter tabcompleter) {
            plugincommand.setTabCompleter(tabcompleter);
         } else {
            plugincommand.setTabCompleter(this.plugin.coreTabComplete());
         }

         this.commands.add(name);
      }
   }

   protected String messagePrefix() {
      if (this.config != null && this.config.isString("prefix")) {
         return ColorUtil.normalize(this.config.getString("prefix"));
      } else {
         String s = this.messages.getString("prefix", "%prefix%");
         return ColorUtil.normalize(s.replace("%prefix%", Prefix.get()));
      }
   }

   public final String raw(String key, String... replacements) {
      String s = this.messages.getString(key, "<missing message: " + this.id + "/" + key + ">");
      s = s.replace("%prefix%", this.messagePrefix());
      return Text.apply(s, replacements);
   }

   public final List<String> rawList(String key, String... replacements) {
      List<String> list = new ArrayList<>(this.messages.getStringList(key));
      if (list.isEmpty()) {
         String s = this.messages.getString(key);
         if (s != null && !s.isEmpty()) {
            list.add(s);
         }
      }

      List<String> list1 = new ArrayList<>(list.size());

      for (String s1 : list) {
         list1.add(Text.apply(s1.replace("%prefix%", this.messagePrefix()), replacements));
      }

      return list1;
   }

   public final void send(CommandSender to, String key, String... replacements) {
      String s = this.raw(key, replacements);
      if (!s.isEmpty()) {
         MessageUtil.deliver(to, Text.c(s), this.resolveDelivery(key));
      }
   }

   public final MessageUtil.Delivery resolveDelivery(String key) {
      if (this.messages != null && this.messages.isString("message-modes." + key)) {
         MessageUtil.Delivery messageutil$delivery = MessageUtil.Delivery.parse(this.messages.getString("message-modes." + key));
         if (messageutil$delivery != null) {
            return messageutil$delivery;
         }
      }

      if (this.config != null && this.config.isString("message-mode")) {
         MessageUtil.Delivery messageutil$delivery1 = MessageUtil.Delivery.parse(this.config.getString("message-mode"));
         if (messageutil$delivery1 != null) {
            return messageutil$delivery1;
         }
      }

      MessageUtil.Delivery messageutil$delivery2 = MessageUtil.Delivery.parse(this.plugin.getConfig().getString("message-mode", "chat"));
      return messageutil$delivery2 != null ? messageutil$delivery2 : MessageUtil.Delivery.CHAT;
   }
}
