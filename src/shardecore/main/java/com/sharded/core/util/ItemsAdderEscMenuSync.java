package com.sharded.core.util;

import com.sharded.core.ShardedCore;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Locale;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ItemsAdderEscMenuSync {
   static final String DEFAULT_TEXT = "&dDisconnect &fFrom &dShardedMC";
   private static final String OLD_TEXT = "&x&A&D&4&E&F&FDisconnect from ShardedMC";
   private static final Pattern HASH_HEX = Pattern.compile("&#([0-9a-fA-F]{6})");
   private static final Pattern AMP_HEX = Pattern.compile("(?i)&x((?:&[0-9a-f]){6})");

   private ItemsAdderEscMenuSync() {
   }

   public static void install(ShardedCore plugin, YamlConfiguration config) {
      if (config.getBoolean("itemsadder-escape-menu.enabled", true)) {
         if (config.getBoolean("itemsadder-escape-menu.auto-install", true)) {
            if (!ItemsAdderHook.isAvailable()) {
               plugin.getLogger().info("[client] ItemsAdder not found — skip escape menu lang install");
            } else {
               String s = config.getString("itemsadder-escape-menu.disconnect-text", "&dDisconnect &fFrom &dShardedMC");
               if (isLegacyDisconnect(s)) {
                  s = "&dDisconnect &fFrom &dShardedMC";
                  config.set("itemsadder-escape-menu.disconnect-text", s);
                  saveClientConfig(plugin, config);
               }

               String s1 = toMinecraftLang(s);
               if (breaksWhenUppercased(s1)) {
                  s = "&dDisconnect &fFrom &dShardedMC";
                  s1 = toMinecraftLang(s);
                  config.set("itemsadder-escape-menu.disconnect-text", s);
                  saveClientConfig(plugin, config);
               }

               String s2 = config.getString("itemsadder-escape-menu.namespace", "shardedcore");
               String s3 = config.getString("itemsadder-escape-menu.block-id", "shardedcore_esc_menu");
               String s4 = "info:\n  namespace: %s\n\nminecraft_lang_overwrite:\n  %s:\n    entries:\n      \"menu.disconnect\": \"%s\"\n    languages:\n      - ALL\n"
                  .formatted(s2, s3, escapeYaml(s1));
               File file1 = resolveInstallFile(
                  plugin, config.getString("itemsadder-escape-menu.install-path", "plugins/ItemsAdder/contents/shardedcore/configs/esc_menu.yml")
               );

               try {
                  File file2 = file1.getParentFile();
                  if (file2 != null && !file2.exists()) {
                     file2.mkdirs();
                  }

                  byte[] abyte = s4.getBytes(StandardCharsets.UTF_8);
                  if (file1.exists() && Arrays.equals(Files.readAllBytes(file1.toPath()), abyte) && !staleLangFile(file1)) {
                     plugin.getLogger().info("[client] ItemsAdder escape menu config already up to date");
                     return;
                  }

                  Files.write(file1.toPath(), abyte);
                  plugin.getLogger().info("[client] Wrote ItemsAdder escape menu lang: " + file1.getPath());
                  if (config.getBoolean("itemsadder-escape-menu.reload-on-install", false)) {
                     String s5 = config.getString("itemsadder-escape-menu.reload-command", "iareload");
                     Bukkit.getScheduler().runTaskLater(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), s5), 40L);
                     plugin.getLogger().info("[client] Scheduled /" + s5 + " for escape menu lang");
                  } else {
                     plugin.getLogger().info("[client] Run /iazip once to apply the escape menu text");
                  }
               } catch (IOException ioexception) {
                  plugin.getLogger().log(Level.WARNING, "[client] Could not write escape menu lang: " + ioexception.getMessage());
               }
            }
         }
      }
   }

   static String toMinecraftLang(String raw) {
      if (raw == null || raw.isBlank()) {
         raw = "&dDisconnect &fFrom &dShardedMC";
      }

      String s = raw.replace('§', '&');
      Matcher matcher = HASH_HEX.matcher(s);
      StringBuilder stringbuilder = new StringBuilder();

      while (matcher.find()) {
         matcher.appendReplacement(stringbuilder, Matcher.quoteReplacement(sectionHex(matcher.group(1))));
      }

      matcher.appendTail(stringbuilder);
      s = stringbuilder.toString();
      Matcher matcher1 = AMP_HEX.matcher(s);
      StringBuilder stringbuilder1 = new StringBuilder();

      while (matcher1.find()) {
         String s1 = matcher1.group(1).replace("&", "");
         matcher1.appendReplacement(stringbuilder1, Matcher.quoteReplacement(sectionHex(s1)));
      }

      matcher1.appendTail(stringbuilder1);
      return stringbuilder1.toString().replace('&', '§');
   }

   static boolean isLegacyDisconnect(String raw) {
      if (raw != null && !raw.isBlank()) {
         String s = raw.replace(" ", "").toLowerCase(Locale.ROOT);
         if (raw.equals("&x&A&D&4&E&F&FDisconnect from ShardedMC")) {
            return true;
         } else {
            return !s.contains("ad4eff") && !s.contains("&x") && !s.contains("&#") && !s.contains("§x")
               ? s.contains("disconnectfromshardedmc")
                  && !s.contains("&ffrom")
                  && !s.contains("§ffrom")
                  && !s.contains("&ddisconnect")
                  && !s.contains("§ddisconnect")
               : true;
         }
      } else {
         return true;
      }
   }

   static boolean breaksWhenUppercased(String lang) {
      if (lang != null && !lang.isBlank()) {
         String s = lang.toUpperCase(Locale.ROOT);
         return s.contains("AD4EFF") || s.contains("§X") || Pattern.compile("(?i)(?<!§)AD4EFF").matcher(lang).find();
      } else {
         return true;
      }
   }

   static boolean staleLangFile(File dest) {
      if (dest != null && dest.isFile()) {
         try {
            String s = Files.readString(dest.toPath(), StandardCharsets.UTF_8);
            return s.contains("AD4EFF") || s.contains("§x") || s.contains("§X") || s.contains("&x") || s.contains("&#");
         } catch (IOException ioexception) {
            return true;
         }
      } else {
         return false;
      }
   }

   private static String sectionHex(String hex) {
      StringBuilder stringbuilder = new StringBuilder("§x");

      for (char c0 : hex.toUpperCase(Locale.ROOT).toCharArray()) {
         stringbuilder.append('§').append(c0);
      }

      return stringbuilder.toString();
   }

   private static void saveClientConfig(ShardedCore plugin, YamlConfiguration config) {
      File file1 = new File(plugin.getDataFolder(), "modules/client/config.yml");
      if (file1.isFile()) {
         try {
            config.save(file1);
         } catch (IOException ioexception) {
         }
      }
   }

   private static File resolveInstallFile(ShardedCore plugin, String path) {
      File file1 = new File(path);
      if (file1.isAbsolute()) {
         return file1;
      } else {
         File file2 = plugin.getDataFolder().getParentFile().getParentFile();
         return new File(file2, path.replace('/', File.separatorChar));
      }
   }

   private static String escapeYaml(String value) {
      return value.replace("\\", "\\\\").replace("\"", "\\\"");
   }
}
