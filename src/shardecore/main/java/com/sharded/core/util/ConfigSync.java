package com.sharded.core.util;

import com.sharded.core.ShardedCore;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ConfigSync {
   public static final int VERSION = 7;

   private ConfigSync() {
   }

   public static void syncMainConfig(ShardedCore plugin) {
      File file1 = new File(plugin.getDataFolder(), "config.yml");
      sync(plugin, file1, "config.yml");
      plugin.reloadConfig();
   }

   public static YamlConfiguration load(ShardedCore plugin, File file, String resourcePath) {
      sync(plugin, file, resourcePath);
      if (!file.exists()) {
         return new YamlConfiguration();
      } else {
         try {
            return YamlConfiguration.loadConfiguration(file);
         } catch (Exception exception) {
            plugin.getLogger().warning("Corrupt config at " + file.getPath() + ", replacing from jar: " + exception.getMessage());
            backup(file);
            if (plugin.getResource(resourcePath) != null) {
               plugin.saveResource(resourcePath, true);
            }

            return file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
         }
      }
   }

   public static void sync(ShardedCore plugin, File file, String resourcePath) {
      InputStream inputstream = plugin.getResource(resourcePath);
      if (inputstream == null) {
         return;
      }
      File parent = file.getParentFile();
      if (parent != null && !parent.exists()) {
         parent.mkdirs();
      }
      if (file.exists()) {
         backup(file);
      }
      try (inputstream) {
         Files.copy(inputstream, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException ioexception) {
         plugin.getLogger().warning("Could not reset " + file.getPath() + " from jar defaults: " + ioexception.getMessage());
      }
   }

   private static boolean shouldReplaceOnUpgrade(String resourcePath) {
      if (resourcePath.endsWith("gui.yml") || resourcePath.endsWith("shop.yml")
            || resourcePath.endsWith("gui-navigation.yml")
            || resourcePath.endsWith("modules/itemshop/itemshop.yml")) {
         return true;
      } else if (resourcePath.endsWith("modules/settings/settings/config.yml")
            || resourcePath.endsWith("modules/settings/config.yml")
            || resourcePath.endsWith("modules/pickupspawners/config.yml")) {
         return true;
      } else {
         return resourcePath.endsWith("modules/invrollback/config.yml") ? true : resourcePath.contains("/menus/") && resourcePath.endsWith(".yml");
      }
   }

   private static void stripArenaBlocks(YamlConfiguration disk, ShardedCore plugin) {
      ConfigurationSection configurationsection = disk.getConfigurationSection("arenas");
      if (configurationsection != null) {
         boolean flag = false;

         for (String s : configurationsection.getKeys(false)) {
            if (configurationsection.isList(s + ".blocks") || configurationsection.isString(s + ".blocks")) {
               configurationsection.set(s + ".blocks", null);
               flag = true;
            }
         }

         if (flag) {
            plugin.getLogger().info("Stripped legacy arena block data from config.yml (use /arena snapshot to rebuild .snap files)");
         }
      }
   }

   private static void stripArenaBlocksFromFile(File file, ShardedCore plugin) {
      File file1 = new File(file.getParentFile(), file.getName() + ".tmp");
      boolean flag = false;
      int i = -1;
      boolean flag1 = false;

      String s;
      try (
         BufferedReader bufferedreader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8);
         BufferedWriter bufferedwriter = Files.newBufferedWriter(file1.toPath(), StandardCharsets.UTF_8);
      ) {
         while ((s = bufferedreader.readLine()) != null) {
            String s1 = s.stripLeading();
            int j = s.length() - s1.length();
            if (s1.startsWith("blocks:")) {
               flag = true;
               i = j;
               flag1 = true;
            } else {
               if (flag) {
                  if (s1.isEmpty()) {
                     bufferedwriter.newLine();
                     continue;
                  }

                  if (j <= i && !s1.startsWith("- ")) {
                     flag = false;
                     i = -1;
                  } else if (s1.startsWith("- ") || j > i) {
                     continue;
                  }
               }

               bufferedwriter.write(s);
               bufferedwriter.newLine();
            }
         }
      } catch (IOException ioexception1) {
         plugin.getLogger().warning("Could not strip arena blocks from " + file.getPath() + ": " + ioexception1.getMessage());
         file1.delete();
         return;
      }

      if (!flag1) {
         file1.delete();
      } else {
         backup(file);

         try {
            Files.move(file1.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("Stripped legacy arena block data from oversized config.yml");
         } catch (IOException ioexception) {
            plugin.getLogger().warning("Could not replace arena config after strip: " + ioexception.getMessage());
            file1.delete();
         }
      }
   }

   public static int resetAll(ShardedCore plugin) {
      int i = 0;
      i += resetResource(plugin, "config.yml");
      return i + resetResourcesIn(plugin, "modules");
   }

   private static int resetResourcesIn(ShardedCore plugin, String folder) {
      int i = 0;
      i += resetIfExists(plugin, "modules/armortrims/config.yml");
      i += resetIfExists(plugin, "modules/armortrims/messages.yml");
      i += resetIfExists(plugin, "modules/autosmelt/config.yml");
      i += resetIfExists(plugin, "modules/autosmelt/messages.yml");
      i += resetIfExists(plugin, "modules/backpack/config.yml");
      i += resetIfExists(plugin, "modules/backpack/messages.yml");
      i += resetIfExists(plugin, "modules/bundles/config.yml");
      i += resetIfExists(plugin, "modules/staff/config.yml");
      i += resetIfExists(plugin, "modules/staff/messages.yml");
      i += resetIfExists(plugin, "modules/client/config.yml");
      i += resetIfExists(plugin, "modules/client/messages.yml");
      i += resetIfExists(plugin, "modules/chat/config.yml");
      i += resetIfExists(plugin, "modules/chat/messages.yml");
      i += resetIfExists(plugin, "modules/craft/config.yml");
      i += resetIfExists(plugin, "modules/craft/messages.yml");
      i += resetIfExists(plugin, "modules/deathmessages/config.yml");
      i += resetIfExists(plugin, "modules/deathmessages/messages.yml");
      i += resetIfExists(plugin, "modules/fix/config.yml");
      i += resetIfExists(plugin, "modules/fix/messages.yml");
      i += resetIfExists(plugin, "modules/fly/config.yml");
      i += resetIfExists(plugin, "modules/fly/messages.yml");
      i += resetIfExists(plugin, "modules/graves/config.yml");
      i += resetIfExists(plugin, "modules/graves/messages.yml");
      i += resetIfExists(plugin, "modules/joinmessages/config.yml");
      i += resetIfExists(plugin, "modules/joinmessages/messages.yml");
      i += resetIfExists(plugin, "modules/kill/config.yml");
      i += resetIfExists(plugin, "modules/kill/messages.yml");
      i += resetIfExists(plugin, "modules/killstreaks/config.yml");
      i += resetIfExists(plugin, "modules/killstreaks/messages.yml");
      i += resetIfExists(plugin, "modules/nightvision/config.yml");
      i += resetIfExists(plugin, "modules/nightvision/messages.yml");
      i += resetIfExists(plugin, "modules/pets/config.yml");
      i += resetIfExists(plugin, "modules/pets/messages.yml");
      i += resetIfExists(plugin, "modules/pets/gui.yml");
      i += resetIfExists(plugin, "modules/pickupmobs/config.yml");
      i += resetIfExists(plugin, "modules/pickupmobs/messages.yml");
      i += resetIfExists(plugin, "modules/pickupspawners/config.yml");
      i += resetIfExists(plugin, "modules/pickupspawners/messages.yml");
      i += resetIfExists(plugin, "modules/portalrtp/config.yml");
      i += resetIfExists(plugin, "modules/portalrtp/messages.yml");
      i += resetIfExists(plugin, "modules/portalrtp/gui.yml");
      i += resetIfExists(plugin, "modules/spawnselect/config.yml");
      i += resetIfExists(plugin, "modules/spawnselect/messages.yml");
      i += resetIfExists(plugin, "modules/spawnselect/gui.yml");
      i += resetIfExists(plugin, "modules/eglow/config.yml");
      i += resetIfExists(plugin, "modules/eglow/messages.yml");
      i += resetIfExists(plugin, "modules/chatcolor/config.yml");
      i += resetIfExists(plugin, "modules/chatcolor/messages.yml");
      i += resetIfExists(plugin, "modules/namecolor/config.yml");
      i += resetIfExists(plugin, "modules/namecolor/messages.yml");
      i += resetIfExists(plugin, "modules/wardrobe/config.yml");
      i += resetIfExists(plugin, "modules/wardrobe/messages.yml");
      i += resetIfExists(plugin, "modules/tags/config.yml");
      i += resetIfExists(plugin, "modules/tags/messages.yml");
      i += resetIfExists(plugin, "modules/privatemessages/config.yml");
      i += resetIfExists(plugin, "modules/privatemessages/messages.yml");
      i += resetIfExists(plugin, "modules/settings/config.yml");
      i += resetIfExists(plugin, "modules/settings/messages.yml");
      i += resetIfExists(plugin, "modules/settings/gui.yml");
      i += resetIfExists(plugin, "modules/abilities/config.yml");
      i += resetIfExists(plugin, "modules/abilities/messages.yml");
      i += resetIfExists(plugin, "modules/abilities/shop.yml");
      i += resetIfExists(plugin, "modules/tempranks/config.yml");
      i += resetIfExists(plugin, "modules/tempranks/messages.yml");
      i += resetIfExists(plugin, "modules/tempranks/tempranks.yml");
      i += resetIfExists(plugin, "modules/tokens/config.yml");
      i += resetIfExists(plugin, "modules/tokens/messages.yml");

      for (String s : new String[]{"mainmenu", "glow", "keys", "cosmetics", "gradients", "chatcolors", "tags", "backpack"}) {
         i += resetIfExists(plugin, "modules/tokens/menus/" + s + ".yml");
      }

      i += resetIfExists(plugin, "modules/toolname/config.yml");
      i += resetIfExists(plugin, "modules/toolname/messages.yml");
      i += resetIfExists(plugin, "modules/trash/config.yml");
      i += resetIfExists(plugin, "modules/trash/messages.yml");
      i += resetIfExists(plugin, "modules/staff/config.yml");
      i += resetIfExists(plugin, "modules/staff/messages.yml");
      i += resetIfExists(plugin, "modules/client/config.yml");
      return i + resetIfExists(plugin, "modules/client/messages.yml");
   }

   private static int resetResource(ShardedCore plugin, String resourcePath) {
      if (plugin.getResource(resourcePath) == null) {
         return 0;
      } else {
         File file1 = new File(plugin.getDataFolder(), resourcePath);
         File file2 = file1.getParentFile();
         if (file2 != null && !file2.exists()) {
            file2.mkdirs();
         }

         if (file1.exists()) {
            backup(file1);
         }

         plugin.saveResource(resourcePath, true);
         return 1;
      }
   }

   private static int resetIfExists(ShardedCore plugin, String resourcePath) {
      return plugin.getResource(resourcePath) == null ? 0 : resetResource(plugin, resourcePath);
   }

   private static void backup(File file) {
      try {
         Files.copy(file.toPath(), new File(file.getParentFile(), file.getName() + ".bak").toPath(), StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException ioexception) {
      }
   }
}
