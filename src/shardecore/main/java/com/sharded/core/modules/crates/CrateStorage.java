package com.sharded.core.modules.crates;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

public final class CrateStorage {
   private CrateStorage() {
   }

   public static boolean isAir(ItemStack item) {
      return item == null || item.getType().isAir() || item.getAmount() <= 0;
   }

   public static String serializeItem(ItemStack item) {
      try {
         String s;
         try (ByteArrayOutputStream bytearrayoutputstream = new ByteArrayOutputStream()) {
            BukkitObjectOutputStream bukkitobjectoutputstream = new BukkitObjectOutputStream(bytearrayoutputstream);

            try {
               bukkitobjectoutputstream.writeObject(item.clone());
               s = Base64.getEncoder().encodeToString(bytearrayoutputstream.toByteArray());
            } catch (Throwable throwable1) {
               try {
                  bukkitobjectoutputstream.close();
               } catch (Throwable throwable) {
                  throwable1.addSuppressed(throwable);
               }

               throw throwable1;
            }

            bukkitobjectoutputstream.close();
         }

         return s;
      } catch (IOException ioexception) {
         throw new IllegalStateException("Could not serialize item stack", ioexception);
      }
   }

   public static ItemStack deserializeItem(String encoded) {
      if (encoded != null && !encoded.isBlank()) {
         try {
            ItemStack itemstack;
            try (ByteArrayInputStream bytearrayinputstream = new ByteArrayInputStream(Base64.getDecoder().decode(encoded))) {
               BukkitObjectInputStream bukkitobjectinputstream = new BukkitObjectInputStream(bytearrayinputstream);

               try {
                  itemstack = (ItemStack)bukkitobjectinputstream.readObject();
               } catch (Throwable throwable1) {
                  try {
                     bukkitobjectinputstream.close();
                  } catch (Throwable throwable) {
                     throwable1.addSuppressed(throwable);
                  }

                  throw throwable1;
               }

               bukkitobjectinputstream.close();
            }

            return itemstack;
         } catch (ClassNotFoundException | IllegalArgumentException | IOException ioexception) {
            Bukkit.getLogger().warning("[core] Could not deserialize item: " + ioexception.getMessage());
            return null;
         }
      } else {
         return null;
      }
   }
}
