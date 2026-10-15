package com.sharded.core.util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import org.bukkit.Bukkit;

public final class ItemsAdderLocatorPack {
   public static final String CONTENT_PACK = "sharded_locator";
   public static final String CONFIG_RELATIVE = "configs/locator.yml";
   public static final String HASH_RELATIVE = ".sharded-hash";
   public static final String[] ASSET_FILES = new String[]{
      "assets/sharded/waypoint_style/event_f.json",
      "assets/sharded/waypoint_style/event_o.json",
      "assets/sharded/waypoint_style/event_k.json",
      "assets/sharded/textures/gui/sprites/hud/locator_bar_dot/event_f.png",
      "assets/sharded/textures/gui/sprites/hud/locator_bar_dot/event_o.png",
      "assets/sharded/textures/gui/sprites/hud/locator_bar_dot/event_k.png",
      "assets/minecraft/waypoint_style/event_f.json",
      "assets/minecraft/waypoint_style/event_o.json",
      "assets/minecraft/waypoint_style/event_k.json",
      "assets/minecraft/textures/gui/sprites/hud/locator_bar_dot/event_f.png",
      "assets/minecraft/textures/gui/sprites/hud/locator_bar_dot/event_o.png",
      "assets/minecraft/textures/gui/sprites/hud/locator_bar_dot/event_k.png"
   };
   public static final String CONFIG_YAML = "info:\n  namespace: sharded_locator\n";

   private ItemsAdderLocatorPack() {
   }

   public static File contentRoot(File itemsAdderFolder) {
      return new File(itemsAdderFolder, "contents" + File.separator + "sharded_locator");
   }

   public static File itemsPacksRoot(File itemsAdderFolder) {
      return new File(itemsAdderFolder, "data" + File.separator + "items_packs" + File.separator + "sharded_locator");
   }

   public static boolean isItemsAdderPresent(File itemsAdderFolder) {
      if (itemsAdderFolder != null && itemsAdderFolder.isDirectory()) {
         return true;
      } else {
         try {
            return Bukkit.getPluginManager().getPlugin("ItemsAdder") != null;
         } catch (Throwable throwable) {
            return false;
         }
      }
   }

   public static boolean sync(File itemsAdderFolder, ItemsAdderLocatorPack.ResourceLoader loader) throws IOException {
      if (itemsAdderFolder != null && loader != null) {
         boolean flag = syncInto(contentRoot(itemsAdderFolder), loader);
         File file1 = new File(itemsAdderFolder, "data" + File.separator + "items_packs");
         if (file1.isDirectory()) {
            flag |= syncInto(itemsPacksRoot(itemsAdderFolder), loader);
         }

         return flag;
      } else {
         return false;
      }
   }

   public static boolean syncInto(File packRoot, ItemsAdderLocatorPack.ResourceLoader loader) throws IOException {
      if (packRoot != null && loader != null) {
         boolean flag = writeBytes(new File(packRoot, "configs/locator.yml"), "info:\n  namespace: sharded_locator\n".getBytes(StandardCharsets.UTF_8));
         StringBuilder stringbuilder = new StringBuilder();
         stringbuilder.append("info:\n  namespace: sharded_locator\n");

         for (String s : ASSET_FILES) {
            try (InputStream inputstream = loader.open("locator-bar-pack/" + s)) {
               if (inputstream != null) {
                  byte[] abyte = inputstream.readAllBytes();
                  flag |= writeBytes(new File(packRoot, "resourcepack/" + s), abyte);
                  stringbuilder.append(s).append('\n');
                  stringbuilder.append(sha256(abyte)).append('\n');
               }
            }
         }

         String s1 = sha256(stringbuilder.toString().getBytes(StandardCharsets.UTF_8));
         return flag | writeBytes(new File(packRoot, ".sharded-hash"), s1.getBytes(StandardCharsets.UTF_8));
      } else {
         return false;
      }
   }

   static boolean writeBytes(File dest, byte[] bytes) throws IOException {
      if (dest.getParentFile() != null) {
         dest.getParentFile().mkdirs();
      }

      if (dest.isFile()) {
         byte[] abyte = Files.readAllBytes(dest.toPath());
         if (Arrays.equals(abyte, bytes)) {
            return false;
         }
      }

      Files.copy(new ByteArrayInputStream(bytes), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
      return true;
   }

   static String sha256(byte[] bytes) {
      try {
         byte[] abyte = MessageDigest.getInstance("SHA-256").digest(bytes);
         return HexFormat.of().formatHex(abyte).toLowerCase(Locale.ROOT);
      } catch (Exception exception) {
         return Integer.toHexString(Arrays.hashCode(bytes));
      }
   }

   @FunctionalInterface
   public interface ResourceLoader {
      InputStream open(String var1) throws IOException;
   }
}
